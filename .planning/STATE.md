---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_phase: 1
current_phase_name: Fundação Segura da Plataforma
status: In progress
stopped_at: F1/P07 encerrado com revisão cruzada PASS; Wave 12 segue com o Plano 01-21 em execução paralela
last_updated: "2026-08-14"
last_activity: 2026-08-14
last_activity_desc: O Plano 01-07 foi implementado na branch feat/01-07-oidc-revocation, provado com Maven offline agregado 78/78 e aprovado em revisão cruzada; a integração em master ainda não ocorreu.
progress:
  total_phases: 1
  completed_phases: 0
  total_plans: 29
  completed_plans: 12
---

# Estado do Projeto

## Project Reference

Consulte: .planning/PROJECT.md (atualizado em 2026-08-01)

**Valor central:** Tornar os jogos universitários da UFU mais envolventes por meio de uma competição social de palpites, justa, transparente e sem dinheiro real.
**Current focus:** Integrar o Plano 01-07 em `master` e acompanhar o 01-21, que segue em execução paralela na mesma Wave 12

## Current Position

Phase: 1 of 7 (Fundação Segura da Plataforma)
Plan: 12 of 29
Status: In progress — F1/P07 concluído e aprovado em revisão cruzada; o 01-21 continua em execução na Wave 12
Last activity: 2026-08-14 — o 01-07 entregou projeção allowlist de claims, opt-out de UserInfo aplicado por bean e revogação server-side do refresh token, com prova contra Keycloak real.

GitHub: milestone `Fase 1 — Fundação Segura da Plataforma`; issues `#1` a `#9` e `#30` concluídas, com `#11` e a tracking `#29` ainda abertas. Este fechamento não alterou o estado remoto.
Git: o trabalho do 01-07 vive na branch `feat/01-07-oidc-revocation`, criada a partir de `7302e3c`; a integração em `master` e o push permanecem pendentes de autorização própria.

Progress: [████░░░░░░] 41%

## Métricas de desempenho

**Velocidade:**

- Total de planos concluídos: 12
- Duração média: não mensurada — os planos foram acompanhados em múltiplas interações
- Tempo total de execução: não mensurado

**Por fase:**

| Fase | Planos | Total | Média/plano |
|------|--------|-------|-------------|
| 1 | 12 | não mensurado | não mensurada |

**Tendência recente:**

- Últimos 5 planos: 01-20, 01-05, 01-29, 01-06, 01-07
- Tendência: ainda não estabelecida sem duração contínua mensurada

## Contexto acumulado

### Decisões

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
- A árvore TypeScript gerada é machine-owned, exige exatamente 13 paths e o digest canônico `78E92BB17EBC45E25F86CA3D23C66958BDCD1786983DEFDC2DD1050BE8D13D5F`; o digest histórico `701CCD...` é não reproduzível e não pode ser reutilizado.
- O risco de o runner resolver Java pelo `PATH` local permanece aceito temporariamente somente com as propriedades aprovadas de Java 25/Eclipse Adoptium; a identidade binária imutável deve ser decidida para CI.
- A identidade local usa o par OIDC exato `(issuer, subject)` como chave natural case-sensitive e um UUID aleatório como identificador público; e-mail e claims opcionais não participam da correlação.
- A migration Flyway V1 e o JPA validam `public.local_identity` no PostgreSQL 18.4; repetição e concorrência usam `ON CONFLICT DO NOTHING` seguido de `SELECT` em `READ_COMMITTED`, sem no-op update.
- O endpoint `PUT /api/v1/tracer/identity/current` existe em todos os profiles de runtime, inclusive production, usa somente o ID token OIDC e responde por DTO allowlist com quatro campos.
- O lifecycle Maven mantém `*Test` no Surefire e executa `*IT` no Failsafe; a prova agregada do 01-05 terminou com Surefire 8/8 e Failsafe 17/17.
- O Plano 01-29 entregou e comprovou o predecessor explícito do runtime browser: datasource PostgreSQL externo, sessão `dev` deliberadamente em memória até o 01-06 e suporte XSRF SPA oficial do Spring Security 7.1 sem endpoint ou header manual no Angular.
- A prova agregada do 01-29 passou com Maven `36/36`, Node `191/191`, runtime `status: ok`, matriz `403/403/403/200/200`, SQL `BETUFU_RUNTIME_SQL_OK`, JAR imutável, zero conexões Redis e cleanup restaurado.
- O Plano 01-21 depende de 01-29 e 01-06 e compartilha a Wave 12 com 01-07; o 01-08 reutiliza o XSRF já provado e o endurece para origem, logout e STOMP CONNECT.
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
- Decisões de produto ainda em aberto continuam sem resolução implícita; a tabela Key Decisions de PROJECT.md permanece a fonte consolidada.

### Pendências

- Reavaliar `GHSA-frvp-7c67-39w9` quando houver correção upstream compatível com a Matriz B, sem `npm audit fix`, `--force` ou downgrade silencioso.
- O provedor e a topologia S3 de homologação/produção continuam sem escolha; SeaweedFS não deve ser promovido silenciosamente além do ambiente local.
- Homologação e produção ainda precisarão fornecer origens e uma lista não vazia de proxies durante os planos de infraestrutura e implantação.
- A coleção `pendingOriginalIds` pode reter uma entrada após falha terminal de save; acompanhar o P3 de possível crescimento lento no caminho de erro antes de ampliar o lifecycle.
- Os beans opcionais de filtro e lifecycle podem degradar silenciosamente quando ausentes; a opcionalidade necessária aos contextos focais de teste deve ser reavaliada antes de novas integrações de segurança.
- `hasApprovedActivity` está efetivamente inativo porque os nomes de atributo ocultam o marcador; decidir se o método deve ser conectado ao contrato ou removido em manutenção futura.
- Os avisos de Mockito/Byte Buddy sobre agente dinâmico futuro, reconnect do Lettuce durante o teardown e conversão Git LF→CRLF permanecem não bloqueantes e não invalidaram as provas.
- Nenhum plano da Fase 1 assumiu os gatilhos `ADMINISTRATIVE`/`SECURITY`. `01-CONTEXT.md:75` delega essas regras a agentes posteriores, mas a delegação está sem destinatário; enquanto isso durar, o `SessionInvalidationListener` do 01-07 permanece sem consumidor em produção.
- O refresh token vive em `InMemoryOAuth2AuthorizedClientService`, que não sobrevive a restart nem é compartilhado entre réplicas, ao contrário da sessão que o 01-06 moveu para o Redis. Aceito como pendência conhecida, a resolver antes de existir qualquer disparo real de revogação; registrado com os demais P3 em `01-22-PLAN.md`.
- Cinco regressões negativas dos validadores Node estão inertes em checkout Windows: `core.autocrlf=true` entrega CRLF enquanto as fixtures mutam com strings `\n`. Buraco de cobertura do projeto, a corrigir por `.gitattributes` ou normalização das mutações.
- O 01-21 também está dependency-ready, mas a adoção do Playwright mantém checkpoint humano próprio para versão, lockfile, instalação e navegador.

### Bloqueios e pontos de atenção

- Antes do planejamento da Phase 2: validade da verificação, ciclo de vida da matrícula/HMAC e política para matrícula reemitida precisam de decisão explícita ou configuração.
- Antes do planejamento da Phase 3: esportes e categorias exatos do piloto e tabelas de fontes confiáveis precisam ser confirmados.
- Antes do planejamento das Phases 4–6: políticas em aberto sobre odds, créditos, correções de liquidação e ranking com verificação expirada precisam ser decididas quando os requisitos correspondentes exigirem.
- Antes do lançamento público: escolha de nuvem/CI e fluxos legais, de privacidade, retenção e direitos dos titulares precisam ser encerrados.
- Antes da divulgação pública: decidir se o repositório será transferido para uma organização própria da BETUFU; essa mudança não é necessária para executar o MVP e não oferece anonimato absoluto.
- A Wave 12 contém 01-07 e 01-21; ambos estão dependency-ready, mas a ordem ou execução paralela não deve ser escolhida silenciosamente e cada plano continua sujeito aos próprios checkpoints.

## Itens adiados

| Categoria | Item | Status | Adiado em |
|-----------|------|--------|-----------|
| v2 | Notificações, janela formal de contestação, esportes ampliados, aplicativos nativos e MFA/passkeys para usuários | Adiado no roadmap do MVP | 2026-08-01 |

## Session Continuity

Last session: 2026-08-14
Stopped at: F1/P07 concluído, documentado e aprovado em revisão cruzada; falta integrar em `master`
Resume file: .planning/phases/01-funda-o-segura-da-plataforma/01-22-PLAN.md — o 01-21-PLAN.md segue em execução paralela na Wave 12
