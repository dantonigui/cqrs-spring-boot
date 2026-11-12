# Documentação do projeto

Referência do backend implementado. Os exemplos usam dados fictícios; credenciais ficam no ambiente de execução.

## Primeiro acesso

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
