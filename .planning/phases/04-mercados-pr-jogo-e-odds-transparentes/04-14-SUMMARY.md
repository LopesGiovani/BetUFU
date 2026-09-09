---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "14"
wave: 16
status: complete
completed_at: "2026-09-09"
base_commit: c0e40fc
technical_commit: 4ae0744
documentation_commit: PENDING
requirements: [ODDS-09, ODDS-10, ODDS-11, ODDS-12, ODDS-13]
---

# Plano 04-14 - Resumo

## Entrega

- Criada a cadeia operacional `OddsMarketService/OddsOverrideService -> OddsOperationsApiAdapter -> OddsOperationsStore -> OddsOperationsPageComponent`.
- A pagina exibe lifecycle, versao, lock version, suspensao, tentativa de correcao, proposta excepcional e resposta de aprovacao sem transformar capability em autorizacao.
- A rota `/operacao/odds/mercados/:marketId` fica na area protegida por `profileCompletionGuard`, separada da rota publica `/odds/mercados/:marketId`.
- O ConsoleShell filtra o destino pelos quatro nomes ratificados: `VIEW_ODDS_MARKET`, `MANAGE_ODDS_MARKET`, `PROPOSE_ODDS_OVERRIDE` e `APPROVE_ODDS_OVERRIDE`.
- Estados 401/403/409, stale e CLOSED/LOCKED usam mensagens fail-closed; nenhuma mutacao faz retry automatico.

## Evidencia

- Foco Angular de contrato, rotas, Arena publica, pagina operacional, adapter e ConsoleShell: `57/57`.
- Suite Angular completa: primeira execucao `363/364` por timeout transitório em spec fora do escopo; repeticao `364/364`, zero falhas, erros ou skips.
- Playwright `phase4-chromium` (Chrome instalado localmente) + `phase4-firefox`, desktop e 320px: `6/6`.
- `git diff --check`: passou.
- `npm ci` foi usado apenas para restaurar `frontend/node_modules` ausente, sem alterar manifests ou lockfiles.

## Privacidade e governanca

- Os testes publicos nao exibem identidade, matricula, HMAC, token, saldo, aposta ou liquidez; a menção textual a volume permanece somente como explicacao de que ele nao entra no calculo.
- A UI nao inventa capability, nao oferece criacao de mercado e nao considera checkbox local como prova de correcao; o servidor continua decidindo escopo, pre-condicoes e maker-checker.
- A API gerada permanece intocada manualmente; headers de mutacao e `Idempotency-Key` sao fornecidos pelo cliente oficial.

## Limites e desvios

- `node scripts/validate-angular-route-boundary.mjs` retornou `DYNAMIC_IMPORT_FORBIDDEN` porque o inventario do validador, fora da allowlist deste plano, ainda nao ratifica a nova importacao lazy operacional. O arquivo nao foi alterado para contornar o fail-closed.
- O contrato OpenAPI atual nao possui endpoint de leitura de propostas pendentes; por isso a tela impede autoaprovacao da proposta criada na sessao local, enquanto a aprovacao backend continua representada pelo cliente tipado e protegida server-side.
- O overflow de texto UUID da Arena publica existente nao foi alterado, pois o componente publico esta fora da allowlist 04-14; a prova 320px valida os containers da pagina operacional e a matriz browser permanece verde.

## Git

- Commit tecnico: `4ae0744`.
- Commit documental: `PENDING` neste arquivo para evitar auto-referencia circular.
