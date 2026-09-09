---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "11"
wave: 14
status: complete
completed_at: "2026-09-08"
base_commit: 5ac47a9
technical_commit: 606be78
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13]
---

# Plano 04-11 - Resumo

## Entrega

- Cliente TypeScript Angular gerado pelo fluxo oficial a partir do contrato OpenAPI ratificado no Plano 04-07.
- Manifesto do gerador fixado no digest `51E5582AEA63F066EE6D80E552663C70269BB8385EB53EA2D31716E37DAC3B92`.
- Arvore `frontend/src/app/api/generated` reproduzivel com 159 arquivos, incluindo os servicos de mercado, override e consulta publica de odds.
- Barrels `api/api.ts` e `model/models.ts`, alem de `operational-capability.ts`, foram atualizados pelo gerador; `index.ts` permanece o barrel raiz estavel.
- Allowlist do plano alinhada por ratificacao formal de 2026-09-08 com os 16 novos paths produzidos pelo inventario ratificado.

## Provas de contrato

- `frontend/scripts/generate-api.test.mjs` fixa o inventario dos 16 outputs de odds e o digest ratificado, preservando as provas existentes de drift, arquivos extras, corrupcao e publicacao atomica.
- `frontend/src/app/api/generated-client.contract.spec.ts` prova a leitura publica tipada (estado, odd atual, historico, explicacao e confianca) sem `Authorization`, cookie, XSRF ou dados privados.
- O mesmo spec prova os comandos operacionais de mercado e override com rotas relativas, corpos tipados, `Content-Type`, XSRF e `Idempotency-Key`.

## Verificacao

| Comando | Resultado |
|---|---|
| `node frontend/scripts/generate-api.mjs --check` | PASS |
| `node --test frontend/scripts/generate-api.test.mjs` | PASS - 73/73 |
| `npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts` | PASS - 14/14 |
| `npm --prefix frontend run test -- --watch=false` | PASS - 58 arquivos, 351 testes |
| `git diff --check` | PASS |

## Governanca e desvios

- O launcher Antigravity nao foi usado, conforme dispensa formal do responsavel.
- A worktree `worktree-agent-04-11` foi restaurada em `5ac47a9` porque nao existia no preflight; a restauracao foi local e reversivel.
- O JAR OpenAPI Generator 7.24.0 foi restaurado somente no cache ignorado e validado com SHA-256 `4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C`.
- `npm ci` usou o lockfile existente; o npm reportou 3 vulnerabilidades moderadas e scripts pendentes, sem executar `npm audit fix` ou aprovar scripts.
- Nenhum arquivo gerado foi editado manualmente; o cliente nasceu integralmente do script oficial.

## Commits

- Implementacao: `606be78 feat(04-11): generate TypeScript client from ratified OpenAPI contract`.
- Formalizacao: `PENDING docs(04-11): formalize plan 04-11 completion`.
