---
phase: 01-funda-o-segura-da-plataforma
plan: "07"
subsystem: identity-security
tags: [oidc, userinfo-opt-out, token-revocation, keycloak, spring-security, testcontainers]

requires:
  - phase: 01-05
    provides: "Vínculo local `(iss, sub)` idempotente e concorrente em PostgreSQL"
  - phase: 01-06
    provides: "Sessão BFF Redis com expiração ociosa seletiva e razões de término"
provides:
  - "Opt-out verificável do `userinfo_endpoint`, aplicado por bean e não apenas por escopo"
  - "Projeção allowlist de exatamente cinco claims lidas do ID token validado"
  - "Revogação server-side do refresh token pelo `revocation_endpoint` descoberto"
  - "Estado pendente com TTL, single-flight e retry após falha remota"
  - "Prova contra Keycloak real de `invalid_grant` no refresh posterior"
affects: [01-08, 01-22, 01-16, backend, security, session, identity]

actuals:
  tokens: 41000
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - "Opt-out de UserInfo pelo predicado `retrieveUserInfo` do próprio framework, sem serviço bespoke"
    - "Endpoint OIDC resolvido por discovery metadata, com falha fechada quando ausente"
    - "Estado local terminal antes da chamada remota, com material retido para retry dentro do TTL"
    - "Teste de mutação: provar que a prova nova falha contra o código antigo antes de confiar nela"

key-files:
  created:
    - "backend/src/main/java/br/com/betufu/identity/internal/OidcClaimsProjection.java"
    - "backend/src/main/java/br/com/betufu/platform/security/OidcRevocationClient.java"
    - "backend/src/main/java/br/com/betufu/platform/session/OidcSessionRevocationService.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SessionInvalidationListener.java"
    - "backend/src/test/java/br/com/betufu/identity/UserInfoOptOutIT.java"
    - "backend/src/test/java/br/com/betufu/platform/session/OidcRevocationContractTest.java"
    - "backend/src/test/java/br/com/betufu/platform/session/OidcRevocationKeycloakIT.java"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-07-SUMMARY.md"
  modified:
    - "backend/src/main/java/br/com/betufu/identity/internal/LocalIdentityController.java"
    - "backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-22-PLAN.md"

key-decisions:
  - "O opt-out de UserInfo é aplicado por um bean `OAuth2UserService` com `setRetrieveUserInfo(false)`; depender só do escopo `openid` deixaria a decisão silenciosamente reversível."
  - "A claim `iss` chega como `java.net.URL` após a conversão real do `OidcIdTokenDecoderFactory`; a projeção aceita `String`/`URL`/`URI` por `toString()`, que é byte-idêntico ao valor cru e preserva a chave natural do 01-05."
  - "`sub`, `preferred_username` e `acr` não são type-converted e mantêm verificação estrita de `String`."
  - "O `revocation_endpoint` vem sempre da metadata de discovery; ausência falha fechado, sem URL derivada."
  - "A ordem é capturar → remover authorized client → chamar remoto; o material fica retido no estado pendente para retry dentro do TTL, e falha remota nunca revive a sessão."
  - "O `PENDING_TTL` limita reuso, não retenção: a purga é oportunista e o material pode permanecer em memória além da janela."
  - "O cliente de revogação tem connect 5s e read 10s; sem eles um provedor pendurado travaria o single-flight e transformaria falha em silêncio."
  - "O Plano 01-07 tocou 9 paths de código contra os 8 declarados; a ampliação foi ratificada pelo responsável."

patterns-established:
  - "Prova de discriminação obrigatória: mutar o código para o comportamento antigo e exigir que o teste novo falhe."
  - "Fixture de IdP com `com.sun.net.httpserver.HttpServer` do JDK, sem dependência nova e compatível com build offline."
  - "Contadores de requisição no fixture provam tanto o que foi enviado quanto o que nunca foi enviado."

requirements-completed: [IDEN-01, IDEN-02, IDEN-03, IDEN-04]

coverage:
  - id: D1
    description: "O opt-out do `userinfo_endpoint` é aplicado pela aplicação, com zero chamadas mesmo quando o endpoint está indisponível."
    requirement: IDEN-01
    verification:
      - kind: integration
        ref: "UserInfoOptOutIT#loginBuildsThePrincipalFromTheIdTokenWithoutCallingUserInfo"
        status: pass
      - kind: integration
        ref: "UserInfoOptOutIT#persistsTheIdentityWhileUserInfoIsUnavailable"
        status: pass
    human_judgment: false
  - id: D2
    description: "A projeção expõe exatamente cinco claims allowlisted e aceita `iss` na forma type-converted que o login real produz."
    requirement: IDEN-02
    verification:
      - kind: integration
        ref: "UserInfoOptOutIT#projectionExposesOnlyTheFiveAllowlistedClaims"
        status: pass
      - kind: integration
        ref: "UserInfoOptOutIT#acceptsTheIssuerInTheTypeConvertedFormThatRealLoginsProduce"
        status: pass
    human_judgment: false
  - id: D3
    description: "Claims de identidade ausentes rejeitam sem persistir; Unicode e caixa preservam a comparação exata."
    requirement: IDEN-02
    verification:
      - kind: integration
        ref: "UserInfoOptOutIT#missingOrBlankIdentityClaimsAreRejectedWithoutPersisting"
        status: pass
      - kind: integration
        ref: "UserInfoOptOutIT#optionalUnicodeClaimsDoNotDisturbTheExactIdentityComparison"
        status: pass
    human_judgment: false
  - id: D4
    description: "Repetição e concorrência convergem para uma identidade única, sem inferir papel de domínio."
    requirement: IDEN-01
    verification:
      - kind: integration
        ref: "UserInfoOptOutIT#repeatedAndConcurrentSyncConvergeOnOneIdentityWithoutDomainRole"
        status: pass
    human_judgment: false
  - id: D5
    description: "A revogação usa endpoint de discovery, envia apenas refresh token e hint, autentica como cliente confidencial e redige token e corpo remoto."
    requirement: IDEN-03
    verification:
      - kind: unit
        ref: "OidcRevocationContractTest — 12/12"
        status: pass
    human_judgment: false
  - id: D6
    description: "Falha remota mantém a sessão terminal, preserva o material para retry dentro do TTL e libera o single-flight."
    requirement: IDEN-03
    verification:
      - kind: unit
        ref: "OidcRevocationContractTest#aFailedRevocationIsRetriableFromTheRetainedMaterial"
        status: pass
      - kind: unit
        ref: "OidcRevocationContractTest#aHungProviderSurfacesAsRemoteFailureAndReleasesTheGuard"
        status: pass
    human_judgment: false
  - id: D7
    description: "Keycloak real rejeita o refresh grant com `invalid_grant` após a revogação, sem alegar rejeição imediata do access JWT."
    requirement: IDEN-03
    verification:
      - kind: integration
        ref: "OidcRevocationKeycloakIT#revokedRefreshTokenIsRejectedWithInvalidGrant"
        status: pass
    human_judgment: false
  - id: D8
    description: "Prontidão operacional da revogação administrativa/de segurança."
    requirement: IDEN-04
    verification: []
    human_judgment: true
    rationale: "O `SessionInvalidationListener` não possui consumidor em produção: nenhum plano da Fase 1 assumiu os gatilhos `ADMINISTRATIVE`/`SECURITY`. A prontidão depende de decisão de roadmap, não de teste."

duration: "não mensurada — execução interativa com checkpoints, correções e duas rodadas de revisão cruzada"
completed: 2026-08-14
status: complete
---

# Phase 1 Plan 07: UserInfo opt-out e revogação OIDC Summary

**O BFF passou a recusar deliberadamente o `userinfo_endpoint` e a revogar o refresh token pelo endpoint descoberto, com prova contra Keycloak real e sem que token algum saia do servidor.**

## Desempenho

- **Conclusão:** 2026-08-14
- **Tasks:** 2
- **Paths de código:** 9 (7 criados, 2 modificados)
- **Commits registrados:** 2
- **Escopo:** projeção allowlist de claims, opt-out aplicado por bean, cliente de revogação por discovery, serviço com TTL e retry, e a costura de invalidação.

## Entregas

- `OidcClaimsProjection` declara a allowlist estruturalmente: um record de exatamente cinco componentes, lido do ID token validado e nunca do claim set mesclado.
- `SecurityConfiguration` declara o `OAuth2UserService` com `setRetrieveUserInfo(request -> false)`. Sem esse bean, o opt-out dependeria só do escopo `openid` e voltaria em silêncio se alguém acrescentasse `profile`.
- `OidcRevocationClient` resolve o `revocation_endpoint` da metadata de discovery, envia `token` e `token_type_hint=refresh_token` autenticado como cliente confidencial, e não possui sobrecarga que aceite access token.
- `OidcSessionRevocationService` age apenas em `ADMINISTRATIVE` e `SECURITY`, captura o material, torna o estado local terminal e só então chama o provedor, com single-flight e retry dentro do TTL.
- `SessionInvalidationListener` fixa o contrato de entrada e registra as obrigações do chamador.

## Evidências de verificação

| Prova | Resultado |
|---|---|
| Maven offline agregado | PASS — `BUILD SUCCESS`; Surefire `39/39` e Failsafe `39/39`, total `78/78`, sem failure, error ou skip. |
| UserInfo opt-out | PASS — `UserInfoOptOutIT 7/7` contra PostgreSQL 18.4 real, com endpoint UserInfo respondendo 500 e contador em zero. |
| Contrato de revogação | PASS — `OidcRevocationContractTest 12/12`, cobrindo discovery, forma exata do corpo, redaction, idempotência, concorrência, retry e TTL. |
| Keycloak real | PASS — `OidcRevocationKeycloakIT 3/3`, com Authorization Code real e PKCE, exigindo `invalid_grant` no refresh posterior. |
| Cleanup Docker | PASS — 34 containers, 6 redes e 23 volumes restaurados; resíduos `org.testcontainers` `0/0/0`. |
| Revisão externa | PASS — sem achados P0, P1 ou P2 em aberto após duas rodadas. |

## Achados corrigidos durante a revisão cruzada

1. **P0 — `iss` chega como `java.net.URL`.** A verificação estrita de `String` teria devolvido 401 em todo login de produção, enquanto os 74 testes verdes seguiam passando por construírem o token à mão. Corrigido e coberto por teste que falha contra o código antigo.
2. **P1 — material destruído sem possibilidade de retry.** A falha remota descartava a única cópia do refresh token, deixando o grant vivo no IdP sem caminho de nova tentativa.
3. **P2 — cliente sem timeout.** Um provedor pendurado travaria o single-flight e degradaria para "nada a revogar", reintroduzindo a falha silenciosa por outro caminho.
4. **P2 — divergência entre comentário e comportamento** na retenção do material pendente.

## Riscos residuais

Registrados em `01-22-PLAN.md`, seção *Pendências herdadas do Plano 01-07*: refresh token em memória do processo, ajuste do `capture()` se houver storage durável, `NO_REFRESH_TOKEN` sobrecarregado, obrigações do chamador, retenção não limitada pelo TTL e ausência de asserção sobre os timeouts do bean de produção.

**Nenhum plano da Fase 1 assumiu os gatilhos `ADMINISTRATIVE`/`SECURITY`.** `01-CONTEXT.md:75` delega essas regras a agentes posteriores, mas a delegação está sem destinatário. Enquanto isso durar, o `SessionInvalidationListener` permanece sem consumidor em produção — o que também significa que as pendências acima não têm caminho de produção que as alcance hoje.

Cinco regressões negativas do validador Node estão inertes em checkout Windows: `core.autocrlf=true` deixa o working copy em CRLF e as fixtures mutam com strings `\n`. É buraco de cobertura do projeto, não do 01-07.

## Requisitos e próximo passo

`IDEN-01` a `IDEN-04` aparecem no frontmatter como contribuição local deste plano. Eles **não estão encerrados globalmente**; `.planning/REQUIREMENTS.md` permanece pendente até as entregas compartilhadas da Fase 1.

A Wave 12 continua com o Plano 01-21 em execução paralela. O 01-22 depende deste plano e recebe as pendências registradas.

## Autoavaliação: APROVADA

- [x] As duas tasks e os 9 paths de código foram entregues.
- [x] O opt-out de UserInfo é aplicado pela aplicação e provado com endpoint indisponível.
- [x] A revogação usa discovery, redige material sensível e prova `invalid_grant` contra Keycloak real.
- [x] Maven offline agregado passou com 78/78 e o cleanup Docker foi restaurado.
- [x] A revisão externa encerrou em PASS, com as pendências P3 registradas.
- [x] Requisitos compartilhados não foram encerrados prematuramente.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-14*
