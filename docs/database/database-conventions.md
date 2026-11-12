# Convenções de banco e migração

## Execução

`MigrationConfig` cria dois beans Flyway com `initMethod=migrate`, usando `classpath:db/migration/command` e `classpath:db/migration/query`. `spring.flyway.enabled=false` desabilita a autoconfiguração única, mas os beans explícitos continuam aplicando migrações.

Cada datasource tem seu `flyway_schema_history`. Hibernate usa `validate`, sem criar tabelas. Configurações dos entity managers dependem das migrações correspondentes.

## Alterações

1. Escolha o banco da entidade.
2. Crie versão nova, por exemplo `V2__descricao.sql`, no diretório correspondente.
3. Preserve migrações já aplicadas e seus checksums.
4. Atualize entidades, DTOs/eventos e projeções quando necessário.
5. Valide banco vazio e atualização com dados, incluindo índices e constraints.

O mapeamento físico converte camelCase em snake_case. Use decimais para dinheiro e constraints para invariantes críticas.

## Legado

`src/main/resources/db.migration/` é um diretório legado distinto de `db/migration/`; não é carregado pelos beans atuais. Não coloque novas migrações ali nem presuma que seu histórico vale para os dois bancos.

## Schema preexistente

Não há baseline automático. Antes de marcar um baseline manual, faça backup e compare tabelas, constraints e índices com a versão a registrar. Marcar uma migração sem aplicar seu schema pode perder invariantes.

Não use `ddl-auto=update` para encobrir incompatibilidades. A recuperação depende do histórico e schema real; veja [operação](../deployment/operations.md).
