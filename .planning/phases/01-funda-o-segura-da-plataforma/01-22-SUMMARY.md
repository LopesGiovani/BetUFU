---
phase: 01-funda-o-segura-da-plataforma
plan: "22"
subsystem: session-http-contract
tags: [session, oidc, logout, bff, csrf, rp-initiated-logout, redis]

requires:
  - phase: 01-06
    provides: "Sessão BFF Redis, SessionPolicy, SessionContractDto e atividade válida seletiva"
  - phase: 01-07
    provides: "Revogação OIDC server-side, SessionInvalidationListener e fiação do cliente OIDC"
provides:
  - "GET /api/v1/session com projeção segura, anônima para o par exato e não renovadora"
  - "POST /api/v1/session/logout que encerra somente o dispositivo atual, protegido por CSRF"
  - "Coordenação RP-initiated logout por client_id, sem id_token_hint no navegador"
  - "Leitor público único do SessionState, sem duplicar o parsing da representação Redis"
affects: [01-08, frontend, security, session]

actuals:
  tasks: 2
  implementation_paths: 7
  documentation_paths: 2
  mutations_executed: 8
  mutations_effective: 8
  mutations_inert_then_made_live: 1
  external_review_rounds: 1

tech-stack:
  added: []
  patterns:
    - "Record de fio aninhado no controller, separado da projeção de política, para que evolução de política não vaze para a API pública"
    - "Terminação local antes de qualquer coordenação remota, espelhando a ordem do OidcSessionRevocationService"
    - "Asserção sobre a string literal nos bytes quando o formato depende de default de biblioteca não fixado pelo projeto"

patterns-established:
  - "Uma mutação só conta como prova depois de observada vermelha; deduzir que ela morderia produz falso PASS na própria disciplina de prova."
  - "permitAll de par exato (método, path) não se estende a subpaths, e isso deve ser assertado explicitamente quando um subpath mutável existe abaixo."
  - "URL devolvida ao navegador é superfície pública: id_token_hint é material de autenticação e não pode aparecer nela."
---

# Plano 01-22 — Estado seguro da sessão e logout do dispositivo atual

`BASE_HEAD`: `d92181a8d6af03608a01554ec5e41ecbafcc28e6`. Nada foi commitado; stage, commit e push
continuam dependendo de autorização própria.

## Task 1 — contrato seguro da sessão

`GET /api/v1/session` publica exatamente `authenticated`, `displayName`, `idleTimeout`,
`absoluteTimeout` e `lastValidActivityAt`. O par exato `(GET, /api/v1/session)` é `permitAll`;
todo o resto de `/api/v1/**` permanece autenticado.

Decisões aplicadas pelo responsável:

- **D1** — anônimo recebe `200` com a projeção não autenticada, e não `302` para o IdP.
- **D2** — `mfaAuthenticated` não é publicado. Nenhuma regra aprovada mapeia `acr`/`amr` nesse
  boolean; D-07 apenas garante que o MFA do login permanece válido na sessão.
- **D3** — `idleTimeout` em ISO-8601 `"PT8H"`, assertado como string literal nos bytes.
- **D4** — record de fio aninhado no `SessionController`; `SessionContractDto` não foi aberto,
  porque é projeção de política do 01-06 enquanto a resposta HTTP é contrato público.

`absoluteTimeout` é emitido como `null` explícito, por D-06.

## Task 2 — logout do dispositivo atual

`POST /api/v1/session/logout` encerra apenas a sessão BFF corrente, remove o authorized client
associado e devolve a URL de RP-initiated logout para o SPA navegar. Não enumera as sessões do par
`(iss, sub)` e não oferece logout-all, per D-08 e D-09.

A ordem é o contrato de segurança e espelha o `OidcSessionRevocationService`: o authorized client
sai primeiro, depois a `HttpSession` é invalidada, depois o `SecurityContext` é limpo, e só então a
coordenação com o provedor é computada. Uma falha de coordenação nunca pode reviver a sessão.

O grant **não** é revogado. Isso é deliberado e coerente com
`OidcSessionRevocationService:151-154`, que revoga somente em `ADMINISTRATIVE` e `SECURITY`:
logout voluntário encerra uma sessão de navegador, não o grant de identidade.

## Revisão externa e correções aplicadas

Uma rodada de revisão externa retornou `CHANGES_REQUIRED` com um P2 documental e cinco P3, sem P0
nem P1. As correções autorizadas pelo responsável foram aplicadas:

- **C1 (P2-1)** — `01-22-PLAN.md` emendado. `backend/openapi/betufu.yaml` saiu de `files_modified`
  e dos dois `<files>`; o `key_links` que apontava para um `operationId` inexistente e o critério
  "Schema OpenAPI não possui..." foram movidos para a nova seção *Bloco material adiado*, ao lado
  das pendências do 01-07. A prohibition de logout-all passou de `unverified` para `verified`,
  citando o IT que a prova — âncora que a revisão não pegou e que estava na mesma classe de
  defasagem.
- **C2** — `<action>` da Task 2 emendado para `client_id` sem `id_token_hint`, com a justificativa
  e o trade-off registrados no próprio plano.
- **C3 (P3-1)** — `SessionLifecycleServiceTest` criado, tornando falsificável o contrato "não cria
  sessão" de `currentState`.

P3-3 (`END_SESSION_UNAVAILABLE` conflaciona três situações) e P3-4 (snapshot corrompido vira 500 no
GET) foram avaliados e mantidos: ambos são fail-closed e deliberados. Ficam como registro.
P3-2 (validador de paridade contrato↔runtime) foi encaminhado ao bloco adiado.

## Desvio ratificado — ausência de `id_token_hint`

O `<action>` da Task 2 pede coordenar o `end_session_endpoint` "com `id_token_hint` quando
disponível". O critério de aceitação da mesma Task proíbe que qualquer token apareça em URL
devolvida, body ou log, e o invariante BFF desta fase mantém material de autenticação no servidor.

Os dois não podem valer juntos: o ID token é material de autenticação, o RP-initiated logout é
front-channel, e uma URL entregue ao navegador vaza por histórico, `Referer` e logs de proxy.
A implementação usa `client_id`, permitido pelo RP-Initiated Logout 1.0 em lugar do hint.

Custo aceito: sem o hint, o provedor pode exibir uma tela de confirmação de logout, porque não
consegue identificar a sessão de forma não interativa.

**Ratificado pelo responsável após a revisão externa**, com um ganho que a primeira análise não
tinha registrado: essa tela de confirmação é também mitigação contra logout forçado por terceiro.
Sem `id_token_hint`, um `GET` no end-session deixa de encerrar sessão silenciosamente, então a troca
melhora a postura contra logout-CSRF ao custo de um clique.

**Condição acionável anexada à ratificação:** o `post_logout_redirect_uri` precisa estar registrado
no realm para ser honrado; sem isso o usuário para na página genérica pós-logout do Keycloak. Isso
deixa de ser apenas limite conhecido e passa a ser item de configuração pendente.

## Fora de escopo, por decisão do responsável

`backend/openapi/betufu.yaml` não foi tocado. A atualização do contrato e a regeneração do cliente
TypeScript machine-owned, com reaprovação da allowlist de 13 arquivos e do digest
`78E92BB17EBC45E25F86CA3D23C66958BDCD1786983DEFDC2DD1050BE8D13D5F` em
`frontend/scripts/generate-api.mjs:84-103`, são bloco separado com checkpoint próprio.

Consequência ativa: `GET /api/v1/session` e `POST /api/v1/session/logout` existem em runtime sem
constar no contrato publicado, e **nenhum validador detecta essa divergência** — grep por
`openapi|betufu.yaml` em `scripts/` retorna apenas `validate-tracer.mjs:176`, não relacionado.

## Paths de implementação

```text
M backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
M backend/src/main/java/br/com/betufu/platform/session/SessionLifecycleService.java
+ backend/src/main/java/br/com/betufu/platform/session/SessionController.java
+ backend/src/main/java/br/com/betufu/platform/session/CurrentDeviceLogoutController.java
+ backend/src/main/java/br/com/betufu/platform/session/OidcLogoutSuccessHandler.java
+ backend/src/test/java/br/com/betufu/platform/session/SessionControllerIT.java
+ backend/src/test/java/br/com/betufu/platform/session/CurrentDeviceLogoutIT.java
```

Duas ampliações de allowlist, ambas ratificadas antes da execução: `SessionLifecycleService.java`
(leitor público único, para não duplicar o parsing da representação Redis) e
`SecurityConfiguration.java` (`permitAll` do par exato e bean do `OidcLogoutSuccessHandler`).
`ValidActivityFilter.java` não foi modificado.

## Prova

Comando: `node scripts/maven.mjs -f backend/pom.xml -o verify`

```text
Surefire 42/42 | Failsafe 49/49 | BUILD SUCCESS | 59,5 s
SessionControllerIT 5/5           CurrentDeviceLogoutIT 5/5
SessionLifecycleServiceTest 3/3   RedisSessionIT 7/7
LocalIdentityTracerIT 17/17       UserInfoOptOutIT 7/7
CsrfSpaIT 5/5                     OidcRevocationKeycloakIT 3/3
```

### Mutações

| # | Mutação | Resultado |
|---|---|---|
| M1 | `/api/v1/session` adicionado ao matcher do `ValidActivityFilter` | VERMELHO — critérios 5 e 6 |
| M2 | `spring.jackson.datatype.datetime.write-durations-as-timestamps=true` | VERMELHO — 3 testes, incl. literal `"PT8H"` |
| M3a | `getSession(true)` em `SessionLifecycleService.currentState` | **VERDE — mutação inerte** |
| M3b | `request.getSession()` incondicional no `SessionController` | VERMELHO — critério 1 |
| M4 | `session.invalidate()` nunca alcançado | VERMELHO — sessão A sobrevive |
| M5 | coordenação computada antes da terminação local | VERMELHO — falha de coordenação revive a sessão |
| M6 | `id_token_hint` adicionado à URL devolvida | VERMELHO — vazamento de token detectado |
| M7 | `@RequestMapping` aceitando `GET` além de `POST` | VERMELHO — GET mutável detectado |
| M3a' | `getSession(true)`, reexecutada após o `SessionLifecycleServiceTest` | VERMELHO — 3/3 do teste focal |

**M3a é o achado mais importante deste plano.** A mutação especificada no pacote aprovado não
mordia: `currentContract` retorna cedo quando `oidcUser == null`, então o caminho anônimo nunca
alcança `currentState`. Ter deduzido que ela morderia teria produzido um falso PASS na própria
disciplina de prova. Corrigida em M3b, mirando o controller.

A inércia da M3a não foi só erro de especificação: ela **localizou a guarda**. Antes dela, um leitor
suporia que `getSession(false)` em `currentState` protegia o caminho anônimo — não protege, nunca
chega lá. A proteção mora no short-circuit `oidcUser == null`, e foi a M3b que mostrou isso.

O resíduo era que o Javadoc de `currentState` afirmava algo que a suíte não podia contradizer — não
falso, não provado, e a distinção passou a importar quando o método virou público. O
`SessionLifecycleServiceTest` fecha isso, e a reexecução **M3a'** confirma: a mutação antes inerte
agora derruba os três testes focais.

## Limites conhecidos da prova

1. Os ITs usam `oidcLogin()`, que injeta uma `Authentication` de teste. Não provam callback OAuth
   real, troca de token nem IdP hospedado.
2. A expiração do critério 6 é forçada movendo apenas o relógio da aplicação. Em produção
   `expiryFor` amarra o TTL do Redis ao mesmo instante do deadline da política, então esse ramo é
   alcançável na prática só sob desvio de relógio entre app e Redis.
3. A URL de end-session é provada como string; nenhuma navegação real ao Keycloak foi executada.
   A tela de confirmação decorrente da ausência de `id_token_hint` não foi observada em navegador.
4. `post_logout_redirect_uri` é derivada da requisição corrente, portanto same-origin por
   construção, e depende do allowlist registrado no provedor como verificação autoritativa. O
   contrato explícito `betufu.web.*` ainda não existe em `src/main` e pertence ao 01-08.
5. A ausência de criação de sessão no caminho anônimo é provada por cookie e por contagem de
   chaves `betufu:session:sessions:*`. Não cobre criação por filtro fora desta cadeia.
6. Warnings não bloqueantes reincidentes: agente dinâmico do Mockito/Byte Buddy e reconnect do
   Lettuce no teardown, já registrados no `STATE.md` na pendência "Os avisos de Mockito/Byte
   Buddy…" (âncora textual: a numeração do `STATE.md` muda a cada fechamento de plano).

## Pendências abertas por este plano

1. `mfaAuthenticated` permanece no `SessionContractDto` sem ser publicado. Decidir a regra
   `acr`/`amr` antes de existir consumidor.
2. Contrato OpenAPI e cliente gerado divergem do runtime, sem detector, até o **Plano 01-26,
   Task 2** executar. Destinatário verificado, não presumido: `01-26-PLAN.md:14-15` já declara o
   yaml e a árvore gerada, `:95` manda comparar todos os mappings `/api/v1` da fase com a spec, e
   `:100` verifica por `scripts/validate-phase1.mjs --contract` — script que ainda não existe. Isso
   também resolve o P3-2 sem criar plano novo, e evita repetir a delegação sem destinatário que
   `STATE.md` registra para os gatilhos `ADMINISTRATIVE`/`SECURITY`. Risco assumido: o 01-26 está
   na Wave 23, então a divergência persiste por muitas ondas.
3. Registrar o `post_logout_redirect_uri` no realm do Keycloak — condição da ratificação do
   `id_token_hint`, sem a qual o redirect pós-logout não é honrado.
4. `END_SESSION_UNAVAILABLE` conflacia três situações (principal não-`OAuth2AuthenticationToken`,
   registro inexistente, discovery sem `end_session_endpoint`). Fail-closed e sanitizado, mas um
   operador não as distingue.
5. Snapshot de sessão corrompido faz o GET responder 500, não contrato não autenticado.
   Deliberado — uma leitura pura não deve encerrar sessão como efeito colateral.
6. As pendências 1 a 6 herdadas do 01-07 seguem abertas. A Task 2 remove o authorized client, mas
   por caminho próprio, sem passar pelo `SessionInvalidationListener` — que continua sem consumidor
   em produção.
7. `STATE.md` ainda não foi atualizado: registra 13 planos concluídos e a Wave 13 como não
   iniciada.
