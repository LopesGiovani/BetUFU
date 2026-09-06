---
phase: 4
slug: mercados-pr-jogo-e-odds-transparentes
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-04
---

# Phase 4 - Validation Strategy

> Contrato de validacao para mercados pre-jogo e odds transparentes. Este
> arquivo foi reconciliado com 19 planos e 46 tasks; os comandos abaixo sao
> propostas futuras e nenhum foi executado nesta sessao.

## Scope and Authority

- Worktree de planejamento: `C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-04-plan`.
- HEAD/base confirmado: `720df5fc84474fa88356caffa0b422a18c857dc5`; RepoWise
  `indexed_commit=720df5fc8447`, `live_head=720df5fc8447`,
  `index_behind=false`; nao executar `repowise update`.
- `STATE.md` conserva `state_head: bedc623fb1a1`; ele nao e alterado aqui.
  Antes de implementar, uma worktree/branch de execucao, HEAD vivo e STATE
  reconciliado exigem briefing e aprovacao proprios.
- A fase continua sem UI-SPEC aprovado: 04-13 e 04-14 permanecem bloqueados
  ate esse gate, sem criar o documento nesta allowlist.
- Nenhum teste de produto, suite agregada, navegador, Maven, npm, Playwright,
  LAUFU ou Git de escrita foi executado nesta sessao.

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

Todos os 46 comandos sao prescricoes futuras, nao resultados executados.
Os 9 checkpoints exigem resposta humana literal ligada ao digest do candidato;
parser, leitura JSON, exit code ou lembrete impresso nao concedem aprovacao.
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
| 04-01/T2 | Aprovar calibracao e evidencias antes de qualquer preco |
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
- SHA-256 de 10 arquivos protegidos permaneceu identico ao inicio do bloco documental
  original; a reconciliacao corrente atualiza somente os documentos explicitamente
  autorizados neste fechamento:
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

### Frozen PLAN SHA-256

Worktree/branch: master (publicacao documental; execucao futura exige worktree neutra).
HEAD/base: 0ac965d6a1abbb644e0b19239ef3c5213d2c495e / 720df5fc84474fa88356caffa0b422a18c857dc5.
Os hashes abaixo cobrem os 19 planos no snapshot corrente; o hash desta validacao e calculado
externamente para evitar referencia circular. Qualquer edicao invalida o snapshot.

| PLAN | SHA-256 |
|---|---|
| 04-01-PLAN.md | `00B43BFC227ACDCCD683C92B0297F3D4DC46C2E5B084FFEA03F212F9ED15B9B2` |
| 04-02-PLAN.md | `5DB4EDC4A65A24C5246ED5834C840E3591667E7709C894F98F76D893A9322281` |
| 04-03-PLAN.md | `FBB1F9CF6B2B5C381CF7E515F67332358934A31CDACD3089CFB500B391A5F563` |
| 04-04-PLAN.md | `C86DAB6FE6BF5D7A87FD95BEDE3B1FA5EDB937FB48CBFC9F0FEA61FF6FCD1E22` |
| 04-05-PLAN.md | `1B364F1A2F815C4A784F086E27AE044A2657DA49BD42E8139688AC54D353339F` |
| 04-06-PLAN.md | `A47D9C2E9D3501DA42224D930F957ADEDA4CA8787CD00AF038B9FA780079E4E5` |
| 04-07-PLAN.md | `64E4799E03DF997FBDC3896DEAF284632BDF25F8C83FE217FB4E4FFFD54B5233` |
| 04-08-PLAN.md | `90CA200F483AAE0BD0A1B4CA2281F65C63E562BEAF43D2D2C22A8841DB0566CB` |
| 04-09-PLAN.md | `E8134C1C67A69607F2EBEA66B898B427651FADD181EA2D389D29BFE3F70AD66B` |
| 04-10-PLAN.md | `E0FB3510D5736E0593222AEF70EB125F41D3C445BBFE770A19B8CC7E86213D38` |
| 04-11-PLAN.md | `F4B4DF55EA76E3428520BE4C7C08529ECA7038993357B1891C2ED7DFEB393D54` |
| 04-12-PLAN.md | `4924E3021D3D931B254E47397058929245EB3287CB912C9154FD47FC6A05A77E` |
| 04-13-PLAN.md | `BE5ADA3A8280D6F38D4A99502CFF99815B2062C0B9906C5DC210761B20D1DF93` |
| 04-14-PLAN.md | `5FA4BBCA67AB4C939FF99EB78EE955194797EF5D228AD8DB8D2CF6CB620CC7F5` |
| 04-15-PLAN.md | `96046F5A79F017990E8E2AAD0964FEB059AB99D1523D414F76FC0B9AB681DFB2` |
| 04-16-PLAN.md | `30AF4FEBB6C13701DB801A77324760CB6EB87E7D95B310795F042645D7BA2817` |
| 04-17-PLAN.md | `7E806E0FCAFB8A4B8E416BE03AFE1329F8DF8AA39D693DDD0AA616A0520855D5` |
| 04-18-PLAN.md | `6E434C8764B3127B7071F614E5393323D6F82E92D434E040B1EA159951B7BD74` |
| 04-19-PLAN.md | `F4897A496D388DD019611E38202AD15344CD3BCA3B4B313B26D4E6EF69A96A65` |
