# CQRS com Spring Boot, Kafka e MySQL

> Projeto de estudo e referência sobre o padrão **CQRS (Command Query Responsibility Segregation)** implementado com Spring Boot 3, Apache Kafka e MySQL — separando claramente as responsabilidades de escrita (Command) e leitura (Query).

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
- [Endpoints](#endpoints)
- [Contribuindo](#contribuindo)
- [Licença](#licença)

---

## Sobre o Projeto

Este projeto tem como objetivo demonstrar de forma prática e didática a implementação do padrão **CQRS** em uma aplicação Java moderna. As entidades do domínio são `Product` (Produto) e `Category` (Categoria).

O lado de **Command** é responsável por receber e processar operações de escrita (criar, atualizar, deletar), publicando eventos no **Apache Kafka**. O lado de **Query** consome esses eventos e mantém sua própria visão dos dados, otimizada para leitura.

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
            ┌──────────▼──────────┐           │
            │     Write Model     │           │
            │  (MySQL - Command)  │           │
            └──────────┬──────────┘           │
                       │                      │
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

---

## Arquitetura

```
command/
  └── Recebe requisições HTTP de escrita
  └── Valida e persiste no banco de dados de escrita
  └── Publica eventos no Kafka (ex: ProductCreatedEvent)

         │
         │  Kafka Topic
         ▼

query/
  └── Consome eventos do Kafka
  └── Atualiza a projeção de leitura no banco de dados
  └── Serve requisições HTTP de leitura
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
| Apache Kafka | — | Event streaming (Command → Query) |
| MySQL | 8+ | Banco de dados relacional |
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

### 4. Execute a aplicação

```bash
./mvnw spring-boot:run
```

A aplicação estará disponível em: `http://localhost:8080`

### 5. Verifique a saúde da aplicação

```bash
curl http://localhost:8080/actuator/health
```

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

# === APLICACAO ===
SERVER_PORT=8080
```

> O arquivo `.env` já está incluído no `.gitignore`. Nunca o remova dessa lista.

---

## Estrutura de Pacotes

```
src/main/java/com/project/cqrs/
│
├── config/                          # Configurações globais (Kafka, beans, etc.)
│
├── command/                         # LADO DE ESCRITA (Write Side)
│   ├── category/
│   │   ├── controller/              # Endpoints REST de escrita para Category
│   │   ├── dto/request/             # DTOs de entrada (ex: CreateCategoryRequest)
│   │   ├── event/                   # Eventos publicados (ex: CategoryCreatedEvent)
│   │   ├── kafka/producer/          # Produtores Kafka para Category
│   │   ├── model/                   # Entidade JPA do lado de escrita
│   │   ├── repository/              # Repositório Spring Data JPA (escrita)
│   │   └── service/                 # Lógica de negócio e orquestração
│   │
│   └── product/
│       ├── controller/              # Endpoints REST de escrita para Product
│       ├── dto/request/             # DTOs de entrada (ex: CreateProductRequest)
│       ├── event/                   # Eventos publicados (ex: ProductCreatedEvent)
│       ├── kafka/producer/          # Produtores Kafka para Product
│       ├── model/                   # Entidade JPA do lado de escrita
│       ├── repository/              # Repositório Spring Data JPA (escrita)
│       └── service/                 # Lógica de negócio e orquestração
│
└── query/                           # LADO DE LEITURA (Read Side)
    ├── category/
    │   ├── controller/              # Endpoints REST de leitura para Category
    │   ├── dto/response/            # DTOs de saída (ex: CategoryResponse)
    │   ├── kafka/consumer/          # Consumidores Kafka — atualizam a projeção
    │   ├── model/                   # Modelo de leitura (pode diferir do command)
    │   ├── repository/              # Repositório Spring Data JPA (leitura)
    │   └── service/                 # Lógica de consulta e projeção
    │
    └── product/
        ├── controller/              # Endpoints REST de leitura para Product
        ├── dto/response/            # DTOs de saída (ex: ProductResponse)
        ├── kafka/consumer/          # Consumidores Kafka — atualizam a projeção
        ├── model/                   # Modelo de leitura
        ├── repository/              # Repositório Spring Data JPA (leitura)
        └── service/                 # Lógica de consulta e projeção
```

---

## Fluxo de Dados

### Escrita (Command Side)

```
POST /api/command/products
        │
        ▼
ProductCommandController
        │
        ▼
ProductCommandService
  ├── Valida o DTO
  ├── Persiste no banco de dados (Write DB)
  └── Publica ProductCreatedEvent no Kafka
```

### Leitura (Query Side)

```
Kafka Topic: product-events
        │
        ▼
ProductKafkaConsumer
        │
        ▼
ProductQueryService
  └── Atualiza a projeção no banco de dados (Read DB)

GET /api/query/products
        │
        ▼
ProductQueryController
        │
        ▼
ProductQueryService
  └── Consulta o banco de dados de leitura e retorna o DTO
```

---

## Endpoints

> Os endpoints abaixo seguem a convenção adotada no projeto. Ajuste conforme necessário.

### Category — Command (Escrita)

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/command/categories` | Cria uma nova categoria |
| `PUT` | `/api/command/categories/{id}` | Atualiza uma categoria |
| `DELETE` | `/api/command/categories/{id}` | Remove uma categoria |

### Category — Query (Leitura)

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/query/categories` | Lista todas as categorias |
| `GET` | `/api/query/categories/{id}` | Busca categoria por ID |

### Product — Command (Escrita)

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/command/products` | Cria um novo produto |
| `PUT` | `/api/command/products/{id}` | Atualiza um produto |
| `DELETE` | `/api/command/products/{id}` | Remove um produto |

### Product — Query (Leitura)

| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/query/products` | Lista todos os produtos |
| `GET` | `/api/query/products/{id}` | Busca produto por ID |

---

<div align="center">

Feito com Java por [Guilherme D'Antoni](https://github.com/dantonigui)

</div>
