---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 15
subsystem: frontend-profile
tags: [angular, signals, profile, accessibility, privacy]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Store, adapter e gate de conclusão do perfil entregues pelo Plano 02-05
provides:
  - Superfície autenticada S2 para consultar e atualizar o apelido
  - Identificador público copiável com alternativa acessível
  - Rota lazy `/conta/perfil` em development e production
  - Estados de carregamento, erro, conflito, cópia e texto longo sem expor dados proibidos
affects: [02-26, 02-27, PROF-01, PROF-02]

actuals:
  tokens: not-recorded
  tasks: 1
  commits: 1

tech-stack:
  added: []
  patterns:
    - Componente standalone OnPush consumindo store baseado em Signals
    - Allowlist visual que renderiza apenas apelido e identificador público
    - Navegação lazy compartilhada entre builds development e production

key-files:
  created:
    - frontend/src/app/features/profile/profile-page.component.ts
    - frontend/src/app/features/profile/profile-page.component.html
    - frontend/src/app/features/profile/profile-page.component.scss
    - frontend/src/app/features/profile/profile-page.component.spec.ts
  modified:
    - frontend/src/app/app.routes.development.ts
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/features/profile/profile-gate-page.component.scss
    - frontend/src/styles/_tokens.scss

key-decisions:
  - "S2 mostra somente apelido e identificador público; e-mail, matrícula, HMAC, papéis, equipe favorita e MFA ficam ausentes do DOM."
  - "O token global $form-max-width: 640px é compartilhado por S1 e S2, sem duplicar o literal nos componentes."
  - "S7 não foi simplificada nem descartada: foi transferida para o Plano 02-26 após suas dependências reais."

requirements-completed: [PROF-01, PROF-02]

coverage:
  - id: D1
    description: A pessoa autenticada consulta e atualiza o próprio apelido e chega a S2 após concluir S1.
    requirement: PROF-01
    verification:
      - kind: component
        ref: profile-page.component.spec.ts
        status: pass
      - kind: route
        ref: validate-angular-route-boundary.test.mjs
        status: pass
    human_judgment: false
  - id: D2
    description: O perfil diferencia homônimos pelo identificador público copiável e preserva valor e feedback em falhas.
    requirement: PROF-02
    verification:
      - kind: component
        ref: profile-page.component.spec.ts
        status: pass
    human_judgment: false
  - id: D3
    description: Dados sensíveis e capacidades operacionais permanecem ausentes da superfície S2.
    requirement: PROF-02
    verification:
      - kind: component
        ref: profile-page.component.spec.ts#privacy-allowlist
        status: pass
      - kind: review
        ref: Codex final snapshot review
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 15: Superfície pública do perfil

**A pessoa autenticada agora pode consultar e atualizar seu apelido em S2 e copiar o identificador público, sem que a interface exponha atributos privados ou operacionais.**

## Accomplishments

- Criou a página standalone S2 com estados de carregamento, conteúdo, vazio, erro, conflito, salvamento e cópia.
- Publicou `/conta/perfil` por carregamento sob demanda nos builds de development e production.
- Fechou a navegação S1 → S2 usando o store e o adapter existentes, sem criar outro cliente HTTP.
- Tornou UUIDs e mensagens longas utilizáveis em 320 px, com foco visível e feedback acessível.
- Compartilhou o limite de largura de formulário entre S1 e S2 pelo token Sass aprovado.

## Decisions Made

- A superfície segue uma allowlist positiva: somente apelido e identificador público são apresentados.
- A ação de copiar usa a API de clipboard quando disponível e oferece alternativa compreensível quando ela falha.
- O menu/guard S7 depende de papéis, capacidades e destinos ainda futuros; sua implementação completa passou ao Plano 02-26 e a aprovação visual final ao 02-27.

## Deviations from Plan

### Replanejamento de S7

- **Issue:** a antiga Task 2 dependia de contratos de papéis/capacidades do 02-17 e de destinos entregues pelos Planos 02-13, 02-18, 02-19 e 02-20.
- **Resolution:** S7 foi transferida integralmente para 02-26; o checkpoint humano S1–S7 ficou no 02-27. Nenhum requisito ou comportamento foi removido.

### Correção dos testes assíncronos

- **Issue:** `fixture.whenStable()` não acompanhava de forma confiável o retry RxJS e a navegação do Router, produzindo três falhas apesar do estado correto posterior.
- **Fix:** as specs passaram a aguardar estados observáveis concretos com `vi.waitFor`.
- **Verification:** suíte Angular completa em `85/85`.

### Token compartilhado de largura

- **Issue:** S1 e S2 precisavam do mesmo limite aprovado de 640 px sem duplicação local.
- **Fix:** `$form-max-width` foi centralizado em `_tokens.scss` e consumido pelas duas superfícies.

**Total deviations:** um replanejamento de dependência e duas correções locais; nenhuma dependência, API ou decisão de produto foi adicionada.

## Issues Encountered

- O worktree não tinha `node_modules`; `npm ci` instalou 486 pacotes a partir do lockfile e reportou zero vulnerabilidades.
- A tentativa inicial da antiga Task 2 foi interrompida no preflight antes de qualquer alteração, pois seus predecessores ainda não existiam.
- O build de produção passou com aviso não bloqueante: `profile-page.component.scss` tem 5,94 kB contra orçamento de 4,00 kB.
- Avisos normais de LF/CRLF não afetaram as verificações.

## Verification Evidence

- Angular completo: `10` arquivos de spec, `85/85` testes, zero falhas.
- Build Angular production: aprovado; somente o aviso de orçamento Sass registrado acima.
- Validador de fronteira de rotas: `OK`.
- Regressões do validador de rotas: `158/158`.
- `git diff --cached --check`: aprovado antes do commit técnico.
- Revisão Codex do snapshot final: `PASS`, sem achado bloqueante.
- Commit técnico `5671611`: exatamente 8 arquivos frontend, 1.583 inserções e 3 remoções.

## Commits

| Commit | Conteúdo |
|---|---|
| `5671611` | Implementação e testes da superfície pública S2. |
| _(pendente de aprovação)_ | Formalização documental deste resumo, validação, estado e roadmap. |

## User Setup Required

Nenhuma configuração manual nova. A instalação local utilizou somente o lockfile existente; `node_modules` e `dist` permanecem artefatos ignorados.

## Next Phase Readiness

- O Plano 02-15 está tecnicamente concluído e formalizado no worktree.
- A Wave 4 permanece parcial: 02-11 e 02-22 ainda estão pendentes.
- S7 será implementada somente no Plano 02-26, depois dos contratos e destinos que ela consome; o checkpoint humano final está no 02-27.
- Nenhum push foi realizado. Stage e commit documental dependem de aprovação Git separada.

## Self-Check: PASSED

- Os oito arquivos do escopo existem e coincidem com o commit técnico `5671611`.
- As contagens de testes e o build foram registradas a partir das execuções concluídas.
- O índice Git permaneceu vazio durante esta formalização.
- O histórico conserva um commit técnico separado da documentação administrativa.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
