---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 18
subsystem: operations-panel-ui
tags: [angular, signals, openapi, operations, privacy, playwright]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: backend governado do painel pelo 02-10 e authorities operacionais pelo 02-17
provides:
  - Contrato OpenAPI e cliente Angular gerado para o painel operacional
  - Superfície S4 responsiva em /operacao/painel, disponível em produção e desenvolvimento
  - Adapter e store Signals que preservam dados em falhas transitórias e limpam estado em 401/403
  - Validação semântica das mutações do cliente gerado antes do gate final de digest
  - Prova em Chromium real a 320px da privacidade, do motivo literal e da ausência de rolagem horizontal
affects: [02-19, 02-20, 02-26, 02-27, OPER-06, OPER-01, OPER-02, OPER-04]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - Contrato OpenAPI aprovado antes do adapter e da UI consumidora
    - Blocos opcionais definidos exclusivamente pela autorização do servidor
    - Store Angular com retry limitado somente para falha transitória e limpeza fail-closed em 401/403
    - Validação semântica de fonte gerada antes da comparação final do digest

key-files:
  created:
    - backend/src/test/java/br/com/betufu/platform/operations/OperationalPanelServiceTest.java
    - frontend/src/app/features/operations/operations.models.ts
    - frontend/src/app/features/operations/operations-api.adapter.ts
    - frontend/src/app/features/operations/operations-panel.store.ts
    - frontend/src/app/features/operations/operations-panel-page.component.ts
    - frontend/e2e/operations-panel.spec.ts
    - frontend/playwright.operations.config.ts
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalPanelService.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/app.routes.development.ts
    - scripts/validate-angular-route-boundary.mjs
    - scripts/validate-angular-route-boundary.test.mjs

key-decisions:
  - "A ordem contrato → cliente gerado → adapter → UI é vinculante; nenhum consumidor interpreta um contrato ainda não aprovado."
  - "A presença dos blocos é decidida pelo backend; o Angular não reconstrói papéis nem revela blocos não autorizados."
  - "401 e 403 não recebem retry e apagam snapshots privilegiados; somente falhas transitórias preservam a última resposta válida."
  - "A janela de sete dias usa limites inclusivos e é aplicada igualmente a alterações de papel e correções administrativas."
  - "O UUID do ator permanece sempre visível na auditoria; o papel é informação complementar e nunca substitui a identidade."
  - "As regras semânticas do gerado executam antes do digest, para que cada mutação prove a propriedade declarada em vez de cair num alarme genérico de integridade."

requirements-completed: [OPER-06, OPER-01, OPER-02, OPER-04]

coverage:
  - id: D1
    description: O contrato e o cliente gerado representam apenas os quatro blocos governados, sem PII proibida, e cada mutação de fonte é rejeitada pela regra semântica correspondente.
    requirement: OPER-06
    verification:
      - kind: other
        ref: validate-phase1 --contract e validate-angular-route-boundary 27/27 mutações semânticas
        status: pass
    human_judgment: false
  - id: D2
    description: Cada agregado autorizado falha isoladamente, os demais sobrevivem e nenhum bloco fora do papel é nomeado na resposta.
    requirement: OPER-01
    verification:
      - kind: unit
        ref: OperationalPanelServiceTest
        status: pass
      - kind: integration
        ref: OperationalPanelIT
        status: pass
    human_judgment: false
  - id: D3
    description: A S4 mostra somente blocos recebidos, preserva motivo literal e UUID, mantém dados em falha transitória e remove estado privilegiado em 401/403 sem retry.
    requirement: OPER-02
    verification:
      - kind: unit
        ref: Angular 108/108, incluindo adapter, store e componente
        status: pass
    human_judgment: false
  - id: D4
    description: A rota produtiva funciona em Chromium a 320px sem rolagem horizontal e sem reintroduzir tracer ou diagnóstico em produção.
    requirement: OPER-04
    verification:
      - kind: e2e
        ref: Playwright operations-panel 1/1 em Chrome real
        status: pass
      - kind: other
        ref: Angular production build e Node 617/617
        status: pass
    human_judgment: false
---

# Plano 02-18 — Contrato e superfície S4 do painel operacional

## Verification Evidence

- Backend completo com `clean`: Surefire `246/246` em 22 classes e Failsafe `303/303` em 40 classes, sem falhas, erros ou testes ignorados.
- Validadores Node: `617/617`. A primeira execução concorrente teve uma falha transitória `copyfile UNKNOWN` no Windows; a repetição serial, sem edição intermediária, terminou integralmente verde.
- Angular: `108/108` em 12 arquivos de spec. O build de produção terminou com sucesso.
- O Playwright dedicado terminou `1/1` em Chrome real. A prova usa viewport de 320px, confirma ausência de rolagem horizontal e preserva literalmente o motivo e o UUID do ator.
- O cliente gerado passou de 34 para 43 arquivos. O digest aprovado mudou de `FE9616E8FF0DF72BED8B336A1CF9BBA759C5CBF45A114B8E397F09FABD493478` para `74172C925F10EA4196021520D72AC386831278155842F582329FDAA5727C0571`.
- As 27 mutações de fonte gerada falham pela regra semântica declarada antes da comparação final do digest; uma mudança sem regra específica continua bloqueada pelo controle de integridade.
- Três mutações de revisão foram discriminantes: remover o filtro temporal das alterações de papel deixou o teste Java vermelho; remover o filtro temporal das correções administrativas deixou o teste Java vermelho; restaurar a precedência antiga do papel sobre o UUID deixou o teste Angular vermelho. Todos os arquivos foram restaurados pelos hashes congelados.
- A revisão independente final concluiu `PASS`, com P0=P1=P2=0.

## What Was Built

- O OpenAPI publica `GET /api/v1/operations/panel` com quatro blocos opcionais: recusas de matrícula, correções administrativas, alterações de papel e contas sem matrícula. Ausência significa que o ator não recebeu aquele escopo; a resposta não transporta uma explicação que revele o bloco.
- O serviço do backend consulta os agregados autorizados isoladamente. Uma falha não derruba os demais e é representada apenas por um identificador seguro de bloco que o ator já estava autorizado a consultar.
- Alterações de papel e correções administrativas são filtradas pela mesma janela inclusiva `[since, now]`, evitando eventos antigos ou futuros na superfície.
- O cliente TypeScript foi regenerado, aprovado por inventário/digest e consumido exclusivamente por `OperationsApiAdapter`; não existe `HttpClient` direto na feature.
- O `OperationsPanelStore` usa Signals e no máximo duas novas tentativas para falhas transitórias. Em 401/403, não repete a chamada e apaga o snapshot atual e anterior antes de expor o erro sanitizado.
- A S4 renderiza cartões e listas responsivos somente para os blocos presentes. O motivo de auditoria usa interpolação Angular, portanto `<` e `>` permanecem texto, nunca HTML.
- A rota `/operacao/painel` entrou nas listas de desenvolvimento e produção, preservando as proibições de `tracer` e `diagnostic` no bundle produtivo.

## Decisions Made

- O servidor continua sendo a autoridade. A UI não carrega uma matriz local de papéis para adivinhar quais blocos deveria mostrar.
- Resposta válida sem blocos é estado vazio, diferente de falha. Falha parcial preserva os blocos válidos e identifica somente os autorizados que ficaram indisponíveis.
- O ator é sempre identificado pelo UUID. Quando existe um papel associado, ele aparece como contexto adicional; nunca substitui o identificador, porque papéis não são identidades.
- O recorte temporal é aplicado no serviço mesmo quando o repositório devolve eventos além da janela, criando defesa contra implementações ou fixtures permissivas.
- A integridade do cliente gerado permanece governada por digest, mas o digest é a última barreira. Regras semânticas vêm antes para dizer qual contrato foi violado e impedir prova vazia.

## Review Corrections

- A primeira revisão encontrou ausência do filtro temporal inclusivo nos dois fluxos de auditoria. O serviço agora filtra alterações de papel e correções administrativas por `[since, now]`, com testes independentes e mutações que tornam cada omissão vermelha.
- A primeira versão da UI mostrava o papel no lugar do UUID quando ambos existiam. O componente passou a mostrar sempre o UUID e, separadamente, o papel complementar; a regressão foi provada por mutação.

## Deviations from Plan

- Nenhuma dependência foi adicionada e nenhum arquivo saiu da allowlist de 32 paths.
- As correções de revisão foram feitas antes do snapshot final e invalidaram o parecer anterior, como exigido. A revisão final olhou os hashes restaurados e retornou `PASS`.
- O teste Node foi repetido de forma serial após uma falha transitória de cópia do cache Angular no Windows. Não houve alteração entre as duas execuções.

## Issues Encountered

- P3 não bloqueante: os exemplos OpenAPI de estado ainda usam `PERMITTED`/`BLOCKED`, enquanto o vocabulário real de runtime é `PERMITIDO`/`BLOQUEADO`. Os tipos e o comportamento executável estão corretos; os exemplos merecem correção documental futura.
- P3 não bloqueante: o build de produção passa, mas o SCSS do painel excede o orçamento de 4 kB em 2,67 kB. O perfil já excedia o mesmo orçamento em 1,94 kB. Não há falha de build, mas o crescimento deve ser tratado antes de ampliar S4.
- Os avisos LF→CRLF do Git continuam informativos no checkout Windows e não alteraram os hashes do snapshot revisado.

## User Setup Required

Nenhuma configuração manual, segredo, dependência ou serviço externo novo é necessário para consumir a S4. A autorização continua vindo da sessão OIDC e das authorities operacionais já entregues pelo 02-17.

## Next Phase Readiness

- O painel S4 está pronto para ser integrado. As superfícies seguintes podem reutilizar o mesmo padrão contrato → cliente → adapter → store → componente.
- A Wave 11 pode iniciar o 02-12, responsável pela correção administrativa D-32 e sua autorização no backend.
- Os dois P3 acima são conhecidos e não bloqueiam a Wave 11, mas não devem desaparecer do registro até correção explícita.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-25*
