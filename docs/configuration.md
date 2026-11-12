# Configuração

## Fontes

Copie `.env.example` para `.env` na raiz. `spring-dotenv` fornece variáveis à execução local; Compose lê `.env` para interpolação e o passa à API no perfil `app`. Não versione `.env`. Os mapeamentos efetivos estão em `application.properties` e `docker-compose.yml`.

## Variáveis

| Grupo | Variáveis | Uso |
|---|---|---|
| Command | `DB_COMMAND_URL_SQL`, `DB_COMMAND_USER`, `DB_COMMAND_PASSWORD` | Banco de escrita |
| Query | `DB_QUERY_URL_SQL`, `DB_QUERY_USER`, `DB_QUERY_PASSWORD` | Banco de leitura |
| Inicialização | `DB_ROOT_PASSWORD` | Criação dos bancos no Compose |
| Pools | `DB_COMMAND_POOL_SIZE`, `DB_QUERY_POOL_SIZE` | Máximo por datasource; padrão 20 |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` | Cache, OAuth2 e sessões |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS` | Endereço do broker |
| Google | `GOOGLE_ID`, `GOOGLE_SECRET`, `GOOGLE_URL` | Cliente e callback OAuth2 |
| Administração | `MY_EMAIL_ADMIN` | E-mails separados por vírgula |
| JWT | `JWT_SECRET`, `JWT_EXPIRATION_MS` | Chave HMAC Base64 e TTL em ms |
| Cookies | `COOKIE_NAME`, `COOKIE_MAX_AGE`, `COOKIE_SECURE` | Nome, TTL em segundos e HTTPS |
| URLs | `APP_FRONTEND_URL`, `APP_BASE_URL` | Origem CORS/frontend e URL pública da API |
| Mercado Pago | `MP_ACCESS_TOKEN`, `MP_PUBLIC_KEY`, `MP_WEBHOOK_SECRET` | Credenciais e assinatura |
| SMTP | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Servidor autenticado com STARTTLS |
| Remetente | `MAIL_FROM`, `MAIL_FROM_NAME` | Identificação dos e-mails |
| Outbox | `OUTBOX_POLL_MS` | Dispatcher; padrão 1000 ms |
| Reservas | `ORDER_RESERVATION_MINUTES`, `ORDER_EXPIRATION_POLL_MS` | Padrões 30 min e 60000 ms |

Refresh: `app.refresh-token.expiration-ms=604800000`, cookie `refresh_token`; altere por propriedades Spring. No exemplo, `COOKIE_MAX_AGE=900` e `JWT_EXPIRATION_MS=900000` representam 15 minutos.

## Chave JWT

Gere pelo menos 32 bytes aleatórios e codifique em Base64. Exemplo PowerShell; cole o resultado em `JWT_SECRET`, sem versioná-lo:

```powershell
$jwtBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$jwtRng.Dispose()
[Convert]::ToBase64String($jwtBytes)
```

## Google e papéis

Configure um cliente OAuth2 web com callback exatamente igual a `GOOGLE_URL`, normalmente `http://localhost:8080/login/oauth2/code/google`. O backend solicita `email,profile` e identifica a conta pelo `sub`.

`MY_EMAIL_ADMIN` atribui ADMIN apenas no cadastro inicial, comparando e-mail sem distinguir maiúsculas. Alterar a lista não muda papéis de usuários existentes. Não há endpoint de gestão de papéis.

## Rede

| Dependência | API no host | API no Compose |
|---|---|---|
| Command | `localhost:3307` | `mysql-command:3306` |
| Query | `localhost:3308` | `mysql-query:3306` |
| Kafka | `localhost:9092` | `kafka:29092` |
| Redis | `localhost:6379` | `redis:6379` |

O serviço `app` sobrescreve esses endereços. `APP_PORT`, `DB_COMMAND_PORT`, `DB_QUERY_PORT` e `KAFKA_PORT` alteram portas publicadas no host. Atualize URLs/callbacks relacionados quando mudar portas. A API permanece em 8080 dentro do contêiner.

## Integrações externas

Use Mercado Pago de teste no desenvolvimento. O webhook precisa de URL pública alcançável pelo provedor; `localhost` não atende esse acesso externo.

O Compose não inclui SMTP. O exemplo aponta para `localhost:1025`, mas requer servidor fornecido à parte e compatível com autenticação/STARTTLS ou configuração diferente. No contêiner, `localhost` é o próprio contêiner: ajuste `MAIL_HOST`. A saúde de e-mail está desabilitada no Actuator.
