---
phase: 4
slug: mercados-pr-jogo-e-odds-transparentes
status: certification_pending_uat
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-04
---

# Phase 4 - Validation Strategy

> Contrato de validacao para mercados pre-jogo e odds transparentes. Este
> arquivo foi reconciliado com 19 planos e 46 tasks; os comandos de produto
> permanecem propostas futuras. A evidencia corrente do Plano 04-15/T1-T2 e
> registrada abaixo, separada do historico documental anterior.

## Scope and Authority

- Worktree de execucao desta retomada: `C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-04-15`, branch `worktree-agent-04-15`.
- Snapshot documental/base: `0963c965e8fced9af426727cecf9a0ed9306dbe0`, publicado em `master` e `origin/master`; o HEAD tecnico validado da worktree e `ee611bec0bf2d4fc56a79df4ba27e8e9f06a944b`. RepoWise foi consultado no preflight em `indexed_commit=0963c965e8fc`, `live_head=0963c965e8fc` e `index_behind=false`; nao executar `repowise update`.
- `STATE.md` inicia com `state_head: 78a8cc896a4ec458a0980ffefe3109af917d48e9`; a reconciliacao com a base e o HEAD tecnico esta registrada nesta formalizacao, sem editar `STATE.md` fora da allowlist. A fase continua sem UI-SPEC aprovado: 04-13 e 04-14 permanecem bloqueados ate esse gate, sem criar o documento nesta allowlist.
- `eligibilitySubject=dataset_snapshot` no manifesto e no relatorio significa que
  `calibrationEligible`/`productionEligible` autorizam o dataset inteiro; os campos
  `eligibleForCalibration` das fixtures e `calibrationEligible` da quarentena sao
  classificacoes por registro e nunca promovem o dataset por inferencia.
- `scripts/data/laufu/laufu_partidas_2026.json` e a entrada bruta imutavel lida pelo
  validador, produzida em bloco anterior autorizado. Somente esse JSON fixo e
  evidencia admissivel nesta captura; CSV e Markdown em `scripts/data/laufu/` e
  HTMLs em `.cache/laufu/` sao residuos de captura fora da allowlist e nao entram
  no gate.
- As contagens preliminares anotadas em `04-CONTEXT.md` (`326` concluidas,
  `299` agendadas, `46` W.O.) diferem da captura viva (`333` concluidas,
  `291` agendadas, `1` adiada, `46` W.O.; `671` no total). O raw snapshot e o
  Git vivos sao a autoridade desta validacao; a divergencia nao e corrigida por
  inferencia.
- A captura nao registra hash/versao do parser nem argumentos efetivos do
  extrator. Isso permanece limitacao exploratoria documentada; nenhum dado do
  extrator e promovido a fonte de producao.
- A fase continua sem UI-SPEC aprovado: 04-13 e 04-14 permanecem bloqueados
  ate esse gate, sem criar o documento nesta allowlist.
- Nenhum teste de produto, navegador, npm, Playwright ou backtest/LAUFU deve ser
  inferido deste plano. A retomada executou o focal literal da T1, os dois
  modos do validador e as suites agregadas Node/Maven registradas abaixo. O T2
  foi aprovado por resposta humana literal e digest vinculado; integracao Git e
  limpeza permanecem registradas somente no fechamento correspondente.

### Calibration Gate Boundary

- O T1 valida as assercoes especificas da captura atual: fingerprint LAUFU,
  contagens observadas, parametros abertos, backtest `not_run`, fixtures e
  quarentena derivadas dessa captura.
- O T2 valida primeiro as invariantes compartilhadas do bundle: schemas, links,
  paths seguros, `realpath` dentro da worktree, tamanho/SHA-256 da fonte e
  consistencia dos digests entre manifesto, relatorio, inventario e quarentena.
  Depois valida os criterios de um candidato aprovado, sem chamar o T1 inteiro.
- A captura LAUFU e rejeitada por path/fingerprint mesmo que seus JSONs sejam
  editados para parecerem aprovados. Uma evidencia temporal futura exige um novo
  JSON versionado sob `scripts/data/`, obtido em bloco autorizado; editar os
  artefatos atuais nao cria known_at, linhagem ou backtest.
- O manifesto e o relatorio usam schemas `v2`; fixtures e quarentena usam `v1`
  intencionalmente, pois seus contratos de inventario/quarentena nao mudaram e
  nao carregam parametros de pricing. Essa diferenca nao implica compatibilidade
  automatica com um motor de odds.
- No T2, `reviewedArtifactHashes.fixtureRegistry` cobre o inventario JSON e
  `reviewedArtifactHashes.fixtureFiles` cobre, individualmente, os sete JSONs
  de fixture. O manifesto e o validador nao entram nesse mapa por autorreferencia;
  seus SHA-256 devem ser congelados externamente no snapshot Git antes da revisao.
- Um candidato aprovado precisa permitir derivar do raw `partidas` as linhas
  elegiveis, seus IDs de treino/avaliacao e os cutoffs por `known_at`; o T2 nao
  aceita contagens declaradas para um dataset vazio, sem linhagem confirmada ou
  com sobreposicao de split. Metricas complexas continuam sujeitas a revisao
  humana independente referenciada pelo checkpoint.
- Para cada fixture real, o T2 reconcilia `fixture.source.recordId` com o
  `sourceRecordId` do inventario e com a linha correspondente em `raw.partidas`,
  incluindo URI, timestamp, escopo e identidades observadas; objetos futuros de
  Davidson e confianca devem conter ao menos um valor numerico finito, sem fixar
  ainda os nomes/chaves do schema estatistico.

## Test Infrastructure

| Area | Command / boundary |
|---|---|
| Backend focal | `node scripts/maven.mjs -f backend/pom.xml -Dtest=<classes> test` |
| PostgreSQL/Flyway | Testcontainers existente; ambiente e aprovacao separados |
| Contract | `node frontend/scripts/generate-api.mjs --check`; `node --test frontend/scripts/generate-api.test.mjs` |
| Angular | `npm --prefix frontend run test -- --watch=false --include ...` |
| Browser | Playwright Chromium/Firefox, sem watch |
| Aggregates | Maven `verify`, Node `node --test scripts/*.test.mjs`, Angular completo |
| Evidence | Contar testes, falhas, erros, skips, arquivos, browsers, HEAD/status e SHA-256 literalmente |

## Dependency and Wave Matrix

| Plan | Wave | Depends on | Paths | Tasks |
|---|---:|---|---:|---:|
| 04-01 | 0 | 03-27 | 12 | 2 |
| 04-15 | 1 | 04-01 | 3 | 2 |
| 04-02 | 2 | 04-01, 04-15 | 10 | 3 |
| 04-03 | 3 | 04-02, 04-15 | 1 | 2 |
| 04-12 | 3 | 04-01 | 7 | 2 |
| 04-09 | 4 | 04-03 | 14 | 3 |
| 04-18 | 5 | 04-09, 04-02, 04-15 | 12 | 4 |
| 04-16 | 6 | 04-03, 04-15, 04-18 | 14 | 3 |
| 04-17 | 7 | 04-16, 04-02 | 5 | 2 |
| 04-04 | 8 | 04-09, 04-12, 04-17 | 10 | 2 |
| 04-10 | 9 | 04-04, 04-12, 04-17 | 7 | 2 |
| 04-19 | 10 | 04-10, 04-17, 04-18, 04-09 | 14 | 4 |
| 04-05 | 11 | 04-02, 04-09, 04-04, 04-10, 04-17, 04-18, 04-19 | 14 | 2 |
| 04-06 | 12 | 04-09, 04-05, 04-10, 04-18 | 8 | 2 |
| 04-07 | 13 | 04-05, 04-06, 04-16, 04-18 | 7 | 2 |
| 04-11 | 14 | 04-07 | 13 | 2 |
| 04-13 | 15 | 04-11 | 9 | 2 |
| 04-14 | 16 | 04-06, 04-13, 04-18 | 14 | 2 |
| 04-08 | 17 | 04-01, 04-02, 04-03, 04-04, 04-05, 04-06, 04-07, 04-09, 04-10, 04-11, 04-12, 04-13, 04-14, 04-15, 04-16, 04-17, 04-18, 04-19 | 7 | 3 |

A ordem e por dependencias, nao pelo numero do plano. Somente 04-03 e 04-12
compartilham Wave 3, em worktrees diferentes. Tasks do mesmo plano sao sequenciais.
Existem 19 planos, 46 tasks, 9 checkpoints humanos e 168 paths propostos distintos;
esses paths de produto nao sao autorizados para escrita nesta sessao.

## Per-Task Verification Map

| Task | Plan/Wave | Requirements | Threat Ref | Expected evidence | Automated verify | Human gate |
|---|---|---|---|---|---|---|
| 04-01/T1 | 04-01/0 | ODDS-02, ODDS-03, ODDS-04, ODDS-06, ODDS-07 | T-04-CALIBRATION, T-04-TAXONOMY, T-04-LAUFU | Tracar evidencia de calibracao ate parametros governados | `node scripts/validate-odds-calibration.mjs --check` | No |
| 04-01/T2 | 04-01/0 | ODDS-02, ODDS-03, ODDS-04, ODDS-06, ODDS-07 | T-04-CALIBRATION, T-04-TAXONOMY, T-04-LAUFU | Aprovar calibracao e evidencias antes de qualquer preco | `node scripts/validate-odds-calibration.mjs --check --require-approval` | BLOCKING |
| 04-15/T1 | 04-15/1 | ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-08, ODDS-13 | T-04-DUAL-HEAD, T-04-HINDSIGHT, T-04-LEGACY | Produzir candidato de autoridade, taxonomia e tempo | `node --test scripts/validate-sports-odds-source-candidate.test.mjs` | No |
| 04-15/T2 | 04-15/1 | ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-08, ODDS-13 | T-04-DUAL-HEAD, T-04-HINDSIGHT, T-04-LEGACY | Aprovar autoridade e politicas de source | `node scripts/validate-sports-odds-source-candidate.mjs --require-approval` | BLOCKING |
| 04-02/T1 | 04-02/2 | ODDS-01, ODDS-04, ODDS-13 | T-04-MODULE, T-04-TAXONOMY | Montar candidato documental de contrato, taxonomia e state machine | `node --test scripts/odds-contract.test.mjs` | No |
| 04-02/T2 | 04-02/2 | ODDS-01, ODDS-04, ODDS-13 | T-04-MODULE, T-04-TAXONOMY | Aprovar fronteira, taxonomia, estados e autoridade | `node scripts/odds-contract.test.mjs --require-approval` | BLOCKING |
| 04-02/T3 | 04-02/2 | ODDS-01, ODDS-04, ODDS-13 | T-04-MODULE, T-04-TAXONOMY | Implementar contrato e state machine aprovados | `powershell -NoProfile -Command { node --test scripts/odds-contract.test.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsContractArchitectureTest,SportsOddsBoundaryArchitectureTest' test; exit $LASTEXITCODE }` | No |
| 04-03/T1a | 04-03/3 | ODDS-08, ODDS-13 | T-04-REWRITE, T-04-RACE | Preparar candidato documental de schema ate a revisao | `node -e "const fs=require('node:fs');const x=JSON.parse(fs.readFileSync('scripts/odds-schema-candidate.json','utf8'));const required=['migrationInventoryDigest','migrationSequence','tables','constraints','forbiddenObjects','sourceAuthority','compatibility'];if(required.some(k=>!(k in x)))process.exit(1);if(!Array.isArray(x.migrationSequence)\|\|x.migrationSequence.length<1)process.exit(2);if(!Array.isArray(x.tables)\|\|x.tables.length<5\|\|!Array.isArray(x.constraints)\|\|x.constraints.length<5)process.exit(3);if(!x.forbiddenObjects.includes('ledger')\|\|!x.forbiddenObjects.includes('settlement'))process.exit(4)"` | No |
| 04-03/T1 | 04-03/3 | ODDS-08, ODDS-13 | T-04-REWRITE, T-04-RACE | Aprovar schema de linhagem e migration forward-only | `node -e "const fs=require('node:fs');const x=JSON.parse(fs.readFileSync('scripts/odds-schema-candidate.json','utf8'));const required=['migrationInventoryDigest','migrationSequence','tables','constraints','forbiddenObjects','sourceAuthority','compatibility'];if(required.some(k=>!(k in x)))process.exit(1);if(!Array.isArray(x.migrationSequence)\|\|x.migrationSequence.length<1)process.exit(2);if(!Array.isArray(x.tables)\|\|x.tables.length<5\|\|!Array.isArray(x.constraints)\|\|x.constraints.length<5)process.exit(3);if(!x.forbiddenObjects.includes('ledger')\|\|!x.forbiddenObjects.includes('settlement'))process.exit(4)"` | BLOCKING |
| 04-12/T1 | 04-12/3 | ODDS-02, ODDS-03, ODDS-04, ODDS-06, ODDS-07, ODDS-08 | T-04-CALIBRATION, T-04-TAXONOMY | Derivar oraculos Elo independentes antes do motor | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCalibrationFixtureTest test` | No |
| 04-12/T2 | 04-12/3 | ODDS-02, ODDS-03, ODDS-04, ODDS-06, ODDS-07, ODDS-08 | T-04-CALIBRATION, T-04-TAXONOMY | Completar oraculos de empate, confianca e margem | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCalibrationFixtureTest test` | No |
| 04-09/T1 | 04-09/4 | ODDS-08, ODDS-13 | T-04-REWRITE, T-04-RACE | Tracar migration odds ratificada ate gravacao e leitura protegidas no PostgreSQL | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsMigrationIT,FlywayMigrationIT,FlywayChecksumIT,AuthoritativeStorageIT' test` | No |
| 04-09/T2 | 04-09/4 | ODDS-08, ODDS-13 | T-04-REWRITE, T-04-RACE | Persistir identidade de mercado e calculo por repositorio | `powershell -NoProfile -Command { node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsSchemaReviewTest test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPersistenceIT test; exit $LASTEXITCODE }` | No |
| 04-09/T3 | 04-09/4 | ODDS-08, ODDS-13 | T-04-REWRITE, T-04-RACE | Persistir historico de publicacao, lifecycle e override com concorrencia | `powershell -NoProfile -Command { node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsSchemaReviewTest test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsMigrationIT,OddsPersistenceIT,OddsPersistenceConcurrencyIT,FlywayMigrationIT,FlywayChecksumIT,AuthoritativeStorageIT' test; exit $LASTEXITCODE }` | No |
| 04-18/T1a | 04-18/5 | ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-AUDIT-VOCAB | Preparar candidato de governanca sem runtime | `node -e "const x=JSON.parse(require('node:fs').readFileSync('scripts/odds-governance-candidate.json','utf8'));if(!x.commands\|\|!x.auditActions\|\|!x.authorizationOwners\|\|!x.migrationCandidate)process.exit(1)"` | No |
| 04-18/T1 | 04-18/5 | ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-AUDIT-VOCAB | Aprovar matriz de capabilities e acoes auditaveis | `node -e "const x=JSON.parse(require('node:fs').readFileSync('scripts/odds-governance-candidate.json','utf8'));if(!x.commands\|\|!x.auditActions\|\|!x.authorizationOwners\|\|!x.migrationCandidate)process.exit(1)"` | BLOCKING |
| 04-18/T2 | 04-18/5 | ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-AUDIT-VOCAB | Materializar capability e auditoria | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsOperationalCapabilitiesIT,OddsAuthorizationIT,OddsAuditVocabularyIT,AuditImmutabilityIT,FlywayMigrationIT,FlywayChecksumIT' test` | No |
| 04-18/T3 | 04-18/5 | ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-AUDIT-VOCAB | Certificar compatibilidade e no-write | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsOperationalCapabilitiesIT,OddsAuthorizationIT,OddsAuditVocabularyIT,AuditImmutabilityIT,AuditEventIT,OperationalCapabilitiesIT,OperationalCapabilitiesResponseTest' test` | No |
| 04-16/T1 | 04-16/6 | ODDS-01, ODDS-04, ODDS-08, ODDS-13 | T-04-DUAL-HEAD, T-04-ADMIN-SCORE | Aplicar schema de outcome e provar invariantes | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsOutcomeAuthorityIT,FlywayMigrationIT,FlywayChecksumIT' test` | No |
| 04-16/T2 | 04-16/6 | ODDS-01, ODDS-04, ODDS-08, ODDS-13 | T-04-DUAL-HEAD, T-04-ADMIN-SCORE | Implementar repositorio e comandos administrativos | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsAdministrativeOutcomeIT' test` | No |
| 04-16/T3 | 04-16/6 | ODDS-01, ODDS-04, ODDS-08, ODDS-13 | T-04-DUAL-HEAD, T-04-ADMIN-SCORE | Provar corrida, replay e compatibilidade | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsOutcomeConcurrencyIT,SportsOutcomeAuthorityIT,SportsEventResultIT,SportsEventResultConcurrencyIT' test` | No |
| 04-17/T1 | 04-17/7 | ODDS-01, ODDS-02, ODDS-04, ODDS-08 | T-04-MODULE, T-04-HINDSIGHT | Conectar produtor ao head e mapper | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsResultForOddsProducerIT' test` | No |
| 04-17/T2 | 04-17/7 | ODDS-01, ODDS-02, ODDS-04, ODDS-08 | T-04-MODULE, T-04-HINDSIGHT | Provar corte temporal, correcoes e fronteira | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsResultForOddsTemporalIT,SportsOddsBoundaryArchitectureTest' test` | No |
| 04-04/T1 | 04-04/8 | ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08 | T-04-CALIBRATION, T-04-TAXONOMY, T-04-VOLUME, T-04-REWRITE | Tracar kernel e calculo persistido ponta a ponta | `powershell -NoProfile -Command { node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsPricingModelTest,OddsCalibrationFixtureTest' test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCalculationApplicationServiceIT test; exit $LASTEXITCODE }` | No |
| 04-04/T1b | 04-04/8 | ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08 | T-04-CALIBRATION, T-04-TAXONOMY, T-04-VOLUME, T-04-REWRITE | Expandir provas do kernel sem alterar parametros aprovados | `powershell -NoProfile -Command { node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsPricingModelTest,OddsCalibrationFixtureTest' test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCalculationApplicationServiceIT test; exit $LASTEXITCODE }` | No |
| 04-10/T1 | 04-10/9 | ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08 | T-04-TAXONOMY, T-04-VOLUME, T-04-REWRITE, T-04-CALIBRATION | Reproduzir correcao confirmada sem reescrever o preco | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsReplayCorrectionTest,OddsPricingVolumeIndependenceTest,OddsResultTaxonomyTest' test` | No |
| 04-10/T2 | 04-10/9 | ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08 | T-04-TAXONOMY, T-04-VOLUME, T-04-REWRITE, T-04-CALIBRATION | Provar exclusoes e parametros com mutacoes restauraveis | `powershell -NoProfile -Command { node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsReplayCorrectionTest,OddsPricingVolumeIndependenceTest,OddsResultTaxonomyTest' test; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node --test scripts/validate-odds-negative-mutations.test.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/validate-odds-negative-mutations.mjs; exit $LASTEXITCODE }` | No |
| 04-19/T1a | 04-19/10 | ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13 | T-04-ROSTER-RACE, T-04-SNAPSHOT | Preparar candidato de binding e atomicidade | `node --test scripts/validate-odds-roster-binding.test.mjs` | No |
| 04-19/T1 | 04-19/10 | ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13 | T-04-ROSTER-RACE, T-04-SNAPSHOT | Aprovar binding, freeze e correcao de roster | `node scripts/validate-odds-roster-binding.mjs --require-approval` | BLOCKING |
| 04-19/T2 | 04-19/10 | ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13 | T-04-ROSTER-RACE, T-04-SNAPSHOT | Implementar binding e elegibilidade atomicos | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsEventRosterBindingIT,FlywayMigrationIT,FlywayChecksumIT,SportsOddsBoundaryArchitectureTest' test` | No |
| 04-19/T3 | 04-19/10 | ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13 | T-04-ROSTER-RACE, T-04-SNAPSHOT | Provar concorrencia da porta de roster e correcao | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsRosterBindingConcurrencyIT,SportsEventRosterBindingIT,SportsRosterIT,SportsRosterConcurrencyIT,SportsTabularImportIT,SportsOddsBoundaryArchitectureTest' test` | No |
| 04-05/T1 | 04-05/11 | ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13 | T-04-SCOPE, T-04-PII, T-04-RACE | Conectar criacao, publicacao e lifecycle do mercado | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsMarketIT,OddsMarketLifecycleIT' test` | No |
| 04-05/T2 | 04-05/11 | ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13 | T-04-SCOPE, T-04-PII, T-04-RACE | Publicar consulta, historico e explicacao segura | `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsPublicViewIT,OddsPublicPrivacyIT' test` | No |
| 04-06/T1 | 04-06/12 | ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-RACE, T-04-SELF-APPROVAL, T-04-REWRITE | Aprovar evidencia de correcao antes do override | `node -e "console.log('override authority approval required before implementation')"` | BLOCKING |
| 04-06/T2 | 04-06/12 | ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-RACE, T-04-SELF-APPROVAL, T-04-REWRITE | Implementar override maker-checker, auditoria e concorrencia | `powershell -NoProfile -Command { node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsOverrideIT,OddsOverrideConcurrencyIT,OddsOverrideSecurityIT,AuditEventIT,AuditImmutabilityIT' test; exit $LASTEXITCODE }` | No |
| 04-07/T1 | 04-07/13 | ODDS-01, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-PII, T-04-SUPPLY-CHAIN | Montar candidato OpenAPI e inventario de DTOs allowlisted | `powershell -NoProfile -Command { node frontend/scripts/generate-api.mjs --candidate frontend/target/api-candidate/04-07; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node --test frontend/scripts/generate-api.test.mjs; exit $LASTEXITCODE }` | No |
| 04-07/T2 | 04-07/13 | ODDS-01, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-PII, T-04-SUPPLY-CHAIN | Aprovar digest literal do contrato de odds | `node -e "const fs=require('fs');const p='frontend/target/api-candidate/04-07/';for(const n of ['inventory.json','digest.sha256','governed-before.sha256','governed-after.sha256'])if(!fs.existsSync(p+n))process.exit(1);if(fs.readFileSync(p+'governed-before.sha256','utf8').trim()!==fs.readFileSync(p+'governed-after.sha256','utf8').trim())process.exit(2)"` | BLOCKING |
| 04-11/T1 | 04-11/14 | ODDS-01, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-PII, T-04-SUPPLY-CHAIN | Gerar cliente a partir do digest literalmente aprovado | `powershell -NoProfile -Command { node frontend/scripts/generate-api.mjs --check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node --test frontend/scripts/generate-api.test.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts; exit $LASTEXITCODE }` | No |
| 04-11/T2 | 04-11/14 | ODDS-01, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-PII, T-04-SUPPLY-CHAIN | Verificar reproducibilidade e fronteira do cliente | `powershell -NoProfile -Command { node frontend/scripts/generate-api.mjs --check; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node --test frontend/scripts/generate-api.test.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts; exit $LASTEXITCODE }` | No |
| 04-13/T1 | 04-13/15 | ODDS-01, ODDS-07, ODDS-08, ODDS-09 | T-04-PII, T-04-SCOPE | Exibir mercado publico pelo cliente gerado | `npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts --include src/app/app.routes.spec.ts --include src/app/features/odds/public-market/public-market-page.component.spec.ts` | No |
| 04-13/T2 | 04-13/15 | ODDS-01, ODDS-07, ODDS-08, ODDS-09 | T-04-PII, T-04-SCOPE | Provar transparencia, privacidade e acesso responsivo | `npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts --include src/app/app.routes.spec.ts --include src/app/features/odds/public-market/public-market-page.component.spec.ts` | No |
| 04-14/T1 | 04-14/16 | ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-SELF-APPROVAL, T-04-PII | Conectar governanca de odds ao Console | `npm --prefix frontend run test -- --watch=false --include src/app/app.routes.spec.ts --include src/app/features/odds/public-market/public-market-page.component.spec.ts --include src/app/features/odds/operations/odds-operations-page.component.spec.ts --include src/app/features/operations/operations-api.adapter.spec.ts --include src/app/layout/console-shell.component.spec.ts` | No |
| 04-14/T2 | 04-14/16 | ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-SCOPE, T-04-SELF-APPROVAL, T-04-PII | Cobrir jornadas publicas e maker-checker nos browsers | `powershell -NoProfile -Command { npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts --include src/app/app.routes.spec.ts --include src/app/features/odds/public-market/public-market-page.component.spec.ts --include src/app/features/odds/operations/odds-operations-page.component.spec.ts --include src/app/features/operations/operations-api.adapter.spec.ts --include src/app/layout/console-shell.component.spec.ts; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; npm --prefix frontend exec playwright test -- --config playwright.odds.config.ts --project=phase4-chromium --project=phase4-firefox; exit $LASTEXITCODE }` | No |
| 04-08/T1 | 04-08/17 | ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-CALIBRATION, T-04-MODULE, T-04-TAXONOMY, T-04-SCOPE, T-04-RACE, T-04-REWRITE, T-04-SELF-APPROVAL, T-04-PII, T-04-VOLUME, T-04-LAUFU, T-04-SUPPLY-CHAIN | Executar certificacao focal e registrar matriz de evidencia | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCertificationIT test` | No |
| 04-08/T2 | 04-08/17 | ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-CALIBRATION, T-04-MODULE, T-04-TAXONOMY, T-04-SCOPE, T-04-RACE, T-04-REWRITE, T-04-SELF-APPROVAL, T-04-PII, T-04-VOLUME, T-04-LAUFU, T-04-SUPPLY-CHAIN | Provar invariantes por mutacao e congelar snapshot tecnico | `powershell -NoProfile -Command { node --test scripts/validate-odds-negative-mutations.test.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; node scripts/validate-odds-negative-mutations.mjs; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }; git diff --check; exit $LASTEXITCODE }` | No |
| 04-08/T3 | 04-08/17 | ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13 | T-04-CALIBRATION, T-04-MODULE, T-04-TAXONOMY, T-04-SCOPE, T-04-RACE, T-04-REWRITE, T-04-SELF-APPROVAL, T-04-PII, T-04-VOLUME, T-04-LAUFU, T-04-SUPPLY-CHAIN | UAT humano de transparencia e governanca | `git diff --check` | BLOCKING |

Os 46 comandos da matriz de tasks sao prescricoes de fase; as duas execucoes
focais do validador desta retomada estao registradas abaixo.
Os 9 checkpoints exigem resposta humana literal ligada ao token de checkpoint do candidato
e aos SHA-256 revisados; parser, leitura JSON, exit code ou lembrete impresso nao concedem aprovacao.
Comando/ambiente ausente e SKIP bloqueante. Threat refs sao mapeadas por plano;
as tasks definem suas provas discriminantes.

## Required Discriminating Proofs

- Altering approved K/recency/regression/Davidson/confidence/margin/rounding
  makes a named golden or model test fail.
- Numeric W.O., abandonment and cancellation never become valid score input;
  adding betting volume, liquidity or exposure cannot change output.
- Replay as-of uses admissible immutable history at sport_time/known_at, not today's head or MAX(version_no); CONFIRMADO and CORRIGIDO qualify when valid. Sporting/admin cross-kind correction never changes prior reproduced prices.
- Sporting payload references exact V16 scores without duplication; administrative payload contains no score; single head and legacy compatibility cannot form independent authorities.
- Future pre-game eligibility does not require its own outcome/confirmed result. Real producer, governance and roster ports are integrated before opening; race with correctRoster/source correction is proven in 04-05.
- Navigation capabilities never authorize backend commands; sports commands remain in sports and never call odds.internal.
- Database rejects UPDATE/DELETE of calculation, publication, lifecycle,
  override and audit facts; duplicate commands are idempotent and races leave
  no partial row.
- Persisted competition scope, distinct actors, CLOSED/LOCKED and stale
  versions reject unauthorized commands with 403/409 and no write.
- Public DTOs/DOM/storage omit actor, evidence, PII, tokens and financial
  objects; generated client drift and manual output edits fail.
- Each mutation runs in an isolated temporary copy, restores SHA-256 in
  `finally`, and verifies the original checkout is unchanged.

## Human Checkpoints

| Gate | Owner decision |
|---|---|
| 04-01/T2 | Aprovar calibracao e evidencias antes de qualquer preco; token humano e SHA-256 revisados |
| 04-15/T2 | Aprovar autoridade e politicas de source |
| 04-02/T2 | Aprovar fronteira, taxonomia, estados e autoridade |
| 04-03/T1 | Aprovar schema de linhagem e migration forward-only |
| 04-18/T1 | Aprovar matriz de capabilities e acoes auditaveis |
| 04-19/T1 | Aprovar binding, freeze e correcao de roster |
| 04-06/T1 | Aprovar evidencia de correcao antes do override |
| 04-07/T2 | Aprovar digest literal do contrato de odds |
| 04-08/T3 | UAT humano de transparencia e governanca |

Gates externos ao DAG: reconciliacao de STATE/worktree/HEAD antes de implementar;
UI-SPEC aprovado antes de 04-13/14; aprovacao de cada teste/ambiente e Git separada.

O modelo fisico de envelope/payload/head, FK condicionais, backfill/legado,
entrada administrativa, temporalidade/regulamento, propagacao/recuperacao e
captura/freeze/correcao/atomicidade de roster continuam candidatos. A escolha
A (fatos administrativos separados com head unico) e a fronteira publica sports
ja foram escolhidas; isso nao ratifica automaticamente os detalhes fisicos.

Marcadores VNN_MARKETS, VNN_GOV, VNN_SOURCE e VNN_ROSTER sao simbolicos:
BLOCKED_REQUIRES_ALLOWLIST_RATIFICATION. Antes de criar migrations, ratificar
filenames literais, inventario vivo e estrategia faseada de constraints nos gates
04-15/03/18/19; atualizar os manifests em bloco documental aprovado. 04-09 nao
pode criar FK para tabela futura de 04-16/19. V16/V24 e scores legados nao mudam.
Os 25 arquivos de migration vivos incluem V5_1; numeracao nao equivale a contagem.

O inventario generated de 04-11 permanece bloqueado ate 04-07 ratificar todos
os paths de odds, operational-capability e workflow administrativo sports.
Arquivo extra exige allowlist literal aprovada antes de gerar; sem wildcard.
Nomes de DTOs/rotas administrativas nao sao inventados como aprovados.

## Preservation and Ownership

| Responsibility | Current ownership |
|---|---|
| Calibration and model | 04-01 evidence/gate; 04-12 independent golden; 04-04 kernel with real producer; 04-10 replay/mutations |
| Sports public boundary | 04-02, preceded by source candidate 04-15 |
| Candidate physical schema | 04-03, without runtime or DDL |
| Odds storage | 04-09, without sports-source implementation |
| Source authority and compatibility | 04-16; V16 scores referenced, never duplicated; sporting/admin correction and legacy readers/writers |
| Real source producer | 04-17; read-model internal to sports, as-of history and pre-game projection |
| Governance foundation | 04-18; menu capabilities distinct from command authorization, closed audit vocabulary |
| Roster binding/freeze | 04-19 candidate/gate/port/atomicity; real opening vs correction proof in 04-05 |
| Market and override | 04-05 consumes source/governance/roster; 04-06 consumes governance instead of creating it |
| Contract/client/UI | 04-07 expanded candidate/digest; 04-11 generated client; 04-13 public; 04-14 operations/menu/browser |
| Final certification | 04-08 repeats all focal commands and aggregate gates after every predecessor |

### Serialized shared paths

| Path | Owners in dependency order |
|---|---|
| `backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java` | 04-09 -> 04-18 -> 04-16 -> 04-19 |
| `backend/src/test/java/br/com/betufu/platform/persistence/FlywayChecksumIT.java` | 04-09 -> 04-18 -> 04-16 -> 04-19 |
| `scripts/validate-odds-negative-mutations.mjs` | 04-10 -> 04-08 |
| `scripts/validate-odds-negative-mutations.test.mjs` | 04-10 -> 04-08 |
| `frontend/scripts/generate-api.mjs` | 04-07 -> 04-11 |
| `frontend/scripts/generate-api.test.mjs` | 04-07 -> 04-11 |
| `frontend/src/app/app.routes.ts` | 04-13 -> 04-14 |
| `frontend/src/app/app.routes.spec.ts` | 04-13 -> 04-14 |
| `frontend/e2e/odds-pilot.spec.ts` | 04-14 -> 04-08 |

Cada plano propoe menos de 15 paths; 04-18 e 04-19 possuem quatro tasks devido
ao candidato nao-runtime seguido de checkpoint e implementacao/prova. Nao
compactar esses gates numa task que escreva runtime antes da resposta humana.
O total de 168 paths e inventario candidato, nao certificacao arquitetural
nem allowlist de execucao aprovada.

## Final Verification Contract

After all dependencies and approvals:

- Run every focal command in the map independently and record exact counts.
- Run architecture/privacy, generator, Angular, Playwright and both aggregate
  suites required by the phase.
- Run `git diff --check` plus whitespace inspection for untracked plan files.
- Freeze branch, HEAD, base, status, changed paths and SHA-256 hashes before UAT.
- Recompute OpenAPI candidate/digest in the consumer worktree; target artifacts
  are not assumed portable through Git.
- Do not execute `scripts/extract-laufu.mjs`; LAUFU remains sample-only.

## Validation Sign-Off

- [x] 19 PLANs, 46 tasks and all wave dependencies are reconciled.
- [x] Every task has a literal verify; nine human checkpoints are blocking.
- [x] No task uses `--auto`, `--all`, `--force` or watch mode.
- [x] Every shared path has serialized ownership through dependencies; each plan has fewer than 15 paths.
- [x] Candidate OpenAPI digest and generated outputs have an explicit recomputation gate.
- [x] Golden fixtures are independent of the future kernel and are consumed only later.
- [x] Aggregate backend, Node, Angular and browser gates remain final-wave responsibilities.
- [ ] `nyquist_compliant: true` is set only after all gates pass.

**Approval:** documentary snapshot approved by the responsible on 2026-09-05.
Future gates remain blocking: calibration, detailed source/physical schema, governance,
roster, override criterion, OpenAPI/generated inventory, UI-SPEC and final UAT.
Product execution remains blocked until each applicable gate is approved.

**Session note:** Planning only. Tests executed: `0`; Git stage/commit/push: `0`.

## Documentary Check - 2026-09-05

Este resultado e somente do bloco documental; nao certifica comportamento de produto.

- 19/19 PLANs com delimitadores de frontmatter e secoes obrigatorias.
- 46/46 tasks com name/files/read_first/action/acceptance_criteria/verify/done.
- 46/46 comandos automated, 81/81 comandos das secoes verification e 46/46
  comandos da matriz passaram Parser.ParseInput do PowerShell, com zero erros.
  Os comandos foram interpretados como texto, nunca executados.
- 63 dependencias internas sem ciclo ou predecessor em onda igual/posterior;
  9 paths compartilhados serializados por dependencias; 168 paths candidatos.
- Task files e artifacts coincidem com as allowlists; zero paths excedentes.
- Git diff --check e inspecao de whitespace dos documentos nao rastreados:
  zero erros. Index/stage e arquivos rastreados sem alteracao.
- No bloco documental original, SHA-256 de 10 arquivos protegidos permaneceu
  identico ao inicio. Naquele momento, a reconciliacao atualizava somente os tres
  documentos explicitamente autorizados e os artefatos do T1 estavam ausentes:
  AGENTS, STATE, ROADMAP, config, PROJECT, REQUIREMENTS, CONTEXT, DISCUSSION-LOG,
  RESEARCH e scripts/extract-laufu.mjs.
- Revisor independente somente leitura confirmou estrutura/ownership e examinou
  invariantes centrais. Parecer PARCIAL: nao concluiu leitura integral nem auditoria
  semantica de todos comandos. Um warning de key_link do 04-17 foi corrigido para
  SportsOddsSourceRepository; nenhum novo blocker foi confirmado nessa cobertura.
- Testes de produto: 0. Calibracao/backtest: 0. Git de escrita: 0.

As validacoes acima nao substituem parser YAML completo, execucao Java/SQL,
provas de seguranca, aprovacao arquitetural ou checkpoints humanos.
nyquist_compliant e wave_0_complete permanecem false. Este snapshot documental foi
aprovado; nenhum proximo plano esta automaticamente autorizado.

### T1 Artifact Validation - 2026-09-06

Este resultado registra a validacao focal do Plano 04-01; nao certifica comportamento de produto nem aprova calibracao.

- `node scripts/validate-odds-calibration.mjs --check`: segunda execucao exit `0`, `20/20` verificacoes, `0` erros. A primeira execucao exit `1` foi corrigida no proprio validador por uma regra excessiva de motivo por registro; os JSONs e fixtures permaneceram inalterados.
- Captura verificada: 671 partidas, 329 candidatas, 0 elegiveis, 20 identidades em quarentena, 46 W.O. numericos excluidos e 7 fixtures inelegiveis.
- Backtest: `not_run`; treino, avaliacao e metricas permanecem nulos. Aprovacao: `pending_human_checkpoint_04-01-T2`.
- Nenhum teste de produto, backtest, extrator LAUFU, stage, commit ou push foi executado neste bloco.
- O validador novo permanece untracked ate aprovacao documental e Git separada. `STATE.md` e o snapshot desta validacao agora apontam para o HEAD atual `6a0b1116197385439d68d30a9afa94291174b968`.

### 04-01 Gate Reconciliation - 2026-09-06

Este bloco registra a correcao mecanica e a segunda verificacao focal; nao aprova
calibracao, parametros ou comportamento de produto.

- O T2 `node scripts/validate-odds-calibration.mjs --check --require-approval`
  retornou exit `1`, com `3` verificacoes concluidas e `1` erro: a captura
  exploratoria LAUFU e explicitamente inelegivel para calibracao. A falha fechada
  ocorre antes de qualquer aprovacao ou precificacao.
- O contrato v2 separa `scope.sourceAuthority`, `scope.calibrationEligible` e
  `scope.productionEligible`. Para a captura LAUFU, o T2 exige fonte
  exploratoria, `productionEligible=false` e
  `parameters.D-120.laufuIsNotProductionAuthority=true`; uma futura evidencia
  aprovada precisara de `calibrationEligible=true`. A separacao foi confirmada,
  mas nenhum parametro estatistico foi aprovado por este bloco.
- `approval.digest` e mantido por compatibilidade, mas sua semantica foi confirmada:
  e um token hexadecimal de checkpoint humano (`kind=human_checkpoint_token`),
  restrito ao control plane, nao uma credencial nem hash canonico. O SHA-256 da
  captura e o controle criptografico; hashes dos artefatos revisados devem ser
  registrados no checkpoint humano. O validador nao concede producao e nao
  desbloqueia a captura LAUFU.
- O T2 agora valida as invariantes compartilhadas do bundle sem reexecutar as
  assercoes especificas do T1, fixa os IDs das sete fixtures, rejeita caminhos
  fora da allowlist/`scripts/data/*.json` versionada, confere a semantica e o
  SHA-256 individual das fixtures, reconcilia o `recordId` e os metadados da
  proveniencia real com `raw.partidas` e exige `productionEligible=false` para
  qualquer gate de calibracao. A captura atual
  e rejeitada por fingerprint; o vocabulario de autoridade futura permanece
  aberto ao gate 04-15 e este snapshot so declara `exploratory`.
- Hashes/bytes revisados no checkpoint documental: manifesto `4531` /
  `9FBC39F37A873E6EDFED0406A875221F3E8426EAA9C59DC8347C43A7E210C6F1`;
  relatorio `3415` / `85FB0BCE58DA67773FACE0B7D17B34E0E9DBD833408AD40C334DDB90FF8A883C`;
  fixtures `2922` / `8D6F92DF2413E89930F644FE4CAA612AC90E9FB82275042E99DEB4C3BECEB298`;
  quarentena `5779` / `3FA2C9ED28CB558A3DB0D490EC40DA2DDB9998D6C502BC5AE8759487A08E3251`;
  captura bruta `987642` / `A4593833F5C9E6060DC26956DC6782F077EDD047546DAF1A44911AFEE023AB92`.
  Esses hashes vinculam a revisao documental; o token humano nao os substitui.
- `git diff --check` retornou exit `0`; os unicos avisos foram conversoes normais
  LF/CRLF do checkout. Nenhum teste de produto, backtest, extrator, stage, commit
  ou push foi executado.
- Hash SHA-256 normalizado do validador vivo: `CA6BF9F5EC530FD623DFE1A16D9219F5AD2B392A05FE59283F6CD4D54B006058`.
- T2 e os demais oito checkpoints humanos continuam bloqueantes; nenhum plano
  posterior ou motor de preco esta liberado.

### 04-01 Correction Follow-up - 2026-09-06

- O validador passou a exigir um numero finito dentro dos objetos ainda abertos
  de Davidson/confianca e a cruzar `fixture.source.recordId` e os metadados da
  fixture real com a linha de origem declarada em `raw.partidas`, sem alterar os
  JSONs de evidencia.
- `node --check`: exit `0`; parse: `4/4` JSONs validos; T1: exit `0`, `20/20`
  verificacoes, `0` erros; T2: exit `1`, `3` verificacoes concluidas, `1` erro
  esperado pelo bloqueio da captura exploratoria LAUFU; `git diff --check`:
  exit `0` (somente avisos normais de LF/CRLF).
- SHA-256 normalizado do validador apos a correcao:
  `CA6BF9F5EC530FD623DFE1A16D9219F5AD2B392A05FE59283F6CD4D54B006058`.

### 04-15 T1 Artifact Validation - 2026-09-06 (historical pre-approval snapshot)

Este resultado certifica somente o candidato documental de source; nao aprova
autoridade, schema, governanca, roster ou runtime.

- Worktree/branch historica: `worktree-agent-04-15`, base/HEAD do snapshot pre-aprovacao `0963c965e8fced9af426727cecf9a0ed9306dbe0`.
- `node --test scripts/validate-sports-odds-source-candidate.test.mjs`: exit `0`,
  `28/28` testes, `0` falhas (snapshot anterior a correcoes e ao T2).
- `node scripts/validate-sports-odds-source-candidate.mjs --check`: exit `0`,
  `416` verificacoes, status `pending_human_checkpoint_04-15-T2`.
- `node scripts/validate-sports-odds-source-candidate.mjs --require-approval`:
  exit `1` esperado, regra `APPROVAL_PENDING`; nenhum token humano havia sido
  criado nesse snapshot.
- Paths alterados: os tres artefatos T1 e os dois documentos de preflight;
  nenhuma migration, Java, OpenAPI, cliente, UI, dependencia ou dado externo.
- SHA-256 bruto dos artefatos: candidato
  `5B34B26285A8F1BDCA92FCE3C23888E87E91D0E7C38868348CA075C6F43BFF12`;
  validador `527A6A410939379B6530B43F8FFF14C54FFECD09884BA7D342C07FF5792B3481`;
  teste `4FEC584E5C66C027A4F5D8B8E6619E5269D43987E79929DE3B04E01DEB5EE3C6`.
- `git diff --check`: exit `0`; nenhum stage, commit, merge, push ou cleanup foi
  executado nesse snapshot historico. O resultado nao certifica o candidato
  corrente.

### 04-15 T2 Approval and final artifact validation - 2026-09-07 (revalidated after checkout line-ending fixes)

Este resultado certifica o candidato corrente e fecha o checkpoint de source;
nao aprova schema, governanca, roster, runtime ou calibracao 04-01.

- Worktree/branch: `worktree-agent-04-15`, base `0963c965e8fced9af426727cecf9a0ed9306dbe0`, HEAD tecnico `ee611bec0bf2d4fc56a79df4ba27e8e9f06a944b`.
- Resposta humana registrada: `04-15/T2@2026-09-07: owner message 'aprovar source 14-15' normalized to plan 04-15`.
- `approvedBy`: `LopesGiovani`; `approvedAt`: `2026-09-07T00:41:36.751Z`.
- Digest canonico do snapshot pre-aprovacao: `1E7DB9D05625CD36CBE083144B95E2429C2523909155498C2CF25336CC17B867`.
- `node --check` dos dois modulos: exit `0` em ambos.
- `node --test scripts/validate-sports-odds-source-candidate.test.mjs`: exit `0`,
  `36/36` testes, `0` falhas, `0` erros, `0` skips.
- `node scripts/validate-sports-odds-source-candidate.mjs --check`: exit `0`,
  `414` verificacoes, `T2=approved`.
- `node scripts/validate-sports-odds-source-candidate.mjs --require-approval`:
  exit `0`, `414` verificacoes, `T2=approved`.
- Suites agregadas: Node `674/674`; Maven Surefire `282/282` + Failsafe
  `454/454` = `736/736` testes distintos, `BUILD SUCCESS`; o wrapper imprimiu
  `1472` como soma bruta de marcadores repetidos. Nenhum skip ou falha foi
  observado.
- Hashes SHA-256 normalizados (LF) dos tres artefatos no snapshot corrente apos as
  correcoes de portabilidade:
  candidato `6D0B8E183D6B8D9AD646C5119BE684BC124C17BD37037A029E6283643D2BD40C`;
  validador `DA0031058F674653E8AC9C202817F1B792D5AC888FC4FBEDF753D02688FF5F2C`;
  testes `5A9BDD41BFA0F0F4B4A7378B0A94682A11D1F9C48F2A2D8B1E567D561FE0921B`.
  O snapshot final tambem registra bytes brutos, `git status -uall` e paths.
- A aprovacao cobre invariantes de autoridade, taxonomia e temporalidade. O
  detalhamento fisico (envelope, constraints, roster, recuperacao e nomes de
  migrations) permanece pendente nos gates 04-03, 04-19, 04-18 e 04-16.
- A calibracao 04-01 continua fail-closed: a captura exploratoria LAUFU nao e
  autoridade de producao e `wave_0_complete` permanece `false`.
- O commit tecnico inicial que contem os tres artefatos T1 e
  `0c486a1a2e288a83b19edcaeb83ef01674a0f4a6`; as correcoes de portabilidade
  foram registradas em `e162a173393fe365006eb831ecede0119a03ae2d` (separador)
  e `ee611bec0bf2d4fc56a79df4ba27e8e9f06a944b` (terminador de fixture). A
  formalizacao documental segue em commit separado.

### 04-15 Post-merge line-ending recovery - 2026-09-07

Esta secao registra uma falha mecanica descoberta no checkout integrado e sua
recuperacao; ela nao altera a regra de validacao de producao.

- A primeira validacao na `master` em `08dadc1` retornou `35/36`; o caso
  `digest rejeita whitespace extra apos o JSON` recebeu `CANDIDATE_LINE_ENDINGS`
  porque a fixture anexava `\n` a um arquivo CRLF.
- `core.autocrlf=true` foi confirmado na `master`; a worktree de execucao usava
  LF. O validador rejeitar finais mistos e comportamento correto e fail-closed;
  o defeito estava somente na fixture do teste.
- A fixture passou a anexar o terminador detectado no proprio source. O ajuste
  esta em `ee611bec0bf2d4fc56a79df4ba27e8e9f06a944b`; a repeticao na worktree LF
  e a validacao equivalente no checkout CRLF do snapshot tecnico passaram
  `36/36`. A prova de finais de linha fica fechada para a integracao.

### 04-15 Aggregate verification recovery - 2026-09-07

Esta secao reconcilia a tentativa Maven que falhou por ambiente ausente com a
reexecucao valida; nao altera o candidato nem libera runtime.

- A tentativa anterior iniciou sem
  `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION` e
  `BETUFU_MATRICULA_HMAC_KEY_V1` no processo. O Failsafe encerrou com `454`
  testes, `0` falhas, `67` erros e `0` skips; o bean `matriculaHmac` recebeu o
  placeholder literal `${BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION}`. Log:
  `C:/Users/giova/AppData/Local/Temp/betufu-maven-VP47gZ/maven.log`.
- Probes locais confirmaram a heranca PowerShell -> Node -> `cmd.exe` -> JVM
  quando as variaveis sao definidas no mesmo processo; `scripts/maven.mjs` e
  `mvnw.cmd` nao removem essas variaveis. Nenhum arquivo, dependencia ou
  configuracao foi alterado para resolver a ocorrencia.
- A reexecucao material foi:
  `BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1` e a chave Base64 de teste
  somente no ambiente do processo, seguida de
  `node scripts/maven.mjs -f backend/pom.xml verify`; resultado `BUILD SUCCESS`,
  `1472` marcadores brutos, `0` falhas, `0` erros e `0` skips. A leitura dos
  XMLs confirmou `282` Surefire + `454` Failsafe = `736` testes distintos.
- Uma segunda tentativa sem `clean` falhou por um `.class` incremental antigo
  (`br.com.betufu.sports.AthleteProfileMediaIT`) no `target/test-classes`,
  enquanto o fonte atual pertence a `br.com.betufu.sports.internal.media`.
  `node scripts/maven.mjs -f backend/pom.xml clean verify` removeu somente o
  `backend/target` gerado, recompilou os fontes atuais e terminou com `BUILD
  SUCCESS`; apenas a classe no pacote correto permaneceu.
- A escada Node foi repetida depois da recuperacao: `node --check` `2/2`, focal
  `36/36`, validadores `--check` e `--require-approval` com `414` verificacoes
  cada, e agregado `674/674` em `9` suites.
- Um probe auxiliar com lista `-Dit.test` sem quoting foi rejeitado pelo parser
  do PowerShell; a forma quoted passou. Esse diagnostico nao pertence ao
  `<verify>` do plano e nao e contado como evidencia de produto.

### 04-15 Final post-clean verification - 2026-09-07

- No checkout limpo da worktree, `node --test scripts/*.test.mjs` passou `674/674`
  em `9` suites; o foco T1 permaneceu `36/36` e ambos os modos do validador
  retornaram `414` verificacoes com `T2=approved`.
- `node scripts/maven.mjs -f backend/pom.xml clean verify` passou com
  `BUILD SUCCESS`; os XMLs registraram `282/282` Surefire e `454/454` Failsafe,
  total distinto `736/736`, sem falhas, erros ou skips.
- A exportacao CRLF do snapshot tecnico `ee611bec0bf2d4fc56a79df4ba27e8e9f06a944b`,
  feita com `core.autocrlf=true`, passou o foco `36/36`, sem LF isolado nos tres
  arquivos. A mesma arvore de blobs sera integrada linearmente na `master`.

### Frozen PLAN SHA-256

Worktree/branch: `worktree-agent-04-15` (execucao isolada; publicacao documental em `master`).
HEAD/base tecnico: `ee611bec0bf2d4fc56a79df4ba27e8e9f06a944b` /
`0963c965e8fced9af426727cecf9a0ed9306dbe0`.
Os hashes abaixo cobrem os 19 planos no snapshot corrente. Cada SHA-256 e calculado
sobre os bytes UTF-8 com `CRLF` convertido para `LF` somente em memoria; finais de linha
do checkout nao alteram a identidade do conteudo. O hash desta validacao e calculado
externamente para evitar referencia circular. Qualquer edicao invalida o snapshot.

| PLAN | SHA-256 |
|---|---|
| 04-01-PLAN.md | `72CBA1EB1457C55DA10C7BB4C462D5917F64A1DC1CD8DCA6CA00F0A95BDE6940` |
| 04-02-PLAN.md | `23A86A2E173F2B33F6CFE2BCFC4B81C7256B30435939BDD7D666F03900DD071E` |
| 04-03-PLAN.md | `6D0BC9A74B8C98A2A4BEAC22FF6474C1F28776907B74326C751E6CA93BCC4268` |
| 04-04-PLAN.md | `1BFB21B12C075B17B680DAF1E4CDA3E507F1A90947F535098C370FDFD5DCEC47` |
| 04-05-PLAN.md | `C8A172CADE363923ECA2F07DAFC6A1519F3261EEB3D20ADAA43B843FA92D6945` |
| 04-06-PLAN.md | `70BA41D0D47C39456B1A7980B1F6A5619866C97BBB4268C9EBAEDA35616DBE01` |
| 04-07-PLAN.md | `D119AF0BE696798B4AB8AA86509980126A67D7E7768ED2D2A3E5EC8D30631250` |
| 04-08-PLAN.md | `3F9F065631C5EF26275094273505ED7A15DFFC0B38EC31270AE51C7EA42DB607` |
| 04-09-PLAN.md | `DF09D983D2C1D23A7001FB96D57FBC839819D75D22A4F41C1D9A590C557D3668` |
| 04-10-PLAN.md | `6861D18D754923879C7E389F1920B0AE8D26050E1FD90880D4D4D4757DDADD9A` |
| 04-11-PLAN.md | `D5E8A1F7C0917D85D14646182866465E09BB5F4110C90D8AC378F9602F8A8B6E` |
| 04-12-PLAN.md | `A433067ECF0B104CDDC108D1AA840A0651D5C3E322897C8626EDD9AF0D1180D5` |
| 04-13-PLAN.md | `C4DC11A60F7BF12D84D82083B617A0D0ADA6A14C8CD426E68EC415A352BAE929` |
| 04-14-PLAN.md | `B09A944BEC595B5ABB5E898F838F695F0B859A0ACB4CED93076D3D5D5A479BD5` |
| 04-15-PLAN.md | `58854C5786456D670781280CBF92E78BC4D2DC5803EB7C35EDE4C68E5C2A7094` |
| 04-16-PLAN.md | `B9E0AC844FD1797713AC06000B6EE0CFBBB09F0ED8EA98A3A1E1B79C87C68900` |
| 04-17-PLAN.md | `8734E858B2CC30477EDF759DF7CCF6239755532A47AED419C6A137F921C0EA00` |
| 04-18-PLAN.md | `4868A58DD49E940286CF809DF9C46BC16BD58DDF3A7DD5A0BBDCF44FA185EBB9` |
| 04-19-PLAN.md | `8B51FDB7551D423C82D7AB58E28EFA67DAE93722C8F17210A36ECDCBD1AC4E13` |

## Wave 17 Certification - 2026-09-09

Este bloco registra a evidencia executada na worktree `worktree-agent-04-08`,
branch `worktree-agent-04-08`, base e HEAD pre-Git
`1e190e601e87f3da341412b30e6f71af946dfaa6`. Os resultados abaixo nao
substituem o UAT humano `04-08/T3` e nao promovem automaticamente os gates que
permaneceram bloqueados.

### Matriz ODDS-01 a ODDS-13

| Requisito | Evidencia atual | Resultado |
|---|---|---|
| ODDS-01 | `OddsCertificationIT`: 1/1; mercado H2H e elegibilidade cobertos internamente | PASS |
| ODDS-02 | Pricing/replay/taxonomia/independencia: 44/44; pricing/calibracao: 24/24 | PASS |
| ODDS-03 | Pricing/calibracao: 24/24; replay/correcao incluído no grupo de 44/44 | PASS |
| ODDS-04 | Taxonomia/replay: grupo 44/44; lifecycle/mercado: 16/16 | PASS |
| ODDS-05 | Pricing/calibracao: 24/24 (margem configuravel e arredondamento) | PASS |
| ODDS-06 | Pricing/calibracao: 24/24 (baixa confianca/neutro) | PASS |
| ODDS-07 | Pricing/replay/taxonomia: 44/44 | PASS |
| ODDS-08 | Mercado/lifecycle/public view/privacy/override: 16/16; governanca: 74/74 | PASS |
| ODDS-09 | Public view/privacy/override: 16/16; Node generator: 73/73; Angular focal: 57/57; Playwright: 6/6 | PASS |
| ODDS-10 | Governanca/capabilities/auditoria: 74/74; override: 16/16 | PASS |
| ODDS-11 | Governanca/capabilities/auditoria: 74/74; override: 16/16; mutacoes M_SCOPE/M_PUBLIC_ALLOWLIST: expected-red | PASS |
| ODDS-12 | Override/lifecycle/concurrency: 16/16; mutacoes M_SELF_APPROVAL/M_FINAL_STATE: expected-red | PASS |
| ODDS-13 | Override/concurrency/privacy: 16/16; mutacoes M_IMMUTABILITY/M_GENERATED_CLIENT: expected-red | PASS |

### Comandos e gates

| Comando | Resultado literal | Estado |
|---|---|---|
| `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCertificationIT test` | 1/1 teste JUnit; wrapper reflexivo exige e executa pelo menos 30 metodos determinísticos | PASS |
| `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsReplayCorrectionTest,OddsPricingVolumeIndependenceTest,OddsResultTaxonomyTest' test` | 44/44 | PASS |
| `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCalibrationFixtureTest test` | 24/24 | PASS |
| `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsMarketIT,OddsMarketLifecycleIT,OddsPublicViewIT,OddsPublicPrivacyIT,OddsOverrideIT,OddsOverrideConcurrencyIT,OddsOverrideSecurityIT' test` | 16/16 | PASS |
| `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsOperationalCapabilitiesIT,OddsAuthorizationIT,OddsAuditVocabularyIT,AuditImmutabilityIT,AuditEventIT,OperationalCapabilitiesIT,OperationalCapabilitiesResponseTest' test` | 74/74, com HMAC de teste definido somente no processo | PASS |
| `node scripts/maven.mjs -f backend/pom.xml '-Dtest=OddsMigrationIT,FlywayMigrationIT,FlywayChecksumIT,AuthoritativeStorageIT' test` | `OddsMigrationIT` 1 falha em 2 (esperava 27 migrations, encontrou 29); `FlywayMigrationIT` 18/18; `FlywayChecksumIT` 1/1; `AuthoritativeStorageIT` 4/4 | FAIL/BLOCKED |
| `node frontend/scripts/generate-api.mjs --check` | `ENGINE_MISSING`: JAR OpenAPI aprovado ausente na worktree | SKIP/BLOCKED |
| `node --test frontend/scripts/generate-api.test.mjs` | 73/73 | PASS |
| Angular focal (contrato, rotas, pagina publica e operacional) | 57/57 | PASS |
| Angular agregado | 364/364 na execucao validada da Wave 16; nao reexecutado nesta worktree de certificacao | NOT RUN (historical evidence only) |
| Playwright `phase4-chromium` + `phase4-firefox` (desktop/320px) | 6/6 | PASS |
| `node --test scripts/validate-odds-negative-mutations.test.mjs` | 21/21 | PASS |
| `node scripts/validate-odds-negative-mutations.mjs` | 11/11 mutacoes expected-red; SHA-256 restaurado em cada caso | PASS |
| `node scripts/validate-angular-route-boundary.mjs` | OK | PASS |
| `node scripts/maven.mjs -f backend/pom.xml verify` | Nao executado nesta worktree de certificacao | NOT RUN/BLOCKED |
| `node --test scripts/*.test.mjs` | Nao executado nesta worktree de certificacao | NOT RUN/BLOCKED |
| `git diff --check` | exit 0 | PASS |

As falhas de migration e do engine ausente sao bloqueios de certificacao, nao
foram corrigidas por alteracao fora da allowlist e nao autorizam editar
migrations aplicadas ou instalar dependencias.

### Mutacoes negativas

As 11 mutacoes (`M_WO_NUMERIC`, `M_VOLUME`, `M_K_FACTOR`, `M_REGRESSION`,
`M_ROUNDING`, `M_SCOPE`, `M_SELF_APPROVAL`, `M_FINAL_STATE`, `M_IMMUTABILITY`,
`M_PUBLIC_ALLOWLIST`, `M_GENERATED_CLIENT`) produziram o sinal vermelho
esperado e restauraram o SHA-256 anterior antes do caso seguinte. A mutacao de
estado final foi deliberadamente ancorada em `requireProposalAllowed`, o ponto
exercitado por `OddsOverrideIT`; a mutacao de imutabilidade usa
`OddsPersistenceIT`, que verifica os triggers reais.

### Human checkpoint

`04-08/T3` permanece `BLOCKING`: o responsavel deve revisar a matriz, os hashes,
as jornadas publicas/operacionais em 320px e desktop, a ausencia de PII e as
proibicoes de creditos, apostas, settlement, liquidez, H2H positivo e LAUFU de
producao. Sinal esperado: `aprovar snapshot final da Fase 4` ou divergencia
especifica.
