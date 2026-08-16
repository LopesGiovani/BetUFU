---
phase: 01-funda-o-segura-da-plataforma
plan: "10"
type: execute
wave: 17
depends_on: [01-23]
requirements: [DATA-01, DATA-02, DATA-06, TEST-01]
files_modified:
  - backend/src/main/resources/db/migration/V2__add_platform_guards.sql
  - backend/src/main/java/br/com/betufu/platform/persistence/FlywayPolicy.java
  - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
  - backend/src/test/java/br/com/betufu/platform/persistence/FlywayChecksumIT.java
  - backend/src/main/java/br/com/betufu/platform/persistence/StorageAuthorityRules.java
  - backend/src/main/java/br/com/betufu/platform/persistence/PostgresAuthorityProbe.java
  - backend/src/main/java/br/com/betufu/platform/persistence/RedisAuxiliaryProbe.java
  - backend/src/main/java/br/com/betufu/platform/persistence/PrivateObjectStorageProbe.java
  - backend/src/test/java/br/com/betufu/platform/persistence/FlywayPolicyTest.java
  - backend/src/test/java/br/com/betufu/platform/persistence/AuthoritativeStorageIT.java
status: completed
---

# Sumário de Execução — Plano 01-10: Flyway, JPA e PostgreSQL Autoritativos com Limites Redis/S3

## 1. Visão Geral

O Plano 01-10 estabeleceu e comprovou empiricamente a autoridade estrita do PostgreSQL 18 como única fonte da verdade de dados de negócio na BETUFU, delimitando formalmente o Flyway como o único mecanismo de evolução de schema, e restringindo o Redis a armazenamento volátil/auxiliar e o S3 (SeaweedFS local) a armazenamento privado de mídia/comprovantes.

---

## 2. Entregas por Tarefa

### Task 1: Bloquear Schema Fora do Flyway
- **`V2__add_platform_guards.sql`**: Migration técnica Flyway criando a tabela `public.platform_guard` com chaves únicas, restrições não-vazias e controle de tamanho em octetos, sem antecipar prematuramente entidades de domínio futuro.
- **`FlywayPolicy.java`**: Componente de validação em tempo de startup (`@Profile("runtime | integrated")`) que:
  1. Exige estritamente `spring.jpa.hibernate.ddl-auto=validate` (rejeitando ausência genuína, strings vazias, `none`, `update`, `create`).
  2. Proíbe desativação do Flyway (`spring.flyway.enabled != false`).
  3. Bloqueia configurações destrutivas de clean (`clean-disabled=false` ou `clean-on-validation-error=true`).
  4. Bloqueia baseline inseguro (`baseline-on-migrate=true`).
  5. Bloqueia locations divergentes que não apontem para `classpath:db/migration`.
- **`FlywayPolicyTest.java`**: Testes unitários com discriminação completa provando falha para cada uma das violações de política acima (incluindo ausência explícita de `ddl-auto`) e sucesso para configuração válida.
- **`FlywayMigrationIT.java`**: Teste de integração real contra PostgreSQL 18 (digest fixado) validando:
  1. Aplicação sequencial de V1 e V2 com status `SUCCESS`.
  2. Idempotência estrita em múltiplas reexecuções de `flyway.migrate()`.
  3. Inicialização e migração concorrente em paralelo serializada com segurança via `pg_advisory_lock`.
- **`FlywayChecksumIT.java`**: Teste de integração com fixture temporária isolada contendo V1 adulterada e V2 intacta, comprovando discriminação ativa: qualquer adulteração de migration versionada aplicada dispara falha de validação (`FlywayException: Migration checksum mismatch`), bloqueando a inicialização do backend.

### Task 2: Provar a Fronteira PostgreSQL / Redis / Objetos S3
- **`StorageAuthorityRules.java`**: Regras de governança de arquitetura e constantes de persistência.
- **`PostgresAuthorityProbe.java`**: Sonda técnica que valida conectividade, versão principal do PostgreSQL 18 e presença dos guards de schema.
- **`RedisAuxiliaryProbe.java`**: Sonda técnica que valida a disponibilidade do Redis exclusivamente para operações auxiliares e efêmeras.
- **`PrivateObjectStorageProbe.java`**: Sonda técnica que valida a configuração do bucket S3 privado sob o contrato genérico `BETUFU_S3_*`.
- **`AuthoritativeStorageIT.java`**: Teste de integração abrangente comprovando:
  1. `postgresAuthorityProbe` atesta PostgreSQL 18 autoritativo e guards ativos.
  2. `redisAuxiliaryProbe` e `privateObjectStorageProbe` confirmam delimitação auxiliar e bucket privado.
  3. Limpeza total do Redis (`FLUSHALL`) não afeta nem altera dados de identidade persistidos no PostgreSQL autoritativo.
  4. Regras ArchUnit impedem repositórios de domínio sob Redis e impedem que o pacote `platform` acerte pacotes internos como `identity.internal` (com teste sintético de discriminação que falha ao violar a regra).

---

## 3. Evidências de Verificação

### Execução Completa Maven Surefire + Failsafe
- **Comando:** `node scripts/maven.mjs -f backend/pom.xml verify`
- **Resultado:** `BUILD SUCCESS`
- **Testes Unitários Surefire (9 classes XML):** **57 testes executados, 0 falhas, 0 erros, 0 ignorados.**
- **Testes de Integração Failsafe (12 classes XML com Testcontainers):** **76 testes de integração executados, 0 falhas, 0 erros, 0 ignorados.**
- **Total do Build:** **133 testes executados com 100% de aprovação GREEN.**
