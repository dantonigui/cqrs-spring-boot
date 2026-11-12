# Fluxo geral do projeto

Leia primeiro o [manual de conceitos](manual-de-conceitos.md). Este documento mostra o conjunto; as decisões de cada mecanismo estão nos [fluxos das estratégias](fluxos-das-estrategias.md).

Os diagramas usam Mermaid. Em um visualizador compatível, como o GitHub, aparecem como desenhos; em um editor simples, o texto continua guardado no Markdown.

## 1. Mapa da arquitetura

~~~mermaid
flowchart TD
    Cliente["Cliente ou administrador"] --> API["API Spring Boot"]
    API --> Seguranca["JWT, sessão Redis, papel e CSRF"]
    Seguranca --> Comando["Comando: mudar o negócio"]
    Seguranca --> Consulta["Consulta: ler dados"]
    Comando --> DBCommand[("MySQL command: fonte de verdade")]
    DBCommand --> Outbox[("outbox_events no mesmo banco")]
    Outbox --> Dispatcher["Dispatcher de pendências"]
    Dispatcher --> Kafka["Kafka: tópicos de eventos"]
    Kafka --> Projecao["Grupo cqrs-query-group"]
    Kafka --> Notificacao["Grupo cqrs-notifications"]
    Projecao --> DBQuery[("MySQL query e processed_events")]
    Projecao --> Cache[("Redis: caches")]
    Consulta --> Cache
    Consulta --> DBQuery
    Notificacao --> SMTP["SMTP: e-mails"]
    Comando --> MP["Mercado Pago"]
    MP --> Webhook["Webhook com assinatura HMAC"]
    Webhook --> Comando
    Scheduler["Scheduler de expiração"] --> Comando
    Projecao --> Falha["Retry e DLQ em caso de erro"]
    Notificacao --> Falha
~~~

As setas mostram dependências, não uma transação única. MySQL command e query são bancos distintos. Kafka, Redis, SMTP e Mercado Pago têm confirmações independentes.

## 2. Uma compra completa, passo a passo

### Preparação

1. O usuário entra com Google OAuth2.
2. O backend cria ou atualiza o usuário, registra eventos e estabelece uma sessão Redis.
3. O navegador recebe cookies de acesso e refresh.
4. Um ADMIN cadastra categoria/produto e repõe o estoque. Produto novo começa com zero.
5. Eventos atualizam o catálogo de leitura.

### Compra

1. O cliente consulta produtos pelo lado query, podendo usar Redis.
2. Envia apenas IDs e quantidades para criar o pedido.
3. O backend verifica identidade e CSRF.
4. Busca preços e estoque no banco command, bloqueando os produtos em ordem de ID.
5. Reserva estoque, grava itens com preços congelados, pedido e outbox.
6. Confirma a transação e devolve 201 com ID e Location.
7. O dispatcher publica os eventos no Kafka.
8. Consumers atualizam a projeção e os caches.
9. O cliente consulta o pedido; pode haver atraso até a projeção existir.
10. O proprietário inicia PIX ou cartão; ADMIN pode confirmar recebimento presencial.
11. Aprovação atualiza o banco command e gera evento na outbox.
12. O grupo de leitura registra o pagamento e o pedido pago.
13. O grupo de notificações tenta enviar o e-mail.

### Alternativas

- Sem estoque: 409; a transação não deixa reserva parcial.
- Kafka indisponível: o pedido confirmado continua no command; o evento aguarda na outbox.
- Consumer falha: a transação query é revertida; retry ou DLQ.
- Cliente cancela: confirma cancelamento/estorno quando necessário, libera estoque e publica evento.
- Pedido não pago vence: após 30 minutos, a rotina periódica tenta cancelar.
- Aprovação chega após cancelamento: o pedido permanece cancelado e o pagamento online é estornado.
- SMTP falha: o consumer de notificações falha e usa retry/DLQ, independentemente da projeção.

## 3. Sequência da criação e pagamento

~~~mermaid
sequenceDiagram
    actor Cliente
    participant API
    participant C as MySQL command
    participant D as Dispatcher
    participant K as Kafka
    participant Q as Consumer de leitura
    participant R as MySQL query
    participant N as Consumer de notificações
    participant SMTP
    participant MP as Mercado Pago

    Cliente->>API: Criar pedido: IDs e quantidades
    API->>C: Transação: bloquear, reservar, salvar pedido e outbox
    C-->>API: Commit
    API-->>Cliente: 201, orderId e Location
    D->>C: Buscar pendências
    D->>K: Publicar eventos
    K-->>D: Confirmação
    D->>C: Marcar publicados e confirmar
    K->>Q: Pedido criado
    Q->>R: Projeção e idempotência na mesma transação
    Cliente->>API: Iniciar checkout do próprio pedido
    API->>MP: Criar cobrança
    MP-->>API: Resultado ou dados PIX
    MP->>API: Webhook quando aplicável
    API->>MP: Consultar estado após validar assinatura
    API->>C: Aprovar e gravar evento na outbox
    D->>K: Publicar aprovação
    K->>Q: Aprovação para grupo de leitura
    Q->>R: Registrar pagamento e pedido pago
    K->>N: Aprovação para grupo de notificações
    N->>SMTP: Enviar e-mail
~~~

Cartão já aprovado pode confirmar sincronamente no checkout, sem aguardar webhook. Presencial usa o serviço comum de aprovação, sem chamar o Mercado Pago. O diagrama representa a alternativa online com webhook.

## 4. Estados do pedido

~~~mermaid
stateDiagram-v2
    [*] --> PENDING: Criar e reservar
    PENDING --> AWAITING_PAYMENT: Iniciar checkout online
    PENDING --> PAID: Aprovação permitida
    AWAITING_PAYMENT --> PAID: Aprovação
    PENDING --> CANCELLED: Cancelar ou expirar
    AWAITING_PAYMENT --> CANCELLED: Cancelar ou expirar
    PAID --> CANCELLED: Cancelar com tratamento financeiro
    CANCELLED --> [*]
~~~

PENDING = criado; AWAITING_PAYMENT = aguardando pagamento; PAID = pago; CANCELLED = cancelado.

CANCELLED é terminal. Pagamento recusado não cancela automaticamente o pedido. Pagamento presencial aprovado exige ADMIN para cancelar; devolução física depende do operador.

## 5. Onde cada informação fica

| Informação | Local principal | Observação |
|---|---|---|
| Preço usado para comprar | product_command | Não usa preço do cache/projeção |
| Quantidade disponível | product_command.stock | Reserva reduz; cancelamento repõe |
| Pedido e seus itens | order_command e order_command_items | Preserva snapshot de nome e preço |
| Pagamentos | payments | Constraint de pagamento ativo |
| Eventos a publicar | outbox_events | Mesmo banco command |
| Consulta do pedido | order_query e order_item_query | Pode atrasar |
| Consulta financeira | payment_query | Atualizada por eventos |
| Deduplicação | processed_events no query | Mesma transação da projeção |
| Respostas temporárias | Redis com prefixo cqrs: | TTL e invalidação |
| Sessões e refresh | Redis com prefixo auth:v2: | Revogação e rotação |
| Falhas esgotadas | Tópico original com sufixo .DLT | Não é uma tabela MySQL |

## 6. Como diagnosticar um caso

| Sintoma | Investigar primeiro |
|---|---|
| POST criou, GET ainda não encontra | Outbox, Kafka e consumer antes do cache |
| Pedido existe no query, mas resposta está antiga | Chave, TTL e invalidação Redis |
| Evento duplicado | processed_events e resultado do consumer |
| Mensagem sempre falha | Exceção, retry, DLQ e dependências |
| E-mail não chega | Grupo de notificações, usuário projetado e SMTP |
| Aprovação não aparece | Estado no command, outbox, tópico e query |
| Login deixou de funcionar | Sessão Redis, expiração, cookies e provedor |
| Estoque reservado por muito tempo | Idade do pedido, scheduler e falha no cancelamento externo |

## 7. Limites do desenho atual

- Um broker Kafka, sem redundância entre brokers.
- Uma instância Redis, compartilhada por cache e autenticação.
- Entrega de eventos pelo menos uma vez; efeitos externos podem repetir.
- Sem transação distribuída com provedor de pagamento ou SMTP.
- Sem reconstrução garantida de todo o query a partir de apenas sete dias de eventos.
- Sem prova de entrega real de e-mail apenas porque a aplicação está UP.

Continue nos [fluxos das estratégias](fluxos-das-estrategias.md) para estudar cada decisão isoladamente.