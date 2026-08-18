---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 1
current_phase_name: Fundação Segura da Plataforma
status: In progress
stopped_at: F1/P26 executado e revisado em 2 rodadas; claim técnica, diagnóstico restrito e contrato OpenAPI final entregues. Wave 23 concluída (01-26); Wave 24 liberada.
last_updated: "2026-08-17"
last_activity: 2026-08-17
last_activity_desc: O Plano 01-26 converteu a claim betufu_roles em ROLE_TECHNICAL por allowlist fechada, restringiu o diagnóstico a dev/homolog com ausência material em produção, fechou os quatro paths do contrato e regenerou o cliente de 13 para 20 arquivos.
progress:
  total_phases: 1
  completed_phases: 0
  total_plans: 31
  completed_plans: 25
---

# Estado do Projeto

## Project Reference

Consulte: .planning/PROJECT.md (atualizado em 2026-08-01)

**Valor central:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.
**Current focus:** Wave 24 — Plano 01-14 (tokens, tema e fontes auto-hospedados); antes do gate do 01-16, o plano do harness de validadores

## Current Position

Phase: 1 of 7 (Fundação Segura da Plataforma)
Plan: 25 of 31 (Plano 01-26 concluído)
Status: In progress — Wave 23 concluída (01-26); Wave 24 liberada com o 01-14
Last activity: 2026-08-17 — Handoff da claim OIDC, diagnóstico restrito por papel e contrato OpenAPI final com cliente regenerado.

GitHub: milestone `Fase 1 — Fundação Segura da Plataforma`; issues `#1` a `#9` e `#30` concluídas, com `#11` e a tracking `#29` ainda abertas.
Git: `master` recebeu o Plano 01-26 em dois commits canônicos, sendo o primeiro `5c89a63` (`feat(01-26)`, 20 paths de código, validadores e cliente gerado); o segundo é o commit de documentação que carrega este arquivo, e seu hash não é citado aqui porque emendar um commit para inserir o próprio hash o mudaria. Sincronização com `origin/master` depende de autorização própria de push.

Progress: [████████░░] 81%

## Métricas de desempenho

**Velocidade:**

- Total de planos concluídos: 25
- Duração média: não mensurada — os planos foram acompanhados em múltiplas interações
- Tempo total de execução: não mensurado

**Por fase:**

| Fase | Planos | Total | Média/plano |
|------|--------|-------|-------------|
| 1 | 25 | não mensurado | não mensurada |

**Tendência recente:**

- Últimos 5 planos: 01-31, 01-12, 01-25, 01-13, 01-26
- Correção de registro: esta seção declarava 14 planos e uma lista defasada enquanto o frontmatter já dizia 18. Reconciliada no fechamento do 01-24.
- Tendência: ainda não estabelecida sem duração contínua mensurada

## Contexto acumulado

### Decisões

- A claim `betufu_roles` do ID token vira `ROLE_TECHNICAL` por allowlist positiva e fechada: exige coleção, depois String, depois igualdade byte a byte com `TECHNICAL`. E-mail, username, grupo e `realm_access` nunca concedem autoridade, e a fiação do mapper é explícita nos dois ramos do `SecurityConfiguration`.
- `GET /api/v1/diagnostic` existe apenas em `dev` e `homolog`. Em produção não há bean nem mapping — ausência material, provada por inspeção do contexto e do `RequestMappingHandlerMapping`, nunca por status 403, que não distingue negado de inexistente.
- O contrato OpenAPI passou a cobrir os quatro paths `/api/v1` da fase, e o modo `--contract` do gate agregado foi implementado: ele compara mappings do código com o contrato e roda o check de drift do cliente gerado.
- **Guard de validador protege propriedade, não identidade de artefato.** Dois guards caíram neste plano sem que nenhuma propriedade fosse violada, e ambos foram escritos quando existia um único artefato do tipo: o `OIDC_BOUNDARY` fixava `Customizer.withDefaults()`, e o `GENERATED_WITH_CREDENTIALS` fixava `identity.service.ts` com contagem exata. É padrão, não coincidência.
- O reset local é o único comando destrutivo da fase. Dry-run é o padrão, `--apply` exige a frase literal `APAGAR DADOS LOCAIS BETUFU` em terminal interativo, e o bootstrap é opcional e desligado.
- Um recurso Docker só é removido pela conjunção de todos os critérios: containers exigem serviço na allowlist, `project=infra`, `oneoff=False` e `config_files` apontando para este checkout; volumes exigem nome literal, `project=infra` e label de volume igual ao nome. Redes ficam fora.
- O label `com.docker.compose.project` sozinho é insuficiente para autorizar remoção, porque `--project-name infra` é genérico e colide com qualquer composição num diretório homônimo. `config_files` é o único discriminador que amarra um recurso a um checkout — verificado presente em containers e ausente em volumes e redes no Compose 5.3.1.
- Prune é o oposto de allowlist: `system prune`, `volume prune`, `image prune` e `down -v` são proibidos no reset, que remove item a item por identidade.
- Um teste de escopo destrutivo não cria o recurso hostil de verdade: se a guarda falhar, o próprio teste causa a perda que deveria detectar. A prova recai sobre a função pura de seleção, com inventário em memória.
- O build backend permanece estritamente em Java 25, Maven 3.9.16, Spring Boot 4.1.0 e Spring Modulith 2.1.0; falhas não autorizam downgrade silencioso.
- Spring Session Redis usa o starter oficial do Boot, com Lettuce transitivo, e toda a família ArchUnit permanece alinhada em 1.4.2.
- Spring atua como BFF OIDC: o Spring Security gerencia `state`, `nonce` e PKCE, enquanto tokens e material de autenticação permanecem no servidor.
- A configuração runtime usa somente variáveis externas; o smoke substitui o issuer por um `ClientRegistration` confidencial em memória e não acessa serviços externos.
- Os profiles `dev`, `homolog` e `prod` ativam automaticamente `runtime`; `integrated` permanece diretamente ativável para testes integrados.
- Origens usam o contrato `betufu.web.*`, forwarded headers dependem de proxies explicitamente confiáveis e o diagnóstico não possui feature flag permissiva.
- O ambiente local usa PostgreSQL, Redis, Keycloak, SeaweedFS e Mailpit por referências OCI fixadas, binds de loopback e volumes nomeados persistentes.
- SeaweedFS permanece somente a implementação S3 local; o backend usa `BETUFU_S3_*` e nenhuma solução de homologação/produção foi selecionada.
- Keycloak mantém somente o papel técnico `TECHNICAL`; o tema `betufu` estende `keycloak.v2` apenas por footer/CSS e usa fontes locais aprovadas por hash.
- O workspace frontend usa a Matriz B com 14 pins diretos exatos, npm 10.9.8 e lockfile v3 governado pelo SHA-256 `FB8FE8A18EA4F72927AF5E3B506E2112F968C3BF98267DA0902EBC5BC40EA841`.
- O bootstrap Angular é standalone, zoneless, strict e sem routing/SSR; development, homolog e production compilam com as configurações e budgets aprovados.
- O host Angular usa uma seam de rotas build-specific: development/homolog importam a lista development e production a substitui por módulo próprio vazio.
- O frontend usa HttpClient same-origin com XSRF e não armazena, constrói ou injeta bearer token; o BFF Spring continuará responsável por tokens e sessão.
- A fronteira Angular é protegida por validador Node fail-closed com 69 regressões negativas e por um AppComponent com router-outlet único.
- A allowlist de cinco pacotes `hasInstallScript` reconhece o grafo, mas não autoriza scripts; `npm ci --ignore-scripts` foi preservado e o risco moderado `GHSA-frvp-7c67-39w9` foi aceito temporariamente.
- O cliente OpenAPI inicial usa o wrapper npm `@openapitools/openapi-generator-cli@2.40.1` somente com `--ignore-scripts` e o motor Java `7.24.0` adquirido separadamente, com 31.904.922 bytes e SHA-256 `4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C`.
- O lockfile do 01-20 é governado pelo SHA-256 `99A856C8C635EB4FB9845DEE6F1F2E7A2A4E8576BF80654B9695B5E6D6C4F035`; sua allowlist de seis entradas `hasInstallScript` apenas reconhece metadados e não autoriza lifecycle.
- A árvore TypeScript gerada é machine-owned e exige exatamente **20 paths** com o digest canônico `27AC44819495367D671C92341400E86EE0B79F4ECAE4229F53BB90F2560C35C0`, aprovado pelo responsável no 01-26. O digest anterior `78E92BB1...` valia para os 13 paths de antes do contrato completo; o histórico `701CCD...` continua não reproduzível.
- O risco de o runner resolver Java pelo `PATH` local permanece aceito temporariamente somente com as propriedades aprovadas de Java 25/Eclipse Adoptium; a identidade binária imutável deve ser decidida para CI.
- A identidade local usa o par OIDC exato `(issuer, subject)` como chave natural case-sensitive e um UUID aleatório como identificador público; e-mail e claims opcionais não participam da correlação.
- A migration Flyway V1 e o JPA validam `public.local_identity` no PostgreSQL 18.4; repetição e concorrência usam `ON CONFLICT DO NOTHING` seguido de `SELECT` em `READ_COMMITTED`, sem no-op update.
- O endpoint `PUT /api/v1/tracer/identity/current` existe em todos os profiles de runtime, inclusive production, usa somente o ID token OIDC e responde por DTO allowlist com quatro campos.
- O lifecycle Maven mantém `*Test` no Surefire e executa `*IT` no Failsafe; a prova agregada do 01-05 terminou com Surefire 8/8 e Failsafe 17/17.
- O Plano 01-29 entregou e comprovou o predecessor explícito do runtime browser: datasource PostgreSQL externo, sessão `dev` deliberadamente em memória até o 01-06 e suporte XSRF SPA oficial do Spring Security 7.1 sem endpoint ou header manual no Angular.
- A prova agregada do 01-29 passou com Maven `36/36`, Node `191/191`, runtime `status: ok`, matriz `403/403/403/200/200`, SQL `BETUFU_RUNTIME_SQL_OK`, JAR imutável, zero conexões Redis e cleanup restaurado.
- O Plano 01-21 comprovou no navegador real o fluxo hospedado, a validação D-15, dois PUTs explícitos com UUID estável, o XSRF same-origin automático, a ausência de tokens no browser e a ausência do tracer em production; o 01-08 reutiliza essa base e a endurece para origem, logout e STOMP CONNECT.
- O Plano 01-06 fixa timeout ocioso `PT8H`, sem prazo absoluto adicional, e usa Spring Session Redis no profile `dev`.
- A allowlist inicial de atividade válida reconhece somente `PUT /api/v1/tracer/identity/current` autenticado e concluído com resposta `2xx`; tráfego técnico ou malsucedido não renova a sessão.
- A persistência seletiva conserva o instante original em operações sem atividade válida, avança o tempo de forma monotônica quando há atividade aprovada e trata session fixation sem reintroduzir uma sessão encerrada.
- A prova agregada do 01-06 passou com Node `263/263` e Maven `56/56` — Surefire `27/27` e Failsafe `29/29`, incluindo `RedisSessionIT` `7/7` — e restaurou containers e redes ao baseline aprovado, sem resíduos do Testcontainers.
- A revisão externa do snapshot final do 01-06 terminou em `PASS`, sem achados P0–P2; os três P3 foram preservados como acompanhamento, sem reabrir a implementação validada.
- O opt-out do `userinfo_endpoint` é aplicado por um bean `OAuth2UserService` com `setRetrieveUserInfo(false)`; depender apenas do escopo `openid` deixaria a decisão silenciosamente reversível.
- A claim `iss` chega como `java.net.URL` após a conversão real do `OidcIdTokenDecoderFactory`; a projeção aceita `String`/`URL`/`URI` por `toString()`, byte-idêntico ao valor cru, preservando a chave natural `(issuer, subject)` do 01-05. `sub`, `preferred_username` e `acr` não são convertidos e mantêm verificação estrita.
- O `revocation_endpoint` vem sempre da metadata de discovery e falha fechado quando ausente; a revogação envia somente refresh token e `token_type_hint`, sem qualquer fallback para access token.
- A ordem da revogação é capturar → remover authorized client → chamar remoto; o material fica retido no estado pendente para retry dentro do TTL, e falha remota nunca revive a sessão.
- O `PENDING_TTL` de cinco minutos limita reuso, não retenção: a purga é oportunista e o material pode permanecer em memória além da janela.
- O Plano 01-07 tocou nove paths de código contra os oito declarados; a ampliação para `SecurityConfiguration.java` foi ratificada pelo responsável, por ser fiação de cliente OIDC.
- Prova nova só é confiável depois de falhar contra o código antigo; o padrão de mutação foi aplicado no `iss` e no timeout de revogação.
- O Plano 01-22 fechou a superfície HTTP de sessão: `GET /api/v1/session` é `permitAll` no par exato `(GET, /api/v1/session)` e não renova o prazo; todo o resto de `/api/v1/**` segue autenticado.
- O logout voluntário usa `client_id` no RP-initiated logout, **sem `id_token_hint`**. O ID token é material de autenticação e vazaria por histórico, `Referer` e log de proxy. A tela de confirmação resultante é também mitigação contra logout forçado por terceiro.
- Logout voluntário remove o authorized client mas **não revoga o grant**, coerente com `OidcSessionRevocationService`, que revoga só em `ADMINISTRATIVE` e `SECURITY`.
- A ordem do logout é contrato de segurança: authorized client, sessão HTTP e SecurityContext caem **antes** de a coordenação com o provedor ser computada, espelhando o 01-07.
- Uma mutação só conta como prova depois de observada vermelha. A M3a do 01-22 foi especificada, aprovada e executou verde: ela mediu que a guarda do caminho anônimo mora no short-circuit do controller, não em `currentState`.
- Decisões de produto ainda em aberto continuam sem resolução implícita; a tabela Key Decisions de PROJECT.md permanece a fonte consolidada.

### Pendências

- **27 regras semânticas de `validate-angular-route-boundary` nunca tiveram prova negativa, e não há controle válido.** O loop de `generatedSourceMutations` asserta sempre `GENERATED_MANIFEST_DIGEST` e ignora o campo `rule` de cada entrada; o digest muda a cada mutação e dispara antes de qualquer regra semântica. Medido: substituindo a constante pelo `rule` declarado, 25 das 27 falham. As duas que passam são as únicas cujo `rule` já é `GENERATED_MANIFEST_DIGEST` — passam por tautologia, e semanticamente pertencem à família `origem absoluta`, cujas outras quatro irmãs declaram `GENERATED_ABSOLUTE_URL`. **São 27 alvos e zero controles.** Entre as regras sem prova está toda a superfície de credencial do cliente gerado. Defeito pré-existente; exige plano próprio e **precede o gate do 01-16**, que consome este validador como prova.
- **Varrer os demais validadores atrás de guards que pinam artefato em vez de propriedade.** Dois apareceram no 01-26 e ambos foram escritos quando existia um único artefato do tipo.
- **O adiamento do contrato OpenAPI tem custo medido.** A dívida aberta no 01-22 e repetida no 01-12 custou **4 dos 7 arquivos novos** do cliente regenerado no 01-26. Paga na época, o 01-26 teria acrescentado 3. Registrar como custo real de adiamento, não como observação.
- ~~Dois validadores em contradição sobre o usuário técnico do Keycloak.~~ **Resolvido pelo Plano 01-30.** `validate-keycloak-realm.mjs` exigia exatamente 9 chaves em `realm.users[0]` e rejeitava `firstName`/`lastName`, que `016390b` (01-29) acrescentou ao realm para destravar o provider `VERIFY_PROFILE`, e que `validate-runtime-prerequisites.mjs:1290-1297` já exigia. O contrato foi ampliado para 11 chaves com valores pinados; o realm não foi alterado, porque o defeito estava no validador. Qual lado cedia não dependeu de julgamento: `01-03-PLAN.md:203` proíbe required action na conta técnica, e `VERIFY_PROFILE` é required action de provider, então o 01-29 respeitou o contrato de origem.
- ~~O reset local nunca executou `--apply`.~~ **Executado pelo responsável em 2026-08-17.** Removeu exatamente os 11 recursos da lista — 7 containers `infra-*` e 4 volumes `betufu-local-*` — com 0 falhas. Verificado depois: zero containers `infra-*` e zero volumes `betufu-local-*` restantes; total da máquina caiu de 36/23 para 29/19, exatamente os 11; `finvise-postgres` e as demais stacks de terceiro intactas; e a segunda execução converge para `(nenhum — nada a fazer)` sobre 29/29 e 19/19. O limite declarado desde o primeiro handoff está fechado: está provado que o reset remove o certo e só o certo.
- Reavaliar `GHSA-frvp-7c67-39w9` quando houver correção upstream compatível com a Matriz B, sem `npm audit fix`, `--force` ou downgrade silencioso.
- O provedor e a topologia S3 de homologação/produção continuam sem escolha; SeaweedFS não deve ser promovido silenciosamente além do ambiente local.
- Homologação e produção ainda precisarão fornecer origens e uma lista não vazia de proxies durante os planos de infraestrutura e implantação.
- A coleção `pendingOriginalIds` pode reter uma entrada após falha terminal de save; acompanhar o P3 de possível crescimento lento no caminho de erro antes de ampliar o lifecycle.
- Os beans opcionais de filtro e lifecycle podem degradar silenciosamente quando ausentes; a opcionalidade necessária aos contextos focais de teste deve ser reavaliada antes de novas integrações de segurança.
- `hasApprovedActivity` está efetivamente inativo porque os nomes de atributo ocultam o marcador; decidir se o método deve ser conectado ao contrato ou removido em manutenção futura.
- Os avisos de Mockito/Byte Buddy sobre agente dinâmico futuro, reconnect do Lettuce durante o teardown e conversão Git LF→CRLF permanecem não bloqueantes e não invalidaram as provas.
- Nenhum plano da Fase 1 assumiu os gatilhos `ADMINISTRATIVE`/`SECURITY`. `01-CONTEXT.md:75` delega essas regras a agentes posteriores, mas a delegação está sem destinatário; enquanto isso durar, o `SessionInvalidationListener` do 01-07 permanece sem consumidor em produção.
- O refresh token vive em `InMemoryOAuth2AuthorizedClientService`, que não sobrevive a restart nem é compartilhado entre réplicas, ao contrário da sessão que o 01-06 moveu para o Redis. Aceito como pendência conhecida, a resolver antes de existir qualquer disparo real de revogação; registrado com os demais P3 em `01-22-PLAN.md`.
- ~~Cinco regressões negativas dos validadores Node inertes em checkout Windows.~~ **Medido no 01-31: era uma, não cinco.** Os dois validadores que mutam texto têm guarda de fixture-mudou, então mutação inerte falha em vez de passar escondida, e o terceiro muta objeto JSON. A única inerte foi corrigida por `replaceAcrossLines`, que casa `\r?\n` e preserva o CRLF real da fixture. **A causa raiz permanece:** `core.autocrlf=true` segue ativo e `.gitattributes` não cobre `backend/src/main/java/**`, portanto mutação multilinha nova sobre fonte Java deve usar aquele helper. A contagem de cinco no `CLAUDE.md` está superestimada e merece correção.
- O Playwright 1.62.1 e o Chromium 1234 do 01-21 foram fixados, inspecionados e validados; qualquer atualização, nova instalação ou aquisição de navegador continua sujeita a checkpoint próprio.
- `GET /api/v1/session`, `POST /api/v1/session/logout` e o schema RFC 9457 de Problem Details (Plano 01-12) existem em runtime sem constar em `backend/openapi/betufu.yaml`, e nenhum validador detecta a divergência. Bloco atribuído ao **Plano 01-26, Task 2** (Wave 23), que já declara o yaml e a árvore gerada e verifica por `scripts/validate-phase1.mjs --contract` — script ainda inexistente. Divergência conhecida, atribuída e sem detector até lá.
- `/ws` e o gate STOMP do 01-08 existem no backend mas não têm rota no `frontend/proxy.conf.json` — `validate-tracer.mjs:775-776` congela o proxy nos três segmentos BFF — nem cobertura E2E em navegador. Atribuído à Phase 7 (`ROADMAP.md:245-250`, `CHAT-01`/`CHAT-02`), que entrega o roteamento `/ws`, o repasse explícito de `Origin` (necessário para que o `setAllowedOrigins` não fique inerte contra CSWSH) e a conexão STOMP real. Sem detector até lá.
- Registrar o `post_logout_redirect_uri` no realm do Keycloak. Condição da ratificação do `id_token_hint`: sem isso o redirect pós-logout não é honrado e o usuário para na página genérica do provedor.
- `mfaAuthenticated` permanece no `SessionContractDto` sem ser publicado pela API; decidir a regra `acr`/`amr` antes de existir consumidor.
- `END_SESSION_UNAVAILABLE` conflacia principal inesperado, registro inexistente e discovery sem `end_session_endpoint`; fail-closed e sanitizado, mas um operador não distingue os três.
- Snapshot de sessão corrompido faz `GET /api/v1/session` responder 500 em vez de contrato não autenticado. Deliberado: uma leitura pura não deve encerrar sessão como efeito colateral.

### Bloqueios e pontos de atenção

- Antes do planejamento da Phase 2: validade da verificação, ciclo de vida da matrícula/HMAC e política para matrícula reemitida precisam de decisão explícita ou configuração.
- Antes do planejamento da Phase 3: esportes e categorias exatos do piloto e tabelas de fontes confiáveis precisam ser confirmados.
- Antes do planejamento das Phases 4–6: políticas em aberto sobre odds, créditos, correções de liquidação e ranking com verificação expirada precisam de decisão explícita ou configuração.
- Antes do lançamento público: escolha de nuvem/CI e fluxos legais, de privacidade, retenção e direitos dos titulares precisam ser encerrados.
- Antes da divulgação pública: decidir se o repositório será transferido para uma organização própria da BETUFU; essa mudança não é necessária para executar o MVP e não oferece anonimato absoluto.
- A Wave 19 foi concluída com o 01-24, único plano da onda; a Wave 20 está liberada e começa pelo 01-12.
- **A suíte Node está integralmente verde: 481/481.** O gate agregado do 01-16 não é mais bloqueado por nenhum detector Node — `validate-keycloak-realm` foi destravado pelo 01-30 e a última falha, de CRLF, pelo 01-31.
- A allowlist do 01-24 declarava 4 paths; `STATE.md` e `ROADMAP.md` foram ratificados pelo responsável no fechamento, para que o registro do projeto não continuasse descrevendo o 01-11. Ampliação registrada aqui, e não no SUMMARY, porque o SUMMARY estava sob `PASS` externo e editá-lo o invalidaria.

## Itens adiados

| Categoria | Item | Status | Adiado em |
|-----------|------|--------|-----------|
| v2 | Notificações, janela formal de contestação, esportes ampliados, aplicativos nativos e MFA/passkeys para usuários | Adiado no roadmap do MVP | 2026-08-01 |

## Session Continuity

Last session: 2026-08-16
Stopped at: F1/P30 concluído com PASS externo; 01-24 e 01-30 commitados localmente, `origin/master` ainda em `6ca340a` e dessincronizado.
Resume file: .planning/phases/01-funda-o-segura-da-plataforma/01-30-SUMMARY.md — depois, `01-12-PLAN.md` continua a Wave 20
