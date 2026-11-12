# Casos de uso

Fluxos do backend implementado, relacionados às [regras de negócio](../business-rules/business-rules.md). As rotas e payloads completos estão na [API](../api/current-api-reference.md). Escritas autenticadas exigem cookies e CSRF; ADMIN também pode executar os fluxos de USER respeitando a propriedade do pedido.

## Catálogo de casos

| ID | Caso | Ator principal | Regras |
|---|---|---|---|
| UC-01 | Entrar com Google | Visitante | RN-001–005 |
| UC-02 | Consultar próprio perfil | USER/ADMIN | RN-042 |
| UC-03 | Encerrar sessões | USER/ADMIN | RN-004–005 |
| UC-04 | Gerir categorias | ADMIN | RN-006, 009, 013–014 |
| UC-05 | Gerir produtos | ADMIN | RN-006, 009–014 |
| UC-06 | Repor estoque | ADMIN | RN-011, 016 |
| UC-07 | Consultar catálogo | USER/ADMIN | RN-014 |
| UC-08 | Criar pedido | USER/ADMIN | RN-015–020 |
| UC-09 | Consultar próprios pedidos | USER/ADMIN | RN-007, 020, 042 |
| UC-10 | Pagar com PIX | Proprietário | RN-008, 024–027, 029 |
| UC-11 | Pagar com cartão | Proprietário | RN-008, 024–026, 028–029 |
| UC-12 | Confirmar recebimento presencial | ADMIN | RN-024–026, 030–031 |
| UC-13 | Processar webhook | Mercado Pago | RN-031–033, 037 |
| UC-14 | Cancelar pedido | Proprietário/ADMIN | RN-021, 034–037 |
| UC-15 | Expirar reserva | Scheduler | RN-022–023, 035 |
| UC-16 | Notificar pagamento/cancelamento | Consumer | RN-038–040 |
| UC-17 | Recuperar eventos em DLQ | ADMIN | RN-039, 041 |
| UC-18 | Diagnosticar/invalidar cache | ADMIN | RN-041 |

## UC-01 — Entrar com Google

**Objetivo:** autenticar e obter sessão local. **Pré-condições:** cliente Google configurado, callback correto e Redis/banco disponíveis. **Entrada:** navegação para `GET /oauth2/authorization/google`.

**Fluxo:** backend guarda estado temporário; Google autentica; backend busca conta pelo `sub`, cria usuário no primeiro acesso ou sincroniza e-mail; emite JWT e refresh vinculados à sessão Redis; define cookies e redireciona ao frontend.

**Alternativas:** consentimento recusado, callback/estado inválido ou dependência indisponível interrompem login. Lista administrativa alterada não promove usuário existente. **Resultado:** sessão ativa; criação/atualização de perfil segue por eventos.

## UC-02 — Consultar próprio perfil

**Objetivo:** identificar o usuário autenticado. **Pré-condição:** sessão válida. **Entrada:** `GET /api/v1/query/auth/me`.

**Fluxo:** extrair ID autenticado; consultar projeção; se não existir, buscar conta na escrita; retornar ID, nome, e-mail e papel. **Alternativas:** sessão inválida → 401; conta ausente nos dois lados → 404. **Resultado:** perfil sem depender da conclusão da primeira projeção.

## UC-03 — Encerrar sessões

**Objetivo:** terminar acesso atual ou em todos os dispositivos. **Entrada:** POST `/api/v1/command/auth/logout` ou `/logout-all` com CSRF.

**Fluxo:** autenticar JWT ou refresh válido sem rotacionar no logout; revogar sessão atual ou todas as sessões indexadas; limpar cookies e contexto. **Alternativas:** autenticação inválida → 401; CSRF inválido → 403. **Resultado:** JWTs e refresh das sessões revogadas recusados em chamadas posteriores. Logout local preserva sessões de outros dispositivos; global preserva outros usuários.

## UC-04 — Gerir categorias

**Objetivo:** criar, renomear ou excluir categorias. **Pré-condição:** ADMIN. **Entrada:** POST/PUT/DELETE `/api/v1/command/categories` e rota com ID.

**Fluxo:** validar nome; localizar registro nas alterações; persistir mudança e evento na mesma transação; projetar posteriormente. **Alternativas:** dados inválidos → 400; ID ausente → 404; categoria referenciada por produto → conflito de integridade, sem exclusão. **Resultado:** alteração autoritativa, seguida de atualização assíncrona da consulta.

## UC-05 — Gerir produtos

**Objetivo:** manter nome, código, preço, imagem e categoria. **Pré-condição:** ADMIN e categoria existente. **Entrada:** POST/PUT/DELETE `/api/v1/command/products` e rota com ID.

**Fluxo:** validar DTO/categoria; criar com estoque zero ou atualizar campos do produto; persistir e registrar evento; invalidar caches conforme processamento. **Alternativas:** produto/categoria ausente → 404; dados inválidos → 400; exclusão de produto usado em pedido → conflito de integridade. **Resultado:** catálogo alterado, sem reprecificar itens históricos. Código de produto não tem unicidade garantida.

## UC-06 — Repor estoque

**Objetivo:** aumentar quantidade disponível. **Pré-condição:** ADMIN. **Entrada:** POST `/api/v1/command/products/{id}/stock` com `quantity` positiva.

**Fluxo:** bloquear produto; somar quantidade; registrar evento de atualização. **Alternativas:** produto ausente → 404; quantidade não positiva → 400. **Resultado:** estoque disponível aumenta na escrita e depois na projeção. Não há endpoint de ajuste negativo de estoque.

## UC-07 — Consultar catálogo

**Objetivo:** listar categorias/produtos ou consultar detalhe. **Pré-condição:** USER/ADMIN autenticado. **Entrada:** GET em `/api/v1/query/categories` ou `/products`, com ID opcional e paginação; produtos aceitam filtro `categoryId`.

**Fluxo:** consultar cache/projeção e devolver resultado. **Alternativas:** sessão inválida → 401; detalhe ausente → 404, inclusive antes de nova criação ser projetada; limite de requisições pode retornar 429. **Resultado:** catálogo consultado, sem reserva nem garantia de disponibilidade para uma compra futura.

## UC-08 — Criar pedido

**Objetivo:** reservar produtos e congelar valor da compra. **Pré-condição:** usuário autenticado, produtos existentes e estoque suficiente. **Entrada:** POST `/api/v1/command/orders` com `items` contendo IDs e quantidades.

**Fluxo:** usar proprietário autenticado; agregar IDs repetidos; bloquear produtos em ordem; reservar; formar snapshots e total; persistir pedido PENDING e eventos; retornar 201 com ID e Location.

**Alternativas:** lista vazia/dados inválidos impedem criação; produto ausente → 404; estoque insuficiente → 409 com rollback das reservas. **Resultado:** reserva efetuada e pedido criado. Consulta pode atrasar; repetir a criação pode gerar outro pedido.

## UC-09 — Consultar próprios pedidos

**Objetivo:** acompanhar histórico, itens e pagamentos projetados. **Pré-condição:** sessão válida. **Entrada:** GET `/api/v1/query/orders` ou `/{orderId}`.

**Fluxo:** filtrar pelo ID autenticado; consultar cache/projeção; devolver lista paginada ou detalhe. **Alternativas:** inexistente, alheio ou ainda não projetado → 404 no detalhe. **Resultado:** consulta sem alterar pedido. ADMIN também fica restrito aos próprios pedidos nessas rotas; cache não dispensa propriedade.

## UC-10 — Pagar com PIX

**Objetivo:** obter uma cobrança PIX do pedido. **Pré-condições:** proprietário, pedido não pago/cancelado, sem outro pagamento ativo. **Entrada:** POST `/api/v1/command/orders/{id}/checkout/pix` com dados do pagador.

**Fluxo:** bloquear e validar pedido; reutilizar PIX pendente quando aplicável; caso contrário criar pagamento local, marcar AWAITING_PAYMENT e chamar provedor com valor do pedido/chave estável; guardar identificador e QR code; retornar dados.

**Alternativas:** pedido alheio → 404; estado inválido/outro pagamento ativo/falha de cobrança → erro de pagamento. **Resultado:** cobrança pendente, sem marcar pedido pago por apenas gerar QR code. Aprovação ocorre pelo fluxo de confirmação. Após falha externa/local, consultar a situação antes de nova tentativa.

## UC-11 — Pagar com cartão

**Objetivo:** solicitar cobrança online por cartão. **Pré-condições:** proprietário, pedido elegível e ausência de pagamento ativo. **Entrada:** POST `/api/v1/command/orders/{id}/checkout/card` com token do frontend, parcelas e dados do pagador.

**Fluxo:** bloquear pedido; usar total/snapshots; criar pagamento e solicitar ao provedor; mapear estado e dados permitidos; se APPROVED, aprovar pedido pelo serviço comum; devolver resultado.

**Alternativas:** pagamento ativo impede nova cobrança; análise pode manter IN_PROCESS/PENDING; recusa não equivale a cancelamento do pedido; falha da API do provedor retorna erro de pagamento. **Resultado:** status financeiro registrado, e PAID apenas quando aprovado. Aprovação também pode chegar depois por webhook.

## UC-12 — Confirmar recebimento presencial

**Objetivo:** registrar dinheiro já recebido pelo operador. **Pré-condições:** ADMIN, recebimento confirmado, pedido elegível e sem pagamento ativo. **Entrada:** POST `/api/v1/command/orders/{id}/checkout/in-person`, método CASH/CREDIT_CARD/DEBIT_CARD/PIX.

**Fluxo:** bloquear pedido; criar registro local presencial; aprovar pelo serviço comum; publicar evento de aprovação. **Alternativas:** USER → 403; método inválido → 400; pedido pago/cancelado ou pagamento ativo impede registro. **Resultado:** pagamento APPROVED e pedido PAID, sem cobrança externa. ADMIN pode operar pedido de outro usuário.

## UC-13 — Processar webhook

**Objetivo:** aplicar confirmação financeira confiável. **Pré-condições:** assinatura válida e dados coerentes. **Entrada:** POST `/api/v1/webhooks/mercadopago?data.id={id}`, headers de assinatura e body.

**Fluxo:** validar assinatura/ID; consultar provedor; localizar pagamento; bloquear pedido e recarregar pagamento; aplicar situação; aprovar uma única vez quando cabível.

**Alternativas:** assinatura inválida rejeita notificação; registro local ainda ausente → 503 para repetição; REFUNDED não é reaplicado; aprovação de pedido CANCELLED solicita estorno e mantém cancelamento. **Resultado:** situação aplicada conforme guardas do serviço, sem confiar apenas no body. Falha de estorno não deve ser tratada como sucesso.

## UC-14 — Cancelar pedido

**Objetivo:** encerrar pedido e liberar reserva, coordenando devolução. **Pré-condições:** proprietário ou ADMIN; presencial aprovado exige ADMIN. **Entrada:** DELETE `/api/v1/command/orders/{id}`, com motivo opcional.

**Fluxo:** bloquear; validar proprietário/papel/estado; consultar/cancelar/estornar pagamentos online; registrar situação financeira; liberar estoque; marcar CANCELLED; gravar evento na mesma transação local.

**Alternativas:** pedido alheio para USER → 404; presencial sem ADMIN → 403; já cancelado → erro de estado, sem segunda liberação; falha externa impede efetivação local. **Resultado:** pedido cancelado e reserva liberada. No presencial, operador realiza devolução física; registro local não a executa.

## UC-15 — Expirar reserva

**Objetivo:** devolver disponibilidade de pedidos não pagos vencidos. **Ator:** scheduler. **Gatilho:** ciclo configurado, padrão um minuto; prazo padrão 30 minutos desde criação.

**Fluxo:** selecionar até 100 PENDING/AWAITING_PAYMENT vencidos; bloquear e revalidar cada pedido; executar cancelamento com motivo de expiração. **Alternativas:** pedido pago durante espera é preservado; falha no provedor ou outra dependência será tentada no próximo ciclo. **Resultado:** cancelamento elegível e estoque liberado. O prazo não é garantia de execução no segundo exato.

## UC-16 — Enviar notificações

**Objetivo:** informar pagamento aprovado ou pedido cancelado. **Gatilho:** evento Kafka. **Pré-condições:** usuário projetado e SMTP disponível.

**Fluxo:** verificar idempotência da notificação; obter destinatário; montar template; enviar SMTP; concluir processamento. **Alternativas:** usuário ainda não projetado/SMTP indisponível aciona retry e possível DLQ; envio aceito antes de falha no commit pode duplicar mensagem. **Resultado:** tentativa de comunicação independente da resposta HTTP da compra; falha de e-mail não desfaz pagamento.

## UC-17 — Recuperar eventos em DLQ

**Objetivo:** repetir mensagens após corrigir causa. **Pré-condições:** ADMIN, tópico aceito e causa investigada. **Entrada:** GET stats/peek e POST `/admin/dlq/{topic}/replay` com CSRF.

**Fluxo:** inspecionar falhas; corrigir dependência/dado; republicar chave/payload/headers; aguardar confirmação; conferir processamento. **Alternativas:** tópico não aceito ou publicação não confirmada impedem sucesso; repetição pode reenviar mensagem antiga. **Resultado:** mensagens republicadas, DLQ preservada e deduplicação mantida. Veja [operação](../deployment/operations.md).

## UC-18 — Diagnosticar e invalidar cache

**Objetivo:** inspecionar ou renovar consultas de produtos. **Pré-condição:** ADMIN. **Entrada:** rotas `/admin/cache` para keys/stats e DELETE products/product-detail/all com CSRF.

**Fluxo:** inspecionar TTL/existência ou remover entradas de produto; próxima consulta recarrega da projeção. **Alternativas:** falha Redis impede operação; projeção atrasada continua atrasada após invalidação. **Resultado:** cache de produto removido, sem alterar estoque, pagamento ou sessões. `all` não significa apagar todo Redis.

## Critérios de aceitação transversais

- Negar operação quando papel, proprietário ou CSRF não atenderem às regras.
- Preservar pedido/reserva quando uma etapa local falhar; considerar que efeito externo já aceito pode exigir conciliação.
- Não liberar estoque nem aprovar pedido duas vezes por repetição.
- Não converter atraso de projeção em novo pedido/cobrança automática.
- Verificar tokens revogados e concorrência na renovação.

Estes critérios são expectativas dos fluxos documentados, não declaração de que toda a suíte foi aprovada. Confira [testes](../testing/testing-strategy.md) e [roteiro manual](../testing/manual-flow.md).
