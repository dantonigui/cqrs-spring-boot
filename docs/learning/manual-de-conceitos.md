# Manual de conceitos — entendendo o projeto

Este guia explica os termos antes de apresentar as classes. Continue pelo [fluxo geral](fluxo-geral.md) e pelos [fluxos de cada estratégia](fluxos-das-estrategias.md). Os exemplos descrevem o código atual; configuração em arquivo não comprova o estado de uma instância em execução.

## 1. Por que existem MySQL, Kafka e Redis?

O sistema precisa registrar pedidos e pagamentos, responder consultas rapidamente e executar tarefas depois da resposta HTTP.

| Recurso | Responsabilidade |
|---|---|
| MySQL command | Fonte de verdade de preço, estoque, pedido e pagamento |
| MySQL query | Representação preparada para consultas |
| Kafka | Transporta e retém eventos para consumidores independentes |
| Redis | Cache e estado temporário de autenticação |
| Mercado Pago | Processa pagamentos externos |
| SMTP | Envia e-mails |

Kafka não substitui o banco financeiro; Redis não decide se um pagamento aconteceu.

## 2. CQRS, comando, consulta e projeção

**Comando** é uma intenção de mudança: criar pedido ou cancelar pedido. Pode ser rejeitado.

**Consulta** pede informação: mostrar meus pedidos.

**CQRS — Command Query Responsibility Segregation** separa escrita e leitura. Aqui, os diretórios command e query usam bancos diferentes.

**Projeção** é uma representação construída a partir dos eventos. Pense em um painel atualizado a partir de registros oficiais.

**Consistência eventual** significa que a consulta pode demorar a refletir uma escrita. Um POST pode criar o pedido 42 e a consulta retornar 404 temporariamente, enquanto o evento ainda está sendo processado.

Isso não significa aceitar divergência permanente. Se o consumer estiver parado, limpar cache não atualiza a projeção.

## 3. Transação, commit e rollback

Uma **transação** agrupa alterações no mesmo banco.

- Commit: confirma.
- Rollback: desfaz as alterações daquela transação.
- Atomicidade: o conjunto confirma ou é desfeito como uma unidade.

Reserva de estoque, criação de pedido e outbox são gravadas na mesma transação command. Se um produto faltar, reservas anteriores dessa operação também são desfeitas.

Não há transação única envolvendo MySQL, Kafka, Redis, Mercado Pago e SMTP. Cada integração possui suas próprias confirmações.

## 4. Kafka: eventos, tópicos e consumidores

Kafka funciona como um diário organizado de eventos: produtores acrescentam registros e grupos de consumidores acompanham o progresso independentemente.

| Termo | Significado | Exemplo |
|---|---|---|
| Evento | Fato ocorrido | Pedido criado |
| Producer | Publica mensagens | Dispatcher usando KafkaTemplate |
| Broker | Servidor Kafka | Serviço kafka no Compose |
| Cluster | Conjunto de brokers | Localmente apenas um broker |
| Topic ou tópico | Canal de eventos de um assunto | order-created |
| Partition ou partição | Sequência ordenada dentro de um tópico | Partição 0 |
| Key ou chave | Identifica o agregado e influencia a partição escolhida | ID do pedido |
| Payload | Conteúdo da mensagem | IDs, itens, total e estado |
| Header | Metadado da mensagem | Alias do tipo Java |
| Offset | Posição do registro em uma partição | Offset 25 |
| Consumer | Processa registros | OrderEventConsumer |
| Consumer group | Grupo que divide as partições consumidas | cqrs-query-group |
| Lag | Distância entre o progresso do grupo e os registros disponíveis | Eventos ainda não acompanhados |

Offset não é um ID global: sua posição pertence a tópico + partição. O eventId é o identificador usado pela idempotência da aplicação.

### Partições e paralelismo

Com três partições e três consumidores no mesmo grupo:

~~~text
Partição 0 -> Consumidor A
Partição 1 -> Consumidor B
Partição 2 -> Consumidor C
~~~

Uma partição fica atribuída a um consumidor por vez dentro de um grupo. Consumidores além do número de partições não aumentam o paralelismo útil naquele tópico.

A ordem é garantida dentro da partição, não globalmente entre tópicos. Usar a mesma chave em tópicos diferentes não cria uma sequência global.

### Grupos independentes

- cqrs-query-group atualiza as projeções.
- cqrs-notifications envia notificações.

Os dois podem receber o mesmo evento de pagamento. Dentro do grupo o trabalho é dividido; entre grupos o progresso é independente. Ler um registro não o apaga imediatamente do Kafka.

### Rebalanceamento e replicação

**Rebalanceamento** redistribui partições quando consumidores entram ou saem. É distribuição de trabalho, sem ser um proxy de requisições HTTP como um load balancer.

**Replicação** mantém cópias da partição em brokers diferentes. Uma réplica sincronizada pode assumir a liderança após falha, conforme a configuração. Réplica não é uma nova partição para aumentar paralelismo.

O Compose atual tem um broker, padrão de uma partição para tópicos criados automaticamente, retenção de sete dias e volume persistente. Não há redundância entre brokers. Mudar o padrão não altera tópicos já existentes.

### Confirmação e repetição

**ACK** é confirmação. O produtor usa acks=all; com um broker isso não cria cópias em outros servidores.

**At-least-once**, ou pelo menos uma vez, permite nova entrega da mesma mensagem. O broker pode receber o evento e a aplicação cair antes de registrar a confirmação no banco.

O produtor idempotente evita certas duplicações de transporte nas próprias retentativas. Não substitui a idempotência dos consumers nem torna SMTP exatamente uma vez.

## 5. Transactional outbox

Outbox é uma tabela de eventos a publicar, gravada junto com o negócio.

Sem ela: o pedido poderia confirmar no banco e o envio ao Kafka se perder.

Com ela:

1. A transação grava o pedido e o evento em outbox_events.
2. O commit confirma ambos.
3. O dispatcher busca pendências.
4. Publica no Kafka e espera confirmação.
5. Marca como publicado.

O dispatcher consulta até 50 registros por lote, a cada segundo por padrão, e aguarda até dez segundos pela confirmação de cada envio. Uma falha interrompe o lote e mantém pendências.

Pode haver repetição se o envio funcionar e o commit do dispatcher falhar. Outbox e idempotência se complementam.

## 6. Idempotência — Idempotency

Uma operação idempotente não reaplica o efeito de negócio quando a mesma operação identificada é repetida.

Exemplo: o mesmo evento de aprovação não deve criar duas aprovações na projeção.

O projeto usa a tabela processed_events e a combinação:

~~~text
event_id + topic
~~~

Os estados são PROCESSING, COMPLETED e FAILED. Bloqueios e uma constraint única enfrentam a concorrência.

Projeção e processamento participam da mesma transação query:

- Se funcionarem, confirmam a alteração e COMPLETED.
- Se houver rollback, as alterações da transação são desfeitas.
- Um COMPLETED existente faz o consumer ignorar a repetição.
- Uma disputa concorrente pode exigir retry.

FAILED escrito nessa mesma transação também pode ser desfeito pelo rollback. Sua ausência não prova que nunca houve erro.

Notificações usam notification: no identificador de tópico da deduplicação. Projetar um evento não impede que ele seja usado para enviar e-mail.

**Limite:** SMTP pode aceitar o envio antes de um commit local falhar. A repetição pode enviar outro e-mail. A garantia não é exactly-once para efeitos externos.

## 7. Retry, backoff, DLQ e replay

Neste contexto o termo é **DLQ**, não DQL. DQL também é usado para consultas de dados em outros contextos, mas não é esse mecanismo de falhas.

| Termo | Significado |
|---|---|
| Retry | Tentar novamente |
| Backoff | Esperar entre tentativas |
| DLQ — Dead Letter Queue | Destino separado para mensagens que não puderam ser processadas |
| DLT — Dead Letter Topic | Implementação desse destino como tópico Kafka |
| Replay | Republicar para tentar processar novamente |

~~~text
order-created -> falha -> retries -> order-created.DLT
~~~

O backoff começa em um segundo, multiplica por dois, limita cada intervalo a quatro segundos e usa orçamento de espera de dez segundos. Isso não é prazo total garantido: a execução das tentativas também consome tempo. Exceções classificadas como não recuperáveis podem ir direto à recuperação.

O replay exige ADMIN, preserva payload, chave e headers e espera confirmação. Percorre a DLQ desde o início e não apaga a origem. Repeti-lo pode republicar mensagens antigas.

Procedimento: investigar -> corrigir causa -> inspecionar -> repetir -> verificar resultado. DLQ não corrige automaticamente dados inválidos.

## 8. Redis e cache

Redis é um armazenamento por chave usado aqui para cache, OAuth2 e sessões.

Cache é uma cópia temporária para evitar consultas repetidas ao banco.

| Termo | Significado |
|---|---|
| Cache hit | A resposta está disponível no cache |
| Cache miss | Buscar no banco porque a chave não possui resposta utilizável |
| TTL | Tempo de vida até expirar |
| Invalidação | Remover a cópia para uma próxima consulta recarregar |
| Namespace | Organização lógica por prefixo |
| Serialização | Converter objetos para um formato como JSON |
| Cache-aside | Aplicação busca cache e carrega o banco quando faltar |

Exemplo de chave:

~~~text
cqrs:order-detail::42-user-7
~~~

O usuário faz parte da chave para não reaproveitar a resposta de um pedido entre clientes.

| Cache configurado | TTL |
|---|---:|
| products | 5 minutos |
| product_details | 10 minutos |
| categories | 30 minutos |
| orders | 2 minutos |
| order-detail | 5 minutos |

O cache de categorias está configurado, mas as consultas de categoria não usam @Cacheable atualmente.

Página, tamanho e ordenação participam das chaves de listagem. Consumers atualizam ou invalidam cache depois de mudanças. O CacheManager transaction-aware adia operações participantes até o commit; isso não cria uma transação distribuída com Redis.

### Redis tem partições?

Aqui é standalone: uma instância, sem Redis Cluster e sem distribuição entre nós. Prefixos são organização lógica, não partições Kafka ou isolamento de memória.

Redis Cluster pode distribuir chaves por hash slots, mas não está implantado aqui. Os scripts de sessão pressupõem standalone.

### Memória e persistência

Redis tem limite de 256 MB, AOF e volume Docker. AOF registra operações para persistência.

A política noeviction não expulsa chaves por pressão de memória. Comandos que precisam de mais espaço podem falhar ao atingir o limite; TTL continua podendo expirar chaves.

Sessões e cache compartilham memória. Essa política evita expulsar sessões indiscriminadamente, mas não elimina o risco de falta de espaço.

## 9. Autenticação e segurança

| Termo | Uso no projeto |
|---|---|
| Autenticação | Identificar o usuário |
| Autorização | Conferir papel e propriedade do recurso |
| OAuth2 | Fluxo de login com Google |
| JWT | Token assinado com ID, papel, validade e sid |
| Refresh token | Credencial para renovar o acesso |
| Sessão | Identidade revogável no Redis |
| HttpOnly | Impedir leitura do cookie por JavaScript |
| CSRF | Proteger escritas que usam cookies |
| CORS | Controlar acesso do navegador entre origens |
| HMAC | Validar assinatura do webhook com segredo compartilhado |

JWT válido não basta: o filtro também confere a sessão no Redis. Logout revoga a sessão e invalida inclusive JWT copiado.

CORS não substitui autorização. O webhook é público no filtro HTTP, mas exige assinatura válida. CSRF e assinatura do webhook resolvem problemas diferentes.

## 10. Concorrência e estoque

Concorrência é disputar os mesmos dados ao mesmo tempo.

Um lock pessimista mantém o registro bloqueado durante a transação. Duas compras da última unidade não podem reservar a mesma unidade com base no mesmo valor antigo.

Constraint é uma regra no banco, como estoque não negativo e um único pagamento ativo por pedido.

Deadlock é uma espera circular entre transações. Bloquear produtos por ID crescente reduz o risco, sem eliminá-lo em todo o sistema. Uma transação abortada precisa ser repetida de forma apropriada.

Reserva reduz o estoque disponível. Pagar não reduz novamente. Cancelar libera uma vez.

## 11. Integrações e operação

- Webhook: aviso HTTP de um provedor. O backend valida a assinatura e consulta o Mercado Pago antes de aplicar o status.
- Estorno: devolução de pagamento aprovado; é diferente de cancelar uma cobrança pendente.
- Idempotency key: identifica uma operação no provedor para reduzir duplicação; não cria transação distribuída.
- Scheduler: rotina periódica; a expiração verifica reservas a cada minuto por padrão.
- SMTP: protocolo de envio de e-mail; aceitação não prova entrega na caixa principal.
- Flyway: migrações SQL versionadas, com histórico próprio em cada banco.
- Hibernate validate: confere entidades contra schema, sem criá-lo automaticamente.
- Healthcheck: verifica saúde do serviço, sem comprovar Google, SMTP ou pagamento real.
- Rate limit: limita a frequência de métodos anotados; aqui é local à JVM, por IP remoto e método.

## 12. O que não confundir

| Recurso | Não garante sozinho |
|---|---|
| Kafka | Ausência de repetição ou ordem entre tópicos |
| Outbox | Efeito único no consumer |
| Idempotência no banco | E-mail exatamente uma vez |
| TTL | Invalidação imediata |
| Cache | Projeção atualizada |
| Volume Docker | Backup ou alta disponibilidade |
| JWT válido | Sessão ativa e autorização para qualquer pedido |
| Uma partição | Ordem global entre tópicos |
| Healthcheck UP | Todos os provedores externos funcionando |

## 13. Mapa para encontrar o código

Caminhos relativos à raiz do repositório:

| Conceito | Local |
|---|---|
| Infraestrutura | docker-compose.yml |
| TTL e serialização | src/main/java/com/project/cqrs/config/redis/RedisConfig.java |
| Consumer e offsets | src/main/java/com/project/cqrs/config/kafka/KafkaConsumerConfig.java |
| Retry e DLQ | src/main/java/com/project/cqrs/config/kafka/KafkaRetryConfig.java |
| Outbox | src/main/java/com/project/cqrs/command/outbox/ |
| Idempotência | src/main/java/com/project/cqrs/admin/idempotency/service/IdempotencyService.java |
| Estoque | src/main/java/com/project/cqrs/command/product/service/InventoryService.java |
| Sessão | src/main/java/com/project/cqrs/command/auth/infra/security/RefreshTokenService.java |
| Notificações | src/main/java/com/project/cqrs/notification/ |

Continue pelo [fluxo geral](fluxo-geral.md).