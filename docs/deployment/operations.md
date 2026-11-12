# Operação e recuperação

## Implantação

Compose é desenvolvimento: broker único, Kafka sem TLS/SASL e portas em loopback. Dockerfile compila Java 21 e executa o JAR com usuário sem privilégios de root. Não há pipeline de implantação definido no repositório.

Para publicar, configure HTTPS, `COOKIE_SECURE=true`, URLs/callbacks públicos, origem CORS exata, credenciais, SMTP e webhook alcançável. Proteja bancos/Kafka/Redis em rede privada e dimensione recursos. Scripts de sessões pressupõem Redis standalone.

## Monitoramento

Health é público; info/metrics exigem ADMIN. Saúde não comprova funcionamento de Google, SMTP ou pagamento real.

| Sinal | Investigar |
|---|---|
| Consulta atrasada | Outbox, Kafka, consumers, DLQ e cache |
| Pendências crescentes | Confirmação Kafka, tipo do evento e logs do dispatcher |
| DLQ crescente | Stats, peek e exceção do consumer |
| Login falhando | Redis, callback, cookies e CORS |
| Pagamento divergente | ID externo, status do provedor e webhook |
| E-mail ausente/duplicado | SMTP, consumer e idempotência |
| Redis recusa escritas | Memória e política noeviction |

No banco command:

```sql
SELECT COUNT(*) AS pending, MIN(created_at) AS oldest
FROM outbox_events WHERE published_at IS NULL;
```

## Recuperar eventos

1. Identifique causa e IDs envolvidos.
2. Corrija dependência, contrato ou dado antes de repetir.
3. Inspecione `/admin/dlq/{topic}/peek?limit=10` com ADMIN.
4. Com CSRF, execute POST `/admin/dlq/{topic}/replay` para tópico aceito pelo serviço.
5. Confira publicação, processamento e deduplicação.

Replay percorre a DLQ desde o início e não a apaga. SMTP pode duplicar e-mail entre envio e commit. Repetir sem diagnóstico pode reproduzir a falha.

## Cache e sessões

`/admin/cache` opera produtos; `/admin/cache/all` não limpa todo Redis. Uma limpeza geral também apaga sessões/OAuth2. Use logout para revogar sessões.

Perda de Redis exige novo login. AOF local não substitui recuperação planejada. Rotação da chave JWT também invalida autenticação existente.

## Backups e retenção

Faça backups independentes de MySQL e teste restauração; preserve históricos Flyway. Reconstruir query requer histórico completo, mas Kafka local retém 168 horas. Apagar query não garante recuperação completa.

Não há limpeza automática da outbox. Defina retenção de eventos publicados, Kafka/DLQ, logs e processamento conforme necessidade de recuperação. Não apague pendências para reduzir volume sem resolver entrega.

## Conciliação financeira

Não existe transação distribuída com Mercado Pago. Uma resposta externa pode ser aceita antes de falha local. Confira pedido, ID de pagamento e status externo antes de nova cobrança. Cancelamento online depende da confirmação de cancelamento/estorno. Devolução presencial depende do operador.

## Escala

Dispatcher confirma envios sequencialmente com bloqueio de lote. Rate limit é local à JVM. Logout global percorre índice de sessões do usuário. Teste e ajuste antes de aumentar réplicas ou adotar Redis Cluster; a configuração atual não garante essas topologias.
