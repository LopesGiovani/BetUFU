---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 12
wave: 13
status: complete
completed: 2026-08-31
subsystem: sports-bracket
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, testcontainers]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: catalogo esportivo, equipes, eventos, resultados versionados e Arena publica
provides:
  - migration V17 para chaveamentos, rodadas, slots, partidas, publicacoes e snapshots
  - grafo derivado de eventos ligados, com validacao de ciclos, escopo, BYE e predecessores
  - comandos operacionais versionados com optimistic locking e conflitos HTTP 409
  - endpoint publico P7 sem rascunhos e endpoint operacional O7 com autorizacao por competicao
  - cliente OpenAPI governado e renderer BracketView compartilhado por P7 e O7
  - deep link publico /chaveamentos/:bracketId com adapters e stores separados
affects: [03-13, public-arena, sports-lifecycle]

actuals:
  tasks: 5
  allowlist_declared: 43
  allowlist_changed_or_considered: 42
  outside_allowlist: 0
  unchanged_allowlist_paths: 1

tech-stack:
  added: []
  patterns:
    - migration Flyway append-only para o agregado de chaveamento e snapshots imutaveis
    - grafo de slots referencia eventos e vencedores de predecessores, sem copiar participante ou placar
    - comandos de escrita exigem versao base e retornam 409 em conflito
    - endpoint publico filtra somente publicacoes oficiais; O7 mantem preview privado
    - cliente Angular usa adapter e store Signals, com BracketView compartilhado entre P7 e O7

key-files:
  created:
    - backend/src/main/resources/db/migration/V17__create_sports_brackets.sql
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketService.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketController.java
    - backend/src/test/java/br/com/betufu/sports/SportsBracketIT.java
    - frontend/src/app/api/generated/api/sports-bracket.service.ts
    - frontend/src/app/features/sports/public-bracket/public-bracket-api.adapter.ts
    - frontend/src/app/features/sports/public-bracket/public-bracket.store.ts
    - frontend/src/app/features/sports/public-bracket/public-bracket-page.component.ts
    - frontend/src/app/features/sports/operations/bracket/sports-bracket-api.adapter.ts
    - frontend/src/app/features/sports/operations/bracket/sports-bracket.store.ts
    - frontend/src/app/features/sports/operations/bracket/sports-bracket-page.component.ts
    - frontend/src/app/shared/sports/bracket-view.component.ts
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/scripts/generate-api.test.mjs
    - frontend/src/app/api/generated-client.contract.spec.ts
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - frontend/src/app/app.routes.ts

key-decisions:
  - Chaveamento e transcrito e derivado de eventos ligados; BETUFU nao sorteia confrontos.
  - Slots podem apontar para evento anterior e seu vencedor; a projecao resolve o participante atual sem copia editavel.
  - Ciclos, eventos fora do escopo, posicoes duplicadas, eventos reutilizados e predecessores invalidos falham fechado.
  - Publicacao e correcao criam versoes e snapshots imutaveis, sempre com fonte oficial e versao declaradas.
  - O7 consulta por competicao autorizada e rotula preview nao publicado; P7 expoe somente snapshot PUBLICADO.
  - P7 e O7 compartilham apenas o renderer BracketView; adapters, stores e estados permanecem isolados.

openapi:
  candidate_digest: 8BE63578F11D7D3DBC4F771A8A0F03B41482C1B7C00698AAC69165EF1A216396
  inventory_files: 121
  governed_before: 46249F23A6F2AC1A11CFC40C88C2CF9C04FF31DE2C3A3F0B75D2CAB96FB85F59
  governed_after: 46249F23A6F2AC1A11CFC40C88C2CF9C04FF31DE2C3A3F0B75D2CAB96FB85F59

coverage:
  - id: D1
    description: "Migration V17 e grafo derivado rejeitam ciclos, escopo divergente e dependencias impossiveis."
    verification:
      - "SportsBracketIT: 3/3"
      - "FlywayMigrationIT: 11/11"
    status: pass
  - id: D2
    description: "Contrato OpenAPI e cliente gerado permanecem alinhados ao candidato aprovado."
    verification:
      - "node frontend/scripts/generate-api.mjs --check"
      - "Candidato aprovado por digest; governed-before e governed-after identicos"
    status: pass
  - id: D3
    description: "P7 possui deep link publico e O7 possui preview operacional sem compartilhamento de estado."
    verification:
      - "Angular focal: 6/6"
      - "Angular completo: 316/316"
      - "Playwright esportivo: 3/3"
      - "Builds development, homolog e production: sucesso"
    status: pass
  - id: D4
    description: "A suite backend completa permanece verde depois da correcao do teste intermitente do painel."
    verification:
      - "Maven completo: Surefire/Failsafe 416/416, BUILD SUCCESS"
      - "Falha inicial OperationalPanelIT isolada; execucao focal posterior passou 6/6"
    status: pass

deviations:
  - "A allowlist foi reconciliada com o diff real: dez modelos TypeScript gerados foram acrescentados ao PLAN; 42 paths foram alterados e um path listado permaneceu inalterado."
  - "A primeira suite completa apresentou uma falha intermitente em OperationalPanelIT.adminGeralReceivesAllFourBlocks; a classe isolada passou 6/6 e a suite completa repetida passou 416/416 sem alterar o contrato."
  - "O checkout principal ja continha o commit 424d98b (RepoWise/MCP), filho direto da base b84e3db; a integracao da W13 preserva esse commit. Os quatro metadados do candidato sao versionados conforme o .gitignore vigente."

validation:
  - "git diff --check: sem erros"
  - "Node: 625/625"
  - "Warnings NG8102, budgets CSS, Mockito/ByteBuddy, teardown Redis/Testcontainers e LF/CRLF sao informativos e nao invalidaram as provas."

issues:
  - "Nenhum problema de regra de negocio ou bloqueio tecnico conhecido ao final da implementacao."

git:
  status: complete
  implementation_commit: 7088123
  documentation_commit: current documentation commit (hash intentionally omitted)
  integration: fast-forward onto master at 424d98b
  cleanup: worktree-agent-03-12 and branch removed
  push: origin/master synchronized at 9f3bbea

next-phase-readiness:
  - "O grafo de chaveamento V17, P7 e O7 estao prontos para a revisao e integracao linear na master."
  - "Apos a integracao, a proxima frente e 03-13/W14, conforme o roadmap da Fase 3."
---

# Fase 3 Plano 03-12: chaveamento derivado P7/O7

O plano entrega chaveamentos como uma projecao derivada de eventos oficiais ligados. A migration V17 guarda a estrutura versionada e snapshots imutaveis; o servico valida o grafo e resolve slots por evento/predecessor, sem sortear confrontos nem duplicar participantes ou placares. A Arena publica P7 mostra apenas publicacoes oficiais, enquanto O7 usa a mesma representacao visual para um preview privado autorizado por competicao.

## Operacoes Git

Os commits tecnico e documental foram integrados por fast-forward na master; a worktree e a branch exatas foram removidas e `origin/master` foi sincronizada em `9f3bbea`.

## Self-check

- O candidato aprovado tem digest `8BE63578F11D7D3DBC4F771A8A0F03B41482C1B7C00698AAC69165EF1A216396` e hashes governed-before/governed-after identicos.
- Os testes focais e completos passaram depois de isolar a falha intermitente inicial do painel operacional.
- Nenhuma regra de negocio foi alterada para acomodar a falha de teste; a evidencia final cobre ciclo, escopo, derivacao, publicacao, autorizacao e fronteira publica.
