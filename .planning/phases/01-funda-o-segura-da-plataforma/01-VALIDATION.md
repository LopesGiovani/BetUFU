# Estratégia de validação — Fase 1

**Status:** planejada, revisada e ainda não executada
**Nyquist:** cada comportamento obrigatório possui prova automatizada nomeada antes do gate humano final.
**TDD:** desabilitado; todos os planos ainda exigem testes explícitos e executáveis.
**Build canônico:** Maven Wrapper com `backend/pom.xml`; não há trilha Gradle.

## Pré-condições bloqueantes

01-01 confirma JDK 25, Node.js >=22.22.3 <23.0.0, daemon Docker e legitimidade dos seis pacotes npm originalmente classificados como `SUS`. Para 01-04, `01-04-GATE.md` registra a Matriz B exata de 14 dependências diretas, o lockfile v3 aprovado pelo SHA-256 integral, a allowlist exata de cinco pacotes com `hasInstallScript` e o risco moderado temporariamente aceito. O Gate global está `approved`, e a Task 5 comprovou instalação com scripts desabilitados, assinaturas, três builds e Vitest. Qualquer divergência futura interrompe a fase sem downgrade, substituição ou instalação silenciosa. Os checkpoints são humanos e não autoaprováveis.

## Infraestrutura de testes a criar

| Camada | Ferramentas e autoridade | Planos criadores |
|---|---|---|
| Backend | JUnit 5, Spring Boot Test, Testcontainers, Modulith Test e ArchUnit via Maven Wrapper | 01-02, 01-17, 01-05, 01-23, 01-10 |
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
| 01-20 | 8 | OpenAPI inicial e cliente gerado | generate + check read-only + build |
| 01-05 | 9 | vínculo backend `(iss, sub)` | `LocalIdentityTracerIT` |
| 01-21 | 10 | tracer browser não-prod, D-15 e ausência production | `validate-tracer.mjs` |
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

1. **Granularidade:** 28 planos, normalmente 1–3 tarefas materiais, <=10 arquivos por plano e <=5 por tarefa material. Exceção explícita: o scaffold basal convencional do Plano 01-04 possui 15 arquivos, distribuídos em três tarefas materiais de cinco arquivos; seu fluxo acrescenta um checkpoint bloqueante do lockfile e uma tarefa final de prova, ambos com zero arquivos. A exceção foi aprovada e está registrada em `01-04-GATE.md`; `frontend/src/app/api/generated/` segue como saída atômica machine-owned do Plano 01-20.
2. **OpenAPI:** 01-20 gera inicialmente; 01-26 audita mappings/regenera; 01-16 só executa check read-only.
3. **Same-origin:** daily/full publicam `http://localhost:4200`; 01-08 prova cookie, CSRF e PUT relativo.
4. **D-13:** `scripts/maven.mjs` seleciona Wrapper por SO e o gate registra Windows e Linux/WSL2.
5. **D-15:** tema Keycloak preserva templates nativos; 01-21 prova campo, valor não sensível e foco.
6. **Revogação:** 01-07 envia refresh token + hint e exige `invalid_grant`, sem prometer access JWT imediato.
7. **Sessão/UI:** 01-27 prova checking/authenticated/expired, limpeza, POST único, zero polling/storage/bearer.
8. **D-01/D-02:** 01-19 prova estaticamente o replacement development→production, a seam única, Router/HttpClient/XSRF e as proibições de URL absoluta, bearer e storage; seus três builds provam compilação, não a ausência de capacidades que ainda não existem. 01-21 acrescenta o controle positivo dev/homolog e a primeira prova de ausência production; 01-28 sela ausência de rota, link, URL, import e lazy chunk de tracer/diagnóstico no artefato production e no navegador.

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
- Não aprovar 01-16 diante de stage ausente, `skip`, mock onde se exige dependência real, drift, ameaça high/critical aberta ou artefato técnico production.
- Após provas verdes, a pessoa valida diagnóstico saudável/degradado, os oito estados UI, responsividade, foco, contraste, português, ausência de segredo e production sem diagnóstico nem tracer.

**Resultado planejado:** zero lacunas Nyquist conhecidas; execução e aprovação permanecem pendentes.
