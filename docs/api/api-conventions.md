# Convenções da API

## Contrato

Prefixos: `/api/v1/command` para escrita e `/api/v1/query` para leitura. Ferramentas administrativas usam `/admin`. O webhook usa `/api/v1/webhooks/mercadopago`.

Requisições e respostas usam JSON. Valores monetários são decimais calculados pelo backend. IDs são numéricos. Datas de domínio usam ISO-8601 com `LocalDateTime`; o contêiner da API usa UTC. Não interprete uma data sem offset como horário local do navegador.

## Autenticação

O browser preserva cookies e envia token CSRF em operações de escrita. Não há endpoint de login com senha. CORS aceita a origem configurada e credenciais.

## Paginação

Parâmetros: `page` a partir de zero, `size` (máximo 100), `sort=campo,direcao`. Produtos e pedidos retornam `PageResponseDTO`: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`, `empty`. Categorias ainda retornam a representação de `Page` do Spring.

## Consistência eventual

201 confirma o comando, não a atualização instantânea da consulta. Use o ID retornado para acompanhar o pedido. Não repita uma criação apenas porque a projeção ainda não apareceu.

## Erros

Consulte [catálogo de erros](error-catalog.md). Erros da aplicação usam `ErrorResponse`; rejeições feitas pelo filtro Spring Security podem usar uma resposta mais simples. Clientes devem se orientar pelo status HTTP.