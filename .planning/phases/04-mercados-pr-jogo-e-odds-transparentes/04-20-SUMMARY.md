---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-20"
wave: 11
status: complete
completed_at: "2026-09-12"
technical_commit: 0d7f3b0
documentation_commit: d8a3570
requirements-completed: [TOOLING-SEED-01, TOOLING-SEED-02, TOOLING-SEED-03]
---

# Plano 04-20 - Resumo

O seeder local foi entregue em Node.js ESM e integrado ao dispatcher de
desenvolvimento. Os perfis `daily` e `full` reencontram ou criam personas,
fixtures esportivas LAUFU e mercados de odds com chaves deterministicas,
transacao SQL unica e `ON CONFLICT` idempotente.

## Seguranca e provas

- Keycloak e acessado somente pela API administrativa local; o realm versionado
  nao foi alterado.
- Logs nao imprimem credenciais, tokens, matriculas, HMAC ou PII.
- O seeder falha fechado quando os servicos necessarios estao offline.
- `node --test scripts/seed-local.test.mjs` passou `7/7` apos os ajustes de
  corpo vazio HTTP 201, sanitizacao de `stderr` e metadados de decisao da
  proposta de vinculo de atleta.

## Git

- Implementacao: `0d7f3b0 feat(tooling): implement local dev seeder with standard users and sports fixtures`.
- Documentacao: `d8a3570 docs(tooling): document dev test personas and seed usage`.
- Correcoes posteriores de projecao de datas, contrato de odds e associacao do
  override estao registradas nos commits `71fd224`, `7fae764` e `1d72461`.
