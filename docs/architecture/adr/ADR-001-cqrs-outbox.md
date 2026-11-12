# ADR-001 — CQRS com outbox e idempotência transacional

## Estado

Aceita.

## Problema

Persistir uma alteração e enviar Kafka diretamente na mesma operação não garante que os dois resultados existam juntos. Marcar um evento como concluído em outra transação também permite perder a projeção sem permitir repetição.

## Decisão

Manter dois bancos independentes. Registrar cada evento na outbox do banco de escrita junto com a alteração de negócio. Publicar depois do commit com confirmação do broker. Persistir idempotência no banco de leitura, na mesma transação da projeção.

## Consequências

Consultas têm consistência eventual. A aplicação tolera indisponibilidade temporária do Kafka mantendo eventos pendentes. Republicação é possível e deve ser deduplicada. A outbox precisa de monitoramento e retenção operacional. O dispatcher atual publica lotes em ordem e prioriza previsibilidade; alto volume pode exigir particionamento dos dispatchers e otimização dos bloqueios.

Operações externas de pagamento e e-mail não fazem parte da transação MySQL. Idempotência no provedor, retentativas e conciliação operacional continuam necessárias.