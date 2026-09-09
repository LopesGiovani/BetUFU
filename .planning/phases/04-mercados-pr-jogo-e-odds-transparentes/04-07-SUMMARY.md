---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-07"
wave: 13
status: complete
completed_at: "2026-09-08"
base_commit: ea4d2a4
technical_commit: d7e74b5
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13]
---

# Plano 04-07 - Resumo

## Entrega

- O contrato OpenAPI candidato documenta a consulta pública anônima de odds e os comandos administrativos de mercado, lifecycle e override maker-checker.
- A projeção pública segue exatamente `OddsPublicController`: uma única rota GET retorna estado, odd atual, histórico e explicação matemática, sem actor IDs, PII, evidência operacional, volume, liquidez ou exposição.
- O contrato administrativo inclui criação/consulta de mercado, transição de lifecycle, proposta e aprovação de override; `Idempotency-Key` é obrigatório nos dois comandos de override.
- As quatro capabilities de odds foram adicionadas ao enum OpenAPI `OperationalCapability`.
- O schema `SportsRosterSnapshotForOdds` foi sincronizado com o record Java (`teamId`, `competitionId`, `editionId`, `publicationId`, `versionNo`, `knownAt`).
- O gerador foi estendido com tags/modelos de odds e inventário de `operationIds`; nenhum cliente em `frontend/src/app/api/generated` foi gerado ou alterado.

## Evidência do candidato

- Digest SHA-256: `51E5582AEA63F066EE6D80E552663C70269BB8385EB53EA2D31716E37DAC3B92`.
- Inventário: `159` arquivos candidatos, seis endpoints de odds e seis `operationIds` de odds.
- `governed-before.sha256`: `5D6400EF390D0D459B05A0D3CB4C94774271928E16D7DB618968100D59809D9B`.
- `governed-after.sha256`: `5D6400EF390D0D459B05A0D3CB4C94774271928E16D7DB618968100D59809D9B`.
- Verificação dos quatro artefatos e igualdade dos hashes governados: passou.
- `node frontend/scripts/generate-api.mjs --candidate frontend/target/api-candidate/04-07`: passou.
- `node --test frontend/scripts/generate-api.test.mjs`: `72/72`, zero falhas, erros, skips ou cancelamentos.
- `git diff --check`: passou.
- RepoWise foi consultado primeiro, mas o transporte encerrou (`Transport closed`); a revisão foi concluída por inspeção nativa e pelo gerador governado.

## Escopo e limites

- Alterados somente `backend/openapi/betufu.yaml`, `frontend/scripts/generate-api.mjs`, `frontend/scripts/generate-api.test.mjs` e os quatro artefatos candidatos allowlisted.
- A árvore `frontend/src/app/api/generated` permanece byte-intacta; a geração oficial de cliente continua pertencendo aos planos consumidores após este checkpoint.
- Nenhuma migration, regra Java, dependência, UI, aposta, crédito ou settlement foi alterado.

## Git

- Commit técnico: `d7e74b5` (`feat(04-07): candidate OpenAPI specification for odds markets and governance`).
- Commit documental: `PENDING` neste arquivo para evitar auto-referência circular.
