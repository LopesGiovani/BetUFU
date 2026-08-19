# 01-28 SUMMARY: Roteamento Técnico Selado e Ausência em Produção

**Data:** 2026-08-19  
**Fase:** 01 — Fundação Segura da Plataforma  
**Plano:** 01-28 (Wave 27)  
**Status:** Concluído com Sucesso  

---

## 1. Escopo Entregue

Neste plano, selamos as fronteiras de roteamento entre os ambientes de desenvolvimento e produção, registrando as rotas técnicas sob `AuthenticatedLayoutComponent` e garantindo por testes automatizados a ausência material e estrita de recursos não-prod no bundle de produção:

1. **Roteamento de Desenvolvimento (`app.routes.development.ts`):**
   - Registradas as rotas `/tracer` e `/diagnostic` com *lazy-loading* (`loadComponent`).
   - Encapsulamento estrito das rotas sob o layout de autenticação (`AuthenticatedLayoutComponent`).
2. **Isolamento de Produção (`app.routes.production.ts`):**
   - Exportação exclusiva de `BUILD_SPECIFIC_ROUTES: Routes = []`.
   - Proibição absoluta de imports, referências, caminhos ou menções a `DiagnosticPageComponent` ou `TracerPageComponent` (D-01 e D-02).
3. **Validador de Fronteiras de Rota (`validate-angular-route-boundary.mjs`):**
   - Atualizado para suportar o padrão canônico com `tracer` e `diagnostic`.
   - Limitação estrita de no máximo 1 import dinâmico para cada feature técnica autorizada.
   - 154 testes unitários de mutação no Node passando 100%.
4. **Modo `--diagnostic-ui` em `validate-phase1.mjs`:**
   - Validação estrita do contrato de rotas (`validateDiagnosticUiContract`).
   - Execução de testes unitários do frontend com Vitest.
   - Compilação de build de produção (`ng build --configuration production`) com inspeção pós-build dos artefatos em `dist/` para assegurar que nenhum chunk, manifest ou HTML vaza componentes técnicos.
5. **Testes de Regressão Negativa (`validate-phase1.test.mjs`):**
   - 18 testes cobrindo recusa de tokens de armazenamento proibidos, polling contínuo e vazamento de rotas em production.

---

## 2. Evidências de Verificação

- **Modo `--diagnostic-ui`:** PASS (contratos, Vitest 46/46, bundle production sem vazamentos).
- **Modo `--all`:** PASS (todos os 4 modos `--infra`, `--contract`, `--session-ui`, `--diagnostic-ui` passaram).
- **Vitest:** 7 arquivos / 46 testes verdes.
- **Node Suites:** 2 arquivos / 172 testes verdes (154 no boundary e 18 no validate-phase1).
- **Prova de Mutação (M-28A):** Injeção de rota de diagnóstico em `app.routes.production.ts` gerou falha imediata no validador.

---

## 3. Próximo Passo

- **Wave 28 — Plano 01-16:** Gate Integral de Fechamento da Fase 1 (execução sem argumentos de `validate-phase1.mjs`).
