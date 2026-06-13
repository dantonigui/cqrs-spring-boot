# CQRS com Spring Boot, Kafka, MySQL e Redis

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.5-brightgreen?style=flat-square&logo=springboot)
![Kafka](https://img.shields.io/badge/Apache_Kafka-Event_Streaming-black?style=flat-square&logo=apachekafka)
![Redis](https://img.shields.io/badge/Redis-7.2-red?style=flat-square&logo=redis)
![MySQL](https://img.shields.io/badge/MySQL-8+-blue?style=flat-square&logo=mysql)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square&logo=docker)
![Status](https://img.shields.io/badge/Status-Em_desenvolvimento-yellow?style=flat-square)

> Implementação de referência do padrão **CQRS (Command Query Responsibility Segregation)** com separação real de modelos de escrita e leitura, event streaming via Apache Kafka, cache distribuído com Redis e autenticação OAuth2 + JWT.

---

## Motivação

Aplicações que crescem rapidamente enfrentam um problema clássico: **operações de leitura e escrita competem pelos mesmos recursos**. Um endpoint de busca paginada com filtros complexos não deveria disputar conexões de banco com operações transacionais de escrita.

Este projeto demonstra como o padrão CQRS resolve esse problema na prática — não como exercício teórico, mas com uma implementação funcional que inclui consistência eventual via Kafka, cache inteligente com Redis e autenticação segura com OAuth2 + JWT em cookie HttpOnly.

**Cada decisão tecnológica aqui tem um motivo.** Esse motivo está documentado abaixo.

---

## Performance

Benchmark realizado com [`hey`](https://github.com/rakyll/hey) no endpoint `GET /api/v1/query/products/{id}`:

| Cenário | Latência média | Observação |
|---|---|---|
| **Sem Redis** (direto no MySQL) | 45ms | Cold path, todo request vai ao banco |
| **Com Redis** (cache HIT) | 22ms | **51% mais rápido**, sem tocar o MySQL |
| **1000 req / 50 concorrentes** (em breve) | — | *Resultados sendo coletados* |

> Novos benchmarks com carga real chegando em breve.

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

Em sistemas de leitura intensa, o modelo único de dados sofre com consultas complexas (joins, filtros, paginação) rodando no mesmo banco que recebe operações de escrita transacional. CQRS permite que cada lado evolua independentemente — o modelo de leitura pode ser desnormalizado para performance sem impactar a integridade do lado de escrita.

**Alternativa considerada:** CRUD simples com um único repositório. Descartada porque não demonstra separação de responsabilidades nem escalabilidade independente.

### Por que Kafka e não RabbitMQ?

| Critério | Kafka | RabbitMQ |
|---|---|---|
| **Retenção de eventos** | Sim (log persistente) | Não (mensagem some após consumo) |
| **Replay de eventos** | Sim | Não |
| **Modelo** | Pull (consumer controla) | Push (broker controla) |
| **Caso de uso ideal** | Event sourcing, CQRS | Task queues, RPC |

Para CQRS, o Kafka é a escolha natural: se o consumer de leitura ficar fora do ar, ele pode **replay** os eventos perdidos ao voltar. Com RabbitMQ, os eventos seriam perdidos.

### Por que Redis como cache?

O lado de leitura do CQRS é otimizado para consultas frequentes. Sem cache, cada `GET /products/{id}` bate no MySQL — desnecessário para dados que mudam raramente. O Redis com TTL por cache (`product-detail`: 10min, `products`: 5min) elimina a maioria das queries de leitura sem sacrificar consistência.

**A estratégia adotada é cache-aside + invalidação orientada a eventos**: o Command Side invalida o cache ao escrever, e o Consumer Kafka sincroniza após processar o evento.

### Por que OAuth2 + JWT em cookie HttpOnly?

JWT em `localStorage` é vulnerável a ataques XSS. Armazenar o token em cookie HttpOnly (inacessível via JavaScript) elimina esse vetor de ataque, mantendo a autenticação stateless.

---

## Tecnologias

| Tecnologia | Versão | Finalidade |
|---|---|---|
| Java | 21 | Linguagem principal |
| Spring Boot | 3.4.5 | Framework principal |
| Spring Security + OAuth2 | — | Autenticação via Google + JWT |
| Spring Data JPA | — | Persistência |
| Spring Cache + Redis | — | Cache distribuído (`@Cacheable`, `@CacheEvict`) |
| Apache Kafka | — | Event streaming (Command → Query) |
| MySQL | 8+ | Banco de dados relacional |
| Redis | 7.2 | Cache distribuído com TTL e política LRU |
| JJWT | — | Geração e validação de JWT |
| Docker + Compose | — | Infraestrutura local em um comando |
| Lombok | — | Redução de boilerplate |

---

## Como Executar

### Pré-requisitos

- [Java 21+](https://adoptium.net/)
- [Docker](https://www.docker.com/) e [Docker Compose](https://docs.docker.com/compose/)
- Credenciais OAuth2 do [Google Cloud Console](https://console.cloud.google.com)

### 1. Clone o repositório

```bash
git clone https://github.com/dantonigui/cqrs-spring-boot.git
cd cqrs-spring-boot
```

### 2. Configure as variáveis de ambiente

```bash
cp .env.example .env
# Edite o .env com suas configurações (veja seção abaixo)
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

### 6. Verifique a saúde

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
JWT_EXPIRATION_MS=86400000

# === ADMIN ===
ADMIN_EMAILS=seuemail@gmail.com

# === FRONTEND ===
FRONTEND_URL=http://localhost:3000

# === APLICACAO ===
SERVER_PORT=8080
```

> Gere um JWT secret seguro com: `openssl rand -base64 32`
> O `.env` está no `.gitignore`. Nunca o remova.

---

## Estrutura de Pacotes

```
src/main/java/com/project/cqrs/
│
├── config/                    # Configurações globais
│   ├── admin/                 # Resolução de role por email
│   ├── kafka/                 # KafkaProducerConfig, KafkaConsumerConfig
│   ├── redis/                 # RedisConfig, CacheService, CacheAdminController
│   └── security/              # SecurityConfig — OAuth2, JWT filter, CORS
│
├── shared/                    # Eventos compartilhados entre Command e Query
│   └── event/
│       ├── user/              # UserCreatedEvent, UserUpdatedEvent, UserLogoutEvent
│       ├── category/          # CategoryCreateEvent, CategoryUpdateEvent, CategoryDeleteEvent
│       └── product/           # ProductCreateEvent, ProductUpdateEvent, ProductDeleteEvent
│
├── command/                   # ✍️ WRITE SIDE
│   ├── auth/                  # OAuth2, JWT, logout
│   ├── category/              # CRUD de escrita para Category
│   └── product/               # CRUD de escrita para Product
│
└── query/                     # 📖 READ SIDE
    ├── auth/                  # GET /me, consumer de eventos de usuário
    ├── category/              # Consultas de Category com cache
    └── product/               # Consultas de Product com cache paginado
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

### Autenticação

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
  ├── Gera JWT (userId, email, role)
  ├── Armazena em cookie HttpOnly
  └── Redireciona para o frontend
        │
        ▼
UserEventConsumer → sincroniza UserQueryEntity no Read DB
```

---

## Cache Redis

| Cache | Chave | TTL | Invalidado por |
|---|---|---|---|
| `products` | `page-{n}-size-{s}` | 5 min | create, update, delete |
| `product-detail` | `{id}` | 10 min | update, delete do produto |
| `categories` | — | 30 min | create, update, delete de categoria |

### Endpoints administrativos

| Método | Endpoint | Descrição |
|---|---|---|
| `DELETE` | `/admin/cache/products` | Invalida cache da lista paginada |
| `DELETE` | `/admin/cache/product-detail/{id}` | Invalida cache do detalhe |
| `DELETE` | `/admin/cache/all` | Limpa todos os caches |
| `GET` | `/admin/cache/stats/{id}` | Status HIT/MISS e TTL restante |
| `GET` | `/admin/cache/keys` | Lista chaves em cache (diagnóstico) |

---

## Endpoints

### Autenticação

| Método | Endpoint | Auth | Descrição |
|---|---|---|---|
| `GET` | `/oauth2/authorization/google` | Público | Inicia fluxo OAuth2 |
| `GET` | `/api/v1/query/auth/me` | Autenticado | Dados do usuário logado |
| `POST` | `/api/v1/command/auth/logout` | Autenticado | Encerra sessão |

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

---

##  Roadmap

- [ ] Deploy em ambiente cloud (Railway + Upstash)
- [ ] Benchmarks com 1000+ requisições e concorrência real
- [ ] Testes unitários e de integração
- [ ] Frontend React consumindo a API

---

<div align="center">

Feito com ☕ por [Guilherme D'Antoni](https://github.com/dantonigui)

</div>