# Estratégia de validação — Fase 1

**Status:** planejada, revisada e parcialmente executada; a Fase 1 permanece em andamento
**Nyquist:** cada comportamento obrigatório possui prova automatizada nomeada antes do gate humano final.
**TDD:** desabilitado; todos os planos ainda exigem testes explícitos e executáveis.
**Build canônico:** Maven Wrapper com `backend/pom.xml`; não há trilha Gradle.

## Pré-condições bloqueantes

01-01 confirma JDK 25, Node.js >=22.22.3 <23.0.0, daemon Docker e legitimidade dos seis pacotes npm originalmente classificados como `SUS`. Para 01-04, `01-04-GATE.md` registra a Matriz B exata de 14 dependências diretas, o lockfile v3 aprovado pelo SHA-256 integral, a allowlist exata de cinco pacotes com `hasInstallScript` e o risco moderado temporariamente aceito. Esse Gate histórico está `approved`, e a Task 5 comprovou instalação com scripts desabilitados, assinaturas, três builds e Vitest.

O Plano 01-20 fechou como **`approved`** o Gate supply-chain em `01-20-GATE.md`. O SHA-256 histórico `FB8FE8A18EA4F72927AF5E3B506E2112F968C3BF98267DA0902EBC5BC40EA841` continua válido somente como baseline do grafo do 01-04 e nunca aprova o lockfile que incluiu o wrapper OpenAPI. Wrapper npm 2.40.1, motor Java 7.24.0, exceção Angular 22, novo hash, allowlist, risco npm, assinaturas, JAR e identidade humana mantêm estados separados e aprovados conforme o Gate. A evidência observada no fechamento registrou fake runner `67/67`, fronteira de rotas `110/110`, runtime same-origin `2/2`, digest canônico `78E92BB17EBC45E25F86CA3D23C66958BDCD1786983DEFDC2DD1050BE8D13D5F` e builds `development`, `homolog` e `production` com êxito. Qualquer divergência interrompe a execução correspondente sem downgrade, substituição, `npm audit fix`, `--force`, lifecycle ou instalação silenciosa; os checkpoints continuam humanos e não autoaprováveis.

## Infraestrutura de testes a criar

| Camada | Ferramentas e autoridade | Planos criadores |
|---|---|---|
| Backend | JUnit 5, Spring Boot Test, Spring Security Test, Testcontainers, Maven Surefire/Failsafe, Modulith Test e ArchUnit via Maven Wrapper | 01-02, 01-17, 01-05, 01-23, 01-10 |
| Frontend | Angular TestBed/test runner, três builds fixados no lockfile e validadores Node sem dependências | 01-04, 01-19 |
| E2E | Playwright contra navegador, Keycloak, BFF, PostgreSQL e dependências reais | 01-21, 01-08, 01-28 |
| Infra | Docker Compose com healthchecks e profiles daily/full | 01-03, 01-11 |
| Gate agregado | backend verify, Angular, drift OpenAPI, Playwright, diagnóstico e ausência production | 01-16 |

## Mapa executável por plano e onda

| Plano | Wave | Capacidade provada | Prova automatizada focal |
|---|---:|---|---|
| 01-01 | 1 | supply chain e runtimes | preflight Java/Node/Docker + `npm view` |
| 01-02 | 2 | Maven/POM/launcher portátil | dependency tree + compile |
| 01-17 | 3 | scaffold Spring BFF e verify Modulith mínimo | `BetufuApplicationSmokeTest` |
| 01-18 | 4 | profiles dev/homolog/prod | `EnvironmentConfigurationTest` |
| 01-03 | 5 | Compose mínimo, realm e tema Keycloak | Compose healthy + smoke do tema |
| 01-04 | 6 | workspace Angular 22 reproduzível | auditoria do lockfile + `npm ci --ignore-scripts` + assinaturas + três builds + Vitest |
| 01-19 | 7 | host e seam de rotas build-specific | `validate-angular-route-boundary.mjs` + regressões negativas + builds development/homolog/production + app component test |
| 01-20 | 8 | OpenAPI inicial e cliente gerado com supply chain e recovery | Gate separado + fake runner + generate/check read-only + runtime PUT relativo/XSRF + builds development/homolog/production |
| 01-05 | 9 | vínculo backend `(iss, sub)` idempotente/concorrente | lifecycle `verify -Dit.test=LocalIdentityTracerIT`: suíte Surefire + IT Failsafe selecionado contra PostgreSQL 18.4 com digest aprovado; depois `verify` agregado |
| 01-21 | 10 | tracer browser não-prod, D-15 e ausência production | **bloqueado antes de código/E2E:** datasource runtime local e bootstrap HTTP XSRF devem ter plano, aprovação, implementação e prova próprios; só então `validate-tracer.mjs` |
| 01-06 | 11 | política PT8H e Redis session | SessionPolicy/RedisSession ITs |
| 01-07 | 12 | UserInfo opt-out e revogação | UserInfo + revocation contract/Keycloak ITs |
| 01-22 | 13 | GET session e logout do dispositivo | SessionController/CurrentDeviceLogout ITs |
| 01-08 | 14 | CSRF HTTP/STOMP e tracer protegido | `validate-tracer.mjs --security` |
| 01-09 | 15 | dez módulos declarados | compile Modulith |
| 01-23 | 16 | gates Modulith/ArchUnit | três testes arquiteturais |
| 01-10 | 17 | Flyway e autoridade de storage | migration/checksum/authority ITs |
| 01-11 | 18 | daily/full e orquestrador | `dev.mjs verify daily/full` |
| 01-24 | 19 | reset local escopado | `reset-local.test.mjs` |
| 01-12 | 20 | Problem Details, correlação e logs | ProblemDetails/Observability ITs |
| 01-25 | 21 | health e métricas seguros | `HealthMetricsIT` |
| 01-13 | 22 | DTO/probes diagnósticos | service/degradation tests |
| 01-26 | 23 | endpoint não-prod e cliente final | `validate-phase1.mjs --contract` |
| 01-14 | 24 | tema e ativos locais | theme spec + build |
| 01-27 | 25 | store/layout autenticado | `validate-phase1.mjs --session-ui` |
| 01-15 | 26 | adapter e página diagnóstica | adapter/component tests |
| 01-28 | 27 | rotas/E2E e ausência production de diagnóstico/tracer | `validate-phase1.mjs --diagnostic-ui` |
| 01-16 | 28 | gate integral e aprovação visual | `validate-phase1.mjs` |

## Provas obrigatórias preservadas

1. **Granularidade:** 28 planos, normalmente 1–3 tarefas materiais, <=10 arquivos por plano e <=5 por tarefa material. Exceção histórica: o scaffold basal do 01-04 possui 15 arquivos em três tarefas de cinco, com checkpoint e prova sem arquivos, conforme `01-04-GATE.md`. Exceção histórica aprovada do 01-20: exatamente 12 paths em três tarefas versionáveis de 5/2/5 — contrato/pipeline, package/lock e geração/integração — mais instalação/aquisição e prova agregada como blocos materiais de zero arquivos, cada qual com checkpoint próprio. Ela permanece registrada para auditabilidade; o Gate 01-20 está `approved`. O 01-05 possui nove paths em tarefas de 2/5/3, com resolução Maven, execução focal PostgreSQL e regressão agregada como blocos materiais de zero arquivos e checkpoints humanos separados.
2. **OpenAPI:** 01-20 gera inicialmente com motor 7.24.0 hash-locked e publica a árvore de modo transacional/recuperável; 01-26 audita mappings/regenera; 01-16 só executa check read-only. Nenhum documento promete substituição de diretório atomicamente indivisível no Windows.
3. **Same-origin:** daily/full publicam `http://localhost:4200`; 01-20 prova que o cliente Angular emite PUT relativo e usa o mecanismo XSRF nativo, mas não prova que o backend materializa o cookie. O 01-05 mantém CSRF ativo e usa `csrf()` somente como fixture MockMvc; isso não equivale ao bootstrap browser `XSRF-TOKEN`/`X-XSRF-TOKEN`. O 01-21 permanece bloqueado até datasource runtime local e bootstrap HTTP XSRF serem planejados, aprovados, implementados e provados. É proibido avançar desabilitando CSRF ou montando token/header manual no Angular; a parte HTTP hoje atribuída ao 01-08 requer correção de sequência própria.
4. **D-13:** `scripts/maven.mjs` seleciona Wrapper por SO e o gate registra Windows e Linux/WSL2.
5. **D-15:** tema Keycloak preserva templates nativos; 01-21 prova campo, valor não sensível e foco.
6. **Revogação:** 01-07 envia refresh token + hint e exige `invalid_grant`, sem prometer access JWT imediato.
7. **Sessão/UI:** 01-27 prova checking/authenticated/expired, limpeza, POST único, zero polling/storage/bearer.
8. **D-01/D-02:** 01-19 prova estaticamente o replacement development→production, a seam única, Router/HttpClient/XSRF e as proibições de URL absoluta, bearer e storage; seus três builds provam compilação, não a ausência de capacidades que ainda não existem. 01-21 acrescenta o controle positivo dev/homolog e a primeira prova de ausência production; 01-28 sela ausência de rota, link, URL, import e lazy chunk de tracer/diagnóstico no artefato production e no navegador.
9. **Identidade local/concorrência:** 01-05 usa `(issuer, subject)` exato como unique natural e UUID opaco como chave pública. O service executa `INSERT ... ON CONFLICT DO NOTHING` seguido de `SELECT` em nova instrução `READ_COMMITTED`; o IT dispara chamadas simultâneas e exige uma linha, mesmo UUID e timestamps estáveis. `find` seguido de `save`, no-op update e recuperação após unique violation na mesma transação não satisfazem a prova.
10. **Ciclo de IT Maven:** `*Test` permanece sob Surefire e `*IT` passa a ser executado pelo Maven Failsafe 3.5.6 nas fases `integration-test`/`verify`. A prova focal usa `verify -Dit.test=LocalIdentityTracerIT`: por atravessar o lifecycle, ela executa a suíte Surefire e seleciona esse IT no Failsafe, não é um comando de teste único. A prova agregada usa `verify` sem filtro e inspeciona relatórios de ambos; um build verde que não executou o IT é falha de cobertura.
11. **Fixture PostgreSQL:** o IT 01-05 aceita somente `docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636`. Preflight ausente encerra sem pull; pull e criação de container/rede exigem autorizações materiais próprias.

## Matriz obrigatória do Plano 01-20

| Dimensão | Prova bloqueante |
|---|---|
| Wrapper npm | nome/versão/licença/Node/integridade/lifecycle exatos; `hasInstallScript: true`; toda resolução/instalação com `--ignore-scripts` |
| Motor Java | URL, 31.904.922 bytes e SHA-256 `4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C`; hash antes/depois; ausência/divergência falha sem download implícito |
| Novo grafo npm | lock v3, origens `registry.npmjs.org`, SHA-512, `npm ls --all --package-lock-only`, audit high/critical e novo SHA-256 humano; baseline do 01-04 não é aprovação |
| Lifecycle/allowlist | lista real coincide exatamente com os seis candidatos ou volta à decisão; nenhum lifecycle executado |
| Assinaturas | `npm audit signatures` somente após Gate aprovado para instalação, `npm ci --ignore-scripts` e autorização material própria |
| Compatibilidade | aceite humano da exceção exata; matriz compensatória completa; spike de 23 arquivos/digest registrado; `ng-packagr`, runtime relativo e três builds passam |
| Fake runner | falha preserva árvore; obsoleto removido; check add/delete/change read-only; concorrência; live/dead/ambiguous lock; nonce; segundo rename/rollback; journal recovery; cleanup; traversal/symlink; LF/NUL/newline; duas gerações mesmo digest |
| Recovery | lock PID+nonce+instante, tombstone de lock morto provado, journal `prepared`/`old-moved`/`new-installed`, staging/backup no mesmo volume e recovery no startup |
| Árvore gerada | somente TypeScript necessário; sem package/lifecycle, README, `git_push.sh`, tsconfig, ng-package ou `.openapi-generator`; machine-owned |
| Integração Angular | `provideApi('')` exatamente uma vez como quinto provider direto; quatro providers anteriores preservados; regressões do validador passam |
| Runtime | PUT relativo `/api/v1/tracer/identity/current`, sem body, XSRF nativo, sem auth ativa/credencial manual; campos dormentes genéricos de `configuration.ts` não falham isoladamente |
| Reprodutibilidade | generate seguido de check sem drift, duas gerações com mesmo digest, builds development/homolog/production verdes |

**UserInfo — OPT-OUT:** 01-07 lê somente claims allowlisted do ID token/OidcUser e prova zero chamadas.

## Gates de cobertura documental

| Gate | Esperado | Evidência |
|---|---:|---|
| Requisitos | 17/17 | frontmatter + 01-16 |
| Decisões | D-01..D-17 | planos responsáveis + 01-16 |
| Edge fallback | 34/34 | 01-20, 01-06, 01-07, 01-10, 01-11 e 01-12 |
| UI-SPEC | 8/8 | 01-15 + E2E 01-28/01-16 |
| Fontes | 47/47 | `01-SOURCE-AUDIT.md` |
| Proibições bespoke | 10/10 | planos responsáveis + 01-16 |
| Segurança | ASVS L1 por plano | `<threat_model>` completo |

## Política de falhas e aprovação

- Executar a prova focal de cada tarefa e a verificação agregada do plano; nenhuma prova manual substitui teste automatizado.
- No 01-20, cada `scope_guard` exige index vazio, inventaria staged/unstaged/untracked e rejeita qualquer path fora do baseline e da allowlist cumulativa da tarefa.
- Não aprovar 01-16 diante de stage ausente, `skip`, mock onde se exige dependência real, drift, ameaça high/critical aberta ou artefato técnico production.
- Após provas verdes, a pessoa valida diagnóstico saudável/degradado, os oito estados UI, responsividade, foco, contraste, português, ausência de segredo e production sem diagnóstico nem tracer.

**Resultado planejado:** zero lacunas Nyquist conhecidas; execução e aprovação permanecem pendentes.
