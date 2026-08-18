# Plano 01-14 — Tema escuro, tokens de acessibilidade e tipografia auto-hospedada

- **Fase:** 01-funda-o-segura-da-plataforma
- **Wave:** 24
- **Dependência:** 01-26
- **Status:** Concluído com sucesso (após incorporação dos achados da reauditoria externa)

---

## 1. O que foi entregue

Este plano materializou o sistema visual e a tipografia base da plataforma BETUFU no frontend Angular, atendendo rigorosamente a **D-01**, **OPS-02** e **01-UI-SPEC.md**:

1. **Tokens SCSS Oficiais (`frontend/src/styles/_tokens.scss`):**
   - **Cores & Superfícies (Dark Theme Exclusivo):** `$color-canvas: #0B1220` (60%), `$color-surface: #142033` (30%), `$color-surface-raised: #1C2C42`, `$color-accent: #FF8A3D` (10%), `$color-on-accent: #0B1220`, `$color-text: #F8FAFC`, `$color-text-muted: #C5D1E0`, `$color-border: #52657D`, `$color-focus: #FFC08A`, `$color-available: #56D19C`, `$color-degraded: #F6C453`, `$color-unavailable: #FF6B6B`.
   - **Escala de Espaçamento:** Múltiplos estritos de 4px (`space-1` = 4px até `space-16` = 64px).
   - **Raios de Borda:** 4px (chips), 8px (cartões, campos e botões), 999px (avatar).
   - **Breakpoints:** Mobile (320–599px), Tablet (600–899px), Desktop (900px+ com contêiner máximo de 1200px).
2. **Tema Dark Modular (`frontend/src/styles/_material-theme.scss`):**
   - Mixin `@mixin apply-theme()` estruturado para estilizar cartões e botões com as cores e raios do contrato.
3. **Estilos Globais e Acessibilidade (`frontend/src/styles.scss`):**
   - Reset global, aplicação do fundo `#0B1220` e texto `#F8FAFC` no `html`/`body`.
   - Foco acessível visível de 3px (`outline: 3px solid #FFC08A; outline-offset: 3px`) em `:focus-visible`.
   - Alvos mínimos de toque de 44×44px (`min-height: 44px; min-width: 44px`) em botões e links.
   - Suporte a `prefers-reduced-motion` para anular transições/animações para usuários sensíveis.
4. **Tipografia Auto-Hospedada (`frontend/src/styles/_fonts.scss` e `frontend/src/assets/fonts/`):**
   - Reúso **byte a byte** dos 5 arquivos canônicos aprovados no Gate 1 do Plano 01-03 (sem download de rede):
     - `inter-regular.woff2` (SHA-256 `e06f6b1bc553aaea4e4668023ed0ab0a147129c3107f511bc7d03d361b0ae085`)
     - `inter-semibold.woff2` (SHA-256 `5cb7103e4e605989afebc03d989c79201e54b21b5183db33981f70db9178a301`)
     - `barlow-condensed-semibold.woff2` (SHA-256 `21a97c42410e968fbb5bd5d413ad1da01c21d1efce0eb51f03cdcbc899c51459`)
     - `inter-ofl.txt` (SHA-256 `262481e844521b326f5ecd053e59b98c8b2da78c8ee1bdbb6e8174305e54935a`)
     - `barlow-condensed-ofl.txt` (SHA-256 `186d750eb496a4c17a76385f82be6aea2ac1cf2de074a811d63786cf374ea73f`)
   - Declaração `@font-face` local com `font-display: swap`.
5. **Configurações de Empacotamento e Repositório:**
   - `.gitattributes`: adicionadas regras para fontes binárias `.woff2` e licenças `text eol=lf`.
   - `frontend/angular.json`: configurado mapeamento de assets para o bundle `dist/betufu/browser/assets/fonts/`.

---

## 2. Reconciliação e Ratificação de Escopo da Allowlist

- **Ampliações Aprovadas:**
  - `.gitattributes`: inclusão de regras `binary` para `frontend/src/assets/fonts/*.woff2` e `text eol=lf` para `-ofl.txt` para prevenir corrupção no Windows por `core.autocrlf`.
  - `frontend/angular.json`: inclusão de `src/assets` no mapeamento de build do Angular CLI.
  - `.planning/ROADMAP.md` e `.planning/STATE.md`: atualização de progresso da Fase 1 (26/31 concluídos — 84%).
- **Omissão Deliberada de `material-symbols-rounded.woff2`:**
  - O `01-14-PLAN.md` declarava originalmente `frontend/src/assets/fonts/material-symbols-rounded.woff2` em `files_modified`.
  - O Gate 1 do Plano 01-03 aprovou e forneceu apenas os 5 arquivos de fontes (Inter e Barlow). O binário do Material Symbols Rounded não possuía origem/checksum fixados no Gate 1.
  - Para não realizar download não auditado em tempo de execução nem introduzir dependências externas sem checkpoint específico, a entrega de fontes desta wave limitou-se estritamente aos 5 ativos canônicos aprovados, operando 100% offline. A estratégia para ícones de diagnóstico e layout fica preservada para as waves de montagem visual (01-27 / 01-15).

---

## 3. Evidências de Testes, Contraste e Discriminação

### Testes de Acessibilidade e Contraste (`theme.spec.ts`)
O teste `theme.spec.ts` lê diretamente os arquivos `_tokens.scss` e `styles.scss` via `fs.readFileSync`, extrai as variáveis reais por regex e calcula a fórmula matemática de luminância relativa do padrão W3C/WCAG 2.2:

| Combinação de Cores | Papel | Razão Medida Exata | Piso Exigido | Status |
|---|---|---|---|---|
| `#F8FAFC` sobre `#0B1220` | Texto normal sobre canvas | **17.50:1** | $\ge 4.5:1$ | APROVADO |
| `#F8FAFC` sobre `#142033` | Texto normal sobre surface | **15.63:1** | $\ge 4.5:1$ | APROVADO |
| `#F8FAFC` sobre `#1C2C42` | Texto normal sobre raised surface | **13.49:1** | $\ge 4.5:1$ | APROVADO |
| `#0B1220` sobre `#FF8A3D` | Texto on-accent sobre botão primário | **7.98:1** | $\ge 4.5:1$ | APROVADO |
| `#C5D1E0` sobre `#0B1220` | Texto secundário (muted) | **12.10:1** | $\ge 4.5:1$ | APROVADO |
| `#FFC08A` sobre `#0B1220` | Foco visível sobre canvas | **11.73:1** | $\ge 3.0:1$ | APROVADO |
| `#56D19C` sobre `#0B1220` | Estado Disponível | **9.80:1** | $\ge 3.0:1$ | APROVADO |
| `#F6C453` sobre `#0B1220` | Estado Degradado | **11.54:1** | $\ge 3.0:1$ | APROVADO |
| `#FF6B6B` sobre `#0B1220` | Estado Indisponível | **6.75:1** | $\ge 3.0:1$ | APROVADO |

### Provas de Discriminação por Mutação
- **M-14A (Mutação de Cor em `_tokens.scss`):** Alteração de `$color-canvas: #0B1220` para `#FFFFFF` → **VERMELHO** (3 falhas imediatas no Vitest em extração, contraste normal e contraste de estados).
- **M-14B (Remoção de Foco em `styles.scss`):** Remoção do bloco `:focus-visible` de `styles.scss` → **VERMELHO** (1 falha imediata no Vitest ao inspecionar seletores globais).

---

## 4. Execução Completa de Suítes Agregadas

1. **Frontend (Vitest):** 4 suítes / 16 testes verdes (`theme.spec.ts`, `generated-client.contract.spec.ts`, `tracer-page.component.spec.ts`, `app.component.spec.ts`).
2. **Frontend Build (`ng build --configuration production`):** Compilação de produção concluída com sucesso; 5 ativos locais empacotados em `dist/betufu/browser/assets/fonts/`.
3. **Validadores de Governança Node (`node --test scripts/*.test.mjs`):** 5 arquivos / 481 testes verdes.
4. **Backend Agregado (`node scripts/maven.mjs -f backend/pom.xml -o clean verify`):** 27 classes / 192 testes verdes (Surefire: 79 testes / Failsafe: 113 testes, 0 falhas, 0 erros, 0 ignorados).

---

## 5. Arquivos Entregues

- `.gitattributes`
- `frontend/angular.json`
- `frontend/src/styles.scss`
- `frontend/src/styles/_tokens.scss`
- `frontend/src/styles/_material-theme.scss`
- `frontend/src/styles/_fonts.scss`
- `frontend/src/styles/theme.spec.ts`
- `frontend/src/assets/fonts/barlow-condensed-ofl.txt`
- `frontend/src/assets/fonts/barlow-condensed-semibold.woff2`
- `frontend/src/assets/fonts/inter-ofl.txt`
- `frontend/src/assets/fonts/inter-regular.woff2`
- `frontend/src/assets/fonts/inter-semibold.woff2`
