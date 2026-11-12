# Ambiente local e Docker

## Iniciar

Na raiz, copie `.env.example` para `.env` e preencha [as variáveis](../configuration.md). Instale Docker com Compose v2 e JDK 21 caso execute a API no host.

```powershell
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

Infraestrutura e API juntas:

```powershell
docker compose --profile app up --build -d --wait
```

O build usa cache de dependências Maven do BuildKit. A imagem final contém o JRE 21 e executa com usuário sem privilégios de root. O build da imagem ignora testes; execute `mvn verify` separadamente.

Não execute simultaneamente API no host e no contêiner usando a mesma porta. Frontend e SMTP são externos. Flyway aplica as migrações dos dois bancos ao iniciar; Hibernate valida o schema.

## Serviços e persistência

O Compose usa dois MySQL 8.0, Kafka/ZooKeeper 7.4.0 e Redis 7.2. Portas publicadas são restritas a `127.0.0.1`. Há volumes para os bancos, Kafka, ZooKeeper e Redis.

Redis usa senha, AOF, limite de 256 MB e política `noeviction`: falta de memória pode impedir escritas. Serviços têm healthchecks e rotação de logs.

```powershell
docker compose ps
docker compose logs --tail 100 app
docker compose logs --tail 100 mysql-command mysql-query kafka redis
docker compose --profile app down
```

`down` preserva volumes. `down -v` apaga os volumes e dados locais; use somente para uma recriação intencional.

## Bancos existentes

Variáveis `MYSQL_*` inicializam credenciais apenas em diretório vazio. Alterar `.env` não muda senhas de um volume existente; atualize o usuário no banco ou use as credenciais originais.

Não há baseline Flyway automático. Um banco não vazio sem histórico pode impedir inicialização. Faça backup e confira o schema conforme [migrações](../database/database-conventions.md).

## Diagnóstico

| Sintoma | Conferir |
|---|---|
| Variável ausente | `.env` na raiz e comparação com `.env.example` |
| Porta ocupada | Serviços ativos, portas publicadas e URLs relacionadas |
| Banco indisponível | URL, credenciais, healthcheck e host versus contêiner |
| Login falhando | Callback, Redis, Secure/SameSite e origem CORS |
| Consulta atrasada | Outbox, Kafka, consumers e DLQ antes do cache |
| E-mail ausente | SMTP; saúde não valida envio |
| Testes não compilam | [Estratégia de testes](testing-strategy.md) |

Não compartilhe `docker compose config` sem revisão: a saída pode expandir segredos.

## Validação realizada

Em 18 de outubro de 2026, a configuração e o build passaram, e o Compose completo iniciou com os seis serviços saudáveis em um projeto descartável. A API respondeu `UP` em `/actuator/health`. Essa validação usa bases novas; para volumes existentes, siga o procedimento de migração acima.
