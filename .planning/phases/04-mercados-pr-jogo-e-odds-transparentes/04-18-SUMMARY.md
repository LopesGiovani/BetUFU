---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "18"
wave: 5
status: complete
completed_at: "2026-09-08"
base_commit: 8797ae2
technical_commit: 8f571bb
documentation_commit: e31aa8a
---

# Plano 04-18 - Resumo

## Entrega

- Criado o candidato nao-runtime `scripts/odds-governance-candidate.json`, com matriz de comandos, papeis, escopo derivado do recurso, capabilities de navegacao e vocabulario fechado.
- Adicionadas as capabilities `VIEW_ODDS_MARKET`, `MANAGE_ODDS_MARKET`, `PROPOSE_ODDS_OVERRIDE` e `APPROVE_ODDS_OVERRIDE`.
- Implementado `OddsAuthorizationService`, que reconsulta `OperationalIdentityLookup` e valida papel e `competition_id` persistido no servidor; capability de UI nunca e prova de permissao.
- Estendido `AuditEntry` e a migration Flyway V26 para preservar acoes legadas e aceitar somente as oito acoes odds ratificadas.
- Expandida a prova append-only para rejeitar acoes desconhecidas e tentativas de `UPDATE`/`DELETE`.

## Evidencia

- Candidato estrutural: passou (`exit code 0`).
- Focal Plano 04-18: `108/108`, zero falhas, erros ou skips.
- Node agregado: `699/699` em 9 suites, zero falhas, erros ou skips.
- Maven agregado: `594/594`, zero falhas, erros ou skips.
- `git diff --check`: passou.
- Spotless dos arquivos novos do plano: passou. O `spotless:check` global continua apontando somente dois arquivos historicos fora desta allowlist (`OddsSchemaReviewTest.java` e `SportsOddsBoundaryArchitectureTest.java`).

## Reconciliacao de escopo

- Tres testes existentes foram atualizados mecanicamente para refletir capabilities novas e a contagem de 27 migrations: `OperationalCapabilitiesIT`, `OperationalCapabilitiesResponseTest` e `OddsMigrationIT`.
- Nenhum arquivo de producao fora da allowlist reconciliada foi alterado; nao houve dependencia, OpenAPI, infraestrutura ou push remoto.

## Riscos e limites

- RepoWise classificou o diff como risco moderado e sinalizou complexidade/cohesao em `OddsAuthorizationService`; o comportamento esta coberto, mas a extracao de politicas menores e um follow-up de manutencao.
- A migration V26 altera somente o vocabulario de auditoria; comandos de mercado, maker-checker e endpoints permanecem nos planos posteriores.

## Snapshot SHA-256

- `scripts/odds-governance-candidate.json`: `A2A528BA3E11DE6FAF6982871B74A78C949518F7942BEB2C0F6A9990D9433EC4`
- `backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java`: `B6CD5523CB3B2B23CFC1B26479CE75C6A5569B802BE53BD03A51A86169283A39`
- `backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java`: `73BCFF750FD0B43C710432A206586D47D4EC44E33303043AE5C788EF1A548062`
- `backend/src/main/java/br/com/betufu/audit/AuditEntry.java`: `9FADE2F843B50296D48DCFD181EDD5F26B0819F8DE97BA89851B481665A8F76A`
- `backend/src/main/java/br/com/betufu/odds/internal/governance/OddsAuthorizationService.java`: `C8B2D32F90FC2A1C1D182694228EC83107E96F7A03F16AE0A67C682084C6C1AD`
- `backend/src/main/resources/db/migration/V26__extend_odds_governance_audit.sql`: `61590B53FB50F86398FD26DBCD865927179395213B8A5A12A041B8C3F573AA33`

## Git

- Commit tecnico: `8f571bb`.
- Commit documental, integracao fast-forward e limpeza da worktree/branch foram concluidos localmente.
- Nenhum push remoto foi executado.
