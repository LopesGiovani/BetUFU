---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 27
subsystem: human-ui-validation
tags: [playwright, accessibility, responsive, privacy, capabilities]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Superfícies S1-S6, contrato fechado de capacidades e navegação S7 dos Planos 02-20, 02-32 e 02-26
provides:
  - Aprovação humana explícita do fluxo integrado S1-S7 em desktop e 320 px
  - Evidência de teclado, foco, responsividade, privacidade e autorização fail-closed
  - Encerramento do checkpoint final da Fase 2 sobre snapshot combinado certificado
affects: [phase-3, ui, accessibility, privacy, operations]

actuals:
  tokens: not-recorded
  tasks: 1
  commits: 0

tech-stack:
  added: []
  patterns:
    - Checkpoint humano executado somente depois das suítes automatizadas e do manifesto de hashes
    - Achado visual encaminhado ao plano proprietário e revisão integral repetida após a correção
    - Fixtures sintéticas sem papel, matrícula real, credencial ou PII como evidência de navegador

key-files:
  created:
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-27-SUMMARY.md
  modified:
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-VALIDATION.md
    - .planning/STATE.md
    - .planning/ROADMAP.md

key-decisions:
  - "Somente a aprovação literal do responsável encerrou o checkpoint; os resultados automatizados prepararam a decisão, mas não a substituíram."
  - "As respostas negativas 404 e 403 das fixtures são evidência esperada; erros inesperados de console e exceções de página permaneceram em zero."
  - "Os achados de Escape e da fonte de ícones foram corrigidos nos planos proprietários 02-26 e 01-14 antes da repetição integral do checkpoint."

requirements-completed: [PROF-01, PROF-02, VERI-06, VERI-07, OPER-01, OPER-02, OPER-03, OPER-04, OPER-06, IDEN-05, PRIV-01]

coverage:
  - id: D1
    description: O snapshot integrado apresenta S1-S7 corretamente em desktop e 320 px, sem overflow horizontal e com alvos interativos mínimos de 44 por 44 px.
    verification:
      - kind: automated_ui
        ref: "Playwright CLI final-browser-checkpoint.js — S1-S7 desktop/mobile"
        status: pass
      - kind: manual_procedural
        ref: "aprovação explícita do responsável em 2026-08-26"
        status: pass
    human_judgment: true
    rationale: Hierarquia visual, legibilidade e adequação da experiência integrada exigem julgamento humano.
  - id: D2
    description: Teclado, foco, disclosures e diálogos funcionam com Enter, Space, Tab e Escape, incluindo retorno de foco ao gatilho.
    verification:
      - kind: automated_ui
        ref: "Playwright CLI final-browser-checkpoint.js — S5, S6 e S7"
        status: pass
      - kind: manual_procedural
        ref: "aprovação explícita do responsável em 2026-08-26"
        status: pass
    human_judgment: true
    rationale: O checkpoint contratava verificação humana da interação física além das especificações Angular.
  - id: D3
    description: Capacidades ausentes ou desconhecidas falham fechadas, o 403 remove blocos privilegiados e nenhuma PII ou matrícula anterior aparece.
    verification:
      - kind: automated_ui
        ref: "Playwright CLI final-browser-checkpoint.js — zero/unknown capabilities e stale 403"
        status: pass
      - kind: integration
        ref: "manifesto final F941E34A81F17600521E8B9904757FEC049781CA3403F94A1721AD31902FC38B"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-26
status: complete
---

# Plano 02-27 — Checkpoint humano integrado S1-S7

**A experiência governada de contas, matrícula e operações foi aprovada pelo responsável em navegador real, em desktop e 320 px, após correção e repetição integral das provas.**

## Snapshot aprovado

- `HEAD`: `a0b5a20c723fe2774dd418721fc8e9a462468168`.
- Base remota no momento da aprovação: `076c7c4575184299a69c7e3e1c3f506b71feeb39`.
- Manifesto: `F941E34A81F17600521E8B9904757FEC049781CA3403F94A1721AD31902FC38B`.
- Paths certificados: 35; 33 permaneceram intactos, dois foram remediados pelos planos proprietários e zero divergiram no snapshot final.
- Worktree do 02-27 limpa; build e runtime servidos do mesmo snapshot combinado.

## Evidência automatizada

- Angular: 19 arquivos, `220/220` testes.
- Validadores Node: `623/623`.
- Build Angular de produção: sucesso, com os três avisos SCSS preexistentes.
- `validate-angular-route-boundary.mjs`: `PASS`.
- Auditoria final Playwright: S1-S7 `PASS`, zero falhas, zero erros inesperados de console e zero exceções de página.

## Caminho da revisão

A primeira inspeção encontrou duas falhas reais: os Material Symbols apareciam como nomes literais e `Escape` no próprio gatilho não fechava o menu. O checkpoint não tomou posse de código. Os achados voltaram, com aprovação separada, ao Plano 01-14 para a fonte local e ao Plano 02-26 para o comportamento de teclado. As correções foram integradas em quatro commits lineares:

- `79df565` — `fix(01-14): self-host approved Material Symbols subset`;
- `7b681f3` — `docs(01-14): ratify Material Symbols source and evidence`;
- `08620f9` — `fix(02-26): close account menu with Escape from trigger`;
- `a0b5a20` — `docs(02-26): certify account menu Escape remediation`.

Depois das correções, todas as provas automatizadas e todo o percurso humano foram executados novamente. Duas falhas intermediárias do script temporário eram expectativas incorretas da própria auditoria — rótulos e localizador ambíguo —, não defeitos do produto; a execução corrigida terminou integralmente verde.

## Resultado S1-S7

- **S1:** primeiro acesso mostra somente `Sair`; nenhuma navegação operacional aparece.
- **S2:** perfil mostra somente apelido, identificador público e estado genérico da matrícula; papel, capability, MFA e PII ficam ausentes.
- **S3:** estado sem matrícula é claro e não revela valor anterior.
- **S4:** painel responsivo, quatro blocos governados e ícones locais renderizados como símbolos reais.
- **S5:** exatamente três papéis fixos, somente o global acionável nesta fase, diálogo com foco inicial em `Cancelar`, contenção e retorno de foco.
- **S6:** busca por identificador sintético, campos transitórios não persistidos nas capturas e confirmação acessível com retorno de foco.
- **S7:** três destinos exatos quando autorizados; ausência total com capabilities vazias/desconhecidas; 403 sem blocos privilegiados; menu operável por Enter, Space, Tab e Escape.
- **Layout:** desktop e 320 px sem overflow horizontal; controles visíveis com pelo menos 44×44 px.

## Aprovação humana

Após receber o relatório final e as capturas, o responsável respondeu literalmente `aprovado` em 2026-08-26. Esse sinal encerrou o gate bloqueante do Plano 02-27 e, com ele, o último plano da Fase 2.

## Desvios do plano

Nenhum arquivo de produção, teste, dependência, configuração ou infraestrutura foi alterado pelo 02-27. Os dois achados foram encaminhados e corrigidos pelos planos proprietários, exatamente como o contrato do checkpoint determinava.

## Preparação para a próxima fase

A Fase 2 conclui seus 32 planos. A Fase 3 pode iniciar a discussão da Experiência Esportiva do Piloto, mas modalidades, categorias e fontes esportivas autoritativas ainda exigem decisão explícita antes do planejamento.

Stage, commit documental, limpeza das worktrees/branches residuais e push não fazem parte desta aprovação e permanecem pendentes de autorização própria.

---
*Fase: 02 — Contas, Verificação e Operações Governadas*
*Concluído: 2026-08-26*
