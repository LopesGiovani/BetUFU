---
phase: 01-funda-o-segura-da-plataforma
plan: "01"
subsystem: infra
tags: [supply-chain, npm, java, nodejs, docker, preflight]

requires: []
provides:
  - "Seis identidades npm legitimadas e aprovadas antes de qualquer instalação"
  - "Host validado com Java 25, Node 22 compatível e daemon Docker ativo"
  - "Evidência de que nenhum scaffold ou dependência do produto antecedeu o gate"
affects: [01-02, 01-04, backend, frontend, infra]

actuals:
  tokens: 2610
  tasks: 1
  commits: 0

tech-stack:
  added: []
  patterns:
    - "Supply chain npm exige conferência de identidade, repositório, integridade e aprovação humana antes do install"
    - "Runtimes incompatíveis bloqueiam o scaffold; não há downgrade silencioso"

key-files:
  created:
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-01-SUMMARY.md"
  modified:
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "Autorizar somente as seis versões npm auditadas e os runtimes aprovados; qualquer substituição ou downgrade volta ao responsável"

patterns-established:
  - "Gate blocking-human: instalações e scaffolds dependentes só podem começar depois da legitimidade documental e do preflight verde"

requirements-completed: [ARCH-01, OPS-01]

coverage:
  - id: D1
    description: "Identidades e versões dos seis pacotes npm conferidas no registry oficial"
    requirement: ARCH-01
    verification:
      - kind: other
        ref: "npm view dos seis pacotes em registry.npmjs.org: nome, versão, repository.url, dist.integrity, assinaturas e scripts"
        status: pass
    human_judgment: true
    rationale: "O gate de confiança exigia aprovação humana explícita; o responsável respondeu 'pacotes e runtimes aprovados'."
  - id: D2
    description: "Host atende Java 25, Node >=22.22.3 e Docker ativo"
    requirement: OPS-01
    verification:
      - kind: other
        ref: "java -version; node --version; docker info --format {{.ServerVersion}}"
        status: pass
    human_judgment: false
  - id: D3
    description: "Nenhum scaffold ou instalação npm do produto ocorreu antes da aprovação"
    requirement: ARCH-01
    verification:
      - kind: other
        ref: "Test-Path backend, frontend, infra, node_modules e package-lock.json retornou false"
        status: pass
    human_judgment: false

duration: "não mensurada — checkpoint acompanhado em múltiplas interações"
completed: 2026-08-08
status: complete
---

# Phase 1 Plan 01: Legitimidade de pacotes e runtimes Summary

**Supply chain npm legitimada, host atualizado para a matriz aprovada e nenhum scaffold iniciado antes do gate humano.**

## Performance

- **Duration:** não mensurada — checkpoint acompanhado em múltiplas interações
- **Started:** não cronometrado
- **Completed:** 2026-08-08T19:12:11.237Z
- **Tasks:** 1
- **Files modified:** 3 documentos de planejamento; 0 arquivos de aplicação

## Accomplishments

- As seis identidades marcadas como `SUS (too-new)` foram conferidas no npm oficial e aprovadas explicitamente pelo responsável.
- O host passou a atender Java Temurin 25.0.4, Node.js 22.23.2 e Docker Server 29.4.1 ativo.
- Foi confirmado que `backend/`, `frontend/`, `infra/`, `node_modules/` e `package-lock.json` não existiam antes da aprovação.

## Evidências da supply chain npm

| Pacote aprovado | Repositório conferido | Integridade | Assinaturas npm | Observação |
|---|---|---|---:|---|
| `@angular/core@22.1.0` | `angular/angular` | SHA-512 | 2 | sem `postinstall` |
| `@angular/cli@22.1.2` | `angular/angular-cli` | SHA-512 | 2 | sem `postinstall` |
| `@angular/material@22.1.0` | `angular/components` | SHA-512 | 2 | sem `postinstall` |
| `@angular/cdk@22.1.0` | `angular/components` | SHA-512 | 2 | sem `postinstall` |
| `@openapitools/openapi-generator-cli@2.40.1` | `OpenAPITools/openapi-generator-cli` | SHA-512 | 2 | `postinstall: opencollective || exit 0`, não executado |
| `@playwright/test@1.62.1` | `microsoft/playwright` | SHA-512 | 2 | sem `postinstall` |

Todas as consultas foram feitas com `npm view` contra o registry oficial. Nenhum dos pacotes foi instalado neste plano.

## Evidências do host

| Requisito | Resultado | Status |
|---|---|---|
| Java 25 | Eclipse Temurin `25.0.4+7-LTS` | PASS |
| Node >=22.22.3 | Node.js `v22.23.2` | PASS |
| Docker ativo | Docker Server `29.4.1` | PASS |
| Ausência de scaffold prematuro | diretórios e lockfile do produto ausentes | PASS |

## Task Commits

Nenhum commit de tarefa foi criado: o plano é um checkpoint documental e não modificou código, banco ou infraestrutura da aplicação.

O commit de metadados permanece deliberadamente pendente de aprovação separada, conforme `AGENTS.md`.

## Files Created/Modified

- `.planning/phases/01-funda-o-segura-da-plataforma/01-01-SUMMARY.md` — evidências e resultado do Gate 0.
- `.planning/STATE.md` — posição atualizada para 1/28, sem autorizar a Onda 2.
- `.planning/ROADMAP.md` — Plano 01 marcado como executado e Fase 1 mantida em andamento.

## Decisions Made

- Somente as seis versões auditadas estão autorizadas para os planos dependentes.
- Falha futura de compatibilidade deve interromper a execução e voltar ao responsável; não autoriza troca de pacote, versão ou stack.

## Deviations from Plan

### Ajuste operacional transparente

**1. USER-SETUP não gerado após a conclusão do setup do host**

- **Found during:** fechamento do checkpoint.
- **Issue:** o frontmatter possui `user_setup`, mas todas as ações ali descritas já foram concluídas e verificadas; gerar um arquivo com status `Incomplete` criaria uma pendência falsa.
- **Fix:** registrar a conclusão no SUMMARY e não criar `01-USER-SETUP.md`, seguindo a regra de que esse artefato contém somente ações humanas ainda pendentes.
- **Files modified:** nenhum arquivo adicional.
- **Verification:** Java, Node e Docker passaram no preflight; não resta configuração manual para este gate.
- **Committed in:** pendente de aprovação separada.

**Total deviations:** 1 ajuste documental do workflow; nenhuma alteração no escopo ou na stack.
**Impact on plan:** o resultado e os critérios do plano foram preservados sem criar uma pendência inexata.

## Issues Encountered

None.

## User Setup Required

None — o setup exigido pelo Gate 0 foi concluído e verificado.

## Next Phase Readiness

- Os pré-requisitos do Plano 01 estão verdes para o Plano 02 e para os scaffolds posteriores.
- `ARCH-01` e `OPS-01` permanecem `Pending` em `REQUIREMENTS.md`, pois são requisitos compartilhados com planos ainda não executados; a consulta `requirements.ready-ids` retornou `0/2` prontos.
- A Onda 2 não está autorizada por esta conclusão e exige apresentação e aprovação próprias.

## Self-Check: PASSED

- [x] Aprovação humana explícita recebida para pacotes e runtimes.
- [x] Java `25.0.4`, Node `22.23.2` e Docker Server `29.4.1` confirmados.
- [x] Nome, versão, repositório e integridade SHA-512 dos seis pacotes conferidos.
- [x] Nenhuma instalação npm ou scaffold do produto ocorreu antes do gate.
- [x] Nenhuma alteração foi feita em `REQUIREMENTS.md`.
- [x] Nenhum stage, commit, push ou avanço para a Onda 2 foi executado.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-08*
