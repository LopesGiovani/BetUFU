---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 16
subsystem: verification-api
tags: [postgresql, hmac, key-rotation, concurrency, openapi, angular]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: vínculo de matrícula, HMAC multiversão e constraints do Plano 02-07
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: cliente OpenAPI governado e digest anterior do Plano 02-14
provides:
  - Provas discriminantes de rotação v1 para v2 e arbitragem concorrente no PostgreSQL
  - Contrato GET/PUT de matrícula sem dados sensíveis em respostas
  - Cliente Angular regenerado, inventário de 26 arquivos e digest aprovado
affects: [02-17, 02-18, VERI-06, VERI-07, VERI-09, PRIV-01]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - Unicidade lógica consultada sob todas as versões HMAC ativas
    - PostgreSQL como árbitro final de corridas, traduzindo constraint em erro de negócio
    - Cliente OpenAPI aceito somente por inventário e digest aprovados

key-files:
  created:
    - backend/src/test/java/br/com/betufu/verification/UfuEnrollmentRotationIT.java
    - backend/src/test/java/br/com/betufu/verification/UfuEnrollmentConcurrencyIT.java
    - frontend/src/app/api/generated/api/enrollment.service.ts
    - frontend/src/app/api/generated/model/enrollment-registration-request.ts
    - frontend/src/app/api/generated/model/enrollment-status-response.ts
  modified:
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - scripts/validate-angular-route-boundary.mjs

key-decisions:
  - "O inventário de 26 arquivos e o digest 31070F37E8A2F8022A75492A13F06D14BC36DE6B04A69C4478C51EFBA673C330 foram aprovados pelo responsável em 2026-08-22."
  - "A resposta de matrícula publica somente status e createdAt; matrícula é exclusivamente entrada."
  - "A mensagem do gate passou a derivar a quantidade da allowlist, evitando texto numérico obsoleto."

requirements-completed: [VERI-06, VERI-07, VERI-09]

coverage:
  - id: D1
    description: Matrícula gravada com v1 continua bloqueando duplicidade depois de v2 virar corrente.
    requirement: VERI-07
    verification:
      - kind: integration
        ref: UfuEnrollmentRotationIT
        status: pass
      - kind: other
        ref: mutação de busca somente pela chave corrente observada vermelha
        status: pass
    human_judgment: false
  - id: D2
    description: Requisições concorrentes deixam exatamente um vínculo ativo e não vazam erro do banco.
    requirement: VERI-07
    verification:
      - kind: integration
        ref: UfuEnrollmentConcurrencyIT
        status: pass
      - kind: other
        ref: mutação de remoção da unique constraint observada vermelha
        status: pass
    human_judgment: false
  - id: D3
    description: OpenAPI e cliente Angular expõem matrícula somente como entrada e respostas allowlist.
    requirement: VERI-09
    verification:
      - kind: other
        ref: validate-phase1 --contract, validate-angular-route-boundary e generate-api --check
        status: pass
      - kind: other
        ref: digest e inventário aprovados pelo responsável em 2026-08-22
        status: pass
    human_judgment: true
    rationale: A aceitação da nova árvore gerada exige comparação e aprovação humana do inventário e digest.

duration: not-recorded
completed: 2026-08-22
status: complete
---

# Phase 2 Plan 16: Rotação, concorrência e contrato de matrícula

**A matrícula mantém unicidade durante rotação HMAC e corrida concorrente, com contrato OpenAPI e cliente Angular governados por digest.**

## Accomplishments

- Provou com PostgreSQL real que uma matrícula registrada sob v1 é recusada quando v2 é corrente e v1 continua ativa.
- Provou que concorrência deixa uma única linha ativa e converte a derrota em resposta de negócio sanitizada.
- Publicou `GET` e `PUT /api/v1/enrollment/me` e regenerou o cliente Angular sem matrícula, digest ou versão nas respostas.
- Fixou, após aprovação humana, o inventário de 26 arquivos e o digest `31070F37E8A2F8022A75492A13F06D14BC36DE6B04A69C4478C51EFBA673C330`.

## Verification Evidence

- Mutações: busca somente pelo digest corrente ficou vermelha; remoção da unique constraint deixou 3 de 4 testes de concorrência vermelhos; ambos os arquivos foram restaurados por SHA-256.
- Maven final com chave HMAC aleatória em memória: Surefire `187/187`; Failsafe `205/205`.
- Node completo: `536/536`.
- Gates focais: `generate:api:check`, `validate-phase1 --contract` e `validate-angular-route-boundary` passaram.
- `git diff --check` passou; o aviso LF para CRLF é informativo.

## Decisions Made

- O responsável aprovou literalmente o digest e o inventário em 2026-08-22.
- O modelo de resposta permanece fechado em `status` e `createdAt`; `matricula` existe apenas no request.

## Deviations from Plan

- `frontend/scripts/generate-api.mjs` não constava no frontmatter, mas sua alteração era exigida pela Task 3 para fixar a constante aprovada.
- A mensagem estática “13 caminhos” em `validate-angular-route-boundary.mjs` foi substituída pela quantidade derivada da allowlist; não houve ampliação de regra.
- Uma execução paralela de dois gates disputou corretamente o lock do gerador; o gate foi repetido sozinho e passou.

## Known Limitations

- A dívida preexistente sobre mutações semânticas interceptadas primeiro pelo digest permanece registrada em `STATE.md`; não foi ampliada nem resolvida neste plano.

## User Setup Required

Nenhuma ação imediata. Chaves reais continuam fora do Git e obedecem ao procedimento governado de HMAC.

## Next Phase Readiness

- O cliente de matrícula está pronto para as UIs consumidoras.
- A rotação real continua sendo operação governada; esta entrega prova o comportamento, mas não promove v2 em produção.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-22*
