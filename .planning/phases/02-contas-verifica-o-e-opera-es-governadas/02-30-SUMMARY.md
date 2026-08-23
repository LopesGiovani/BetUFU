---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 30
subsystem: platform-session
tags: [password-reset, inbox, hmac, session-termination, csrf, flyway]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: encerramento de sessões do Plano 02-09 e outbox de origem do Plano 02-24
provides:
  - Inbox durável com unicidade por sourceEventId e resolução de concorrência no banco
  - Endpoint interno stateless fechado por HMAC exato com comparação em tempo constante
  - Encerramento de todas as sessões do sujeito por motivo SECURITY
  - Exceção de CSRF governada por invariantes estruturais na única rota M2M
affects: [02-29, 02-31, AUTH-04, IDEN-05, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 1

tech-stack:
  added: []
  patterns:
    - Insert-first idempotente com a restrição do banco arbitrando concorrência
    - Erro persistido como código estável, nunca como classe ou mensagem de exceção
    - Validador troca proibição por invariantes estruturais quando a exceção é legítima

key-files:
  created:
    - backend/src/main/resources/db/migration/V5_1__create_password_reset_event_inbox.sql
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventEndpoint.java
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventInboxService.java
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetSignedEnvelopeVerifier.java
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventSecurityConfiguration.java
    - backend/src/test/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventInboxIT.java
    - backend/src/test/java/br/com/betufu/platform/session/passwordreset/PasswordResetHermeticTracerIT.java
  modified:
    - scripts/validate-runtime-prerequisites.mjs
    - scripts/validate-runtime-prerequisites.test.mjs
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java

key-decisions:
  - "O destino é autoritativo sobre o contrato de transporte: recusa qualquer valor de payload que não seja VALUE_STRING."
  - "A concorrência é resolvida pela restrição única do banco, não por lock em memória."
  - "Falha de encerramento grava o código estável TERMINATION_FAILED; o detalhe segue disponível pelo rethrow."
  - "A exceção de CSRF não ignora o arquivo: troca a proibição por invariantes estruturais que mantêm a rota estreita."
  - "A V5_1 não tem colunas de claim/lease nem de erro cru: são semântica da origem e superfície de vazamento contra PRIV-01."
  - "O endpoint interno é stateless e fecha por HMAC exato com comparação em tempo constante; não aceita bearer nem cookie como substituto da assinatura e falha fechado sem chave."
  - "A chave é sempre Base64, sem fallback para bytes UTF-8 crus: a mesma string configurada podia resolver para duas chaves diferentes."

requirements-completed: [AUTH-04, IDEN-05, PRIV-01]

coverage:
  - id: D1
    description: Entrega duplicada ou concorrente não produz duplo encerramento; a unicidade do banco arbitra.
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: PasswordResetEventInboxIT — insert-first e idempotência
        status: pass
    human_judgment: false
  - id: D2
    description: O inbox nunca persiste classe nem mensagem cruas da exceção.
    requirement: PRIV-01
    verification:
      - kind: integration
        ref: PasswordResetEventInboxIT#sessionTerminationFailurePreservesFailedStatusForRecovery
        status: pass
      - kind: other
        ref: mutação M15 restaurando a exceção crua observada vermelha
        status: pass
    human_judgment: false
  - id: D3
    description: A única rota autorizada a desativar CSRF permanece estreita, stateless e sem matcher curinga.
    requirement: AUTH-04
    verification:
      - kind: unit
        ref: quatro provas Node do validador de pré-requisitos de runtime
        status: pass
    human_judgment: false

---

# Plano 02-30 — Inbox assinado e encerramento de sessões

## Verification Evidence

- Árvore deste commit: backend Surefire `187/187`, Failsafe `229/229`, Node `540/540`.
- Árvore combinada da onda: Surefire `201/201`, Failsafe `241/241`, módulo Keycloak `56/56`, Node `540/540`.
- Mutação M15, restaurando a persistência da exceção crua, observada vermelha; restauração conferida por SHA-256 com `touch` forçado.
- Hash final do serviço de inbox: `B45EF2B4A915D9AC603A2523F22DA682175F8A52FE052D8C8B824CC126ABB19F`.
- Hashes dos validadores: `83B7F6689A9746CE42FA3FCF2D97950D63F1A7E1CF901131DAA9007394431CEC` e `C55B03B0D939D3A2CC5A55665B8C1F62EB7421322F8F932BF21B0DEA89974988`.

## Decisions Made

- O erro persistido virou o código estável `TERMINATION_FAILED`. A mensagem de uma `RuntimeException` carrega o que a causa tiver colocado nela — host, SQL, caminho, identificador do titular — e a linha do inbox é lida por operador. O rethrow preserva a exceção original para o tratamento e o log da borda.
- O validador Node deixou de proibir `csrf disable` por arquivo. A rota M2M é stateless, não usa cookie nem sessão e autentica por HMAC exato, então CSRF não se aplica a ela. A exceção não ignora o arquivo: exige `securityMatcher` no `ENDPOINT_PATH`, `SessionCreationPolicy.STATELESS` e ausência de matcher curinga.

## Deviations from Plan

- **Três emendas de allowlist, ratificadas antes da edição:** os dois `scripts/validate-runtime-prerequisites*` e o `FlywayMigrationIT.java`.
- A reconciliação do `FlywayMigrationIT` para 6 migrations foi trazida para dentro deste commit. **Antes disso o commit era vermelho isoladamente:** ele cria a `V5_1` mas o arquivo de contagem pertencia à allowlist do 02-23, então qualquer `git bisect` sobre a onda daria falso positivo entre este commit e o do 02-23. O defeito não constava de nenhum achado de revisão; apareceu porque a dobra obrigou a medir cada commit individualmente.

## Issues Encountered

- Um teste **assertava o vazamento**: exigia que a classe e a mensagem cruas da exceção estivessem persistidas. Foi invertido para exigir o código estável e a ausência do texto interno.

## User Setup Required

Nenhuma. As execuções exigem `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION` e `BETUFU_MATRICULA_HMAC_KEY_V1`, conforme `02-CHAVE-HMAC-PROCEDIMENTO.md`.

## Next Phase Readiness

- **Limite de evidência inalterado:** `PasswordResetHermeticTracerIT` é simulação contratual. Ele monta a representação do evento e assina com as constantes do destino, sem exercitar a outbox nem o dispatcher reais. Origem e destino foram alinhados por inspeção, nunca por execução conjunta.
- A prova E2E real da cadeia D-40 permanece gate bloqueante do 02-29.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-23*
