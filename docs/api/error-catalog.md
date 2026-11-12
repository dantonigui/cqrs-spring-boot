# Catálogo de erros

| HTTP | Situação | Ação do cliente |
|---|---|---|
| 400 | DTO inválido, tipo de parâmetro ou estado inválido | Corrigir dados; consultar `fieldErrors` quando presente |
| 401 | Sem autenticação ou assinatura inválida | Refazer login ou revisar integração |
| 403 | Papel insuficiente ou CSRF ausente/inválido | Verificar permissões e atualizar CSRF |
| 404 | Recurso inexistente ou pedido de outro usuário | Não expor informações sobre outro proprietário |
| 409 | Estoque insuficiente ou violação de integridade | Atualizar catálogo e quantidades |
| 422 | Operação de pagamento recusada/falhou | Revisar resultado e confirmar situação no provedor antes de repetir |
| 429 | Rate limit | Aguardar antes de repetir; não há `Retry-After` garantido |
| 500 | Falha inesperada | Guardar contexto não sensível e consultar logs |
| 503 | Webhook chegou antes do registro local | O provedor deve repetir a notificação |

Exemplo de erro da aplicação:

```json
{"timestamp":"2026-10-09T12:00:00","status":409,"error":"Conflict","message":"Estoque insuficiente para o produto 1","path":"/api/v1/command/orders","fieldErrors":null}
```

Detalhes internos de SDK, token de acesso, segredo HMAC e dados completos do cartão não são respostas de erro públicas. Os filtros de segurança podem produzir formatos distintos; o status HTTP é a referência primária.