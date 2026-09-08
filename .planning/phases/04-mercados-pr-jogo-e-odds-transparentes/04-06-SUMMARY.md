---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-06"
wave: 12
status: complete
completed_at: "2026-09-08"
base_commit: 02e2fd9
technical_commit: PENDING
documentation_commit: PENDING
requirements: [ODDS-10, ODDS-11, ODDS-12, ODDS-13]
---

# Plano 04-06 - Resumo

## Entrega

- Implementada `OddsOverridePolicy` com pré-condição de mercado `SUSPENDED`, tentativa de correção verificável e maker-checker obrigatório.
- `OddsCorrectionAttempt` referencia um cálculo imutável persistido e transporta odds anterior, proposta, delta e evidência; nenhuma tabela ou migration nova foi criada.
- `OddsOverrideService` revalida identidade e `competitionId` no servidor, rejeita autoaprovação/escopo alheio/estado final/stale lock e usa decisões `PROPOSED` e `APPROVED` como fatos append-only.
- Aprovação cria nova versão de mercado, novo cálculo e nova publicação antes de avançar a cabeça com compare-and-set; fatos anteriores permanecem intactos.
- `OddsOverrideController` expõe comandos administrativos autenticados e mapeia entradas inválidas para Problem Details sanitizados.

## Evidência

- Focal override + auditoria: `34/34`, zero falhas, erros ou skips.
- Spotless `apply` e `check`: passaram.
- Suíte Maven completa: `646/646`, `BUILD SUCCESS`, zero falhas, erros ou skips.
- Suíte Node agregada: `719/719` em 9 suites, zero falhas, erros, skips ou cancelamentos.
- A primeira tentativa do focal falhou somente por ausência das variáveis efêmeras de teste HMAC; a repetição com `v1` e chave de fixture do repositório passou integralmente.
- RepoWise foi consultado primeiro, mas o transporte encerrou (`Transport closed`); a validação estrutural foi concluída por inspeção nativa, compilação e testes.

## Escopo e limites

- Alterados somente os cinco componentes de produção e três testes autorizados; nenhuma migration, dependência, OpenAPI, frontend, betting, ledger ou settlement.
- O armazenamento existente `odds_override` mantém a trilha de propostas/aprovações; a tentativa é comprovada pelo cálculo imutável referenciado.
- Não há atualização ou exclusão de odds, cálculo, publicação, lifecycle ou auditoria.

## Git

- Commit técnico: `PENDING` neste arquivo para evitar auto-referência circular.
- Commit documental: `PENDING` neste arquivo para evitar auto-referência circular.
