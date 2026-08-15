---
phase: 01-funda-o-segura-da-plataforma
plan: "21"
subsystem: browser-walking-skeleton
tags: [angular, playwright, keycloak, bff, xsrf, production-boundary, runtime-validation]

requires:
  - phase: 01-29
    provides: "Datasource runtime local, Authorization Code hospedado e bootstrap XSRF oficial"
  - phase: 01-06
    provides: "Sessão BFF Redis e PUT de atividade válida"
provides:
  - "Harness Angular lazy somente em development/homolog para o vínculo de identidade"
  - "Jornada Chromium com login hospedado, D-15, XSRF automático e dois PUTs explícitos"
  - "Prova estática e browser de ausência material do tracer em production"
  - "Validador runtime fail-closed com supply chain, processos, containers e cleanup verificados"
affects: [01-07, frontend, keycloak, security, runtime, validation]

actuals:
  tasks: 3
  implementation_paths: 16
  documentation_paths: 4
  preexisting_committed_paths: 3
  commits_preexisting: 2
  implementation_commits_this_execution: 1
  documentation_commits_this_execution: self

tech-stack:
  added:
    - "@playwright/test 1.62.1 com Chromium revision 1234"
  patterns:
    - "Lazy route canônica exclusiva de development/homolog, com ausência semântica e material em production"
    - "E2E observa sessão e XSRF reais sem fabricar cookie, header ou token"
    - "Runner local possui processos, containers e artefatos e sempre executa cleanup independente"

patterns-established:
  - "O barrel do cliente gerado não deve ser importado por app.config quando isso retiver serviços exclusivos do harness no bundle production."
  - "Cookies observados são allowlisted por nome, origem, path, HttpOnly e Secure; respostas transitórias vazias não são tratadas como dados sensíveis, mas o estado final permanece estrito."
  - "D-15 permanece no formulário hospedado do Keycloak por script do child theme, sem substituir login.ftl ou template.ftl."

key-files:
  created:
    - "frontend/src/app/features/tracer/tracer-page.component.ts"
    - "frontend/src/app/features/tracer/tracer-page.component.html"
    - "frontend/src/app/features/tracer/tracer-page.component.spec.ts"
    - "frontend/playwright.config.ts"
    - "frontend/e2e/walking-skeleton.spec.ts"
    - "frontend/proxy.conf.json"
    - "infra/keycloak/themes/betufu/login/resources/js/required-field-validation.js"
    - "scripts/validate-tracer.mjs"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-21-SUMMARY.md"
  modified:
    - "frontend/package.json"
    - "frontend/package-lock.json"
    - "frontend/src/app/app.config.ts"
    - "frontend/src/app/app.routes.development.ts"
    - "infra/keycloak/themes/betufu/login/theme.properties"
    - "scripts/validate-angular-route-boundary.mjs"
    - "scripts/validate-angular-route-boundary.test.mjs"
    - "scripts/validate-keycloak-theme.mjs"

key-decisions:
  - "TracerPageComponent chama exclusivamente IdentityService.putCurrentIdentity(), sem HTTP, retry, Authorization, bearer ou XSRF manual."
  - "A rota tracer existe uma única vez por lazy import em app.routes.development.ts; o build production permanece sem rota, link, URL, import ou chunk alcançável."
  - "O proxy aceita somente os segmentos regex de /api, /oauth2 e /login e substitui cinco forwarded headers por valores loopback fixos."
  - "Playwright roda um worker, retries zero e artefatos visuais desligados; os dois PUTs decorrem de dois cliques separados por reload."
  - "A validação D-15 foi adicionada ao child theme oficial porque o Keycloak 26.7.1 real submetia senha vazia e devolvia erro geral, sem associação ao campo."
  - "Hashes dos manifests e do script D-15 normalizam somente CRLF para LF com UTF-8 estrito; executáveis, navegador, builds e JAR permanecem validados byte a byte."

requirements-completed: [IDEN-03, API-02, OPS-01]

coverage:
  - id: D1
    description: "O harness lazy alcança o PUT gerado somente em development/homolog e mantém erro sanitizado sem retry."
    requirement: API-02
    verification:
      - kind: unit
        ref: "TracerPageComponent focal — executado pelo validate-tracer"
        status: pass
      - kind: static
        ref: "validate-angular-route-boundary — 151/151 e contrato canônico OK"
        status: pass
    human_judgment: false
  - id: D2
    description: "Login Authorization Code hospedado comprova D-15 sem formulário Angular ou Direct Grant."
    requirement: IDEN-03
    verification:
      - kind: browser
        ref: "walking-skeleton.spec.ts — D-15, foco, erro associado, username preservado e senha vazia"
        status: pass
      - kind: static
        ref: "validate-keycloak-theme --local-only — contrato, arquivos e hashes aprovados"
        status: pass
    human_judgment: false
  - id: D3
    description: "Cookie e header XSRF reais são apenas observados; dois cliques produzem dois PUTs e o mesmo UUID opaco."
    requirement: API-02
    verification:
      - kind: browser
        ref: "validate-tracer — E2E hospedado, XSRF e dois PUTs"
        status: pass
    human_judgment: false
  - id: D4
    description: "Access token, refresh token e bearer permanecem ausentes de URL, DOM, storage, IndexedDB, headers e cookies legíveis."
    requirement: IDEN-03
    verification:
      - kind: browser
        ref: "observadores contínuos do walking-skeleton"
        status: pass
    human_judgment: false
  - id: D5
    description: "Production não contém nem renderiza rota, link, URL, import ou chunk alcançável do tracer."
    requirement: OPS-01
    verification:
      - kind: build
        ref: "validate-tracer — inspeção semântica de stats/import graph e prova browser production"
        status: pass
    human_judgment: false
  - id: D6
    description: "Supply chain, árvores de processos, containers congelados e cleanup falham fechado."
    requirement: OPS-01
    verification:
      - kind: runtime
        ref: "validate-tracer — supply chain, processo neto, compose config hash e cleanup complete"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com checkpoints, auditorias e correções runtime"
implementation_completed: 2026-08-15
status: complete-integrated-awaiting-push
---

# Phase 1 Plan 21: Browser walking skeleton Summary

**O walking skeleton prova no Chromium a arquitetura BFF real — login hospedado, sessão, XSRF e identidade persistida — sem transformar o tracer em interface ou código alcançável de produção.**

## Entregas

- `TracerPageComponent` é standalone, `OnPush` e zoneless-safe. Um clique chama somente o cliente gerado, bloqueia concorrência, mostra `Verificando…`, preserva uma identidade anterior em erro e nunca renderiza detalhes técnicos.
- A lazy route literal `tracer` existe somente em `app.routes.development.ts`, consumida também por homolog. O build production substitui o arquivo de rotas e não retém o componente nem `IdentityService`.
- `app.config.ts` importa `provideApi` diretamente do arquivo gerado. Isso evita que o barrel, que também reexporta `IdentityService`, torne o endpoint do harness alcançável no grafo de produção.
- Playwright 1.62.1 está fixado sem range. O proxy cobre apenas os três segmentos BFF aprovados e sobrescreve Host e quatro `X-Forwarded-*` com valores locais exatos.
- O E2E executa login hospedado, D-15 e exatamente dois PUTs reais separados por reload e nova ação. XSRF é observado no cookie/header produzidos pela aplicação; não existe fabricação por teste, proxy ou validador.
- Observadores contínuos reduzem URL, DOM, storage, IndexedDB, cookies e rede a flags seguras. Valores de senha, sessão, XSRF e UUID nunca aparecem em relatórios de falha.
- O runner compila backend offline, constrói development/homolog/production, inicia somente os serviços que possui, valida configuração congelada de containers, mata árvores de processo e executa cleanup por etapa mesmo sob falha.

## Desvios autorizados e rastreabilidade

O escopo original enumerava 12 paths. Quatro paths materiais adicionais foram necessários e foram autorizados durante a execução completa:

1. `frontend/src/app/app.config.ts`: troca do barrel por `./api/generated/provide-api` para impedir retenção de `IdentityService` no bundle production.
2. `infra/keycloak/themes/betufu/login/theme.properties`: registra o script do child theme.
3. `infra/keycloak/themes/betufu/login/resources/js/required-field-validation.js`: implementa `required`, associação acessível, mensagem nativa e foco no campo senha sem ler seu valor.
4. `scripts/validate-keycloak-theme.mjs`: fixa hash/tamanho e prova semanticamente que o script autorizado é o servido.

O plano histórico não foi reescrito para apagar a descoberta. O scope guard adaptado cobre os 16 paths de implementação; qualquer path além deles continua sendo drift. Nenhum FTL, endpoint backend ou realm foi alterado. `ROADMAP.md`, `STATE.md` e `01-VALIDATION.md` foram atualizados somente no fechamento posterior à integração, sem ampliar o código de implementação.

## Evidências de verificação

| Prova | Resultado |
|---|---|
| Validador canônico | PASS final na árvore combinada `fc31576f…` — contratos/supply chain, processo neto, Angular focal, backend offline, três builds, browser E2E, ausência production e cleanup completo. |
| Portabilidade textual | PASS focal — manifests e script D-15 convergem entre LF, CRLF e finais mistos; UTF-8 inválido e CR isolado falham fechado. |
| Jornada browser | PASS — login hospedado, D-15, XSRF automático e exatamente dois PUTs com UUID estável. |
| Fronteira Angular | PASS — `151/151`; nenhum failure, cancelled, skipped ou todo; validador do repositório também OK. |
| TypeScript E2E/config | PASS — `tsc --ignoreConfig --noEmit --strict` para Playwright config e spec. |
| Tema Keycloak | PASS — contrato local, inventário, bytes e SHA-256 do script aprovados; carregamento real incluído no E2E. |
| Whitespace | PASS — `git diff --check`; somente avisos informativos de futura conversão LF/CRLF. |
| Cleanup | PASS — PostgreSQL, Redis e Keycloak parados; nenhuma porta 4200/5432/6379/8080/8081 aberta; nenhum temporário/test-result restante. |

Hashes finais do gate canônico:

| Artefato | Arquivos/bytes | SHA-256 |
|---|---:|---|
| development | 11 / 4.241.505 | `9B3AA4D83FA315E0F72AA2B7CEB097E3B475C309E83C7BCEA5D499AB6F377C1B` |
| homolog | 7 / 237.552 | `064DA972F508C0AF3C4131CAF441E4488EF4150750ED3BBA26037B721E072EFC` |
| production | 6 / 456.735 | `63246EC4447363495B7A16CC995D6DBAEDB47D380974B97E993D4F71E4C6A90A` |
| backend JAR | 78.723.937 bytes | `2EBB23E1FC11E71CC9934508885CF754BEA2B8C10667BEEB5E97FD7FD946B67A` |

## Diagnósticos relevantes

- A primeira integração `--no-commit` parou corretamente em `SUPPLY_PACKAGE`: o `package.json` tinha SHA-256 bruto `6BADB52B2A3AAC4561C632539E4B820A8D5ABEB633C9A9BE8F908AAA5C8D2FCD` no worktree executor com finais mistos e `90493A9DBC464363A3FD3810C0879560144967FE3ADB0E9CDE64DED54EF5A91E` no checkout normal com CRLF. O conteúdo textual é equivalente e converge para o hash canônico `550D9FBE769F990BE649A8615CA6E4D4EE29ECF347FB05B28F6169EA31EDA41E`; o `package-lock.json` converge para `A471B9954695C0A8857A8432FD7AD025C71CDE44CCF2D538FCE97D78A7233520`. A tentativa foi abortada antes de commit, preservando o master.
- A segunda tentativa parou antes do runtime em `KEYCLOAK_THEME_STATIC`: o script D-15 aprovado possui 1.782 bytes em LF e 1.837 bytes no checkout CRLF, exatamente 55 bytes `CR` adicionais. `validate-keycloak-theme.mjs` passou a aplicar a mesma canonicalização estrita antes de conferir tamanho e SHA-256, sem alterar o script servido ou seu comportamento.
- O Keycloak 26.7.1 submetia a senha vazia e devolvia erro geral; esperar mais tempo no teste não corrigiria o produto. O child-theme passou a aplicar o contrato D-15 no formulário hospedado.
- O runtime emite `KC_AUTH_SESSION_HASH` legível e `KEYCLOAK_SESSION` não-HttpOnly por contrato do login hospedado. Ambos são allowlisted nominalmente, e cookies legíveis continuam varridos contra marcadores de token.
- O BFF local em HTTP emite `SESSION`/`XSRF-TOKEN` sem `Secure`; o Keycloak emite seus cookies com `Secure`. O E2E verifica a política por origem, sem wildcard.
- Antes do gate combinado, PostgreSQL e Keycloak ainda carregavam config hashes e bind mounts do worktree executor. Somente esses dois containers parados foram recriados a partir do master, sem pull e com volumes preservados; Redis já coincidia. O gate então validou config hash, imagem, portas loopback, mounts, rede e healthcheck dos três serviços.
- A terceira integração `--no-commit` chegou ao fim: jornada hospedada, dois PUTs, ausência production e cleanup passaram na árvore `fc31576f…`, que combina 01-07 e 01-21. O merge de validação foi abortado e a feature foi rebaseada sobre `c76b88e`; sua árvore resultante permaneceu exatamente `fc31576f…`.

## Operações Git

O histórico final linear do 01-21 contém quatro unidades intencionais sobre o master `c76b88e`; a quarta incorpora este fechamento por amend:

1. `293f4b9` — `docs(01-21): define route validator transition`; altera `01-21-PLAN.md` para registrar a transição aprovada do validador.
2. `975a559` — `chore(01-21): prepare tracer route boundary validator`; altera `validate-angular-route-boundary.mjs` e seu teste, com 344 linhas do bloco material fail-closed.
3. `eb4f188` — `feat(01-21): prove browser walking skeleton`; consolida os 16 paths finais, incluindo as duas correções de portabilidade, com 3.921 inserções e 8 remoções.
4. `docs(01-21): document consolidated browser skeleton` — adiciona o Summary consolidado e incorpora `ROADMAP.md`, `STATE.md` e `01-VALIDATION.md`; seu hash final não é embutido para evitar referência circular.

Os hashes anteriores ao rebase — incluindo `cf86a31`, `f5d2da6`, `88dca30` e `14f9b17` — e os commits locais provisórios `35e3007`, `befdbbb`, `f835b0b` e `4f2304b` foram substituídos antes de integração ou push. Nenhum código foi descartado: a árvore rebaseada é exatamente `fc31576f31b8150baa248d936a470339596c8d72`, a mesma aprovada pelo gate combinado. Esta atualização final foi incorporada por amend ao próprio commit documental, sem criar uma quinta unidade no histórico.

`master` avançou inicialmente por fast-forward até o Summary do 01-21, sem merge commit, e o commit documental de ponta foi então amendado para incorporar o fechamento global. `feat/01-21-browser-walking-skeleton` permanece no hash pré-amend `a283bad`; `origin/master` e `feat/01-07-oidc-revocation` permanecem em `c76b88e` até eventual push autorizado. As duas primeiras tentativas e o merge temporário usado para validar a terceira foram abortados; não houve push do 01-21.

## Revisões independentes e estado atual

A primeira revisão independente encontrou somente um P2 de rastreabilidade/durabilidade, sem P0 ou P1. A branch foi ancorada, os commits preexistentes passaram a ser distinguidos da execução e o P2 foi fechado sem alterar a evidência técnica daquele snapshot.

Uma segunda revisão estreita sobre `f835b0b` + `4f2304b` concluiu **PASS**, com P0=P1=P2=0. Ela confirmou de forma independente a canonicalização dos manifests, a separação entre hashing textual e binário e a ausência de enfraquecimento do supply-chain.

A segunda tentativa de integração revelou depois a divergência CRLF do script D-15. A revisão independente final sobre `88dca30` + `14f9b17` concluiu **PASS**, com P0=P1=P2=0, e confirmou a nova canonicalização, a preservação byte a byte de `validate-tracer.mjs` e o escopo consolidado. O gate combinado passou depois sobre a árvore `fc31576f…`.

O rebase alterou somente pais e hashes: a árvore permaneceu exatamente `fc31576f…`, e o gate canônico completo passou sobre essa árvore combinada. O responsável dispensou explicitamente uma nova revisão independente pós-rebase; o aceite final se apoia no `PASS` anterior, na igualdade exata da árvore e no gate verde, sem alegar um parecer adicional que não ocorreu.

### Achados P3 não bloqueantes

1. A sonda browser procura `tracer` no nome da subrequest e, isoladamente, não detectaria chunk hasheado; a inspeção estática forte de todos os arquivos e do grafo de imports fecha esse caso.
2. Metade do check `observers active` usa `observerFailed: false` constante; a metade ligada ao observador real continua discriminante.
3. D-15 é um contrato client-side de UX/acessibilidade; a rejeição de credencial permanece server-side, mas não existe contrapartida server-side para associação/foco.
4. Há uma corrida possível entre `childHasExited()` e `taskkill` que pode causar falha de cleanup espúria, sempre na direção fail-closed.
5. A prova D-15 cobre username preenchido e senha vazia; o caso ambos vazios não comprova qual controle recebe primeiro foco no parent theme.
6. `isTokenEndpoint` usa lógica de segmentos mais frágil que comparar o sufixo exato; no BFF o navegador não deve alcançar esse endpoint.

Os pareceres também registraram como P3 o hash bruto histórico `6BADB52B…`, a canonicalização pré-existente mais permissiva do CSS, a ausência da métrica `tokens` e a falta então existente de um gate verde no snapshot final. A propriedade de portabilidade foi reproduzida e o gate combinado fechou os apontamentos de runtime e hashes; CSS e `tokens` permanecem observações não bloqueantes. O antigo P3 sobre posições conflitantes da branch foi corrigido pela seção Git linear acima.

## Requisitos e próximo passo

`IDEN-03`, `API-02` e `OPS-01` aparecem como contribuição local deste plano. Eles não devem ser encerrados globalmente enquanto as entregas compartilhadas da Fase 1 continuarem pendentes.

O fast-forward de `master` foi concluído sem merge commit e este fechamento documental foi incorporado ao commit de ponta por amend. O único próximo passo Git pendente é o push, que continua sujeito a autorização própria.

## Autoavaliação: IMPLEMENTAÇÃO E FECHAMENTO INTEGRADOS; PUSH PENDENTE

- [x] As três tasks e os 16 paths de implementação foram entregues.
- [x] Login hospedado, D-15, XSRF automático, dois PUTs e UUID estável passaram no navegador real.
- [x] Development e homolog incluem a lazy route canônica; production não contém referência ou chunk alcançável do tracer.
- [x] Supply chain, containers, processos e cleanup passaram fail-closed.
- [x] Os commits locais provisórios foram consolidados e rebaseados no único commit de implementação `eb4f188`; não houve merge commit nem push, e o estado global foi alinhado somente neste fechamento.
- [x] Revisão independente final concluída com P0=P1=P2=0 e quatro P3 não bloqueantes.
- [x] Rastreabilidade dos dois commits preexistentes corrigida neste Summary.
- [x] Durabilidade dos commits `cf86a31` e `f5d2da6` — branch ancorada por fast-forward autorizado.
- [x] P2 fechado pelos critérios do parecer, sem alteração do snapshot técnico revisado.
- [x] Portabilidade dos manifests e do script D-15 validada entre LF, CRLF e finais mistos.
- [x] Gate canônico completo aprovado na árvore combinada `fc31576f…`, incluindo Chromium e cleanup.
- [x] Nova revisão independente pós-rebase dispensada explicitamente pelo responsável; `PASS` anterior, igualdade da árvore e gate combinado foram aceitos como evidência final.
- [x] Fast-forward do master para a feature linear concluído sem merge commit.
- [x] Fechamento documental de `ROADMAP.md`, `STATE.md`, `01-VALIDATION.md` e deste Summary incorporado ao commit documental por amend, sem commit pequeno adicional.
- [ ] Push de `master` para `origin/master`.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Implementation completed: 2026-08-15*
