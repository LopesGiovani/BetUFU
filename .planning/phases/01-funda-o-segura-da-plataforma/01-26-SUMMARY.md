---
phase: 01-funda-o-segura-da-plataforma
plan: "26"
subsystem: oidc-authority-and-diagnostic-surface
tags: [oidc, claim, authority, diagnostic, profile, openapi, generated-client]

requires:
  - phase: 01-03
    provides: "Realm, claim betufu_roles aprovada no Gate 1 e contrato da conta tecnica"
  - phase: 01-13
    provides: "Projecao diagnostica e probes tolerantes a falha em platform.diagnostic"
  - phase: 01-20
    provides: "Pipeline transacional e recuperavel de geracao do cliente"
provides:
  - "Handoff allowlistado da claim OIDC para ROLE_TECHNICAL"
  - "GET /api/v1/diagnostic restrito por papel e ausente materialmente em producao"
  - "Contrato OpenAPI com os quatro paths da fase e cliente Angular regenerado"
  - "Modo --contract do gate agregado, que antes nao existia"
affects: [01-16, 01-27, 01-28, frontend]

actuals:
  tasks: 2
  implementation_paths: 12
  mutations_executed: 13
  mutations_effective: 11
  mutations_inert_by_preexisting_harness: 2
  allowlist_expansions_ratified: 3
  generated_client_paths: 20
  external_review_rounds: 2
  preexisting_defect_found: "27 regras semanticas sem prova negativa em validate-angular-route-boundary"

tech-stack:
  added: []
  patterns:
    - "Guard de validador protege propriedade, nao identidade de artefato nem contagem fixa"
    - "Ausencia em producao provada por inspecao de bean e mapping, nunca por status 403"
    - "IT de autorizacao faz a claim atravessar o mapper real, para provar handoff e nao apenas regra"

patterns-established:
  - "Prova agregada neste repositorio sao duas suites: Maven verify sem filtro e os validadores Node. Declarar conclusao com uma delas e cobertura parcial em silencio."
  - "Um guard escrito quando existia um unico artefato tende a pinar aquele artefato; a primeira ampliacao legitima o derruba sem que nenhuma propriedade tenha sido violada."
  - "Entrada de teste cujo comentario alega provar o que ela nao prova e pior que entrada ausente."
---

# Plano 01-26 — Claim tecnica, diagnostico restrito e contrato final

`BASE_HEAD`: `9f5b263c66c3b5cefe59added6df881dd39b2fe6`.
Nada commitado; stage, commit e push dependem de autorizacao propria.

## Task 1 — a claim vira autoridade

`OidcTechnicalAuthorityMapper` converte a claim `betufu_roles` do ID token em `ROLE_TECHNICAL`,
por allowlist positiva e fechada. Exige `Collection<?>`, depois `String`, depois igualdade byte a
byte com `TECHNICAL`. Sem `equalsIgnoreCase`, sem `contains`, sem `startsWith`. Nao le
`preferred_username`, `email`, `groups` nem `realm_access`. As autoridades originais sao sempre
preservadas: o mapper acrescenta, nunca substitui.

A fiacao e explicita nos dois ramos do `SecurityConfiguration`, e nao por deteccao implicita de
bean — mesma razao que levou o 01-07 a declarar o `oidcUserService`: opt-in implicito e
silenciosamente reversivel. A revisao externa confirmou, por inspecao de bytecode, que
`OidcAuthorizationCodeAuthenticationProvider` tem campo `authoritiesMapper` e invoca
`mapAuthorities` — o mapeamento e do provider, e `userInfoEndpoint()` e apenas onde o DSL o expoe.
Isso fechou um risco que eu nao havia nomeado: com o UserInfo desabilitado pelo 01-07, a fiacao
poderia ter sido codigo morto.

**A medicao do tipo da claim foi feita, nao deduzida.** `roleClaimHasNoDefaultConverter` executa
`OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()` e asserta ausencia de
`betufu_roles` mais presenca de `iss` como controle — sem a segunda linha, um mapa vazio faria a
primeira passar por acidente. Limite declarado: isso prova que o Spring nao converte, nao qual
classe concreta o parser produz. A mitigacao e de desenho — a guarda aceita `Collection<?>`, larga
o bastante para nao repetir o P0 do `iss` como `java.net.URL`.

## Task 2 — diagnostico restrito e contrato fechado

`GET /api/v1/diagnostic` existe apenas em `dev` e `homolog`. Em `prod` **nao ha bean nem mapping**,
e o IT prova isso inspecionando o contexto e o `RequestMappingHandlerMapping`, nunca por status —
um teste que afirma 403 nao distingue "negado" de "inexistente", e D-02 exige inexistente. E o
equivalente Spring da inspecao de bundle que o 01-21 usou para o tracer.

O `DiagnosticControllerIT` faz a claim bruta atravessar o **mapper real** antes de montar o
principal. Sem isso o teste forneceria `ROLE_TECHNICAL` pronto e provaria a regra de autorizacao,
nao o handoff.

## Tres emendas ratificadas antes de editar

O plano foi escrito antes de decisoes que o precedem, e a auditoria inicial as encontrou.

1. **Path.** O plano declarava `br/com/betufu/diagnostic/`. Existem exatamente 11 subpacotes
   diretos de `br.com.betufu` contra o `hasSize(11)` de `ModulithArchitectureTests:48`; criar o
   pacote de topo produziria um decimo-segundo modulo e derrubaria o gate. Controller e IT foram
   para `platform.diagnostic`, junto da projecao do 01-13.
2. **Sem `DiagnosticSecurityConfiguration`.** Duas `SecurityFilterChain` sem `@Order` teriam
   precedencia dependente de ordem de bean, nao auditavel por leitura. O matcher entrou na cadeia
   unica, antes de `/api/v1/**`.
3. **`scripts/validate-phase1.mjs` na allowlist.** O `<verify>` da Task 2 apontava para
   `--contract`, que estava **comentado** em `:92`. O criterio de aceite era inexequivel.

## A divida do 01-22, medida

O contrato tinha 1 path e o codigo tinha 3 mappings: `GET /api/v1/session` e
`POST /api/v1/session/logout` nunca entraram no `betufu.yaml`. Com o diagnostico, a regeneracao
fechou **quatro** paths.

O custo apareceu em numeros: dos 7 arquivos novos do cliente, **4 vieram da divida do 01-22**.
Paga na epoca, este plano teria acrescentado 3.

```text
cliente gerado   13 -> 20 paths
digest           78E92BB1... -> 27AC44819495367D671C92341400E86EE0B79F4ECAE4229F53BB90F2560C35C0
```

A sequencia preservou o checkpoint humano: gerar em staging, reportar contagem e digest, o
responsavel aprovar os dois numeros, e so entao constantizar em `generate-api.mjs:84-99`.
Regeneracao **nao exigiu rede** — o motor 7.24.0 esta no disco e e invocado por `java -jar`.

## Dois guards que pinavam artefato, nao propriedade

A execucao derrubou dois validadores sem que nenhuma propriedade tivesse sido violada. Ambos
foram escritos quando existia um unico artefato do seu tipo.

**`OIDC_BOUNDARY`** exigia literalmente `.oauth2Login(Customizer.withDefaults())`. A fiacao do
mapper usa lambda, e o guard caiu — derrubando 7 testes. A mensagem diz "Authorization Code deve
ser preservado", que e propriedade; o regex fixava sintaxe. Ampliado para aceitar
`withDefaults()` **ou** lambda, continuando a recusar ausencia, chamada vazia e `null`.

**`GENERATED_WITH_CREDENTIALS`** permitia `withCredentials` apenas em `identity.service.ts` com
exatamente 3 ocorrencias. Os servicos novos usam o mesmo encadeamento dormente e o guard caiu —
9 testes. Ampliado para o invariante real, medido: `ocorrencias == 3 x spreads`,
`destructurings == spreads`, e `withCredentials: true` proibido em qualquer arquivo.

## Prova

```text
node scripts/maven.mjs -f backend/pom.xml -o verify
  Surefire   11 classes ·  79 testes · 0 falhas · 0 erros · 0 ignorados
  Failsafe   17 classes · 113 testes · 0 falhas · 0 erros · 0 ignorados

node --test scripts/*.test.mjs        483/483
node scripts/validate-phase1.mjs --contract   PASS (paridade 4/4, zero drift)
npm --prefix frontend run build               209.38 kB, sem vazamento
```

O bundle production nao contem `diagnostic`, `session`, `getDiagnostic` nem
`logoutCurrentSession` — zero ocorrencias. O barrel entra, os servicos nao usados saem por
tree-shaking, mantendo o padrao do 01-21.

### Mutacoes — 13 executadas, 11 efetivas, 2 inertes

| # | Mutacao | Resultado |
|---|---|---|
| N1 | `equalsIgnoreCase` no papel | vermelho |
| N2 | `contains` no lugar de `equals` | vermelho |
| N3 | aceitar String isolada | vermelho |
| N4 | ler `preferred_username` | vermelho |
| N5 | ler `groups` | vermelho |
| M-A | remover `@Profile` do controller | vermelho — os dois testes de ausencia |
| M-B | `hasRole` para `authenticated` | vermelho |
| M-C | remover path do contrato | vermelho — paridade |
| D1 | reverter `OIDC_BOUNDARY` ao regex rigido | vermelho — 7 testes |
| D2 | `OIDC_BOUNDARY` frouxo (`.oauth2Login\(` solto) | vermelho |
| E2 | voltar a prender `identity.service.ts` | vermelho — 9 testes |
| **E1** | remover o invariante de contagem | **verde — inerte** |
| **E3** | permitir `withCredentials: true` | **verde — inerte** |

N4 merece registro: previ que derrubaria `otherClaimsNeverGrant` e nao derrubou, porque
`preferred_username` e String e cai na guarda de tipo antes. Refeita como N5 lendo `groups`, que
na fixture hostil e lista com `TECHNICAL`. Sem a correcao, uma mutacao estaria creditada a um
criterio que ela nao tocava.

## O achado que explica E1 e E3

O loop de `generatedSourceMutations` em `validate-angular-route-boundary.test.mjs` assere
**sempre** `GENERATED_MANIFEST_DIGEST`, ignorando o campo `rule` de cada entrada. **27 entradas
declaram `rule` que nunca e lido**, e nenhum teste do repositorio assere
`GENERATED_WITH_CREDENTIALS` — busca direta retorna zero.

**Medido, nao estimado.** Substituindo a constante por `generatedSourceMutation.rule ?? ...` e
executando: **25 das 27 falham**. As duas que passam sao as unicas cujo `rule` declarado ja e
`GENERATED_MANIFEST_DIGEST` — ou seja, coincidem com a assercao fixa. Elas nao provam que o
harness funciona; passam por tautologia.

E ha um agravante que a revisao externa localizou: essas duas chamam-se `origem absoluta
construida por concatenacao` e `origem absoluta depois de regex com barras escapadas`, e as
outras quatro entradas da mesma familia `origem absoluta ...` declaram `GENERATED_ABSOLUTE_URL`.
Duas foram etiquetadas como digest.

**O numero correto e 27 de 27, com zero controles.** Nenhuma regra semantica jamais teve prova
negativa. Um plano de correcao que assuma dois controles validos herda baseline falsa.

Qualquer mutacao num arquivo gerado quebra o digest primeiro, e o digest mascara todas as regras
semanticas. Os testes passam provando algo mais fraco do que aparentam.

E defeito **pre-existente**, nao introduzido aqui: o lado negativo de `GENERATED_WITH_CREDENTIALS`
nunca teve cobertura, antes ou depois da ampliacao. O lado positivo tem, e E2 prova.

Cheguei a inserir uma entrada nova alegando fechar essa lacuna, e a **removi** ao medir que ela
nao mordia. Entrada cujo comentario alega provar o que nao prova e pior que entrada ausente.

## Limites conhecidos da prova

1. **Nenhum login real.** O handoff foi provado no nivel do mapper e do IT com token construido; o
   pipeline Keycloak para Spring nao subiu.
2. **A classe concreta de `betufu_roles` em runtime nao foi medida**, e sim mitigada por desenho.
3. **`GENERATED_WITH_CREDENTIALS` nao tem prova negativa**, pelo defeito de harness acima.
4. **O diagnostico nunca foi exercitado contra dependencias reais** — os probes respondem no
   contexto focal, sem PostgreSQL, Redis ou S3 de verdade.
5. O build Angular compila e nao vaza, mas **nenhuma navegacao real** foi feita.

## Pendencias

1. **Harness de `validate-angular-route-boundary.test.mjs`.** Corrigir exige fixture com digest
   realinhado por entrada, para a regra semantica ser alcancada; afeta **27 entradas, com zero
   controles validos** — as duas que hoje passam sao tautologicas. **Recomendado
   abrir plano proprio antes do gate do 01-16**, que depende desses validadores.
2. **Varrer os demais validadores** atras de guards que pinam artefato em vez de propriedade. Dois
   apareceram neste plano, ambos escritos quando existia um unico artefato do tipo.
3. `SecurityConfiguration` recebeu a quinta edicao da fase. Vale avaliar um plano de manutencao da
   ordem de matchers antes que ela deixe de ser legivel.
