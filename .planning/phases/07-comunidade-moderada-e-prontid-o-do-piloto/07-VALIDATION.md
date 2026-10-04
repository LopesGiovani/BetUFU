---
phase: 07
slug: comunidade-moderada-e-prontid-o-do-piloto
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-10-04
---

# Phase 7 - Validation Strategy

Contrato FUTURO de validacao, nao resultado de testes. A proposta revisada tem
35 planos, 17 ondas e 102 tasks. Substitui o mapa anterior de dez planos/44 tasks.
Nenhuma prova material executada; nao transferir o passed da Fase 6 para esta fase.
UI e politicas dependem dos checkpoints do CONTEXT e de cada PLAN.

## Test Infrastructure And Sampling

JUnit/Maven, PostgreSQL Testcontainers, Modulith/ArchUnit, Vitest/Angular, Node
e Playwright real existentes; nenhuma instalacao nesta etapa. Criar testes
discriminantes junto da fatia aprovada, antes do codigo. Classes/scripts novos
ainda ausentes sao W0, nunca passed ou skipped como substituto da prova.
Executar focal por task, agregados por bloco/onda aprovada e navegador nas
jornadas materiais. Checkpoint humano verifica autoridade, nao comportamento.
Nenhuma sequencia de tres tasks pode ficar sem prova ou checkpoint registrado.
Latencia de feedback e tempo de suite devem ser medidos, nao inventados.

## Requirement Verification Map

| Requirements | Plans | Required evidence |
|---|---|---|
| CHAT-01/02, RT-01 | 04..12, 16..17, 21, 31..32 | Evento publico/global autenticado, ATIVO, cursor/marker, origem/sessao, protocolo aprovado, deadlines e retries concorrentes |
| CHAT-03/04 | 13..15, 31..32 | Enum fechado, denunciante privado, mute imediato sincronizado sem afetar outro leitor |
| CHAT-05/06 | 13, 16..19, 27, 31..32 | Moderador scoped, original restrito/auditado, sancao admin-only, progressao e revisao sem DELETE |
| INTE-05 | 17, 20..23, 31..32 | Alvos e retomada atomicos, historico intacto, saldo/revisao/direitos acessiveis |
| PRIV-03/04/05 | 02, 24..27, 29..32 | Titularidade, direitos reais, exports/redacao, politica por classe e identidade legitima |
| OPS-04 | 28..30, 32 | Backup tres substratos e restore utilizavel, supressao posterior ao snapshot, RPO/RTO medidos |
| TEST-03/05, UX-01/02 | Conforme requirements em cada PLAN | Contratos/gerador, CI negativa, real browser desktop/mobile/teclado, estados/foco |
| TEST-04 (launch obligation) | 30, 32 | Perfil 50 simultaneos com mix/duracao/SLO/taxa de erro ratificados e medidos |

## Per-task Verification Map

Todos os comandos abaixo sao futuros, extraidos do <verify> de cada task.
O PLAN correspondente contem arquivos, comportamento, criterio de aceite e
threat_model T-07-NN-1/2. Checkpoint com git status ou hash NAO e teste de negocio.
Comandos de candidato exigem engine real aprovado; nao editar cliente gerado.
Cada auto consumidor primeiro valida --plan/--stage: prepare-candidates ou
implementation, distinto do aceite humano e sem autorizacao por fixture.
Ausencia/falha/divergencia interrompe; apenas 07-01 tem bootstrap explicito.
Classes/scripts/specs inexistentes devem ser criados na task antes de executar.
Referencias a gates juridicos/UI/protocolo nao sao aprovacoes ja obtidas.

| Task | Wave | Type | Planned automated verification | State |
|---|---|---|---|---|
| 07-01-01 | 1 | checkpoint:decision | `git status --short` | Pending human |
| 07-01-02 | 1 | auto | `node --test scripts/validate-phase7-approvals.test.mjs` | W0 / not executed |
| 07-02-01 | 2 | checkpoint:decision | `git status --short` | Pending human |
| 07-02-02 | 2 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-02 --stage implementation`; `node --test scripts/validate-phase7-policy.test.mjs` | W0 / not executed |
| 07-03-01 | 2 | checkpoint:decision | `git status --short` | Pending human |
| 07-03-02 | 2 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-03 --stage implementation`; `node --test frontend/scripts/generate-api.test.mjs` | W0 / not executed |
| 07-04-01 | 2 | checkpoint:decision | `git status --short` | Pending human |
| 07-04-02 | 2 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-04-candidate/V39__chat_channels_and_messages.sql'` | W0 / not executed |
| 07-04-03 | 2 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-04-candidate/V39__chat_channels_and_messages.sql'` | Pending human |
| 07-04-04 | 2 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatSchemaIT,FlywayMigrationIT" test` | W0 / not executed |
| 07-05-01 | 3 | checkpoint:decision | `git status --short` | Pending human |
| 07-05-02 | 3 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-05 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatReadIT,ModulithArchitectureTests" test` | W0 / not executed |
| 07-06-01 | 4 | checkpoint:decision | `git status --short` | Pending human |
| 07-06-02 | 4 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-06 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-06-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-06-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-06.json` | W0 / not executed |
| 07-06-03 | 4 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-06-candidate/inventory.json'` | Pending human |
| 07-06-04 | 4 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-06 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-07-01 | 4 | checkpoint:decision | `git status --short` | Pending human |
| 07-07-02 | 4 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-07 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatPushSecurityIT,ChatPushDeliveryIT" test` | W0 / not executed |
| 07-08-01 | 5 | checkpoint:decision | `git status --short` | Pending human |
| 07-08-02 | 5 | tracer | `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-read.spec.ts` | W0 / not executed |
| 07-09-01 | 4 | checkpoint:decision | `git status --short` | Pending human |
| 07-09-02 | 4 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-09 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-09-candidate/V40__chat_writing.sql'` | W0 / not executed |
| 07-09-03 | 4 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-09-candidate/V40__chat_writing.sql'` | Pending human |
| 07-09-04 | 4 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-09 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatWriteIT,ChatWriteConcurrencyIT" test` | W0 / not executed |
| 07-10-01 | 5 | checkpoint:decision | `git status --short` | Pending human |
| 07-10-02 | 5 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-10 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-10-candidate/V41__chat_definitive_lifecycle.sql'` | W0 / not executed |
| 07-10-03 | 5 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-10-candidate/V41__chat_definitive_lifecycle.sql'` | Pending human |
| 07-10-04 | 5 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-10 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatLifecycleIT,ChatLifecycleConcurrencyIT" test` | W0 / not executed |
| 07-11-01 | 6 | checkpoint:decision | `git status --short` | Pending human |
| 07-11-02 | 6 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-11 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-11-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-11-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-11.json` | W0 / not executed |
| 07-11-03 | 6 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-11-candidate/inventory.json'` | Pending human |
| 07-11-04 | 6 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-11 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-12-01 | 10 | checkpoint:decision | `git status --short` | Pending human |
| 07-12-02 | 10 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-12 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-write.spec.ts` | W0 / not executed |
| 07-13-01 | 6 | checkpoint:decision | `git status --short` | Pending human |
| 07-13-02 | 6 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-13 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-13-candidate/V42__chat_reports_and_mutes.sql'` | W0 / not executed |
| 07-13-03 | 6 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-13-candidate/V42__chat_reports_and_mutes.sql'` | Pending human |
| 07-13-04 | 6 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-13 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatReportIT,ChatMuteIT" test` | W0 / not executed |
| 07-14-01 | 7 | checkpoint:decision | `git status --short` | Pending human |
| 07-14-02 | 7 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-14 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-14-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-14-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-14.json` | W0 / not executed |
| 07-14-03 | 7 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-14-candidate/inventory.json'` | Pending human |
| 07-14-04 | 7 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-14 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-15-01 | 11 | checkpoint:decision | `git status --short` | Pending human |
| 07-15-02 | 11 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-15 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-community.spec.ts` | W0 / not executed |
| 07-16-01 | 7 | checkpoint:decision | `git status --short` | Pending human |
| 07-16-02 | 7 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-16 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-16-candidate/V43__chat_moderation_cases.sql'` | W0 / not executed |
| 07-16-03 | 7 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-16-candidate/V43__chat_moderation_cases.sql'` | Pending human |
| 07-16-04 | 7 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-16 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatModerationIT,ChatEvidenceAccessIT" test` | W0 / not executed |
| 07-17-01 | 8 | checkpoint:decision | `git status --short` | Pending human |
| 07-17-02 | 8 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-17 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatSanctionIT,ChatReviewIT,ChatSanctionConcurrencyIT" test` | W0 / not executed |
| 07-18-01 | 9 | checkpoint:decision | `git status --short` | Pending human |
| 07-18-02 | 9 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-18 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-18-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-18-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-18.json` | W0 / not executed |
| 07-18-03 | 9 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-18-candidate/inventory.json'` | Pending human |
| 07-18-04 | 9 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-18 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-19-01 | 12 | checkpoint:decision | `git status --short` | Pending human |
| 07-19-02 | 12 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-19 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-moderation.spec.ts` | W0 / not executed |
| 07-20-01 | 8 | checkpoint:decision | `git status --short` | Pending human |
| 07-20-02 | 8 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-20 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-20-candidate/V44__emergency_pauses.sql'` | W0 / not executed |
| 07-20-03 | 8 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-20-candidate/V44__emergency_pauses.sql'` | Pending human |
| 07-20-04 | 8 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-20 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=EmergencyPauseIT,ModulithArchitectureTests" test` | W0 / not executed |
| 07-21-01 | 9 | checkpoint:decision | `git status --short` | Pending human |
| 07-21-02 | 9 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-21 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=EmergencyPauseConcurrencyIT,ChatPauseIT,PredictionAvailabilityIT,ModulithArchitectureTests" test` | W0 / not executed |
| 07-22-01 | 10 | checkpoint:decision | `git status --short` | Pending human |
| 07-22-02 | 10 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-22 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-22-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-22-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-22.json` | W0 / not executed |
| 07-22-03 | 10 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-22-candidate/inventory.json'` | Pending human |
| 07-22-04 | 10 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-22 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-23-01 | 13 | checkpoint:decision | `git status --short` | Pending human |
| 07-23-02 | 13 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-23 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-operations.spec.ts`; `npm --prefix frontend test -- --watch=false --include=src/app/features/odds/public-market/public-market-bet-ticket.component.spec.ts --include=src/app/features/odds/public-market/public-market-betting.store.spec.ts` | W0 / not executed |
| 07-24-01 | 9 | checkpoint:decision | `git status --short` | Pending human |
| 07-24-02 | 9 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-24 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-24-candidate/V45__privacy_rights_cases.sql'` | W0 / not executed |
| 07-24-03 | 9 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-24-candidate/V45__privacy_rights_cases.sql'` | Pending human |
| 07-24-04 | 9 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-24 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=PrivacyRightsIT,ModulithArchitectureTests" test` | W0 / not executed |
| 07-25-01 | 11 | checkpoint:decision | `git status --short` | Pending human |
| 07-25-02 | 11 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-25 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-25-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-25-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-25.json` | W0 / not executed |
| 07-25-03 | 11 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-25-candidate/inventory.json'` | Pending human |
| 07-25-04 | 11 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-25 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-26-01 | 14 | checkpoint:decision | `git status --short` | Pending human |
| 07-26-02 | 14 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-26 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-privacy.spec.ts` | W0 / not executed |
| 07-27-01 | 10 | checkpoint:decision | `git status --short` | Pending human |
| 07-27-02 | 10 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-27 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-27-candidate/V46__privacy_retention_and_suppressions.sql'` | W0 / not executed |
| 07-27-03 | 10 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-27-candidate/V46__privacy_retention_and_suppressions.sql'` | Pending human |
| 07-27-04 | 10 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-27 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=PrivacyRetentionIT,PrivacyExportIT" test` | W0 / not executed |
| 07-28-01 | 2 | checkpoint:decision | `git status --short` | Pending human |
| 07-28-02 | 2 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-28 --stage implementation`; `node scripts/phase7-backup.mjs --policy infra/backup/phase7-backup-policy.json --isolated --schedule-once` | W0 / not executed |
| 07-29-01 | 11 | checkpoint:decision | `git status --short` | Pending human |
| 07-29-02 | 11 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-29 --stage implementation`; `node scripts/phase7-restore.mjs --policy infra/backup/phase7-backup-policy.json --isolated` | W0 / not executed |
| 07-30-01 | 15 | checkpoint:decision | `git status --short` | Pending human |
| 07-30-02 | 15 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-30 --stage implementation`; `node --test scripts/phase7-quality.test.mjs scripts/phase7-load.test.mjs scripts/validate-publication-privacy.test.mjs` | W0 / not executed |
| 07-31-01 | 16 | checkpoint:decision | `git status --short` | Pending human |
| 07-31-02 | 16 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-31 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-integrated.spec.ts` | W0 / not executed |
| 07-32-01 | 17 | checkpoint:decision | `git status --short` | Pending human |
| 07-32-02 | 17 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-32 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=Phase7ConcurrencyIT,Phase7PrivacyIT,Phase7CertificationIT,ModulithArchitectureTests" test` | W0 / not executed |

| 07-33-01 | 11 | checkpoint:decision | `git status --short` | Pending human |
| 07-33-02 | 11 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-33 --stage prepare-candidates`; `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-33-candidate/V47__chat_rules_dismissals.sql'` | W0 / not executed |
| 07-33-03 | 11 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-33-candidate/V47__chat_rules_dismissals.sql'` | Pending human |
| 07-33-04 | 11 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-33 --stage implementation`; `node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatRulesPreferenceSchemaIT,ChatRulesPreferenceIT,ModulithArchitectureTests" test` | W0 / not executed |
| 07-34-01 | 12 | checkpoint:decision | `git status --short` | Pending human |
| 07-34-02 | 12 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-34 --stage prepare-candidates`; `npm --prefix frontend run generate:api -- --candidate .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-34-candidate/ --candidate-source .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-34-candidate/betufu.yaml --candidate-approval docs/phase7-approvals/07-34.json` | W0 / not executed |
| 07-34-03 | 12 | checkpoint:decision | `Get-FileHash -Algorithm SHA256 -LiteralPath '.planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-34-candidate/inventory.json'` | Pending human |
| 07-34-04 | 12 | auto | `node scripts/validate-phase7-approvals.mjs --plan 07-34 --stage implementation`; `npm --prefix frontend run generate:api:check` | W0 / not executed |
| 07-35-01 | 14 | checkpoint:decision | `git status --short` | Pending human |
| 07-35-02 | 14 | tracer | `node scripts/validate-phase7-approvals.mjs --plan 07-35 --stage implementation`; `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-rules.spec.ts` | W0 / not executed |

## Review Reconciliation Coverage (Future)

A revisao documental acrescenta obrigacoes testaveis sem afirmar execucao:

| Gap | Producer / contract / UI | Discriminating evidence required |
|---|---|---|
| Encaminhar caso | 16 / 18 / 19, repetido em 31 | Fato append-only unico, retry, 403 fora de escopo/revogado; UI real moderador encaminha, refresh, administrador localiza/decide; nenhuma sancao automatica |
| Disponibilidade de pausas | 20/21 / 22 / 23, repetido em 31 | GET publico so para alvo publicado, global 401, conta A/B privada/no-store, DTO nao vazio sem PII; poscommit/rollback/replay; refresh e refetch sem listagem admin |
| Bloqueio visual do bilhete | 21/22 / 23 | Store e DOM TS/HTML provam bloqueio real, erro/desconhecido, deadline prioritario, retomada sem bypass e comprovante preservado; E2E todos os mercados do evento |
| Gate antes de mutacao | 01 e consumers 12/15/19/23/26/31 | Comando literal --plan/--stage antes de escrever ou iniciar setup; ausencia, pending, etapa/hash/HEAD divergentes interrompem |

21 focal inclui PredictionAvailabilityIT e ModulithArchitectureTests. 23 deve
executar tambem npm --prefix frontend test -- --watch=false
--include=src/app/features/odds/public-market/public-market-bet-ticket.component.spec.ts
--include=src/app/features/odds/public-market/public-market-betting.store.spec.ts
alem do runner Playwright literal. 31 repete fluxo integrado entre sessoes.
Novas rotas/schemas/evento/copy/falha sao propostas a ratificar no checkpoint
do consumidor. Se a geracao produzir paths extras, bloquear/subdividir antes de
publicar. Nenhum comando desta secao foi executado; estados continuam W0/pending.

## Rules Popup Coverage (Future)

07-08: header HTML/teste e descoberta desktop/mobile/teclado. 07-33: PostgreSQL
real, concorrencia, CSRF e contas A/B. 07-34: geracao governada e schema minimo.
07-35: primeira visita, dispensa A/refresh/segundo dispositivo, B independente,
Regras manual/visitante, foco/Escape/logout/TTL e erro de sincronizacao honesto.
07-31 repete jornada integrada apos 35; nenhuma dessas provas executada.

## Aggregate And Real Evidence

Focais acima nao substituem <verification> de cada PLAN. Apos autorizacao
material, repetir independentemente focais e agregados:

    node scripts/maven.mjs -f backend/pom.xml verify
    node --test scripts/*.test.mjs
    npm --prefix frontend test -- --watch=false
    npm --prefix frontend run generate:api:check
    npm --prefix frontend run lint
    npm --prefix frontend run build:production
    git diff --check

Capturar contagens XML Surefire/Failsafe, falhas/erros/skips e artefatos reais.
Testar harness nao e executar Playwright; testar script de backup nao e restaurar.
Os PLANs UI usam runner real criado em 07-08; 07-31 amplia as jornadas.
07-29 exige restore dos tres substratos e supressao posterior ao snapshot antes
de liberar leitura. 07-30 mede carga/seguranca; 07-32 certifica evidencia congelada.
Nunca resetar volumes/apostas do dono. Alvos/limites/externos exigem aceite proprio.

## Manual And External Gates

07-UI-SPEC draft e protocolo server-push pendentes; RT-01/STOMP exige
reconciliacao especifica. CHAT-01/05 e Out of Scope reconciliados, nao entregues.
Arquitetura/falhas/retencao da preferencia de regras aguardam checkpoint de 33. Unidade Unicode/limiter, casos de cancelamento,
compensacoes, mute e infracao devem ser ratificados onde consumidos.
Revisao juridica, retencao/direitos, identidade institucional, destino/chaves/
consistencia de backup e mix/SLO/erro de carga continuam checkpoints.
90/180 dias sao propostas; 30 dias de backup e RPO/RTO nao comprovam resultados.
Toda vulnerabilidade confirmada bloqueia piloto; falha de scanner nao e clean.
WCAG completo nao e substituido por verificar foco e overflow. Sem pentest
externo, novas dependencias, Docker ou testes materiais autorizados neste bloco.

## Validation Sign-off

- [ ] Contrato UI e requisitos conflitantes reconciliados/aprovados.
- [ ] Propostas revisadas e aprovadas pelo responsavel para bloco especifico.
- [x] Mapa por task atualizado para 35 planos/102 tasks.
- [ ] Provas W0 criadas e executadas com discriminacao do comportamento.
- [ ] Gates juridicos/operacionais evidenciados.
- [ ] Jornadas reais, restore e carga executados em recursos isolados aprovados.
- [ ] Agregados completos sem falhas obrigatorias.
- [ ] Snapshot/revisao finais aprovados.
- [ ] nyquist_compliant confirmado por evidencia material.

Approval: pending. Nenhum status passed atribuido por geracao deste documento.
