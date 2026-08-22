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

---

## 6. Medição A-3: Comportamento do Keycloak 26.7.1 na Redefinição de Senha e Gatilhos de Sessão (D-40)

- **Afirmação original:** A redefinição de senha (`AUTH-04`) no Keycloak encerra as sessões de SSO existentes do usuário e/ou dispara um gatilho de canal de retaguarda (OIDC Back-Channel Logout) para a aplicação (`D-40`).
- **Data da medição:** 2026-08-22
- **Referência no código:** `backend/src/test/java/br/com/betufu/platform/session/PasswordResetTriggerSpikeIT.java` (`spikeMeasuresPasswordResetPreservesUserSessionAndEmitsNoBackchannelLogout`, `spikeEnumeratesKeycloakRealmAndFlowConfigurationsForSessionTermination`)
- **Ambiente de teste:** Container Keycloak 26.7.1 fixado por digest (`quay.io/keycloak/keycloak:26.7.1@sha256:f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6`) e Mailpit v1.30.7 (`docker.io/axllent/mailpit:v1.30.7@sha256:d5ecbb067db3705fa953d79e1b7f81ef84038df67aba6c52825d8c02a1ea748a`)
- **Comando de evidência:** `node scripts/maven.mjs -f backend/pom.xml test-compile failsafe:integration-test failsafe:verify '-Dit.test=PasswordResetTriggerSpikeIT'`, com chave HMAC descartável restrita ao processo de teste.
- **Resultado automatizado:** 2 testes executados, 0 falhas, 0 erros e 0 ignorados (`BUILD SUCCESS`).
- **Veredito:** `gatilho não observado`. O fluxo hospedado de redefinição de senha do Keycloak 26.7.1 preserva a sessão SSO e não emite Back-Channel Logout.
- **Perguntas e valores observados:**
  1. **Pergunta 1 — A sessão de SSO da pessoa no provedor sobreviveu à redefinição?**
     - **Método de teste:** Criar usuário no Keycloak, autenticar via Authorization Code Flow para estabelecer sessão SSO real, consultar `/users/{userId}/sessions` antes e depois de executar o fluxo completo de redefinição de senha (`reset-credentials` + action-token via Mailpit) e testar renovação de token com o refresh token anterior via `/protocol/openid-connect/token`.
     - **Valor observado:** Sim. Havia 1 sessão antes e 1 depois; o identificador permaneceu igual e o refresh token emitido antes da redefinição continuou válido, com HTTP 200.
  2. **Pergunta 2 — Com o canal de retaguarda habilitado no cliente, chega alguma requisição de volta para a aplicação?**
     - **Método de teste:** Configurar `backchannel.logout.url` no cliente `betufu-bff` apontando para o receptor HTTP do teste (`BACKCHANNEL_SERVER`) e observar continuamente por 10 segundos as requisições recebidas durante o fluxo de redefinição, usando a mesma janela máxima do controle positivo.
     - **Controle discriminante (Rejeição de Prova Vazia):** Executar logout administrativo explícito (`POST /users/{userId}/logout`) para provar que o canal de retaguarda e o receptor do teste recebem `logout_token` quando o Keycloak emite o evento.
     - **Valor observado:** Não. Foram recebidas 0 requisições durante a redefinição. O controle positivo recebeu exatamente 1 requisição após o logout administrativo explícito.
  3. **Pergunta 3 — Se algo chegou, o conteúdo casa com o que o registro em memória guardou?**
     - **Método de teste:** Inspecionar as claims do `logout_token` recebido (`iss`, `sub`, `sid`, `events`, `nonce`).
     - **Valor observado:** Não aplicável ao reset, pois nenhum evento chegou. No controle positivo, a requisição foi `POST`, continha `logout_token`, `iss` e `sub` corresponderam ao emissor e à pessoa do teste, `events` continha o evento de backchannel logout, e `sid`/`nonce` estavam ausentes como esperado para logout por pessoa.
  4. **Pergunta 4 — Existe alguma configuração de realm ou de cliente que faça a redefinição encerrar as sessões?**
     - **Método de teste:** Inspecionar via Admin API as representações do realm (`ssoSessionIdleTimeout`, `ssoSessionMaxLifespan`, `revokeRefreshToken`, `notBefore`), atributos do cliente e execuções do fluxo `reset credentials` (`reset-credentials-choose-user`, `reset-credential-email`, `reset-password`).
     - **Valor observado:** Não foi encontrada execução de revogação. O realm expôs `ssoSessionIdleTimeout` e `ssoSessionMaxLifespan`, com `revokeRefreshToken=false` e `notBefore=0`; o fluxo continha `reset-credentials-choose-user`, `reset-credential-email` e `reset-password`, sem provider cujo nome indicasse sessão ou logout.
- **Decisão D-40:** `plugin-evento-keycloak`.
- **Data da decisão:** 2026-08-22.
- **Razão:** Como a redefinição preserva a sessão e não emite backchannel logout, a BETUFU precisa observar `UPDATE_PASSWORD` por um listener do Keycloak com entrega autenticada e durável para manter o encerramento automático e imediato exigido por D-40. A implementação depende de replanejamento e aprovação específicos.
- **Errata canônica (2026-08-22):** a decisão arquitetural continua sendo `plugin-evento-keycloak`, mas o filtro técnico aprovado é `EventType.UPDATE_CREDENTIAL` com `Details.CREDENTIAL_TYPE` exatamente `password`; `UPDATE_PASSWORD` é legado/deprecated e deve ser ignorado. A errata corrige apenas a terminologia técnica e não altera a observação empírica A-3: o reset hospedado não emitiu backchannel logout e preservou a sessão.
- **Verificação agregada final (2026-08-22):** teste focal `2/2`; Maven completo com Surefire `172/172` e Failsafe `163/163`; Node completo `536/536`; zero falhas, erros, cancelamentos ou testes ignorados. A observação negativa e o controle positivo usaram janelas de 10 segundos.
