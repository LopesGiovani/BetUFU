# Fase 2: Contas, Verificação e Operações Governadas — Pesquisa

**Pesquisado em:** 2026-08-20
**Domínio:** identidade OIDC federada (Keycloak 26.7.1), sessão distribuída (Spring Session Redis 4.1.0), criptografia de pseudonimização (HMAC), autorização por papel local e trilha de auditoria imutável em PostgreSQL 18.
**Confiança geral:** ALTA nas descobertas de artefato e código; MÉDIA nas de comportamento Keycloak que só a documentação sustenta.

---

## Convenção de prova deste documento

`CLAUDE.md`, seção *Padrões de prova*, exige evidência empírica para toda afirmação sobre framework. Cada afirmação abaixo carrega **exatamente um** rótulo:

| Rótulo | Significado |
|---|---|
| `[ARTEFATO]` | Inspecionei o JAR sob `~/.m2`, o `.class` ou o arquivo de configuração em disco. O arquivo é nomeado. |
| `[CÓDIGO]` | Verifiquei contra fonte deste repositório. `caminho:linha` é citado. |
| `[DOC — NÃO VERIFICADO]` | Li apenas em documentação oficial ou conhecimento prévio. **Não foi medido nesta máquina.** O planejador deve converter em tarefa de verificação, nunca em suposição. |

Toda afirmação `[DOC — NÃO VERIFICADO]` está consolidada em *Assumptions Log* no fim do documento, com o comando ou a inspeção discriminante que a resolveria.

**Método usado nas verificações `[ARTEFATO]`:** escrevi um parser de class file em JavaScript no diretório de scratchpad (fora do repositório) que lê o pool de constantes, a superclasse, as interfaces, os métodos e as constantes UTF-8 de um `.class`. Nenhuma JVM foi executada — `javap` executa Java e está fora do modo somente leitura. Os `.class` foram extraídos dos JARs sob `~/.m2` com `unzip`. O `src.zip` do JDK 25.0.4.7 em `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/lib/src.zip` foi usado para as afirmações sobre `java.base`.

---

<user_constraints>

## User Constraints (de `02-CONTEXT.md`)

### Decisões travadas

Copiadas verbatim de `02-CONTEXT.md`, seção `<decisions>`. **Não relitigar.**

**Escopo da verificação UFU**

- **D-25:** A verificação documental do vínculo UFU sai do MVP. No MVP a pessoa **autodeclara** a matrícula e a plataforma verifica somente a **unicidade** por HMAC; não há envio de comprovante, revisor, estados de análise nem exclusão de arquivo. — **Reversibility:** costly.
- **D-26:** A matrícula registrada **não expira**. O estado `expirado` não é implementado. — **Reversibility:** costly.
- **D-27:** O risco de alguém registrar matrícula alheia foi apresentado ao responsável e **aceito explicitamente**, inclusive a ausência de meio de disputa. A única saída é a correção administrativa da `D-32`.

**Matrícula: formato, normalização e duplicidade**

- **D-28:** A matrícula da UFU é **alfanumérica**. Descartar não-dígitos destruiria informação significativa e é proibido.
- **D-29:** A normalização, aplicada **antes** de qualquer cálculo criptográfico, é: recortar espaços das pontas, converter letras para maiúscula e descartar separadores não alfanuméricos (hífen, ponto, barra, espaço interno). O resultado normalizado é então validado contra um padrão de matrícula UFU, e entrada malformada é recusada como erro de formulário **antes** de gerar HMAC. — **Reversibility: one-way.**
- **D-30:** Colisão de matrícula produz **recusa explícita**, combinada com **limite de tentativas** por conta e por origem. Toda tentativa recusada é auditada (`AUDT-01`).
- **D-31:** O oráculo de enumeração é mitigado, não eliminado: cada conta registra no máximo uma matrícula. O limite de tentativas é a segunda barreira.
- **D-32:** A matrícula é **imutável para o usuário**. Somente o administrador geral a corrige, com motivo obrigatório e trilha completa; a correção libera a matrícula antiga e registra a nova.

**Chave HMAC: guarda, versionamento e rotação**

- **D-33:** A chave vive em **variável de ambiente `BETUFU_*`**, fora do PostgreSQL e fora do Git. A aplicação **falha fechada** se a variável estiver ausente.
- **D-34:** A versão da chave fica numa **coluna dedicada** ao lado do HMAC, com restrição de valor válido, indexável junto do HMAC e declarada em migration Flyway, no estilo de `V1` e `V2`.
- **D-35:** Rotação planejada **mantém as chaves antigas ativas somente para comparação**: escritas novas usam a chave corrente, e a checagem de unicidade calcula o candidato sob **todas as versões ativas** e compara com cada uma. — **Reversibility: one-way** na direção oposta.
- **D-36:** Custo aceito: rotação assim **reduz o uso da chave, não a exposição**.
- **D-37:** Comprometimento da chave = **rotacionar, abrir janela de reinformação sob a nova versão e expurgar os HMACs da versão comprometida**. Na Fase 2 entra como **procedimento escrito**.

**Cadastro, confirmação de e-mail e recuperação**

- **D-38:** `verifyEmail: true` no realm. O Keycloak impõe `VERIFY_EMAIL` como *required action* e o login **não conclui** até a confirmação.
- **D-39:** O perfil BETUFU é **obrigatório antes de navegar**. Invariante *toda conta tem apelido* (`apelido NOT NULL`). — **Reversibility:** costly.
- **D-40:** Redefinição de senha **encerra todas as sessões BETUFU** daquela identidade, via gatilho `SECURITY`. Primeiro consumidor em produção do `SessionInvalidationListener`. O mecanismo de integração fica em aberto; *back-channel logout* do OIDC é o candidato padrão, **a confirmar na pesquisa**.
- **D-41:** O identificador de login é o **e-mail**. O apelido público nunca serve como identificador de acesso. O vínculo estável continua sendo `(iss, sub)`.

**Perfil público e apelido**

- **D-42:** O apelido **não é único**. A identidade real é o UUID público da conta.
- **D-43:** As listas exibem **somente o apelido**; a distinção entre homônimos vive no perfil.
- **D-44:** O apelido aceita **ASCII estrito, 3 a 20 caracteres**: `A-Z`, `a-z`, dígitos e os separadores `_` e `-`.
- **D-45:** *Equipe favorita* é **referência opcional nula**, com a interface omitindo o seletor até haver equipes.

**Papéis operacionais**

- **D-46:** A Fase 2 modela os **três** papéis: administrador geral, administrador de competição e moderador. Papéis continuam autoritativos na BETUFU/PostgreSQL, nunca no Keycloak (`D-20`).
- **D-47:** O primeiro administrador geral nasce por **semente de configuração de ambiente** (variável `BETUFU_*`), aplicada na inicialização de forma **idempotente** e auditada.
- **D-48:** **(a)** concessão cujo alvo seja o próprio ator é bloqueada e auditada; **(b)** revogar o próprio papel de administrador geral é recusado quando ele for o **último**. Cada guarda deve virar um teste que falha se a guarda sumir.
- **D-49:** Concessão ou revogação de papel **encerra todas as sessões da identidade afetada**, via gatilho `ADMINISTRATIVE`. Segundo consumidor do `SessionInvalidationListener`.

**MFA**

- **D-50:** **MFA é opcional em todo o MVP.** Nenhum comando da plataforma é condicionado a ele.
- **D-51:** Risco aceito: a governança passa a depender apenas de senha. As guardas de duas pessoas distintas permanecem e são a mitigação real.
- **D-52:** O campo `mfaAuthenticated` **permanece ausente** do contrato de sessão.

### Discrição dos agentes posteriores

Pontos em que o responsável não fixou regra. Decidir de forma conservadora e registrar a suposição:

- Frequência de troca do apelido — livre, com intervalo mínimo, ou definitiva.
- **Padrão exato da matrícula UFU** — a regex da `D-29` depende do formato real. **Confirmar na pesquisa antes de fixar**, preferindo padrão que não rejeite pós-graduação ou alunos antigos. → *Ver `Open Questions` OQ-1: não foi possível confirmar autoritativamente. Recomendação registrada.*
- **Algoritmo e tamanho da chave HMAC**, e o formato de armazenamento do digest. → *Ver `HMAC da matrícula`.*
- **Valores numéricos do limite de tentativas** da `D-30` e da janela de contagem. → *Ver `Open Questions` OQ-2.*
- **Como a semente da `D-47` identifica a pessoa** — por e-mail ou pelo par `(iss, sub)`. → *Ver `Semente do primeiro administrador`.*
- **Mecanismo de integração do gatilho `SECURITY`** da `D-40`. → *Ver `Gatilho SECURITY`.*
- **Códigos de erro estáveis e textos**, respeitando `D-14` a `D-17` e o `ApiErrorCode` existente. → *Ver `ApiErrorCode`.*
- **Conteúdo do painel operacional** (`OPER-06`) — pertence ao `UI-SPEC.md`, **já aprovado**, seção S4.

### Ideias adiadas (FORA DE ESCOPO)

- Verificação documental do vínculo UFU (`VERI-01`–`VERI-05`, `VERI-08`, `VERI-12`, `OPER-05`) — movida para a v2.
- Obrigatoriedade de MFA — `D-50`. `PASS-01` permanece na v2.
- Step-up de MFA por operação sensível — sem consumidor.
- Discriminador visível para homônimos — recusado em `D-43`.
- Prazo de validade do vínculo UFU — `D-26`.

</user_constraints>

---

<phase_requirements>

## Phase Requirements

| ID | Descrição (verbatim de `REQUIREMENTS.md`) | Suporte da pesquisa |
|----|-------------|---------------------|
| AUTH-02 | Pessoa pode criar uma conta pelo fluxo hospedado do Keycloak com e-mail e senha e completar o perfil BETUFU, sem que a aplicação receba ou persista a senha. | §1 — `registrationAllowed: true` + as três travas de validador que precisam mudar juntas. §5 — tabela de perfil. |
| AUTH-03 | Usuário pode confirmar o e-mail, entrar, permanecer autenticado entre acessos e encerrar a sessão. | §1 — `verifyEmail: true`, efeito da required action `VERIFY_EMAIL` no authorization code flow, e o efeito colateral sobre o usuário técnico. |
| AUTH-04 | Usuário pode recuperar o acesso por um fluxo seguro de redefinição de senha. | §1 — `resetPasswordAllowed: true` e SMTP→Mailpit já prontos e já pinados. |
| PROF-01 | Usuário pode editar apelido e equipe favorita dentro das regras públicas de perfil. | §5 — `betufu_profile` pendurada em `local_identity`; equipe favorita como FK nula (D-45). |
| PROF-02 | Perfil público exibe somente apelido, equipe favorita, posição permitida e conquistas, sem revelar e-mail, matrícula, HMAC, comprovante ou identidade civil. | §5 + §6 — DTO allowlist; `LogSanitizer` **não** cobre os campos novos. |
| VERI-06 | A matrícula normalizada gera um HMAC único para impedir duplicidade, sem armazenar o número original de forma consultável. | §3 — construção `HmacSHA256`, `bytea`, sem coluna de número original. |
| VERI-07 | Uma matrícula pode estar associada a somente uma conta BETUFU; conflito ou colisão é bloqueado e auditado, sem criar segunda associação. | §3 — índice único parcial + verificação multi-versão e a corrida que o índice sozinho não cobre. |
| VERI-09 | Somente conta com matrícula UFU registrada e única pode apostar em eventos internos e integrar o ranking UFU. | §5 — o predicado nasce na Fase 2, os consumidores nas Fases 5 e 6. |
| VERI-11 | Nenhuma etapa de cadastro ou verificação exige login, senha, conta ou SSO oficial da UFU. | §1 — o realm não tem identity provider; verificado no JSON. |
| OPER-01 | Administrador geral pode conceder e revogar separadamente os papéis de moderador e administrador de competição, sempre com escopo e auditoria. | §4, §5, §6. |
| OPER-02 | Administrador de competição visualiza e opera somente competições atribuídas, salvo permissão superior explicitamente auditada. | §5 — coluna de escopo na concessão, nula na Fase 2. |
| OPER-03 | Administrador geral gerencia papéis e configurações da plataforma e não pode registrar apostas em nenhum evento. | §5 — a guarda D-48a fecha o desvio; o predicado de aposta nasce na Fase 5. |
| OPER-04 | Moderador pode atuar nos chats e denúncias sem receber acesso a comprovantes, saldos ou funções esportivas que não lhe pertençam. | §5 — autoridade separada por papel; nenhuma capacidade ligada nesta fase. |
| OPER-06 | Painel operacional apresenta pendências, falhas e estados necessários para manter a edição piloto. | `02-UI-SPEC.md` S4 (aprovado) + §6 agregados sem PII. |
| IDEN-05 | Comando administrativo sensível exige papel vigente e fica auditado; concessão ou revogação de papel invalida as sessões da identidade afetada. MFA é opcional no MVP e não condiciona nenhum comando. | §4 — **bloqueio material identificado**: o repositório de sessão configurado não sabe enumerar sessões por principal. |
| PRIV-01 | Matrículas, HMACs, documentos, tokens, chaves e dados privados não aparecem em URLs, conteúdo público, telemetria ou logs operacionais comuns. | §6 — `LogSanitizer` medido: **não cobre**. |
| AUDT-01 | Evento auditável registra ator, papel e MFA, alvo, ação, versão anterior e nova, motivo, evidência, horário, correlation ID e chave de idempotência quando aplicável. | §5 — tabela `audit_event` no estilo `V1`/`V2`. |
| AUDT-02 | Trilha de auditoria possui acesso restrito, proteção contra alteração e retenção definida sem armazenar segredos ou dados pessoais desnecessários. | §5 — imutabilidade por REVOKE + regra/trigger, não por convenção. |

</phase_requirements>

---

## Summary

Esta fase tem **cinco bloqueios materiais já identificados por inspeção**, e nenhum deles é uma questão de opinião de design. Todos são contratos travados por código ou validador que precisam mudar **no mesmo bloco** em que a funcionalidade nova entra, sob pena de a suíte Node ou o contexto Spring quebrar.

O maior deles é o do requisito `IDEN-05`/`D-49`. `application.yaml:50` fixa `spring.session.data.redis.repository-type: default`, e a inspeção do bytecode mostra que o bean resultante — `RedisSessionRepository` — **implementa apenas `SessionRepository`** e não tem nenhum método para localizar sessões por principal. O método que a fase precisa, `findByIndexNameAndIndexValue`, vive em `FindByIndexNameSessionRepository`, implementada por `RedisIndexedSessionRepository`, que **não é subtipo** de `RedisSessionRepository`. Trocar o tipo de repositório não é uma linha de YAML: `SessionSecurityConfiguration.java:83-87` lança `IllegalStateException` se o bean não for exatamente `RedisSessionRepository`, `:96-98` exige a propriedade literal `default`, `validate-runtime-prerequisites.mjs:973` a pina, e `validate-runtime-prerequisites.test.mjs:878` usa `repository-type: indexed` **como a mutação negativa que prova o guard**. A troca inverte o significado de uma regressão negativa existente — é exatamente a pendência "guard pina artefato, não propriedade" já registrada em `STATE.md`.

O segundo é o do cadastro. Virar `registrationAllowed` e `verifyEmail` para `true` toca **quatro** sítios além do realm, não os dois anunciados em `02-CONTEXT.md`: `validate-keycloak-realm.mjs:91` e `:93`, `validate-runtime-prerequisites.mjs:1299` (que amarra `realm.verifyEmail === false` à regra `KEYCLOAK_TECHNICAL_USER`) e `validate-runtime-prerequisites.test.mjs:1029`, onde `realm.verifyEmail = true` é a mutação negativa. E há um efeito colateral operacional real: o usuário técnico do realm tem `emailVerified: false` em `infra/keycloak/realm-betufu.json`, e o `OidcRevocationKeycloakIT` autentica com ele.

O terceiro é o das rotas de produção. `app.routes.production.ts` é hoje uma lista vazia, e `validate-angular-route-boundary.mjs` a pina por **três** regras distintas — `PRODUCTION_ROUTES_EXPORT`, `PRODUCTION_ROUTES_EMPTY` e `PRODUCTION_ROUTES_SHAPE`. A Fase 2 é a primeira a entregar rota de produto **em produção**.

O quarto é privacidade: `LogSanitizer` tem cinco padrões e **nenhum** deles alcança matrícula ou HMAC. `PRIV-01` não se cumpre estendendo o sanitizador — uma matrícula alfanumérica é indistinguível de qualquer outro token por regex. Cumpre-se estruturalmente: o valor nunca entra num logger.

O quinto é o `SECURITY`/`D-40`. O `OidcBackChannelLogoutHandler` do Spring Security 7.1.0 existe e funciona, mas depende de um `OidcSessionRegistry` cuja implementação padrão é **em memória**, e sua invalidação por sessão é uma auto-chamada HTTP com o cookie de sessão cujo nome padrão no bytecode é `JSESSIONID` — enquanto o cookie real desta aplicação é `SESSION`. Além disso, **não confirmei** que o Keycloak emita back-channel logout ao redefinir senha; a integração pode ter de ser por outro caminho.

**Recomendação primária:** planejar a Fase 2 em ondas cujo primeiro bloco é *reconciliação de contratos travados* — realm + três validadores, repositório de sessão indexado + dois guards, rotas de produção + três regras — antes de qualquer código de produto. Cada trava alterada precisa de uma regressão negativa **nova** que prove a propriedade sobrevivente, porque as regressões atuais deixam de ser mutações quando os valores viram os aprovados.

---

## Architectural Responsibility Map

| Capacidade | Camada primária | Camada secundária | Racional |
|---|---|---|---|
| Cadastro, senha, confirmação de e-mail, reset | Keycloak (IdP hospedado) | — | `AUTH-02` exige que a aplicação nunca receba a senha. `02-UI-SPEC.md` torna a fronteira normativa. |
| Sessão do usuário e autoridades | Backend Spring (BFF) + Redis | — | `01-06` já pôs a sessão em Redis; `SecurityContext` mora dentro dela. |
| Papéis de domínio | Backend + PostgreSQL | — | `D-20`: só `TECHNICAL` no Keycloak. |
| Normalização e HMAC da matrícula | Backend | — | A chave é `BETUFU_*` no processo (`D-33`); nunca no banco, nunca no browser. |
| Unicidade da matrícula | PostgreSQL (índice único) | Backend (multi-versão) | O índice é o árbitro sob concorrência; a varredura multi-versão é o predicado da `D-35`. |
| Trilha de auditoria | PostgreSQL | Backend | `AUDT-02` exige proteção contra alteração — só o banco pode negá-la ao próprio serviço. |
| Limite de tentativas | Backend + Redis | — | Redis já está no stack e tem TTL nativo; PostgreSQL exigiria varredura. |
| Guardas de papel (D-48) | Backend | — | Autoridade travada em servidor; a UI apenas antecipa a recusa. |
| Redirecionamento por perfil incompleto | Backend (recusa) | Angular (guard de conveniência) | `02-UI-SPEC.md` S1: "A guarda Angular é conveniência; a recusa do BFF prevalece." |
| Painel operacional | Backend (agregação) | Angular (render) | Agregados sem PII (`OPER-06`, `PRIV-01`). |

---

## Standard Stack

**Regra desta fase: nenhuma dependência nova.** Toda capacidade tem cobertura no que já está no `pom.xml`. Introduzir biblioteca exige aprovação explícita do responsável (`AGENTS.md`) e reabre a auditoria de supply chain do 01-19/01-20.

### Núcleo — já presente

| Artefato | Versão | Propósito nesta fase | Prova |
|---|---|---|---|
| `spring-boot-starter-parent` | 4.1.0 | plataforma | `[CÓDIGO] backend/pom.xml:8-10` |
| Java | 25 | `javax.crypto.Mac`, `java.util.HexFormat` | `[CÓDIGO] backend/pom.xml:23`; enforcer `[25,26)` em `:144` |
| `spring-boot-starter-oauth2-client` | via parent → `spring-security-*` 7.1.0 | BFF OIDC, back-channel logout | `[ARTEFATO] ~/.m2/.../spring-security-oauth2-client/7.1.0/` |
| `spring-boot-starter-session-data-redis` | Spring Session 4.1.0 | sessão distribuída | `[ARTEFATO] ~/.m2/.../spring-session-core/4.1.0/`, `spring-session-data-redis/4.1.0/` |
| `spring-boot-starter-data-jpa` | via parent | entidades de perfil, matrícula, papel, auditoria | `[CÓDIGO] backend/pom.xml:62-63` |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | via parent | `V3+` | `[CÓDIGO] backend/pom.xml:74-79` |
| `spring-boot-starter-validation` | via parent | Bean Validation nos DTOs (apelido, motivo) | `[CÓDIGO] backend/pom.xml:50-51` |
| `spring-modulith-starter-core` | 2.1.0 | fronteiras `identity`/`verification`/`audit` | `[CÓDIGO] backend/pom.xml:25, 87-88` |
| PostgreSQL | 18.4-trixie (digest fixado) | armazenamento autoritativo | `[CÓDIGO] infra/compose.yaml:3` |
| Redis | 8.10.0-trixie (digest fixado) | sessão + limite de tentativas | `[CÓDIGO] infra/compose.yaml:28` |
| Keycloak | 26.7.1 (digest fixado), `start-dev --import-realm` | IdP | `[CÓDIGO] infra/compose.yaml:45,47` |
| Mailpit | v1.30.7 | SMTP local para `VERIFY_EMAIL` e reset | `[CÓDIGO] infra/compose.yaml:105`; realm aponta para `mailpit:1025` em `infra/keycloak/realm-betufu.json` |

### Suporte — teste

| Artefato | Uso |
|---|---|
| `spring-boot-testcontainers` + `testcontainers-junit-jupiter` + `testcontainers-postgresql` | `[CÓDIGO] backend/pom.xml:102-113` |
| `GenericContainer` para Redis e Keycloak | **Não há módulo Testcontainers de Redis nem de Keycloak no `pom.xml`.** `RedisSessionIT.java:137` e `OidcRevocationKeycloakIT.java:71` usam `GenericContainer` com `DockerImageName` fixado. Os ITs novos desta fase devem seguir o mesmo padrão. `[CÓDIGO]` |
| `archunit-junit5` + `spring-modulith-starter-test` | fronteiras de módulo | `[CÓDIGO] backend/pom.xml:117-124` |

### O que NÃO está no classpath e não deve ser adicionado

| Tentação | Por que não | Substituto |
|---|---|---|
| Bucket4j / resilience4j-ratelimiter | ausente do `pom.xml` `[CÓDIGO]`; adicionar reabre auditoria de supply chain | contador Redis com TTL (`INCR` + `EXPIRE`), já disponível via `spring-data-redis` transitivo do starter de sessão |
| Keycloak Admin Client (`org.keycloak:keycloak-admin-client`) | ausente `[CÓDIGO]`; traz RESTEasy e um grafo grande | se a integração `SECURITY` exigir consulta administrativa, usar `RestClient` já usado em `SecurityConfiguration.java:115-124` |
| Biblioteca de auditoria (Javers, Hibernate Envers) | ausente; Envers geraria esquema fora do controle Flyway, contra `spring.jpa.hibernate.ddl-auto: validate` `[CÓDIGO] application.yaml:42` | tabela `audit_event` explícita em migration |
| Biblioteca de HMAC (Guava, Tink, BouncyCastle) | `javax.crypto.Mac` do `java.base` basta | JDK |

**Instalação:** nenhuma. Zero comandos de instalação nesta fase.

---

## Package Legitimacy Audit

**Nenhum pacote externo novo é instalado nesta fase.** Toda capacidade é atendida por dependência já declarada em `backend/pom.xml` e já auditada nas Fases 01-01 a 01-20, ou pelo `java.base` do JDK 25.

| Pacote | Registro | Verdito | Disposição |
|---|---|---|---|
| — | — | — | Nenhuma instalação prevista |

**Pacotes removidos por verdito `[SLOP]`:** nenhum.
**Pacotes marcados `[SUS]`:** nenhum.

**Consequência para o planejador:** se um plano desta fase propuser `npm install` ou uma dependência Maven nova, isso é **desvio de escopo** e exige aprovação separada do responsável por `AGENTS.md` antes de qualquer edição — não é uma decisão do executor.

---

## §1 — Autocadastro Keycloak e confirmação obrigatória de e-mail

### Estado atual do realm, medido

`[CÓDIGO] infra/keycloak/realm-betufu.json` — chaves de topo, verbatim:

```json
"realm": "betufu-local",
"enabled": true,
"registrationAllowed": false,
"resetPasswordAllowed": true,
"verifyEmail": false,
"loginTheme": "betufu",
"smtpServer": { "host": "mailpit", "port": "1025", "from": "no-reply@betufu.local",
                "fromDisplayName": "BETUFU", "auth": "false", "ssl": "false", "starttls": "false" }
```

O realm **não declara nenhum `identityProviders`** — verificado por leitura integral do arquivo. Isso é a prova de `VERI-11`: não existe SSO da UFU no caminho. `[CÓDIGO]`

O cliente `betufu-bff` é confidencial, com `standardFlowEnabled: true`, `implicitFlowEnabled: false`, `directAccessGrantsEnabled: false`, `pkce.code.challenge.method: "S256"` e `post.logout.redirect.uris: "http://localhost:4200/"`. `[CÓDIGO]`

> Nota de reconciliação: `STATE.md`, seção *Pendências*, ainda lista "Registrar o `post_logout_redirect_uri` no realm do Keycloak" como pendência aberta. O atributo **está presente** no realm em disco. `[CÓDIGO] infra/keycloak/realm-betufu.json`. Vale conferir se a pendência já foi fechada e o `STATE.md` não foi atualizado, ou se o valor presente não é o que o handler emite.

### O que precisa mudar — e onde a mudança dói

`AUTH-02` exige `registrationAllowed: true`. `D-38` exige `verifyEmail: true`. `02-CONTEXT.md` anuncia **dois** sítios de validador. São **quatro**:

| # | Sítio | Conteúdo atual | Prova |
|---|---|---|---|
| 1 | `scripts/validate-keycloak-realm.mjs:91` | `expectExact(realm.registrationAllowed, false, 'realm.registrationAllowed');` | `[CÓDIGO]` |
| 2 | `scripts/validate-keycloak-realm.mjs:93` | `expectExact(realm.verifyEmail, false, 'realm.verifyEmail');` | `[CÓDIGO]` |
| 3 | `scripts/validate-runtime-prerequisites.mjs:1298-1302` | `expect(realm.verifyEmail === false, 'KEYCLOAK_TECHNICAL_USER', 'realm tecnico nao pode exigir verificacao de email.');` | `[CÓDIGO]` |
| 4 | `scripts/validate-runtime-prerequisites.test.mjs:1029` | `['verificacao de email obrigatoria', (realm) => { realm.verifyEmail = true; }],` — entrada da tabela de mutações que **espera falha** na regra `KEYCLOAK_TECHNICAL_USER` | `[CÓDIGO]` |

O sítio 4 é o que torna isto perigoso. Hoje `verifyEmail = true` é a **mutação negativa** que prova o guard `KEYCLOAK_TECHNICAL_USER`. Depois da fase, `true` passa a ser o valor aprovado — e a regressão negativa deixa de mutar coisa alguma. Se o executor apenas inverter o `expectExact`, a suíte fica verde com uma regressão vazia, exatamente o defeito que `CLAUDE.md` chama de "prova vazia" e que `STATE.md` já registra como padrão recorrente ("guard pina artefato, não propriedade"). A propriedade que o sítio 3 realmente protege não é "o realm não exige verificação" — é **"o usuário técnico consegue autenticar sem required action pendente"**. Reescrito assim, a nova regressão negativa passa a ser `technicalUser.emailVerified = false` com `realm.verifyEmail = true`.

### O efeito colateral operacional: o usuário técnico

`[CÓDIGO] infra/keycloak/realm-betufu.json`, bloco `users[0]`, verbatim:

```json
"username": "technical",
"email": "technical@betufu.local",
"enabled": true,
"emailVerified": false,
"requiredActions": [],
```

E `validate-runtime-prerequisites.mjs:1303-1307` exige `technicalUser.requiredActions.length === 0`. `[CÓDIGO]`

Com `verifyEmail: true` no realm e `emailVerified: false` no usuário, a conta técnica **passa a ter a required action `VERIFY_EMAIL` computada em tempo de login**, e o `OidcRevocationKeycloakIT` — que autentica exatamente com esse usuário `[CÓDIGO] backend/src/test/java/br/com/betufu/platform/session/OidcRevocationKeycloakIT.java:71-85` — deixa de conseguir concluir o code flow. **A correção mínima é `"emailVerified": true` no usuário técnico**, e não desligar a verificação do realm.

Atenção: `VERIFY_EMAIL` é required action **computada a partir do estado do usuário**, não uma entrada literal em `requiredActions`. `[DOC — NÃO VERIFICADO]` — ver A-1 no *Assumptions Log*. Isso significa que o guard de `requiredActions.length === 0` pode continuar passando enquanto o login quebra: é exatamente a classe de defeito silencioso que este projeto já pagou uma vez. **O plano precisa de um IT que faça o code flow real contra o container, não um teste de leitura do JSON.**

### Efeito de `VERIFY_EMAIL` sobre o authorization code flow

O que `D-38` afirma — "a BETUFU nunca recebe ID token de conta não confirmada, então não existe estado intermediário no backend" — depende de o Keycloak não emitir código de autorização enquanto a required action estiver pendente. `[DOC — NÃO VERIFICADO]` — ver A-2. Não consegui medir isto sem subir o container, o que está fora dos limites desta pesquisa. É a afirmação de maior consequência arquitetural da fase inteira: se estiver errada, o backend precisa de uma checagem de `email_verified` no `OidcUser` e de uma superfície Angular para o estado intermediário — que o `02-UI-SPEC.md` **explicitamente não desenhou**.

Detalhe agravante: `SecurityConfiguration.java:93-98` desliga o `userinfo_endpoint` (`setRetrieveUserInfo(userRequest -> false)`) `[CÓDIGO]`, e a registration pede **apenas o escopo `openid`** `[CÓDIGO] application.yaml:72-73`. A claim `email_verified` normalmente viaja no escopo `email`. Portanto, mesmo que o backend quisesse conferir, **hoje ele não tem o dado** — o ID token emitido sob escopo `openid` isolado não carrega `email_verified`. `[DOC — NÃO VERIFICADO]` para o conteúdo exato da claim; `[CÓDIGO]` para o escopo pedido. Consequência para o planejador: **defesa em profundidade sobre `email_verified` custa uma mudança de escopo OIDC**, que reabre a decisão de minimização de dados do 01-07. Recomendo não pedir o escopo, e em vez disso provar `D-38` empiricamente com um IT de fluxo real.

### Tema e páginas novas

O tema `betufu` estende `keycloak.v2` apenas por `footer.ftl` e CSS — regra herdada, reafirmada em `02-UI-SPEC.md`. As duas páginas novas (`register`, `login-verify-email`) precisam exibir o aviso de rodapé e ter smoke próprio, conforme o UI-SPEC. Existe `scripts/validate-keycloak-theme.mjs` `[CÓDIGO]` que deve ser lido pelo planejador para descobrir se ele enumera páginas — não o inspecionei em profundidade.

### Contrato que o validador deve passar a ter

Formulação recomendada, para o planejador transformar em tarefa:

| Regra | Propriedade (não artefato) | Regressão negativa que a prova |
|---|---|---|
| `KEYCLOAK_SELF_REGISTRATION` | `registrationAllowed === true` **e** o realm não declara `identityProviders` | pôr `registrationAllowed: false`; acrescentar um `identityProviders` não vazio |
| `KEYCLOAK_EMAIL_VERIFICATION` | `verifyEmail === true` | pôr `false` |
| `KEYCLOAK_TECHNICAL_USER` | usuário técnico autentica **sem required action pendente**: `requiredActions` vazio **e** (`realm.verifyEmail === false` **ou** `technicalUser.emailVerified === true`) | `emailVerified: false` com `verifyEmail: true` — mutação **nova**, que não existe hoje |
| `KEYCLOAK_PASSWORD_RESET` | `resetPasswordAllowed === true` e SMTP apontando ao Mailpit | já existe; preservar |

---

## §2 — Gatilho `SECURITY`: como o backend descobre que a senha mudou (D-40)

### O que já existe

`[CÓDIGO] backend/src/main/java/br/com/betufu/platform/session/SessionTerminationReason.java`, verbatim e integral:

```java
public enum SessionTerminationReason {
    IDLE_TIMEOUT,
    CURRENT_DEVICE_LOGOUT,
    ADMINISTRATIVE,
    SECURITY
}
```

`SessionInvalidationListener` `[CÓDIGO] .../SessionInvalidationListener.java:29-49` é `@Component @Profile("runtime | integrated")` e expõe `onSessionInvalidated(SessionInvalidation)` retornando `OidcSessionRevocationService.Outcome`. O javadoc do próprio arquivo fixa **duas obrigações do chamador**, e ambas são armadilhas para esta fase:

- `:19-22` — "This listener terminates the identity *grant*; it does NOT invalidate the `HttpSession`. The caller that raises the event owns that."
- `:24-27` — "The returned `Outcome` must be inspected. `REMOTE_FAILURE` and `NO_REFRESH_TOKEN` both mean the grant may still be alive at the provider."

`OidcSessionRevocationService.revokeOnInvalidation` só age quando `reason == ADMINISTRATIVE || reason == SECURITY` `[CÓDIGO] .../OidcSessionRevocationService.java:152-153`, e devolve `NOT_APPLICABLE`, `ALREADY_CLAIMED`, `NO_REFRESH_TOKEN`, `REVOKED` ou `REMOTE_FAILURE` `[CÓDIGO] :97-127, :208`.

O record `SessionInvalidation` exige `sessionId`, `registrationId`, `principalName` e `reason`, todos não-brancos `[CÓDIGO] :57-74`. **Ou seja: o listener opera sobre UMA sessão por chamada.** Terminar "todas as sessões da identidade" é responsabilidade do chamador, que precisa primeiro **enumerá-las** — o que nos leva direto ao §4.

### Candidato 1: OIDC Back-Channel Logout — existe no classpath, medido

`[ARTEFATO] ~/.m2/repository/org/springframework/security/spring-security-config/7.1.0/spring-security-config-7.1.0.jar` contém, entre outras:

```
org/springframework/security/config/annotation/web/configurers/oauth2/client/OidcLogoutConfigurer.class
org/springframework/security/config/annotation/web/configurers/oauth2/client/OidcLogoutConfigurer$BackChannelLogoutConfigurer.class
org/springframework/security/config/annotation/web/configurers/oauth2/client/OidcBackChannelLogoutFilter.class
org/springframework/security/config/annotation/web/configurers/oauth2/client/OidcBackChannelLogoutHandler.class
org/springframework/security/config/annotation/web/configurers/oauth2/client/OidcBackChannelLogoutTokenValidator.class
org/springframework/security/config/annotation/web/configurers/oauth2/client/OidcBackChannelLogoutAuthenticationProvider.class
```

`OidcLogoutConfigurer` estende `AbstractHttpConfigurer` e expõe, por assinatura de bytecode `[ARTEFATO]`:

```
public clientRegistrationRepository(ClientRegistrationRepository) : OidcLogoutConfigurer
public oidcSessionRegistry(OidcSessionRegistry)                  : OidcLogoutConfigurer
public backChannel(Customizer)                                   : OidcLogoutConfigurer
```

`BackChannelLogoutConfigurer` expõe `logoutUri(String)` e `logoutHandler(LogoutHandler)` `[ARTEFATO]`.

`[ARTEFATO] OidcSessionRegistry.class` — interface com exatamente três métodos:

```
saveSessionInformation(OidcSessionInformation) : void
removeSessionInformation(String)               : OidcSessionInformation
removeSessionInformation(OidcLogoutToken)      : Iterable
```

Implementação disponível no `spring-security-oauth2-client-7.1.0.jar`: **`InMemoryOidcSessionRegistry`** `[ARTEFATO]`. É a única no JAR (a outra, `InMemoryReactiveOidcSessionRegistry`, é do stack reativo).

**Três constantes decisivas, lidas do pool de constantes de `OidcBackChannelLogoutHandler.class` `[ARTEFATO]`:**

1. `{baseUrl}/logout/connect/back-channel/{registrationId}` — o template de logout URI padrão.
2. `JSESSIONID` — o nome de cookie de sessão padrão do handler (`sessionCookieName`).
3. `org/springframework/web/client/RestTemplate` como `restOperations`, e as strings `Failed to invalidate session` e `not all sessions were terminated: `.

O mecanismo é, portanto: o handler pega as `OidcSessionInformation` que o registry devolve para o logout token e, **para cada sessão, faz uma requisição HTTP de volta para a própria aplicação** carregando o cookie de sessão daquela sessão. Isso produz três consequências duras neste repositório:

| Consequência | Prova |
|---|---|
| O nome do cookie está errado por padrão. O cookie desta aplicação é `SESSION`, não `JSESSIONID` — `DefaultCookieSerializer.class` do `spring-session-core-4.1.0.jar` tem `SESSION` como valor da constante `cookieName` `[ARTEFATO]`, e `application.yaml:13-19` **não** define `server.servlet.session.cookie.name` `[CÓDIGO]`. Sem `setSessionCookieName("SESSION")` a auto-chamada chega anônima e não invalida nada — e falha **em silêncio parcial**, porque o handler apenas acumula `errors`. | `[ARTEFATO]` + `[CÓDIGO]` |
| O endpoint `/logout/connect/back-channel/keycloak` cairia em `.anyRequest().denyAll()`. `SecurityConfiguration.java:216-245` lista os `permitAll` explicitamente (`/login`, `/oauth2/authorization/**`, `/login/oauth2/code/**`, `/actuator/health/liveness`, `/ws`, `/ws/**`, `GET /api/v1/session`), depois `"/api/v1/**"` autenticado, depois `anyRequest().denyAll()`. O caminho de back-channel não casa com nenhum. | `[CÓDIGO]` |
| É um POST sem token CSRF vindo do Keycloak. `SecurityConfiguration.java:246-248` aplica `csrf(csrf -> csrf.spa().csrfTokenRepository(...))` sem nenhum `ignoringRequestMatchers`. | `[CÓDIGO]` |
| O registry é em memória. Não sobrevive a restart nem é compartilhado entre réplicas — o mesmo defeito que `STATE.md` já registra para o `InMemoryOAuth2AuthorizedClientService`, e que a mesma pendência manda resolver "antes de existir qualquer disparo real de revogação". **Esta fase é esse disparo real.** | `[ARTEFATO]` + `STATE.md` |

### O furo que nenhuma dessas verificações fecha

Back-channel logout do OIDC é acionado quando a **sessão SSO do usuário no provedor termina**. Que o Keycloak 26.7.1 dispare um logout token de back-channel **como consequência de uma redefinição de senha** é precisamente o que `D-40` precisa, e é o que **não consegui verificar**. `[DOC — NÃO VERIFICADO]` — ver A-3.

Meu entendimento de treino, explicitamente não verificado nesta sessão, é que o Keycloak **não** encerra automaticamente as sessões existentes ao concluir o fluxo `reset-credentials`; o encerramento é uma ação administrativa separada ("Sign out all sessions" / push de `not-before`). Se isso se confirmar, **back-channel logout não é o mecanismo de `D-40`** e a fase precisa de um caminho diferente.

### Candidatos alternativos, ordenados por custo

| # | Mecanismo | Como funcionaria | Custo e risco |
|---|---|---|---|
| A | **Back-channel logout** com o realm configurado para encerrar sessões no reset | O Keycloak POSTa o logout token; `OidcBackChannelLogoutHandler` invalida cada sessão | Depende de A-3. Exige: permitir o path, isentar de CSRF, trocar o cookie para `SESSION`, e substituir o registry em memória por um durável |
| B | **Revalidação no refresh** | Ao renovar o access token, um refresh recusado (`invalid_grant`) prova que o grant morreu → dispara `SECURITY` | Não exige nada do Keycloak. Mas a latência é o TTL do refresh, e `D-40` argumenta exatamente contra "bastar uma requisição válida a cada 8 horas". Cobre tarde demais |
| C | **Introspecção periódica** do refresh token | Chamada ao `introspection_endpoint` a cada requisição válida ou em intervalo | Acopla cada request ao IdP; contraria o desenho de 8h ocioso do 01-06 |
| D | **`not-before` / push de revogação** | O Keycloak propaga um `notBefore` por realm ou cliente; o BFF compara com o `iat` da sessão | Precisa de um endpoint de admin-url no cliente; **não verificado** se o Keycloak 26.7.1 ainda envia o push de `not-before` para clientes OIDC |
| E | **Admin Events / Webhook** | Consumir eventos administrativos do Keycloak | Exige dependência nova ou polling; contra o princípio de "nenhuma dependência nova" |
| F | **Nada automático: reset de senha via superfície BETUFU que encadeia a invalidação** | Fora de escopo — `AUTH-04` e o UI-SPEC fixam o reset como página hospedada pelo Keycloak | Descartado por contrato |

**Recomendação para o planejador:** a primeira tarefa desta frente é um **spike de verificação empírica** contra o container Keycloak 26.7.1 real, respondendo em ordem: (i) o realm tem opção que encerre sessões ao concluir `reset-credentials`? (ii) com back-channel logout habilitado no cliente, um reset dispara logout token? (iii) o `iss`/`sid`/`sub` do logout token casa com o que o `InMemoryOidcSessionRegistry` guardou? Sem essa medição, qualquer plano de `D-40` é dedução de documentação — exatamente o que produziu o P0 do `iss`.

**Nota de projeto que sobrevive a qualquer resposta:** independentemente do gatilho, o *efeito* — "encerrar todas as sessões desta identidade" — é o mesmo de `D-49`. Convém que `D-40` e `D-49` chamem **um único serviço** `terminateAllSessionsFor(identity, reason)`, com `reason` sendo `SECURITY` ou `ADMINISTRATIVE`. Esse serviço é o que o §4 destrava.

---

## §3 — HMAC da matrícula (VERI-06, VERI-07, D-29, D-33–D-37)

### Construção recomendada

**Algoritmo: `HmacSHA256`.** `[ARTEFATO]` — o javadoc de `javax/crypto/Mac.java` no `src.zip` do JDK 25.0.4.7 lista, entre os algoritmos que toda implementação da plataforma deve suportar, `HmacSHA1` (linha 59) e `HmacSHA256` (linha 60). Está garantido pelo `java.base`; nenhum provider adicional é necessário.

**Tamanho da chave: 32 bytes (256 bits).** `[DOC — NÃO VERIFICADO]` — ver A-4. A recomendação clássica é chave de comprimento igual ao tamanho do bloco/saída da função hash; para SHA-256 isso é 32 bytes. Não medi isto contra uma fonte normativa nesta sessão. O que **está** verificado: `SecretKeySpec` recusa chave vazia com `IllegalArgumentException("Empty key")` `[ARTEFATO] javax/crypto/spec/SecretKeySpec.java:157-158` — mas **não** impõe comprimento mínimo. A validação de comprimento é responsabilidade da BETUFU, e é o que dá dentes à falha-fechada de `D-33`.

**Formato de transporte da chave: Base64 na variável de ambiente**, decodificada na inicialização. Justificativa: uma chave de 32 bytes aleatórios não é texto imprimível; hex também serve, mas Base64 é mais curto e `java.util.Base64` é `java.base`. **Decisão de discrição** — registrar como suposição no plano.

**Comparação: `java.security.MessageDigest.isEqual`.** Verificado como tempo-constante por leitura do fonte `[ARTEFATO] src.zip → java.base/java/security/MessageDigest.java:497-519`, verbatim do corpo:

```java
        int result = 0;
        result |= lenA - lenB;

        // time-constant comparison
        for (int i = 0; i < lenA; i++) {
            // If i >= lenB, indexB is 0; otherwise, i.
            int indexB = ((i - lenB) >>> 31) * i;
            result |= digesta[i] ^ digestb[indexB];
        }
        return result == 0;
```

Isto é prova de artefato, não de documentação: o laço não faz early return e o comprimento entra no acumulador. `Arrays.equals` **não** tem essa propriedade e não deve ser usado. Ressalva honesta: aqui a comparação tempo-constante é higiene, não a defesa principal — o atacante não controla o lado direito da comparação porque não conhece a chave. A defesa principal é a chave.

**Armazenamento do digest: `bytea` de 32 octetos.** Alternativa seria `char(64)` hex com `COLLATE "C"`, no estilo textual de `V1`. `bytea` é preferível: metade do espaço, sem ambiguidade de caixa, e o `CHECK (octet_length(...) = 32)` é exatamente o estilo de constraint que `V1__create_local_identity.sql` já usa (`CHECK (octet_length(issuer) <= 2048)`) `[CÓDIGO]`. **Decisão de discrição** — registrar.

**Falha fechada (`D-33`):** a validação da chave pertence à inicialização do contexto, não ao primeiro uso. O precedente do repositório é `SessionSecurityConfiguration.SelectiveRepositoryPostProcessor.requireProperty`, que lança `IllegalStateException(contractName + " drifted from the approved contract")` durante `postProcessAfterInitialization` `[CÓDIGO] SessionSecurityConfiguration.java:135-139`. Seguir esse padrão faz a aplicação **não subir** com chave ausente, curta ou não decodificável — que é literalmente o que `D-33` pede. Um `@Value` com default seria a violação; note que `validate-runtime-prerequisites.mjs` já tem uma regra `DATASOURCE_PLACEHOLDER` que proíbe fallback `${VAR:...}` em variável de datasource `[CÓDIGO] :961-966` — o mesmo tipo de regra deve cobrir `BETUFU_MATRICULA_HMAC_KEY_*`.

### Multi-versão e o índice único do PostgreSQL — onde mora a sutileza

`D-34` pede coluna `hmac_version` dedicada; `D-35` pede que a checagem de unicidade calcule o candidato sob **todas as versões ativas**.

O ponto que o planejador não pode errar: **um índice `UNIQUE (matricula_hmac)` sozinho NÃO implementa `D-35`.** Ele garante que dois registros não tenham o mesmo digest. Mas a mesma matrícula sob `v1` e sob `v2` produz digests **diferentes**, e o índice os aceita alegremente como duas linhas distintas — que é precisamente o cenário que `D-35` descreve como "`VERI-07` cairia silenciosamente no dia da rotação".

A forma correta:

```
                 candidato (texto normalizado)
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
   HMAC sob v1       HMAC sob v2       HMAC sob vN     ← todas as versões ATIVAS
        │                 │                 │
        └────────► SELECT ... WHERE matricula_hmac IN (h1, h2, …, hN) ◄────┘
                          │
              existe linha? ──sim──► recusa VERI-07 (auditada, D-30)
                          │
                         não
                          │
              INSERT (hmac = HMAC sob versão CORRENTE, hmac_version = corrente)
                          │
              violação do índice único? ──sim──► recusa VERI-07 (corrida perdida)
```

O `SELECT ... IN (...)` é o predicado da `D-35`; o índice único é o árbitro sob concorrência. **Os dois são necessários e nenhum substitui o outro.** Sem o `IN`, a rotação fura a unicidade. Sem o índice, duas requisições concorrentes com a mesma matrícula passam ambas pelo `SELECT` e ambas inserem — o mesmo raciocínio que levou `V1` a usar `ON CONFLICT DO NOTHING` em vez de "checar depois inserir" `[CÓDIGO] LocalIdentityRepository.java:13-21`.

**Consequência de desempenho, honesta:** com N versões ativas o índice serve as N buscas por igualdade, então o custo é N lookups de índice, não uma varredura. Com N pequeno (a `D-35` fala em rotação planejada, não em dezenas de versões) isto é irrelevante.

**Sobre a violação do índice como caminho de erro:** capturar `DataIntegrityViolationException` e traduzir para a recusa de negócio é aceitável, mas o plano precisa distinguir **qual** constraint foi violada — o `uq` da matrícula ou outra — porque uma tradução cega converteria qualquer violação futura na mensagem "esta matrícula já está registrada". O nome da constraint é o discriminador, e `V1`/`V2` já nomeiam todas as constraints explicitamente `[CÓDIGO]`.

### Índice único parcial e a `D-32`

`D-32` diz que a correção administrativa "libera a matrícula antiga e registra a nova". Duas modelagens:

- **(a) Update in place** na linha de vínculo — simples, mas destrói o histórico do digest anterior, e `AUDT-01` pede "versão anterior e nova". Registrar o digest anterior na auditoria conflita com a ideia de a auditoria não guardar dado sensível (`AUDT-02`)? Não: o digest **é** o pseudônimo; guardá-lo na auditoria é o mesmo nível de exposição da tabela principal. Mas então o expurgo da `D-37` precisa alcançar a auditoria também — e `AUDT-02` exige imutabilidade. **Tensão real entre `D-37` e `AUDT-02`, registrada como OQ-3.**
- **(b) Linha de vínculo com estado** (`ativo`/`liberado`) + índice **único parcial** `WHERE status = 'ATIVO'` — preserva histórico, libera o digest antigo para reuso legítimo, e mantém o árbitro de concorrência. O PostgreSQL suporta índice único parcial. `[DOC — NÃO VERIFICADO]` — ver A-5; é conhecimento estabelecido, mas não inspecionei nada nesta máquina que o comprove.

Recomendo **(b)**, porque é a única que satisfaz `AUDT-01` ("versão anterior e nova") sem duplicar o dado sensível na trilha.

### O que NÃO fazer

| Tentação | Por que é errado |
|---|---|
| Guardar o número original "só para poder recalcular" | `VERI-06` proíbe explicitamente; e `D-29` já declara a irreversibilidade como custo aceito |
| Usar SHA-256 sem chave | Matrícula tem espaço de busca pequeno; um dump permitiria força bruta em segundos. O HMAC com chave fora do banco é o que torna o dump inútil (`D-33`) |
| Derivar a chave de algo previsível | Anula `D-33` |
| Normalizar depois de calcular | `D-29` fixa a ordem: normalizar → validar padrão → só então HMAC |
| Descartar letras na normalização | `D-28` proíbe |
| Logar a matrícula em qualquer nível, inclusive `DEBUG` | `PRIV-01`; e ver §6 — o sanitizador não pega |
| Pôr a matrícula em path, query string ou `correlationId` | `PRIV-01`; o `02-UI-SPEC.md` já proíbe no cliente |

---

## §4 — Enumerar e invalidar as sessões de um principal (IDEN-05, D-49)

### O bloqueio, medido no bytecode

`[ARTEFATO] ~/.m2/repository/org/springframework/session/spring-session-data-redis/4.1.0/spring-session-data-redis-4.1.0.jar`:

```
CLASS   : org/springframework/session/data/redis/RedisSessionRepository
SUPER   : java/lang/Object
IFACES  : org/springframework/session/SessionRepository
```

Métodos públicos: `createSession`, `save`, `findById`, `deleteById`, `getSessionRedisOperations`, `setDefaultMaxInactiveInterval`, `setRedisKeyNamespace`, `setFlushMode`, `setSaveMode`, `setSessionIdGenerator`, `setRedisSessionMapper`. **Não existe nenhum método que localize sessão por principal.** `[ARTEFATO]`

```
CLASS   : org/springframework/session/data/redis/RedisIndexedSessionRepository
SUPER   : java/lang/Object
IFACES  : org/springframework/session/FindByIndexNameSessionRepository,
          org/springframework/data/redis/connection/MessageListener,
          org/springframework/beans/factory/InitializingBean,
          org/springframework/beans/factory/DisposableBean
```

com `public findByIndexNameAndIndexValue (Ljava/lang/String;Ljava/lang/String;)Ljava/util/Map;` e `public setIndexResolver (Lorg/springframework/session/IndexResolver;)V`. `[ARTEFATO]`

`[ARTEFATO] spring-session-core-4.1.0.jar → FindByIndexNameSessionRepository.class`: interface que estende `SessionRepository`, campo `PRINCIPAL_NAME_INDEX_NAME`, métodos `findByIndexNameAndIndexValue` e `findByPrincipalName`. O pool de constantes contém a string `.PRINCIPAL_NAME_INDEX_NAME` e as chamadas `Class.getName()` + `String.concat` — o valor do índice é, portanto, o nome qualificado da interface concatenado com `.PRINCIPAL_NAME_INDEX_NAME`. `[ARTEFATO]`

**As duas classes são irmãs, não parentes.** `RedisIndexedSessionRepository` estende `java.lang.Object`, não `RedisSessionRepository`. Nenhum cast, adapter ou `instanceof` faz uma virar a outra.

### O que está configurado hoje

`[CÓDIGO] backend/src/main/resources/application.yaml:45-52`, verbatim:

```yaml
  session:
    timeout: 8h
    data:
      redis:
        namespace: betufu:session
        repository-type: default
        flush-mode: on-save
        save-mode: on-set-attribute
```

`repository-type: default` → bean `RedisSessionRepository` → **sem capacidade de enumerar por principal**. `D-49` é hoje inexequível. Este é o achado de maior impacto de planejamento da fase.

### As quatro travas que a mudança encontra

| # | Trava | Conteúdo | Prova |
|---|---|---|---|
| 1 | `SessionSecurityConfiguration.java:83-87` | `if (!SESSION_REPOSITORY_BEAN_NAME.equals(beanName) \|\| !(bean instanceof RedisSessionRepository redisDelegate)) { throw new IllegalStateException("Expected exactly the official Redis bean named sessionRepository"); }` — com `repository-type: indexed` o bean deixa de ser `RedisSessionRepository` e **o contexto não sobe** | `[CÓDIGO]` |
| 2 | `SessionSecurityConfiguration.java:96-98` | `requireProperty(propertyEquals("spring.session.data.redis.repository-type", "default"), "Redis repository type");` | `[CÓDIGO]` |
| 3 | `validate-runtime-prerequisites.mjs:973` | `['spring.session.data.redis.repository-type', 'default'],` na tabela de `assertUniqueApprovedYamlValue` | `[CÓDIGO]` |
| 4 | `validate-runtime-prerequisites.test.mjs:878` | `['repository-type', 'repository-type: default', 'repository-type: indexed'],` — `indexed` é **a mutação negativa** que hoje prova a regra `REDIS_SESSION_SETTING` | `[CÓDIGO]` |

A trava 4 repete exatamente o padrão do §1: o valor que a fase precisa adotar é o valor que hoje serve de mutação. Depois da troca, a regressão fica vazia. **A propriedade que a regra realmente protege precisa ser reformulada** — de "o tipo é `default`" para algo como "o tipo é exatamente o aprovado, e o namespace, o timeout, o flush-mode e o save-mode não driftam", com a nova mutação sendo `repository-type: default` (perda da capacidade de enumerar).

E `SelectiveTouchSessionRepository` — o wrapper que implementa a política de toque seletivo do 01-06 — envolve hoje um `RedisSessionRepository` `[CÓDIGO] SessionSecurityConfiguration.java:105`. Envolvendo um `RedisIndexedSessionRepository`, ele precisa **também implementar `FindByIndexNameSessionRepository`** e delegar `findByIndexNameAndIndexValue`; caso contrário o Spring Security e qualquer consumidor enxergam apenas `SessionRepository` e a capacidade fica inacessível apesar de existir no delegado. Não li o corpo de `SelectiveTouchSessionRepository.java` nesta sessão — é leitura obrigatória para o planejador.

### Requisito de infraestrutura que vem junto — medido no bytecode

`RedisIndexedSessionRepository` implementa `org.springframework.data.redis.connection.MessageListener` `[ARTEFATO]`: ele depende de **notificações de keyspace do Redis** para saber que uma sessão expirou e limpar o índice de principal.

Inspecionei `org/springframework/session/data/redis/config/ConfigureNotifyKeyspaceEventsAction.class` no `spring-session-data-redis-4.1.0.jar`. O pool de constantes contém, em ordem de uso `[ARTEFATO]`:

```
getNotifyOptions (RedisConnection) : String
"E"   String.contains(CharSequence)   makeConcatWithConstants
"A"   "g"   "x"
RedisConnection.serverCommands() : RedisServerCommands
"notify-keyspace-events"
RedisServerCommands.setConfig (String,String) : void
RedisServerCommands.getConfig (String)        : Properties
"Unable to configure Redis to keyspace notifications. See https://docs.spring.io/spring-session/docs/current/reference/html5/#api-redisindexedsessionrepository-sessiondestroyedevent"
IllegalStateException (String,Throwable)
```

Isso resolve três dúvidas de uma vez, e **nenhuma delas por documentação**:

1. **O Spring Session configura o Redis sozinho, na inicialização.** Ele lê `getConfig("notify-keyspace-events")`, verifica se as opções já contêm `E` (ou `A`), e se não contiverem acrescenta `E`, `g` e `x` via `setConfig(...)` — ou seja, um `CONFIG SET` contra o servidor. **Portanto o `compose.yaml` NÃO precisa declarar `notify-keyspace-events` de antemão**, ao contrário do que eu havia suposto.
2. **Ele falha fechado.** Se o `CONFIG SET` for recusado, a `InvalidDataAccessApiUsageException` vira `IllegalStateException` com aquela mensagem literal, e o contexto **não sobe**. Não há degradação silenciosa — o que é a boa notícia: um Redis que proíba `CONFIG` produz falha ruidosa no boot, não um `D-49` que invalida zero sessões em produção.
3. **Existe opt-out explícito.** `ConfigureRedisAction` expõe o campo estático `NO_OP` `[ARTEFATO]`. Declarar um bean `ConfigureRedisAction` igual a `NO_OP` desliga o `CONFIG SET` automático — e transfere ao operador a obrigação de pré-configurar `notify-keyspace-events` no `compose.yaml`.

**Consequência para o planejador.** O Redis do `infra/compose.yaml:30` sobe com `["redis-server", "--appendonly", "yes", "--requirepass", ...]` `[CÓDIGO]` — sem renomear nem desabilitar `CONFIG`, e sem ACL restritiva. O caminho de menor atrito é **deixar o Spring Session fazer o `CONFIG SET`** e não mexer no compose. A decisão que sobra é de política: em homologação e produção o Redis pode ser gerenciado e proibir `CONFIG` (é comum em Redis como serviço). Nesse cenário o par correto é `ConfigureRedisAction.NO_OP` **mais** a configuração no servidor — e a escolha precisa ser explícita, porque a falha aparece só no boot do ambiente que ninguém testou.

Recomendação: no local, nada muda no `compose.yaml`; o `RedisSessionIT` (que usa `GenericContainer` com o mesmo comando `[CÓDIGO] RedisSessionIT.java:137-139`) continua servindo. Registrar como pendência de infraestrutura, ao lado da escolha de S3 e de origens que `STATE.md` já mantém em aberto, que homologação/produção precisam decidir entre `CONFIG SET` permitido e `NO_OP` + configuração no servidor.

### Alternativa: índice próprio, sem trocar o repositório

Se o custo acima se mostrar alto demais, existe um caminho que **não** toca o tipo de repositório: manter uma tabela/estrutura própria `identidade → conjunto de sessionIds` (em PostgreSQL, junto do resto do domínio, ou em Redis com o mesmo TTL da sessão), populada no `SessionLifecycleService` — que já é o `successHandler` do `oauth2Login` `[CÓDIGO] SecurityConfiguration.java:265` e portanto já vê o momento em que uma sessão nasce autenticada. A invalidação passaria a ser `deleteById` sobre cada id conhecido, método que `RedisSessionRepository` **tem** `[ARTEFATO]`.

Comparação honesta:

| | Repositório indexado | Índice próprio |
|---|---|---|
| Capacidade | nativa, mantida pelo framework | nossa, com risco de divergir da verdade |
| Travas a reescrever | 4 (duas de código, duas de validador) + wrapper + possivelmente Redis | 0 nas travas existentes |
| Limpeza na expiração | automática, via keyspace notifications | precisa de TTL espelhado ou varredura; **divergência silenciosa é o risco** |
| Precedente no projeto | é o caminho do framework | é hand-rolling do que o framework já resolve — contra o princípio de *Don't Hand-Roll* |
| Prova de que funciona | um IT contra Redis real | um IT contra Redis real **mais** prova de que o índice não vaza nem envelhece |

**Recomendação:** repositório indexado. A tabela acima mostra que o índice próprio troca quatro reescritas de contrato por uma fonte permanente de divergência, e `CLAUDE.md` valoriza justamente o oposto. Mas a decisão tem custo visível e **merece ser apresentada ao responsável antes de qualquer edição**, conforme `AGENTS.md` — é decisão de arquitetura, não de execução.

### Sequência da invalidação (vale para D-40 e D-49)

Ordem obrigatória, derivada do contrato já estabelecido pelo 01-22 (`STATE.md`: "A ordem do logout é contrato de segurança: authorized client, sessão HTTP e SecurityContext caem **antes** de a coordenação com o provedor ser computada"):

```
terminateAllSessionsFor(identityId, reason):
  1. resolver principalName a partir da identidade        ← ver armadilha abaixo
  2. sessions = findByPrincipalName(principalName)
  3. para cada sessão:
       a. deleteById(sessionId)                            ← a sessão morre primeiro
       b. listener.onSessionInvalidated(                   ← depois o grant
              new SessionInvalidation(sessionId, registrationId, principalName, reason))
       c. INSPECIONAR o Outcome                            ← obrigação de :24-27
  4. registrar auditoria com o total e os outcomes não-REVOKED
```

### O que o índice realmente contém — medido, não deduzido

Esta era a maior armadilha em aberto do §4, e o bytecode a fecha quase inteira.

`org/springframework/session/PrincipalNameIndexResolver.class`, constantes em ordem de uso `[ARTEFATO]`:

```
Session.getAttribute(String)      <- "SPRING_SECURITY_CONTEXT"
SpelExpressionParser.parseExpression("authentication?.name")
Expression.getValue(Object, Class)
FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME   <- fallback por atributo
```

Ou seja: o resolvedor lê o atributo de sessão `SPRING_SECURITY_CONTEXT`, avalia sobre ele a expressão SpEL `authentication?.name`, e — se isso for nulo — recorre ao atributo cujo nome é a própria constante do índice. **O valor indexado é literalmente `Authentication#getName()`.**

Falta então uma pergunta: o que `getName()` devolve para um principal OIDC? Inspecionei `org/springframework/security/oauth2/core/oidc/user/DefaultOidcUser.class` no `spring-security-oauth2-core-7.1.0.jar`. O pool de constantes contém `org/springframework/security/oauth2/core/oidc/IdTokenClaimNames` e a string `sub`, e os quatro construtores públicos são `[ARTEFATO]`:

```
<init>(Collection, OidcIdToken)
<init>(Collection, OidcIdToken, String)                       <- nameAttributeKey explícito
<init>(Collection, OidcIdToken, OidcUserInfo)
<init>(Collection, OidcIdToken, OidcUserInfo, String)
```

Os construtores sem `String` delegam usando `IdTokenClaimNames.SUB` como chave padrão — daí a presença conjunta da classe de constantes e do literal `sub`. E `application.yaml:64-73` **não** declara `user-name-attribute` na registration `keycloak` `[CÓDIGO]` (os únicos campos são `provider`, `client-id`, `client-secret`, `client-authentication-method`, `authorization-grant-type`, `redirect-uri` e `scope`).

**Conclusão prática, com a ressalva honesta.** Tudo indica que o índice contém o valor da claim `sub` — que é exatamente a metade `subject` da chave natural `(issuer, subject)` de `LocalIdentity` `[CÓDIGO] LocalIdentity.java:30-34`. Isso torna o mapeamento `identidade → principalName` trivial: **é a própria coluna `subject`**, sem estrutura auxiliar nenhuma.

A ressalva: qual chave o `OidcUserService` escolhe ao construir o `DefaultOidcUser` — o padrão do construtor ou algo lido da `ClientRegistration` — é caminho que **não** rastreei no bytecode. `[DOC — NÃO VERIFICADO]` — ver A-12. Como `SecurityConfiguration.java:93-98` substitui o `OidcUserService` padrão por um com `setRetrieveUserInfo(userRequest -> false)` `[CÓDIGO]`, o caminho é o do framework e a inferência é forte — mas *forte* não é *medido*, e este projeto já pagou caro por essa diferença exata no `iss`. **Um IT que grave uma sessão autenticada real e leia a chave do índice no Redis encerra a questão em minutos** e deve ser a primeira tarefa da frente `D-49`.

### O atalho idiomático: `SpringSessionBackedSessionRegistry`

Não é preciso chamar `findByPrincipalName` na mão. `spring-session-core-4.1.0.jar` traz `org/springframework/session/security/SpringSessionBackedSessionRegistry.class` `[ARTEFATO]`:

```
CLASS  : org/springframework/session/security/SpringSessionBackedSessionRegistry
IFACES : org/springframework/security/core/session/SessionRegistry

public <init>(FindByIndexNameSessionRepository)
public getAllPrincipals()                        : List
public getAllSessions(Object, boolean)           : List
public getSessionInformation(String)             : SessionInformation
public registerNewSession(String, Object)        : void
public removeSessionInformation(String)          : void
```

O JAR também traz `SpringSessionBackedSessionInformation` `[ARTEFATO]`, que é a `SessionInformation` do Spring Security respaldada pelo Spring Session — o objeto sobre o qual `expireNow()` é a operação de expiração.

Isto é o `SessionRegistry` **nativo do Spring Security**, respaldado pelo repositório indexado. `getAllSessions(principal, false)` é a enumeração que `D-49` precisa, com o vocabulário que o Spring Security já usa em toda a sua superfície de concorrência de sessão. **Confirma a recomendação de §4 pela via do framework** e reduz o código próprio a quase nada — o serviço `terminateAllSessionsFor` vira uma composição de peças existentes, e não uma engenhoca.

Note que o construtor exige `FindByIndexNameSessionRepository` `[ARTEFATO]`. Isso reforça o ponto central desta seção: **o registry nem sequer é construível com `repository-type: default`**. A limitação não é de estilo; é de tipo, e o compilador a impõe.

---

## §5 — Modelo de dados: perfil, matrícula, papéis e auditoria

### O estilo que este projeto usa, medido

`[CÓDIGO] backend/src/main/resources/db/migration/V1__create_local_identity.sql`, integral:

```sql
CREATE TABLE public.local_identity (
    identity_id uuid NOT NULL DEFAULT gen_random_uuid(),
    issuer varchar(2048) COLLATE "C" NOT NULL,
    subject varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_local_identity PRIMARY KEY (identity_id),
    CONSTRAINT uq_local_identity_issuer_subject UNIQUE (issuer, subject),
    CONSTRAINT ck_local_identity_issuer_not_empty CHECK (issuer <> ''),
    CONSTRAINT ck_local_identity_subject_not_empty CHECK (subject <> ''),
    CONSTRAINT ck_local_identity_issuer_octets CHECK (octet_length(issuer) <= 2048),
    CONSTRAINT ck_local_identity_subject_octets CHECK (octet_length(subject) <= 255),
    CONSTRAINT ck_local_identity_timestamp_order CHECK (updated_at >= created_at)
);
```

`V2__add_platform_guards.sql` repete o padrão e acrescenta um `INSERT` de semente `[CÓDIGO]`.

**Regras extraídas do estilo real** (o planejador deve tratá-las como obrigatórias):

1. Schema `public.` explícito.
2. PK `uuid ... DEFAULT gen_random_uuid()`.
3. Toda coluna textual comparada por igualdade leva `COLLATE "C"`.
4. `timestamptz(6)` com `DEFAULT CURRENT_TIMESTAMP(6)`.
5. **Toda** constraint é nomeada: `pk_`, `uq_`, `ck_`.
6. `CHECK` de não-vazio **e** `CHECK` de `octet_length` para cada coluna textual.
7. `CHECK` de ordem temporal quando há dois instantes.
8. Semente idempotente vive na própria migration (`V2`) — precedente direto para a `D-47`.

E `spring.jpa.hibernate.ddl-auto: validate` `[CÓDIGO] application.yaml:42` significa que **o JPA precisa casar com o DDL exatamente**, ou o contexto não sobe. `LocalIdentity` demonstra o rigor: `@Table(schema = "public")`, `@Column(columnDefinition = "timestamptz(6)")`, `@UniqueConstraint(name = ...)` `[CÓDIGO] LocalIdentity.java:15-48`.

### O identificador público já existe

`CurrentIdentityResponse` é `record CurrentIdentityResponse(UUID identityId, String status, Instant createdAt, Instant updatedAt)` `[CÓDIGO] CurrentIdentityResponse.java:6-10`, e `identity_id` é o PK aleatório `gen_random_uuid()` `[CÓDIGO] V1:2`. `STATE.md` confirma a decisão: "a identidade local usa o par OIDC exato `(issuer, subject)` como chave natural ... e um UUID aleatório como identificador público".

**Consequência:** o "Identificador público" da tela S2 do `02-UI-SPEC.md` e o seletor de pessoa das telas S5/S6 **são o `identity_id` que já existe**. Não criar uma segunda coluna. É PK e identificador público ao mesmo tempo — aceitável porque o valor é aleatório, não sequencial, e não vaza cardinalidade.

### Tabelas propostas (esboço para o planejador refinar)

Nomes e colunas são **propostas**, não decisões travadas. O rigor de constraints é obrigatório.

**`betufu_profile`** — 1:1 com `local_identity`, satisfaz `PROF-01`, `PROF-02`, `D-39`, `D-44`, `D-45`.

- `profile_id uuid PK`, `identity_id uuid NOT NULL UNIQUE REFERENCES local_identity(identity_id)`
- `nickname varchar(20) COLLATE "C" NOT NULL` — `D-39` fixa `NOT NULL` desde a primeira linha
- `favorite_team_id uuid NULL` — `D-45`; **sem FK nesta fase**, porque a tabela de equipes nasce na Fase 3. Uma FK para tabela inexistente não compila; documentar que a Fase 3 acrescenta a FK sem migration de dados
- `created_at`, `updated_at timestamptz(6)`
- `ck_betufu_profile_nickname_pattern CHECK (nickname ~ '^[A-Za-z0-9_-]{3,20}$')` — trava `D-44` **no banco**, não só no Bean Validation. É o que impede o homoglifo cirílico de entrar por qualquer caminho
- `ck_betufu_profile_nickname_octets CHECK (octet_length(nickname) <= 20)`
- `ck_betufu_profile_timestamp_order CHECK (updated_at >= created_at)`

Sobre a regex no `CHECK`: `~` é o operador de match de regex POSIX do PostgreSQL. `[DOC — NÃO VERIFICADO]` — ver A-7. O ganho é decisivo: sendo ASCII estrito, `[A-Za-z0-9_-]` sob `COLLATE "C"` não admite equivalência de classe local, então o `а` cirílico é rejeitado pelo banco.

**`ufu_enrollment`** — satisfaz `VERI-06`, `VERI-07`, `D-32`, `D-34`, `D-35`.

- `enrollment_id uuid PK`
- `identity_id uuid NOT NULL REFERENCES local_identity(identity_id)`
- `matricula_hmac bytea NOT NULL` + `ck_..._hmac_octets CHECK (octet_length(matricula_hmac) = 32)`
- `hmac_version smallint NOT NULL` + `ck_..._hmac_version_range CHECK (hmac_version >= 1)` — `D-34` pede "restrição de valor válido"
- `status varchar(16) COLLATE "C" NOT NULL` + `CHECK (status IN ('ATIVO','LIBERADO'))`
- `created_at`, `updated_at`, `ck_..._timestamp_order`
- `uq_ufu_enrollment_active_hmac` — **índice único parcial** `(matricula_hmac) WHERE status = 'ATIVO'`
- `uq_ufu_enrollment_active_identity` — **índice único parcial** `(identity_id) WHERE status = 'ATIVO'`; é o que implementa "cada conta registra no máximo uma matrícula" da `D-31`
- **Nenhuma coluna com o número original.** `VERI-06`

Nota: índice único parcial não cabe em `CONSTRAINT ... UNIQUE`; sai como `CREATE UNIQUE INDEX ... WHERE ...`. O JPA sob `ddl-auto: validate` não valida índices (só tabelas e colunas) `[DOC — NÃO VERIFICADO]` — ver A-8 —, então o índice precisa de um IT que o exercite de verdade, e não apenas do contexto subindo.

**`operational_role_grant`** — satisfaz `OPER-01`–`OPER-04`, `IDEN-05`, `D-46`, `D-48`.

- `grant_id uuid PK`
- `identity_id uuid NOT NULL REFERENCES local_identity(identity_id)`
- `role_name varchar(32) COLLATE "C" NOT NULL` + `CHECK (role_name IN ('ADMINISTRADOR_GERAL','ADMINISTRADOR_COMPETICAO','MODERADOR'))` — `D-46` exige os três desde já
- `scope_ref uuid NULL` — `OPER-02`; nulo na Fase 2, preenchido na Fase 3
- `status varchar(16) COLLATE "C" NOT NULL CHECK (status IN ('VIGENTE','REVOGADO'))`
- `granted_at`, `revoked_at timestamptz(6) NULL`, `ck_..._revocation_order CHECK (revoked_at IS NULL OR revoked_at >= granted_at)`
- índice único parcial `(identity_id, role_name, scope_ref) WHERE status = 'VIGENTE'` — impede concessão duplicada
- **`D-48b` não é implementável por constraint de linha.** "Não revogar o último administrador geral" é um invariante sobre o conjunto. As opções são: trava de transação com `SELECT ... FOR UPDATE` sobre as linhas de `ADMINISTRADOR_GERAL` vigentes, ou uma constraint de exclusão. **Recomendo a trava explícita em transação serializável ou `FOR UPDATE`**, porque é legível e testável. E `D-48` é literal: "Cada guarda deve virar um teste que falha se a guarda sumir" — isto é, mutação discriminante obrigatória em ambas as guardas.

**`audit_event`** — satisfaz `AUDT-01`, `AUDT-02`. Ver §7.

### Semente do primeiro administrador (D-47)

`V2__add_platform_guards.sql` já estabelece o precedente de semente idempotente numa migration `[CÓDIGO]`. Mas a semente da `D-47` **não** pode ser um `INSERT` de migration: o valor vem de variável de ambiente `BETUFU_*`, e Flyway não interpola ambiente por padrão. O caminho coerente com o repositório é um componente de inicialização `@Profile("runtime | integrated")` — o mesmo padrão de `LocalIdentityService` `[CÓDIGO] LocalIdentityService.java:8-10` — que aplica a semente de forma idempotente com `ON CONFLICT DO NOTHING`, exatamente como `LocalIdentityRepository.insertIfAbsent` `[CÓDIGO] :13-21`.

A pergunta em aberto da `D-47` — identificar por e-mail ou por `(iss, sub)` — tem uma resposta forçada pela arquitetura: **`(iss, sub)` é o único vínculo estável** (`D-41`), mas o operador humano que preenche a variável de ambiente **não conhece o `sub`** antes do primeiro login. Recomendação conservadora: a variável carrega o **e-mail**; a semente é aplicada de forma preguiçosa **no momento do primeiro login daquela identidade** (quando `iss`, `sub` e e-mail estão todos disponíveis), converte-se em `operational_role_grant` e é auditada. Isso responde também "o que acontece se a identidade semeada ainda não tiver feito o primeiro login": nada acontece, e a semente permanece pendente e inofensiva. **Suposição a registrar no plano**, porque cria uma dependência de `email` no ID token — e o escopo pedido hoje é só `openid` `[CÓDIGO] application.yaml:72-73`. Tensão real; ver OQ-4.

---

## §6 — Privacidade operacional (PRIV-01) — medido, não suposto

`02-CONTEXT.md` instrui: "verificar se o sanitizador cobre os novos campos em vez de assumir". Verifiquei.

`[CÓDIGO] backend/src/main/java/br/com/betufu/platform/observability/LogSanitizer.java:38-61` — os cinco padrões, verbatim:

```java
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "((?:Authorization|Proxy-Authorization)\\s*[:=]\\s*)\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern COOKIE = Pattern.compile(
            "((?:Set-)?Cookie\\s*[:=]\\s*)[^\\n]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{10,}(?:\\.[A-Za-z0-9_-]+)*");
    private static final Pattern CREDENTIAL = Pattern.compile(
            "((?:password|passwd|pwd|secret|client_secret|api[_-]?key)\\s*[:=]\\s*)\\S+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cc}");
```

**Veredito: nenhum dos cinco alcança uma matrícula ou um HMAC.** `matricula=2021ABC123` não casa com `CREDENTIAL`; um digest hex ou Base64 não casa com `JWT` (que exige o prefixo `eyJ`).

E a resposta correta **não é acrescentar um sexto padrão**. Uma matrícula alfanumérica é, por construção, indistinguível de um id de correlação, de um SKU ou de um nome de arquivo. Um padrão largo o bastante para pegá-la redigiria metade dos logs úteis; um padrão estreito falharia em silêncio — e falha silenciosa em `PRIV-01` é vazamento.

**A garantia tem de ser estrutural.** Três tarefas concretas para o planejador:

1. O valor cru da matrícula existe apenas como parâmetro de método, no caminho normalizar→validar→HMAC. Nunca vira campo de entidade, nunca vira atributo de sessão, nunca entra em DTO de resposta.
2. **Nenhuma exceção que possa carregar o valor pode ser lançada com ele na mensagem.** Bean Validation, por padrão, pode incluir o valor rejeitado na mensagem; o `ProblemDetailsHandler` e `FieldViolation` `[CÓDIGO] platform/api/` precisam ser auditados quanto a isso. Este é o vetor de vazamento mais provável da fase, porque é indireto.
3. Um validador — Node ou ArchUnit — que **falhe** se o tipo/campo da matrícula aparecer num argumento de logger, num `toString()` gerado, ou num DTO. ArchUnit já está no classpath `[CÓDIGO] backend/pom.xml:122-124` e o projeto já tem `ModuleDependencyRulesTest` e `ModulithArchitectureTests` `[CÓDIGO]`. Uma regra ArchUnit é o instrumento mais barato aqui.

Também vale para o painel S4: o `02-UI-SPEC.md` já exige apenas números agregados, "jamais exibe número de matrícula, HMAC ou quem tentou".

### Rotas de produção — a quarta trava

`[CÓDIGO] frontend/src/app/app.routes.production.ts`, integral:

```typescript
import type { Routes } from '@angular/router';

export const BUILD_SPECIFIC_ROUTES: Routes = [];
```

E `scripts/validate-angular-route-boundary.mjs` pina isso por **três** regras `[CÓDIGO]`:

- `:1378-1383` `PRODUCTION_ROUTES_EXPORT` — exige `export const BUILD_SPECIFIC_ROUTES : Routes = [ ] ;` exatamente uma vez
- `:1384-1388` `PRODUCTION_ROUTES_EMPTY` — `countMatches(..., /\bBUILD_SPECIFIC_ROUTES\b/u) === 1`
- `:1402-1406` `PRODUCTION_ROUTES_SHAPE` — `EMPTY_BUILD_SPECIFIC_ROUTES_PATTERN.test(production)`, ancorado em `^...\]\s*;\s*$`

Há ainda `:1389-1395` `DEVELOPMENT_ROUTES_SHAPE`, que restringe `app.routes.development.ts` a **uma de quatro** formas canônicas enumeradas (vazia, tracer, layout+tracer, layout+tracer+diagnostic) `[CÓDIGO]`. Acrescentar rota de produto quebra também esse lado.

E `:1396-1401` `PRODUCTION_TRACER_FORBIDDEN` — `!/(?:\btracer\b|\bTracerPageComponent\b|\bdiagnostic\b|\bDiagnosticPageComponent\b)/iu.test(production)` — **essa é a regra que protege a propriedade real** e deve sobreviver intacta. As outras três protegiam artefato, e o 01-28 as escreveu quando produção legitimamente não tinha rota nenhuma. É o terceiro caso do mesmo padrão nesta fase, e o `STATE.md` já pede: "Varrer os demais validadores atrás de guards que pinam artefato em vez de propriedade."

`02-UI-SPEC.md` fixa as rotas: `/conta/perfil/completar`, `/conta/perfil`, `/conta/matricula`, `/operacao/painel`, `/operacao/papeis`, `/operacao/matriculas/corrigir` — todas **em produção**.

### Contrato OpenAPI — pagar na hora

`backend/openapi/betufu.yaml` tem 222 linhas e cobre quatro paths: `/api/v1/tracer/identity/current`, `/api/v1/session`, `/api/v1/session/logout`, `/api/v1/diagnostic` `[CÓDIGO]`. A árvore gerada tem exatamente 20 arquivos `[CÓDIGO] frontend/src/app/api/generated/**`, e `validate-angular-route-boundary.mjs:792-794` exige `manifest.digest === APPROVED_GENERATED_DIGEST` `[CÓDIGO]` — hoje `27AC44819495367D671C92341400E86EE0B79F4ECAE4229F53BB90F2560C35C0` segundo `STATE.md`.

`STATE.md` mede o custo de adiar: "O adiamento do contrato OpenAPI tem custo medido ... custou **4 dos 7 arquivos novos** do cliente regenerado no 01-26." **Cada plano que acrescentar endpoint deve editar o yaml no mesmo plano**, regenerar o cliente e atualizar o digest aprovado — que é aprovação do responsável, não decisão do executor.

Há também `validate-phase1.mjs --contract` `[CÓDIGO] :108-147`, que compara os mappings declarados no código Java com o contrato e roda o check de drift do cliente. Ele varre `backend/src/main/java` `[CÓDIGO] :31`, então **detecta automaticamente** endpoints novos ausentes do yaml. Isto é uma boa notícia: o detector desta divergência já existe. Nome do script contém "phase1"; o planejador deve decidir se generaliza o nome ou o mantém.

### Autoridades: de onde os papéis entram no `SecurityContext`

`OidcTechnicalAuthorityMapper` é allowlist fechada: só `TECHNICAL`, dentro da claim `betufu_roles` do ID token, exigindo `Collection` de `String` e comparação por `equals` `[CÓDIGO] OidcTechnicalAuthorityMapper.java:36-87`. O javadoc `:33-35` declara: "As autoridades originais do provedor são sempre preservadas: este mapper acrescenta, nunca substitui."

Os papéis de domínio vêm do PostgreSQL (`D-20`, `D-46`), não do Keycloak. Duas fiações possíveis:

- **(a)** um segundo `GrantedAuthoritiesMapper`, ou uma extensão do existente, que consulte `operational_role_grant` no login. Risco: o mapper de autoridades não recebe naturalmente a identidade BETUFU, apenas as autoridades OIDC.
- **(b)** enriquecer no `SessionLifecycleService`, que já é o `successHandler` do `oauth2Login` `[CÓDIGO] SecurityConfiguration.java:265` e roda depois da autenticação completa, com o `OidcUser` disponível.

**(b) parece o encaixe natural**, mas mexe numa classe cujo contrato o 01-06 fixou. Qualquer que seja a escolha, `SecurityConfiguration.java:252-266` avisa em comentário: "O mapper de autoridade e fiado nos DOIS ramos. Ligar apenas no ramo completo deixaria os contextos focais de teste sem o handoff, e um teste que passasse ali estaria provando um pipeline diferente do de producao." **A mesma disciplina vale para as autoridades novas.**

E é justamente porque as autoridades ficam congeladas dentro da sessão que `D-49` existe. `02-CONTEXT.md` já o diz: "o Spring Security guarda as autoridades no `SecurityContext` dentro da sessão (Redis, 01-06), e mexer no papel no PostgreSQL não altera essa cópia."

### `ApiErrorCode` — o que falta

`[CÓDIGO] ApiErrorCode.java:13-43` tem hoje cinco constantes: `VALIDATION_FAILED` (`BETUFU-400-VALIDATION`), `METHOD_NOT_ALLOWED` (`BETUFU-405-METHOD`), `NOT_ACCEPTABLE` (`BETUFU-406-NOT_ACCEPTABLE`), `UNSUPPORTED_MEDIA_TYPE` (`BETUFU-415-MEDIA_TYPE`), `INTERNAL_ERROR` (`BETUFU-500-INTERNAL`). Cada uma carrega `code`, `title`, `detail`, `action` em pt-BR, e o javadoc `:7-8` fixa: "Codes must never be renamed or removed once published — they become part of the API contract per D-14."

`fromStatus(int)` mapeia 400/405/406/415 e cai em `INTERNAL_ERROR` para o resto `[CÓDIGO] :79-87`. **Note que não há mapeamento para 401, 403, 404, 409 nem 429** — todos caem em `INTERNAL_ERROR`. As recusas de negócio do `02-UI-SPEC.md` (matrícula duplicada, limite de tentativas, autoconcessão bloqueada, último administrador) precisam de códigos próprios, e o `fromStatus` provavelmente precisa de mapeamentos novos. As mensagens em pt-BR **já estão escritas e aprovadas** no `02-UI-SPEC.md`, seção *Recusas de negócio* — o executor deve copiá-las, não reescrevê-las.

---

## §7 — Trilha de auditoria (AUDT-01, AUDT-02)

### Campos exigidos, do texto do requisito

`AUDT-01`: ator, papel **e MFA**, alvo, ação, versão anterior e nova, motivo, evidência, horário, correlation ID e chave de idempotência quando aplicável.

Tensão a resolver: o requisito pede "papel e MFA", mas `D-50`/`D-52` removeram MFA de toda superfície e `mfaAuthenticated` está deliberadamente ausente do contrato de sessão `[02-CONTEXT.md D-52]`. **Recomendação:** a coluna de MFA **não** é criada. Registrar um campo constante ou nulo publicaria um sinalizador que não governa nada — exatamente o argumento de `D-52`. Registrar como suposição explícita; é a leitura coerente com as emendas da discussão, mas contraria a letra de `AUDT-01`. Ver OQ-5.

O `correlationId` já existe: `CorrelationIdFilter` `[CÓDIGO] platform/observability/CorrelationIdFilter.java`, e o `02-UI-SPEC.md` já o expõe ao usuário como "Código de referência: {correlationId}" em toda recusa. Não inventar um segundo.

### Forma proposta

**`audit_event`**, no estilo `V1`/`V2`:

- `event_id uuid PK DEFAULT gen_random_uuid()`
- `occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)`
- `actor_identity_id uuid NULL REFERENCES local_identity(identity_id)` — nulo quando o ator é o sistema (semente da `D-47`)
- `actor_role varchar(32) COLLATE "C" NULL`
- `target_identity_id uuid NULL REFERENCES local_identity(identity_id)`
- `action varchar(64) COLLATE "C" NOT NULL` + `CHECK (action <> '')` + `CHECK (octet_length(action) <= 64)`
- `outcome varchar(16) COLLATE "C" NOT NULL CHECK (outcome IN ('PERMITIDO','BLOQUEADO'))` — `D-30` e `D-48` exigem auditar **tentativas bloqueadas**, e o painel S4 as exibe separadamente
- `previous_value`, `new_value` — `jsonb NULL` ou texto; ver a nota de expurgo abaixo
- `reason varchar(1024) COLLATE "C" NULL` + `CHECK` de octetos — obrigatório em correção de matrícula (`D-32`), aplicado na camada de serviço porque a obrigatoriedade é condicional à ação
- `correlation_id varchar(64) COLLATE "C" NULL`
- `idempotency_key varchar(128) COLLATE "C" NULL` + índice único parcial `WHERE idempotency_key IS NOT NULL`

### Imutabilidade — no banco, não por convenção

`AUDT-02` pede "proteção contra alteração". Convenção Java (`@Immutable` como em `LocalIdentity.java:16` `[CÓDIGO]`) protege contra o próprio ORM, e **não** contra `UPDATE`/`DELETE` por SQL. Três camadas, em ordem crescente de força:

1. **JPA:** `@Immutable` + `@Column(updatable = false)`, exatamente como `LocalIdentity` faz `[CÓDIGO] :26-48`.
2. **Privilégios:** o usuário de aplicação recebe `INSERT` e `SELECT` sobre `audit_event`, e **não** `UPDATE` nem `DELETE`. É a camada que efetivamente satisfaz `AUDT-02` e "acesso restrito". Custo: o usuário de aplicação é hoje o mesmo que roda as migrations Flyway `[CÓDIGO] application.yaml:37-44` — separar papéis exige decidir como o Flyway continua migrando. Ver OQ-6.
3. **Regra/trigger:** `CREATE RULE ... AS ON UPDATE TO public.audit_event DO INSTEAD NOTHING`, ou um `BEFORE UPDATE OR DELETE` trigger que levanta exceção. Funciona mesmo se os privilégios forem afrouxados por engano.

Recomendo **(1) + (3) na Fase 2**, com (2) explicitamente registrada como pendência de infraestrutura, porque separar papéis de banco toca o `compose.yaml` e o bootstrap do PostgreSQL — fora da allowlist natural de uma fase de aplicação.

Contra-argumento honesto: uma regra `DO INSTEAD NOTHING` faz o `UPDATE` **silenciosamente não acontecer**. Um trigger que levanta exceção é preferível: falha ruidosa é melhor que sucesso mentiroso, e um teste consegue observar a exceção. `[DOC — NÃO VERIFICADO]` quanto à sintaxe exata; ver A-9.

**Retenção (`AUDT-02` pede "retenção definida"):** definir um prazo é decisão de produto que ninguém tomou. Ver OQ-7.

**Expurgo da `D-37` versus imutabilidade:** se `previous_value`/`new_value` guardarem HMACs, o expurgo da versão comprometida precisa alcançar a auditoria — e uma auditoria imutável não pode ser expurgada. Saída limpa: **a auditoria nunca guarda o HMAC**, apenas `enrollment_id` e `hmac_version`. A tabela de vínculo guarda o digest; a auditoria referencia a linha. O expurgo então opera só sobre `ufu_enrollment` e a imutabilidade da trilha sobrevive. **Esta é uma decisão de design com consequência direta em `AUDT-01` ("versão anterior e nova")** — a "versão" registrada passa a ser a referência, não o valor. Recomendo e registro como suposição.

---

## Runtime State Inventory

A Fase 2 não é rename nem refactor, mas **muda contratos de runtime já materializados fora do código**, o que produz a mesma classe de risco. Inventário explícito:

| Categoria | Encontrado | Ação necessária |
|---|---|---|
| **Dados armazenados** | O realm `betufu-local` é importado por `start-dev --import-realm` `[CÓDIGO] infra/compose.yaml:47`, mas o Keycloak persiste em PostgreSQL (`KC_DB: postgres` `[CÓDIGO] :49`). **Um realm já importado não é reimportado a cada subida.** Mudar `registrationAllowed`/`verifyEmail` no JSON **não** altera um realm que já existe no banco | Determinar o procedimento: reset do volume via `scripts/reset-local.mjs`, ou mudança pela console de admin. **Isto é a diferença entre "o arquivo mudou" e "o Keycloak mudou".** Não verificado empiricamente; ver A-10 |
| **Configuração de serviço vivo** | Sessões Redis existentes sob `betufu:session` `[CÓDIGO] application.yaml:49`. Trocar para repositório indexado muda o formato das chaves e **não** migra as sessões existentes | Aceitar que todas as sessões vivas caem na implantação. Documentar; é aceitável em MVP |
| **Estado registrado no SO** | Nenhum. Nenhum agendador, serviço ou registro de SO foi encontrado ligado à BETUFU | Nenhuma |
| **Segredos e variáveis de ambiente** | `BETUFU_S3_*`, `BETUFU_DB_*`, `BETUFU_REDIS_*`, `BETUFU_OIDC_*`, `BETUFU_TECHNICAL_*` `[CÓDIGO] application.yaml`, `infra/compose.yaml`. A fase acrescenta pelo menos a chave HMAC (`D-33`) e a semente de administrador (`D-47`) | Nomes novos precisam de entrada em `infra/.env.example` — **arquivo bloqueado por política de permissão** (`CLAUDE.md`). O plano deve prever que o responsável o edite, não o agente |
| **Artefatos de build** | Cliente TypeScript gerado, 20 paths com digest governado `[CÓDIGO]`. Regenerar muda o digest e quebra `validate-angular-route-boundary.mjs:792` até a constante ser atualizada | Cada plano que toca o yaml regenera o cliente e propõe o digest novo ao responsável para aprovação |
| **Redis: notificações de keyspace** | `infra/compose.yaml:30` sobe o Redis sem `notify-keyspace-events` `[CÓDIGO]`. **Resolvido:** `ConfigureNotifyKeyspaceEventsAction` faz o `CONFIG SET` na inicialização e falha fechada se não puder `[ARTEFATO]` | Local: **nada a mudar** no compose. Homologação/produção: decidir entre permitir `CONFIG` ou usar `ConfigureRedisAction.NO_OP` + configuração no servidor. Registrar como pendência de infraestrutura |

**O `OidcRevocationKeycloakIT` autentica com o placeholder literal `${BETUFU_TECHNICAL_PASSWORD}`** porque o container do teste não recebe a variável (`CLAUDE.md`, *Pendências herdadas*). Se a fase mexer no realm de teste, esse arranjo precisa continuar funcionando.

---

## Common Pitfalls

### Pitfall 1: inverter o valor pinado e deixar a regressão negativa vazia

**O que dá errado:** `verifyEmail` e `repository-type` são hoje **os dois** valores usados como mutação negativa nas suítes Node. Inverter o `expectExact` sem repensar a mutação deixa uma prova que não pode falhar.
**Por que acontece:** o guard foi escrito quando existia um único valor legítimo. `STATE.md` já registra o padrão duas vezes (`OIDC_BOUNDARY`, `GENERATED_WITH_CREDENTIALS`).
**Como evitar:** para cada trava invertida, escrever a **propriedade** que sobrevive e derivar a mutação nova dela. Depois observar a mutação **vermelha** — `STATE.md`: "Uma mutação só conta como prova depois de observada vermelha."
**Sinal de alerta:** a suíte fica verde e o número de testes não muda.

### Pitfall 2: deduzir o comportamento do Keycloak da documentação

**O que dá errado:** `D-38` inteira depende de o Keycloak não emitir código de autorização com `VERIFY_EMAIL` pendente. `D-40` depende de o reset disparar algo observável.
**Por que acontece:** é caro subir o container; a documentação parece suficiente.
**Como evitar:** `CLAUDE.md` é literal — "Afirmação sobre Spring, Jackson, Angular ou qualquer biblioteca exige bytecode, execução ou inspeção do artefato real." Um IT contra o container fixado, no padrão de `OidcRevocationKeycloakIT` `[CÓDIGO]`, é o único aceite.
**Sinal de alerta:** um plano que trate A-2 ou A-3 como fato.

### Pitfall 3: achar que o índice único implementa a `D-35`

**O que dá errado:** com uma versão de chave o índice basta e todos os testes passam. Na rotação, a mesma matrícula sob `v2` produz digest diferente e entra.
**Como evitar:** teste que **configura duas versões ativas**, registra sob `v1`, rotaciona, e tenta registrar a mesma matrícula sob `v2` esperando recusa. Sem esse teste, `VERI-07` está não provada.
**Sinal de alerta:** todos os testes de HMAC usam uma única versão de chave.

### Pitfall 4: invalidar o grant e deixar a sessão viva (ou vice-versa)

**O que dá errado:** o `SessionInvalidationListener` avisa em javadoc que **não** invalida o `HttpSession` `[CÓDIGO] :19-22`, e que o `Outcome` **tem** de ser inspecionado `[CÓDIGO] :24-27`. Ignorar qualquer uma produz meia-invalidação.
**Como evitar:** o serviço único do §4 faz as duas coisas na ordem do 01-22 e propaga os outcomes para a auditoria.
**Sinal de alerta:** uma chamada a `onSessionInvalidated` cujo retorno é descartado.

### Pitfall 5: mutação multilinha inerte em checkout Windows

**O que dá errado:** `core.autocrlf=true` e `.gitattributes` que não cobre `backend/src/main/java/**` fazem mutação multilinha sobre fonte Java não casar. `STATE.md` mediu: era **uma** regressão inerte, não cinco, e a causa raiz permanece.
**Como evitar:** toda mutação multilinha nova usa `replaceAcrossLines`, que casa `\r?\n`. `02-CONTEXT.md` registra que a própria discussão tropeçou nisso.

### Pitfall 6: adiar o contrato OpenAPI

**O que dá errado:** custo já medido — 4 dos 7 arquivos novos do cliente regenerado no 01-26 `[STATE.md]`.
**Como evitar:** yaml no mesmo plano que o endpoint.

### Pitfall 7: vazar a matrícula por mensagem de exceção

**O que dá errado:** o valor rejeitado aparece numa mensagem de validação, é logado por um handler genérico, e `LogSanitizer` não pega (medido no §6).
**Como evitar:** auditar `ProblemDetailsHandler` e `FieldViolation`; regra ArchUnit que proíba o tipo da matrícula em argumento de logger e em DTO.
**Sinal de alerta:** qualquer `String matricula` que chegue a um `catch` genérico.

### Pitfall 8: subdeclarar `files_modified`

`STATE.md` registra três ocorrências (01-07: 9 contra 8; 01-23: 5 contra 3; 01-10). Esta fase toca realm, três validadores, dois arquivos de rota, yaml, cliente gerado e digest — a superfície é ampla e a allowlist errará por baixo se for estimada.
**Como evitar:** reconciliar contra `git status -uall` **antes** do handoff, conforme `CLAUDE.md`.

---

## Don't Hand-Roll

| Problema | Não construir | Usar | Por quê |
|---|---|---|---|
| HMAC | implementação própria de HMAC | `javax.crypto.Mac` com `HmacSHA256` | `[ARTEFATO]` garantido pelo `java.base` |
| Comparação de digest | `Arrays.equals` ou `==` | `MessageDigest.isEqual` | `[ARTEFATO]` tempo-constante verificado no fonte do JDK |
| Enumerar sessões por principal | índice próprio identidade→sessão | `SpringSessionBackedSessionRegistry` sobre `FindByIndexNameSessionRepository` | `[ARTEFATO]` a capacidade e o `SessionRegistry` nativo já existem; um índice próprio diverge silenciosamente na expiração |
| Unicidade sob concorrência | `SELECT` seguido de `INSERT` | índice único no PostgreSQL como árbitro | precedente `ON CONFLICT DO NOTHING` do `V1` `[CÓDIGO]` |
| Imutabilidade da auditoria | "ninguém dá UPDATE" | privilégio + trigger no banco | `AUDT-02` pede proteção, não etiqueta |
| Códigos de erro | estrutura paralela | `ApiErrorCode` | `[CÓDIGO]` javadoc: parte do contrato por `D-14` |
| Correlation ID | segundo identificador | `CorrelationIdFilter` | `[CÓDIGO]` já existe e já é exibido ao usuário |
| Sessão / cookie / CSRF | qualquer coisa manual | Spring Session + `csrf().spa()` | `[CÓDIGO] SecurityConfiguration.java:246-248` |
| Fluxo de senha e e-mail | tela BETUFU | páginas hospedadas do Keycloak | `AUTH-02` e `02-UI-SPEC.md` |
| Limite de tentativas | biblioteca nova | `INCR`+`EXPIRE` no Redis já presente | evita reabrir auditoria de supply chain |

**Insight:** quase tudo que esta fase precisa **já está no classpath ou no repositório**. O trabalho real não é escolher biblioteca — é **destravar contratos que a Fase 1 fechou deliberadamente**, e provar que cada destravamento preserva a propriedade que a trava protegia.

---

## Validation Architecture

### Test Framework

| Propriedade | Valor |
|---|---|
| Suíte agregada 1 (backend) | `node scripts/maven.mjs -f backend/pom.xml verify` — Surefire (`*Test`) + Failsafe (`*IT`) `[CÓDIGO] backend/pom.xml:165-167`, `STATE.md` |
| Suíte agregada 2 (Node) | `node --test scripts/*.test.mjs` — 481/481 verdes hoje `[STATE.md]` |
| Config backend | `backend/pom.xml` |
| Testes Node | `scripts/*.test.mjs` (5 arquivos) `[CÓDIGO]` |
| Gate de contrato | `node scripts/validate-phase1.mjs --contract` `[CÓDIGO] :108-147, :428` |
| ArchUnit / Modulith | `ModuleDependencyRulesTest`, `ModulithArchitectureTests` `[CÓDIGO]` |
| Containers | `GenericContainer` com `DockerImageName` fixado — **não há módulo Testcontainers de Redis nem Keycloak** `[CÓDIGO] backend/pom.xml:102-113` |

**Regra vinculante de `CLAUDE.md`:** "A prova agregada é obrigatória, e neste repositório são duas suítes." Declarar conclusão com apenas uma é cobertura parcial em silêncio — foi assim que a regressão do `OIDC_BOUNDARY` atravessou o 01-26. **Ambas, sem filtro, antes de qualquer task concluída.**

### Requisitos → mapa de teste

| Req | Comportamento | Tipo | Comando automatizado | Existe? |
|---|---|---|---|---|
| AUTH-02 | realm permite autocadastro; contrato do validador reformulado | Node | `node --test scripts/validate-keycloak-realm.test.mjs` | ❌ Onda 0 — regra e regressão novas |
| AUTH-02 | autocadastro real conclui e produz sessão BETUFU | IT Keycloak | `…verify -Dit.test=SelfRegistrationKeycloakIT` | ❌ Onda 0 |
| AUTH-03 | `VERIFY_EMAIL` impede conclusão do code flow (**prova de `D-38`**) | IT Keycloak | novo IT no padrão de `OidcRevocationKeycloakIT` | ❌ Onda 0 — **prova de A-2** |
| AUTH-03 | usuário técnico continua autenticando com `verifyEmail: true` | IT Keycloak | `OidcRevocationKeycloakIT` (existente) + regressão Node nova | ⚠️ existe, **regride** se `emailVerified` não subir |
| AUTH-04 | reset permanece habilitado e SMTP fixado | Node | `validate-keycloak-realm.test.mjs` | ✅ existe; preservar |
| PROF-01 | apelido salvo, editado, `NOT NULL` desde a criação | unit + IT | `BetufuProfileTest`, `ProfileControllerIT` | ❌ Onda 0 |
| PROF-01/D-44 | banco recusa apelido fora do padrão ASCII (homoglifo cirílico) | IT PostgreSQL | `ProfilePersistenceIT` | ❌ Onda 0 — **mutação: remover o `CHECK`** |
| PROF-02/PRIV-01 | DTO público não expõe e-mail, matrícula, HMAC | unit + ArchUnit | `PublicProfileDtoTest` + regra ArchUnit | ❌ Onda 0 |
| VERI-06 | normalizar→validar→HMAC, ordem e determinismo | unit | `MatriculaHmacTest` | ❌ Onda 0 |
| VERI-06/D-33 | contexto **não sobe** sem a chave, com chave curta ou não decodificável | unit de contexto | `HmacKeyFailClosedTest` | ❌ Onda 0 — **mutação: default na propriedade** |
| VERI-07 | segunda conta com a mesma matrícula é recusada | IT PostgreSQL | `UfuEnrollmentUniquenessIT` | ❌ Onda 0 |
| VERI-07/D-35 | **duas versões ativas**: registra sob `v1`, rotaciona, recusa sob `v2` | IT PostgreSQL | `UfuEnrollmentRotationIT` | ❌ Onda 0 — **a prova central da `D-35`** |
| VERI-07 | corrida concorrente não cria segunda associação | IT PostgreSQL | `UfuEnrollmentConcurrencyIT` | ❌ Onda 0 |
| VERI-09 | predicado "tem matrícula ativa" | unit | `EnrollmentEligibilityTest` | ❌ Onda 0 |
| VERI-11 | realm não declara identity provider | Node | `validate-keycloak-realm.test.mjs` | ❌ Onda 0 — regra nova |
| OPER-01 | conceder e revogar separadamente, com auditoria | IT | `RoleGrantControllerIT` | ❌ Onda 0 |
| OPER-02 | escopo respeitado | unit | `RoleScopeTest` | ❌ Onda 0 |
| OPER-03/D-48a | **autoconcessão bloqueada** | unit + IT | `RoleGuardTest`, `RoleGrantControllerIT` | ❌ Onda 0 — **`D-48` exige mutação discriminante** |
| OPER-03/D-48b | **último administrador geral não se revoga** | unit + IT | idem | ❌ Onda 0 — **mutação discriminante** |
| OPER-04 | moderador sem acesso a saldo/esporte | IT | matriz de autorização | ❌ Onda 0 |
| OPER-06 | painel só devolve blocos do papel; agregados sem PII | IT | `OperationalPanelIT` | ❌ Onda 0 |
| IDEN-05/D-49 | alteração de papel **encerra todas** as sessões daquela identidade | IT Redis | `RoleChangeSessionTerminationIT` | ❌ Onda 0 — **falha hoje: `repository-type: default` não enumera** |
| IDEN-05 | repositório indexado presente e habilitado | Node + IT | `validate-runtime-prerequisites.test.mjs` reformulado | ❌ Onda 0 — **regra invertida** |
| D-40 | reset de senha encerra todas as sessões | IT Keycloak+Redis | `PasswordResetSessionTerminationIT` | ❌ Onda 0 — **depende de A-3** |
| PRIV-01 | matrícula/HMAC não aparecem em log, URL ou telemetria | ArchUnit + IT | regra ArchUnit + `ObservabilityIT` estendido | ❌ Onda 0 — `LogSanitizer` medido: **não cobre** |
| AUDT-01 | evento grava ator, alvo, ação, antes/depois, motivo, correlation, idempotência | IT | `AuditEventIT` | ❌ Onda 0 |
| AUDT-01 | **tentativas bloqueadas são auditadas** (`D-30`, `D-48`) | IT | idem | ❌ Onda 0 |
| AUDT-02 | `UPDATE`/`DELETE` em `audit_event` **falham** | IT PostgreSQL | `AuditImmutabilityIT` | ❌ Onda 0 — **mutação: remover o trigger** |
| D-47 | semente idempotente: segunda execução não duplica nem reescreve | IT | `AdminSeedIdempotencyIT` | ❌ Onda 0 |
| — | rotas de produto **existem** em produção e tracer/diagnostic **não** | Node | `validate-angular-route-boundary.test.mjs` | ❌ Onda 0 — três regras reformuladas, `PRODUCTION_TRACER_FORBIDDEN` preservada |
| — | paridade código↔OpenAPI e digest do cliente | Node | `node scripts/validate-phase1.mjs --contract` | ✅ existe; consumir |

### Sampling Rate

- **Por commit de task:** `node scripts/maven.mjs -f backend/pom.xml verify` **e** `node --test scripts/*.test.mjs`, ambos sem filtro. Execução focal esconde quebra de contexto Spring (`CLAUDE.md`).
- **Por merge de onda:** as duas suítes **mais** `node scripts/validate-phase1.mjs --contract`, **mais** a transcrição das contagens somadas de `target/surefire-reports` e `target/failsafe-reports` (classes, testes, falhas, erros, ignorados por suíte) — nunca estimadas.
- **Portão de fase:** as duas suítes verdes, contagens transcritas, e cada mutação discriminante desta fase **observada vermelha** contra o código antigo e restaurada com conferência de hash.

### Wave 0 Gaps

- [ ] **Reformular** `scripts/validate-keycloak-realm.mjs` regras de `registrationAllowed`/`verifyEmail` — de artefato para propriedade, com mutação nova
- [ ] **Reformular** `scripts/validate-runtime-prerequisites.mjs:1298-1302` — a propriedade é "técnico autentica sem required action pendente", não "realm não verifica e-mail"
- [ ] **Substituir** a mutação `validate-runtime-prerequisites.test.mjs:1029` — `verifyEmail = true` deixa de mutar
- [ ] **Reformular** `validate-runtime-prerequisites.mjs:973` e a mutação `.test.mjs:878` para `repository-type`
- [ ] **Reformular** `validate-angular-route-boundary.mjs` `PRODUCTION_ROUTES_EXPORT`/`_EMPTY`/`_SHAPE` e `DEVELOPMENT_ROUTES_SHAPE`; preservar `PRODUCTION_TRACER_FORBIDDEN`
- [ ] Regra ArchUnit de privacidade (matrícula/HMAC fora de logger e DTO)
- [ ] `HmacKeyFailClosedTest` — contexto não sobe sem chave válida
- [ ] `UfuEnrollmentRotationIT` — duas versões ativas, a prova central da `D-35`
- [ ] `AuditImmutabilityIT` — `UPDATE`/`DELETE` falham no banco
- [ ] IT de fluxo real contra Keycloak que prove `D-38` (A-2)
- [ ] Spike de verificação de A-3 (reset → gatilho observável) **antes** de planejar `D-40`
- [ ] Decidir a política de `notify-keyspace-events` para homologação/produção (`CONFIG SET` permitido versus `ConfigureRedisAction.NO_OP`) — resolvido para o ambiente local por inspeção de artefato
- [ ] IT que leia a chave real do índice de principal no Redis, encerrando A-12 — **primeira tarefa da frente `D-49`**
- [ ] Fixture Redis compartilhada para os ITs de sessão, no padrão de `RedisSessionIT`
- [ ] Instalação de framework: **nenhuma**

---

## Security Domain

### Categorias ASVS aplicáveis

| Categoria | Aplica | Controle padrão nesta fase |
|---|---|---|
| V2 Autenticação | sim | Keycloak hospedado; a BETUFU nunca recebe senha (`AUTH-02`). `verifyEmail: true` (`D-38`). MFA opcional (`D-50`) |
| V3 Gestão de sessão | sim | Spring Session Redis, 8h ocioso, `changeSessionId` na fixação `[CÓDIGO] SecurityConfiguration.java:249-250`; cookie `http-only`, `secure`, `same-site` `[CÓDIGO] application.yaml:13-19`. **Invalidação por identidade é a lacuna do §4** |
| V4 Controle de acesso | sim | `authorizeHttpRequests` com `anyRequest().denyAll()` `[CÓDIGO] :244-245`; papéis em PostgreSQL; guardas `D-48` |
| V5 Validação de entrada | sim | Bean Validation + `CHECK` no banco. Apelido ASCII estrito é defesa contra homoglifo (`D-44`) |
| V6 Criptografia | sim | `HmacSHA256` do `java.base`; chave em ambiente, falha fechada (`D-33`); comparação tempo-constante |
| V7 Log e monitoramento | sim | `LogSanitizer` + `CorrelationIdFilter`; **`PRIV-01` exige garantia estrutural, ver §6** |
| V8 Proteção de dados | sim | Matrícula pseudonimizada e nunca conservada (`VERI-06`); auditoria sem dado desnecessário (`AUDT-02`) |
| V13 API | sim | RFC 9457 via `ProblemDetailsHandler`; contrato OpenAPI mantido junto do código |

### Padrões de ameaça

| Padrão | STRIDE | Mitigação |
|---|---|---|
| Enumeração de matrícula pela recusa explícita | Information Disclosure | `D-31`: uma matrícula por conta + limite de tentativas. **Aceito, não eliminado** |
| Registro de matrícula alheia | Spoofing | **Aceito explicitamente em `D-27`**. Correção administrativa é a única saída (`D-32`) |
| Sósia por homoglifo no apelido | Spoofing | `D-44` ASCII estrito, travado por `CHECK` no banco |
| Escalada por autoconcessão | Elevation of Privilege | `D-48a`, com teste que falha se a guarda sumir |
| Travamento da governança | Denial of Service | `D-48b` |
| Autoridade obsoleta em sessão viva | Elevation of Privilege | `D-49` — **hoje inexequível pelo §4** |
| Sessão sobrevivendo à troca de senha | Spoofing | `D-40` — **mecanismo não confirmado, A-3** |
| Dump do banco revelando matrículas | Information Disclosure | HMAC com chave fora do banco (`D-33`) |
| Chave HMAC vazada | Information Disclosure | `D-37`: rotacionar + reinformar + expurgar. Procedimento escrito nesta fase |
| CSRF nas superfícies novas | Tampering | `csrf().spa()` já ativo `[CÓDIGO] :246-248`. **Cuidado: isentar o back-channel logout abre um buraco se o path não for exato** |
| Vazamento de matrícula em log/exceção | Information Disclosure | §6 — garantia estrutural, sanitizador **não** cobre |
| Governança dependendo só de senha | Spoofing | **Aceito em `D-51`**; mitigação real são as guardas de duas pessoas |

---

## Environment Availability

Auditoria por leitura de arquivos e do disco. **Nenhum serviço foi contatado** — o limite de somente leitura proíbe.

| Dependência | Exigida por | Declarada | Versão | Observação |
|---|---|---|---|---|
| JDK | build | ✓ em disco | 25.0.4.7 Adoptium (`JAVA_HOME` aponta para ele) | `[ARTEFATO]` `src.zip` inspecionado |
| Maven | build | ✓ | enforcer `[3.9.16,3.9.17)` `[CÓDIGO] backend/pom.xml:148` | não executado |
| PostgreSQL | dados | ✓ compose | 18.4-trixie, digest fixado | não contatado |
| Redis | sessão + limite | ✓ compose | 8.10.0-trixie, digest fixado | sem `notify-keyspace-events` no comando `[CÓDIGO] :30`, mas o Spring Session o define por `CONFIG SET` no boot `[ARTEFATO]` |
| Keycloak | IdP | ✓ compose | 26.7.1, digest fixado | não contatado |
| Mailpit | `VERIFY_EMAIL` e reset | ✓ compose | v1.30.7 | realm já aponta para `mailpit:1025` |
| SeaweedFS | S3 local | ✓ compose | 4.41 | **não usado nesta fase** — `D-25` tirou o comprovante do MVP |
| Docker | ITs com Testcontainers | presumido | — | não verificado; não executei Docker |
| `spring-session-data-redis` 4.1.0 | §4 | ✓ `~/.m2` | 4.1.0 | `[ARTEFATO]` |
| `spring-security-*` 7.1.0 | §2 | ✓ `~/.m2` | 7.1.0 | `[ARTEFATO]`; note que 6.2.4, 7.0.3 e 7.0.4 também estão no `~/.m2` — **não confundir ao inspecionar** |

**Sem fallback:** o `VERIFY_EMAIL` depende de SMTP funcionando; o Mailpit cobre o local, e homologação/produção continuam sem escolha de provedor — a mesma pendência que `STATE.md` registra para o S3.

---

## Assumptions Log

Toda linha aqui é `[DOC — NÃO VERIFICADO]`. **O planejador deve converter cada uma em tarefa de verificação, nunca em premissa.**

**A-6 foi encerrada durante esta sessão** e por isso não aparece mais na tabela: a dúvida sobre notificações de keyspace do Redis foi resolvida por inspeção de `ConfigureNotifyKeyspaceEventsAction` e `ConfigureRedisAction` no bytecode. O resultado está em §4 como `[ARTEFATO]`. A numeração das demais foi preservada para que nenhuma referência `A-NN` fique ambígua.

| # | Afirmação | Seção | Risco se estiver errada | Como resolver |
|---|---|---|---|---|
| A-1 | `VERIFY_EMAIL` é required action **computada** do estado do usuário, não entrada literal em `requiredActions` | §1 | O guard `requiredActions.length === 0` continua verde enquanto o login do usuário técnico quebra — falha silenciosa | IT que faça o code flow real contra o container 26.7.1 com `verifyEmail: true` e `emailVerified: false` |
| A-2 | Com `VERIFY_EMAIL` pendente, o Keycloak **não** emite código de autorização, então a BETUFU nunca recebe ID token de conta não confirmada (base de `D-38`) | §1 | Existe estado intermediário que nem o backend nem o `02-UI-SPEC.md` preveem; e o backend **não tem** `email_verified` (escopo `openid` isolado + userinfo desligado) | IT de fluxo completo com conta não confirmada, observando o que chega ao `/login/oauth2/code/keycloak` |
| A-3 | O Keycloak 26.7.1 **não** encerra automaticamente as sessões ao concluir `reset-credentials`, logo back-channel logout pode não ser o gatilho de `D-40` | §2 | Toda a frente `D-40` é replanejada tarde | Spike contra o container: habilitar back-channel logout no cliente, executar um reset, observar se o logout token chega |
| A-4 | Chave HMAC-SHA256 de 32 bytes é a recomendação de comprimento | §3 | Chave subdimensionada; `SecretKeySpec` só recusa vazia `[ARTEFATO]`, não curta | Confirmar em NIST SP 800-107 / RFC 2104 e travar o mínimo em código |
| A-5 | PostgreSQL suporta índice único **parcial** (`CREATE UNIQUE INDEX ... WHERE`) | §3, §5 | O modelo de estado da matrícula e do papel não se sustenta | Documentação oficial + IT que exercite a cláusula |
| A-7 | O operador `~` do PostgreSQL faz match de regex POSIX e serve num `CHECK` | §5 | O padrão de apelido `D-44` fica só no Bean Validation, contornável por outro caminho de escrita | Documentação + IT que tente inserir `а` cirílico |
| A-8 | `ddl-auto: validate` valida tabelas e colunas, **não** índices | §5 | Índice único parcial ausente passa despercebido pelo boot | Migration com índice deliberadamente ausente + observar se o contexto sobe |
| A-9 | Sintaxe exata de trigger/rule para bloquear `UPDATE`/`DELETE` em `audit_event` | §7 | `AUDT-02` fica só na convenção Java | Documentação + `AuditImmutabilityIT` |
| A-10 | `--import-realm` **não** reimporta um realm que já existe no PostgreSQL do Keycloak | Runtime State | O JSON muda, o realm rodando não, e o time depura um fantasma | Ler a documentação de import do Keycloak 26 e confirmar o procedimento operacional |
| A-11 | O ID token sob escopo `openid` isolado **não** carrega `email` nem `email_verified` | §1, §5 | A semente da `D-47` por e-mail não tem o dado; defesa em profundidade sobre `email_verified` é impossível sem mudar escopo | Inspecionar um ID token real emitido pelo container, ou o `betufu-bff` client scope no realm |
| A-12 | **Reduzida por medição.** Está verificado `[ARTEFATO]` que o índice guarda `Authentication#getName()` (SpEL `authentication?.name` sobre `SPRING_SECURITY_CONTEXT`) e que `DefaultOidcUser` usa `IdTokenClaimNames.SUB` como chave padrão. **Resta** confirmar que o `OidcUserService` usa esse padrão, e não uma chave vinda da `ClientRegistration` | §4 | `findByPrincipalName` devolve vazio e `D-49` "funciona" invalidando zero sessões — falha silenciosa | IT que grave uma sessão autenticada real e leia a chave do índice no Redis. **Barato; primeira tarefa da frente `D-49`** |

**Nenhuma destas está verificada** — A-12 apenas teve o escopo reduzido, e o que resta dela continua não medido. As afirmações que **foram** verificadas estão marcadas `[ARTEFATO]` ou `[CÓDIGO]` no corpo do documento, com arquivo e linha.

Ordem de ataque recomendada, por custo crescente: **A-12** (um IT de sessão, minutos) → **A-5, A-7, A-8, A-9** (documentação PostgreSQL + ITs que este projeto já sabe escrever) → **A-4** (leitura normativa) → **A-1, A-2, A-11** (um único IT de fluxo real contra o container Keycloak resolve os três de uma vez) → **A-10** (procedimento operacional) → **A-3** (spike dedicado, o mais caro e o que mais muda o plano).

---

## Open Questions (RESOLVED)

Esta seção preserva as perguntas originais como índice histórico, mas a situação autoritativa é a registrada abaixo. “Deferida” significa que a decisão da Fase 2 não foi fabricada nem reaberta: o item foi atribuído explicitamente a um gate posterior e não bloqueia as Waves 14–16.

| Questão | Status | Decisão efetivamente tomada | Artefato autoritativo |
|---|---|---|---|
| OQ-1 — padrão da matrícula UFU | RESOLVIDA | O responsável aprovou `^[A-Z0-9]{5,20}$` sobre a matrícula já normalizada; a ordem canônica continua recorte, `Locale.ROOT`, remoção de separadores, validação e HMAC. | `02-06-SUMMARY.md`, `MatriculaNormalizer.java` |
| OQ-2 — limite de tentativas | RESOLVIDA | Foram aprovadas e implementadas 5 recusas por conta em 15 minutos e 50 por origem em 1 hora, com reserva atômica no Redis e sem revelar contadores ao usuário. | `02-07-SUMMARY.md`, `EnrollmentAttemptLimiterIT` |
| OQ-3 — D-37 versus auditoria imutável | RESOLVIDA | A auditoria referencia vínculo/estado e versão da chave quando aplicável, nunca o digest HMAC; assim o expurgo D-37 não exige alterar a trilha. | `02-04-SUMMARY.md` §OQ-3, `.planning/REQUIREMENTS.md` `AUDT-01`, `02-21-SUMMARY.md` |
| OQ-4 — chave da semente administrativa | RESOLVIDA | `BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER` usa o UUID público canônico de uma conta que já realizou o primeiro login; e-mail/claim não é identificador da semente. Ausente, inválido ou inexistente falha fechado. | `02-08-SUMMARY.md`, `02-17-SUMMARY.md` |
| OQ-5 — MFA/evidência na auditoria | RESOLVIDA | O MVP não cria coluna de MFA nem evidência documental; MFA não governa autorização e verificação documental saiu do escopo. O requisito foi reconciliado. | `02-04-SUMMARY.md` §OQ-5, `.planning/REQUIREMENTS.md` `AUDT-01` |
| OQ-6 — usuário de migration versus runtime | RESOLVIDA E IMPLEMENTADA | Flyway e runtime usam identidades PostgreSQL distintas; runtime recebe somente `SELECT`/`INSERT` sobre `audit_event`, sem `UPDATE`, `DELETE`, `TRUNCATE` ou DDL, com trigger em profundidade. | `02-04-SUMMARY.md` §OQ-6, `02-21-SUMMARY.md` |
| OQ-7 — retenção da auditoria | RESOLVIDA PARA O MVP; POLÍTICA PÚBLICA DEFERIDA | No MVP fechado, a trilha vive por toda a vida do ambiente sem exclusão automática. Política jurídica por classe de dado permanece gate obrigatório anterior ao lançamento público sob `PRIV-05`; não é decisão aberta da Wave 14. | `02-04-SUMMARY.md` §OQ-7, `02-21-SUMMARY.md`, `.planning/REQUIREMENTS.md` `PRIV-05` |
| OQ-8 — repositório indexado versus índice próprio | RESOLVIDA E IMPLEMENTADA | O responsável escolheu o repositório indexado nativo do Spring Session; a decisão e sua razão DRY foram registradas antes da implementação. | `02-MEASUREMENTS.md` §5, `02-09-SUMMARY.md` |
| OQ-9 — frequência de troca do apelido | RESOLVIDA E IMPLEMENTADA | Criação não inicia cooldown, primeira troca real é imediata, trocas posteriores exigem 30 dias e repetir o mesmo valor é idempotente. | `02-03-SUMMARY.md`, `ProfileControllerIT` |
| OQ-10 — redirect pós-logout | CONFIGURAÇÃO PRESENTE; PROVA DE RUNTIME DEFERIDA AO FECHAMENTO | O realm contém `post.logout.redirect.uris=http://localhost:4200/`. A confirmação do comportamento real de logout e a reconciliação final do registro continuam explicitamente atribuídas ao fechamento da fase; isso não bloqueia as Waves 14–16 e não deve ser marcado como runtime comprovado. | `02-KEYCLOAK-OPERACAO.md` §9/§11, `.planning/STATE.md` pendência de fechamento |

---

## State of the Art

| Abordagem antiga | Abordagem atual neste projeto | Impacto na Fase 2 |
|---|---|---|
| Guardar CPF/matrícula em claro ou hash sem chave | HMAC com chave fora do banco, versionada | Um dump do banco não revela matrícula (`D-33`) |
| Papéis no IdP | Papéis autoritativos na aplicação (`D-20`, `D-46`) | Autoridade congela na sessão → `D-49` é obrigatória, não opcional |
| Token no browser | BFF com sessão no servidor | Invalidação é possível de verdade — e por isso é exigida |
| MFA obrigatório para admin | MFA opcional (`D-50`), guardas de duas pessoas como mitigação | Nenhuma superfície de MFA nesta fase (`D-52`) |
| Auditoria por log de aplicação | Tabela relacional com imutabilidade no banco | `AUDT-02` só se cumpre no banco |
| Front-channel logout | Back-channel logout OIDC | Existe no Spring Security 7.1.0 `[ARTEFATO]`, mas o gatilho de `D-40` é A-3 |

**Obsoleto / a não repetir:**
- Guard que pina o valor único existente em vez da propriedade — três ocorrências novas nesta fase, além das duas que `STATE.md` já registra.
- Adiar o contrato OpenAPI — custo medido em 4 arquivos.
- Deduzir comportamento de framework da documentação — origem do P0 do `iss`.

---

## Project Constraints (de `CLAUDE.md` e `AGENTS.md`)

Diretivas acionáveis que o planejador **deve** respeitar. Têm a mesma autoridade das decisões travadas.

**De `AGENTS.md`:**
1. Antes de cada plano, onda ou bloco material: apresentar objetivo, abordagem, arquivos, comandos, impactos, riscos e resultado esperado.
2. Aprovação explícita e específica antes de decisão de produto ou arquitetura, instalação, scaffold, alteração de código, banco ou infraestrutura, e ação externa.
3. **Stage, commit e push exigem aprovações separadas.**
4. Uma aprovação vale só para o escopo descrito.
5. **Modo pedagógico obrigatório** — o responsável é desenvolvedor Java júnior. Explicar em linguagem clara antes de agir; traduzir jargão na primeira ocorrência; interpretar a saída dos comandos, inclusive falhas.
6. GSD em modo `interactive`, `auto_advance` desativado, sem `--auto`, `--all`, `--force`.

**De `CLAUDE.md`:**
7. **Prova de discriminação:** teste novo só é confiável depois de falhar contra o código antigo. Mutar, confirmar vermelho, restaurar, conferir hash.
8. **Evidência empírica para framework:** bytecode, execução ou inspeção do artefato real. Nunca deduzir de documentação ou memória.
9. **Cuidado com prova vazia:** verificar se o teste poderia falhar.
10. **Prova agregada nas duas suítes**, sem filtro, antes de declarar qualquer task concluída.
11. Nunca incluir segredo, token, senha, cookie ou URL sensível em relatório.
12. **`infra/.env.example` está bloqueado por política de permissão.** Não tentar contornar.
13. Trabalho em `master`: commits pequenos, cada um revertível sozinho; nunca deixar `master` sem compilar ou com teste vermelho.
14. Congelar a árvore durante revisão externa; não editar nenhum path até o veredito.
15. Reconciliar `files_modified` contra `git status -uall` antes do handoff.
16. Transcrever as contagens dos relatórios `TEST-*.xml`, nunca estimá-las.
17. Toda operação Git mutável autorizada fica registrada em `STATE.md`, com hashes e sincronização de `origin/master`.
18. Emenda de commit muda o hash — não citar o hash do próprio commit dentro dele.
19. Mutação multilinha sobre fonte Java usa `replaceAcrossLines` (`core.autocrlf=true`).
20. Em conflito, `AGENTS.md` prevalece — inclusive sobre o commit por task do `git-integration.md`.

---

## Sources

### Primária — ALTA confiança (`[ARTEFATO]` / `[CÓDIGO]`)

Inspeção de bytecode via parser de class file próprio, sem executar JVM:
- `~/.m2/.../spring-session-data-redis/4.1.0/spring-session-data-redis-4.1.0.jar` — `RedisSessionRepository`, `RedisIndexedSessionRepository`, `ConfigureNotifyKeyspaceEventsAction`, `ConfigureRedisAction`, listagem completa de classes
- `~/.m2/.../spring-session-core/4.1.0/spring-session-core-4.1.0.jar` — `FindByIndexNameSessionRepository`, `PrincipalNameIndexResolver`, `SingleIndexResolver`, `SpringSessionBackedSessionRegistry`, `DefaultCookieSerializer`
- `~/.m2/.../spring-security-oauth2-core/7.1.0/spring-security-oauth2-core-7.1.0.jar` — `DefaultOidcUser` (chave de nome padrão)
- `~/.m2/.../spring-security-config/7.1.0/spring-security-config-7.1.0.jar` — `OidcLogoutConfigurer`, `BackChannelLogoutConfigurer`, `OidcBackChannelLogoutFilter`, `OidcBackChannelLogoutHandler`
- `~/.m2/.../spring-security-oauth2-client/7.1.0/spring-security-oauth2-client-7.1.0.jar` — `OidcSessionRegistry`, `OidcSessionInformation`, `InMemoryOidcSessionRegistry`
- `C:/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/lib/src.zip` — `java.base/java/security/MessageDigest.java`, `java.base/javax/crypto/Mac.java`, `java.base/javax/crypto/spec/SecretKeySpec.java`

Fontes do repositório, lidas integralmente ou nas linhas citadas:
- `backend/src/main/resources/application.yaml`, `application-dev.yaml`, `application-homolog.yaml`, `application-prod.yaml`
- `backend/src/main/resources/db/migration/V1__create_local_identity.sql`, `V2__add_platform_guards.sql`
- `backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java`, `OidcTechnicalAuthorityMapper.java`
- `backend/src/main/java/br/com/betufu/platform/session/SessionSecurityConfiguration.java`, `SessionInvalidationListener.java`, `SessionTerminationReason.java`, `OidcSessionRevocationService.java` (grep dirigido)
- `backend/src/main/java/br/com/betufu/platform/api/ApiErrorCode.java`
- `backend/src/main/java/br/com/betufu/platform/observability/LogSanitizer.java`
- `backend/src/main/java/br/com/betufu/identity/internal/*.java`
- `backend/pom.xml`, `backend/openapi/betufu.yaml`
- `infra/compose.yaml`, `infra/keycloak/realm-betufu.json`
- `scripts/validate-keycloak-realm.mjs`, `validate-runtime-prerequisites.mjs`, `validate-runtime-prerequisites.test.mjs`, `validate-angular-route-boundary.mjs`, `validate-phase1.mjs`
- `frontend/src/app/app.routes.ts`, `app.routes.development.ts`, `app.routes.production.ts`, `frontend/src/app/api/generated/**`
- `.planning/PROJECT.md` (via `STATE.md`), `REQUIREMENTS.md`, `ROADMAP.md`, `STATE.md`, `02-CONTEXT.md`, `02-UI-SPEC.md`, `CLAUDE.md`, `AGENTS.md`

### Secundária — MÉDIA confiança

Nenhuma. Não houve consulta a documentação oficial online nesta sessão.

### Terciária — BAIXA confiança (`[DOC — NÃO VERIFICADO]`)

Conhecimento de treino, **não verificado nesta máquina**: A-1 a A-12 no *Assumptions Log*. Nenhuma delas deve virar premissa de plano sem verificação prévia.

---

## Metadata

**Confiança por área:**

| Área | Nível | Razão |
|---|---|---|
| Repositório de sessão e capacidade de enumerar (§4) | **ALTA** | Bytecode de `RedisSessionRepository`, `RedisIndexedSessionRepository`, `FindByIndexNameSessionRepository`, `PrincipalNameIndexResolver` e `SpringSessionBackedSessionRegistry` inspecionado; travas citadas com linha |
| Travas de validador (§1, §6) | **ALTA** | Todos os sítios citados com arquivo e linha |
| Estilo de migration e modelo de dados (§5) | **ALTA** para o estilo (migrations lidas integrais); **MÉDIA** para o esquema proposto, que é proposta |
| Construção HMAC (§3) | **ALTA** para algoritmo e comparação (fonte do JDK); **MÉDIA** para comprimento de chave (A-4) e índice parcial (A-5) |
| Superfície de back-channel logout (§2) | **ALTA** para o que existe no classpath e suas constantes; **BAIXA** para o gatilho de `D-40` (A-3) |
| Notificações de keyspace do Redis (§4) | **ALTA** — `ConfigureNotifyKeyspaceEventsAction` e `ConfigureRedisAction.NO_OP` inspecionados no bytecode; A-6 encerrada |
| Comportamento do Keycloak (§1) | **BAIXA** — A-1, A-2, A-10, A-11 não medidas |
| Auditoria (§7) | **MÉDIA** — estratégia sólida, sintaxe exata não verificada (A-9) |

**Data da pesquisa:** 2026-08-20

**Válido até:** 2026-09-19 (30 dias) para as descobertas de artefato e código, que só mudam se as dependências mudarem. As linhas de `[DOC — NÃO VERIFICADO]` **não têm validade** — são pendências de verificação, não conhecimento.

## Suplemento D-40 — Event Listener UPDATE_PASSWORD do Keycloak (2026-08-22)

### Escopo e conclusão

A medição A-3 na imagem fixada mostrou que o reset hospedado preserva a sessão SSO/refresh token e não envia back-channel logout; o logout administrativo de controle o enviou. Logo, o plano 02-24 baseado nesse canal não satisfaz mais D-40 e deve ser replenejado, não apenas adaptado. [VERIFIED: .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-MEASUREMENTS.md:76-124]

`plugin-evento-keycloak` é a decisão já tomada. Ela não decide, por si, como tornar a entrega durável: essa topologia permanece um checkpoint humano bloqueador (D-40-B).

### SPI, registro e identidade correlacionável

| Necessidade | Fato verificado | Direção de plano |
|---|---|---|
| Callbacks | `org.keycloak.events.EventListenerProvider` declara `onEvent(Event)` e `onEvent(AdminEvent, boolean)`; a fábrica é `org.keycloak.events.EventListenerProviderFactory`, que estende `ProviderFactory<EventListenerProvider>`. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventListenerProvider.java] [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventListenerProviderFactory.java] | Criar uma fábrica por `KeycloakSession` e, se D-40-C for aprovado, tratar usuário e admin no mesmo provider. |
| Service Loader | O guia oficial exige `META-INF/services/org.keycloak.events.EventListenerProviderFactory` com o nome totalmente qualificado da fábrica. [CITED: https://www.keycloak.org/docs/latest/server_development/index.html] | Esse arquivo é obrigatório; Spring não descobre este JAR. |
| Habilitação por realm | O `EventBuilder` seleciona fábricas não globais cujo `id` aparece em `RealmModel.getEventsListeners()`; `isGlobal()` é `false` por padrão. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventBuilder.java] [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventListenerProviderFactory.java] | Manter `isGlobal=false`, acrescentar o ID no `eventsListeners` de `betufu-local`, atualizar o validador estrito e seu teste. O realm atual não tem essa chave e o validador fecha a lista de chaves raiz. [VERIFIED: infra/keycloak/realm-betufu.json:1-130] [VERIFIED: scripts/validate-keycloak-realm.mjs:83-101] |
| Correlação | `Event` expõe `id`, `time`, `type`, `realmId`, `clientId`, `userId`, `sessionId`, IP, erro e detalhes; `EventBuilder` atribui UUID ao ID antes do despacho. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/Event.java] [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventBuilder.java] | Usar `event.id` como idempotency key de origem; transportar apenas `realmId`, `userId`, tipo canônico e instante. Nunca usar IP, `sessionId` ou `clientId` como identidade. |

**Recomendação:** resolver a mensagem somente para uma identidade BETUFU existente por `(issuer, subject)`. O modelo local possui exatamente esses campos:

<!-- DATA_D40A9K_START -->
```java
private String issuer;
private String subject;
```
<!-- DATA_D40A9K_END -->

[VERIFIED: backend/src/main/java/br/com/betufu/identity/internal/LocalIdentity.java:30-34]

Não assumir por convenção que `event.getUserId()` sempre é o `sub` OIDC. A medição atual usou o mesmo `userId` para conferir o `sub` do token de logout, mas o novo teste de container deve comparar ID de evento, ID do usuário Keycloak e `sub` do ID token antes de liberar esse mapeamento. [VERIFIED: backend/src/test/java/br/com/betufu/platform/security/PasswordResetTriggerSpikeIT.java:273-277]

### Timing transacional e a lacuna de HTTP síncrono

O contrato oficial diz que os callbacks executam **dentro da transação**. Para efeitos não reversíveis, recomenda `KeycloakTransactionManager.enlistAfterCompletion(KeycloakTransaction)`, que os deixa ocorrer só após conclusão bem-sucedida. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventListenerProvider.java] O `EventBuilder` captura/loga exceções dos listeners; lançar exceção não é um contrato de rollback ou de retry. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventBuilder.java]

- HTTP dentro de `onEvent` pode revogar na BETUFU e a senha ainda sofrer rollback: falso positivo.
- HTTP em `enlistAfterCompletion` evita esse falso positivo, mas se o processo cair entre commit e envio não há evento persistido nem retry: perda.
- Inbox durável somente no receptor deduplica o que chegou; não recupera perda anterior à chegada.

**Recomendação obrigatória:** proibir HTTP direto do callback, mesmo com `try/catch`. D-40 só pode alegar entrega durável se houver uma fonte persistida de retry após o commit.

```text
mudança de senha -> Event Listener (na transação) -> persistência atômica de origem
                                                       |
                                                       v
                                      worker pós-commit/retry -> HTTPS autenticado
                                                       |
                                                       v
                                      inbox BETUFU única -> consumidor idempotente
                                                       |
                                                       v
                         terminateAllSessionsFor(subject, SECURITY)
```

### Evento canônico e reset administrativo

Em 26.7.1, `UpdatePassword` registra `UPDATE_CREDENTIAL` com `Details.CREDENTIAL_TYPE` password **e também** emite o legado `UPDATE_PASSWORD`; este último está depreciado. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/services/src/main/java/org/keycloak/authentication/requiredactions/UpdatePassword.java] [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventType.java]

**Recomendação técnica:** implementar a intenção de D-40 com `UPDATE_CREDENTIAL` + credential type password, ignorando `UPDATE_PASSWORD` legado. Ouvir ambos gera duas mensagens com IDs diferentes, que não serão deduplicadas por `event.id`.

**Checkpoint humano D-40-A:** ratificar esse filtro canônico, em vez de filtrar literalmente só `UPDATE_PASSWORD`. Se o tipo legado for exigido, registrar o risco de depreciação e provar a ausência de dupla entrega para a configuração escolhida.

O endpoint administrativo `PUT /users/{id}/reset-password` emite `AdminEvent` com `ResourceType.USER` e `OperationType.ACTION`; não é o mesmo evento de usuário. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/services/src/main/java/org/keycloak/services/resources/admin/UserResource.java] [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/admin/ResourceType.java] [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/admin/OperationType.java] `AdminEvent.getResourceId()` devolve o último segmento do caminho e, para esta rota, não é o usuário alvo. [CITED: https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/admin/AdminEvent.java]

**Checkpoint humano D-40-C:** decidir se reset administrativo integra D-40. Se incluir, filtrar realm + `USER` + `ACTION`, extrair e validar o segmento `{id}` de `users/{id}/reset-password` e provar esse path em container. Se excluir, declarar expressamente que a sessão pode persistir após mudança administrativa.

### Alternativas de durabilidade

| Alternativa | Benefício real | Lacuna | Nova dependência/decisão |
|---|---|---|---|
| HTTP síncrono + endpoint autenticado/inbox BETUFU | Autentica e deduplica o que recebeu. | Não é durável: envio antes de rollback ou perda pós-commit/restart. | **Rejeitar para D-40.** |
| Ingestão autenticada + inbox + fonte de retry persistente | O aceite da inbox é atômico e um retry pode reenviar até `2xx`. | Só é válida se a própria fonte de retry for persistida atomicamente com a senha; caso contrário apenas renomeia a lacuna. | Definir a fonte e a recuperação de crash. |
| Broker durável | Desacopla consumidor, retry e DLQ. | Publicar do callback ainda não é atômico com a senha; precisa outbox/ponte transacional antes do broker. | Broker, ACL, credenciais, retenção, monitoramento e DLQ: aprovação explícita. |
| Outbox do Keycloak + worker + inbox BETUFU | Registro de origem na transação, envio pós-commit, retry/restart e entrega ao menos uma vez. | Requer modelo, worker e operação no Keycloak; o guia marca o SPI JPA customizado como API não suportada, com risco de upgrade. | Nova persistência/migração Keycloak e decisão de compatibilidade. [CITED: https://www.keycloak.org/docs/latest/server_development/index.html] |

**Checkpoint humano D-40-B — BLOQUEADOR:** antes do plano executável, aprovar uma topologia: (1) outbox transacional no banco Keycloak, reconhecendo `JpaEntityProvider`/Liquibase customizados; (2) broker durável **mais** origem atômica comprovada; ou (3) outra fonte persistida demonstrada por teste de crash/restart. Não escolher banco, broker, esquema ou agendador por inferência.

### Contrato de segurança e operação recomendado

Os itens abaixo são recomendações, não funcionalidade existente:

- **Autenticação/autorização:** endpoint interno HTTPS dedicado; assinatura por mensagem cobrindo `key-id`, timestamp, método, path, `sourceEventId` e hash do corpo. D-40-B deve escolher HMAC ou mTLS. Autenticar antes de processar, aceitar apenas realm/issuer e esquema esperados, e não conceder ao plugin permissão administrativa.
- **Replay/idempotência:** inbox com unicidade `(sourceSystem, sourceEventId)` gravada no mesmo commit do aceite; janela temporal, ID único e `2xx` somente após persistir. A assinatura impede adulteração; a inbox remove duplicata/replay.
- **Privacidade/auditoria:** transportar somente ID, instante, tipo, `realmId` e sujeito. Nunca senha, representação admin, email, IP, `sessionId`, cookies, tokens ou mapa livre de detalhes. Registrar correlação, tentativa, status, `key-id` e motivo redigido; PRIV-01 já veda segredos/tokens/cookies nos logs. [VERIFIED: .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-CONTEXT.md:72-80]
- **Timeout/retry/DLQ:** valores explícitos e limitados de conexão/leitura, backoff exponencial com jitter, teto de tentativas, `5xx`/rede reexecutáveis e assinatura/esquema/autorização em quarentena. DLQ alerta e só reprocessa por ação autorizada. Os números e o RTO são decisão operacional pendente.
- **Ordenação/rotação:** não prometer ordenação global; serializar por sujeito quando necessário e tornar revogação monotônica. Usar chave atual + anterior só em janela de rotação auditada; segredos entram por configuração segura, nunca realm JSON, JAR ou logs.
- **Falha:** uma entrega falha não desfaz mudança de senha; fica pendente na fonte durável, alerta e recupera. O plano deve fixar o intervalo máximo aceitável até a revogação e o owner de recovery.

### Consumidor BETUFU: reutilizar a única revogação

Após autenticar, deduplicar e localizar a identidade existente, o consumidor deve chamar exclusivamente:

<!-- DATA_D40B7N_START -->
```java
identitySessionTerminationService.terminateAllSessionsFor(
    subject,
    SessionTerminationReason.SECURITY
);
```
<!-- DATA_D40B7N_END -->

[VERIFIED: backend/src/main/java/br/com/betufu/platform/session/IdentitySessionTerminationService.java:58-77] [VERIFIED: backend/src/main/java/br/com/betufu/platform/session/SessionTerminationReason.java:3-8]

O serviço central já busca por principal, remove sessões locais, aciona o listener de invalidação e inspeciona o resumo; falha remota não reabre sessão local. [VERIFIED: backend/src/main/java/br/com/betufu/platform/session/IdentitySessionTerminationService.java:17-30] O novo fluxo registra resultado e trata falha conforme política existente, mas não reimplementa Redis, `SessionInvalidationListener` ou revogação de grant.

### Imagem, build e configuração da versão fixada

O compose atual fixa `quay.io/keycloak/keycloak:26.7.1` no digest `sha256:f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6` e inicia `start-dev --import-realm`.

<!-- DATA_D40C3R_START -->
```yaml
image: quay.io/keycloak/keycloak:26.7.1@sha256:f1f1f01e472c8a78df40d8f2a49a925274eda4d3d80d5f6edbb5c880ee3c01c6
command: ["start-dev", "--import-realm"]
```
<!-- DATA_D40C3R_END -->

[VERIFIED: infra/compose.yaml:46-69]

O procedimento oficial é copiar o JAR para `/opt/keycloak/providers` e executar `/opt/keycloak/bin/kc.sh build` na imagem customizada antes de iniciá-la. Providers compartilham classloader e não são isolados, portanto apenas código/dependências revisados devem entrar na imagem. [CITED: https://www.keycloak.org/server/containers] [CITED: https://www.keycloak.org/server/configuration-provider]

**Recomendação de implantação:** módulo Maven isolado do provider, APIs Keycloak 26.7.1 e bytecode Java 21; Dockerfile multi-stage a partir do digest fixado, JAR em `providers/`, `kc.sh build`, e compose apontando para a imagem resultante enquanto preserva o import do realm. A imagem oficial Keycloak usa Java 21, enquanto o backend é compilado em Java 25; não reutilizar este alvo sem compatibilização. [CITED: https://www.keycloak.org/server/supported-configurations] [VERIFIED: backend/pom.xml:10-24]

A alteração de `realm-betufu.json` para declarar `eventsListeners` deve alterar a lista exata do validador e teste negativo correspondente. [VERIFIED: scripts/validate-keycloak-realm.mjs:8-29] [VERIFIED: scripts/validate-keycloak-realm.mjs:83-101] Nenhuma dependência Maven foi instalada nesta pesquisa; a auditoria automatizada de legitimidade do GSD suporta npm/PyPI/crates, não Maven. [VERIFIED: gsd-tools package-legitimacy invocation, 2026-08-22] Fixar Keycloak 26.7.1 e obter aprovação antes do primeiro install/build.

### Arquivos e módulos prováveis (não criar nesta pesquisa)

| Ação | Alvo provável | Responsabilidade |
|---|---|---|
| Criar | `keycloak-password-event-listener/pom.xml`, `.../BetufuPasswordEventListener.java`, `.../BetufuPasswordEventListenerFactory.java` | JAR SPI, filtro canônico, payload mínimo e ligação à topologia escolhida. |
| Criar | `keycloak-password-event-listener/src/main/resources/META-INF/services/org.keycloak.events.EventListenerProviderFactory` | Registro Service Loader da fábrica. |
| Condicional D-40-B | entidades/outbox/worker; se JPA for escolhido, fábrica JPA, service file e changelog Liquibase no módulo Keycloak | Persistência, claim, retry, DLQ e recuperação após restart. |
| Criar/modificar | `infra/keycloak/Dockerfile`, `infra/compose.yaml` | Imagem pré-construída e configuração segura de destino/chaves. |
| Modificar | `infra/keycloak/realm-betufu.json`, `scripts/validate-keycloak-realm.mjs` e teste | Habilitar listener e preservar contrato de realm. |
| Criar/modificar | controller/validador de assinatura, inbox/migração/repositório/consumer do backend; `SecurityConfiguration.java` e `application.yaml` | Aceite autenticado, deduplicação e chamada da revogação central. A cadeia atual possui regras explícitas e `anyRequest().denyAll()`, então o endpoint deve ter matcher estrito, não abertura ampla. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:214-246] |
| Criar/modificar | testes Java, IT Keycloak/Testcontainers e validadores Node de realm/imagem | Provas de contrato, duplicata, replay, falha, restart e D-40 E2E. |
| Replanejar, não executar como está | `.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-24-PLAN.md` | O plano back-channel é evidência histórica, mas não implementação de D-40 depois de A-3. [VERIFIED: .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-24-PLAN.md:85-95] |

Os nomes novos são alvos de planejamento, não contrato já criado; a estrutura final depende de D-40-B.

### Estratégia de testes, Maven/Node e prova D-40

| Camada | Prova necessária |
|---|---|
| Unitária do provider | Aceita só `UPDATE_CREDENTIAL` password, ignora legado/erro/outro tipo, monta payload mínimo e não chama HTTP no callback. |
| Unitária admin (se incluído) | Aceita somente realm + `USER` + `ACTION` e o path estrito `users/{id}/reset-password`; rejeita variantes e não usa `getResourceId()` como usuário. |
| Container Keycloak 26.7.1 | Imagem customizada descobre Service Loader, realm seleciona listener, reset hospedado emite o evento escolhido, e prova `userId`--`sub`; exercitar também dupla emissão/legado e admin quando incluído. |
| Falha/retry/restart | Receptor indisponível, timeout, `5xx`, queda e restart do worker; provar que a origem persistiu depois do commit e retoma sem perda. |
| Inbox/replay | Mesma mensagem, assinatura inválida, timestamp expirado e sujeito/issuer desconhecido; uma revogação por duplicatas e nenhuma chamada de serviço em mensagem inválida. |
| DLQ/recovery | Exaurir tentativas, alertar, auditar de forma redigida e reprocessar por procedimento autorizado. |
| E2E D-40 | Criar sessão BETUFU, reset hospedado, aguardar entrega e provar que todas as sessões locais foram encerradas por `terminateAllSessionsFor(..., SECURITY)`; incluir admin se D-40-C o determinar. |

Antes da execução, o plano deve incluir comandos Maven do módulo plugin e backend e comandos Node dos validadores de realm/compose; nenhum foi rodado nesta pesquisa. O backend já declara Spring Boot 4.1.0, Java 25 e Testcontainers. [VERIFIED: backend/pom.xml:10-24] [VERIFIED: backend/pom.xml:91-119]

### Ameaças ASVS L1 a levar ao plano

ASVS é a referência OWASP de controles verificáveis; a nova API interna e a cadeia de sessão exigem threat model específico mesmo em tráfego serviço-a-serviço. [CITED: https://owasp.org/www-project-application-security-verification-standard/]

| Ameaça | Controle de planejamento |
|---|---|
| Evento forjado/credencial excessiva | Assinatura ou mTLS, `key-id`, menor privilégio, rotação e endpoint exclusivo. |
| Replay/adulteração | Hash de corpo assinado, janela temporal e unique key de inbox. |
| DoS, indisponibilidade ou crash pós-commit | Fonte persistida, timeout, jitter/retry, DLQ, alerta e teste de restart. |
| Revogar sujeito errado | Realm/issuer fixos, identidade local existente, prova `userId`--`sub` e parser admin estrito. |
| Vazamento de segredo/sessão | Payload mínimo, redaction e proibição de tokens, representations e details. |
| Endpoint amplo | Matcher Spring exato, autenticação antes de processamento e `denyAll` preservado. |

### Decisões pendentes para plano executável

1. **D-40-A:** ratificar o gatilho `UPDATE_CREDENTIAL` + password e ignorar o legado `UPDATE_PASSWORD`.
2. **D-40-B (bloqueadora):** aprovar topologia de durabilidade, persistência/infraestrutura e recovery operacional.
3. **D-40-C:** incluir ou excluir explicitamente reset administrativo.
4. **Política operacional:** escolher HMAC ou mTLS, RTO/limites/DLQ, retenção de inbox/outbox/auditoria e owner de recuperação.

### Fontes primárias

- [EventListenerProvider 26.7.1](https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventListenerProvider.java) — timing transacional; [Event](https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/Event.java), [EventBuilder](https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventBuilder.java) e [EventType](https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/server-spi-private/src/main/java/org/keycloak/events/EventType.java) — campos, IDs e despacho.
- [UpdatePassword](https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/services/src/main/java/org/keycloak/authentication/requiredactions/UpdatePassword.java) e [UserResource administrativo](https://raw.githubusercontent.com/keycloak/keycloak/26.7.1/services/src/main/java/org/keycloak/services/resources/admin/UserResource.java) — semântica de senha em 26.7.1.
- [Guia de desenvolvimento](https://www.keycloak.org/docs/latest/server_development/index.html), [containers](https://www.keycloak.org/server/containers) e [configuração de providers](https://www.keycloak.org/server/configuration-provider) — Service Loader, build e extensão JPA.
- [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/) — baseline de segurança.
