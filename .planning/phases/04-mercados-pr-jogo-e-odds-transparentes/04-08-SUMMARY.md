---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-08"
wave: 17
status: complete_pending_human_uat
completed_at: "2026-09-09"
base_commit: 1e190e601e87f3da341412b30e6f71af946dfaa6
technical_commit: a4cd613
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13]
---

# Plano 04-08 - Resumo

## O que foi certificado

- Criado `OddsCertificationIT`, um gate focal que executa as suítes
  determinísticas de pricing, taxonomia, replay, lifecycle, override,
  privacidade, contrato e calibração.
- Ampliado o harness de mutações negativas para 11 invariantes: W.O. numérico,
  volume, parâmetros Elo, margem/arredondamento, escopo persistido,
  autoaprovação, estado suspenso, imutabilidade, allowlist pública e cliente
  gerado.
- Cada mutação produz sinal vermelho discriminante e restaura os bytes por
  SHA-256 antes do caso seguinte.

## Evidências

- `OddsCertificationIT`: 1/1 teste JUnit; wrapper reflexivo exige pelo menos 30
  métodos determinísticos.
- Pricing/replay/taxonomia/independência: 44/44; pricing/calibração: 24/24.
- Mercado/lifecycle/public view/privacy/override: 16/16.
- Governança/capabilities/auditoria: 74/74.
- Node generator: 73/73; Angular focal: 57/57; Playwright Chromium/Firefox:
  6/6; validador de rotas: `OK`.
- Harness: 21/21 testes estruturais e 11/11 mutações reais expected-red.

## Limitações explícitas

- `OddsMigrationIT` permanece vermelho em 1 de 2 testes por divergência
  objetiva `27` migrations esperadas versus `29` encontradas; as suítes
  auxiliares Flyway/checksum/storage passaram.
- `generate-api.mjs --check` permanece bloqueado por `ENGINE_MISSING`; o JAR
  OpenAPI aprovado não está disponível no checkout e não foi instalado.
- Maven/Node/Angular agregados não foram reexecutados nesta worktree de
  certificação; não há contagens históricas promovidas a PASS.

## Checkpoint humano

`04-08/T3` está pendente: o responsável deve revisar `04-VALIDATION.md`,
`04-VERIFICATION.md`, hashes, privacidade, responsividade e maker-checker. O
plano só deve ser considerado pronto para a próxima fase após o sinal literal
`aprovar snapshot final da Fase 4`.

## Git

- Commit técnico: `a4cd613 test(04-08): certify phase 4 odds invariants and negative mutations`.
- Commit documental: `PENDING docs(04-08): formalize plan 04-08 verification, roadmap and snapshot`.
