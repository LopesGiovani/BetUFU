---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "03"
status: complete
completed_at: "2026-09-07"
requirements: [ODDS-08, ODDS-13]
base_commit: 8db17c6
technical_commit: 1bed82d
documentation_commit: e896158
---

# Plano 04-03 - Resumo

## Entrega

- Criado o candidato documental PostgreSQL forward-only em `scripts/odds-schema-candidate.json`.
- Registradas identidades de mercado, versoes, calculo, probabilidade justa, preco publicado, lifecycle, override, auditoria e idempotencia.
- Registradas constraints de imutabilidade, lineage, referencias V16/V24, lock pessimista com compare-and-set e retry seguro.
- Mantidos PostgreSQL/Flyway como autoridade; Redis, ledger, settlement, bets, creditos, liquidity e exposure permanecem proibidos.
- Nenhuma migration, DDL, entidade, repository ou runtime foi criado.

## Checkpoint 04-03/T1

- Aprovacao do responsavel: `04-03/T1@2026-09-07: owner approved additive odds schema modeling`.
- A aprovacao libera somente a revisao do candidato e o planejamento posterior de 04-09; nao libera DDL.
- Qualquer alteracao no candidato exige novo SHA-256 e nova ratificacao antes de 04-09.

## Evidencia

- Comando oficial de schema: exit code `0`.
- Candidato, SHA-256 normalizado para LF: `FE7F4C7A45EBC426D968E52B4A2D7BA6621818BEFDB7B1F330D9921C74E17DA9`.
- Inventario existente de migrations: `B2A8576E678F6F95DDB99E2658879A8641C7E7146C6AE1C15FABB56F76DC3870`.
- Nenhuma tabela de betting, ledger ou settlement aparece no candidato.

## Limites

- 04-09 materializa a migration literal somente apos nova ratificacao do filename e dos invariantes.
- 04-16, 04-17, 04-18 e 04-19 continuam donos da autoridade sports, produtor, governanca e roster.
