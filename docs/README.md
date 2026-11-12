# Documentação do projeto

Referência do backend implementado. Os exemplos usam dados fictícios; credenciais ficam no ambiente de execução.

## Primeiro acesso

Para estudar os conceitos antes de operar o sistema:

- [Manual de conceitos: Redis, Kafka, DLQ, idempotência e outros termos](learning/manual-de-conceitos.md).
- [Fluxo geral do projeto, com exemplo de uma compra](learning/fluxo-geral.md).
- [Fluxos de 16 estratégias, incluindo caminhos de falha](learning/fluxos-das-estrategias.md).

1. [Visão geral](../README.md).
2. [Configuração e credenciais](configuration.md).
3. [Docker local](testing/docker-local.md).
4. [Login e permissões](business-rules/access-control.md).
5. [Integração do frontend](api/frontend-integration.md).
6. [Validação manual](testing/manual-flow.md).

## Arquitetura e dados

- [Arquitetura, transações e caches](architecture/backend-architecture.md).
- [ADR-001: CQRS, outbox e idempotência](architecture/adr/ADR-001-cqrs-outbox.md).
- [Eventos e consumers](architecture/events.md).
- [Modelo dos bancos e Redis](database/data-model.md).
- [Convenções e migrações](database/database-conventions.md).

## API e negócio

- [Regras de negócio consolidadas](business-rules/business-rules.md).
- [Casos de uso, atores e alternativas](use-cases/README.md).
- [Endpoints e payloads](api/current-api-reference.md).
- [Convenções HTTP e paginação](api/api-conventions.md).
- [Erros](api/error-catalog.md).
- [Autenticação](business-rules/access-control.md).
- [Pedidos e estoque](business-rules/orders-and-stock.md).
- [Pagamentos e cancelamento](business-rules/payments.md).

## Desenvolvimento e operação

- [Segurança](security/security.md).
- [Estratégia de testes](testing/testing-strategy.md).
- [Monitoramento e recuperação](deployment/operations.md).

## Manutenção

Consulte o [registro das 17 correções](review.md) e os [resultados dos testes](testing/testing-strategy.md).

Atualize contrato e roteiro manual ao mudar endpoints; regras e testes ao mudar estados ou autorização; configuração e operação ao mudar infraestrutura. Migrações aplicadas são imutáveis. Não descreva planejamento como funcionalidade disponível. Confira a documentação contra Java, SQL, `pom.xml`, `.env.example` e Compose.

## Prévias dos e-mails

Os templates usam valores em reais no padrão brasileiro e layout fluido com estilos inline e tabelas de apresentação. Os exemplos abaixo são snapshots com dados fictícios; a implementação está em NotificationTemplateService.

- [Pagamento confirmado](examples/emails/pagamento-confirmado.html).
- [Pedido cancelado com estorno](examples/emails/pedido-cancelado.html).

A aparência final pode variar conforme o cliente de e-mail. O envio real por SMTP continua exigindo validação no ambiente.
