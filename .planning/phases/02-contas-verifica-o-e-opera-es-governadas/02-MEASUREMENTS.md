# Registro de Medições Empíricas da Fase 2 (02-MEASUREMENTS.md)

**Data de início das medições:** 2026-08-20
**Ambiente de teste:** Container PostgreSQL 18.4 fixado por digest (`docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636`)
**Arquivo de prova:** `backend/src/test/java/br/com/betufu/platform/persistence/PostgresSemanticsIT.java`

---

## 1. Medição A-7: Regex POSIX dentro de constraint `CHECK`

- **Afirmação original:** O operador `~` do PostgreSQL faz match de regex POSIX e serve num `CHECK` para restringir apelidos ao padrão da `D-44` (`^[A-Za-z0-9_-]{3,20}$`) com `COLLATE "C"`, rejeitando homoglifos e reportando o nome da constraint.
- **Data da medição:** 2026-08-20
- **Referência no código:** `PostgresSemanticsIT.java:56-96` (`posixRegexCheckConstraintRejectsHomoglyphAndIdentifiesConstraint`)
- **Veredito:** `OBSERVADO NO IT REAL`
- **Comportamento observado:**
  1. Criação de tabela com coluna `nickname varchar(20) COLLATE "C" NOT NULL` e constraint `CONSTRAINT ck_test_profile_nickname_format CHECK (nickname ~ '^[A-Za-z0-9_-]{3,20}$')` é aceita pelo PostgreSQL 18.4.
  2. Inserção de valor ASCII estrito válido (`giovani_123`) tem sucesso (`1` linha inserida).
  3. Inserção de string contendo homoglifo cirílico da letra `a` (`\u0430` em `giovаni`) com tamanho 7 é recusada pelo banco com `DataIntegrityViolationException` / `PSQLException` reportando violação de constraint.
  4. O nome exato da constraint (`ck_test_profile_nickname_format`) é retornado na mensagem de erro da violação, permitindo que a camada de persistência diferencie violações de formato de apelido de outras violações de integridade.
  5. A tabela mantém exatamente `1` registro (apenas o válido).
  6. **Mutação discriminante:** O IT verde passou 3/3 contra PostgreSQL 18.4. Na mutação controlada, a constraint `CHECK` foi removida mantendo SQL válido: Surefire permaneceu 79/79, Failsafe teve exatamente 1 falha em 3, somente `posixRegexCheckConstraintRejectsHomoglyphAndIdentifiesConstraint` na linha 84, com `Expecting code to raise a throwable`. Após restauração, `PostgresSemanticsIT.java` voltou exatamente ao SHA-256 `4BC030B49BC6882D1564DEC2A7B68A9FBABD21F4A5DCCC61524E366AC9642C29`.

---

## 2. Medição A-5: Índice Único Parcial com cláusula `WHERE`

- **Afirmação original:** PostgreSQL 18.4 suporta índice único parcial (`CREATE UNIQUE INDEX ... WHERE`) e recusa duplicatas somente enquanto o predicado do índice for verdadeiro, permitindo valores idênticos quando o predicado for falso.
- **Data da medição:** 2026-08-20
- **Referência no código:** `PostgresSemanticsIT.java:98-151` (`partialUniqueIndexAllowsDuplicatesAcrossInactiveStatesAndRejectsActiveConflict`)
- **Veredito:** `OBSERVADO NO IT REAL`
- **Comportamento observado:**
  1. Criação de tabela `test_ufu_enrollment` e índice único parcial `CREATE UNIQUE INDEX uq_test_ufu_enrollment_active_hmac ON test_ufu_enrollment (matricula_hmac) WHERE status = 'ATIVO'` é aceita pelo PostgreSQL 18.4.
  2. Inserção da primeira linha com `matricula_hmac = sampleHmac` e `status = 'ATIVO'` tem sucesso.
  3. Inserção da segunda linha com o **mesmo** `matricula_hmac` e `status = 'LIBERADO'` tem sucesso, coexistindo ambas as linhas no banco (total de `2` linhas para o mesmo digest).
  4. Inserção de terceira linha com o **mesmo** `matricula_hmac` e `status = 'ATIVO'` é imediatamente recusada com `DataIntegrityViolationException` reportando a violação do índice `uq_test_ufu_enrollment_active_hmac`.
  5. O total de linhas permanece `2`, comprovando a semântica de transição de estado da matrícula (`D-32` e Plano 02-07).

---

## 3. Medição A-9: Imutabilidade de Trilha de Auditoria no PostgreSQL

- **Afirmação original:** No PostgreSQL 18.4, um trigger `BEFORE UPDATE OR DELETE` com `RAISE EXCEPTION` bloqueia alterações ruidosamente com erro explícito, enquanto uma regra `CREATE RULE ... DO INSTEAD NOTHING` falha em silêncio retornando 0 linhas afetadas sem erro.
- **Data da medição:** 2026-08-20
- **Referência no código:** `PostgresSemanticsIT.java:153-264` (`triggerBlocksWithObservableExceptionWhileRuleFailsSilently`)
- **Veredito:** `OBSERVADO NO IT REAL`
- **Comportamento observado:**
  1. **Trigger (Falha Ruidosa):**
     - Função PL/pgSQL com `RAISE EXCEPTION 'audit_event is immutable: UPDATE and DELETE are prohibited'` associada a trigger `BEFORE UPDATE OR DELETE` em tabela `test_audit_trigger`.
     - `UPDATE` e `DELETE` foram recusados por `UncategorizedSQLException` envolvendo `SQLException` com SQLSTATE `P0001` e mensagem `audit_event is immutable: UPDATE and DELETE are prohibited`.
     - A linha permaneceu intacta.
  2. **Rule `DO INSTEAD NOTHING` (Falha Silenciosa):**
     - Regras `rule_prevent_audit_rule_update` e `rule_prevent_audit_rule_delete` com `DO INSTEAD NOTHING` em `test_audit_rule`.
     - No cenário `DO INSTEAD NOTHING`, `UPDATE` e `DELETE` retornaram `0` sem exceção e a linha permaneceu inalterada.

---

## 4. Medição A-12: Índice de Principal do Spring Session no Redis sob Sessão Autenticada Real

- **Afirmação original:** O Spring Session com OIDC autenticado grava uma chave de índice de principal no Redis indexando o subject (`sub` / `Authentication#getName()`) para busca reversa por principal.
- **Data da medição:** 2026-08-20
- **Referência no código:** `backend/src/test/java/br/com/betufu/platform/session/PrincipalNameIndexProbeIT.java` (`probeAuthenticatedSessionWritesSecurityContextHashAndNoPrincipalIndex`, `probeRuntimeFootprintUnderDefaultRepositoryAndSelectiveTouchWrapperDoesNotDiscriminateIndividualCause`), `backend/src/main/java/br/com/betufu/platform/session/SelectiveTouchSessionRepository.java:74-105` (`save`), `backend/src/main/java/br/com/betufu/platform/session/SessionSecurityConfiguration.java:96-105`
- **Veredito:** `NÃO OBSERVADO NO RUNTIME VIGENTE` (Chave de índice de principal e estruturas auxiliares ausentes no Redis sob a configuração vigente)
- **Comportamento e Evidências:**
  1. **Observado no Runtime (Medição Empírica):**
     - Uma sessão autenticada com `SecurityContext` contendo `DefaultOidcUser` (comprovando que a claim `sub` e `Authentication#getName()` coincidem: `true`) foi persistida no Redis hermético local (`docker.io/library/redis:8.10.0-trixie@sha256:344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366`).
     - No Redis, o hash da sessão sob a chave sanitizada `betufu:session:sessions:<session-id>` contém o atributo `sessionAttr:SPRING_SECURITY_CONTEXT` (booleano `true`), além de `creationTime`, `lastAccessedTime`, `maxInactiveInterval` e os atributos de ciclo de vida (`sessionAttr:betufu.session.lastValidActivityAt.epochSecond` e `sessionAttr:betufu.session.authentication.acr`).
     - Em contrapartida, uma sessão anônima não contém `sessionAttr:SPRING_SECURITY_CONTEXT` (booleano `false`), rejeitando prova vazia.
     - A contagem de chaves de índice de principal (`betufu:session:index:*`, `*principal*`, `*index*`) no Redis é exatamente `0`.
     - A contagem de estruturas auxiliares de expiração (`betufu:session:expirations:*`) no Redis é exatamente `0`.
     - Todas as chaves presentes sob o namespace seguem estritamente a forma sanitizada `betufu:session:sessions:<session-id>`.
  2. **Não Discriminável pelo Runtime:**
     - O footprint observado no Redis em runtime não discrimina isoladamente se a ausência de índices e estruturas auxiliares decorre da propriedade `spring.session.data.redis.repository-type=default`, do wrapper `SelectiveTouchSessionRepository`, ou da combinação de ambos.
  3. **Observado por Leitura de Código:**
     - `SelectiveTouchSessionRepository.java:74-105` implementa `save()` executando transações Redis diretamente via `redisOperations.execute(...)` e `saveOnce(...)`, gravando o hash e configurando o TTL via `expireAt()`, sem chamar `delegateRepository.save(...)`.
     - `SessionSecurityConfiguration.java:96-105` e o construtor de `SelectiveTouchSessionRepository` exigem e aceitam explicitamente `RedisSessionRepository` (tipo default), e não `RedisIndexedSessionRepository`.
  4. **Consequência Arquitetural (Insumo para OQ-8):**
     - Escolher um repositório indexado no futuro exigiria adaptar as travas em `SessionSecurityConfiguration` e reestruturar o `SelectiveTouchSessionRepository` para manter ou delegar a atualização dos índices secundários. Alterar isoladamente a configuração em YAML (`repository-type: indexed`) não seria suficiente para materializar índices no Redis.

---

## 5. Decisão de Arquitetura (OQ-8 / Task 3)

- **Opção escolhida:** `indexado` (trocar futuramente para o repositório indexado nativo do Spring Session).
- **Data da decisão:** 2026-08-20
- **Razão:** Usar a capacidade nativa do framework segue DRY/Don't Hand-Roll e evita manter um índice próprio sujeito a divergência silenciosa, aceitando o custo explícito de adaptar as travas e o `SelectiveTouchSessionRepository`.
