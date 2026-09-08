---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "02"
status: complete
completed_at: "2026-09-07"
requirements: [ODDS-01, ODDS-04, ODDS-13]
base_commit: 0daddee711fb
technical_commit: 1caa579
documentation_commit: 87e2817
---

# Plano 04-02 - Resumo

## Entrega

- Registrada a aprovação T2 da fronteira `sports` -> `odds`, com contrato público pertencente a `sports` e sem imports de `sports.internal` pelo módulo `odds`.
- Implementados `SportsResultForOddsPort`, `SportsEventForOdds`, `ConfirmedSportsResultForOdds` e `SportsResultKindForOdds` como contratos públicos imutáveis.
- Implementados `MarketState` e `MarketTransition` com transições estritas, deadline de fechamento e rejeição de saltos não autorizados.
- Mantida a separação entre resultado esportivo com placar e fatos administrativos/lifecycle sem placar sintético.
- Adicionados testes ArchUnit para fronteira modular e testes Java para taxonomia, projeção H2H e máquina de estados.

## Evidência

- `node --test scripts/odds-contract.test.mjs`: `25/25`.
- `node --test scripts/*.test.mjs`: `699/699` em 9 suítes.
- `node scripts/maven.mjs -f backend/pom.xml "-Dtest=OddsContractArchitectureTest,SportsOddsBoundaryArchitectureTest" test`: `12/12`.
- `node scripts/maven.mjs -f backend/pom.xml test`: `576/576`, sem falhas, erros ou skips.
- Gate documental: `node scripts/odds-contract.test.mjs --require-approval` retornou `T2=approved` com `258` verificações.

## Decisões e limites

- A taxonomia aprovada é `SPORTING_RESULT`, `ADMINISTRATIVE_WALKOVER`, `ABANDONED_NO_VALID_RESULT`, `CANCELLED` e `POSTPONED`.
- `CONFIRMADO` e `CORRIGIDO` são os únicos status de versão admissíveis no replay do resultado esportivo.
- A persistência, o produtor Spring concreto, pricing, settlement, migrations, OpenAPI e UI permanecem nos planos próprios.
- O gate 04-01/T2 continua fail-closed por D-120; a captura LAUFU não é promovida para calibração ou produção.

## Paths revisados

- `scripts/odds-contract.test.mjs` - `E193D0D75B6A5A9B62D45CD5590E92F0D5EF33D01D724413C880E96E7C642FEB`
- `scripts/odds-state-machine-fixtures.json` - `F648766A028CC55A49ECB8778A6A88896614E878FB1499B6A61CA3E6F7E1BB1A`
- `backend/src/main/java/br/com/betufu/sports/SportsResultForOddsPort.java` - `2CA6A44124BF7FF68464E66BDBEB96DF08A4369081D28AA1110FE2F1C7043F26`
- `backend/src/main/java/br/com/betufu/sports/SportsEventForOdds.java` - `5F2A18ABF294004FA3943B5B370B2428F1B4ABB4B2ACE879B243B72591F22DE1`
- `backend/src/main/java/br/com/betufu/sports/ConfirmedSportsResultForOdds.java` - `7A4BB418426B1CC3B575902670CCB2BA58C64575C66867F8F0D2D96F0D35227A`
- `backend/src/main/java/br/com/betufu/sports/SportsResultKindForOdds.java` - `D9530954A3861BC37AA37EF1952B9A00A6EBA95CF104FCFB78137C51931EF9A6`
- `backend/src/main/java/br/com/betufu/odds/internal/market/MarketState.java` - `03DF1CC0955617C94A3BB436598474BE4B6053F35F918E84C3FE2E4228A8D605`
- `backend/src/main/java/br/com/betufu/odds/internal/market/MarketTransition.java` - `2F1257C1D5CE7A06A33CD3685E4CC5FEC44D1ED758771807F8F7BFC7ECEE4EE9`
- `backend/src/test/java/br/com/betufu/odds/OddsContractArchitectureTest.java` - `F3D962FDE8AB2F38CCB6D6A13C4DE03F1B88375F4A20C7EEB7F690A5ABC6172B`
- `backend/src/test/java/br/com/betufu/sports/SportsOddsBoundaryArchitectureTest.java` - `3057EE83A65088731462A8D2A4B36FBAFC62F0E78602B99924DDF314A6BE925F`
