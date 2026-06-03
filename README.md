# CQRS com Spring Boot, Kafka, MySQL e Redis

> Projeto de estudo e referência sobre o padrão **CQRS (Command Query Responsibility Segregation)** implementado com Spring Boot 3, Apache Kafka, MySQL e Redis Cache — separando claramente as responsabilidades de escrita (Command) e leitura (Query), com camada de cache para alta performance nas consultas.

---

## Índice

- [Sobre o Projeto](#sobre-o-projeto)
- [Padrão CQRS](#padrão-cqrs)
- [Arquitetura](#arquitetura)
- [Tecnologias](#tecnologias)
- [Pré-requisitos](#pré-requisitos)
- [Como Executar](#como-executar)
- [Variáveis de Ambiente](#variáveis-de-ambiente)
- [Estrutura de Pacotes](#estrutura-de-pacotes)
- [Fluxo de Dados](#fluxo-de-dados)
- [Cache Redis](#cache-redis)
- [Endpoints](#endpoints)
- [Contribuindo](#contribuindo)
- [Licença](#licença)

---

## Sobre o Projeto

Este projeto tem como objetivo demonstrar de forma prática e didática a implementação do padrão **CQRS** em uma aplicação Java moderna. As entidades do domínio são `Product` (Produto), `Category` (Categoria) e `User` (Usuário).

O lado de **Command** é responsável por receber e processar operações de escrita (criar, atualizar, deletar), publicando eventos no **Apache Kafka**. O lado de **Query** consome esses eventos, mantém sua própria visão dos dados otimizada para leitura e utiliza **Redis** como camada de cache para evitar consultas desnecessárias ao banco de dados.

A autenticação é feita via **OAuth2 com Google**, gerando um **JWT armazenado em cookie HttpOnly** após o login bem-sucedido.

---

## Padrão CQRS

**CQRS** (Command Query Responsibility Segregation) é um padrão arquitetural que separa as operações de leitura das operações de escrita em modelos distintos.

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
            │  (MySQL - Command)  │  │   (HIT → skip   │
            └──────────┬──────────┘  │    MISS → DB)   │
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

### Benefícios aplicados neste projeto

| Benefício | Como se manifesta aqui |
|---|---|
| **Separação de responsabilidades** | Pacotes `command` e `query` completamente independentes |
| **Escalabilidade independente** | Lado de leitura e escrita podem escalar separadamente |
| **Desacoplamento** | Kafka desacopla producers (Command) de consumers (Query) |
| **Modelos otimizados** | Cada lado possui seu próprio Model, Repository e Service |
| **Performance de leitura** | Redis elimina consultas ao MySQL em cache HIT |
| **Consistência eventual** | Consumer Kafka sincroniza cache após cada evento de escrita |

---

## Arquitetura

```
command/
  └── Recebe requisições HTTP de escrita
  └── Valida e persiste no banco de dados de escrita
  └── Invalida entradas do cache Redis (@CacheEvict)
  └── Publica eventos no Kafka (ex: ProductCreatedEvent, UserCreatedEvent)

         │
         │  Kafka Topic
         ▼

query/
  └── Consome eventos do Kafka
  └── Atualiza a projeção de leitura no banco de dados
  └── Sincroniza o cache Redis (put / evict por evento)
  └── Serve requisições HTTP de leitura
        └── Cache HIT  → retorna do Redis (sem tocar o MySQL)
        └── Cache MISS → consulta MySQL, armazena no Redis e retorna
```

---

## Tecnologias

| Tecnologia | Versão | Finalidade |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework principal |
| Spring Web | — | API REST |
| Spring Data JPA | — | Persistência |
| Spring Validation | — | Validação de DTOs |
| Spring Actuator | — | Health check e métricas |
| Spring Security | — | Autenticação e autorização |
| Spring OAuth2 Client | — | Login com Google |
| Spring Cache | — | Abstração de cache com `@Cacheable` / `@CacheEvict` |
| Spring Data Redis | — | Integração com Redis via Lettuce |
| Redis | 7.2 | Cache distribuído (TTL por cache, política LRU) |
| JJWT | — | Geração e validação de JWT |
| Apache Kafka | — | Event streaming (Command → Query) |
| MySQL | 8+ | Banco de dados relacional |
| Commons Pool2 | — | Pool de conexões Lettuce |
| Lombok | — | Redução de boilerplate |
| Jackson | — | Serialização JSON |
| Docker + Compose | — | Infraestrutura local |
| spring-dotenv | 3.0.0 | Variáveis de ambiente via `.env` |

---

## Pré-requisitos

Antes de iniciar, certifique-se de ter instalado:

- [Java 21+](https://adoptium.net/)
- [Maven 3.8+](https://maven.apache.org/)
- [Docker](https://www.docker.com/) e [Docker Compose](https://docs.docker.com/compose/)
- [Git](https://git-scm.com/)
- Uma conta no [Google Cloud Console](https://console.cloud.google.com) com credenciais OAuth2 configuradas

---

## Como Executar

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

### 3. Suba a infraestrutura com Docker

```bash
docker-compose up -d
```

Isso irá inicializar:
- MySQL (banco de dados)
- Apache Kafka + Zookeeper (broker de mensagens)
- Redis (cache distribuído)

### 4. Execute a aplicação

```bash
./mvnw spring-boot:run
```

A aplicação estará disponível em: `http://localhost:8080`

### 5. Verifique a saúde da aplicação

```bash
curl http://localhost:8080/actuator/health
```

### 6. Faça login com Google

Acesse no browser:
```
http://localhost:8080/oauth2/authorization/google
```

Após o login, o JWT será armazenado automaticamente em um cookie HttpOnly.

---

## Variáveis de Ambiente

Crie um arquivo `.env` na raiz do projeto com base no exemplo abaixo. **Nunca versione o `.env` real com credenciais.**

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
JWT_EXPIRATION_MS=86400000

# === ADMIN ===
ADMIN_EMAILS=seuemail@gmail.com

# === FRONTEND ===
FRONTEND_URL=http://localhost:3000

# === APLICACAO ===
SERVER_PORT=8080
```

> O arquivo `.env` já está incluído no `.gitignore`. Nunca o remova dessa lista.

> Para gerar um JWT secret seguro: `openssl rand -base64 32`

---

## Estrutura de Pacotes

```
src/main/java/com/project/cqrs/
│
├── config/                          # Configurações globais
│   ├── admin/                       # AdminConfig — resolução de role por email
│   ├── kafka/                       # KafkaProducerConfig, KafkaConsumerConfig
│   ├── redis/                       # RedisConfig, CacheService, CacheAdminController
│   └── security/                    # SecurityConfig — OAuth2, JWT filter e CORS
│
├── shared/                          # Compartilhado entre command e query
│   └── event/
│       ├── user/                    # UserEvent, UserCreatedEvent, UserUpdatedEvent, UserLogoutEvent
│       ├── category/                # CategoryCreateEvent, CategoryUpdateEvent, CategoryDeleteEvent
│       └── product/                 # ProductCreateEvent, ProductUpdateEvent, ProductDeleteEvent
│
├── command/                         # LADO DE ESCRITA (Write Side)
│   ├── auth/
│   │   ├── controller/              # AuthCommandController (POST /logout)
│   │   ├── infra/
│   │   │   ├── cookie/              # CookieTokenUtil
│   │   │   ├── kafka/               # UserEventProducer
│   │   │   └── security/            # JwtTokenService, JwtAuthFilter, OAuth2AuthSuccessHandler
│   │   ├── model/                   # UserCommandEntity, UserRole
│   │   ├── repository/              # UserCommandRepository
│   │   └── service/                 # CustomOAuth2UserService
│   │
│   ├── category/
│   │   ├── controller/              # Endpoints REST de escrita para Category
│   │   ├── dto/request/             # DTOs de entrada
│   │   ├── kafka/producer/          # CategoryEventProducer
│   │   ├── model/                   # Entidade JPA do lado de escrita
│   │   ├── repository/              # Repositório Spring Data JPA (escrita)
│   │   └── service/                 # Lógica de negócio, orquestração e @CacheEvict
│   │
│   └── product/
│       ├── controller/              # Endpoints REST de escrita para Product
│       ├── dto/request/             # DTOs de entrada
│       ├── kafka/producer/          # ProductEventProducer
│       ├── model/                   # Entidade JPA do lado de escrita
│       ├── repository/              # Repositório Spring Data JPA (escrita)
│       └── service/                 # Lógica de negócio, orquestração e @CacheEvict
│
└── query/                           # LADO DE LEITURA (Read Side)
    ├── auth/
    │   ├── controller/              # AuthQueryController (GET /me)
    │   ├── dto/                     # UserQueryDTO
    │   ├── kafka/consumer/          # UserEventConsumer
    │   ├── model/                   # UserQueryEntity
    │   └── repository/              # UserQueryRepository
    │
    ├── category/
    │   ├── controller/              # Endpoints REST de leitura para Category
    │   ├── dto/response/            # DTOs de saída
    │   ├── kafka/consumer/          # CategoryEventConsumer (sincroniza cache)
    │   ├── model/                   # Modelo de leitura
    │   ├── repository/              # Repositório Spring Data JPA (leitura)
    │   └── service/                 # Lógica de consulta, projeção e @Cacheable
    │
    └── product/
        ├── controller/              # Endpoints REST de leitura para Product
        ├── dto/response/            # DTOs de saída (PageResponseDTO, ProductQueryDTO)
        ├── kafka/consumer/          # ProductEventConsumer (sincroniza cache Redis)
        ├── model/                   # Modelo de leitura
        ├── repository/              # Repositório Spring Data JPA (leitura)
        └── service/                 # Lógica de consulta, projeção e @Cacheable
```

---

## Fluxo de Dados

### Autenticação

```
GET /oauth2/authorization/google
        │
        ▼
Google OAuth2 (login e consentimento)
        │
        ▼
CustomOAuth2UserService.loadUser()
  ├── Busca ou cria o usuário no banco (Command DB)
  ├── Atribui role ADMIN ou USER com base no email configurado
  └── Publica UserCreatedEvent ou UserUpdatedEvent no Kafka
        │
        ▼
OAuth2AuthSuccessHandler
  ├── Gera JWT com userId, email e role
  ├── Armazena JWT em cookie HttpOnly
  └── Redireciona para o frontend
        │
        ▼
Kafka Topic: user-created / user-updated
        │
        ▼
UserEventConsumer
  └── Sincroniza UserQueryEntity no banco de leitura (Query DB)
```

### Escrita (Command Side)

```
POST /api/v1/command/products
        │
        ▼
ProductCommandController
        │
        ▼
ProductCommandService
  ├── Valida o DTO
  ├── Persiste no banco de dados (Write DB)
  ├── Invalida entradas do cache Redis (@CacheEvict)
  └── Publica ProductCreatedEvent no Kafka
```

### Leitura (Query Side)

```
GET /api/v1/query/products
        │
        ▼
ProductQueryController
        │
        ▼
ProductQueryService (@Cacheable)
  ├── Cache HIT  → retorna do Redis (MySQL não é consultado)
  └── Cache MISS → consulta MySQL, armazena no Redis e retorna

Kafka Topic: product-created / product-updated / product-deleted
        │
        ▼
ProductEventConsumer
  ├── Atualiza a projeção no banco de dados (Read DB)
  └── Sincroniza o Redis:
        ├── CREATE → put detalhe no cache + evict lista
        ├── UPDATE → atualiza detalhe no cache + evict lista
        └── DELETE → evict detalhe + evict lista
```

---

## Cache Redis

### Estratégia

O projeto adota **cache-aside** no lado de leitura combinado com **invalidação orientada a eventos** via Kafka.

| Cache | Chave | TTL | Invalidado por |
|---|---|---|---|
| `products` | `page-{n}-size-{s}` | 5 min | create, update, delete |
| `product-detail` | `{id}` | 10 min | update, delete do produto |
| `categories` | — | 30 min | create, update, delete de categoria |

### Fluxo de cache

```
GET /api/v1/query/products/{id}
        │
        ▼
 Redis HIT? ──── YES ──→ retorna DTO (sem tocar o MySQL)
        │
       NO
        │
        ▼
  Consulta MySQL
        │
        ▼
  Armazena no Redis com TTL
        │
        ▼
  Retorna DTO
```

### Sincronização via Kafka

```
ProductCommandService          Kafka              ProductEventConsumer
        │                        │                        │
  POST/PUT/DELETE                │                        │
        │──── @CacheEvict ───▶   │                        │
        │──── publica evento ──▶ │                        │
                                 │ ──── consome ────────▶ │
                                 │                  persiste no MySQL
                                 │                  sincroniza Redis
                                 │                  (put / evict)
```

### Endpoints administrativos de cache

| Método | Endpoint | Descrição |
|---|---|---|
| `DELETE` | `/admin/cache/products` | Invalida toda a lista paginada |
| `DELETE` | `/admin/cache/product-detail/{id}` | Invalida o detalhe de um produto |
| `DELETE` | `/admin/cache/all` | Limpa todos os caches de produto |
| `GET` | `/admin/cache/stats/{id}` | Retorna status HIT/MISS e TTL restante |
| `GET` | `/admin/cache/keys` | Lista chaves em cache (uso diagnóstico) |

> Em produção, proteja esses endpoints com `@PreAuthorize("hasRole('ADMIN')")`.

---

## Endpoints

### Autenticação

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/oauth2/authorization/google` | Público | Inicia o fluxo OAuth2 com Google |
| `GET` | `/api/v1/query/auth/me` | Autenticado | Retorna dados do usuário logado |
| `POST` | `/api/v1/command/auth/logout` | Autenticado | Encerra a sessão e limpa o cookie |

### Category — Command (Escrita) — `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/command/categories` | Cria uma nova categoria |
| `PUT` | `/api/v1/command/categories/{id}` | Atualiza uma categoria |
| `DELETE` | `/api/v1/command/categories/{id}` | Remove uma categoria |

### Category — Query (Leitura) — `Autenticado`

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/v1/query/categories` | Lista todas as categorias |
| `GET` | `/api/v1/query/categories/{id}` | Busca categoria por ID |

### Product — Command (Escrita) — `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/v1/command/products` | Cria um novo produto |
| `PUT` | `/api/v1/command/products/{id}` | Atualiza um produto |
| `DELETE` | `/api/v1/command/products/{id}` | Remove um produto |

### Product — Query (Leitura) — `Autenticado`

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/v1/query/products` | Lista todos os produtos (paginado) |
| `GET` | `/api/v1/query/products/{id}` | Busca produto por ID |

### Cache — Admin — `ADMIN`

| Método | Endpoint | Descrição |
|---|---|---|
| `DELETE` | `/admin/cache/products` | Invalida cache da lista de produtos |
| `DELETE` | `/admin/cache/product-detail/{id}` | Invalida cache do detalhe |
| `DELETE` | `/admin/cache/all` | Limpa todos os caches de produto |
| `GET` | `/admin/cache/stats/{id}` | Status HIT/MISS e TTL de um produto |
| `GET` | `/admin/cache/keys` | Lista chaves em cache (diagnóstico) |

---

<div align="center">

Feito com Java por [Guilherme D'Antoni](https://github.com/dantonigui)

</div>