---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "09"
status: complete
completed_at: "2026-09-08"
requirements: [ODDS-08, ODDS-13]
base_commit: 4a40ad2
technical_commit: b406927
documentation_commit: 8797ae2
---

# Plano 04-09 - Resumo

## Entrega

- Criada a migration Flyway `V25__create_odds_market_lineage.sql`, sucessora direta da V24 conforme ratificacao literal do responsavel.
- Materializadas identidades e historicos de mercado, versao, calculo, publicacao, lifecycle, override, auditoria e comandos idempotentes.
- Aplicadas constraints e triggers PostgreSQL que rejeitam `UPDATE` e `DELETE` dos fatos historicos append-only.
- Criados records Java imutaveis e um repositorio JDBC transacional com chave de idempotencia, lock pessimista, compare-and-set de versao e advisory lock.
- Mantidos fora do schema os objetos financeiros de fases futuras: betting, ledger, settlement, liquidity e exposure.

## Evidencia

- Verificacao focal do schema: `2/2`, zero falhas, erros ou skips.
- Verificacao focal consolidada de migration, persistencia, concorrencia e plataforma: `54/54`, zero falhas, erros ou skips.
- Suite agregada Node: `699/699` em 9 suites, zero falhas, skips ou cancelamentos.
- Suite agregada Maven `test`: `594/594`, zero falhas, erros ou skips.
- Spotless direcionado aos 12 arquivos Java alterados pelo plano: `BUILD SUCCESS`.
- Uma tentativa adicional de `clean verify`, com HMAC de teste explicita no processo, executou as suites e parou no Spotless global. A checagem posterior identificou `19` arquivos preexistentes com divergencia LF/CRLF (incluindo `MarketTransition.java` e varios `package-info.java`), todos fora da allowlist e inalterados nesta Wave. A limitacao nao afeta os comandos de certificacao autorizados do 04-09.

## Garantias

- Repetir um comando com a mesma chave e digest preserva uma unica referencia; reutilizar a chave com payload divergente falha fechado.
- A correcao incorporada em `b406927` garante que retries de calculo, publicacao, lifecycle e override relerem o fato original em vez de tentar um novo `INSERT`.
- Corridas usam transacao PostgreSQL e locks para impedir escrita parcial ou avancos concorrentes inconsistentes.
- Calculo, preco, lifecycle e override mantem referencias historicas exatas em vez de reescrever o passado.
- A migration nao cria apostas, saldo, ledger ou liquidacao e nao antecipa os planos de governanca, produtor sports ou roster.

## Snapshot SHA-256

- `OddsCalculationRecord.java`: `A3DF98FB32006BF5207BF519F36015C597FF181EC507D6E8F9B40D3A54231447`
- `OddsLifecycleRecord.java`: `71B02E8CA5B908258D8602875320DA02A600F3D334761565A37FF44D3BEA7487`
- `OddsMarketRecord.java`: `CA0633783866E1CDB72508890D8BCCFA58DA526B1EA220497FF710CCB56689E5`
- `OddsMarketRepository.java`: `6443C8633FE8F1009A1681DA8EBDA95DCB64BFD6687E0E02C541DEA64D71A783`
- `OddsOverrideRecord.java`: `8C2CA23A88520C7E66840E7F49AA2A003C160021234AB328A535014D5A42E9F4`
- `OddsPublicationRecord.java`: `8A83E6057EF10598CFCDFEA2608335CA700C76FECB5AE266E45E346730BC8DE5`
- `V25__create_odds_market_lineage.sql`: `216FC7DCD426A08EE391B5F3DA7D23303BFE93268143DE4F53ED7D1C1D18850F`
- `OddsMigrationIT.java`: `21384CE94138991D846D8A1C839DC33F82B74C1A3B6D7836292025726E4628E8`
- `OddsPersistenceConcurrencyIT.java`: `83302034E78FB3CC3B4DA3BB60D5EADEE8C842C93400C2725BC99B87889C36F1`
- `OddsPersistenceIT.java`: `617BF264260D1D7CD80A7FD143B1E819749554127CCE6E08F4CE1EDBD8EBAE51`
- `OddsSchemaReviewTest.java`: `23A79660BAA785DE566917583F37A5C71EB6B8EF48BFFCB8E8708EDEC76E5529`
- `AuthoritativeStorageIT.java`: `FCFE83D6B63E7269CED815727F98A707225183B0C244161E99A7008B7A67ED10`
- `FlywayMigrationIT.java`: `EFE1DC77AC2592B7EE54CE7F2393594CB6048D7E5BA54E2FCA13DCAA456C8605`

## Limites

- Este plano entrega somente persistencia e invariantes de lineage; regras de negocio, endpoints, autorizacao maker-checker e integracao produtora pertencem a planos posteriores.
- Nenhum push remoto foi executado.

## Revisao

- O risco estrutural do range canonico `4a40ad2..b406927` foi classificado como `Elevated` pelo RepoWise, sem consumidores quebrados, ciclos, violacoes de seguranca ou gaps de testes funcionais apontados.
- A metrica de coesao do repositorio e um alerta de manutencao para futura separacao por agregado; a classe permanece deliberadamente unificada nesta allowlist para manter a transacao e o lock idempotente no mesmo boundary.
- A cobertura por teste do RepoWise nao esta ingerida; a evidencia executavel focal e agregada permanece registrada acima.
