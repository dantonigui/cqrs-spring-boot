# Segurança

## Identidade e sessão

Login Google OAuth2, sem senha local. A identidade é associada ao `sub`. JWT contém ID, e-mail, papel, emissor, emissão, expiração e `sid`; o filtro valida assinatura, expiração, emissor e sessão Redis.

Refresh opaco é rotacionado atomicamente com Lua. Logout invalida sessão e refresh atual; logout global revoga todas as sessões do usuário. Tokens antigos sem `sid` são rejeitados. Redis indisponível impede autenticação, sem aceitar uma sessão não verificada.

## Cookies, CSRF e CORS

Access e refresh são HttpOnly, `Path=/`, com `Secure` configurável. Access usa `Lax`, refresh usa `Strict`, cookie temporário OAuth2 usa `Lax`. Produção HTTPS requer `COOKIE_SECURE=true`.

O CSRF usa cookie legível pelo JavaScript e header nas escritas. `/api/v1/csrf` é público; webhook é dispensado do CSRF e exige assinatura na aplicação. CORS aceita `APP_FRONTEND_URL` com credenciais. CORS não substitui autorização.

SameSite pode bloquear cookies entre sites distintos, mesmo com CORS correto. Veja [frontend](../api/frontend-integration.md).

## Autorização

Autenticação é exigida por padrão. Queries exigem login; comandos gerais, administração e Actuator além da saúde básica exigem ADMIN. Pedidos têm exceções e validação de proprietário. Pedido alheio retorna 404. Administração também usa `@PreAuthorize`.

Papel é consultado na emissão/renovação. Mudá-lo no banco não modifica JWT já emitido; revogue sessões para aplicar imediatamente. A lista de e-mails administrativos atua no cadastro inicial.

## Integrações e segredos

Webhook valida HMAC usando headers e ID da URL; o provedor é consultado para confirmar o pagamento. Segredo ausente não libera a validação.

Não versione credenciais nem registre cookies, tokens, segredo HMAC ou dados completos de cartão. Cartão chega como token do frontend. Credencial exposta exige rotação no provedor; editar o arquivo não remove o histórico Git.

## Limites

Rate limit usa Bucket4j/Caffeine por IP remoto e método anotado, local à JVM. Não há limite distribuído nem `Retry-After` garantido. O código não confia diretamente em `X-Forwarded-For`; atrás de proxy, avalie a identificação do cliente.

Swagger/OpenAPI e health são públicos. Compose publica dependências em loopback e usa Kafka sem TLS/SASL para desenvolvimento. Veja [operação](../deployment/operations.md) para implantação.
