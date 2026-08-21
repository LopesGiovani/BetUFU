---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 04
subsystem: audit
tags: [postgresql, audit, privileges, retention, privacy]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Decisões D-25, D-37, D-50 e D-52 sobre minimização, HMAC e MFA
provides:
  - Contrato minimizado dos dados permanentes da trilha de auditoria
  - Decisão de segregar o usuário de migration do usuário da aplicação
  - Política de retenção explícita para o MVP fechado e gate anterior ao lançamento público
affects: [02-21, AUDT-01, AUDT-02, PRIV-05, infra]

actuals:
  tokens: 3803
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - Auditoria imutável protegida por privilégio mínimo e trigger no PostgreSQL
    - Retenção do MVP fechado subordinada a revisão jurídica anterior ao lançamento

key-files:
  created:
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-04-SUMMARY.md
  modified:
    - .planning/REQUIREMENTS.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "A auditoria referencia vínculo e versão da chave quando aplicável, sem armazenar o HMAC da matrícula."
  - "O MVP não registra MFA nem evidência documental na auditoria."
  - "Usuários de migration e aplicação serão segregados; o runtime não possuirá UPDATE, DELETE, TRUNCATE ou DDL sobre audit_event."
  - "Durante o MVP fechado, a auditoria é retida pela vida do ambiente; uma política definitiva é gate obrigatório anterior ao lançamento público."

patterns-established:
  - "Defesa em profundidade: privilégio mínimo impede a operação e trigger falha ruidosamente se a concessão for afrouxada."
  - "Decisão não é implementação: requisitos de auditoria permanecem pendentes até o Plano 02-21 provar o contrato."

requirements-completed: []

coverage:
  - id: D1
    description: O contrato de auditoria foi minimizado sem HMAC da matrícula, MFA ou evidência documental no MVP.
    requirement: AUDT-01
    verification:
      - kind: manual_procedural
        ref: "Checkpoint OQ-3/OQ-5 aprovado pelo responsável em 2026-08-21 e refletido em REQUIREMENTS.md"
        status: pass
    human_judgment: true
    rationale: A alteração reconcilia decisões de produto e privacidade que somente o responsável pode aprovar.
  - id: D2
    description: O contrato operacional combina segregação de usuários do banco, defesa por trigger e retenção explícita do MVP fechado.
    requirement: AUDT-02
    verification:
      - kind: manual_procedural
        ref: "Checkpoint OQ-6/OQ-7 aprovado pelo responsável em 2026-08-21"
        status: pass
    human_judgment: true
    rationale: Este plano aprova o desenho; os privilégios, o trigger e os testes serão materializados no Plano 02-21.

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 04: Contrato da trilha de auditoria

**A trilha futura foi limitada aos dados necessários, com segregação de privilégios e retenção explícita para o MVP fechado.**

## Performance

- **Duration:** não cronometrada; decisões tomadas em duas sessões
- **Started:** 2026-08-21, horário não registrado
- **Completed:** 2026-08-21T12:47:40.865Z
- **Tasks:** 2
- **Files modified:** 4, incluindo este SUMMARY e os registros globais

## Accomplishments

- Reconciliou `AUDT-01` com D-25, D-37, D-50 e D-52 sem eternizar HMAC ou criar colunas sem significado.
- Aprovou a separação entre o proprietário/migrador do schema e o usuário de runtime da aplicação.
- Definiu retenção pela vida do ambiente durante o MVP fechado e revisão jurídica obrigatória antes do lançamento público.
- Preservou `AUDT-01` e `AUDT-02` como pendentes de implementação e prova no Plano 02-21.

## Task Commits

Nenhum commit foi criado. `AGENTS.md` exige aprovação separada para stage e commit.

## Files Created/Modified

- `.planning/REQUIREMENTS.md` — remove de `AUDT-01` HMAC literal, MFA e evidência documental do MVP.
- `02-04-SUMMARY.md` — registra as quatro decisões bloqueantes e seus limites.
- `.planning/STATE.md` — corrige posição, decisões e continuidade da Fase 2.
- `.planning/ROADMAP.md` — mostra 02-04 e 02-14 concluídos e 02-06 ainda pendente na Wave 2.

## Decisions Made

### OQ-3 — HMAC da matrícula

A auditoria registra referências aos estados anterior e novo — incluindo `enrollment_id` e versão da chave quando aplicável —, mas nunca o digest. Isso permite que D-37 expurgue HMACs comprometidos da tabela de vínculo sem violar a imutabilidade da trilha.

### OQ-5 — MFA e evidência

Não existem colunas de MFA ou evidência documental no MVP. MFA é opcional e não governa autorização; a evidência documental não existe porque D-25 removeu a verificação por comprovante.

### OQ-6 — Privilégios do banco

O usuário do Flyway será separado do usuário da aplicação. O migrador será proprietário do schema; o runtime receberá somente as permissões necessárias. Sobre `audit_event`, o runtime poderá inserir e fazer as leituras aprovadas, mas não atualizar, excluir, truncar ou alterar a estrutura. Um trigger que lança erro será a segunda camada de proteção.

### OQ-7 — Retenção

No MVP fechado, os eventos são conservados pela vida do ambiente, sem exclusão automática. Antes do lançamento público, `PRIV-05` exige revisão jurídica e uma política definitiva por classe de dado. Eventual expurgo será uma operação administrativa versionada, nunca uma capacidade do usuário de runtime.

## Deviations from Plan

### Correção de referência conceitual

O Plano 02-04 associa D-40 ao checkpoint de auditoria, mas D-40 trata da invalidação de sessões após redefinição de senha. A decisão foi fundamentada em `AUDT-02`, OQ-6/OQ-7, no bootstrap PostgreSQL e na seção de imutabilidade da pesquisa; D-40 não foi usada como justificativa.

**Total deviations:** 1 correção documental de fundamento, sem alteração de escopo funcional.

## Issues Encountered

- O `STATE.md` ainda descrevia a integração do 02-03 como pendente. O registro foi reconciliado com o Git e com os SUMMARYs atuais.
- A política definitiva de retenção pública depende de revisão jurídica. Isso não bloqueia o MVP fechado, mas permanece gate obrigatório de lançamento.

## Evidências de verificação

- Decisões OQ-3/OQ-5 e OQ-6/OQ-7 aprovadas explicitamente pelo responsável em 2026-08-21.
- `AUDT-01` conferido ao lado de `AUDT-02` em `.planning/REQUIREMENTS.md`.
- Configuração atual conferida: Spring e Flyway usam hoje `BETUFU_DB_USER`.
- Bootstrap atual conferido: `infra/postgres/init-databases.sh` torna esse mesmo usuário proprietário do banco.
- Nenhum código, banco, infraestrutura, dependência ou serviço externo foi alterado.

## Escopo dos requisitos

Este plano conclui somente as decisões que governam `AUDT-01` e `AUDT-02`. Os requisitos continuam pendentes até que o Plano 02-21 implemente e prove a tabela, os privilégios, o trigger e o procedimento operacional.

## User Setup Required

None — nenhuma configuração externa foi alterada neste plano.

## Next Phase Readiness

- O Plano 02-21 recebeu um contrato executável para a trilha append-only.
- A ampliação de infraestrutura deverá ser apresentada com allowlist própria antes de editar Compose, bootstrap ou configuração Spring/Flyway.
- A Wave 2 ainda depende do Plano 02-06.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
