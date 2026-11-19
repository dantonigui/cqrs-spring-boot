# E-commerce CQRS Backend

A Java backend for an online store with product catalog, inventory, orders, checkout, and payment workflows. The frontend is maintained separately.

## Architecture overview

The command side validates business operations and writes to the source database. Business changes and events are recorded together through an outbox. A dispatcher publishes events to Kafka, and consumers update query projections, invalidate caches, and trigger notifications. Read models are eventually consistent and may take time to reflect a successful command.

```mermaid
flowchart LR
  UI[Frontend] --> API[Spring Boot API]
  API --> C[(MySQL command + outbox)]
  C --> D[Dispatcher]
  D --> K[Kafka]
  K --> P[Projections and notifications]
  P --> Q[(MySQL query)]
  API --> Q
  API --> R[(Redis cache and sessions)]
  API --> G[Google OAuth 2.0]
  API --> MP[Mercado Pago]
  P --> SMTP[SMTP]
```

## Features

- Google OAuth 2.0 login with JWTs in HttpOnly cookies, rotating refresh tokens, and revocable Redis-backed sessions.
- User and administrator roles, CSRF protection, and order ownership checks.
- Product and category management, inventory restocking, and transactional stock reservations.
- PIX, card, and in-person checkout flows, including payment webhooks and applicable refunds.
- Automatic expiration of unpaid reservations and payment/cancellation email notifications.
- Paginated queries, caching, and administrative dead-letter queue and cache operations.

## Technology

- Java 21 and Spring Boot 3
- MySQL command and query databases
- Kafka and transactional outbox
- Redis for cache and sessions
- Google OAuth 2.0, Mercado Pago, and SMTP
- Docker Compose, Maven Wrapper, Swagger/OpenAPI

## Run locally

Prerequisites: Docker with Compose v2. To run the API on the host, install JDK 21 and use the Maven Wrapper.

```bash
cp .env.example .env
# Set values as described in docs/configuration.md.
docker compose up -d --wait
./mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

To run the API in Docker as well:

```bash
docker compose --profile app up --build -d --wait
```

Set your own credentials for the databases, Redis, Google, JWT, Mercado Pago, and SMTP. The Compose setup does not include a frontend or SMTP server.

| Service | Local address |
|---|---|
| API | `http://localhost:8080` |
| Swagger UI / OpenAPI | `http://localhost:8080/swagger` / `http://localhost:8080/api-docs` |
| Health check | `http://localhost:8080/actuator/health` |
| Google login | `http://localhost:8080/oauth2/authorization/google` |
| MySQL command / query | `localhost:3307` / `localhost:3308` |
| Kafka / Redis | `localhost:9092` / `localhost:6379` |

## Frontend integration

Use the origin configured in `APP_FRONTEND_URL` and send requests with `credentials: 'include'`. Begin login by navigating to the Google authorization route. After login, request `/api/v1/query/auth/me`. For POST, PUT, and DELETE requests, retrieve `/api/v1/csrf` and send the returned token in the indicated header.

Each JWT contains a session identifier that is checked in Redis. Logout revokes the current session; `logout-all` revokes all sessions for the user. The API does not use an HTTP session to authenticate requests. See the [frontend integration guide](docs/api/frontend-integration.md).

## Project structure

The Java packages are located at `src/main/java/com/project/cqrs/`.

| Path | Responsibility |
|---|---|
| `command/` | Authentication, catalog, inventory, orders, payments, webhooks, and outbox |
| `query/` | Read projections, consumers, and queries |
| `admin/` | Cache, dead-letter queue, and idempotency operations |
| `notification/` | Email templates and delivery |
| `config/` | Security, databases, Kafka, Redis, rate limits, and errors |
| `shared/` | Events, enums, topics, and DTOs |
| `src/main/resources/db/migration/` | Database migrations |
| `src/test/java/` | Unit, security, and integration tests |
| `docs/` | Technical and operational documentation |

## Build and tests

```bash
./mvnw test
./mvnw verify
./mvnw package
```

The full integration suite requires Docker. See the [testing strategy](docs/testing/testing-strategy.md).

## Documentation

Start with the [documentation index](docs/README.md).

- [Backend architecture](docs/architecture/backend-architecture.md)
- [Configuration](docs/configuration.md)
- [API reference](docs/api/current-api-reference.md)
- [Business rules](docs/business-rules/business-rules.md)
- [Database model](docs/database/data-model.md)
- [Operations](docs/deployment/operations.md)

## Current limitations

Events are delivered at least once. There is no distributed transaction across MySQL, Kafka, Redis, Mercado Pago, and SMTP. Email delivery may be repeated after failures. Redis is required for authentication, the session scripts assume standalone Redis, and rate limiting is local to each application instance.
