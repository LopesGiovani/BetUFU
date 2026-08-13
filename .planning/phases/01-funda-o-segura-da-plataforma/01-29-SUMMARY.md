---
phase: 01-funda-o-segura-da-plataforma
plan: "29"
subsystem: runtime-security
tags: [spring-boot, postgresql, keycloak, oauth2, xsrf, docker, node, runtime-validation]

requires:
  - phase: 01-03
    provides: "PostgreSQL 18.4 e Keycloak 26.7.1 locais, persistentes e fixados por digest"
  - phase: 01-17
    provides: "Scaffold Spring Boot 4.1.0 com BFF OIDC, sessão server-side e CSRF ativo"
  - phase: 01-18
    provides: "Profiles dev, homolog, prod, runtime e integrated sem segredos embutidos"
  - phase: 01-20
    provides: "Cliente Angular same-origin com XSRF-TOKEN/X-XSRF-TOKEN e base relativa"
  - phase: 01-05
    provides: "Persistência da identidade local, Flyway V1 e endpoint tracer idempotente"
provides:
  - "Datasource PostgreSQL externo para o backend executado no host, sem defaults ou segredos no YAML"
  - "Sessão servlet em memória limitada ao profile dev até a migração obrigatória para Redis no 01-06"
  - "Proteção SPA XSRF oficial do Spring Security 7.1, sem endpoint /csrf ou token manual no Angular"
  - "Runner fail-closed para login OAuth2 hospedado, matriz XSRF, persistência SQL, ownership e cleanup"
affects: [01-06, 01-08, 01-16, 01-21, backend, security, identity, database, runtime, ci]

actuals:
  tokens: 51059
  tasks: 2
  commits: 3

tech-stack:
  added: []
  patterns:
    - "Configuração runtime externa: placeholders obrigatórios e valores locais apenas no env example"
    - "SPA XSRF double-submit oficial: CookieCsrfTokenRepository + csrf.spa()"
    - "Prova runtime fail-closed: artefatos ancorados, origens/redirects allowlisted e cleanup por ownership"

key-files:
  created:
    - "backend/src/test/java/br/com/betufu/platform/security/CsrfSpaIT.java"
    - "scripts/validate-runtime-prerequisites.mjs"
    - "scripts/validate-runtime-prerequisites.test.mjs"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-29-SUMMARY.md"
  modified:
    - "backend/src/main/resources/application.yaml"
    - "backend/src/main/resources/application-dev.yaml"
    - "backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java"
    - "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java"
    - "infra/.env.example"
    - "infra/keycloak/realm-betufu.json"

key-decisions:
  - "runtime e integrated recebem o datasource somente por BETUFU_DB_JDBC_URL, BETUFU_DB_USER e BETUFU_DB_PASSWORD, sem fallback no YAML."
  - "Somente dev exclui temporariamente SessionDataRedisAutoConfiguration; o 01-06 deve remover essa exceção e ativar Spring Session Redis."
  - "O backend usa csrf.spa() com CookieCsrfTokenRepository, cookie host-only Path=/, HttpOnly=false, SameSite=Lax e Secure derivado do request/proxy confiável."
  - "O contrato é double-submit cookie/header e não promete vínculo criptográfico entre o token XSRF e a sessão."
  - "Não foi criado endpoint /csrf, filtro bespoke, alteração OpenAPI, cliente regenerado ou montagem manual de token no Angular."

patterns-established:
  - "Runtime host: configuração externa obrigatória e Testcontainers integrated preservado por precedência"
  - "Fronteira HTTP: sessão BFF + XSRF same-origin, sem bearer token observável; DOM e Web Storage ficam para o 01-21"
  - "Runtime proof: login hospedado real, máquina OAuth2 explícita, evidência sanitizada e restauração de recursos"

requirements-completed: [IDEN-03, IDEN-04, DATA-01, DATA-02, OPS-01, TEST-01]

coverage:
  - id: D1
    description: "O backend dev no host conecta ao PostgreSQL local com configuração externa, enquanto integrated continua usando Testcontainers"
    requirement: DATA-01
    verification:
      - kind: unit
        ref: "EnvironmentConfigurationTest — 10/10"
        status: pass
      - kind: integration
        ref: "Checkpoint H — runtime status ok e SQL BETUFU_RUNTIME_SQL_OK"
        status: pass
    human_judgment: false
  - id: D2
    description: "O Spring materializa e valida o contrato SPA XSRF sem desabilitar CSRF nem introduzir token manual no frontend"
    requirement: IDEN-03
    verification:
      - kind: integration
        ref: "CsrfSpaIT — 5/5"
        status: pass
      - kind: integration
        ref: "Checkpoint H — login authorization-code-hosted e matriz 403/403/403/200/200"
        status: pass
    human_judgment: false
  - id: D3
    description: "O runner comprova login, rotação XSRF, identidade persistida, artefato imutável e cleanup sem usar Redis prematuramente"
    requirement: OPS-01
    verification:
      - kind: unit
        ref: "scripts/validate-runtime-prerequisites.test.mjs — 191/191"
        status: pass
      - kind: integration
        ref: "Checkpoint H — runtime status ok, Redis 0 conexões, JAR intacto e cleanup restored"
        status: pass
    human_judgment: false
  - id: D4
    description: "O profile dev mantém explicitamente a sessão servlet provisória e não tenta conectar ao Redis antes do Plano 01-06"
    requirement: IDEN-04
    verification:
      - kind: unit
        ref: "EnvironmentConfigurationTest — exclusão Redis limitada ao dev"
        status: pass
      - kind: integration
        ref: "Checkpoint H — sentinela Redis com zero conexões e cleanup restored"
        status: pass
    human_judgment: false
  - id: D5
    description: "Flyway V1, JPA validate e a identidade persistida foram comprovados contra PostgreSQL 18.4 no runtime host"
    requirement: DATA-02
    verification:
      - kind: integration
        ref: "LocalIdentityTracerIT — 17/17"
        status: pass
      - kind: integration
        ref: "Checkpoint H — SQL BETUFU_RUNTIME_SQL_OK"
        status: pass
    human_judgment: false
  - id: D6
    description: "A verificação agregada preserva testes rápidos e integrados reais nos lifecycles Surefire e Failsafe"
    requirement: TEST-01
    verification:
      - kind: integration
        ref: "node scripts/maven.mjs -o -f backend/pom.xml verify — 36/36"
        status: pass
      - kind: unit
        ref: "scripts/validate-runtime-prerequisites.test.mjs — 191/191"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com checkpoints, diagnósticos e autorizações por bloco"
completed: 2026-08-13
status: complete
---

# Phase 1 Plan 29: Datasource runtime e proteção XSRF SPA Summary

**Backend dev ligado ao PostgreSQL local e fluxo OAuth2/XSRF same-origin comprovado no runtime HTTP hospedado, com sessão provisória em memória e validação fail-closed.**

## Desempenho

- **Início:** 2026-08-12
- **Conclusão:** 2026-08-13
- **Tasks:** 2
- **Arquivos de implementação:** 9
- **Commits registrados antes deste fechamento:** 3
- **Diff de implementação:** 204.233 caracteres, equivalentes a 51.059 tokens na escala `chars/4`

## Entregas

- O documento `runtime | integrated` agora exige URL, usuário e senha do datasource por variáveis externas, sem default ou segredo embutido; o `@ServiceConnection` dos testes integrados continua tendo precedência.
- O profile `dev` usa deliberadamente a sessão servlet em memória até o Plano 01-06, sem remover o starter Redis nem antecipar sua configuração.
- `SecurityConfiguration` usa o suporte SPA do Spring Security 7.1 com `CookieCsrfTokenRepository`, preservando o fluxo BFF e o XSRF nativo do Angular.
- O runner Node executa login OAuth2 hospedado, valida redirects e cookies, prova a rotação tombstone(A)→B, executa a matriz XSRF e dois PUTs convergentes, consulta o PostgreSQL e restaura apenas os recursos que possui.
- O fixture versionado do usuário técnico do Keycloak contém perfil completo, impedindo que `VerifyProfile` intercepte o login de teste.

## Commits das tarefas

1. **Task 1 — datasource runtime e sessão dev provisória:** `5dbd275` (`feat(01-29): configure runtime datasource and dev session`).
2. **Task 2 — proteção SPA XSRF e prova runtime:** `016390b` (`feat(01-29): add SPA XSRF protection and runtime proof`).
3. **Plano, Gate e dependências documentais:** `3cab79e` (`docs(01-29): record approved runtime datasource and XSRF gate`).

Esses três commits são locais no momento deste fechamento. Stage, commit do próprio fechamento e push continuam operações separadas.

## Evidências de verificação

| Prova | Resultado |
|---|---|
| Maven offline completo | PASS — `36/36`: `BetufuApplicationSmokeTest` 4/4, `EnvironmentConfigurationTest` 10/10, `LocalIdentityTracerIT` 17/17 e `CsrfSpaIT` 5/5; zero failure/error/skip. |
| Runner e regressões Node | PASS — dois `node --check`, validador canônico e `191/191` testes, sem fail/cancelled/skipped/todo. |
| Login real | PASS — fluxo `authorization-code-hosted` contra Keycloak local. |
| Matriz XSRF | PASS — respostas `403/403/403/200/200`, com tombstone(A)→B e continuidade de B. |
| Persistência | PASS — dois PUTs convergiram no UUID `3831f446-54da-4167-98af-bb7ad5fbd78e`; SQL retornou `BETUFU_RUNTIME_SQL_OK`. |
| JAR do backend | PASS — antes/depois com 78.665.537 bytes e SHA-256 `15D7514BF98EE415D0FC83E6008AE0838ACBD01EE17CB53B09B3ABF33A5A0878`. |
| Redis provisoriamente ausente | PASS — sentinela observou zero conexões durante o backend dev. |
| Higiene e ownership | PASS — PostgreSQL e Keycloak restaurados, cinco portas livres, lock/processos próprios ausentes e scope guard sem path inesperado. |
| Gate humano | PASS — Checkpoints H e I aprovados por LopesGiovani; Gate global aprovado em `2026-08-13T19:00:20.4878257Z`. |

## Decisões e limites de segurança

- O Angular não recebe access token, refresh token ou ID token. Ele continua usando requisições relativas, cookie de sessão do BFF e o mecanismo XSRF nativo do `HttpClient`.
- O XSRF adotado é double-submit: o servidor exige que cookie e header coincidam. Isso impede a mutação sem o valor legível pelo JavaScript same-origin, mas não cria vínculo criptográfico com uma sessão específica.
- O atributo `Secure` deriva da requisição já processada pela política de proxy confiável. O harness local aceita o comportamento especial de `localhost` apenas nas duas origens exatas aprovadas.
- O endpoint de liveness apenas comprova continuidade; os PUTs protegidos são a evidência de sessão autenticada e CSRF válido.
- Homologação e produção continuam obrigadas a fornecer datasource, origens e proxies confiáveis externamente; nenhum valor local foi promovido para esses ambientes.

## Desvios e correções

### 1. Perfil técnico persistido do Keycloak

O realm persistido tinha `VerifyProfile` ativo e o usuário técnico não possuía `firstName`/`lastName`. O fixture foi endurecido e uma reconciliação administrativa separadamente autorizada alterou somente esses dois campos, com comparação integral e cleanup restaurado.

### 2. Compatibilidade de cookies reais do Keycloak

O runner foi corrigido para aceitar cookies gerais quoted válidos e o uso de cookies `Secure` pelo Keycloak em `http://localhost`, sem relaxar o contrato não quoted do `XSRF-TOKEN` nem permitir outras origens.

### 3. Máquina de estados OAuth2 e observabilidade

Redirects deixaram de usar uma allowlist solta e passaram a obedecer transições explícitas. Erros runtime preservam somente uma linha `RUNTIME_*` sanitizada, suficiente para diagnóstico sem expor URL, `code`, `state`, senha ou token.

### 4. Momento correto da rotação XSRF

A hipótese inicial esperava o cookie B no liveness. A análise do Spring Security 7.1/Tomcat 11 mostrou que o callback OAuth2 emite o tombstone de A e cria B na mesma resposta. O runner passou a provar essa causalidade e usa o liveness somente para confirmar que B permanece inalterado.

**Impacto:** as correções foram necessárias para representar o comportamento real e manter a prova fail-closed. O escopo terminou com nove arquivos de implementação: oito das duas tasks e `infra/keycloak/realm-betufu.json` na correção autorizada do perfil.

## Riscos residuais

- A sessão em memória é uma exceção exclusiva do `dev`; o Plano 01-06 deve removê-la e implementar Spring Session Redis com política PT8H.
- A prova HTTP inspeciona URL, body e cookies observáveis pelo runner. DOM e Web Storage serão verificados no navegador real pelo Playwright do 01-21.
- Mockito/Byte Buddy ainda emite aviso de agente dinâmico no Java 25; os testes passaram, mas a configuração precisará ser adaptada antes que futuras versões do Java tornem esse comportamento bloqueante.
- A identidade imutável da distribuição Java para CI e os riscos supply-chain já registrados continuam decisões posteriores; este plano não os ampliou.

## Requisitos e próximo passo

O frontmatter registra como concluída a contribuição local do 01-29 para `IDEN-03`, `IDEN-04`, `DATA-01`, `DATA-02`, `OPS-01` e `TEST-01`, conforme o contrato de rastreabilidade do GSD. Isso não encerra esses requisitos globalmente: eles permanecem compartilhados com planos posteriores da Fase 1, e `.planning/REQUIREMENTS.md` continua inalterado e `Pending`.

O próximo plano é o **01-06**, que substitui a sessão dev provisória pela sessão Redis com política ociosa PT8H. O 01-21 permanece bloqueado até essa transição e até seu próprio checkpoint de aquisição/execução Playwright.

## Configuração manual necessária

Nenhuma nova configuração versionada fora do repositório. O ambiente local usa os valores públicos descartáveis de `infra/.env.example`; homologação e produção deverão fornecer seus valores reais externamente nos planos de implantação correspondentes.

## Autoavaliação: APROVADA

- [x] Datasource host, precedência Testcontainers e exceção dev de sessão foram comprovados.
- [x] Proteção XSRF SPA permaneceu ativa, sem endpoint adicional ou token manual no Angular.
- [x] Maven 36/36, Node 191/191, login hospedado, matriz XSRF e SQL passaram.
- [x] JAR, containers, portas, processos, lock e escopo foram restaurados ou preservados.
- [x] Gate global foi aprovado humanamente sem encerrar requisitos compartilhados ou a Fase 1.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-13*
