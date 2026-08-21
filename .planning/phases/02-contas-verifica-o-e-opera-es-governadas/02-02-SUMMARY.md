---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 02
subsystem: auth
tags: [keycloak, oidc, email-verification, hosted-login, mailpit]

requires:
  - phase: 01-fundacao-segura-da-plataforma
    provides: Keycloak 26.7.1, OIDC authorization code flow, Mailpit e tema herdado
provides:
  - Autocadastro hospedado somente com email e verificacao obrigatoria
  - Criacao de senha posterior a confirmacao, sem a BETUFU receber credenciais
  - Perfil declarativo Keycloak restrito a username e email
  - Smokes HTTP, validadores negativos e IT completo do cadastro
affects: [02-08, AUTH-02, AUTH-03, AUTH-04, VERI-11]

actuals:
  tokens: 18598
  tasks: 3
  commits: 0

tech-stack:
  added: []
  patterns:
    - Fluxos de conta permanecem hospedados no Keycloak
    - Tema DRY herda templates oficiais e altera apenas footer, CSS e assets
    - Contratos de realm usam validadores exatos e mutacoes negativas

key-files:
  created:
    - backend/src/test/java/br/com/betufu/identity/SelfRegistrationKeycloakIT.java
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-KEYCLOAK-OPERACAO.md
  modified:
    - infra/keycloak/realm-betufu.json
    - scripts/validate-keycloak-realm.mjs
    - scripts/validate-keycloak-realm.test.mjs
    - scripts/validate-runtime-prerequisites.mjs
    - scripts/validate-runtime-prerequisites.test.mjs
    - scripts/validate-keycloak-theme.mjs
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-UI-SPEC.md

key-decisions:
  - "Cadastro inicial pede somente email; o Keycloak deriva o username do mesmo valor."
  - "A senha nasce somente depois da confirmacao de email, dentro do Keycloak."
  - "Nao duplicar templates register.ftl ou login-update-password.ftl; manter heranca do keycloak.v2."

patterns-established:
  - "Hosted identity boundary: a BETUFU nao recebe, persiste ou processa senha."
  - "Live import proof: validar arquivo, realm importado, HTML real e fluxo completo."

requirements-completed: [AUTH-02, AUTH-03, AUTH-04, VERI-11]

coverage:
  - id: D1
    description: Cadastro hospedado solicita somente email e nao apresenta senha, nome ou sobrenome.
    requirement: AUTH-02
    verification:
      - kind: integration
        ref: "SelfRegistrationKeycloakIT#selfRegistrationEnforcesEmailVerificationBeforeIssuingAuthorizationCode"
        status: pass
      - kind: automated_ui
        ref: "scripts/validate-keycloak-theme.mjs"
        status: pass
    human_judgment: false
  - id: D2
    description: Nenhum authorization code e emitido antes da confirmacao; depois dela a pessoa cria senha e entra com email.
    requirement: AUTH-03
    verification:
      - kind: integration
        ref: "SelfRegistrationKeycloakIT#selfRegistrationEnforcesEmailVerificationBeforeIssuingAuthorizationCode"
        status: pass
    human_judgment: false
  - id: D3
    description: Redefinicao de senha permanece habilitada e SMTP local continua apontando para Mailpit.
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: "scripts/validate-keycloak-realm.test.mjs"
        status: pass
    human_judgment: false
  - id: D4
    description: O realm nao declara provedor de identidade externo nem exige conta UFU.
    requirement: VERI-11
    verification:
      - kind: unit
        ref: "validate-keycloak-realm.test.mjs#recusa provedor de identidade externo declarado"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-20
status: complete
---

# Phase 2 Plan 02: Conta hospedada no Keycloak

**Cadastro somente por email, verificacao obrigatoria e criacao posterior de senha foram comprovados no Keycloak 26.7.1 real.**

## Performance

- **Duration:** nao cronometrada; execucao retomada em mais de uma sessao
- **Started:** nao registrado
- **Completed:** 2026-08-20T21:21:09-03:00
- **Tasks:** 3
- **Files modified:** 9

## Accomplishments

- Ativou autocadastro, verificacao de email e `registrationEmailAsUsername` no realm local.
- Restringiu o perfil de cadastro a `username` interno e `email`, resultando em um unico campo editavel visivel.
- Provou email, confirmacao, criacao de senha, novo login, claims OIDC e continuidade da conta tecnica.
- Preservou o tema DRY sem criar novos templates FTL.

## Task Commits

Nenhum commit foi criado. `AGENTS.md` exige autorizacao separada para stage e commit; as tres tasks permanecem no working tree verificado.

## Files Created/Modified

- `infra/keycloak/realm-betufu.json` - Realm com autocadastro, email confirmado e perfil declarativo.
- `scripts/validate-keycloak-realm.mjs` e `.test.mjs` - Contrato exato e mutacoes negativas.
- `scripts/validate-runtime-prerequisites.mjs` e `.test.mjs` - Conta tecnica valida sob verificacao obrigatoria.
- `scripts/validate-keycloak-theme.mjs` - Smoke HTTP stateful das paginas herdadas.
- `backend/src/test/java/br/com/betufu/identity/SelfRegistrationKeycloakIT.java` - Prova integral com Keycloak e Mailpit reais.
- `02-UI-SPEC.md` e `02-KEYCLOAK-OPERACAO.md` - Contrato visual e procedimento de reimportacao.

## Decisions Made

- O formulario inicial possui somente email; username e derivado pelo Keycloak.
- A senha e criada apenas depois da confirmacao do endereco.
- As paginas continuam herdadas de `keycloak.v2`; somente rodape, CSS e recursos locais sao customizados.

## Deviations from Plan

### Auto-fixed Issues

**1. Smoke HTTP precisava preservar a sessao do Keycloak**
- **Found during:** Task 2
- **Issue:** seguir o link de cadastro sem o cookie da pagina de login produzia uma sessao invalida.
- **Fix:** adicionado cookie jar efemero, restrito a mesma origem e mantido apenas em memoria.
- **Files modified:** `scripts/validate-keycloak-theme.mjs`
- **Verification:** smoke HTTP passou duas vezes apos reset completo.

**2. Perfil importado ainda expunha nome e sobrenome**
- **Found during:** verificacao visual final
- **Issue:** o formulario real contrariava o contrato de cadastro somente por email.
- **Fix:** configurado `declarative-user-profile` com exatamente `username` e `email`, e ampliados validadores/IT.
- **Files modified:** realm, validadores, IT e alinhamento documental do plano.
- **Verification:** formulario visual com um campo, Node 518/518 e Java 200/200.

**Total deviations:** 2 auto-fixed por necessidade de corretude.
**Impact on plan:** as correcoes materializaram o contrato ja aprovado; nao adicionaram dependencia nem fluxo da BETUFU.

## Issues Encountered

- Uma primeira invocacao Maven focal perdeu a citacao do argumento PowerShell; o comando corrigido executou normalmente e nao revelou defeito no codigo.
- A sessao antiga de automacao do navegador expirou apos o reset; uma nova aba confirmou o formulario real sem enviar dados.

## User Setup Required

None - o reset local reimporta o realm pelo procedimento documentado.

## Next Phase Readiness

- AUTH-02, AUTH-03, AUTH-04 e VERI-11 possuem prova automatizada no snapshot atual.
- A medicao dos claims `email` e `email_verified` fica disponivel para OQ-4/Plano 02-08.
- A Wave 1 ainda depende do Plano 02-03.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-20*
