# Registro de issues GitHub — Fase 1

> Estrutura aprovada e publicada no GitHub em 2026-08-06. As correções documentais que originaram as issues estão versionadas na branch principal.

## Resultado remoto

- **Milestone:** `Fase 1 — Fundação Segura da Plataforma`
- **Issue de acompanhamento:** `#29 — Fundação Segura da Plataforma`
- **Issues executáveis:** `#1` a `#28`, na ordem das waves
- **Labels próprias:** 16
- **Auditoria remota:** 29/29 issues no milestone, 28/28 itens no checklist, 0 divergências de corpo, labels ou dependências

## Decisão estrutural aplicada

- **1 milestone:** `Fase 1 — Fundação Segura da Plataforma`
- **1 issue de acompanhamento:** objetivo, critérios globais e checklist ordenado
- **28 issues executáveis:** uma para cada plano GSD
- **Sem prazo:** nenhuma data foi aprovada
- **Sem responsáveis:** nenhuma atribuição foi aprovada

Manter uma issue por plano preserva a relação direta entre issue, plano, wave, prova focal, summary e futuro commit. Foram avaliados agrupamentos como `01-01 + 01-02`, `01-06 + 01-07` e `01-17 + 01-18`, mas cada plano possui gate ou verificação própria; uni-los criaria issues grandes e parcialmente concluídas.

## Estado remoto atual

- Repositório privado.
- 28 issues executáveis abertas.
- Issue de acompanhamento `#29` aberta.
- Milestone `Fase 1 — Fundação Segura da Plataforma` aberto.
- 16 labels próprias, além das labels padrão do GitHub.

## Milestone publicado

**Título:** `Fase 1 — Fundação Segura da Plataforma`

**Descrição:**

> Entregar uma fundação modular, reproduzível e segura para a BETUFU, com BFF OIDC, sessões protegidas, PostgreSQL autoritativo, integração OpenAPI, observabilidade segura e uma estrutura Angular autenticada. A fase termina somente quando o gate Nyquist e a revisão humana estiverem aprovados.

## Labels publicadas

| Label | Cor | Uso |
|---|---|---|
| `fase:1` | `5319E7` | Todas as issues desta fase |
| `tipo:tracking` | `0B1220` | Issue de acompanhamento |
| `tipo:gate` | `B60205` | Checkpoint que bloqueia avanço |
| `revisao:humana` | `D93F0B` | Aprovação explícita necessária |
| `area:build` | `D4C5F9` | Toolchain, wrappers e builds |
| `area:backend` | `1D76DB` | Java, Spring e BFF |
| `area:frontend` | `FF8A3D` | Angular e integração web |
| `area:infra` | `006B75` | Docker, ambientes e operação local |
| `area:identidade` | `8A2BE2` | Keycloak, OIDC e sessão |
| `area:seguranca` | `D73A4A` | Proteções e controles de segurança |
| `area:dados` | `0E8A16` | PostgreSQL, Flyway e storages |
| `area:api` | `0052CC` | REST, OpenAPI e cliente gerado |
| `area:arquitetura` | `FBCA04` | Limites modulares e dependências |
| `area:observabilidade` | `2CBE4E` | Logs, health, métricas e diagnóstico |
| `area:ui` | `C5DEF5` | Sistema visual e acessibilidade |
| `area:testes` | `BFDADC` | Integração, E2E e quality gates |

As labels padrão existentes foram preservadas. Labels de status, prioridade e responsável não foram criadas porque essas decisões ainda não foram tomadas.

## Issue de acompanhamento

**Título:** `[Fase 1] Fundação Segura da Plataforma`

**Labels:** `fase:1`, `tipo:tracking`

**Descrição:**

> Acompanhar a entrega dos 28 planos da Fase 1 na ordem das waves aprovadas. Esta issue não substitui os planos nem adiciona escopo; ela consolida progresso, dependências e os quatro critérios globais da fase.

**Critérios de aceite globais:**

- O ambiente local inicia com versões fixadas e oferece saúde, logs, métricas e correlação sem dados sensíveis.
- PostgreSQL e Flyway governam os dados e o schema, e violações modulares falham automaticamente.
- O navegador usa API versionada e cliente gerado, mantendo tokens OIDC exclusivamente no BFF.
- Sessões, logout e STOMP CONNECT aplicam as políticas aprovadas de OIDC, cookies, origem e CSRF.

**Checklist planejado:**

- [ ] F1/P01 — validar cadeia de suprimentos e runtimes
- [ ] F1/P02 — fixar build Maven canônico
- [ ] F1/P17 — criar scaffold Spring e BFF OIDC
- [ ] F1/P18 — isolar configurações por ambiente
- [ ] F1/P03 — provisionar infraestrutura e Keycloak
- [ ] F1/P04 — criar workspace Angular
- [ ] F1/P19 — criar host Angular e rotas por build
- [ ] F1/P20 — gerar cliente OpenAPI
- [ ] F1/P05 — persistir vínculo estável de identidade
- [ ] F1/P21 — validar walking skeleton pelo navegador
- [ ] F1/P06 — implementar política e persistência de sessão
- [ ] F1/P07 — implementar UserInfo opt-out e revogação
- [ ] F1/P22 — expor estado de sessão e logout atual
- [ ] F1/P08 — proteger HTTP e STOMP com CSRF
- [ ] F1/P09 — declarar limites Spring Modulith
- [ ] F1/P23 — bloquear violações modulares
- [ ] F1/P10 — tornar Flyway e PostgreSQL autoritativos
- [ ] F1/P11 — entregar ambientes daily/full
- [ ] F1/P24 — implementar reset local seguro
- [ ] F1/P12 — padronizar erros, correlação e logs
- [ ] F1/P25 — expor health e métricas seguras
- [ ] F1/P13 — implementar diagnóstico backend
- [ ] F1/P26 — restringir diagnóstico e fechar OpenAPI
- [ ] F1/P14 — implementar sistema visual
- [ ] F1/P27 — construir layout autenticado
- [ ] F1/P15 — implementar página diagnóstica
- [ ] F1/P28 — selar rotas técnicas em produção
- [ ] F1/P16 — executar gate integral da fase

A issue de acompanhamento `#29` contém os links reais; este checklist local preserva a mesma ordem de execução.

## Issues executáveis

### Wave 1 — F1/P01

**Título:** `[F1/P01] Validar cadeia de suprimentos e runtimes obrigatórios`

- **Plano:** [01-01-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-01-PLAN.md)
- **Depende de:** nenhuma issue
- **Requisitos:** `ARCH-01`, `OPS-01`
- **Labels:** `fase:1`, `tipo:gate`, `revisao:humana`, `area:build`, `area:infra`, `area:seguranca`
- **Descrição:** bloquear qualquer instalação ou scaffold até que os seis pacotes npm e os runtimes obrigatórios sejam legitimados.
- **Aceite:** pacotes conferidos e aprovados; JDK 25, Node `>=22.22.3` e Docker ativos; nenhum artefato executável criado antes do gate.
- **Verificação:** `java -version`, `node --version`, `docker info` e seis consultas `npm view`, seguidas de aprovação humana explícita.

### Wave 2 — F1/P02

**Título:** `[F1/P02] Fixar o build Maven canônico do backend`

- **Plano:** [01-02-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-02-PLAN.md)
- **Depende de:** F1/P01
- **Requisito:** `ARCH-01`
- **Labels:** `fase:1`, `area:build`, `area:backend`
- **Descrição:** fixar Java, Maven Wrapper e BOMs da stack backend aprovada, com launcher portátil.
- **Aceite:** Java 25 obrigatório; nenhuma versão dinâmica ou snapshot; launcher seleciona Wrapper por sistema, preserva exit code e rejeita metacaracteres.
- **Verificação:** dependency tree e compilação pelo `scripts/maven.mjs`.

### Wave 3 — F1/P17

**Título:** `[F1/P17] Criar o scaffold Spring Boot e a fronteira BFF OIDC`

- **Plano:** [01-17-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-17-PLAN.md)
- **Depende de:** F1/P02
- **Requisitos:** `ARCH-01`, `IDEN-01`, `IDEN-03`
- **Labels:** `fase:1`, `area:backend`, `area:identidade`, `area:seguranca`
- **Descrição:** criar o scaffold Spring Boot/Modulith e manter autenticação e tokens exclusivamente no servidor.
- **Aceite:** contexto sobe na stack fixada; OIDC usa Authorization Code, state, nonce e PKCE; cookies são protegidos; `/api/v1/**` exige autenticação; não existe bearer SPA ou formulário BETUFU de credenciais.
- **Verificação:** `BetufuApplicationSmokeTest` e verificação mínima do Spring Modulith.

### Wave 4 — F1/P18

**Título:** `[F1/P18] Isolar configurações de desenvolvimento, homologação e produção`

- **Plano:** [01-18-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-18-PLAN.md)
- **Depende de:** F1/P17
- **Requisito:** `OPS-02`
- **Labels:** `fase:1`, `area:backend`, `area:infra`, `area:seguranca`
- **Descrição:** separar configurações dos três ambientes sem segredos versionados ou chave permissiva de diagnóstico em produção.
- **Aceite:** profiles carregam isoladamente; produção não habilita diagnóstico; origens e proxies são exatos; Flyway permanece ativo e JPA em `validate`.
- **Verificação:** `EnvironmentConfigurationTest`.

### Wave 5 — F1/P03

**Título:** `[F1/P03] Provisionar infraestrutura local isolada e configurar o Keycloak`

- **Plano:** [01-03-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-03-PLAN.md)
- **Depende de:** F1/P18
- **Requisitos:** `IDEN-01`, `DATA-06`, `OPS-01`, `OPS-02`
- **Labels:** `fase:1`, `area:infra`, `area:identidade`, `area:dados`, `area:seguranca`
- **Descrição:** após gate humano das imagens, subir PostgreSQL, Redis, Keycloak, SeaweedFS como S3 exclusivamente local e e-mail com isolamento, saúde, contrato S3 genérico e tema mínimo BETUFU.
- **Aceite:** cinco serviços saudáveis; bancos e roles separados; volumes persistentes; realm sem wildcards; nenhum segredo real; fluxos nativos Keycloak e acessibilidade preservados.
- **Verificação:** validação do Compose, healthchecks e smoke do tema.

### Wave 6 — F1/P04

**Título:** `[F1/P04] Criar o workspace Angular 22 reproduzível`

- **Plano:** [01-04-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-04-PLAN.md)
- **Depende de:** F1/P03
- **Requisito:** `ARCH-01`
- **Labels:** `fase:1`, `area:frontend`, `area:build`
- **Descrição:** criar a base standalone Angular 22/TypeScript 6 com lockfile e build reproduzível.
- **Aceite:** `npm ci` usa somente o lockfile; build passa com budgets; não existem OAuth SPA SDK, NgRx, Nx, microfrontends, analytics ou fontes remotas.
- **Verificação:** `npm --prefix frontend ci` e build do frontend.

### Wave 7 — F1/P19

**Título:** `[F1/P19] Criar o host Angular com rotas seguras por build`

- **Plano:** [01-19-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-19-PLAN.md)
- **Depende de:** F1/P04
- **Requisitos:** `ARCH-01`, `IDEN-03`
- **Labels:** `fase:1`, `area:frontend`, `area:seguranca`
- **Descrição:** estabelecer HTTP same-origin, XSRF e substituição material das rotas técnicas no build de produção.
- **Aceite:** development e production resolvem rotas distintas; production não importa rotas não-prod; não existe bearer interceptor, token storage ou URL absoluta do backend; host não antecipa produto futuro.
- **Verificação:** builds Angular e `app.component.spec.ts`.

### Wave 8 — F1/P20

**Título:** `[F1/P20] Gerar o cliente Angular a partir do contrato OpenAPI`

- **Plano:** [01-20-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-20-PLAN.md)
- **Depende de:** F1/P19
- **Requisitos:** `API-01`, `API-02`
- **Labels:** `fase:1`, `area:api`, `area:frontend`, `area:build`
- **Descrição:** criar contrato interno `/api/v1` e geração atômica, determinística e protegida do cliente TypeScript.
- **Aceite:** nenhuma operação OIDC externa na spec; duas gerações produzem saída idêntica; concorrência não corrompe a saída; check é read-only; cliente compila.
- **Verificação:** geração, drift check e build do frontend.

### Wave 9 — F1/P05

**Título:** `[F1/P05] Persistir o vínculo estável de identidade OIDC`

- **Plano:** [01-05-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-05-PLAN.md)
- **Depende de:** F1/P20
- **Requisitos:** `IDEN-02`, `DATA-01`, `DATA-02`, `API-01`, `TEST-01`
- **Labels:** `fase:1`, `area:backend`, `area:identidade`, `area:dados`, `area:api`, `area:testes`
- **Descrição:** correlacionar `(iss, sub)` ao usuário BETUFU e provar persistência idempotente no PostgreSQL real.
- **Aceite:** repetição retorna o mesmo UUID sem duplicata; e-mail não define identidade; issuer ou subject diferente cria outra identidade; concorrência converge; resposta expõe somente dados allowlisted.
- **Verificação:** `LocalIdentityTracerIT` com PostgreSQL Testcontainers e Flyway.

### Wave 10 — F1/P21

**Título:** `[F1/P21] Validar o walking skeleton Angular–BFF–PostgreSQL`

- **Plano:** [01-21-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-21-PLAN.md)
- **Depende de:** F1/P05
- **Requisitos:** `IDEN-03`, `API-02`, `OPS-01`, `TEST-01`
- **Labels:** `fase:1`, `area:frontend`, `area:identidade`, `area:testes`, `area:seguranca`
- **Descrição:** provar a jornada browser real somente em development/homolog/harness e sua ausência material em produção.
- **Aceite:** componente usa o cliente gerado; UUID persiste após reload; tokens não aparecem no navegador; login Keycloak preserva apenas dado não sensível; tracer não existe no build production.
- **Verificação:** `node scripts/validate-tracer.mjs` e Playwright.

### Wave 11 — F1/P06

**Título:** `[F1/P06] Implementar sessões BFF com expiração ociosa de oito horas`

- **Plano:** [01-06-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-06-PLAN.md)
- **Depende de:** F1/P21
- **Requisitos:** `IDEN-03`, `IDEN-04`
- **Labels:** `fase:1`, `area:backend`, `area:identidade`, `area:seguranca`, `area:testes`
- **Descrição:** implementar política `PT8H`, sem limite absoluto, com atividade allowlisted e sessões independentes no Redis.
- **Aceite:** limites temporais e concorrência cobertos; health/polling/heartbeat não renovam TTL; sessões de dispositivos diferentes não se afetam; ID é regenerado após autenticação.
- **Verificação:** `SessionPolicyTest` e `RedisSessionIT`.

### Wave 12 — F1/P07

**Título:** `[F1/P07] Implementar UserInfo opt-out e revogação OIDC`

- **Plano:** [01-07-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-07-PLAN.md)
- **Depende de:** F1/P06
- **Requisitos:** `IDEN-01`, `IDEN-02`, `IDEN-03`, `IDEN-04`
- **Labels:** `fase:1`, `area:backend`, `area:identidade`, `area:seguranca`, `area:testes`
- **Descrição:** usar claims allowlisted do ID token sem UserInfo e revogar refresh tokens exclusivamente no servidor.
- **Aceite:** zero chamadas UserInfo; `iss` e `sub` obrigatórios; endpoint de revogação vem do discovery; invalidação local é terminal; repetição e concorrência convergem; Keycloak retorna `invalid_grant` no refresh posterior.
- **Verificação:** `UserInfoOptOutIT`, `OidcRevocationContractTest` e `OidcRevocationKeycloakIT`.

### Wave 13 — F1/P22

**Título:** `[F1/P22] Expor estado seguro da sessão e logout do dispositivo atual`

- **Plano:** [01-22-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-22-PLAN.md)
- **Depende de:** F1/P07
- **Requisitos:** `IDEN-03`, `IDEN-04`, `API-01`
- **Labels:** `fase:1`, `area:backend`, `area:identidade`, `area:api`, `area:seguranca`
- **Descrição:** publicar somente o estado necessário da sessão e encerrar exclusivamente a sessão/dispositivo corrente.
- **Aceite:** GET não renova sessão nem expõe IDs/tokens; logout é POST com CSRF; sessão paralela permanece ativa; falha remota não reativa a sessão; nenhum token aparece em URL, corpo ou log.
- **Verificação:** `SessionControllerIT` e `CurrentDeviceLogoutIT`.

### Wave 14 — F1/P08

**Título:** `[F1/P08] Proteger mutações HTTP e STOMP CONNECT com CSRF`

- **Plano:** [01-08-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-08-PLAN.md)
- **Depende de:** F1/P22
- **Requisitos:** `IDEN-03`, `IDEN-04`
- **Labels:** `fase:1`, `area:backend`, `area:api`, `area:seguranca`, `area:testes`
- **Descrição:** exigir sessão, origem exata e CSRF correspondente em mutações HTTP e tentativas STOMP CONNECT.
- **Aceite:** tentativas ausentes, cruzadas ou de origem divergente recebem 403; bearer STOMP não concede acesso; heartbeat não renova sessão; nenhum teste desabilita CSRF globalmente.
- **Verificação:** `node scripts/validate-tracer.mjs --security`.

### Wave 15 — F1/P09

**Título:** `[F1/P09] Declarar os limites dos módulos Spring Modulith`

- **Plano:** [01-09-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-09-PLAN.md)
- **Depende de:** F1/P08
- **Requisito:** `ARCH-02`
- **Labels:** `fase:1`, `area:backend`, `area:arquitetura`
- **Descrição:** declarar os dez módulos de negócio aprovados sem antecipar entidades, serviços ou endpoints futuros.
- **Aceite:** exatamente dez módulos de negócio mais suporte técnico reconhecido; somente metadados nos domínios futuros; interfaces exportadas explícitas; nenhuma stack distribuída fora do escopo.
- **Verificação:** compilação Maven e enumeração estrutural exata dos dez módulos; a prova bloqueante do grafo ocorre em F1/P23.

### Wave 16 — F1/P23

**Título:** `[F1/P23] Bloquear violações modulares com Modulith e ArchUnit`

- **Plano:** [01-23-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-23-PLAN.md)
- **Depende de:** F1/P09
- **Requisitos:** `ARCH-03`, `TEST-02`
- **Labels:** `fase:1`, `area:backend`, `area:arquitetura`, `area:testes`
- **Descrição:** fazer o build falhar diante de ciclos, acesso a internals ou dependências modulares não autorizadas.
- **Aceite:** grafo permitido passa; fixture negativa prova bloqueio; identity passa isolado; Kafka, Kubernetes e microsserviços não aparecem no POM ou source.
- **Verificação:** `ModulithArchitectureTests`, `ModuleDependencyRulesTest` e `IdentityModuleTest`.

### Wave 17 — F1/P10

**Título:** `[F1/P10] Tornar Flyway e PostgreSQL autoritativos`

- **Plano:** [01-10-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-10-PLAN.md)
- **Depende de:** F1/P23
- **Requisitos:** `DATA-01`, `DATA-02`, `DATA-06`, `TEST-01`
- **Labels:** `fase:1`, `area:backend`, `area:dados`, `area:testes`, `area:seguranca`
- **Descrição:** tornar migrations versionadas a única autoridade de schema e delimitar Redis e armazenamento de objetos.
- **Aceite:** migrations concorrentes aplicam cada versão uma vez; checksum adulterado e `ddl-auto=create|update` falham; não existe H2; Redis não altera identidade autoritativa; bucket rejeita acesso anônimo; bancos BETUFU/Keycloak permanecem isolados.
- **Verificação:** `FlywayMigrationIT`, `FlywayChecksumIT` e `AuthoritativeStorageIT`.

### Wave 18 — F1/P11

**Título:** `[F1/P11] Entregar ambientes locais daily e full`

- **Plano:** [01-11-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-11-PLAN.md)
- **Depende de:** F1/P10
- **Requisitos:** `DATA-06`, `OPS-01`, `OPS-02`
- **Labels:** `fase:1`, `area:infra`, `area:build`, `area:seguranca`
- **Descrição:** oferecer modos locais reproduzíveis e orquestração cross-platform sem composição de comandos de shell.
- **Aceite:** reinicialização preserva volumes; concorrência é serializada; falha para no primeiro exit code; modo full adiciona app/Nginx sem mudar a base; Nginx não entrega segredos, source maps production ou cache autenticado.
- **Verificação:** `node scripts/dev.mjs verify daily` e `verify full`.

### Wave 19 — F1/P24

**Título:** `[F1/P24] Implementar reset local seguro e confirmado`

- **Plano:** [01-24-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-24-PLAN.md)
- **Depende de:** F1/P11
- **Requisitos:** `DATA-06`, `OPS-01`, `OPS-02`
- **Labels:** `fase:1`, `area:infra`, `area:seguranca`, `area:testes`
- **Descrição:** oferecer reset destrutivo separado, em dry-run por padrão, com confirmação literal e allowlist local.
- **Aceite:** confirmação incorreta não altera nada; CI/homolog/produção e paths fora da raiz são recusados; recursos externos permanecem intactos; bootstrap volta saudável após reset autorizado.
- **Verificação:** `node --test scripts/reset-local.test.mjs`.

### Wave 20 — F1/P12

**Título:** `[F1/P12] Padronizar Problem Details, correlação e logs seguros`

- **Plano:** [01-12-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-12-PLAN.md)
- **Depende de:** F1/P24
- **Requisitos:** `API-01`, `OPS-03`
- **Labels:** `fase:1`, `area:backend`, `area:api`, `area:observabilidade`, `area:seguranca`
- **Descrição:** uniformizar erros RFC 9457, correlation IDs e logs estruturados sem exposição de dados sensíveis.
- **Aceite:** uma schema `application/problem+json`; campos proibidos ausentes; violações ordenadas; MDC isolado e removido em `finally`; requests concorrentes não compartilham estado; fixtures sensíveis não aparecem nos logs.
- **Verificação:** `ProblemDetailsIT` e `ObservabilityIT`.

### Wave 21 — F1/P25

**Título:** `[F1/P25] Expor health checks e métricas operacionais seguras`

- **Plano:** [01-25-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-25-PLAN.md)
- **Depende de:** F1/P12
- **Requisito:** `OPS-03`
- **Labels:** `fase:1`, `area:backend`, `area:observabilidade`, `area:seguranca`, `area:testes`
- **Descrição:** expor sinais operacionais mínimos sem identidade, segredos, topologia indevida ou alta cardinalidade.
- **Aceite:** liveness/readiness sanitizados; exportadores controlados por profile; tags fixas; correlation ID e identidade nunca viram tag; endpoints Actuator sensíveis não ficam públicos.
- **Verificação:** `HealthMetricsIT` com fixtures adversariais.

### Wave 22 — F1/P13

**Título:** `[F1/P13] Implementar diagnóstico backend tolerante a falhas`

- **Plano:** [01-13-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-13-PLAN.md)
- **Depende de:** F1/P25
- **Requisito:** `OPS-03`
- **Labels:** `fase:1`, `area:backend`, `area:observabilidade`, `area:testes`, `area:seguranca`
- **Descrição:** produzir projeção diagnóstica mínima, determinística e útil diante de falha parcial.
- **Aceite:** exatamente seis componentes; somente estados allowlisted; nenhuma topologia ou segredo; falha isolada não impede outros probes; timeout total limitado; duplicatas e ausências tratadas de modo determinístico.
- **Verificação:** `DiagnosticServiceTest` e `DiagnosticDegradationIT`.

### Wave 23 — F1/P26

**Título:** `[F1/P26] Restringir diagnóstico a não produção e fechar o OpenAPI`

- **Plano:** [01-26-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-26-PLAN.md)
- **Depende de:** F1/P13
- **Requisitos:** `IDEN-01`, `IDEN-03`, `API-01`, `API-02`, `OPS-03`
- **Labels:** `fase:1`, `area:backend`, `area:api`, `area:observabilidade`, `area:seguranca`
- **Descrição:** autorizar diagnóstico somente para papel técnico em ambientes permitidos e regenerar o cliente final sem drift.
- **Aceite:** técnico recebe 200, anônimo 401 e usuário comum 403; produção não possui bean ou mapping; todos os endpoints `/api/v1` têm paridade com a spec; cliente contém todas as operações.
- **Verificação:** `node scripts/validate-phase1.mjs --contract`.

### Wave 24 — F1/P14

**Título:** `[F1/P14] Implementar sistema visual e ativos auto-hospedados`

- **Plano:** [01-14-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-14-PLAN.md)
- **Depende de:** F1/P26
- **Requisitos:** `ARCH-01`, `OPS-02`
- **Labels:** `fase:1`, `area:frontend`, `area:ui`
- **Descrição:** estabelecer tema escuro acessível, tokens reutilizáveis, tipografia e ícones locais.
- **Aceite:** foco global, alvos de 44px, reduced motion e breakpoints aprovados; nenhum tema claro ou CDN; quatro assets locais com checksum; branding não sugere vínculo oficial com a UFU.
- **Verificação:** `theme.spec.ts` e build do frontend.

### Wave 25 — F1/P27

**Título:** `[F1/P27] Construir o layout autenticado e os estados de sessão`

- **Plano:** [01-27-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-27-PLAN.md)
- **Depende de:** F1/P14
- **Requisitos:** `IDEN-03`, `IDEN-04`
- **Labels:** `fase:1`, `area:frontend`, `area:ui`, `area:identidade`, `area:seguranca`
- **Descrição:** conectar a projeção segura da sessão BFF ao layout inicial sem antecipar funcionalidades futuras.
- **Aceite:** estados checking/authenticated/expired protegem conteúdo; logout envia um POST único; zero storage, bearer, polling ou cronômetro no browser; layout acessível desde 320px.
- **Verificação:** `session-layout.spec.ts` e `node scripts/validate-phase1.mjs --session-ui`.

### Wave 26 — F1/P15

**Título:** `[F1/P15] Implementar a página diagnóstica acessível e resiliente`

- **Plano:** [01-15-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-15-PLAN.md)
- **Depende de:** F1/P27
- **Requisitos:** `API-02`, `OPS-03`
- **Labels:** `fase:1`, `area:frontend`, `area:ui`, `area:api`, `area:observabilidade`, `area:testes`
- **Descrição:** implementar adapter/store e UI diagnóstica usando exclusivamente o cliente OpenAPI gerado.
- **Aceite:** nenhum HTTP ou wire model manual; snapshot válido preservado; quantidades inválidas falham fechadas; seis cartões na ordem contratada; retry somente de GET; oito garantias de UI cobertas.
- **Verificação:** testes do adapter e do componente diagnóstico.

### Wave 27 — F1/P28

**Título:** `[F1/P28] Selar rotas técnicas e provar ausência em produção`

- **Plano:** [01-28-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-28-PLAN.md)
- **Depende de:** F1/P15
- **Requisitos:** `API-02`, `OPS-03`
- **Labels:** `fase:1`, `area:frontend`, `area:testes`, `area:seguranca`, `tipo:gate`
- **Descrição:** permitir tracer/diagnóstico apenas em builds autorizados e provar sua ausência material no artefato production.
- **Aceite:** produção não contém rota, import, chunk ou referência alcançável; E2E cobre saúde, degradação, autorização, expiração, teclado e breakpoints; network log não contém token, analytics, fonte remota ou Actuator bruto.
- **Verificação:** `node scripts/validate-phase1.mjs --diagnostic-ui`.

### Wave 28 — F1/P16

**Título:** `[F1/P16] Executar o gate integral da Fase 1`

- **Plano:** [01-16-PLAN.md](../phases/01-funda-o-segura-da-plataforma/01-16-PLAN.md)
- **Depende de:** F1/P28 e, semanticamente, de todas as issues anteriores
- **Requisitos:** todos os 17 requisitos da Fase 1
- **Labels:** `fase:1`, `tipo:gate`, `revisao:humana`, `area:testes`, `area:seguranca`
- **Descrição:** executar todas as provas automatizadas e a aprovação visual humana, sem aceitar evidência parcial.
- **Aceite:** Maven, Angular, OpenAPI, Compose e Playwright verdes; nenhuma suíte filtrada; revogação real e UserInfo opt-out provados; 17/17 requisitos, 17/17 decisões, 34/34 arestas e 8/8 garantias UI cobertos; revisão humana aprovada.
- **Verificação:** `node scripts/validate-phase1.mjs` e checkpoint humano final.

## Ajustes documentais incorporados

Estes pontos encontrados durante a auditoria foram alinhados e versionados antes da criação remota:

1. **Referência D-15 em F1/P03:** a prova browser completa agora aponta para o plano `01-21`, em conformidade com `01-VALIDATION.md` e a ordem executável.
2. **Cobertura de sincronização em F1/P07:** o `UserInfoOptOutIT` agora deve provar explicitamente repetição e concorrência para o mesmo `(iss, sub)`, convergência em uma identidade e ausência de papel de domínio inferido do Keycloak.
3. **Prova focal em F1/P09:** a verificação agora combina compilação com enumeração estrutural exata dos dez módulos; os limites e o grafo continuam sob o gate bloqueante do plano `01-23`.

## Procedimento de publicação executado

1. As 16 labels próprias foram criadas sem alterar as labels padrão.
2. O milestone foi criado sem prazo.
3. As 28 issues executáveis foram criadas na ordem das waves.
4. A issue de acompanhamento `#29` foi criada com links para as 28 issues.
5. Todas as 29 issues foram associadas ao milestone.
6. Títulos, labels, dependências, links e contagem foram auditados sem divergências.
