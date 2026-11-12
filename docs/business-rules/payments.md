# Pagamentos, cancelamento e devolução

## Checkout

PIX e cartão exigem proprietário autenticado. Checkout é rejeitado para pedidos pagos ou cancelados. Um pedido possui no máximo um pagamento ativo (`PENDING`, `IN_PROCESS` ou `APPROVED`), reforçado por índice único no banco.

PIX pendente reutiliza os dados existentes. Cartão ou presencial não criam outra cobrança quando há pagamento ativo. PIX também rejeita um pagamento ativo de outro método.

O valor é o total congelado no pedido. CPF, token de cartão e e-mail do pagador são encaminhados ao provedor; os números completos do cartão não passam por esta API.

## Pagamento presencial

Somente ADMIN chama `/checkout/in-person` após confirmar o recebimento real. Métodos aceitos: `CASH`, `CREDIT_CARD`, `DEBIT_CARD`, `PIX`. O registro local é aprovado imediatamente e publica o evento que atualiza o lado de leitura e as notificações.

## Aprovação

Cartão aprovado sincronamente e confirmação via webhook usam o mesmo serviço de aprovação. A operação bloqueia o pedido, impede duplicação de eventos e publica pela outbox. IDs de evento são determinísticos por pagamento; pagamentos locais usam o ID local para evitar colisões.

## Cancelamento

1. Verificar proprietário ou autorização ADMIN e estado.
2. Para pagamento online ativo, consultar o provedor e cancelar ou estornar antes de efetivar localmente.
3. Se o provedor falhar, preservar pedido e reserva para nova tentativa.
4. Registrar status financeiro, liberar estoque, cancelar pedido e registrar evento na mesma transação local.

Pedidos pagos presencialmente só podem ser cancelados por ADMIN. Essa operação registra a devolução; o operador deve realizar e confirmar a devolução física ao executar o cancelamento. O sistema não movimenta dinheiro em espécie nem opera uma maquininha externa.

## Aprovação tardia

Pedido cancelado nunca volta a PAID. Se o provedor confirmar um pagamento online depois do cancelamento, o serviço solicita estorno com chave de idempotência estável e registra `REFUNDED`. Falha de estorno não é respondida como sucesso e deve ser repetida ou conciliada.

## Webhook

Rota: `/api/v1/webhooks/mercadopago?data.id={id}`. A assinatura é HMAC-SHA256, com os headers `x-signature` e `x-request-id`; o ID assinado vem da URL e deve corresponder ao body. Segredo ausente não desativa a validação. O backend consulta o pagamento no Mercado Pago antes de aplicar o status.

Referência do provedor: [validação e configuração de notificações](https://www.mercadopago.com.br/developers/pt/docs/wallet-connect/notifications).

## Limites de consistência

Uma transação MySQL não desfaz uma chamada já aceita pelo provedor. Uma falha entre resposta externa e commit pode exigir repetição com a mesma chave e conciliação. Nunca tente cobrar novamente com outra operação antes de confirmar a situação do pagamento anterior. A outbox garante a persistência do evento local, não atomicidade distribuída com o provedor.