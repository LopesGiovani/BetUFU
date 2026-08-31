---
phase: 03-experiencia-esportiva-do-piloto
plan: 08
wave: 9
status: complete-pending-git
completed: 2026-08-30
subsystem: sports-rosters
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, optimistic-locking]

requires:
  - phase: 03-experiencia-esportiva-do-piloto
    provides: equipes, atletas, autorizacao esportiva escopada e cliente OpenAPI governado ate V14
provides:
  - elenco por equipe da edicao com rascunho, publicacao e correcao versionada
  - snapshots publicos imutaveis sem matricula, HMAC ou escala por partida
  - O4 operacional em Angular com estado privado Signal e RosterView reutilizavel
affects: [03-09, 03-11, 03-16, sports-roster, public-sports-pages]

actuals:
  tokens: 113349
  tasks: 5
  commits: 2

tech-stack:
  added: []
  patterns:
    - migration Flyway one-way com snapshot append-only e triggers de imutabilidade
    - optimistic locking por lock_version com conflito HTTP 409 sem overwrite
    - OpenAPI candidato read-only antes de manifesto e geracao governada
    - adapter Angular sobre servico gerado e store Signal com limpeza fail-closed em 401/403

key-files:
  created:
    - backend/src/main/resources/db/migration/V15__create_sports_rosters.sql
    - backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterService.java
    - backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterController.java
    - backend/src/test/java/br/com/betufu/sports/SportsRosterIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsRosterConcurrencyIT.java
    - frontend/src/app/shared/sports/roster-view.component.ts
    - frontend/src/app/features/sports/operations/rosters/sports-rosters.store.ts
    - frontend/src/app/features/sports/operations/rosters/sports-rosters-page.component.ts
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/scripts/generate-api.test.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-08-PLAN.md

key-decisions:
  - "V15 substitui a colisao documental V14 identificada apos o 03-07; migrations aplicadas nao sao editadas."
  - "Elenco pertence a equipe da edicao, nasce RASCUNHO e a publicacao cria snapshot versionado imutavel."
  - "PUT de rascunho nao pode alterar elenco PUBLICADO; correcao publicada exige motivo, nova versao e auditoria."
  - "A autoridade deriva da equipe e de team.edition.competition persistidos; UUID ou scopeRef livre nao concede acesso."
  - "O MVP nao modela TITULAR/RESERVA por partida; jersey_number e position_name sao apenas dados do membro do elenco."
  - "Os quatro modelos gerados adicionais foram ratificados porque o servico OpenAPI tipado depende deles."

patterns-established:
  - "Snapshot publicado copia nomes e membros allowlisted para leitura historica, mantendo versoes anteriores navegaveis."
  - "Concorrencia stale falha com 409 e preserva a edicao local para comparacao; mutacoes nao fazem retry automatico."
  - "401/403 limpa rascunho, preview, selecao, versao base e conflito antes de expor estado seguro."

requirements-completed: [SPRT-02, SPRT-09]

coverage:
  - id: D1
    description: "V15 persiste elenco por equipe da edicao, membros com FKs e constraints, rascunho/publicacao/correcao e historico versionado."
    requirement: SPRT-09
    verification:
      - kind: integration
        ref: "SportsRosterIT + SportsRosterConcurrencyIT + FlywayMigrationIT: 17/17 focal"
        status: pass
      - kind: integration
        ref: "Maven completo: Surefire 251/251, Failsafe 383/383, total 634, sem falhas/erros/ignorados"
        status: pass
    human_judgment: false
  - id: D2
    description: "Contrato OpenAPI de elenco foi aprovado por digest e gerado de forma deterministica sem alterar a arvore governada anterior."
    verification:
      - kind: other
        ref: "candidato 03-08 digest 19B1F6C2F78F5C93E25BD1A18C8D027C6D925E12A0AD5435EA5F2D2F53C19974"
        status: pass
      - kind: other
        ref: "governed-before/governed-after: B75187EFECB25E0D4809F6ED5D0354D329D9175DB9ACE3BCEB1BBA4ECDB7A8B2"
        status: pass
      - kind: unit
        ref: "node frontend/scripts/generate-api.mjs --check; Node completo 625/625"
        status: pass
    human_judgment: true
    rationale: "Aprovar inventario e digest do contrato gerado e um checkpoint humano; o responsavel aprovou o candidato 03-08."
  - id: D3
    description: "O4 oferece cadastro, preview, publicacao, correcao e conflito recuperavel em Console, com RosterView reutilizavel para o 03-11."
    requirement: SPRT-02
    verification:
      - kind: unit
        ref: "RosterView, SportsRostersStore e SportsRostersPage: Angular focal 6/6"
        status: pass
      - kind: automated_ui
        ref: "Playwright esportivo: 2/2 em desktop e viewport 320px"
        status: pass
    human_judgment: false
  - id: D4
    description: "Edicao publicada direta e rejeitada; somente correcao versionada com motivo altera o estado, e o snapshot permanece intacto."
    requirement: SPRT-09
    verification:
      - kind: integration
        ref: "SportsRosterIT: teste discriminante de PUT apos publicacao e snapshot imutavel"
        status: pass
      - kind: other
        ref: "git diff --check"
        status: pass
    human_judgment: false

duration: multi-session
completed: 2026-08-30
status: complete-pending-git
---

# Fase 3 Plano 03-08: Elencos versionados e O4

**Elencos por equipe da edicao com snapshots publicados imutaveis, correcoes auditadas e Console O4 em Angular Signals**

## Performance

- **Duration:** execucao repartida em multiplas sessoes
- **Started:** 2026-08-30
- **Completed:** 2026-08-30
- **Tasks:** 5/5
- **Files modified:** 28 paths com mudanca de conteudo/status; quatro metadados do candidato permanecem ignorados intencionalmente; `frontend/src/app/api/generated/index.ts` e `frontend/src/app/api/generated-client.contract.spec.ts` permaneceram byte-identicos.

## Accomplishments

- Criou V15 com `sports_roster`, membros, publicacoes e membros de publicacao, incluindo FKs, unicidades, checks, lock e triggers de imutabilidade.
- Implementou rascunho, preview, publicacao e correcao versionada; a autoridade e sempre derivada da equipe persistida e da competicao da edicao.
- Gerou e consumiu o contrato OpenAPI aprovado para elencos, incluindo os quatro modelos tipados adicionais ratificados.
- Entregou O4 com adapter, store privado em Signals, estados 401/403 fail-closed, conflitos 409 e `RosterViewComponent` sem rota publica antecipada.
- Corrigiu a lacuna encontrada durante a verificacao: `PUT` nao pode editar elenco ja `PUBLICADO`; a alteracao publicada exige correcao com motivo e nova versao.

## Task Commits

- `f0ff725` - `feat(03-08): deliver versioned sports rosters` - implementacao, testes, OpenAPI, cliente gerado e O4 em 27 paths tecnicos.
- `docs(03-08): formalize versioned sports rosters` - este commit formaliza o PLAN e o SUMMARY; seu proprio SHA nao e citado dentro dele para evitar autorreferencia.

## Files Created/Modified

- `backend/src/main/resources/db/migration/V15__create_sports_rosters.sql` - tabelas de rascunho, membros e snapshots append-only.
- `backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterService.java` - comandos escopados, locking, publicacao e correcao.
- `backend/src/test/java/br/com/betufu/sports/SportsRosterIT.java` - ciclo funcional, privacidade, escopo e regra pos-publicacao.
- `backend/src/test/java/br/com/betufu/sports/SportsRosterConcurrencyIT.java` - corridas de edicao/publicacao sem overwrite.
- `backend/openapi/betufu.yaml` e `frontend/scripts/generate-api.mjs` - contrato candidato e geracao governada.
- `frontend/src/app/features/sports/operations/rosters/` - adapter, store e pagina O4.
- `frontend/src/app/shared/sports/roster-view.component.ts` - renderer compartilhado para expansao publica futura.

## Decisions Made

- A colisao V14 foi resolvida por renumeracao forward-only para V15 antes da migration; nenhuma migration aplicada foi reescrita.
- Snapshot de publicacao guarda somente allowlist esportiva publica e identificadores de dominio; matricula, HMAC, titulares e reservas por partida ficam fora.
- A edicao direta de elenco publicado foi explicitamente bloqueada durante a verificacao para preservar a regra D-72.

## Deviations from Plan

### Auto-fixed Issues

**1. Colisao de versao Flyway e allowlist de saida gerada**

- **Found during:** preflight do plano, antes da implementacao.
- **Issue:** V14 ja era usada pelo endurecimento de propostas do 03-07, e a task de geracao precisou de quatro modelos adicionais.
- **Fix:** migration passou a V15; o PLAN foi atualizado para declarar os quatro modelos determinisiticos adicionais.
- **Verification:** `FlywayMigrationIT` 9/9 no gate completo e `generate-api --check` verde.

**2. Edicao direta apos publicacao**

- **Found during:** verificacao do ciclo de elenco.
- **Issue:** `PUT` ainda permitia modificar membros de elenco `PUBLICADO` sem motivo ou nova versao.
- **Fix:** service rejeita a operacao com `400 VALIDATION_FAILED`; a UI exibe somente leitura e orienta correcao versionada; teste confirma snapshot intacto.
- **Verification:** mutacao discriminante falhou contra a guarda removida e `SportsRosterIT` voltou a 6/6.

**Total deviations:** 2 grupos de correcoes necessarias para integridade de migration e regra de publicacao; nenhum ampliou a regra de negocio do plano.

## Validation

- `npm --prefix frontend ci`: 478 pacotes, 0 vulnerabilidades, executado com autorizacao.
- Focal backend: `SportsRosterIT`, `SportsRosterConcurrencyIT` e `FlywayMigrationIT`, 17/17.
- Maven completo: Surefire `251/251`; Failsafe `383/383`; `BUILD SUCCESS`, total `634` testes Java.
- Node completo: `625/625`.
- Angular focal O4/RosterView: `6/6`.
- Angular completo: `278/283`; as cinco falhas permanecem exclusivamente em `role-admin-page.component.spec.ts`, fora da allowlist e fora deste plano.
- `node frontend/scripts/generate-api.mjs --check`: verde.
- Build Angular production: verde; somente avisos de budget SCSS ja existentes.
- Playwright esportivo: `2/2`, desktop e viewport de 320px.
- `git diff --check`: verde.

## Issues Encountered

- A suite Angular completa preserva cinco falhas historicas de `role-admin-page.component.spec.ts`; nenhum arquivo desse escopo foi alterado.
- Avisos de teardown Redis/Hikari/Testcontainers e conversao LF/CRLF do checkout Windows sao nao bloqueantes e nao alteraram os codigos de saida.

## User Setup Required

None - nenhuma configuracao externa nova foi criada.

## Next Phase Readiness

- O4 e o renderer compartilhado estao prontos para o plano 03-11 adicionar as paginas publicas completas sem duplicar o contrato.
- A worktree esta pronta para revisao Codex somente leitura; qualquer edicao posterior invalida as provas e exige nova rodada.
- Integracao linear na `master`, limpeza da worktree exata e push ainda nao foram executados e precisam de autorizacoes Git separadas.

## Self-Check: PASSED

- Os artefatos-chave existem na worktree.
- O candidato possui digest aprovado e hashes governed-before/after iguais.
- Os focais e as suites agregadas exigidas foram verificados; nenhuma alteracao de codigo ocorreu depois das provas.

---
*Phase: 03-experiencia-esportiva-do-piloto*
*Completed: 2026-08-30*
