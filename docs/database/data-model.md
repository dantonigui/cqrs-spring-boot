# Modelo de dados

## Bancos

`cqrs_command` mantém a fonte de verdade; `cqrs_query` mantém projeções. Cada banco tem datasource, entity manager, transaction manager e histórico Flyway independentes.

### Command

| Tabela | Conteúdo/restrições |
|---|---|
| `user_command` | Identidade Google, e-mail e papel; Google ID único |
| `category_command` | Categoria do catálogo |
| `product_command` | Produto, categoria e estoque; preço positivo, estoque não negativo |
| `order_command` | Proprietário, estado, total e datas; total positivo |
| `order_command_items` | Snapshot de produto/nome/preço/quantidade; FKs para pedido e produto |
| `payments` | Método, tipo, estado, ID externo e dados PIX/cartão; FK para pedido |
| `outbox_events` | Tópico, chave, tipo Java, payload e datas de publicação |

`payments.mp_payment_id` é único. A coluna gerada `active_payment_sentinel` recebe o ID do pedido em `PENDING`, `APPROVED` ou `IN_PROCESS`; índice único impede dois pagamentos ativos. Quantidades, preços, parcelas e valores têm constraints de positividade.

### Query

| Tabela | Conteúdo |
|---|---|
| `user_query` | Perfil projetado |
| `category_query` | Categoria projetada |
| `product_query` | Produto, categoria e estoque projetados |
| `order_query` | Pedido por `order_id` único e proprietário |
| `order_item_query` | Snapshot de itens associado ao pedido |
| `payment_query` | Pagamento; unicidade de `(order_id, mp_payment_id)` |
| `processed_events` | Idempotência por `(event_id, topic)`, status, tentativas e datas |

Relações entre bancos são lógicas, sem FK distribuída. A projeção não decide preço ou disponibilidade. `/me` usa `user_command` enquanto a projeção não existir.

## Redis

| Prefixo | Função | TTL |
|---|---|---|
| `cqrs:{cache}::` | Consulta serializada | Por cache |
| `oauth2_request:{id}` | Estado temporário OAuth2 | 3 minutos |
| `auth:v2:session:{sid}` | Hash `userId` e refresh atual | TTL do refresh |
| `auth:v2:refresh:{token}` | Identificador da sessão | TTL do refresh |
| `auth:v2:user:{userId}` | Conjunto de sessões para logout global | Renovado na criação/rotação |

JWT contém `sid`, validado no Redis. Lua torna criação, rotação e revogação atômicas. A implementação pressupõe Redis standalone.

## Referência SQL

Schemas ativos: `src/main/resources/db/migration/command/V1__command_schema.sql` e `src/main/resources/db/migration/query/V1__query_schema.sql`.

Veja [convenções](database-conventions.md). Restaurar projeções por eventos requer histórico completo disponível; mantenha backups.
