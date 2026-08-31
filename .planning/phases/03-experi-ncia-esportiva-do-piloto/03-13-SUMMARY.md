---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 13
wave: 14
status: complete-pending-git
completed: 2026-08-31
subsystem: sports-standings
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, testcontainers]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: catalogo esportivo, eventos, resultados versionados e chaveamentos publicados
provides:
  - migration V18 para snapshots de classificacao, colunas e linhas ordenadas
  - classificacao oficial transcrita, versionada e ligada a fonte, sem calculo de pontos ou desempate
  - endpoints publicos e operacionais com fronteira de autorizacao por competicao
  - cliente OpenAPI governado para SportsStandingsSnapshot e SportsStandingsRow
  - ResponsiveDataView compartilhado, com tabela desktop e cards rotulados em largura estreita
  - deep links /classificacoes/:standingsId e /operacao/esportes/classificacoes/:standingsId
affects: [03-14, public-arena, sports-lifecycle]

actuals:
  tasks: 5
  allowlist_declared: 33
  allowlist_changed_or_considered: 32
  outside_allowlist: 0
  unchanged_allowlist_paths: 1

tech-stack:
  added: []
  patterns:
    - migration Flyway append-only para snapshot oficial e versoes imutaveis
    - payload generico preserva colunas, ordem, fonte e valores declarados pelo boletim
    - publicacao e correcao criam novas versoes; rascunho nunca cruza a fronteira publica
    - adapters e stores Signals publicos e operacionais permanecem separados
    - ResponsiveDataView preserva semantica de cabecalhos em tabela e cards

key-files:
  created:
    - backend/src/main/resources/db/migration/V18__create_sports_standings.sql
    - backend/src/main/java/br/com/betufu/sports/internal/standings/SportsStandingsModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/standings/SportsStandingsRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/standings/SportsStandingsService.java
    - backend/src/main/java/br/com/betufu/sports/internal/standings/SportsStandingsController.java
    - backend/src/test/java/br/com/betufu/sports/SportsStandingsIT.java
    - frontend/src/app/api/generated/api/sports-standings.service.ts
    - frontend/src/app/api/generated/model/sports-standings-snapshot.ts
    - frontend/src/app/api/generated/model/sports-standings-row.ts
    - frontend/src/app/shared/responsive-data-view/responsive-data-view.component.ts
    - frontend/src/app/features/sports/public-standings/public-standings-page.component.ts
    - frontend/src/app/features/sports/operations/standings/sports-standings-page.component.ts
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
  - A classificacao e transcrita de um boletim oficial; BETUFU nao calcula pontos, criterios ou desempates.
  - Colunas e linhas pertencem ao snapshot versionado e mantem a ordem declarada pela fonte.
  - Correcao preserva a versao anterior e exige nova fonte/versao; publicacao nao sobrescreve historico.
  - O endpoint publico retorna somente snapshot publicado; o operacional pode consultar rascunho autorizado.
  - UUID opaco identifica o snapshot, mas autoridade continua derivada do escopo autenticado no servidor.
  - P8 e O7 compartilham somente o renderer; adapters, stores e estado privado nao sao reutilizados.

openapi:
  candidate_digest: 5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B
  inventory_files: 137
  governed_before: 5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B
  governed_after: 5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B

coverage:
  - id: D1
    description: "V18 e o agregado preservam fonte, versao, ordem e historico sem motor de regras esportivas."
    verification:
      - "SportsStandingsIT: 4/4"
      - "FlywayMigrationIT: 12/12"
    status: pass
  - id: D2
    description: "Contrato OpenAPI e cliente gerado correspondem ao candidato aprovado."
    verification:
      - "node frontend/scripts/generate-api.mjs --check"
      - "Node completo: 625/625"
      - "digest, governed-before e governed-after identicos"
    status: pass
  - id: D3
    description: "P8 e O7 exibem o mesmo snapshot com estados separados e reflow acessivel."
    verification:
      - "Angular focal: 15/15"
      - "Angular completo final: 330/330"
      - "Playwright esportivo: 3/3"
      - "Builds development, homolog e production: sucesso"
    status: pass
  - id: D4
    description: "A suite backend completa permanece verde com a nova migration e os endpoints de standings."
    verification:
      - "Surefire: 266/266"
      - "Failsafe: 421/421"
      - "Maven verify: BUILD SUCCESS, total 687 testes"
    status: pass

deviations:
  - "O digest inicialmente apresentado era o 8BE635... da wave 03-12. O candidato 03-13 foi regenerado e aprovado com o digest correto 5BC25D0D...."
  - "frontend/src/app/api/generated/index.ts estava na allowlist, mas permaneceu inalterado porque os reexports necessarios ja eram alcancados pelos barrels existentes."
  - "A primeira suite Angular completa teve timeout intermitente em athlete-link-proposal-page.component.spec.ts, fora da allowlist. O spec isolado passou 2/2 e a repeticao completa passou 330/330 sem mudanca de codigo."
  - "O Maven emitiu avisos de reconexao Redis/PostgreSQL e encerrou um fork apos o timeout de teardown; todos os testes terminaram verdes e o processo retornou exit code 0 com BUILD SUCCESS."
  - "O novo ResponsiveDataView possui 4.64 kB de estilo e excede o budget informativo de 4 kB em 638 bytes; homolog e production continuam passando."

validation:
  - "git diff --check: sem erros"
  - "Allowlist: 32 paths tecnicos alterados, zero paths extras, um path listado inalterado"
  - "Diretorio temporario frontend/tmp/api-candidate-debug-03-13-1 removido"
  - "Warnings NG8102, budgets CSS, teardown Redis/Testcontainers e LF/CRLF sao informativos e nao invalidaram as provas"

issues:
  - "Nenhum problema de regra de negocio ou bloqueio tecnico conhecido ao final da implementacao."
  - "A reducao do CSS do ResponsiveDataView pode ser tratada como manutencao sem alterar seu contrato visual ou semantico."

git:
  status: technical-committed
  implementation_commit: e03c0b535fc380ddf8e771b937b25dd2d822beb5
  documentation_commit: pending authorization
  integration: pending authorization
  cleanup: pending authorization
  push: pending authorization

next-phase-readiness:
  - "O snapshot V18, P8 e O7 estao certificados e prontos para revisao somente leitura e operacoes Git separadas."
  - "Depois da integracao linear e limpeza do 03-13, a proxima frente tecnica e 03-14/W15."
---

# Fase 3 Plano 03-13: classificacao transcrita P8/O7

O plano entrega classificacoes oficiais como snapshots transcritos de boletins. A V18 guarda colunas, linhas, fonte e versoes imutaveis; o backend valida estrutura, escopo e lifecycle, mas nao calcula pontuacao ou desempate. A Arena publica P8 mostra somente a publicacao oficial, enquanto O7 permite preparar, publicar e corrigir o snapshot dentro da competicao autorizada.

## Operacoes Git

O commit tecnico `e03c0b5` foi criado com os 32 paths certificados. O commit documental, a integracao, a limpeza de worktree/branch e o push permanecem separados e pendentes.

## Self-check

- O candidato aprovado tem digest `5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B` e hashes governed-before/governed-after identicos.
- Backend, Node, Angular, builds e Playwright passaram; a unica falha inicial foi um timeout Angular intermitente fora da allowlist e ficou verde isolado e na repeticao completa.
- Nenhuma regra esportiva foi inventada para fechar os testes: a BETUFU continua transcrevendo a fonte oficial sem calcular classificacao.
