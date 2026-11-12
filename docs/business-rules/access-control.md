# Autenticação e controle de acesso

## Regras

| ID | Regra |
|---|---|
| AUTH-001 | Login ocorre com Google OAuth2; o backend emite access token e refresh token. |
| AUTH-002 | `USER` e `ADMIN` podem consultar catálogo e seus próprios pedidos. |
| AUTH-003 | Somente `ADMIN` altera catálogo, repõe estoque e acessa ferramentas administrativas. |
| AUTH-004 | PIX e cartão exigem que o pedido pertença ao usuário autenticado; ser ADMIN não dispensa ownership nesses checkouts. |
| AUTH-005 | Somente ADMIN confirma recebimento presencial. |
| AUTH-006 | Um usuário não acessa detalhes de outro pedido; a resposta é 404, inclusive após o proprietário preencher o cache. |
| AUTH-007 | Pedido pago presencialmente exige ADMIN para cancelamento; ADMIN pode operar pedidos de outros usuários. |
| AUTH-008 | Escritas autenticadas exigem token CSRF, além dos cookies. |

## Rotas públicas

Fluxo OAuth2, documentação OpenAPI/Swagger, obtenção de CSRF e saúde básica são públicos. O webhook Mercado Pago é público no filtro HTTP e exige assinatura válida na aplicação. O restante da API exige autenticação e, quando aplicável, papel ADMIN.

## Cookies

Access token e refresh token são HttpOnly e têm `Path=/`. Em produção use `COOKIE_SECURE=true` e HTTPS. O modo local do Compose permite HTTP com `COOKIE_SECURE=false`. Refresh tokens duram sete dias por padrão; access tokens usam o TTL configurado.

O estado temporário OAuth2 permanece no Redis por três minutos. O cookie contém apenas um identificador aleatório, não objetos Java serializados.

## Frontend

Use `credentials: 'include'`. Consulte `GET /api/v1/csrf`, conserve os cookies e envie o `token` no `headerName` retornado em POST, PUT, DELETE e outras operações inseguras. Atualize o token após alterações de autenticação. A origem permitida é `APP_FRONTEND_URL`.

Cada JWT contém um `sid`, vinculado a uma sessão ativa no Redis. O logout revoga essa sessão e seu refresh token atual, invalidando também JWTs copiados. `logout-all` revoga atomicamente todas as sessões do usuário, sem afetar outros usuários. JWTs anteriores à adoção do `sid` são rejeitados; é necessário fazer login novamente.

A rotação do refresh token usa Lua para validar, consumir e substituir o token atomicamente. Apenas uma requisição concorrente consegue usar o token antigo; as demais não emitem cookies e precisam repetir a chamada com os cookies atualizados. Logout com JWT expirado autentica pelo refresh sem rotacioná-lo. O Redis é necessário também para validar access tokens; indisponibilidade impede autenticação.

`GET /api/v1/query/auth/me` usa a projeção de leitura e, enquanto ela não existir, consulta o usuário no banco de escrita. O estado da sessão, refresh e índice por usuário tem TTL de sete dias, renovado na rotação. Os scripts usam o Redis standalone configurado pelo projeto.