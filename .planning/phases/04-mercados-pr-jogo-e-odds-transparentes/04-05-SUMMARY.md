---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-05"
wave: 11
status: complete
completed_at: "2026-09-08"
base_commit: 3e15f33
technical_commit: PENDING
documentation_commit: PENDING
requirements: [ODDS-01, ODDS-04, ODDS-08, ODDS-09, ODDS-13]
---

# Plano 04-05 - Resumo

## Entrega

- Conectado o lifecycle independente de mercados H2H: criação em `DRAFT`, publicação condicionada a roster publicado e transições validadas por `MarketTransition`.
- Reutilizado `odds.internal.persistence.OddsMarketRepository`, com versões append-only, cabeça corrente única e controle otimista de `lock_version`.
- Publicação cria cálculo e preço imutáveis; `CANCELLED` é traduzido para `VOID` no schema V25 sem reescrever histórico.
- Criada projeção pública anônima em duas camadas (resumo e explicação), expondo apenas odds, probabilidade justa, margem, arredondamento, confiança e linhagem algorítmica segura.
- Liberadas somente as rotas GET `/api/v1/public/odds/**` para acesso anônimo; comandos administrativos continuam autenticados.

## Evidência

- Focal `OddsMarketIT, OddsMarketLifecycleIT, OddsPublicViewIT, OddsPublicPrivacyIT`: `8/8`, zero falhas, erros ou skips.
- Spotless `apply` e `check`: passaram.
- Suíte Maven completa: `646/646`, `BUILD SUCCESS`, zero falhas, erros ou skips.
- RepoWise foi tentado, mas o transporte encerrou (`Transport closed`); a análise estrutural foi validada por leitura nativa e compilação/testes.

## Escopo e limites

- Nenhuma migration nova, dependência, frontend, aposta, crédito ou settlement foi criado.
- A abertura depende da porta pública de sports e do binding de roster; evento `GENERIC` é recusado.
- O DTO público não contém actor IDs, matrícula, HMAC, digest de configuração, evidência operacional ou notas internas.

## Git

- Commit técnico: `PENDING` neste arquivo para evitar auto-referência circular.
- Commit documental: `PENDING` neste arquivo para evitar auto-referência circular.
