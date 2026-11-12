# Fluxos de cada estratégia do sistema

Este documento complementa o [manual](manual-de-conceitos.md) e o [fluxo geral](fluxo-geral.md). Cada estratégia mostra objetivo, decisões, falhas e localização no código. Os caminhos citados são relativos à raiz do repositório.

## Índice

1. [CQRS e consistência eventual](#1-cqrs-e-consistência-eventual)
2. [Outbox](#2-outbox-publicação-durável)
3. [Idempotência](#3-idempotência-e-transação-da-projeção)
4. [Retry, DLQ e replay](#4-retry-dlq-e-replay)
5. [Cache e invalidação](#5-cache-aside-e-invalidação)
6. [Reserva de estoque](#6-reserva-de-estoque-e-concorrência)
7. [Expiração](#7-expiração-da-reserva)
8. [PIX e cartão](#8-checkout-pix-e-cartão)
9. [Webhook e aprovação](#9-webhook-e-aprovação)
10. [Presencial](#10-pagamento-presencial)
11. [Cancelamento](#11-cancelamento-e-estorno)
12. [Notificações](#12-notificações-por-e-mail)
13. [Login e sessão](#13-login-oauth2-e-sessão)
14. [Refresh e logout](#14-refresh-atômico-e-logout)
15. [Proteções HTTP e rate limit](#15-proteções-http-e-rate-limit)
16. [Migrações e infraestrutura](#16-migrações-e-inicialização)

## 1. CQRS e consistência eventual

**Objetivo:** separar decisões de negócio das representações de consulta.

~~~mermaid
flowchart LR
    A["Comando HTTP"] --> B["Validar negócio"]
    B --> C[("Commit no command com outbox")]
    C --> D["Publicar no Kafka"]
    D --> E["Consumer de projeção"]
    E --> F[("Commit no query")]
    F --> G["Atualizar ou invalidar cache"]
    H["Consulta HTTP"] --> I["Cache ou query"]
~~~

**Atenção:** resposta da escrita não espera o consumer. Uma consulta imediata pode encontrar dados antigos. Eventos em tópicos diferentes podem chegar fora da ordem esperada; consumers dependentes falham temporariamente e usam retry.

**Código:** command/, query/ e command/outbox/. Não tomar decisões de preço ou estoque pelo query.

## 2. Outbox: publicação durável

**Objetivo:** confirmar o negócio sem perder seu evento se o Kafka estiver fora.

~~~mermaid
flowchart TD
    A["Iniciar transação command"] --> B["Salvar negócio e outbox"]
    B --> C{"Operação válida?"}
    C -- Não --> R["Rollback de ambos"]
    C -- Sim --> D["Commit de ambos"]
    D --> E["Dispatcher bloqueia lote pendente"]
    E --> F["Enviar e esperar confirmação"]
    F --> G{"Kafka confirmou?"}
    G -- Não --> H["Interromper lote e manter pendência"]
    H --> E
    G -- Sim --> I["Marcar publicado"]
    I --> J["Commit do dispatcher"]
~~~

Se o broker confirmou, mas o commit do dispatcher falhou, o evento pode reaparecer no próximo lote. O consumer precisa deduplicar.

**Configuração:** até 50 eventos; intervalo padrão de 1 segundo; espera de confirmação de até 10 segundos por envio. Não há limpeza automática dos publicados.

**Código:** command/outbox/OutboxPublisher.java e OutboxDispatcher.java.

## 3. Idempotência e transação da projeção

**Objetivo:** repetir entrega sem repetir o efeito já concluído.

~~~mermaid
flowchart TD
    A["Receber eventId e tópico"] --> B["Iniciar transação query"]
    B --> C{"Registro existente?"}
    C -- COMPLETED --> D["Ignorar repetição"]
    C -- PROCESSING --> E["Lançar erro e aguardar retry"]
    C -- FAILED --> F["Preparar nova tentativa"]
    C -- Ausente --> G["Inserir com chave única"]
    F --> H["Aplicar alteração na projeção"]
    G --> H
    H --> I{"Processamento funcionou?"}
    I -- Sim --> J["Marcar COMPLETED e confirmar"]
    I -- Não --> K["Rollback e erro para Kafka"]
~~~

Concorrência pode causar disputa na inserção ou deadlock. A tentativa abortada não confirma a projeção. Um registro FAILED escrito na mesma transação pode ser desfeito junto com o restante.

Deduplicação não significa ignorar qualquer evento do mesmo pedido: eventos diferentes possuem IDs diferentes.

**Código:** admin/idempotency/service/IdempotencyService.java e ProcessedEventRepository.java.

## 4. Retry, DLQ e replay

**Objetivo:** recuperar falhas temporárias e separar mensagens que precisam de intervenção.

~~~mermaid
flowchart TD
    A["Consumer processa mensagem"] --> B{"Sucesso?"}
    B -- Sim --> C["Confirmar processamento"]
    B -- Não --> D{"Erro permite retry e há orçamento?"}
    D -- Sim --> E["Esperar backoff"]
    E --> A
    D -- Não --> F["Encaminhar para tópico .DLT"]
    F --> G["ADMIN inspeciona causa e mensagem"]
    G --> H["Corrigir causa antes de repetir"]
    H --> I["Replay publica no tópico original"]
    I --> A
~~~

Backoff configurado: 1 segundo, fator 2, teto de 4 segundos e limite de espera de 10 segundos. O tempo total inclui as execuções. A classificação da exceção influencia o retry.

Replay preserva conteúdo e headers, espera ACK e não limpa o tópico .DLT. A contagem da DLQ não equivale necessariamente a falhas ainda não resolvidas: o histórico permanece após replay.

**Código:** config/kafka/KafkaRetryConfig.java e admin/dlq/. Endpoints: GET /admin/dlq/stats, GET /admin/dlq/{topic}/peek e POST /admin/dlq/{topic}/replay. Escrita exige CSRF.

## 5. Cache-aside e invalidação

**Objetivo:** consultar rápido sem depender de uma cópia permanente.

~~~mermaid
flowchart TD
    A["Consulta autorizada"] --> B["Calcular chave"]
    B --> C{"Cache hit?"}
    C -- Sim --> D["Responder com cópia"]
    C -- Não --> E["Consultar query"]
    E --> F["Validar dono no detalhe de pedido"]
    F --> G["Montar DTO e guardar com TTL"]
    G --> D
    H["Evento altera projeção"] --> I["Confirmar transação"]
    I --> J["Atualizar detalhe ou invalidar caches"]
    J --> C
~~~

Chaves de pedidos incluem usuário; listagens incluem página, tamanho e ordenação. Pedido alheio retorna 404. A separação de chaves é essencial porque um cache hit evita executar o corpo do método anotado.

Produtos: consumers atualizam detalhes e invalidam listas. Pedidos: invalidam listas e todos os detalhes do respectivo cache, uma escolha mais ampla e simples. Expiração por TTL é uma segunda proteção, não uma invalidação imediata.

Se a projeção estiver atrasada, um cache miss também pode carregar dados atrasados. Atualização de Redis e commit MySQL não são atomicamente distribuídos.

**Código:** config/redis/RedisConfig.java, query/order/service/OrderQueryService.java, query/product/service/ProductQueryService.java e admin/cache/service/CacheSynchronizationService.java.

## 6. Reserva de estoque e concorrência

**Objetivo:** impedir que compras simultâneas reservem mais do que existe.

~~~mermaid
flowchart TD
    A["Receber IDs e quantidades"] --> B["Agregar produtos repetidos"]
    B --> C["Ordenar IDs"]
    C --> D["Iniciar transação e bloquear produtos"]
    D --> E{"Todos têm quantidade suficiente?"}
    E -- Não --> F["Erro 409 e rollback de toda a operação"]
    E -- Sim --> G["Reduzir estoque disponível"]
    G --> H["Congelar nome e preço nos itens"]
    H --> I["Salvar pedido e eventos na outbox"]
    I --> J["Commit e resposta 201"]
~~~

Dois pedidos disputando a última unidade não leem livremente o mesmo saldo: o lock serializa a decisão sobre aquele produto.

Pagar não reduz estoque novamente. Não existe tabela separada de reserva: a reserva é representada pela redução do disponível e pelos itens/estado do pedido.

**Código:** command/product/service/InventoryService.java e command/order/service/OrderCommandService.java. ADMIN repõe estoque pelo endpoint de stock.

## 7. Expiração da reserva

**Objetivo:** não manter estoque preso indefinidamente em pedidos não pagos.

~~~mermaid
flowchart TD
    A["Scheduler periódico"] --> B["Buscar até 100 pedidos vencidos"]
    B --> C["Bloquear cada pedido"]
    C --> D{"Ainda PENDING ou AWAITING_PAYMENT?"}
    D -- Não --> E["Preservar pedido"]
    D -- Sim --> F["Executar fluxo de cancelamento"]
    F --> G{"Cancelamento funcionou?"}
    G -- Sim --> H["CANCELLED, estoque liberado e outbox"]
    G -- Não --> I["Registrar falha e repetir em outro ciclo"]
~~~

Padrão: prazo de 30 minutos desde a criação; verificação a cada minuto. Não são 30 minutos desde o último checkout. A execução pode atrasar pelo ciclo ou por falhas externas.

O lock revalida o estado para não cancelar um pedido pago durante a espera.

**Código:** OrderExpirationService.java e OrderCancelledService.java em command/order/service/.

## 8. Checkout PIX e cartão

**Objetivo:** criar cobrança para o proprietário, sem usar valores enviados pelo cliente.

~~~mermaid
flowchart TD
    A["Checkout autenticado"] --> B["Bloquear pedido"]
    B --> C{"É o dono e o estado permite?"}
    C -- Não --> D["Rejeitar"]
    C -- Sim --> E{"PIX pendente reutilizável?"}
    E -- Sim --> F["Retornar QR code já registrado"]
    E -- Não --> G{"Há outro pagamento ativo?"}
    G -- Sim --> D
    G -- Não --> H["Criar cobrança com total do command"]
    H --> I["Registrar retorno do Mercado Pago"]
    I --> J{"Cartão já aprovado?"}
    J -- Sim --> K["Serviço comum de aprovação"]
    J -- Não --> L["Responder QR code ou status"]
~~~

PIX pendente é reutilizado no fluxo atual; cartão recusado não cancela automaticamente o pedido. O banco também impede dois pagamentos ativos por pedido.

O cartão chega como token gerado no frontend, sem o número completo. A autorização de ADMIN não dispensa propriedade no checkout online.

Falha entre aceitação do provedor e commit local exige conciliação pelo ID/status externo antes de uma nova cobrança.

**Código:** command/payment/service/MercadoPagoPaymentService.java e PaymentCommandRepository.java.

## 9. Webhook e aprovação

**Objetivo:** aceitar avisos verificáveis e preservar o estado terminal do pedido.

~~~mermaid
flowchart TD
    A["Webhook público"] --> B{"Assinatura válida e ID coerente?"}
    B -- Não --> C["401 ou 400"]
    B -- Sim --> D["Consultar pagamento no provedor"]
    D --> E{"Existe localmente?"}
    E -- Não --> F["503 para nova tentativa"]
    E -- Sim --> G["Bloquear pedido e atualizar leitura do pagamento"]
    G --> H{"Aprovação nova?"}
    H -- Não --> I["Aplicar status permitido sem rebaixar aprovado"]
    H -- Sim --> J{"Pedido cancelado?"}
    J -- Sim --> K["Estornar e manter CANCELLED"]
    J -- Não --> L{"Pedido já pago?"}
    L -- Sim --> M["Não publicar outra aprovação"]
    L -- Não --> N["Marcar pago e gravar aprovação na outbox"]
~~~

Pagamento já REFUNDED é ignorado pelo processador. Falhas externas não são convertidas silenciosamente em 200. A aprovação síncrona do cartão e a confirmação presencial também usam o serviço comum.

**Código:** command/webhook/service/ e command/payment/service/PaymentApprovalService.java.

## 10. Pagamento presencial

**Objetivo:** permitir ao operador registrar um recebimento feito fora do provedor online.

~~~mermaid
flowchart LR
    A["ADMIN confirma recebimento"] --> B["Bloquear pedido"]
    B --> C["Validar estado e ausência de pagamento ativo"]
    C --> D["Registrar pagamento IN_PERSON"]
    D --> E["Serviço comum aprova e grava outbox"]
    E --> F["Consumers atualizam consulta e tentam e-mail"]
~~~

Não chama Mercado Pago. Não existe validação automática de que dinheiro ou transação de máquina física foi realmente recebido; isso é responsabilidade do operador.

**Código:** registerInPersonPayment em MercadoPagoPaymentService.java.

## 11. Cancelamento e estorno

**Objetivo:** encerrar pedido e cobrança, liberando estoque uma única vez.

~~~mermaid
flowchart TD
    A["Pedido de cancelamento ou expiração"] --> B["Bloquear pedido e validar dono ou ADMIN"]
    B --> C{"Já cancelado?"}
    C -- Sim --> D["Não liberar novamente"]
    C -- Não --> E{"Pago presencialmente?"}
    E -- Sim --> F["Exigir ADMIN"]
    E -- Não --> G["Verificar pagamentos"]
    F --> G
    G --> H{"Cobrança online?"}
    H -- Sim --> I["Consultar e cancelar pendente ou estornar aprovado"]
    H -- Não --> J["Registrar devolução ou cancelamento local"]
    I --> K{"Tratamento externo confirmou?"}
    K -- Não --> L["Falhar sem liberar estoque local"]
    K -- Sim --> M["Liberar estoque"]
    J --> M
    M --> N["Marcar CANCELLED e gravar outbox"]
    N --> O["Projeção e notificação independentes"]
~~~

Falha externa provoca rollback local. Entretanto, se o provedor já executou uma operação e o commit local falhou depois, é necessária conciliação: rollback MySQL não desfaz o provedor.

No presencial, registrar REFUNDED não devolve dinheiro fisicamente. O operador deve efetuar a devolução.

**Código:** command/order/service/OrderCancelledService.java e command/payment/service/PaymentGateway.java.

## 12. Notificações por e-mail

**Objetivo:** enviar confirmação/cancelamento sem acoplar envio ao tempo da requisição HTTP.

~~~mermaid
flowchart TD
    A["Evento de aprovação ou cancelamento"] --> B["Grupo cqrs-notifications"]
    B --> C["Reivindicar idempotência notification:topic"]
    C --> D{"Já concluído?"}
    D -- Sim --> E["Ignorar repetição"]
    D -- Não --> F["Buscar usuário projetado"]
    F --> G["Gerar template HTML com moeda brasileira"]
    G --> H["Enviar via SMTP"]
    H --> I{"Envio aceito?"}
    I -- Sim --> J["Marcar COMPLETED e confirmar"]
    I -- Não --> K["Rollback e retry ou DLQ"]
~~~

Se o usuário ainda não foi projetado, ocorre erro e retry. O envio SMTP é um efeito externo: aceitação antes de falha do commit pode gerar e-mail duplicado.

O grupo query não é bloqueado por uma falha do grupo de notificações. Layout pronto não comprova envio real configurado ou entrega na caixa principal.

**Código:** notification/consumer/NotificationEventConsumer.java e notification/service/.

## 13. Login OAuth2 e sessão

**Objetivo:** identificar o usuário com Google e estabelecer acesso revogável.

~~~mermaid
flowchart TD
    A["Navegador inicia login"] --> B["Guardar pedido OAuth2 no Redis por 3 minutos"]
    B --> C["Cookie recebe somente ID aleatório"]
    C --> D["Redirecionar ao Google"]
    D --> E["Callback validado pelo fluxo OAuth2"]
    E --> F["Criar ou atualizar usuário e registrar eventos"]
    F --> G["Criar sessão e refresh no Redis"]
    G --> H["Emitir JWT com sid e cookies HttpOnly"]
    H --> I["Nas chamadas validar JWT e sessão ativa"]
~~~

Estado OAuth2, cache de consultas e sessão são usos diferentes do Redis. Perder a sessão exige novo login; não é apenas um cache miss de produto.

O endpoint /me usa o command como alternativa enquanto o usuário não existe na projeção query.

**Código:** HttpCookieOAuth2AuthorizationRequestRepository.java, CustomOAuth2UserService.java, OAuth2AuthSucessHandler.java e JwtAuthFilter.java.

## 14. Refresh atômico e logout

**Objetivo:** renovar acesso sem permitir reutilização concorrente da mesma credencial.

~~~mermaid
flowchart TD
    A["Requisição sem access válido"] --> B["Consultar refresh"]
    B --> C{"Refresh e sessão válidos?"}
    C -- Não --> D["Sem autenticação"]
    C -- Sim --> E{"É logout?"}
    E -- Sim --> F["Autenticar sem rotacionar"]
    E -- Não --> G["Lua consome token antigo e cria substituto"]
    G --> H{"Esta requisição venceu a disputa?"}
    H -- Sim --> I["Emitir novo JWT e cookies"]
    H -- Não --> J["Não emitir substituição"]
    F --> K["Revogar sessão atual ou todas as sessões"]
    K --> L["Remover cookies"]
~~~

O script Lua executa a troca de forma atômica: apenas uma chamada consome o refresh antigo. Logout global remove as sessões do usuário, sem revogar as de outras pessoas. JWTs copiados também são recusados porque seu sid deixa de estar ativo.

**Código:** command/auth/infra/security/RefreshTokenService.java e command/auth/service/LogoutService.java.

## 15. Proteções HTTP e rate limit

São camadas com objetivos diferentes, não uma única proteção.

~~~mermaid
flowchart TD
    A["Requisição HTTP"] --> B["Filtros de CORS, CSRF e autenticação"]
    B --> C{"Rota e papel permitidos?"}
    C -- Não --> D["401 ou 403 conforme a falha"]
    C -- Sim --> E["Controller e validação dos dados"]
    E --> F["Rate limit se o método estiver anotado"]
    F --> G{"Há capacidade no bucket?"}
    G -- Não --> H["429"]
    G -- Sim --> I["Regras de propriedade e negócio"]
    I --> J["Executar ou rejeitar"]
~~~

O desenho agrupa filtros sem afirmar uma ordem universal entre todos eles. Escrita sem CSRF pode ser recusada antes da verificação final da rota.

O rate limit usa Bucket4j/Caffeine em memória por IP remoto + método anotado. Não confia diretamente no X-Forwarded-For. Duas instâncias possuem limites independentes; não é um rate limit distribuído no Redis.

**Código:** config/security/SecurityConfig.java e config/rateLimit/RateLimitAspect.java.

## 16. Migrações e inicialização

**Objetivo:** subir a aplicação com schemas conhecidos e dependências disponíveis.

~~~mermaid
flowchart TD
    A["Docker Compose inicia infraestrutura"] --> B["Healthchecks de bancos e Redis"]
    A --> C["ZooKeeper saudável libera Kafka"]
    B --> D["Dependências da API saudáveis"]
    C --> D
    D --> E["Iniciar API quando perfil app está ativo"]
    E --> F["Flyway aplica migrações em cada datasource"]
    F --> G["Hibernate valida entidades e schema"]
    G --> H["Iniciar listeners e rotinas"]
    H --> I["Healthcheck HTTP da API"]
~~~

Cada banco possui seu flyway_schema_history. Banco antigo sem histórico não recebe baseline automático: exige backup e comparação do schema.

Health UP não testa login Google, entrega real SMTP ou uma cobrança. O Compose é local: um broker, Redis standalone e portas publicadas em loopback.

**Código:** docker-compose.yml, config/database/MigrationConfig.java, CommandDatabaseConfig.java e QueryDatabaseConfig.java.

## Como praticar sem confundir as garantias

Use somente um ambiente de desenvolvimento e dados fictícios.

| Exercício | O que observar |
|---|---|
| Parar Kafka e criar pedido | Command confirma; outbox fica pendente |
| Religá-lo | Dispatcher publica; query acompanha depois |
| Consultar duas vezes | Miss seguido de hit enquanto TTL/chave permitirem |
| Consultar pedido com outro usuário | 404 inclusive depois de preencher o cache do dono |
| Repetir o mesmo evento | Consumer não reaplica um COMPLETED |
| Forçar falha persistente controlada | Retry e depois tópico .DLT |
| Corrigir e executar replay | Processamento retoma sem limpar o histórico da DLQ |
| Disputar a última unidade | Um pedido reserva; outro falha sem saldo negativo |
| Expirar reserva não paga | Cancelamento libera uma vez; pedido pago é preservado |
| Simular aprovação tardia | Pedido permanece CANCELLED e há tentativa de estorno |
| Simular SMTP indisponível | Grupo de notificações falha sem desfazer projeção já confirmada |
| Fazer logout em uma sessão | JWT daquela sessão deixa de autenticar |

Não pare serviços de outro projeto nem apague volumes para realizar esses exercícios. Para executar o ambiente, consulte [Docker local](../testing/docker-local.md); para recuperação, consulte [operação](../deployment/operations.md).