---
phase: 2
slug: contas-verifica-o-e-opera-es-governadas
# status lifecycle: draft (seeded by plan-phase) → validated (set by validate-phase §6)
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-08-20
---

# Fase 2 — Estratégia de Validação

> Contrato de validação por fase, para amostragem de feedback durante a execução.
> Semeado a partir de `02-RESEARCH.md` § *Validation Architecture*. Os IDs de task são preenchidos depois do planejamento.

---

## Test Infrastructure

| Propriedade | Valor |
|----------|-------|
| **Framework backend** | JUnit 5 + Testcontainers, via Surefire (`*Test`) e Failsafe (`*IT`) — `backend/pom.xml:165-167` |
| **Framework Node** | `node:test` nativo — `scripts/*.test.mjs` (5 arquivos) |
| **Config file** | `backend/pom.xml` |
| **Quick run command** | não existe atalho aprovado — ver *Sampling Rate* |
| **Full suite command** | `node scripts/maven.mjs -f backend/pom.xml verify` **e** `node --test scripts/*.test.mjs` |
| **Gate de contrato** | `node scripts/validate-phase1.mjs --contract` |
| **Limites modulares** | `ModuleDependencyRulesTest`, `ModulithArchitectureTests` (ArchUnit + Spring Modulith) |
| **Containers** | `GenericContainer` com `DockerImageName` fixado. **Não há módulo Testcontainers de Redis nem de Keycloak** — `backend/pom.xml:102-113` |
| **Runtime estimado** | não mensurado; a Fase 1 fechou com 192 testes Maven e 481 Node |

**Regra vinculante de `CLAUDE.md`:** a prova agregada são **duas** suítes, ambas sem filtro. Declarar conclusão com apenas uma é cobertura parcial em silêncio — foi assim que a regressão do `OIDC_BOUNDARY` atravessou execução e revisão no 01-26.

---

## Sampling Rate

- **Após cada commit de task:** as duas suítes agregadas, sem filtro. Execução focal esconde quebra de contexto Spring.
- **Após cada onda:** as duas suítes **mais** `node scripts/validate-phase1.mjs --contract`, **mais** a transcrição das contagens somadas de `target/surefire-reports` e `target/failsafe-reports` — classes, testes, falhas, erros e ignorados por suíte, somados dos `TEST-*.xml`, nunca estimados.
- **Antes do gate de fase:** as duas suítes verdes, contagens transcritas, e **cada mutação discriminante desta fase observada vermelha** contra o código antigo, restaurada com conferência de hash.
- **Latência máxima de feedback:** não mensurada; a suíte completa da Fase 1 era tolerável por commit e assume-se que continue.

---

## Per-Task Verification Map

> Semeado por requisito. A coluna *Task ID* é preenchida quando os `02-NN-PLAN.md` existirem.

| Task ID | Requisito | Comportamento verificado | Tipo | Comando automatizado | Existe? | Status |
|---|---|---|---|---|---|---|
| _(a preencher)_ | AUTH-02 | realm permite autocadastro; contrato do validador reformulado | Node | `node --test scripts/validate-keycloak-realm.test.mjs` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | AUTH-02 | autocadastro real conclui e produz sessão BETUFU | IT Keycloak | `…verify -Dit.test=SelfRegistrationKeycloakIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | AUTH-03 | `VERIFY_EMAIL` impede a conclusão do code flow — **prova da `D-38`** | IT Keycloak | novo IT no padrão de `OidcRevocationKeycloakIT` | ❌ Onda 0 (**A-2**) | ⬜ pending |
| _(a preencher)_ | AUTH-03 | usuário técnico segue autenticando com `verifyEmail: true` | IT Keycloak | `OidcRevocationKeycloakIT` + regressão Node nova | ⚠️ existe, **regride** se `emailVerified` não subir | ⬜ pending |
| _(a preencher)_ | AUTH-04 | reset habilitado e SMTP fixado | Node | `validate-keycloak-realm.test.mjs` | ✅ existe; preservar | ⬜ pending |
| _(a preencher)_ | PROF-01 | apelido salvo e editado, `NOT NULL` desde a criação | unit + IT | `BetufuProfileTest`, `ProfileControllerIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | PROF-01 / D-44 | banco recusa apelido fora do ASCII (homoglifo cirílico) | IT PostgreSQL | `ProfilePersistenceIT` | ❌ Onda 0 — **mutação: remover o `CHECK`** | ⬜ pending |
| _(a preencher)_ | PROF-02 / PRIV-01 | DTO público não expõe e-mail, matrícula nem HMAC | unit + ArchUnit | `PublicProfileDtoTest` + regra ArchUnit | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | VERI-06 | ordem normalizar→validar→HMAC e determinismo | unit | `MatriculaHmacTest` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | VERI-06 / D-33 | contexto **não sobe** sem chave, com chave curta ou não decodificável | unit de contexto | `HmacKeyFailClosedTest` | ❌ Onda 0 — **mutação: default na propriedade** | ⬜ pending |
| _(a preencher)_ | VERI-07 | segunda conta com a mesma matrícula é recusada | IT PostgreSQL | `UfuEnrollmentUniquenessIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | VERI-07 / D-35 | duas versões ativas: registra sob `v1`, rotaciona, recusa sob `v2` | IT PostgreSQL | `UfuEnrollmentRotationIT` | ❌ Onda 0 — **prova central da `D-35`** | ⬜ pending |
| _(a preencher)_ | VERI-07 | corrida concorrente não cria segunda associação | IT PostgreSQL | `UfuEnrollmentConcurrencyIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | VERI-09 | predicado "tem matrícula ativa" | unit | `EnrollmentEligibilityTest` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | VERI-11 | realm não declara identity provider | Node | `validate-keycloak-realm.test.mjs` | ❌ Onda 0 — regra nova | ⬜ pending |
| _(a preencher)_ | OPER-01 | conceder e revogar separadamente, com auditoria | IT | `RoleGrantControllerIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | OPER-02 | escopo respeitado | unit | `RoleScopeTest` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | OPER-03 / D-48a | **autoconcessão bloqueada** | unit + IT | `RoleGuardTest`, `RoleGrantControllerIT` | ❌ Onda 0 — **mutação discriminante** | ⬜ pending |
| _(a preencher)_ | OPER-03 / D-48b | **último administrador geral não se revoga** | unit + IT | idem | ❌ Onda 0 — **mutação discriminante** | ⬜ pending |
| _(a preencher)_ | OPER-04 | moderador sem acesso a saldo ou função esportiva | IT | matriz de autorização | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | OPER-06 | painel devolve só os blocos do papel; agregados sem PII | IT | `OperationalPanelIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | IDEN-05 / D-49 | mudança de papel encerra **todas** as sessões da identidade | IT Redis | `RoleChangeSessionTerminationIT` | ❌ Onda 0 — **falha hoje**: `repository-type: default` não enumera | ⬜ pending |
| _(a preencher)_ | IDEN-05 | repositório indexado presente e habilitado | Node + IT | `validate-runtime-prerequisites.test.mjs` reformulado | ❌ Onda 0 — **regra invertida** | ⬜ pending |
| _(a preencher)_ | D-40 | reset de senha encerra todas as sessões | IT Keycloak+Redis | `PasswordResetSessionTerminationIT` | ❌ Onda 0 — **depende de A-3** | ⬜ pending |
| _(a preencher)_ | PRIV-01 | matrícula e HMAC não aparecem em log, URL ou telemetria | ArchUnit + IT | regra ArchUnit + `ObservabilityIT` estendido | ❌ Onda 0 — `LogSanitizer` **medido: não cobre** | ⬜ pending |
| _(a preencher)_ | AUDT-01 | evento grava ator, alvo, ação, antes/depois, motivo, correlation, idempotência | IT | `AuditEventIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | AUDT-01 | **tentativas bloqueadas são auditadas** (`D-30`, `D-48`) | IT | idem | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | AUDT-02 | `UPDATE` e `DELETE` em `audit_event` **falham** | IT PostgreSQL | `AuditImmutabilityIT` | ❌ Onda 0 — **mutação: remover o trigger** | ⬜ pending |
| _(a preencher)_ | D-47 | semente idempotente: segunda execução não duplica nem reescreve | IT | `AdminSeedIdempotencyIT` | ❌ Onda 0 | ⬜ pending |
| _(a preencher)_ | — | rotas de produto existem em produção; tracer e diagnostic **não** | Node | `validate-angular-route-boundary.test.mjs` | ❌ Onda 0 — 3 regras reformuladas, `PRODUCTION_TRACER_FORBIDDEN` preservada | ⬜ pending |
| _(a preencher)_ | — | paridade código↔OpenAPI e digest do cliente gerado | Node | `node scripts/validate-phase1.mjs --contract` | ✅ existe; consumir | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Reformulações de validador — **todas de artefato para propriedade**, porque o `STATE.md` já registra que guard que pina identidade de artefato cai sem que nenhuma propriedade seja violada:

- [ ] `scripts/validate-keycloak-realm.mjs` — regras de `registrationAllowed` e `verifyEmail`, com mutação nova
- [ ] `scripts/validate-runtime-prerequisites.mjs:1298-1302` — a propriedade é *"o técnico autentica sem required action pendente"*, não *"o realm não verifica e-mail"*
- [ ] `scripts/validate-runtime-prerequisites.test.mjs:1029` — a mutação `verifyEmail = true` **deixa de mutar** e precisa ser substituída
- [ ] `scripts/validate-runtime-prerequisites.mjs:973` e a mutação `.test.mjs:878` — idem para `repository-type`
- [ ] `scripts/validate-angular-route-boundary.mjs` — `PRODUCTION_ROUTES_EXPORT`, `_EMPTY`, `_SHAPE` e `DEVELOPMENT_ROUTES_SHAPE`; **preservar** `PRODUCTION_TRACER_FORBIDDEN`

Infraestrutura de teste nova:

- [ ] Regra ArchUnit de privacidade — matrícula e HMAC fora de logger e de DTO público
- [ ] `HmacKeyFailClosedTest` — contexto não sobe sem chave válida
- [ ] `UfuEnrollmentRotationIT` — duas versões ativas
- [ ] `AuditImmutabilityIT` — `UPDATE` e `DELETE` falham no banco
- [ ] IT de fluxo real contra Keycloak provando a `D-38` (fecha **A-2**)
- [ ] IT que leia a chave real do índice de principal no Redis (fecha **A-12**) — primeira tarefa da frente `D-49`
- [ ] Fixture Redis compartilhada para os ITs de sessão, no padrão de `RedisSessionIT`
- [ ] **Spike de verificação de A-3** — reset de senha produz gatilho observável? — **antes** de planejar a `D-40`
- [ ] Decidir `notify-keyspace-events` para homologação e produção (`CONFIG SET` permitido versus `ConfigureRedisAction.NO_OP`); resolvido para o ambiente local por inspeção de artefato

**Instalação de framework:** nenhuma. A infraestrutura existente cobre os tipos de teste desta fase.

---

## Manual-Only Verifications

| Comportamento | Requisito | Por que manual | Instruções |
|---|---|---|---|
| Fluxo hospedado de cadastro e confirmação de e-mail no navegador real | AUTH-02, AUTH-03 | As páginas são renderizadas pelo Keycloak com o tema `betufu`; o IT prova o protocolo, não a legibilidade | Playwright 1.62.1 fixado no 01-21; reaproveitar aquele padrão, com checkpoint próprio para qualquer aquisição de navegador |
| Legibilidade e contraste das superfícies novas | `02-UI-SPEC.md` | Contraste é calculado no contrato, mas leitura em tela real não é automatizável nesta fase | Conferir contra a tabela de contraste do `02-UI-SPEC.md` |

---

## Validation Sign-Off

- [ ] Toda task tem verificação automatizada ou dependência declarada de Onda 0
- [ ] Continuidade de amostragem: nunca 3 tasks consecutivas sem verificação automatizada
- [ ] Onda 0 cobre todas as referências MISSING
- [ ] Nenhuma flag de watch-mode
- [ ] Cada mutação discriminante desta fase foi **observada vermelha** antes de contar como prova
- [ ] `nyquist_compliant: true` no frontmatter

**Approval:** pending
