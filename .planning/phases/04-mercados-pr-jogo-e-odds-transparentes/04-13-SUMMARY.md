---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "13"
wave: 15
status: complete
completed_at: "2026-09-08"
base_commit: df6a7cf
technical_commit: 4856861
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-07, ODDS-08, ODDS-09]
---

# Plano 04-13 - Resumo

## Entrega

- Pagina publica de odds Angular implementada com a cadeia `OddsPublicService -> PublicMarketApiAdapter -> PublicMarketStore -> PublicMarketPageComponent`.
- O adapter valida o DTO gerado, aceita somente mercados `HEAD_TO_HEAD`, normaliza odd atual, historico, explicacao e confianca e rejeita respostas incompletas antes da renderizacao.
- O store usa Signals, controla `loading`, `populated`, `empty`, `stale` e `error`, ignora respostas fora de ordem e permite retry somente da leitura GET idempotente.
- A pagina standalone OnPush apresenta odd atual, historico imutavel, metodologia, margem, arredondamento, baixa confianca, suspensao e estados de erro/vazio com navegacao acessivel por teclado.
- A rota publica anonima `odds/mercados/:marketId` foi registrada em development e production e espalhada na `ArenaShell` antes da raiz protegida por perfil. Nenhuma rota ou componente operacional do Plano 04-14 foi importado.

## Transparencia e privacidade

- A superficie publica usa apenas `PublicOddsView` e seus campos publicos; nao renderiza ator, evidencia privada, matricula, HMAC, token, saldo, aposta, liquidez ou volume.
- A explicacao e exibida em duas camadas e a interface usa copy em portugues para baixa confianca, mercado suspenso, mercado nao publicado e falha recuperavel.
- Os testes verificam estados populados, vazios/404, erro, stale, retry, historico, disclosure, responsividade sem perda de informacao e ausencia de dados sensiveis.

## Fronteira de rotas

- `scripts/validate-angular-route-boundary.mjs` agora ratifica `PUBLIC_MARKET_ROUTES` como lista publica separada, exige a rota literal em development e production e verifica o spread unico na Arena sem atravessar o `profileCompletionGuard`.
- `scripts/validate-angular-route-boundary.test.mjs` recebeu a fixture canonica e preserva as mutacoes negativas de import lazy, layout, allowlist, duplicacao e rotas eager.
- A inclusao dos dois scripts e uma excecao mecanica do `AGENTS.md`: a allowlist fechada precisava reconhecer a rota publica legitima. Nao houve decisao de produto, dependencia nova ou alteracao de infraestrutura.

## Verificacao

| Comando | Resultado |
|---|---|
| `node --check scripts/validate-angular-route-boundary.mjs` | PASS |
| `node --check scripts/validate-angular-route-boundary.test.mjs` | PASS |
| `node --test scripts/validate-angular-route-boundary.test.mjs` | PASS - 173/173 |
| `npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts --include src/app/app.routes.spec.ts --include src/app/features/odds/public-market/public-market-page.component.spec.ts` | PASS - 3 arquivos, 25 testes |
| `npm --prefix frontend run test -- --watch=false` | PASS - 59 arquivos, 357 testes |
| `node scripts/validate-angular-route-boundary.mjs` | PASS - fronteira Angular segue o contrato aprovado |
| `git diff --check` / `git diff --cached --check` | PASS |

Os builds Angular repetem cinco avisos historicos `NG8102` em `sports-event-result-page.component.html`, fora da allowlist deste plano; nao houve falhas, erros, skips ou cancelamentos.

## Governanca e desvios

- O launcher Antigravity nao foi usado, conforme dispensa formal do responsavel; a implementacao ocorreu diretamente em `worktree-agent-04-13` sobre `df6a7cf`.
- A worktree precisou de `npm ci` porque `node_modules` nao existia. O lockfile aprovado foi preservado; o npm reportou tres vulnerabilidades moderadas, sem `npm audit fix` ou mudanca de dependencia.
- Nenhum arquivo gerado do cliente OpenAPI foi editado manualmente. A pagina consome o cliente ratificado do Plano 04-11, cujo digest permanece `51E5582AEA63F066EE6D80E552663C70269BB8385EB53EA2D31716E37DAC3B92`.

## Commits

- Implementacao: `4856861 feat(04-13): implement public odds market page, adapter and routes`.
- Formalizacao: `PENDING docs(04-13): formalize plan 04-13 completion`.

