# Integração do frontend

## Login

Use a origem configurada em `APP_FRONTEND_URL`. Inicie o login por navegação para `/oauth2/authorization/google` na API. O callback define cookies e redireciona ao frontend.

Consulte `/api/v1/query/auth/me` para obter `userId`, `userName`, `userEmail` e `userRole`. Há fallback para escrita enquanto o usuário não estiver projetado. Não copie tokens HttpOnly para localStorage.

## Cookies e CSRF

Exemplo JavaScript para ambiente local:

```javascript
const api = 'http://localhost:8080';

async function createOrder(items) {
  const csrfResponse = await fetch(`${api}/api/v1/csrf`, {
    credentials: 'include',
  });
  if (!csrfResponse.ok) throw new Error(`CSRF: ${csrfResponse.status}`);
  const csrf = await csrfResponse.json();
  const response = await fetch(`${api}/api/v1/command/orders`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ items }),
  });
  if (!response.ok) throw new Error(`Pedido: ${response.status}`);
  return response.json();
}
```

GETs também precisam de `credentials: 'include'`. Atualize CSRF após mudanças de autenticação. A exceção é o webhook assinado do provedor.

## Renovação e concorrência

Não há endpoint separado de refresh. O filtro renova silenciosamente quando access está ausente/inválido. Na corrida pelo mesmo refresh, só uma chamada vence; demais podem receber 401, sem sobrescrever cookies. Após a vencedora concluir, repita uma consulta com os cookies atualizados. Evite ciclos ilimitados.

Não repita automaticamente comandos de criação: não há chave geral de idempotência HTTP. Em pagamentos, confirme o estado local e externo antes de iniciar outra operação.

## Logout e consistência

POST `/api/v1/command/auth/logout` ou `/logout-all`, com cookies e CSRF, apaga cookies e revoga sessões. JWT expirado pode autenticar logout por refresh, sem criar novos tokens.

201 confirma o comando, não sua projeção. Guarde o ID e consulte com atraso limitado; não recrie um pedido devido a 404 temporário. Veja [contrato](current-api-reference.md), [paginação](api-conventions.md) e [erros](error-catalog.md).

## Domínios

CORS e SameSite têm funções diferentes. Cookies `Lax`/`Strict` podem ser bloqueados entre sites distintos. Prefira frontend/API do mesmo site e HTTPS em produção. Configure origem exata; `credentials: 'include'` não supera bloqueio do navegador.
