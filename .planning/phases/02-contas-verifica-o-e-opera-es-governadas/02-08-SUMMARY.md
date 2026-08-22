---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 08
subsystem: governance-bootstrap
tags: [bootstrap-admin, public-identifier, uuid, fail-closed]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: UUID público da conta exibido e copiável pelo perfil do Plano 02-05
provides:
  - Decisão OQ-4 sobre o identificador da semente do primeiro administrador
  - Comportamento fail-closed para UUID ausente, inválido ou ainda inexistente
affects: [02-17, 02-23, OPER-01, OPER-03, IDEN-05]

actuals:
  tokens: not-recorded
  tasks: 1
  commits: 1

tech-stack:
  added: []
  patterns:
    - Configuração referencia somente identidade local preexistente
    - Bootstrap pendente e observável em vez de criação implícita de cadastro

key-files:
  created:
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-08-SUMMARY.md
  modified: []

key-decisions:
  - "OQ-4: BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER recebe o UUID público canônico da conta, escolhido pelo responsável em 2026-08-22."
  - "A pessoa deve fazer o primeiro login e copiar o próprio UUID no perfil antes de configurar a semente."
  - "UUID ausente, inválido ou sem identidade correspondente não cria cadastro nem concede papel; a semente permanece pendente e observável."

requirements-completed: [OPER-01, OPER-03, IDEN-05]

coverage:
  - id: D1
    description: A semente do primeiro administrador usa o UUID público de uma conta já existente.
    requirement: OPER-01
    verification:
      - kind: manual_procedural
        ref: decisão explícita do responsável em 2026-08-22 pela opção identificador-publico
        status: pass
    human_judgment: true
    rationale: A escolha do identificador é decisão de produto e operação do responsável.
  - id: D2
    description: Configuração inválida ou identidade inexistente falha fechada sem criar cadastro ou autoridade.
    requirement: OPER-03
    verification:
      - kind: manual_procedural
        ref: comportamento registrado neste SUMMARY para implementação nos planos dependentes
        status: pass
    human_judgment: true
    rationale: Este plano registra arquitetura; a prova executável pertence aos planos de implementação.

duration: not-recorded
completed: 2026-08-22
status: complete
---

# Phase 2 Plan 08: Identificador da semente administrativa

**O primeiro administrador será localizado pelo UUID público de uma conta já existente, sem ampliar claims OIDC nem criar identidade por configuração.**

## Decision OQ-4

- Alternativa escolhida: `identificador-publico`.
- Formato literal: `BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER=<UUID público canônico da conta>`.
- Procedimento: a pessoa faz o primeiro login, abre o perfil, copia o identificador público e então configura a variável no ambiente autorizado.
- Se a variável estiver ausente, malformada ou apontar para conta inexistente, nenhum cadastro ou papel é criado. A semente fica pendente e deve produzir diagnóstico operacional sanitizado.

## Rationale

- O UUID já existe e é exibido pelo perfil, portanto não exige expor `subject` nem acrescentar e-mail ao escopo OIDC.
- A escolha preserva D-41, D-47, D-49 e D-51: identidade existente é precondição, concessão é idempotente e término de sessão continua responsabilidade da máquina de papéis.
- Um valor opaco pode ser copiado sem erro de digitação; se ainda assim houver erro, o comportamento é observável e fail-closed.

## Scope

- Nenhum arquivo de produção, configuração ou segredo foi alterado.
- A implementação da semente continua nos planos dependentes e exige seus próprios gates.

## User Setup Required

Nenhuma configuração deve ser feita ainda; este plano registra somente a decisão.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-22*
