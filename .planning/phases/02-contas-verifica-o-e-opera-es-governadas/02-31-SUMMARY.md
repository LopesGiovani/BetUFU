---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 31
subsystem: platform-session
tags: [password-reset, hmac, recovery, retention, audit, openapi]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: outbox endurecida do 02-28, inbox assinado do 02-30 e authorities/lookup do 02-17
provides:
  - Verificação HMAC current+previous com chave mínima de 32 bytes e janela exata de cinco minutos
  - Claim durável antes do efeito, retomada após crash e retenção segura do inbox
  - Operação administrativa com duas guardas independentes e auditoria transacional
  - OpenAPI, cliente gerado, validador e runbook do recovery
affects: [02-29, AUTH-04, IDEN-05, OPER-06, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - Claim persistido antes do efeito externo e lease recuperável após reinício
    - Auditoria de intenção e resultado em fronteiras transacionais separadas
    - Defesa em profundidade com matcher Spring e consulta atual da concessão global
    - Retenção por estado: somente DELIVERED antigo é removido

key-files:
  created:
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventRecoveryController.java
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventMaintenanceConfiguration.java
    - backend/src/test/java/br/com/betufu/platform/session/passwordreset/PasswordResetDeliveryRecoveryIT.java
    - backend/src/main/resources/db/migration/V8__add_password_reset_replay_audit_action.sql
    - scripts/validate-password-reset-recovery.mjs
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-PASSWORD-RESET-RECOVERY.md
  modified:
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetEventInboxService.java
    - backend/src/main/java/br/com/betufu/platform/session/passwordreset/PasswordResetSignedEnvelopeVerifier.java
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs

key-decisions:
  - "O destino aceita exatamente a chave corrente e a anterior, ambas Base64 com ao menos 32 bytes decodificados."
  - "O timestamp textual assinado é validado sem normalização e só entra na janela inclusiva de ±5 minutos."
  - "O recovery humano permanece na cadeia OIDC/CSRF; a cadeia M2M HMAC continua exata, stateless e separada."
  - "Um administrador precisa passar pelo matcher e por uma consulta atual de ADMINISTRADOR_GERAL global; uma concessão escopada não basta."
  - "O contrato público de recovery acompanha este commit para que OpenAPI, cliente e endpoints existam juntos no histórico."

requirements-completed: [AUTH-04, IDEN-05, OPER-06, PRIV-01]

coverage:
  - id: D1
    description: Envelope fora da janela, com terceira chave ou adulterado falha fechado antes do efeito.
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: PasswordResetEventInboxIT
        status: pass
    human_judgment: false
  - id: D2
    description: Crash após claim preserva estado recuperável e não duplica encerramento.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: PasswordResetDeliveryRecoveryIT
        status: pass
    human_judgment: false
  - id: D3
    description: Somente ADMINISTRADOR_GERAL global consulta e reprocessa FAILED, com auditoria sem dados sensíveis.
    requirement: OPER-06
    verification:
      - kind: integration
        ref: PasswordResetDeliveryRecoveryIT e AuditEventIT
        status: pass
      - kind: unit
        ref: validate-password-reset-recovery 19/19
        status: pass
    human_judgment: false
---

# Plano 02-31 — Hardening e operação do recovery de senha

## Verification Evidence

- Snapshot combinado final: Surefire `217/217`, Failsafe `276/276`, módulo Keycloak `56/56` e Node `580/580`, todos sem falhas.
- `validate-password-reset-recovery` terminou com `19/19` provas e verificou os mappings reais, as duas guardas, o driver de manutenção e o runbook.
- Revisão externa final em `REVIEW.md`: `PASS`, 48 arquivos revisados e zero achados P0–P2.
- Os 48 caminhos não documentais do histórico normalizado são idênticos por blob ao snapshot aprovado.
- Digest final do cliente, reproduzido por duas gerações offline e aprovado: `FE9616E8FF0DF72BED8B336A1CF9BBA759C5CBF45A114B8E397F09FABD493478`.

## Decisions Made

- O claim é commitado antes do encerramento de sessões. Se o processo cair, outro ciclo recupera o registro após o lease; se o efeito for parcial, o estado permanece `FAILED`.
- O replay administrativo grava a intenção junto do claim e o resultado junto do estado final. Uma falha de auditoria impede a declaração falsa de `DELIVERED`.
- O cleanup remove somente `DELIVERED` com mais de 30 dias. `FAILED` permanece como evidência até resolução autorizada.
- A manutenção agendada nasce desabilitada e bounded; ativação produtiva continua bloqueada pelo Plano 02-29.

## Deviations from Plan

- O OpenAPI, o gerador e os arquivos gerados do recovery foram transferidos do 02-17 para este plano no fechamento. A árvore final não mudou; a alteração mantém a paridade código ↔ contrato em cada commit e torna o histórico adequado a `git bisect`.
- `PasswordResetHermeticTracerIT.java` entrou na allowlist porque a remoção da chave legada `keyV1` exigiu atualizar a fixture. A emenda foi ratificada antes da correção.
- O adaptador local `.agents/agy-wave.mjs` recebeu suporte a `/**` e timeout maior, mas `.agents/` é ignorado e não integra o produto.

## Issues Encountered

- A revisão encontrou doze problemas nas primeiras rodadas, incluindo chave curta, claim não durável, estado falso de sucesso parcial, autorização insuficiente e paridade OpenAPI unidirecional. Todos foram corrigidos antes do `PASS`; a lista completa está em `REVIEW.md`.
- Algumas mutações inicialmente permaneceram verdes porque atingiam um ramo diferente do que o teste exercitava. Foram adicionadas provas discriminantes específicas antes de aceitar a correção.

## User Setup Required

Nenhum segredo real deve ser registrado no Git. Chaves e versões continuam externas; owner, backup e procedimento operacional estão em `02-PASSWORD-RESET-RECOVERY.md`.

## Next Phase Readiness

- A Wave 8 está pronta, mas não ativa a integração D-40 em produção.
- O 02-29 continua bloqueado até provar a cadeia real Keycloak → dispatcher → endpoint e a validação criptográfica real do Bearer no Keycloak.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-23*
