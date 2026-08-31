---
phase: 03-experiencia-esportiva-do-piloto
plan: 11
wave: 12
status: complete
completed: 2026-08-31
subsystem: public-arena
tags: [spring-boot, postgresql, openapi, angular, signals, playwright]

requires:
  - phase: 03-experiencia-esportiva-do-piloto
    provides: catalogo esportivo, organizacoes, equipes, atletas, elencos e lifecycle de eventos/resultados
provides:
  - consultas publicas P1-P6 com filtro fail-closed de publicacao
  - DTOs publicos sem PII, atores internos, HMAC, matricula ou IDs tecnicos
  - cliente OpenAPI governado e rotas/deep links da Arena publica
  - adapter/store Angular com Signals e estados loading, vazio, erro e 404
affects: [03-12, public-arena, sports-lifecycle]

actuals:
  tasks: 5
  allowlist_declared: 52
  allowlist_changed_or_considered: 52
  outside_allowlist: 0
  missing_paths: 0

tech-stack:
  added: []
  patterns:
    - consultas anonimas retornam somente snapshots e entidades PUBLICADO
    - contrato OpenAPI candidato aprovado por digest antes da geracao governada
    - componentes publicos usam adapter e store Signals, sem chamar HTTP diretamente
    - deep links publicos mantem estados honestos e navegacao contextual

key-files:
  created:
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewController.java
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewService.java
    - backend/src/test/java/br/com/betufu/sports/SportsPublicViewIT.java
    - frontend/src/app/features/sports/public-view/sports-public-view.store.ts
    - frontend/src/app/features/sports/public-view/sports-public-view-api.adapter.ts
    - frontend/src/app/features/sports/public-competition/public-competition-page.component.ts
    - frontend/src/app/features/sports/public-event/public-event-page.component.ts
    - frontend/src/app/features/sports/public-organization/public-organization-page.component.ts
    - frontend/src/app/features/sports/public-team/public-team-page.component.ts
    - frontend/src/app/features/sports/public-athlete/public-athlete-page.component.ts
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerController.java
    - backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/api/sports-public.service.ts
    - frontend/src/app/api/generated/model/models.ts
    - frontend/src/app/app.routes.ts
    - frontend/e2e/sports-pilot.spec.ts
    - .planning/phases/03-experiencia-esportiva-do-piloto/03-11-PLAN.md

key-decisions:
  - A fronteira anonima publica somente dados allowlisted em estado PUBLICADO; rascunhos e escopos operacionais ficam fora.
  - Os DTOs nao carregam matricula, e-mail, HMAC, atores internos, lock/version ou IDs tecnicos de snapshots.
  - O endpoint legado de evento publico delega ao detalhe rico, evitando handlers Spring duplicados.
  - P1 permanece a agenda ja entregue; 03-11 completa P2-P6 sem duplicar o hero da home.
  - O candidato OpenAPI foi aprovado antes da geracao do cliente e os hashes governed-before/governed-after permanecem iguais.

openapi:
  candidate_digest: 46249F23A6F2AC1A11CFC40C88C2CF9C04FF31DE2C3A3F0B75D2CAB96FB85F59
  inventory_files: 121
  governed_before: 7F65D6527FBEF16C53EF34A1A0A3F0D8C8C73BB511030F1140C5F5C9582F90EC
  governed_after: 7F65D6527FBEF16C53EF34A1A0A3F0D8C8C73BB511030F1140C5F5C9582F90EC

coverage:
  - id: D1
    description: "Queries publicas P1-P6 filtram publicacao e retornam 404 quando a entidade nao e publicavel."
    verification:
      - "Maven focal: SportsPublicViewIT 14/14 e SportsTracerIT 10/10, BUILD SUCCESS"
      - "Maven completo: BUILD SUCCESS"
    status: pass
  - id: D2
    description: "Contrato OpenAPI e cliente gerado permanecem alinhados ao candidato aprovado."
    verification:
      - "node frontend/scripts/generate-api.mjs --check"
      - "Candidato aprovado por digest; governed-before e governed-after iguais"
    status: pass
  - id: D3
    description: "Arena publica possui rotas P1-P6, estados honestos, deep links e percurso contextual."
    verification:
      - "Node completo: 625/625"
      - "Angular completo: 309/309"
      - "Builds development, homolog e production: sucesso"
      - "Playwright completo: 3/3"
    status: pass
  - id: D4
    description: "A regressao de time que deixa de ser PUBLICADO nao vaza elenco, eventos nem estatisticas."
    verification:
      - "SportsPublicViewIT cobre a transicao PUBLICADO para RASCUNHO e a ausencia dos dados na Arena"
    status: pass

deviations:
  - "A allowlist foi reconciliada de acordo com o diff real: o plano, o tracer historico, seu IT, modelos gerados auxiliares e as paginas P4-P6 fazem parte do conjunto certificado."
  - "Os quatro metadados do candidato ficaram fora do Git por politica do repositorio e foram removidos junto com a worktree isolada apos o digest ser registrado neste SUMMARY."
  - "SportsPublicViewRepository passou a exigir sports_team.status = PUBLICADO nas consultas de elenco de organizacao e atleta, com teste de regressao adicional."

validation:
  - "git diff --check: sem erros"
  - "Warnings NG8102, budgets CSS de componentes antigos, Mockito/ByteBuddy, teardown Redis/Testcontainers e LF/CRLF sao informativos e nao invalidam as provas."

issues:
  - "Nenhum problema de regra de negocio ou bloqueio tecnico conhecido ao final da implementacao."

git:
  status: complete
  implementation_commit: 36a3d8c
  documentation_commit: 1db2d19
  integration: fast-forward completed
  cleanup: worktree-agent-03-11 and branch removed
  push: origin/master synchronized

next-phase-readiness:
  - "A Arena publica P1-P6 e o contrato de leitura estao prontos para o plano 03-12/W13."
  - "A worktree 03-11 foi integrada e removida; o proximo plano tecnico e 03-12/W13."
---

# Fase 3 Plano 03-11: Arena publica P1-P6

O plano completa a superficie publica da Arena para agenda, competicao, evento, organizacao, equipe e atleta. O backend consulta somente dados publicados e transforma as respostas em DTOs publicos reduzidos. O Angular consome o cliente governado por adapter e store Signals, com rotas diretas e estados explicitos.

## Operacoes Git

Os commits tecnico e documental foram integrados por fast-forward na `master`; a worktree e a branch temporarias foram removidas e `origin/master` foi sincronizada.

## Self-check

- O candidato aprovado tem digest `46249F23A6F2AC1A11CFC40C88C2CF9C04FF31DE2C3A3F0B75D2CAB96FB85F59` e hashes governed-before/governed-after identicos.
- Os 52 paths declarados foram reconciliados com 52 paths alterados ou considerados; `outside=0` e `missing=0`.
- A implementacao nao usa fixtures para esconder lacunas da API e nenhuma PII atravessa a fronteira anonima.
- As suites focal e completas, os tres builds Angular, o gerador e o percurso Playwright passaram conforme registrado acima.
