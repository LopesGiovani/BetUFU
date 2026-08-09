---
phase: 01-funda-o-segura-da-plataforma
plan: "17"
subsystem: auth
tags: [spring-boot, spring-security, oidc, bff, spring-modulith, mockmvc]

requires:
  - phase: 01-02
    provides: "Build Maven canônico em Java 25, Spring Boot 4.1.0 e Spring Modulith 2.1.0"
provides:
  - "Aplicação Spring Boot e primeiro módulo técnico `platform` verificados pelo Spring Modulith"
  - "Fronteira BFF OIDC confidencial, server-side e fechada por padrão"
  - "Configuração runtime sem segredos versionados e cookie de sessão protegido"
  - "Smoke test isolado de Keycloak, Redis, PostgreSQL, Docker e rede"
affects: [01-18, 01-03, 01-06, 01-07, 01-09, backend, authentication]

actuals:
  tokens: 2571
  tasks: 1
  commits: 1

tech-stack:
  added: []
  patterns:
    - "Spring atua como BFF OIDC e mantém credenciais, tokens e sessão fora do navegador"
    - "Política HTTP por allowlist pública mínima, API autenticada e negação do restante"
    - "Testes de scaffold usam ClientRegistration confidencial em memória e endpoints `.invalid`"

key-files:
  created:
    - "backend/src/main/java/br/com/betufu/BetufuApplication.java"
    - "backend/src/main/java/br/com/betufu/platform/package-info.java"
    - "backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java"
    - "backend/src/main/resources/application.yaml"
    - "backend/src/test/java/br/com/betufu/BetufuApplicationSmokeTest.java"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-17-SUMMARY.md"
  modified:
    - ".gitignore"
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "Confiar no fluxo padrão do Spring Security 7.1 para `state`, `nonce` e PKCE, sem reimplementação própria"
  - "Ativar issuer e credenciais externas somente nos profiles `runtime` ou `integrated`"
  - "Usar HttpSession em memória apenas no scaffold; Redis e timeout ocioso permanecem no Plano 01-06"
  - "Não alegar integração real com Keycloak enquanto o teste usar somente ClientRegistration em memória"

patterns-established:
  - "Configuração externa: arquivos versionados contêm somente nomes de variáveis, nunca valores secretos"
  - "Smoke OIDC: endpoints `.invalid` tornam qualquer acesso externo acidental uma falha visível"
  - "Segurança HTTP: liberar explicitamente o mínimo e negar qualquer rota não classificada"

requirements-completed: [ARCH-01, IDEN-01, IDEN-03]

coverage:
  - id: D1
    description: "O scaffold Spring Boot/Modulith sobe e verifica o módulo `platform` sob Java 25"
    requirement: ARCH-01
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/BetufuApplicationSmokeTest.java#verifiesTheInitialModulithBoundaries"
        status: pass
    human_judgment: false
  - id: D2
    description: "O BFF usa Authorization Code confidencial e mantém o fluxo OIDC no servidor"
    requirement: IDEN-03
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/BetufuApplicationSmokeTest.java#keepsTheOidcRedirectServerSideAndUsesStandardProtections"
        status: pass
    human_judgment: false
  - id: D3
    description: "Login, callback e liveness formam a superfície pública; API exige autenticação e o restante é negado"
    requirement: IDEN-03
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/BetufuApplicationSmokeTest.java#exposesOnlyTheApprovedPublicSurface"
        status: pass
    human_judgment: false
  - id: D4
    description: "Configuração runtime referencia issuer e cliente externos sem versionar credenciais reais"
    requirement: IDEN-01
    verification:
      - kind: other
        ref: "Inspeção de backend/src/main/resources/application.yaml e ClientRegistration em memória no smoke"
        status: pass
    human_judgment: false

duration: "não mensurada — execução acompanhada em múltiplos gates"
completed: 2026-08-09
status: complete
---

# Phase 1 Plan 17: Scaffold BFF OIDC Summary

**Spring Boot/Modulith inicial com fronteira BFF OIDC confidencial, rotas fechadas por padrão e smoke sem serviços externos**

## Performance

- **Duration:** não mensurada — implementação acompanhada em três blocos aprovados
- **Started:** não registrado — trabalho conduzido em múltiplas interações
- **Completed:** 2026-08-09T02:24:36-03:00
- **Tasks:** 1
- **Files modified:** 6 artefatos de entrega e 3 documentos de acompanhamento GSD

## Accomplishments

- Aplicação Spring Boot criada sob Java 25 e primeiro módulo `platform` aceito pelo `ApplicationModules.verify()`.
- BFF OIDC configurado com cliente confidencial, Authorization Code e mecanismos padrão de `state`, `nonce` e PKCE.
- Política HTTP limitada a login, callback e liveness públicos, `/api/v1/**` autenticado e negação do restante.
- Smoke executado sem Keycloak, Redis, PostgreSQL, Docker ou rede: 4 testes, 0 falhas e 0 erros.

## Task Commits

A única tarefa foi implementada em três blocos revisados pelo responsável e consolidada em um commit atômico:

1. **Task 1: criar boot e fronteira BFF OIDC** — `689de8f` (`feat`).

Este `SUMMARY`, `STATE` e `ROADMAP` compõem o fechamento documental do plano no commit de metadados correspondente.

## Files Created/Modified

- `backend/src/main/java/br/com/betufu/BetufuApplication.java` — ponto de entrada do Spring Boot.
- `backend/src/main/java/br/com/betufu/platform/package-info.java` — declaração do módulo técnico `Plataforma`.
- `backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java` — fronteira HTTP e login OIDC fechados por padrão.
- `backend/src/main/resources/application.yaml` — profiles OIDC externos, cookie protegido e liveness.
- `backend/src/test/java/br/com/betufu/BetufuApplicationSmokeTest.java` — smoke de contexto, OIDC, rotas e Modulith.
- `.gitignore` — ignora apenas o metadado de Javadoc gerado pelo processador do Spring Modulith.
- `.planning/phases/01-funda-o-segura-da-plataforma/01-17-SUMMARY.md` — registra resultados, cobertura e desvios.
- `.planning/STATE.md` — avança a posição operacional para o Plano `01-17` concluído.
- `.planning/ROADMAP.md` — atualiza o progresso da Fase 1 para `3/28`.

## Decisions Made

- O Spring Security 7.1 permanece responsável por `state`, `nonce` e PKCE; a BETUFU não implementa versões próprias desses controles.
- O navegador recebe apenas o cookie de sessão; tokens e material OIDC permanecem no servidor.
- O arquivo versionado usa somente `BETUFU_OIDC_ISSUER_URI`, `BETUFU_OIDC_CLIENT_ID` e `BETUFU_OIDC_CLIENT_SECRET`; valores reais pertencem ao ambiente.
- A sessão servlet em memória é provisória para o scaffold. Redis e a regra de oito horas não foram antecipados.

## Deviations from Plan

### Ajuste de higiene aprovado

**1. [Escopo controlado] Ignorar metadado gerado pelo Spring Modulith**

- **Found during:** compilações dos Blocos 1 e 2 e execução do smoke.
- **Issue:** `spring-modulith-apt` gerava `build/generated-spring-modulith/javadoc.json` fora dos diretórios já ignorados.
- **Fix:** adicionada a regra específica `/build/generated-spring-modulith/` ao `.gitignore`, após aprovação explícita.
- **Files modified:** `.gitignore`.
- **Verification:** `git check-ignore -v --no-index build/generated-spring-modulith/javadoc.json` confirmou a regra exata.
- **Committed in:** `689de8f`.

---

**Total deviations:** 1 ajuste de higiene aprovado.
**Impact on plan:** nenhum comportamento ou dependência foi alterado; o ajuste evita resíduos gerados no status Git.

## Issues Encountered

- A primeira compilação foi corretamente bloqueada pelo Maven Enforcer porque o terminal apontava para Java 21; a execução foi repetida com o JDK Temurin 25 já aprovado e instalado.
- A primeira execução de testes baixou componentes já fixados do Maven Surefire `3.5.6`, sem modificar o `pom.xml`.
- O JDK 25 exibiu um aviso não bloqueante sobre o auto-attach do Mockito/Byte Buddy em versões futuras; os testes atuais passaram e nenhuma configuração foi alterada silenciosamente.

## User Setup Required

None — o smoke não exige serviço externo. Os valores OIDC reais e o Keycloak serão configurados nos planos de profiles e infraestrutura.

## Next Phase Readiness

- A Onda 3 está tecnicamente concluída e habilita o Plano `01-18`, que separará os profiles dev/homolog/prod sem segredos.
- Keycloak real, Redis e a política de oito horas continuam pertencendo aos planos posteriores; este fechamento não os declara entregues.
- `ARCH-01`, `IDEN-01` e `IDEN-03` estão cobertos no escopo deste plano, mas continuam compartilhados com entregas posteriores da Fase 1.

## Self-Check: PASSED

- [x] Os cinco arquivos declarados no Plano `01-17` existem.
- [x] O commit de tarefa `689de8f` contém exatamente os cinco arquivos do scaffold e a regra específica do `.gitignore`.
- [x] `node scripts/maven.mjs -f backend/pom.xml test -Dtest=BetufuApplicationSmokeTest` terminou com `BUILD SUCCESS`.
- [x] O smoke executou 4 testes, sem falhas, erros ou testes ignorados.
- [x] `state`, `nonce` e PKCE aparecem no redirecionamento produzido pelo Spring Security.
- [x] Liveness é público, `/api/v1/**` exige autenticação e uma rota não listada retorna acesso negado.
- [x] `ApplicationModules.of(BetufuApplication.class).verify()` passou sob Java 25.
- [x] Nenhum segredo real, bearer token de SPA, Direct Grant ou integração externa foi introduzido.
- [x] O metadado gerado pelo Modulith está coberto pela regra específica do `.gitignore`.
- [x] Nenhum push ocorreu durante este fechamento.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-09*
