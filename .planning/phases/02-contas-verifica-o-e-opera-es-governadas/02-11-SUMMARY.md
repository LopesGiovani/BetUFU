---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 11
subsystem: identity-session-security-spike
tags: [keycloak, oidc, backchannel-logout, testcontainers, session-security]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Serviço único de encerramento de sessões por identidade entregue pelo Plano 02-09
provides:
  - Medição empírica A-3 contra Keycloak 26.7.1 real
  - Decisão arquitetural plugin-evento-keycloak para D-40
  - Replanejamento verificado da origem Keycloak até o encerramento de sessões na BETUFU
affects: [02-24, 02-28, 02-30, 02-31, 02-29, AUTH-04, IDEN-05, D-40]

actuals:
  tokens: not-recorded
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - Spike hermético com Keycloak e Mailpit fixados por digest
    - Controle positivo que rejeita prova vazia de ausência de backchannel
    - Planejamento em camadas com ativação somente depois da origem e do destino endurecidos

key-files:
  created:
    - backend/src/test/java/br/com/betufu/platform/session/PasswordResetTriggerSpikeIT.java
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-28-PLAN.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-29-PLAN.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-30-PLAN.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-31-PLAN.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/COVERAGE.md
  modified:
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-MEASUREMENTS.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-RESEARCH.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-11-PLAN.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-24-PLAN.md
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-VALIDATION.md
    - .planning/ROADMAP.md
    - .planning/STATE.md

key-decisions:
  - "O reset hospedado do Keycloak 26.7.1 preserva a sessão SSO, o identificador da sessão e o refresh token anterior; não há backchannel logout observável."
  - "D-40 será implementada por listener do evento canônico UPDATE_CREDENTIAL com credential type password; UPDATE_PASSWORD legado é ignorado."
  - "A entrega usará outbox durável na origem, HTTPS com HMAC e proteção contra replay, e inbox idempotente PostgreSQL na BETUFU, sem broker no MVP."
  - "A ativação do realm e do Compose ocorrerá somente no Plano 02-29, depois da implementação e do hardening dos dois lados."

requirements-completed: []

coverage:
  - id: D1
    description: A-3 mede no fluxo hospedado real se resetar a senha preserva sessão e refresh token ou emite backchannel logout.
    requirement: AUTH-04
    verification:
      - kind: integration
        ref: PasswordResetTriggerSpikeIT#spikeMeasuresPasswordResetPreservesUserSessionAndEmitsNoBackchannelLogout
        status: pass
    human_judgment: false
  - id: D2
    description: O controle positivo prova que o receptor recebe e valida logout_token quando o Keycloak realmente emite backchannel.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: PasswordResetTriggerSpikeIT#positive-control
        status: pass
    human_judgment: false
  - id: D3
    description: A decisão D-40 preserva encerramento automático e imediato por uma arquitetura durável, autenticada e planejada antes da ativação.
    requirement: D-40
    verification:
      - kind: decision
        ref: 02-MEASUREMENTS.md#medição-a-3
        status: pass
      - kind: plan_review
        ref: 02-24, 02-28, 02-30, 02-31 e 02-29
        status: pass
    human_judgment: true

duration: not-recorded
completed: 2026-08-22
status: complete
---

# Phase 2 Plan 11: Gatilho de segurança após redefinição de senha

**A BETUFU agora possui evidência real de que o reset hospedado do Keycloak não encerra sessões e uma arquitetura aprovada e executável para fechar essa lacuna sem depender de comportamento presumido.**

## Accomplishments

- Executou o fluxo completo de redefinição com Keycloak 26.7.1 e Mailpit reais, fixados por digest.
- Provou que havia uma sessão antes e depois do reset, com o mesmo identificador, e que o refresh token anterior continuava válido com HTTP 200.
- Observou zero callbacks de backchannel por 10 segundos e recebeu exatamente um callback válido no logout administrativo de controle.
- Inspecionou realm, cliente e fluxo `reset credentials`, sem encontrar execução nativa de revogação de sessão.
- Registrou a escolha `plugin-evento-keycloak` e converteu a decisão em cinco planos ordenados e aprovados pelo verificador.

## Decisions Made

- O listener filtra `EventType.UPDATE_CREDENTIAL` quando `Details.CREDENTIAL_TYPE` é exatamente `password`; o evento legado `UPDATE_PASSWORD` não participa do filtro.
- Reset hospedado e reset administrativo entram no escopo, com parser administrativo estrito.
- A origem grava outbox transacional no banco do Keycloak; um worker interno entrega à inbox PostgreSQL idempotente da BETUFU.
- A chamada usa HTTPS e HMAC-SHA256 sobre método, caminho, timestamp, `sourceEventId` e hash do corpo, aceitando chave atual e anterior por uma janela de replay de cinco minutos.
- O consumidor reutiliza `IdentitySessionTerminationService.terminateAllSessionsFor(subject, SECURITY)` em vez de criar um segundo mecanismo de revogação.

## Deviations from Plan

### O candidato original foi refutado pela medição

- **Issue:** o plano considerava OIDC Back-Channel Logout como candidato, mas a documentação não provava que o reset o disparava.
- **Resolution:** o spike mostrou sessão e refresh token preservados e zero callbacks; o responsável escolheu `plugin-evento-keycloak`.
- **Impact:** a implementação não ocorreu no `02-11`; ela foi replanejada antes de qualquer alteração de produção.

### Correção do evento canônico

- **Issue:** a formulação inicial mencionava `UPDATE_PASSWORD`, que é legado/deprecated no Keycloak 26.7.1.
- **Fix:** pesquisa oficial confirmou `UPDATE_CREDENTIAL` com `credential_type=password`; a errata foi propagada aos planos e medições.

### Janela negativa endurecida

- **Issue:** a primeira versão observava ausência de callback por 1 segundo, enquanto o controle positivo aceitava até 10 segundos.
- **Fix:** a observação negativa passou a verificar continuamente a lista por 10 segundos e falhar assim que qualquer callback chegar.
- **Verification:** teste focal `2/2`, seguido por Maven e Node completos verdes.

### Plano grande dividido após revisão

- **Issue:** as primeiras revisões concentravam arquivos demais no `02-24` e permitiam ativação precoce.
- **Resolution:** a cadeia foi dividida em `02-24`, `02-28`, `02-30`, `02-31` e `02-29`; cada plano tem no máximo 13 arquivos e a ativação ficou no último.

**Total deviations:** quatro correções de mecanismo, terminologia, confiança temporal e granularidade; nenhum requisito foi reduzido.

## Issues Encountered

- O Maven completo produziu avisos de Logback, Mockito/Byte Buddy e reconexões Redis/Hikari durante teardown de containers; Surefire e Failsafe classificaram todas as suítes como aprovadas.
- A primeira hipótese arquitetural exigiu pesquisa e múltiplas revisões porque ativar apenas metade do caminho criaria uma falsa garantia de encerramento de sessão.
- Avisos normais de LF/CRLF não causaram erro em `git diff --check`.

## Verification Evidence

- Teste focal corrigido: `2/2`, zero falhas, erros ou skips, com 10 segundos de observação negativa.
- Maven completo: Surefire `172/172`; Failsafe `163/163`; zero falhas, erros ou skips.
- Node completo: `536/536`, sem falhas, cancelamentos ou skips.
- Keycloak observado: uma sessão antes/depois, mesmo identificador, refresh anterior HTTP 200 e zero backchannels no reset.
- Controle positivo: logout administrativo removeu a sessão, tornou o refresh inválido e entregou exatamente um `logout_token` válido.
- Gap analysis: `46/46` itens cobertos; 18 requisitos e 28 decisões sem lacunas.
- Verificador final dos cinco planos: `VERIFICATION PASSED`.
- Revisão Codex do snapshot final: sem achado bloqueante após o endurecimento temporal.

## Commits

Nenhum commit foi criado durante a execução ou formalização. O índice Git permaneceu vazio; stage e os dois commits dependem de aprovação separada.

## User Setup Required

Nenhuma configuração manual nova. As chaves HMAC usadas nos testes foram descartáveis e restritas ao processo; nenhum segredo foi gravado em arquivo.

## Next Phase Readiness

- O Plano 02-11 está tecnicamente concluído e formalizado no worktree.
- `AUTH-04`, `IDEN-05` e D-40 ainda não estão concluídos: a implementação começa no `02-24` depois do `02-07` e termina com ativação no `02-29`.
- A Wave 4 permanece parcial somente porque o `02-22` ainda está pendente.
- Nenhum push foi realizado.

## Self-Check: PASSED

- O spike e a medição respondem às quatro perguntas A-3 sem alterar produção, realm ou infraestrutura.
- As contagens foram extraídas dos XML de Surefire/Failsafe e do resumo TAP do Node.
- O replanejamento mantém 18/18 requisitos e 28/28 decisões cobertos.
- O HEAD técnico permaneceu em `aa719f5244f08558746ff7db70b8f41d4193d7e3`; o índice Git permaneceu vazio.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-22*
