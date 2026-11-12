# Registro das correções

Os 17 problemas da revisão inicial foram tratados no código. A execução e suas limitações estão em [estratégia de testes](testing/testing-strategy.md).

| Problema | Mudança |
|---|---|
| Escrita/administração públicas | Autenticação nas consultas/pedidos; ADMIN no catálogo e administração |
| Checkout sem autorização | ADMIN no presencial; ownership no PIX/cartão |
| Cache de pedido entre usuários | Chave inclui pedido e usuário |
| Segredos de pagamento no código | Variáveis de ambiente e exemplos sem credenciais |
| Defaults ignorados pelo builder | Defaults Lombok explícitos |
| Webhook bloqueado/URL incorreta | Rota pública específica, URL consistente e HMAC obrigatório |
| Aprovação sem evento | Serviço comum de aprovação publica na outbox |
| PIX selecionando entidade errada | Consulta de pagamento PIX PENDING |
| Divergência banco/Kafka | Outbox transacional com envio confirmado e repetição |
| Idempotência inconsistente | Unique por evento/tópico, lock e transação da projeção |
| Cancelamento sem encerrar cobrança | Cancelamento/estorno remoto; CANCELLED terminal e estorno tardio |
| Path do refresh incompatível | Path / compatível com renovação e remoção |
| Preço da projeção/sem estoque | Snapshot command; reserva com locks, liberação e expiração em 30 minutos |
| Desserialização Java no OAuth | Cookie opaco, estado no Redis e JSON tipado |
| Falhas do webhook como sucesso | Erros propagados e 503 para pagamento ainda não sincronizado |
| Migrações/testes incompletos | Flyway por datasource, constraints e integração com dois bancos |
| Header falsificável no rate limit | Chave usa endereço remoto |

## Melhorias adicionais

DLQ usa tópicos reais, preserva JSON/headers no replay e aguarda confirmação. Cancelamento atualiza o estado financeiro na projeção. Cache de categoria usa namespace próprio e ordenação participa das chaves de paginação. Conflitos de integridade retornam 409.

Compose inclui healthchecks, dependências saudáveis, persistência, logs limitados e perfil opcional da API. Dockerfile usa Java 21 e usuário sem privilégios de root. Documentação cobre arquitetura, casos de uso, regras, API, configuração e operação.

## Etapas externas

Rotacione no Mercado Pago as credenciais expostas anteriormente. Os bancos locais com dados não foram migrados: faça backup e compare schema antes de adotar o histórico Flyway.

Login Google, SMTP e cobrança real exigem validação no ambiente. Banco e provedor não compartilham transação distribuída; se o provedor aceitar uma operação e o commit local falhar, concilie pelo ID/status externo antes de repetir. Veja [pagamentos](business-rules/payments.md).