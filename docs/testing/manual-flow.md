# Validação manual

Use ambiente local, contas próprias e credenciais de teste. Configure [infraestrutura](docker-local.md), frontend e SMTP à parte.

## Login

1. Navegue para `/oauth2/authorization/google` e conclua login.
2. Consulte `/api/v1/query/auth/me` com cookies; primeiro login deve funcionar antes da projeção.
3. Obtenha CSRF e envie no header retornado nas escritas.
4. Sem cookies, rota protegida retorna 401; sem CSRF, escrita retorna 403.
5. Use conta ADMIN cadastrada conforme `MY_EMAIL_ADMIN` para operar catálogo.

## Catálogo e estoque

1. Crie categoria/produto conforme [payloads](../api/current-api-reference.md).
2. Aguarde projeção; produto novo tem estoque zero.
3. Reponha em `/api/v1/command/products/{id}/stock`.
4. Crie pedido com IDs/quantidades; guarde ID e Location.
5. Confira total calculado e reserva.
6. Compra acima do estoque deve retornar 409 sem reserva parcial.
7. Pedido de outro usuário retorna 404, mesmo após cache do proprietário.

## Pagamento e cancelamento

Use pedidos distintos para cada cenário:

- PIX: obtenha QR code e confira aprovação após pagamento de teste/webhook.
- Cartão: envie token do frontend e confira aprovação/recusa de teste.
- Presencial: ADMIN confirma recebimento; USER recebe 403.
- Cancelamento pendente: estado CANCELLED e reposição uma única vez.
- Pago online: confirme estorno no provedor e estado local; falha externa preserva estado para repetição.
- Presencial: cancelamento exige ADMIN e devolução física realizada pelo operador.
- Expiração: ajuste duração no teste e confira o scheduler sem cancelar um pedido já pago.

## Sessões e eventos

Abra sessões em dois navegadores/perfis. Logout deve invalidar somente a atual; logout global, ambas. JWT copiado de sessão revogada deve ser recusado. Faça esse teste somente no seu ambiente e não registre o token.

Consulta com JWT expirado e refresh válido renova cookies; logout nesse estado não gera substituições. Renovação concorrente tem um único vencedor.

Confira outbox, projeção e SMTP. Kafka temporariamente indisponível mantém pendências. Para falhas persistentes, confira DLQ e [recuperação](../deployment/operations.md).

Registre cenário, IDs não sensíveis e status observado. Não versione tokens, CPF real, segredos nem dados de cartão.
