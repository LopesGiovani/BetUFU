---
phase: 01-funda-o-segura-da-plataforma
plan: "19"
subsystem: frontend-routing
tags: [angular, routing, xsrf, bff, vitest, node, build-isolation]

requires:
  - phase: 01-04
    provides: "Workspace Angular 22/TypeScript 6, Matriz B e três configurações de build reproduzíveis"
provides:
  - "Host Angular standalone e zoneless com um único router-outlet neutro"
  - "Seam de rotas build-specific com replacement exclusivo de development para production"
  - "HttpClient same-origin com XSRF e proibição de bearer token ou storage OAuth no navegador"
  - "Validador Node sem dependências com 69 regressões negativas auditáveis"
  - "Builds development, homolog e production e Vitest focal executados com sucesso"
affects: [01-20, 01-21, 01-28, frontend, routing, security, ci]

actuals:
  tokens: 16044
  tasks: 5
  commits: 6

tech-stack:
  added: []
  patterns:
    - "Rotas técnicas entram por uma seam build-specific e production usa file replacement explícito"
    - "O navegador usa sessão BFF e XSRF same-origin, sem construir ou persistir bearer token"
    - "O host Angular inicial permanece neutro e expõe somente um router-outlet"
    - "Validadores arquiteturais falham de modo fechado diante de formas de código fora do subconjunto aprovado"

key-files:
  created:
    - "frontend/src/app/app.routes.ts"
    - "frontend/src/app/app.routes.development.ts"
    - "frontend/src/app/app.routes.production.ts"
    - "scripts/validate-angular-route-boundary.mjs"
    - "scripts/validate-angular-route-boundary.test.mjs"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-19-SUMMARY.md"
  modified:
    - "frontend/angular.json"
    - "frontend/src/app/app.config.ts"
    - "frontend/src/app/app.component.ts"
    - "frontend/src/app/app.component.html"
    - "frontend/src/app/app.component.spec.ts"
    - ".planning/STATE.md"
    - ".planning/ROADMAP.md"

key-decisions:
  - "Development e homolog importam a seam development; somente production a substitui pelo módulo production"
  - "As listas de rotas permanecem vazias neste plano para não antecipar tracer, diagnóstico ou produto"
  - "provideHttpClient usa XSRF-TOKEN e X-XSRF-TOKEN; URL absoluta, bearer, storage OAuth e interceptores de token são proibidos"
  - "O componente raiz contém exatamente um RouterOutlet e nenhum link, formulário ou capacidade futura"
  - "A fronteira estática usa forma canônica fail-closed; ampliar o host exige revisão explícita do contrato e dos testes"

patterns-established:
  - "Build isolation: app.routes.ts importa development e angular.json troca esse módulo exclusivamente em production"
  - "BFF browser boundary: same-origin + XSRF, sem access/refresh token no frontend"
  - "Neutral host: AppComponent standalone com router-outlet único e teste focal de ausência"
  - "Architecture test: fixture canônica mais mutações negativas executadas por Node sem dependências externas"

requirements-completed: [ARCH-01, IDEN-03]

coverage:
  - id: D1
    description: "A seam e o file replacement impedem que rotas técnicas sejam herdadas silenciosamente pelo build production"
    requirement: ARCH-01
    verification:
      - kind: other
        ref: "node scripts/validate-angular-route-boundary.mjs e builds development/homolog/production da Task 5"
        status: pass
    human_judgment: false
  - id: D2
    description: "O HttpClient usa XSRF same-origin e a fronteira rejeita bearer token, storage OAuth e interceptor no navegador"
    requirement: IDEN-03
    verification:
      - kind: unit
        ref: "scripts/validate-angular-route-boundary.test.mjs — 69/69 regressões negativas"
        status: pass
    human_judgment: false
  - id: D3
    description: "O host Angular renderiza exatamente um router-outlet e não antecipa links, credenciais ou capacidades futuras"
    requirement: ARCH-01
    verification:
      - kind: unit
        ref: "frontend/src/app/app.component.spec.ts — Vitest focal, 1 arquivo e 3/3 testes"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com revisão, correções e aprovações por bloco"
completed: 2026-08-11
status: complete
---

# Phase 1 Plan 19: Host Angular e rotas build-specific Summary

**Host Angular neutro com fronteira de rotas isolada por build, HttpClient/XSRF same-origin e validador arquitetural de 69 regressões**

## Desempenho

- **Duração:** não mensurada — execução acompanhada em blocos aprovados
- **Início:** 2026-08-11
- **Conclusão:** 2026-08-11
- **Tasks:** 5
- **Arquivos de implementação:** 10
- **Artefatos de fechamento:** 3 documentos de planejamento
- **Diff realizado:** 64.174 caracteres, equivalentes a 16.044 tokens na escala `chars/4`

## Entregas

- Seam `APP_ROUTES` que consome a lista build-specific sem importar production diretamente.
- Replacement production exato de `app.routes.development.ts` para `app.routes.production.ts`; development e homolog não possuem esse replacement.
- `ApplicationConfig` canônico com Router, HttpClient, XSRF, tratamento global de erros e execução zoneless.
- Componente raiz neutro com um único `<router-outlet />` e teste de ausência de capacidades futuras.
- Validador Node fail-closed, acompanhado por 69 regressões negativas e pelas provas reais dos três builds.

## Commits das tarefas

1. **Task 1 — fronteira Angular e rotas build-specific:** `863be3e` (`feat(01-19): add build-specific Angular route boundary`).
2. **Task 2 — validador arquitetural inicial:** `2571f0e` (`feat(01-19): add Angular route boundary validator`).
3. **Correção documental do critério do host:** `f5437b6` (`docs(01-19): clarify root outlet acceptance`).
4. **Task 3 — host Angular neutro:** `22210de` (`feat(01-19): add neutral Angular router host`).
5. **Endurecimento após revisão da Task 4:** `ccb97f3` (`fix(01-19): harden Angular route boundary validator`).
6. **Fechamento da mutação da lista build-specific:** `6ed2de4` (`fix(01-19): close build-specific route mutation gap`).

Tasks 4 e 5 não alteraram arquivos de implementação: a primeira foi um checkpoint humano; a segunda produziu somente `dist/` e cache Angular ignorados.

## Evidências de verificação

| Prova | Resultado |
|---|---|
| `node --check` nos dois scripts | PASS |
| `node scripts/validate-angular-route-boundary.test.mjs` | PASS — 69/69 |
| `node scripts/validate-angular-route-boundary.mjs` | PASS |
| `npm.cmd run build:development` | PASS — código 0 |
| `npm.cmd run build:homolog` | PASS — código 0 |
| `npm.cmd run build:production` | PASS — código 0 |
| `npm.cmd run test -- --watch=false --include='**/app.component.spec.ts'` | PASS — 1 arquivo, 3/3 testes |
| SHA-256 de `frontend/package-lock.json` | PASS — `FB8FE8A18EA4F72927AF5E3B506E2112F968C3BF98267DA0902EBC5BC40EA841` |
| `package.json` e lockfile contra o baseline 01-04 | PASS — sem drift |
| Postflight Git e ignore de `dist/`/cache | PASS — limpo e sincronizado em `6ed2de4` |

## Arquivos criados e modificados

- `frontend/angular.json` — replacement production exclusivo.
- `frontend/src/app/app.config.ts` — providers canônicos de Router, HttpClient e XSRF.
- `frontend/src/app/app.routes.ts` — seam única `APP_ROUTES`.
- `frontend/src/app/app.routes.development.ts` — lista build-specific inicial de dev/homolog.
- `frontend/src/app/app.routes.production.ts` — lista production vazia.
- `frontend/src/app/app.component.ts` e `.html` — host standalone com outlet único.
- `frontend/src/app/app.component.spec.ts` — prova focal do host neutro.
- `scripts/validate-angular-route-boundary.mjs` — contrato estático da fronteira.
- `scripts/validate-angular-route-boundary.test.mjs` — fixture canônica e regressões negativas.

## Decisões tomadas

- A separação de rotas acontece no build, antes da aplicação chegar ao navegador; production não decide isso em runtime.
- O frontend não é cliente OIDC público: tokens e sessão pertencem ao BFF Spring server-side, enquanto o Angular usa same-origin e XSRF.
- O host inicial não possui navegação visível nem fallback porque ainda não existem páginas de produto aprovadas.
- O validador aceita somente as formas canônicas necessárias ao host neutro; flexibilizações futuras exigem nova decisão e regressões correspondentes.

## Desvios do plano e correções

### 1. Critério textual contraditório do outlet

- **Encontrado em:** proposta da Task 3.
- **Problema:** uma linha podia ser lida como falha quando o outlet aparecesse, embora os critérios adjacentes exigissem exatamente um.
- **Correção:** redação alinhada para falhar somente diante de outlet ausente ou duplicado.
- **Commit:** `f5437b6`.

### 2. Validador inicialmente não provava todas as formas canônicas

- **Encontrado em:** checkpoint da Task 4.
- **Problema:** providers fora do array real, código em interpolation, parsing de comentários/regex e mutações posteriores podiam criar falsos negativos.
- **Correção:** lexer fail-closed, providers diretos e ordenados, formas canônicas de `appConfig`, `main.ts` e componente raiz e regressões adicionais.
- **Commit:** `ccb97f3`.

### 3. Mutação da lista importada pela seam

- **Encontrado em:** repetição do checkpoint da Task 4.
- **Problema:** `BUILD_SPECIFIC_ROUTES.push(...)` em `app.routes.ts` poderia contaminar também production antes do spread.
- **Correção:** contagem exclusiva dos usos do símbolo e regressão negativa específica.
- **Commit:** `6ed2de4`.

**Impacto:** as três correções foram necessárias para clareza e segurança, permaneceram nos dez arquivos do plano ou no próprio PLAN e não anteciparam funcionalidade.

## Problemas encontrados

- Duas inspeções auxiliares em PowerShell 5.1 trataram `null`/array de forma ambígua; a leitura de `angular.json` foi refeita com Node e confirmou o contrato correto. Nenhum arquivo do projeto foi afetado.
- O validador paralelo final não retornou no limite de espera; ele não foi contado como evidência. As revisões independentes anteriores e todas as provas locais concluídas foram preservadas.

## Riscos residuais registrados

- A ausência de tracer e diagnóstico nos bundles production será comprovada em 01-21 e selada em 01-28, quando essas capacidades existirem.
- `ARCH-01` e `IDEN-03` estão cobertos neste plano, mas permanecem `Pending` em `REQUIREMENTS.md` porque são compartilhados por entregas posteriores da Fase 1.
- O subconjunto lexical fail-closed do validador é intencional; novas necessidades de regex, escapes ou imports dinâmicos no host exigem alteração explícita do contrato.

## Configuração manual necessária

Nenhuma. A Task 5 reutilizou exclusivamente o grafo npm e o `node_modules` aprovados no Plano 01-04.

## Prontidão para o próximo plano

- O Plano 01-20 pode consumir o workspace e o host agora comprovados para criar o contrato OpenAPI e o cliente Angular gerado.
- Antes de executar 01-20, seu `autonomous: true`, escopo de dependências e comandos devem ser revisados sob a governança interativa; esse campo não concede autorização.
- `REQUIREMENTS.md` deve permanecer sem alteração neste fechamento.

## Autoavaliação: APROVADA

- [x] Os dez arquivos do frontmatter existem e correspondem exatamente ao intervalo de implementação.
- [x] A seam, o replacement production, Router, HttpClient e XSRF foram auditados no código real.
- [x] O validador e suas 69 regressões passaram após os dois endurecimentos.
- [x] Development, homolog e production compilaram; o Vitest focal passou.
- [x] `package.json`, lockfile e seu hash aprovado permaneceram imutáveis.
- [x] Git terminou limpo e sincronizado; somente outputs ignorados foram gerados.
- [x] Nenhum requisito compartilhado foi encerrado globalmente de forma prematura.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-11*
