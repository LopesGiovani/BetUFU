---
phase: 01-funda-o-segura-da-plataforma
plan: "05"
subsystem: identity-persistence
tags: [spring-boot, oidc, postgresql, flyway, jpa, testcontainers, csrf]

requires:
  - phase: 01-03
    provides: "PostgreSQL 18.4 local com imagem e persistência aprovadas"
  - phase: 01-17
    provides: "Aplicação Spring Boot 4.1.0 em Java 25 e SecurityFilterChain BFF OIDC com sessão server-side e CSRF ativo"
  - phase: 01-18
    provides: "Profiles dev, homolog e prod agrupados em runtime, com integrated diretamente ativável para testes"
  - phase: 01-20
    provides: "Contrato OpenAPI e cliente Angular para PUT /api/v1/tracer/identity/current"
provides:
  - "Migration Flyway V1 da identidade local, validada contra PostgreSQL 18.4 real"
  - "Correlação idempotente e concorrente do par OIDC exato (iss, sub) com UUID opaco"
  - "Endpoint autenticado PUT /api/v1/tracer/identity/current com DTO público allowlist"
  - "IT PostgreSQL/OIDC/CSRF executado por Failsafe, com concorrência e negativos de segurança"
affects: [01-06, 01-07, 01-08, 01-10, 01-12, 01-16, 01-21, identity, database, api, security]

actuals:
  tokens: 12576
  tasks: 3
  commits: 3

tech-stack:
  added:
    - "spring-security-test 7.1.0 (escopo test, gerenciado pelo Spring Boot)"
    - "Maven Failsafe Plugin 3.5.6"
  patterns:
    - "Identidade local pela chave natural OIDC exata (issuer, subject), nunca por e-mail"
    - "INSERT ... ON CONFLICT DO NOTHING seguido de SELECT em READ_COMMITTED"
    - "Migration Flyway imutável e JPA em ddl-auto=validate"
    - "Controller fino: principal OIDC -> service transacional -> DTO allowlist"

key-files:
  created:
    - "backend/src/main/resources/db/migration/V1__create_local_identity.sql"
    - "backend/src/main/java/br/com/betufu/identity/internal/LocalIdentity.java"
    - "backend/src/main/java/br/com/betufu/identity/internal/LocalIdentityRepository.java"
    - "backend/src/main/java/br/com/betufu/identity/internal/LocalIdentityService.java"
    - "backend/src/main/java/br/com/betufu/identity/internal/CurrentIdentityResponse.java"
    - "backend/src/main/java/br/com/betufu/identity/internal/LocalIdentityController.java"
    - "backend/src/test/java/br/com/betufu/identity/LocalIdentityTracerIT.java"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-05-SUMMARY.md"
  modified:
    - ".gitattributes"
    - "backend/pom.xml"
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "A identidade BETUFU é correlacionada pelo par (iss, sub) exato e case-sensitive; e-mail, username e claims opcionais não participam da chave."
  - "Repetições não executam no-op update: createdAt e updatedAt permanecem iguais e estáveis nesta fase."
  - "A concorrência é resolvida pelo PostgreSQL com unique natural e ON CONFLICT, sem find-then-save nem recuperação dentro de transação invalidada."
  - "O endpoint backend existe em dev, homolog e production; somente o futuro tracer Angular é excluído de production."
  - "Principal não OIDC ou iss/sub inválido recebe provisoriamente 401 genérico; o Problem Details canônico pertence ao Plano 01-12."

patterns-established:
  - "Persistência idempotente: INSERT concorrente tolerante + leitura autoritativa pela chave natural"
  - "Fronteira de dados: entidade interna nunca é serializada; resposta HTTP usa record allowlist"
  - "Teste integrado real: Testcontainers PostgreSQL fixado por tag e digest, executado pelo Failsafe"

requirements-completed: [IDEN-02, DATA-02]

coverage:
  - id: D1
    description: "A migration Flyway V1 cria e valida o schema autoritativo de identidade local no PostgreSQL 18.4"
    requirement: DATA-02
    verification:
      - kind: integration
        ref: "LocalIdentityTracerIT#appliesTheAuthoritativeSchemaOnPostgresql18"
        status: pass
    human_judgment: false
  - id: D2
    description: "Chamadas repetidas e concorrentes para o mesmo (iss, sub) convergem em uma linha, UUID e timestamps estáveis"
    requirement: IDEN-02
    verification:
      - kind: integration
        ref: "LocalIdentityTracerIT#repeatedCallsReturnTheSameIdentityAndStableTimestamps e concurrentCallsConvergeOnOneIdentity"
        status: pass
      - kind: integration
        ref: "LocalIdentityTracerIT#repeatedHttpCallsIgnoreEmailAndReturnTheSameRepresentation e concurrentHttpCallsConvergeOnOneRepresentation"
        status: pass
    human_judgment: false
  - id: D3
    description: "O endpoint deriva a identidade somente do ID token OIDC e rejeita CSRF ausente, principal incorreto, claims inválidas e spoofing sem persistir"
    requirement: IDEN-02
    verification:
      - kind: integration
        ref: "LocalIdentityTracerIT — negativos OIDC/CSRF/UserInfo/body/query/header"
        status: pass
    human_judgment: false
  - id: D4
    description: "A resposta 200 contém exatamente identityId, status, createdAt e updatedAt, sem dados internos ou credenciais"
    requirement: API-01
    verification:
      - kind: integration
        ref: "LocalIdentityTracerIT#persistsTheOidcIdentityAndReturnsOnlyTheOpenApiFields"
        status: pass
    human_judgment: false
  - id: D5
    description: "O lifecycle Maven preserva os testes rápidos no Surefire e executa o IT PostgreSQL no Failsafe"
    requirement: TEST-01
    verification:
      - kind: integration
        ref: "node scripts/maven.mjs -f backend/pom.xml verify — Surefire 8/8 e Failsafe 17/17"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com checkpoints, correções e aprovações por bloco"
completed: 2026-08-12
status: complete
---

# Phase 1 Plan 05: Identidade local OIDC e persistência PostgreSQL Summary

**Vínculo backend seguro entre o principal OIDC e uma identidade BETUFU estável, idempotente e concorrente, provado contra PostgreSQL 18.4 real.**

## Desempenho

- **Conclusão:** 2026-08-12
- **Tasks:** 3
- **Arquivos versionáveis:** 9
- **Commits de implementação:** 3
- **Diff realizado:** 50.304 caracteres, equivalentes a 12.576 tokens na escala `chars/4`.

## Entregas

- A migration `V1__create_local_identity.sql` cria `public.local_identity` com UUID opaco, chave natural única `(issuer, subject)`, `COLLATE "C"`, limites em caracteres e bytes e timestamps estáveis.
- `LocalIdentityRepository` executa `INSERT ... ON CONFLICT DO NOTHING`; `LocalIdentityService` relê a linha autoritativa em transação `READ_COMMITTED`.
- `LocalIdentityController` usa exclusivamente `iss` e `sub` do ID token do `OidcUser`, sem aceitar identidade por body, query ou header.
- `CurrentIdentityResponse` expõe somente os quatro campos do OpenAPI: `identityId`, `status=PERSISTED`, `createdAt` e `updatedAt`.
- `LocalIdentityTracerIT` valida migration, JPA, autenticação, CSRF, privacidade, repetição e concorrência em PostgreSQL 18.4 real.

## Commits das tarefas

1. **Task 1 — tooling do teste integrado:** `b696142` (`build(01-05): prepare PostgreSQL integration test tooling`).
2. **Task 2 — migration e persistência concorrente:** `9e95d17` (`feat(01-05): add concurrent local identity persistence`).
3. **Task 3 — endpoint autenticado e IT de segurança:** `4a4dcf7` (`feat(01-05): add authenticated local identity endpoint`).

Decisões documentais relevantes: `05e4068` endureceu o plano e `637c188` registrou a aprovação do DDL exato.

## Evidências de verificação

| Prova | Resultado |
|---|---|
| Resolução Maven controlada | PASS — `spring-security-test` 7.1.0 e Maven Failsafe 3.5.6, sem duplicar a execução herdada do parent Spring Boot. |
| IT focal | PASS — lifecycle `verify` executou Surefire e `LocalIdentityTracerIT` no Failsafe contra a imagem PostgreSQL 18.4 aprovada. |
| Verify agregado | PASS — Surefire `8/8` e Failsafe `17/17`, com zero failure, error ou skip e `timeout=false`. |
| Correspondência fonte/relatório | PASS — os 17 métodos `@Test` da fonte correspondem exatamente aos 17 casos do relatório Failsafe; os relatórios são posteriores às fontes. |
| Contrato PostgreSQL | PASS — Flyway V1 aplicada, JPA `ddl-auto=validate`, constraints, collation, SQLSTATE e limites UTF-8 comprovados. |
| Segurança HTTP | PASS — SecurityFilterChain e CSRF reais no MockMvc; negativos não persistem; body/query/header e UserInfo não substituem o ID token. |
| Concorrência | PASS — oito chamadas simultâneas pelo service e oito pelo HTTP convergem no mesmo UUID, timestamps e única linha. |
| Higiene | PASS — zero container/rede Testcontainers residual após a execução; `backend/target/` permanece ignorado; Git terminou limpo e sincronizado. |

## Desvios e correções

- A configuração local do Failsafe removeu uma execução duplicada: o parent Spring Boot já fornece os goals `integration-test` e `verify`.
- A referência `tag@digest` do PostgreSQL exigiu `@ServiceConnection(name = "postgres")` para o Spring Boot identificar corretamente o tipo da conexão.
- A consulta ao catálogo PostgreSQL trocou o alias reservado `collation` por `collation_record`.
- No PowerShell 5.1, `-Dit.test=...` precisou ser passado como dois argumentos separados pelo launcher Maven; a prova agregada final não usa filtro.

## Riscos residuais e limites da prova

- `oidcLogin()` injeta um principal OIDC já validado. Assinatura, discovery e fluxo Keycloak real continuam para a prova E2E posterior.
- O helper `csrf()` prova a proteção no backend, mas não materializa o cookie `XSRF-TOKEN` para um navegador real.
- `@ServiceConnection` configura somente o IT; o backend diário ainda precisa de datasource local externo aprovado.
- O IT compartilha container/tabela e pressupõe execução sequencial. Se paralelismo JUnit for habilitado, a classe deverá ser explicitamente serializada.
- O aviso do Mockito sobre autoanexação do ByteBuddy não falha no Java 25 atual, mas deve ser tratado antes que uma versão futura do JDK proíba esse comportamento.

## Requisitos e prontidão

O plano concluiu sua responsabilidade por `IDEN-02` e `DATA-02`. Também entregou cobertura local que contribui para `DATA-01`, `API-01` e `TEST-01`, mas não encerra esses requisitos mais amplos. `REQUIREMENTS.md` permanece globalmente `Pending` até o fechamento das entregas compartilhadas da Fase 1.

O próximo plano nominal é o **01-21**, mas sua implementação continua bloqueada até um bloco de planejamento separado definir, aprovar, implementar e provar:

1. datasource PostgreSQL do runtime local por configuração externa, sem segredo hardcoded;
2. bootstrap HTTP XSRF same-origin que entregue o cookie esperado pelo Angular.

## Autoavaliação: APROVADA

- [x] Os nove paths do plano existem e estão distribuídos nos três commits de implementação.
- [x] Flyway, JPA e PostgreSQL 18.4 foram comprovados em integração real.
- [x] Repetição e concorrência convergem sem duplicata nem alteração de timestamps.
- [x] O endpoint usa somente o principal OIDC e não vaza dados internos.
- [x] Surefire e Failsafe executaram todas as provas obrigatórias sem skip.
- [x] Nenhum requisito compartilhado foi encerrado globalmente de forma prematura.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-12*
