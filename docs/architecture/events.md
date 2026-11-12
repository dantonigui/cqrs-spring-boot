# Eventos e processamento assíncrono

## Publicação

Producers gravam snapshots na outbox na transação de escrita. `OutboxDispatcher` consulta até 50 pendências em ordem, a cada segundo por padrão. Espera até dez segundos pela confirmação de cada envio; uma falha interrompe o lote e mantém pendências.

Falha após confirmação Kafka e antes de commit pode republicar eventos. A garantia é pelo menos uma vez e exige deduplicação. Não há limpeza automática da outbox.

## Tópicos

Os nomes são literais e diferenciam maiúsculas/minúsculas.

| Domínio | Tópicos |
|---|---|
| Usuários | `user-created`, `user-updated`, `user-logout` |
| Categorias | `category-created`, `category-updated`, `category-deleted` |
| Produtos | `products-created`, `products-updated`, `products-deleted` |
| Pedidos | `order-created`, `order-updated`, `order-deleted` |
| Pagamentos | `payment_Approved` |

Constantes: `shared/kafka/topics`. `order-deleted` representa cancelamento, sem apagar histórico. Renomear exige planejar produtores, consumidores e mensagens existentes.

## Consumers

Consumers aplicam projeções e registram `(eventId, topic)` em `processed_events`, na mesma transação de leitura. Evento concluído não deve reaplicar alterações.

O grupo `cqrs-notifications` consome aprovação e cancelamento. Usa prefixo `notification:` no identificador de tópico para separar deduplicação de notificações e projeções. Depende do usuário projetado. SMTP pode duplicar e-mails se aceitar o envio antes de falha no commit.

Não há ordem global entre tópicos. Retry atende dependências ainda não projetadas. Estados terminais devem ser preservados.

## Retry e DLQ

`KafkaRetryConfig` usa backoff de 1 segundo, multiplicador 2, intervalo máximo 4 segundos e limite de tempo 10 segundos. Falhas são encaminhadas para `{topico}.DLT` na mesma partição.

Replay administrativo republica payload, chave e headers e aguarda confirmação. Percorre a DLQ desde o início e não a apaga; repetições podem reenviar mensagens antigas. Corrija a causa antes de repetir. Veja [recuperação](../deployment/operations.md).
