---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "17"
wave: 7
status: complete
completed_at: "2026-09-08"
base_commit: 032a98d
technical_commit: f1bcce9
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-02, ODDS-04, ODDS-08]
---

# Plano 04-17 - Resumo

## Entrega

- Implementado o produtor Spring `SportsResultForOddsProducer` dentro de `sports.internal.odds`, expondo somente a porta pública `SportsResultForOddsPort`.
- Criado o read-model JDBC `SportsOddsSourceRepository`, que lê eventos HEAD_TO_HEAD publicados, o head V27, fatos append-only e a versão V16 exata referenciada pelo payload esportivo.
- O mapeamento preserva scores nos slots V16 e mantém fatos administrativos (`ADMINISTRATIVE_WALKOVER` e `ABANDONED_NO_VALID_RESULT`) sem scores sintéticos.
- O replay temporal usa `known_at <= cutoff`, resolve supersessões sem hindsight e rejeita ciclos, forks, IDs duplicados e supersessões entre eventos.
- Nenhuma dependência de `sports.internal` foi exposta ao módulo odds; a fronteira arquitetural continua protegida.

## Evidência

- T1 focal: `2/2` testes, zero falhas, erros ou skips.
- T2 focal + arquitetura: `10/10` testes, zero falhas, erros ou skips.
- Spotless: `spotless:apply` e `spotless:check` passaram.
- Suíte Node agregada: `699/699` em 9 suites, zero falhas, erros, skips ou cancelamentos.
- Suíte Maven agregada: `594/594`, `BUILD SUCCESS`, zero falhas, erros ou skips.

## Escopo e limites

- Foram alterados somente os três componentes de produção e os dois testes autorizados pelo plano, além deste resumo e do estado GSD.
- V16, V24, OpenAPI, frontend, pricing, apostas, liquidação e infraestrutura não foram alterados.
- O replay/as-of foi implementado na porta interna de leitura; a exposição HTTP de odds permanece fora deste plano.

## Git

- Commit técnico: `f1bcce9`.
- Commit documental: `PENDING`.
- Nenhum push remoto foi executado neste ponto do fechamento.
