---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-10"
wave: 9
status: complete
completed_at: "2026-09-08"
base_commit: 4d142a6
technical_commit: c9dd669
documentation_commit: PENDING
requirements: [ODDS-02, ODDS-03, ODDS-04, ODDS-05, ODDS-06, ODDS-07, ODDS-08]
---

# Plano 04-10 - Resumo

## Entrega

- Implementado `OddsInputFilter`, que aceita somente resultados esportivos validos para o modelo e exclui W.O. administrativo/numerico, abandono sem resultado e cancelamento sem sintetizar placar.
- Implementado `OddsReplayService`, que aplica corte temporal por `sportTime` e `knownAt`, resolve a cadeia de fatos/supersessoes sem hindsight e cria nova linhagem para correcoes sem reescrever calculos anteriores.
- Mantida a independencia do volume de apostas, liquidez e exposicao; a entrada do replay permanece restrita ao contexto esportivo e aos resultados confirmados admissiveis.
- Adicionadas provas de correcao, independencia de volume, taxonomia de resultados e mutacoes negativas restauraveis para W.O. numerico, volume, K-factor, regressao e arredondamento.

## Evidencia

- Focal Maven: `44/44` testes, zero falhas, erros ou skips.
- Harness Node focal: `15/15` testes; a execucao real eliminou as cinco mutacoes declaradas com sinal vermelho discriminante e restauracao dos SHA-256.
- Spotless: `spotless:apply` e `spotless:check` passaram.
- Suite Node agregada: `714/714` testes em `9` suites, zero falhas, erros, skips ou cancelamentos.
- Suite Maven agregada: `646/646` testes, `BUILD SUCCESS`, zero falhas, erros ou skips.

## Escopo e limites

- Foram alterados somente os sete paths autorizados pelo plano.
- Nenhuma migration, contrato OpenAPI, frontend, aposta, liquidacao, dependencia ou infraestrutura foi alterada.
- O replay preserva historico e produz calculo atual para correcoes; publicacao de odds, lifecycle de mercado e governanca administrativa permanecem nos planos adjacentes.

## Git

- Commit tecnico: `c9dd669`.
- Commit documental: `PENDING` neste arquivo para evitar auto-referencia circular.
- Integracao e push remoto serao executados apos o fechamento documental.
