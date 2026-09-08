---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-04"
wave: 8
status: complete
completed_at: "2026-09-08"
base_commit: 7fed324
technical_commit: f741e21
documentation_commit: PENDING
requirements: [ODDS-05, ODDS-06, ODDS-07, ODDS-09]
---

# Plano 04-04 - Resumo

## Entrega

- Implementado o kernel puro `OddsPricingKernel`, sem relogio, rede, banco ou volume de apostas como entrada.
- Implementados Elo, Davidson, confianca e margem com os parametros ratificados: rating inicial 1500, K 32/16 no limiar de 30 jogos, regressao de edicao de 50%, `theta=0.50`, bandas `<5`, `5..15`, `>15`, margem 5% e `HALF_UP` em duas casas.
- A probabilidade justa permanece separada da odd publicada; amostras baixas retornam estimativa neutra e fatos administrativos nao alimentam o Elo.
- Implementado `OddsCalculationApplicationService`, que consome somente `SportsResultForOddsPort`, aplica corte temporal (`sportTime` e `knownAt`) e persiste cada calculo como fato imutavel via `OddsMarketRepository`.
- O snapshot de entrada e deterministico e JSONB valido, preservando a linhagem das versoes confirmadas sem ler volume de apostas.

## Evidencia

- Focal de modelo/calibracao: `24/24`, zero falhas, erros ou skips.
- Focal PostgreSQL do servico: `4/4`, zero falhas, erros ou skips.
- Spotless: `spotless:apply` e `spotless:check` passaram.
- Suite Node agregada: `699/699` em 9 suites, zero falhas, erros, skips ou cancelamentos.
- Suite Maven agregada: `602/602`, `BUILD SUCCESS`, zero falhas, erros ou skips.

## Escopo e limites

- Foram alterados somente os oito componentes Java e os dois testes autorizados pelo plano.
- Nenhuma migration, contrato OpenAPI, frontend, aposta, liquidacao ou infraestrutura foi alterada.
- A aplicacao calcula e persiste fatos; publicacao de odds, comandos administrativos e exposicao HTTP permanecem nos planos adjacentes.
- A calibracao usa exclusivamente os vetores independentes aprovados em 04-12; a captura exploratoria LAUFU continua fora de producao.

## Snapshot SHA-256

Os hashes do snapshot sao derivados do commit tecnico `f741e21`; a verificacao de integridade do commit foi concluida com `git diff --cached --check`.

## Git

- Commit tecnico: `f741e21`.
- Commit documental: `PENDING` neste arquivo para evitar auto-referencia circular.
- Integracao e push remoto serao executados apos o fechamento documental.
