---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "12"
status: complete
completed_at: "2026-09-07"
requirements: [ODDS-02, ODDS-03, ODDS-04, ODDS-06, ODDS-07, ODDS-08]
base_commit: e896158
technical_commit: 672fdd8
documentation_commit: current documentation commit (hash intentionally omitted)
---

# Plano 04-12 - Resumo

## Entrega

- Criados seis vetores golden independentes para calibracao: Elo inicial, K32/K16, regressao de edicao, Davidson, confianca e margem/arredondamento.
- Criado `OddsCalibrationFixtureTest` sem imports do motor futuro, sem reflexao sobre runtime e sem consumo da captura LAUFU.
- Cada vetor possui digest SHA-256 de uma entrada canonica explicita; o teste prova rejeicao de digest adulterado.
- A margem de 5% permanece separada da probabilidade justa; duas casas sao exigidas, mas o modo de arredondamento permanece explicitamente aberto.

## Evidencia

- `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsCalibrationFixtureTest test`: `BUILD SUCCESS`, `16/16`, zero falhas, erros ou skips.
- Os seis arquivos permanecem sob `backend/src/test/resources/odds/golden/`.
- A fixture explicita `usesLaufu=false` e autoridade `owner_ratified_parameter_set`.
- Certificacao agregada da Wave 3: Node `699/699` em 9 suites; Maven `592/592`, sem falhas, erros ou skips.

## Snapshot SHA-256

Hashes normalizados para LF, independentes do checkout Windows:

- `OddsCalibrationFixtureTest.java`: `0F78B17EE4BF709FCD71B8B57D7B3E486E13AB1A49BBE80375C75C1398FE76DA`
- `elo-1500.json`: `CABB43D13E400C1F2EE48BED55760E7F84E0FC0766B7BA4B45FED5C6D674DF55`
- `elo-k-threshold.json`: `0FD84735375B72EFF608719271F088C145719180BF5179F415500FCA13CC079F`
- `edition-regression-50.json`: `31A883BBD7EA09FA7A3A4A0A8F16C83932F15CF9B042A492A8926F4FA57F54B1`
- `davidson-draw.json`: `E81FF01DB728C2BAE62C6EB08AF1E4EB3D2FCD1B755E7064447ED05FF9F93B49`
- `confidence-bands.json`: `64F8B258001A132054322629E116570B184D8829CE9ACEC334B8D4D74052F692`
- `margin-rounding.json`: `7D3BE237D5A84702079C9D004A0223AB016CD854D59E61EFCA38E63456DC3AAF`

## Limites

- Os vetores sao oraculos de teste, nao implementam pricing nem selecionam parametros novos.
- O limiar exato de transicao K e deixado para o gate de calibracao; o vetor fixa somente os modos K32/K16.
- O prior Davidson, os limiares numericos de confianca e o modo de arredondamento permanecem abertos; nenhum foi escolhido por este plano.
- O motor, backtest real, persistencia, API e UI pertencem a planos posteriores.
