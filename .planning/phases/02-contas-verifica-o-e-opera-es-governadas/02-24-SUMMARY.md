---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 24
subsystem: identity-outbox
tags: [keycloak, spi, jpa, liquibase, outbox, password-reset]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: identidade local e contrato de sessão dos Planos 02-07 e 02-11
provides:
  - Listener Keycloak Java 21 para resets hospedado e administrativo canônicos
  - Outbox JPA transacional com unicidade por sourceEventId e migration Liquibase
  - Prova focal de rollback, idempotência, ServiceLoader e rejeições de origem
affects: [02-28, 02-29, 02-30, 02-31, AUTH-04, IDEN-05, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: [Keycloak SPI 26.7.1, Liquibase transitive 4.33.0]
  patterns:
    - Callback privilegiado realiza somente persistência JPA na transação do Keycloak
    - Parser administrativo estrito usa resourcePath e nunca getResourceId
    - Outbox local separa captura de evento da futura entrega BETUFU

key-files:
  created:
    - keycloak-password-event-listener/pom.xml
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/BetufuPasswordEventListener.java
    - keycloak-password-event-listener/src/main/java/br/com/betufu/keycloak/password/PasswordResetOutboxEntity.java
    - keycloak-password-event-listener/src/main/resources/META-INF/betufu-password-reset-outbox-changelog.xml
    - keycloak-password-event-listener/src/test/java/br/com/betufu/keycloak/password/PasswordResetProviderTracerIT.java
  modified: []

key-decisions:
  - "UPDATE_CREDENTIAL/password e USER/ACTION users/{id}/reset-password são as únicas origens aceitas nesta etapa."
  - "O callback não faz HTTP; a entrega e invalidação de sessões pertencem aos planos dependentes."
  - "Realm, compose, imagem e backend permanecem intocados até a ativação governada do Plano 02-29."

requirements-completed: [AUTH-04, IDEN-05, PRIV-01]

coverage:
  - id: D1
    description: Reset hospedado canônico persiste uma linha PENDING na mesma transação e rollback não deixa resíduo.
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: PasswordResetProviderTracerIT#updateCredentialPassword e transactionRollback
        status: pass
      - kind: other
        ref: mutação da allowlist de credential type observada vermelha
        status: pass
    human_judgment: false
  - id: D2
    description: Reset administrativo extrai o usuário exclusivamente de users/{id}/reset-password.
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: PasswordResetProviderTracerIT#adminEvent_poisonResourceId
        status: pass
      - kind: other
        ref: mutação para getResourceId observada com duas falhas
        status: pass
    human_judgment: false
  - id: D3
    description: Provider JPA aplica migration idempotente e unicidade de sourceEventId.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: PasswordResetProviderTracerIT#liquibaseAppliesOutboxChangelog
        status: pass
      - kind: other
        ref: mutação unique=true para false observada vermelha
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-22
status: complete
---

# Phase 2 Plan 24: Origem transacional de reset de senha no Keycloak

**Os dois resets de senha canônicos agora produzem uma outbox JPA mínima e transacional, ainda sem qualquer ativação produtiva ou chamada HTTP.**

## Accomplishments

- Criou módulo Java 21 com listener e factories descobertos por ServiceLoader no Keycloak 26.7.1.
- Captura somente `UPDATE_CREDENTIAL/password` e `USER/ACTION users/{id}/reset-password` sem erro.
- Persiste `sourceEventId`, sujeito, realm, tipo, instante e estado de entrega; não persiste senha, e-mail, username ou token.
- Aplicou migration Liquibase real duas vezes em banco isolado e comprovou unicidade por `source_event_id`.
- Manteve realm, compose, Dockerfile, backend e entrega HTTP fora do escopo.

## Verification Evidence

- Teste focal e módulo completo: `26/26` no Surefire.
- Mutação da allowlist de credencial: 1 falha esperada; restauração pelo hash do listener.
- Mutação do parser para `getResourceId()`: 2 falhas esperadas; restauração pelo hash do listener.
- Mutação `unique=true` para `false`: 1 falha esperada na metadata real; restauração pelo hash do changelog.
- Backend completo no snapshot da 02-24: Surefire `187/187`; Failsafe `196/196`.
- Node completo: `536/536`; `git diff --check` passou.
- Hash final do listener: `368B2C3BB0278E76D597D9E56DEA1E13F49582E6B81D8A4484E7981325B779DF`.
- Hash final do teste: `371851279DAB68FD93B76ADFB03CBA331BB4FA0ECB9432553024C66B8BF6970D`.

## Decisions Made

- O subject administrativo vem apenas do grupo capturado no `resourcePath`; `getResourceId()` é tratado como fonte não confiável.
- Persistência participa da transação JPA do Keycloak, garantindo que rollback do reset também reverta a outbox.
- A dependência Liquibase 4.33.0 é fornecida transitivamente por `keycloak-model-jpa:26.7.1`; nenhuma coordenada adicional foi adicionada ao produto.

## Deviations from Plan

- `slopcheck` foi deliberadamente dispensado pelo responsável após explicação; todas as coordenadas Maven foram verificadas diretamente e responderam HTTP 200.
- O primeiro teste de migration usava `hibernate create-drop` e apenas lia o XML. A revisão detectou a prova fraca; o teste passou a executar Liquibase real e recebeu mutação discriminante.
- A primeira variante administrativa usou um enum inexistente no teste; a API real do JAR 26.7.1 foi inspecionada e o caso negativo passou a usar `REALM_ROLE`.

## Issues Encountered

- O Antigravity retornou envelopes `ERROR` em duas continuações apesar de produzir edições dentro da allowlist; nenhuma edição foi aceita sem inspeção, compilação e mutação.
- Avisos de API depreciada, JBoss LogManager e a violação SQL 23505 são evidências esperadas dos testes negativos e não reprovaram a suíte.

## User Setup Required

Nenhuma ação nesta etapa. O listener permanece desabilitado em runtime; ativação pertence ao Plano 02-29.

## Next Phase Readiness

- 02-28 pode endurecer a origem e 02-30 pode construir o destino consumidor.
- Nenhum reset invalida sessões BETUFU em produção até os planos dependentes e o gate de ativação serem concluídos.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-22*
