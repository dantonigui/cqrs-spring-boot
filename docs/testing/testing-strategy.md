# Estratégia de testes

## Comandos

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Linux/macOS: `./mvnw`. Surefire executa `*Test`; Failsafe executa `*IT` no `verify`. Mesmo com `-Dtest=...`, Maven compila os arquivos de teste antes de selecionar classes.

## Cobertura

| Tipo | Escopo |
|---|---|
| Unitário | Estoque, estados, pagamento, erros e templates com dependências simuladas |
| Segurança | JWT, cookies, filtro, logout e papéis |
| Redis real | Sessões e disputa entre oito chamadas pelo mesmo refresh |
| Integração | Pedidos, pagamentos e eventos com dois MySQL, Kafka e Redis |

`RefreshTokenServiceTest` usa Redis 7.2 via Testcontainers e pode ser ignorado se Docker não estiver disponível. Assim, `test` não significa execução completa sem infraestrutura.

`AbstractIntegrationTest` inicia dois MySQL 8.0, Kafka e Redis para classes que o estendem, em sequência para reduzir a carga de inicialização no Docker Desktop. Não usa bancos locais do `.env`; e-mail e o gateway Mercado Pago são simulados nessa base. Os bancos, a outbox, Kafka e Redis são reais. Esses testes não substituem login real Google nem pagamento de teste no provedor.

## Situação verificada

Em 18 de outubro de 2026, `mvn verify` passou com o POM normal: 142 testes Surefire e 11 testes Failsafe, sem falhas, erros ou testes ignorados. Redis, dois MySQL e Kafka executaram em containers isolados.

A integração confirmou migrações em bases novas, idempotência concorrente e rollback, reserva concorrente sem venda acima do estoque, expiração com devolução única, isolamento do cache por usuário, aprovação presencial e projeção, cancelamento/estorno tardio e segurança HTTP/CSRF. Uma primeira tentativa perdeu a conexão com o Docker Desktop; a execução aprovada usou inicialização sequencial dos serviços.

Uma execução adicional de `PaymentLifecycleIT` passou com três cenários, incluindo a rejeição de dois pagamentos ativos pelo índice único MySQL. O conjunto final de relatórios Failsafe registra 12 testes de integração aprovados.

## Build

`package` inclui testes. `-DskipTests` ignora execução mas ainda pode compilá-los; `-Dmaven.test.skip=true` ignora compilação e execução. O Dockerfile usa a segunda opção. Imagem construída não prova aprovação da suíte.

Em 18 de outubro de 2026, `docker build -t cqrs:local .` também passou usando o Dockerfile final com Java 21 e cache Maven do BuildKit.

O Compose foi executado com projeto, credenciais, portas e volumes descartáveis separados. Os seis serviços ficaram saudáveis; a API respondeu `UP`, o endpoint CSRF retornou token e uma rota administrativa recusou acesso anônimo com 401. O teste identificou e corrigiu o argumento de conexão do healthcheck ZooKeeper (`localhost:2181`). Os bancos existentes do usuário não foram utilizados.

Ao mudar autenticação, verifique revogação, expiração, CSRF e concorrência. Para estoque, confira rollback e disputa. Para eventos, confira duplicação, ordem e idempotência transacional. Use [validação manual](manual-flow.md) para integrações reais.
