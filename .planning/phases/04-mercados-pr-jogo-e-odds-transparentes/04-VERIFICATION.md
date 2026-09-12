---
phase: 4
plan: "04-08"
wave: 17
status: passed
base_commit: 1e190e601e87f3da341412b30e6f71af946dfaa6
pre_git_head: 1e190e601e87f3da341412b30e6f71af946dfaa6
technical_commit: a4cd613
reconciled_commit: 1d72461
verification_reconciled_at: "2026-09-12"
branch: worktree-agent-04-08
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-04-08
---

# Plano 04-08 - Verificacao

## Escopo congelado

O plano foi executado diretamente na worktree dedicada, sem Antigravity. O
commit técnico congelado é `a4cd613`. Os
unicos paths tecnicos alterados sao:

- `scripts/validate-odds-negative-mutations.mjs`
- `scripts/validate-odds-negative-mutations.test.mjs`
- `backend/src/test/java/br/com/betufu/odds/OddsCertificationIT.java`

Os documentos deste relatorio pertencem ao bloco documental aprovado e nao
alteram migrations, contratos OpenAPI ou cliente gerado.

## Evidencia executada

| Gate | Resultado |
|---|---|
| Certificacao focal `OddsCertificationIT` | PASS - 1/1 teste JUnit; wrapper reflexivo executa pelo menos 30 metodos deterministicos |
| Pricing/replay/taxonomia/independencia | PASS - 44/44 |
| Pricing/calibracao | PASS - 24/24 |
| Mercado/lifecycle/public/privacy/override | PASS - 16/16 |
| Governanca/capabilities/auditoria | PASS - 74/74, com HMAC de teste somente no processo |
| Gerador Node | PASS - 73/73; `--check` bloqueado por `ENGINE_MISSING` |
| Angular focal | PASS - 57/57 |
| Playwright Chromium/Firefox, desktop/320px | PASS - 6/6 |
| Validador de rotas | PASS - `OK` |
| Mutações estruturais | PASS - 21/21 |
| Mutações reais | PASS - 11/11 expected-red, todos os bytes restaurados |
| `git diff --check` | PASS - exit 0 |

## Bloqueios e desvios

1. `OddsMigrationIT`: 1 falha em 2 testes, porque a expectativa congelada de
   27 migrations encontrou 29. As provas auxiliares `FlywayMigrationIT` (18/18),
   `FlywayChecksumIT` (1/1) e `AuthoritativeStorageIT` (4/4) passaram. O teste
   divergente está fora da allowlist e não foi alterado silenciosamente.
2. `node frontend/scripts/generate-api.mjs --check`: `ENGINE_MISSING`, pois o
   JAR OpenAPI aprovado não existe na worktree. Não houve instalação nem mudança
   de manifesto; o teste Node do gerador passou 73/73.
3. Maven agregado, Node agregado e Angular agregado não foram reexecutados nesta
   worktree de certificação; permanecem `NOT RUN/BLOCKED`, sem reaproveitar
   contagens históricas como PASS. A execução Angular 364/364 e Playwright 6/6
   são evidências anteriores já registradas na matriz, não substitutos do UAT.

## Hashes congelados dos insumos

SHA-256 dos bytes presentes no checkout antes dos commits:

| Categoria | Path | Bytes | SHA-256 |
|---|---|---:|---|
| Migration | `backend/src/main/resources/db/migration/V25__create_odds_market_lineage.sql` | 10749 | `216FC7DCD426A08EE391B5F3DA7D23303BFE93268143DE4F53ED7D1C1D18850F` |
| Migration | `backend/src/main/resources/db/migration/V26__extend_odds_governance_audit.sql` | 1066 | `61590B53FB50F86398FD26DBCD865927179395213B8A5A12A041B8C3F573AA33` |
| Migration | `backend/src/main/resources/db/migration/V27__create_sports_outcome_authority.sql` | 7561 | `6BEA5B46E11A52294809EF21FE6F8DB1B542651FC8F0303C1B9F834C607CA9F9` |
| Migration | `backend/src/main/resources/db/migration/V28__bind_event_roster_publication.sql` | 4459 | `CCEE8D1A579DD175380DB98FCB9088C0C07A065A6CC89FD696C5CAC9657EAA27` |
| Contract | `backend/openapi/betufu.yaml` | 227675 | `603D74EBEC33392F0D391C614B84A1FECA2CB78AF3F6213115E3587677848691` |
| Contract | `scripts/odds-schema-candidate.json` | 9421 | `25CE479A422EA846C9B08E904BB669CDC5D5531E23F1925B69445C6F9B54E12B` |
| Contract | `scripts/odds-governance-candidate.json` | 4948 | `A7FF20977AE667ACC7BF956BD892262D3DC517C360C7EE3D36A0B8CFD7BFE5AB` |
| Contract | `scripts/odds-roster-binding-candidate.json` | 2738 | `4ADA53E6CDBAC8998509870E43AC4562F6AD8CB2B79848ECBE315E06696555F7` |
| Contract | `scripts/sports-odds-source-candidate.json` | 20786 | `E41640DFFD702FA3473AE80FBB5F4A0DA00EA5A35D0BB841C5640C2B0BC40D32` |
| Fixture | `scripts/odds-calibration-manifest.json` | 4618 | `D3CA6E8220788AA39DAF2F095BF1E00160FD9A236D4C5C2D03B57368B9824901` |
| Fixture | `scripts/odds-backtest-report.json` | 3468 | `679B5F72337ECC9C3118D722A5BFA4CD99653B9D4C3EF886C4C42A85965D0B72` |
| Fixture | `scripts/odds-calibration-fixtures.json` | 2988 | `30998E33F6A9E53295E30CE4F6F5C073D7FEF074D98729D987B9D0A8C784A42A` |
| Fixture | `scripts/odds-state-machine-fixtures.json` | 27195 | `F648766A028CC55A49ECB8778A6A88896614E878FB1499B6A61CA3E6F7E1BB1A` |
| Fixture | `scripts/odds-unresolved-quarantine.json` | 5830 | `C8B7FF57A16FDFD71063EC41B80BBA78A36B92BA6DE0665D3A6D0AF2DA64585B` |
| Generated contract | `frontend/scripts/generate-api.mjs` approved digest | - | `51E5582AEA63F066EE6D80E552663C70269BB8385EB53EA2D31716E37DAC3B92` |

## Reconciliacao final - 2026-09-12

O checkpoint humano `04-08/T3` foi aprovado pelo responsavel com o sinal
literal `aprovar snapshot final da Fase 4`. O snapshot original continua
preservado acima como historico.

A auditoria posterior na linha `1d72461` confirmou: `28` testes de backend,
Angular `366/366`, seeder `7/7`, Playwright `6/6`, gerador Node `73/73`,
mutacoes estruturais `21/21`, mutacoes negativas reais `11/11`, lint sem
erros e smoke Chromium/Firefox em desktop e 320px para `/`, `/arena` e o
deep link de odds. O endpoint publico de odds respondeu HTTP 200 anonimo e
sem campos privados.

Os bloqueios do snapshot original foram resolvidos no ambiente de certificacao
e permanecem documentados sem apagar o historico. O unico alerta residual foi
um `404` de `favicon.ico`, fora do escopo funcional. A verificacao esta
`passed` e a Fase 4 pode ser usada como dependencia da Fase 5.

## UAT T3

O checkpoint humano `04-08/T3` continua bloqueante. A aprovação deve confirmar
transparência da odd, histórico e explicação em duas camadas; privacidade sem
PII; responsividade em 320px/desktop; maker-checker com dois administradores;
bloqueio de `CLOSED/LOCKED`; e ausência de créditos, apostas, settlement,
liquidez, H2H positivo e LAUFU de produção.

Sinal esperado: `aprovar snapshot final da Fase 4`. Qualquer divergência exige
novo ciclo de correção e invalida estes hashes.
