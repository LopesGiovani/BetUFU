---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "04-21"
wave: 18
status: complete
completed_at: "2026-09-12"
technical_commit: d3de1e3
documentation_commit: 681d479
requirements-completed: [AUTH-REDIRECT-01]
---

# Plano 04-21 - Resumo

O login OIDC preserva a prioridade do `SavedRequest` e, quando nao ha rota
protegida salva, direciona perfis operacionais para `/operacao/esportes`.
Usuarios sem papel operacional continuam na Arena (`/`). Os destinos sao
constantes internas; nenhum parametro, `Referer` ou URL externa do cliente e
aceito, evitando open redirect.

## Integracao

A autorizacao continua no backend e usa somente as authorities operacionais
existentes. A alteracao nao cria capability, nao altera Keycloak, OpenAPI,
migrations ou frontend.

## Git

- Implementacao: `d3de1e3 fix(auth): redirect operational users to console after login`.
- Planejamento/documentacao: `681d479 docs(04-21): plan administrative post-login redirect`.
