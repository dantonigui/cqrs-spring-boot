# Referência da API atual

## Endpoints

| Método | Rota | Acesso | Resultado |
|---|---|---|---|
| GET | `/oauth2/authorization/google` | Público | Inicia OAuth2 |
| GET | `/api/v1/csrf` | Público | Token e nome do header |
| GET | `/api/v1/query/auth/me` | Autenticado | Usuário; fallback para escrita enquanto a projeção não existir |
| POST | `/api/v1/command/auth/logout` | Autenticado + CSRF | Revoga sessão atual, access e refresh tokens |
| POST | `/api/v1/command/auth/logout-all` | Autenticado + CSRF | Revoga todas as sessões, access e refresh tokens |
| POST | `/api/v1/command/categories` | ADMIN + CSRF | 201 |
| PUT | `/api/v1/command/categories/{id}` | ADMIN + CSRF | 200 |
| DELETE | `/api/v1/command/categories/{id}` | ADMIN + CSRF | 204 |
| GET | `/api/v1/query/categories` | Autenticado | Página de categorias |
| GET | `/api/v1/query/categories/{id}` | Autenticado | Detalhe |
| POST | `/api/v1/command/products` | ADMIN + CSRF | 201 |
| PUT | `/api/v1/command/products/{id}` | ADMIN + CSRF | 200 |
| DELETE | `/api/v1/command/products/{id}` | ADMIN + CSRF | 204 |
| POST | `/api/v1/command/products/{id}/stock` | ADMIN + CSRF | 204 |
| GET | `/api/v1/query/products` | Autenticado | Página; aceita `categoryId` |
| GET | `/api/v1/query/products/{id}` | Autenticado | Detalhe com estoque projetado |
| POST | `/api/v1/command/orders` | Autenticado + CSRF | 201, `{orderId}` e Location |
| DELETE | `/api/v1/command/orders/{id}` | Proprietário/ADMIN + CSRF | Cancelamento; aceita `reason` |
| POST | `/api/v1/command/orders/{id}/checkout/pix` | Proprietário + CSRF | Dados PIX |
| POST | `/api/v1/command/orders/{id}/checkout/card` | Proprietário + CSRF | Resultado do cartão |
| POST | `/api/v1/command/orders/{id}/checkout/in-person` | ADMIN + CSRF | Confirma recebimento |
| GET | `/api/v1/query/orders` | Autenticado | Seus pedidos |
| GET | `/api/v1/query/orders/{id}` | Proprietário | Detalhe com itens e pagamentos |
| POST | `/api/v1/webhooks/mercadopago` | Assinatura HMAC | 200 após processamento |
| GET | `/admin/dlq/stats` | ADMIN | Contagens por tópico |
| GET | `/admin/dlq/{topic}/peek?limit=10` | ADMIN | Inspeção |
| POST | `/admin/dlq/{topic}/replay` | ADMIN + CSRF | Republicação |
| DELETE | `/admin/cache/products` | ADMIN + CSRF | Invalida listas |
| DELETE | `/admin/cache/product-detail/{id}` | ADMIN + CSRF | Invalida detalhe |
| DELETE | `/admin/cache/all` | ADMIN + CSRF | Invalida caches de produtos |
| GET | `/admin/cache/stats/{id}` | ADMIN | Existência/TTL |
| GET | `/admin/cache/keys` | ADMIN | Chaves de diagnóstico |

## Exemplos de entrada

Categoria:

```json
{"categoryName":"Eletrônicos"}
```

Produto (estoque inicial zero):

```json
{"productName":"Produto exemplo","productCode":"SKU-001","productPrice":99.90,"productImage":"https://example.com/produto.png","categoryId":1}
```

Reposição:

```json
{"quantity":10}
```

Criação de pedido:

```json
{"items":[{"productId":1,"quantity":2}]}
```

Resposta:

```json
{"orderId":42}
```

PIX:

```json
{"payerEmail":"cliente@example.com","payerFirstName":"João","payerLastName":"Silva","payerDocument":"12345678900"}
```

Cartão:

```json
{"cardToken":"token-gerado-pelo-sdk-do-frontend","installments":1,"payerEmail":"cliente@example.com","payerDocument":"12345678900","paymentMethodId":"visa"}
```

Presencial:

```json
{"method":"CASH"}
```

## Respostas de checkout

PIX: `paymentId`, `orderId`, `status`, `pixQrCode`, `pixQrCodeBase64`, `pixExpiration`, `amount`.

Cartão: identificadores, status e detalhe do provedor, método, últimos quatro dígitos, parcelas e valor. Presencial: identificadores, método, status aprovado e valor.

Para os nomes completos de todos os DTOs, consulte `/api-docs`. Credenciais, tokens reais e dados pessoais não devem ser colocados em exemplos ou documentação versionada.