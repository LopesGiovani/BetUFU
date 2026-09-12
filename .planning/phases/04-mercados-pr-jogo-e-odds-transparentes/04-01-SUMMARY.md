---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-01"
wave: 0
status: complete_fail_closed_boundary
completed_at: "2026-09-12"
technical_commit: 78a8cc8
requirements-completed: [ODDS-02, ODDS-03, ODDS-04, ODDS-06, ODDS-07]
---

# Plano 04-01 - Resumo

O plano fechou a fronteira de evidencia de calibracao em modo fail-closed.
Manifesto, fixtures, relatorio, quarentena e validador foram versionados com
hashes reproduziveis, separando elegibilidade de calibracao de elegibilidade de
producao.

## Resultado

- T1: `node scripts/validate-odds-calibration.mjs --check` passou `20/20`.
- T2: a captura exploratoria LAUFU permaneceu rejeitada para promocao de
  producao; nenhum parametro ou backtest foi inventado.
- O plano foi encerrado como limite de seguranca, nao como aprovacao de dataset
  de producao. Os planos posteriores consumiram somente os contratos e
  invariantes permitidos por essa fronteira.

## Git

- Commit tecnico: `78a8cc8 fix(04-01): harden odds calibration evidence gate`.
- Nenhuma decisao numerica aberta foi resolvida implicitamente.
