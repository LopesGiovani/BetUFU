# Plano 01-27 — Estrutura inicial autenticada, store de sessão e navegação segura

- **Fase:** 01-funda-o-segura-da-plataforma
- **Wave:** 25
- **Dependência:** 01-14
- **Status:** Concluído com sucesso

---

## 1. O que foi entregue

Este plano materializou o gerenciamento de estado de sessão reativo no Angular e a casca/layout inicial autenticada da plataforma BETUFU, atendendo rigorosamente a **D-01**, **D-08**, **D-09**, **D-16**, **IDEN-03**, **IDEN-04** e **01-UI-SPEC.md**:

1. **SessionStore Reativo (`frontend/src/app/core/session/session.store.ts`):**
   - Gerenciado exclusivamente por **Angular Signals** (`status`, `displayName`, `idleTimeout`, `endSessionUrl`, `error`).
   - Injeção exclusiva de `SessionService` do cliente OpenAPI gerado pelo 01-26.
   - Chamada `loadSession()` pontual (zero polling, zero loops e zero retry automático).
   - Chamada `logoutCurrentDevice()` chamando `logoutCurrentSession()` (POST único, sem `logout-all`) e limpando os dados em memória.
   - **Isolamento de Segurança (D-08, D-09, D-16):** Zero chamadas a `localStorage`, `sessionStorage`, `IndexedDB`, `document.cookie` ou manipulação manual de cabeçalho `Bearer`.
2. **Componente de Layout Autenticado (`frontend/src/app/layout/authenticated-layout.component.*`):**
   - Transição e renderização acessível dos 4 estados de sessão:
     - `checking`: Exibe container com `"Preparando sua sessão…"` (`role="status"` e `aria-live="polite"`), sem expor conteúdo protegido ou rotas filhas.
     - `authenticated`: Exibe `<header>` com marca BETUFU, identificador não sensível (`displayName`) e botão `Sair` (`min-height/min-width: 44px`). Exibe `<main id="main-content" tabindex="-1">` com `<router-outlet />`.
     - `expired`: Oculta rotas filhas e exibe aviso claro de sessão encerrada com CTA para login.
     - `logged_out`: Redireciona para `endSessionUrl` pública se presente ou exibe confirmação de saída.
   - **Acessibilidade:** Skip link `<a href="#main-content" class="skip-link">Pular para o conteúdo principal</a>`, suporte a foco de 3px (`#FFC08A`), alvos mínimos de 44px e múltiplos estritos de 4px nos tokens de espaçamento.
3. **Roteamento Build-Specific Protegido (`frontend/src/app/app.routes.development.ts`):**
   - Encapsula as rotas de desenvolvimento (`/tracer`) sob o `AuthenticatedLayoutComponent`, mantendo `app.routes.production.ts` com `BUILD_SPECIFIC_ROUTES = []` por substituição de build.
4. **Validador de Fase 1 e Governança:**
   - Implementado modo `--session-ui` em `scripts/validate-phase1.mjs`, validando estaticamente isolamento de storage/polling e executando Vitest e build de produção.
   - Atualizado `scripts/validate-angular-route-boundary.mjs` para aceitar a casca `AuthenticatedLayoutComponent` na regra `DEVELOPMENT_ROUTES_SHAPE`.

---

## 2. Reconciliação e Ratificação de Escopo da Allowlist

- **Ampliações Aprovadas:**
  - `scripts/validate-phase1.mjs`: implementação e habilitação da flag `--session-ui`.
  - `scripts/validate-angular-route-boundary.mjs`: atualização do pattern `CANONICAL_LAYOUT_TRACER_ROUTES_PATTERN` para suportar a estrutura hierárquica sob `AuthenticatedLayoutComponent`.
  - `.planning/ROADMAP.md` e `.planning/STATE.md`: avanço de progresso da Fase 1 (27/31 concluídos — 87%).

---

## 3. Evidências de Verificação e Discriminação

### Testes Focais Frontend (`session-layout.spec.ts` & `theme.spec.ts`)
- 5 arquivos de teste / **27 testes verdes** no Vitest (`theme.spec.ts`, `session-layout.spec.ts`, `generated-client.contract.spec.ts`, `tracer-page.component.spec.ts`, `app.component.spec.ts`).

### Prova de Discriminação por Mutação
- **M-27A (Injeção de `localStorage.setItem` no store):** **VERMELHO** (2 falhas imediatas no Vitest ao detectar a escrita em storage e o identificador no arquivo).

### Validador do Gate da Fase 1 (`node scripts/validate-phase1.mjs --session-ui`)
- PASS: Verificação de isolamento OIDC recursiva em todos os arquivos de `core/session` e `layout`, 27 testes Vitest e compilação de produção com `ng build`.

### Suíte Completa de Validadores Node (`node --test scripts/*.test.mjs`)
- 5 arquivos (`reset-local.test.mjs`, `validate-angular-route-boundary.test.mjs`, `validate-keycloak-realm.test.mjs`, `validate-runtime-prerequisites.test.mjs`, `validate-phase1.test.mjs`) / **496 testes verdes** (incluindo 154 testes de `validate-angular-route-boundary.test.mjs` com regressões negativas para `AuthenticatedLayoutComponent` e 10 testes de `validate-phase1.test.mjs`).

### Suíte Agregada Backend (`node scripts/maven.mjs -f backend/pom.xml -o clean verify`)
- 28 classes / **192 testes verdes** (Surefire: 79 testes / Failsafe: 113 testes, 0 falhas, 0 erros, 0 ignorados).

---

## 4. Arquivos Entregues

- `frontend/src/app/core/session/session.store.ts`
- `frontend/src/app/layout/authenticated-layout.component.ts`
- `frontend/src/app/layout/authenticated-layout.component.html`
- `frontend/src/app/layout/authenticated-layout.component.scss`
- `frontend/src/app/layout/session-layout.spec.ts`
- `frontend/src/app/app.routes.development.ts`
- `scripts/validate-phase1.mjs`
- `scripts/validate-phase1.test.mjs`
- `scripts/validate-angular-route-boundary.mjs`
- `scripts/validate-angular-route-boundary.test.mjs`

