# CQRS com Spring Boot, Kafka, MySQL e Redis

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-brightgreen?style=flat-square&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Streaming-black?style=flat-square&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-7.2-red?style=flat-square&logo=redis)
![MySQL](https://img.shields.io/badge/MySQL-8+-blue?style=flat-square&logo=mysql)
![Mercado Pago](https://img.shields.io/badge/Mercado_Pago-SDK_3.1.0-009EE3?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI_3-85EA2D?style=flat-square&logo=swagger)
![Status](https://img.shields.io/badge/Status-Em_desenvolvimento-yellow?style=flat-square)

> Implementação de referência do padrão **CQRS (Command Query Responsibility Segregation)** com separação real de modelos de escrita e leitura, event streaming via Apache Kafka, cache distribuído com Redis, autenticação OAuth2 + JWT com Refresh Token e integração completa com Mercado Pago.

---

## Índice

- [Motivação](#motivação)
- [Arquitetura](#arquitetura)
- [Decisões Arquiteturais](#decisões-arquiteturais)
- [Tecnologias](#tecnologias)
- [Como Executar](#como-executar)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Estrutura de Pacotes](#estrutura-de-pacotes)
- [Fluxo de Dados](#fluxo-de-dados)
- [Autenticação e Refresh Token](#autenticação-e-refresh-token)
- [Cache Redis](#cache-redis)
- [Resiliência Kafka](#resiliência-kafka)
- [Módulo de Pagamentos](#módulo-de-pagamentos)
- [Documentação da API](#documentação-da-api)
- [Endpoints](#endpoints)
- [Como Testar](#como-testar)
- [Roadmap](#roadmap)

---

## Motivação

Aplicações que crescem rapidamente enfrentam um problema clássico: **operações de leitura e escrita competem pelos mesmos recursos**. Um endpoint de busca paginada com filtros complexos não deveria disputar conexões de banco com operações transacionais de escrita.

Este projeto demonstra como o padrão CQRS resolve esse problema na prática — não como exercício teórico, mas com uma implementação funcional que inclui consistência eventual via Kafka, cache inteligente com Redis, autenticação segura com OAuth2 + JWT, resiliência no consumo de eventos e integração completa com gateway de pagamento.

**Cada decisão tecnológica aqui tem um motivo. Esse motivo está documentado abaixo.**

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT / API                            │
└──────────────────────┬──────────────────────┬───────────────────┘
                       │                      │
               [WRITE / Command]       [READ / Query]
                       │                      │
            ┌──────────▼──────────┐  ┌────────▼────────┐
            │   Command Handler   │  │  Query Handler  │
            │  (valida e executa) │  │  (lê e projeta) │
            └──────────┬──────────┘  └────────▲────────┘
                       │                      │
            ┌──────────▼──────────┐  ┌────────┴────────┐
            │     Write Model     │  │   Redis Cache   │
            │  (MySQL - Command)  │  │  HIT → retorna  │
            └──────────┬──────────┘  │  MISS → MySQL   │
                       │             └────────▲────────┘
            ┌──────────▼──────────────────────┤
            │         Apache Kafka            │
            │      (Event Bus / Broker)       │
            └─────────────────────────────────┘
                                              │
                             ┌────────────────▼────────────┐
                             │         Read Model          │
                             │     (MySQL - Query)         │
                             └─────────────────────────────┘
```

---

## Decisões Arquiteturais

> *"Usar tecnologia sem saber por quê é receita para complexidade desnecessária."*

### Por que CQRS?

Em sistemas de leitura intensa, o modelo único de dados sofre com consultas complexas rodando no mesmo banco que recebe operações transacionais de escrita. CQRS permite que cada lado evolua independentemente — o modelo de leitura pode ser desnormalizado para performance sem impactar a integridade do lado de escrita.

### Por que Kafka e não RabbitMQ?

| Critério | Kafka | RabbitMQ |
|---|---|---|
| **Retenção de eventos** | Sim (log persistente) | Não (mensagem some após consumo) |
| **Replay de eventos** | Sim | Não |
| **Modelo** | Pull (consumer controla) | Push (broker controla) |
| **Caso de uso ideal** | Event sourcing, CQRS | Task queues, RPC |

Para CQRS, o Kafka é a escolha natural: se o consumer de leitura ficar fora do ar, ele pode fazer replay dos eventos perdidos ao voltar. Com RabbitMQ, esses eventos seriam perdidos.

### Por que Redis como cache?

O lado de leitura do CQRS é otimizado para consultas frequentes. Sem cache, cada `GET /products/{id}` bate no MySQL — desnecessário para dados que mudam raramente. A estratégia adotada é **cache-aside com invalidação orientada a eventos**: o Command Side invalida o cache ao escrever, e o Consumer Kafka sincroniza proativamente após processar o evento.

### Por que OAuth2 + JWT em cookie HttpOnly com Refresh Token?

JWT em `localStorage` é vulnerável a XSS. Armazenar o token em cookie HttpOnly elimina esse vetor de ataque. O Refresh Token armazenado no Redis com TTL de 7 dias permite renovação silenciosa do JWT de acesso (15min) sem interromper a sessão do usuário — e é revogado imediatamente no logout.

### Por que idempotência em múltiplas camadas no pagamento?

O Kafka garante at-least-once delivery. O Mercado Pago pode reenviar webhooks. O usuário pode clicar duas vezes em "Pagar". Cada cenário tem sua proteção: lock pessimista no banco, verificação de status do pedido, reutilização do PIX pendente, `idempotencyKey` na SDK e `eventId` determinístico no evento Kafka.

---

## Tecnologias

| Tecnologia | Versão | Finalidade |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework principal |
| Spring Security + OAuth2 | — | Autenticação via Google + JWT |
| Spring Data JPA | — | Persistência |
| Spring Cache + Redis | — | Cache distribuído (`@Cacheable`, `@CacheEvict`) |
| Spring Data Redis | — | Refresh Token store e operações manuais |
| Apache Kafka | — | Event streaming (Command → Query) |
| MySQL | 8+ | Banco de dados relacional |
| Redis | 7.2 | Cache distribuído + Refresh Token store |
| JJWT | — | Geração e validação de JWT |
| Mercado Pago SDK | 3.1.0 | PIX, cartão de crédito e pagamento presencial |
| Springdoc OpenAPI | 2.6.0 | Documentação Swagger UI |
| Docker + Compose | — | Infraestrutura local em um comando |
| Lombok | — | Redução de boilerplate |
| Commons Pool2 | — | Pool de conexões Lettuce (Redis) |
| Spring Retry | — | Retry com backoff exponencial no Kafka |

---

## Como Executar

### Pré-requisitos

- [Java 21+](https://adoptium.net/)
- [Docker](https://www.docker.com/) e [Docker Compose](https://docs.docker.com/compose/)
- Credenciais OAuth2 do [Google Cloud Console](https://console.cloud.google.com)
- Credenciais de sandbox do [Mercado Pago](https://www.mercadopago.com.br/developers)

### 1. Clone o repositório

```bash
git clone https://github.com/dantonigui/cqrs-spring-boot.git
cd cqrs-spring-boot
```

### 2. Configure as variáveis de ambiente

```bash
cp .env.example .env
# Edite o .env com suas configurações
```

### 3. Suba toda a infraestrutura

```bash
docker-compose up -d
```

Isso inicializa: MySQL · Apache Kafka + Zookeeper · Redis

### 4. Execute a aplicação

```bash
./mvnw spring-boot:run
```

Acesse: `http://localhost:8080`

### 5. Autentique-se com Google

```
http://localhost:8080/oauth2/authorization/google
```

O JWT é armazenado automaticamente em cookie HttpOnly após o login.

### 6. Acesse a documentação interativa

```
http://localhost:8080/swagger-ui.html
```

### 7. Verifique a saúde

```bash
curl http://localhost:8080/actuator/health
```

---

## Variáveis de Ambiente

```env
# === BANCO DE DADOS ===
DB_URL=jdbc:mysql://localhost:3306/cqrs_db
DB_USERNAME=root
DB_PASSWORD=sua_senha

# === KAFKA ===
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# === REDIS ===
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=sua_senha_redis

# === GOOGLE OAUTH2 ===
GOOGLE_CLIENT_ID=seu_client_id
GOOGLE_CLIENT_SECRET=seu_client_secret

# === JWT ===
JWT_SECRET=sua_chave_base64_minimo_32_bytes
JWT_EXPIRATION_MS=900000

# === REFRESH TOKEN ===
REFRESH_TOKEN_EXPIRATION_MS=604800000

# === ADMIN ===
ADMIN_EMAILS=seuemail@gmail.com

# === MERCADO PAGO ===
MP_ACCESS_TOKEN=seu_access_token
MP_PUBLIC_KEY=sua_public_key
MP_WEBHOOK_SECRET=sua_webhook_secret

# === APLICACAO ===
APP_BASE_URL=http://localhost:8080
FRONTEND_URL=http://localhost:3000
SERVER_PORT=8080
```

> Gere um JWT secret seguro com: `openssl rand -base64 32`
> O `.env` está no `.gitignore`. Nunca o remova dessa lista.

---

## Estrutura de Pacotes

```
src/main/java/com/project/cqrs/
│
├── config/                          # Configurações globais
│   ├── admin/                       # Resolução de role por email
│   ├── kafka/                       # KafkaProducerConfig, KafkaConsumerConfig
│   ├── mercadopago/                 # MercadoPagoConfiguration
│   ├── redis/                       # RedisConfig, CacheService, CacheAdminController
│   ├── resilience/                  # KafkaResilienceConfig, IdempotencyService, DlqAdminController
│   └── security/                    # SecurityConfig, SwaggerConfig
│
├── shared/                          # Eventos compartilhados entre Command e Query
│   └── event/
│       ├── user/                    # UserCreatedEvent, UserUpdatedEvent, UserLogoutEvent
│       ├── category/                # CategoryCreateEvent, CategoryUpdateEvent, CategoryDeleteEvent
│       ├── product/                 # ProductCreateEvent, ProductUpdateEvent, ProductDeleteEvent
│       └── payment/                 # PaymentApprovedEvent
│
├── command/                         # WRITE SIDE
│   ├── auth/                        # OAuth2, JWT, Refresh Token, logout
│   ├── category/                    # CRUD de escrita para Category
│   ├── order/                       # Criação de pedidos e checkout
│   ├── payment/                     # PIX, cartão, presencial, PaymentApprovalService
│   └── webhook/                     # WebhookController, WebhookService,
│                                    # WebhookSignatureValidator, MercadoPagoWebhookProcessor
│
└── query/                           # READ SIDE
    ├── auth/                        # GET /me, consumer de eventos de usuário
    ├── category/                    # Consultas com cache
    └── product/                     # Consultas com cache paginado
```

---

## Fluxo de Dados

### Escrita (Command Side)

```
POST /api/v1/command/products
        │
        ▼
ProductCommandController → ProductCommandService
  ├── Valida o DTO
  ├── Persiste no MySQL (Write DB)
  ├── Invalida cache Redis (@CacheEvict)
  └── Publica ProductCreatedEvent no Kafka
```

### Leitura (Query Side)

```
GET /api/v1/query/products/{id}
        │
        ▼
ProductQueryService (@Cacheable)
  ├── Cache HIT  → retorna do Redis (MySQL não é consultado)
  └── Cache MISS → consulta MySQL, armazena no Redis, retorna

Kafka: product-created / product-updated / product-deleted
        │
        ▼
ProductEventConsumer
  ├── Persiste no MySQL (Read DB)
  └── Sincroniza Redis:
        ├── CREATE → put detalhe no cache + evict lista
        ├── UPDATE → atualiza detalhe + evict lista
        └── DELETE → evict detalhe + evict lista
```

---

## Autenticação e Refresh Token

### Fluxo de login

```
GET /oauth2/authorization/google
        │
        ▼
Google OAuth2 → CustomOAuth2UserService
  ├── Cria ou atualiza usuário no Command DB
  ├── Atribui role ADMIN ou USER por email
  └── Publica UserCreatedEvent/UserUpdatedEvent no Kafka
        │
        ▼
OAuth2AuthSuccessHandler
  ├── Gera JWT de acesso (TTL: 15 minutos) → cookie HttpOnly
  ├── Gera Refresh Token (UUID, TTL: 7 dias) → Redis + cookie HttpOnly
  └── Redireciona para o frontend
```

### Renovação silenciosa

```
Qualquer requisição com JWT expirado
        │
        ▼
JwtAuthFilter
  ├── JWT inválido → lê cookie refresh_token
  ├── Valida Refresh Token no Redis
  ├── Rotaciona: invalida token antigo, gera novo
  ├── Emite novo JWT e novo Refresh Token nos cookies
  └── Autentica a requisição atual (transparente para o usuário)
```

### Logout

```
POST /api/v1/command/auth/logout
        │
        ▼
LogoutService
  ├── Revoga Refresh Token no Redis (imediato)
  ├── Limpa ambos os cookies (access + refresh)
  ├── Limpa SecurityContext
  └── Publica UserLogoutEvent no Kafka

POST /api/v1/command/auth/logout-all
  └── Revoga TODOS os Refresh Tokens do usuário no Redis
```

---

## Cache Redis

| Cache | Chave | TTL | Invalidado por |
|---|---|---|---|
| `products` | `page-{n}-size-{s}` | 5 min | create, update, delete |
| `product-detail` | `{id}` | 10 min | update, delete do produto |
| `categories` | — | 30 min | create, update, delete de categoria |
| `refresh_token:{uuid}` | userId | 7 dias | logout, logout-all, rotação |

### Endpoints administrativos de cache

| Método | Endpoint | Descrição |
|---|---|---|
| `DELETE` | `/admin/cache/products` | Invalida lista paginada |
| `DELETE` | `/admin/cache/product-detail/{id}` | Invalida detalhe |
| `DELETE` | `/admin/cache/all` | Limpa todos os caches |
| `GET` | `/admin/cache/stats/{id}` | HIT/MISS e TTL restante |
| `GET` | `/admin/cache/keys` | Lista chaves (diagnóstico) |

---

## Resiliência Kafka

O consumo de eventos implementa três camadas de proteção:

### Retry com Backoff Exponencial

Quando um consumer lança exceção, o Spring Kafka tenta novamente com espera crescente antes de desistir:

```
Tentativa 1 → aguarda 1s
Tentativa 2 → aguarda 2s
Tentativa 3 → aguarda 4s → falha → DLQ
Tempo total máximo: 10s
```

### Dead Letter Queue (DLQ)

Após esgotar as tentativas, a mensagem é publicada automaticamente no topic `{original}.DLT`. O consumer continua processando as próximas mensagens normalmente.

| Topic original | DLQ |
|---|---|
| `product.created` | `product.created.DLT` |
| `product.updated` | `product.updated.DLT` |
| `product.deleted` | `product.deleted.DLT` |
| `payment.approved` | `payment.approved.DLT` |

### Endpoints administrativos de DLQ

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/admin/dlq/stats` | Total de mensagens pendentes por DLQ |
| `GET` | `/admin/dlq/{topic}/peek` | Inspeciona mensagens sem consumir |
| `POST` | `/admin/dlq/{topic}/replay` | Reenvia para o topic original |

### Idempotência

Antes de processar qualquer evento Kafka, o consumer verifica se o `eventId` já foi processado na tabela `processed_events`. Duplicatas são descartadas silenciosamente.

---

## Módulo de Pagamentos

Integração com **Mercado Pago SDK 3.1.0** cobrindo três métodos de pagamento.

### Fluxo de checkout

```
POST /api/v1/command/orders
  └── Cria pedido (preço buscado no banco — nunca aceito do frontend)

POST /api/v1/command/orders/{id}/checkout/pix
  └── Retorna QR code + copia-e-cola
      Status: PENDING até confirmação via webhook

POST /api/v1/command/orders/{id}/checkout/card
  └── Tokenização pelo MP.js no frontend (PCI Compliance)
      Resultado síncrono: APPROVED | REJECTED | IN_PROCESS

POST /api/v1/command/orders/{id}/checkout/in-person
  └── Registra pagamento presencial (CASH, CARD, PIX)
      Aprovado imediatamente — sem chamada à API do MP
```

### Fluxo do webhook

```
Mercado Pago
  → WebhookController          (recebe HTTP, delega)
  → WebhookService             (orquestra: valida → filtra → extrai ID)
  → WebhookSignatureValidator  (validação HMAC-SHA256 exclusivamente)
  → MercadoPagoWebhookProcessor (consulta API MP, atualiza status)
  → PaymentApprovalService     (lock pessimista, marca PAID, publica Kafka)
```

### Proteções contra duplo pagamento

| Camada | Proteção |
|---|---|
| Lock pessimista | `SELECT ... FOR UPDATE` em todos os métodos de checkout |
| Status do pedido | Rejeita se `PAID` ou `CANCELLED` |
| PIX pendente | Reenvia QR code existente sem criar novo pagamento no MP |
| Cartão / Presencial | Rejeita se já existe pagamento `PENDING` ou `APPROVED` |
| Webhook duplicado | Ignora se pagamento já está `APPROVED` ou pedido já está `PAID` |
| `idempotencyKey` | Passado na SDK do MP por operação |
| `eventId` determinístico | `UUID.nameUUIDFromBytes("approved-{mpPaymentId}")` no evento Kafka |
| Constraint única no banco | `UNIQUE INDEX` em pagamentos ativos por pedido |
| CHECK constraints | `unit_price > 0`, `total_amount > 0`, `transaction_amount > 0` |

### Proteção contra price tampering

O `CreateOrderRequestDTO` aceita apenas `productId` e `quantity`. O preço e o nome do produto são buscados exclusivamente no banco pelo backend — qualquer valor enviado pelo frontend é ignorado.

---

## Documentação da API

A documentação interativa está disponível via Swagger UI após subir a aplicação:

```
http://localhost:8080/swagger-ui.html
```

A especificação OpenAPI em JSON:

```
http://localhost:8080/v3/api-docs
```

---

## Endpoints

### Autenticação

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/oauth2/authorization/google` | Público | Inicia fluxo OAuth2 |
| `GET` | `/api/v1/query/auth/me` | Autenticado | Dados do usuário logado |
| `POST` | `/api/v1/command/auth/logout` | Autenticado | Encerra sessão e revoga token |
| `POST` | `/api/v1/command/auth/logout-all` | Autenticado | Revoga todos os tokens do usuário |

### Category — Command `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/command/categories` | Cria categoria |
| `PUT` | `/api/v1/command/categories/{id}` | Atualiza categoria |
| `DELETE` | `/api/v1/command/categories/{id}` | Remove categoria |

### Category — Query `Autenticado`

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/v1/query/categories` | Lista categorias |
| `GET` | `/api/v1/query/categories/{id}` | Busca por ID |

### Product — Command `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/command/products` | Cria produto |
| `PUT` | `/api/v1/command/products/{id}` | Atualiza produto |
| `DELETE` | `/api/v1/command/products/{id}` | Remove produto |

### Product — Query `Autenticado`

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/v1/query/products` | Lista produtos (paginado) |
| `GET` | `/api/v1/query/products/{id}` | Busca por ID |

### Order & Checkout `Autenticado`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/command/orders` | Cria pedido |
| `POST` | `/api/v1/command/orders/{id}/checkout/pix` | Checkout PIX |
| `POST` | `/api/v1/command/orders/{id}/checkout/card` | Checkout Cartão |
| `POST` | `/api/v1/command/orders/{id}/checkout/in-person` | Checkout Presencial |

### Webhook

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/command/payments/webhook` | Público (HMAC) | Notificação IPN do Mercado Pago |

### Admin — Cache `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `DELETE` | `/admin/cache/products` | Invalida lista paginada |
| `DELETE` | `/admin/cache/product-detail/{id}` | Invalida detalhe |
| `DELETE` | `/admin/cache/all` | Limpa todos os caches |
| `GET` | `/admin/cache/stats/{id}` | HIT/MISS e TTL |
| `GET` | `/admin/cache/keys` | Lista chaves (diagnóstico) |

### Admin — DLQ `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/admin/dlq/stats` | Mensagens pendentes por DLQ |
| `GET` | `/admin/dlq/{topic}/peek` | Inspeciona sem consumir |
| `POST` | `/admin/dlq/{topic}/replay` | Reenvia para topic original |

---

## Como Testar

### Swagger UI

A forma mais direta. Acesse `http://localhost:8080/swagger-ui.html` e use o botão "Try it out" em qualquer endpoint.

### Fluxo completo via Postman

```
1. GET  /oauth2/authorization/google          → login, recebe cookie

2. POST /api/v1/command/products              → cria produto (ADMIN)

3. POST /api/v1/command/orders
        body: { "items": [{ "productId": 1, "quantity": 2 }] }
        → retorna orderId

4. POST /api/v1/command/orders/{id}/checkout/in-person
        body: { "method": "CASH" }
        → aprovado imediatamente, sem precisar do Mercado Pago
```

### Webhook com ngrok

Para testar o fluxo de PIX e cartão com o sandbox do Mercado Pago:

```bash
ngrok http 8080
# Use a URL gerada no .env: APP_BASE_URL=https://abc123.ngrok.io
```

### Monitorar eventos Kafka

```bash
docker exec -it kafka bash

kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic payment.approved \
  --from-beginning

kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group cqrs-resilient-consumer-group
```

### Inspecionar o Redis

```bash
docker exec -it redis redis-cli -a sua_senha

KEYS cqrs:*                        # todas as chaves de cache
GET "cqrs:product-detail::1"       # valor cacheado
TTL "cqrs:product-detail::1"       # tempo restante
KEYS refresh_token:*               # tokens de sessão ativos
```

---

## Roadmap

- [ ] Query Side para pedidos e pagamentos (`OrderQueryEntity`, `GET /orders`)
- [ ] Cancelamento de pedido com estorno no Mercado Pago
- [ ] Testes de integração com Testcontainers
- [ ] Distributed Tracing com Micrometer + Zipkin
- [ ] Métricas com Prometheus + Grafana
- [ ] Notificação ao usuário após pagamento aprovado
- [ ] Deploy em ambiente cloud (Railway + Upstash)
- [ ] Frontend React consumindo a API

---

<div align="center">

Feito com ☕ por [Guilherme D'Antoni](https://github.com/dantonigui)

</div>