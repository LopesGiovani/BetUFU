# Phase 3: Experiência Esportiva do Piloto - Research

**Researched:** 2026-08-26
**Domain:** catálogo esportivo versionado, administração escopada, vínculo atleta-conta, importação CSV, mídia e experiência pública Angular
**Confidence:** HIGH para o estado do repositório e decisões de produto; MEDIUM para as duas dependências novas e detalhes físicos ainda sujeitos aos checkpoints humanos

<user_constraints>
## User Constraints (from CONTEXT.md)

O conteúdo das três subseções abaixo foi copiado literalmente de `03-CONTEXT.md`; ele é restrição, não alternativa de pesquisa. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md:18-89,160-171]

### Locked Decisions

## Decisões de implementação

**Numeração:** esta série continua o `02-CONTEXT.md`, que terminou em `D-52`.

### Catálogo esportivo genérico

- **D-53:** O domínio segue a hierarquia `Esporte → Competição → Edição → Categoria → Fase → Evento`. A Olimpíada UFU é uma competição, 2026 é uma edição e os documentos de 2025 servem somente como referência de domínio. — **Reversibility:** costly — fixar competição ou edição diretamente nos eventos exigiria migrar dados e contratos públicos quando a segunda competição fosse cadastrada.
- **D-54:** A compatibilidade operacional pertence ao **formato do evento**, não a uma lista rígida de modalidades. O MVP detalha eventos `HEAD_TO_HEAD`; modalidades com provas coletivas ou vários competidores entram no catálogo e na agenda básica, mas sem baterias, raias, marcas, pódios ou regras próprias.
- **D-55:** Todas as modalidades conhecidas podem ser catalogadas desde o início. A adequação de cada uma ao modelo deverá ser reavaliada antes de ganhar operação detalhada; a extensão futura para formatos multi-competidor deve ser possível, mas não justifica criar classes vazias ou estruturas especulativas agora.

### Organizações, equipes e favorito

- **D-56:** A atlética, delegação ou organização esportiva é permanente entre edições. Cada edição cria suas próprias equipes por modalidade/categoria e seus próprios elencos, preservando o histórico de 2026, 2027 e anos seguintes.
- **D-57:** O favorito do usuário aponta para a organização esportiva inteira, não para uma equipe de uma modalidade. A página da organização agrega equipes, elencos públicos autorizados, próximos eventos e resultados. Esta decisão substitui a semântica “equipe favorita” reservada pela `D-45`; o atual nome técnico `favoriteTeamId` deve ser reconciliado pelo planejamento sem manter um contrato público enganoso. — **Reversibility:** costly — a referência já existe no perfil e sua mudança pode exigir migration e regeneração de contrato.
- **D-58:** Um mesmo atleta possui um registro esportivo global e pode participar de vários elencos, modalidades e edições. O sistema não duplica a identidade esportiva para cada equipe.

### Administração escopada e publicação

- **D-59:** O administrador geral cria competições/edições e atribui `ADMINISTRADOR_COMPETICAO`. Vários administradores podem compartilhar o mesmo UUID de competição em `scopeRef`; cada um opera somente seu escopo e não pode conceder papéis.
- **D-60:** Dados esportivos nascem em `RASCUNHO`, passam por pré-visualização e só ficam públicos após ação explícita. Correção posterior exige fonte ou motivo e preserva ator, horário e versões. Operações concorrentes não podem sobrescrever silenciosamente o trabalho de outro administrador.
- **D-61:** Uma única pessoa autorizada pode publicar no MVP; não existe dupla aprovação obrigatória para cadastro esportivo comum.

### Agenda, locais e navegação pública

- **D-62:** A entrada pública é orientada por agenda: `Hoje`, próximos eventos e filtros por esporte, competição/edição, categoria, fase e estado. Cada item deixa clara a competição a que pertence; páginas de competição, organização, equipe, atleta e evento complementam a navegação.
- **D-63:** Local é reutilizável e pode conter espaços internos, como `Centro Esportivo UFU → Ginásio 05`. O espaço interno, endereço, mapa e observações são opcionais; o evento pode permanecer com `LOCAL_A_DEFINIR`.

### Chaveamento e classificação

- **D-64:** O chaveamento oficial é transcrito manualmente pelo administrador; a BETUFU não sorteia confrontos no MVP. Ele é derivado dos eventos ligados, admite rodadas, `bye`, disputa de terceiro lugar e participante definido pelo vencedor de evento anterior, sem manter uma cópia editável divergente.
- **D-65:** Fases de grupos e pontos corridos exibem jogos e resultados, mas a tabela/classificação é transcrita do boletim oficial. Critérios automáticos de pontuação e desempate ficam para motores futuros por modalidade/regulamento.

### Fontes, resultados e liquidação futura

- **D-66:** Resultado público é registrado com fonte oficial. O boletim oficial mais recente prevalece somente quando declara correção ou substituição; fontes anteriores permanecem no histórico. Informação verbal ou mensagem informal pode gerar pendência, mas não confirma o placar.
- **D-67:** O administrador pode confirmar sozinho quando estiver seguro ou marcar `AGUARDANDO_REVISAO` para ajuda opcional de outro administrador. O resultado confirmado atualiza agenda e chaveamento; a Fase 3 não movimenta créditos.
- **D-68:** A Fase 6 consome a versão esportiva já confirmada; não recria nem reconfirma o cadastro do placar. No MVP, quem registrou ou confirmou o resultado pode também autorizar aquela versão para liquidação. Os atores dessas ações devem permanecer separados no histórico para tornar a política de duas pessoas (*maker-checker*) uma evolução futura, obrigatoriamente reavaliada antes de prêmio real, crescimento operacional relevante ou parceria institucional. Correção, reversão e reliquidação nunca apagam versões anteriores. Esta decisão substitui somente a exigência anterior de segundo confirmador para **resultados**; o segundo administrador no override excepcional de odds continua obrigatório. — **Reversibility:** costly — exigir pessoas distintas depois altera autorização e fluxos da liquidação, mas não deve exigir remodelar o histórico se os atores já forem persistidos separadamente.

### Atletas, matrícula e vínculo com conta

- **D-69:** Atleta pode existir sem conta BETUFU. Ao cadastrá-lo, o administrador informa a matrícula apenas como entrada transitória; o backend normaliza, calcula o mesmo HMAC versionado da Fase 2 e descarta o valor bruto, sem resposta, log, URL ou persistência consultável.
- **D-70:** Matrícula coincidente prova apenas que os dois cadastros informaram o mesmo valor; não prova vínculo ativo com a UFU, titularidade ou legitimidade esportiva. O MVP não recebe comprovante, não usa armazenamento documental e não apresenta selo `VERIFICADO_PELA_UFU`.
- **D-71:** Correspondência única entre HMAC de conta e de atleta cria uma **proposta**, nunca vínculo silencioso. A pessoa precisa aceitar ou rejeitar; ambiguidade, HMAC já ligado ou múltiplos candidatos bloqueiam a associação e exigem tratamento administrativo auditado.

### Elencos e importação

- **D-72:** Elenco pertence à equipe daquela edição. Nasce como rascunho, é publicado manualmente e, depois disso, qualquer alteração exige motivo e auditoria. A futura abertura de mercado deve depender de elenco publicado.
- **D-73:** O MVP não cadastra titulares nem reservas por partida. Escalações ficam como evolução futura; o modelo atual não deve transformar `TITULAR` ou `RESERVA` em propriedade permanente do atleta.
- **D-74:** O administrador pode cadastrar individualmente ou importar CSV UTF-8 usando um modelo fixo da BETUFU. A importação valida todas as linhas e é atômica: havendo um erro, salva zero; estando válida, cria tudo em rascunho. Equipes, modalidades e categorias referenciadas precisam existir e não são criadas implicitamente.
- **D-75:** A entrada tabular usa uma linha lógica comum com nome esportivo, matrícula transitória, organização/equipe, modalidade, categoria e período de vínculo. O parser deve ficar separado da validação/importação para permitir um parser XLSX futuro sem duplicar regras, HMAC, transação ou auditoria.

### Perfil público do atleta

- **D-76:** Depois de aceitar o vínculo, somente o próprio atleta controla foto, curso, biografia e links de redes sociais, todos opcionais e removíveis. Administrador não publica foto pessoal em nome dele; atleta sem conta usa avatar padrão.
- **D-77:** Equipes, modalidades, participações e resultados vêm dos registros esportivos oficiais. No MVP, estatísticas públicas são genéricas e confiáveis — participações, vitórias, derrotas e resultados disponíveis —; gols, cartões, sets, tempos e métricas específicas ficam para evolução por modalidade.
- **D-78:** Matrícula, HMAC, e-mail, identificadores internos e dados de autenticação nunca fazem parte do perfil público. Biografia não aceita HTML executável e links sociais precisam de validação segura.

### Política de apostas do MVP

- **D-79:** O vínculo de atleta **não bloqueia apostas no MVP**: a pessoa pode apostar em qualquer mercado, inclusive em evento ou equipe de que participa e tanto a favor quanto contra. O risco de informação interna e influência sobre créditos/ranking virtuais foi apresentado e aceito para evitar complexidade prematura.
- **D-80:** A relação atleta–equipe–evento deve permanecer modelada para permitir uma política futura sem reconstruir o domínio. A regra será reavaliada obrigatoriamente antes de prêmio real, crescimento relevante ou mudança de enquadramento. Administradores gerais continuam proibidos de apostar em qualquer evento e administradores de competição continuam proibidos no próprio escopo.

### the agent's Discretion

### Discrição dos agentes posteriores

Pesquisador e planejador podem decidir, de forma conservadora e registrada:

- nomes técnicos, pacotes e limites internos do novo módulo esportivo, preservando Spring Modulith;
- paginação, ordenação estável, códigos de erro e textos finais;
- limites de tamanho/formato para foto, biografia e CSV, e processamento seguro da imagem;
- estratégia de cache e atualização da agenda pública;
- mecanismo de concorrência otimista e formato físico das versões/fontes;
- desenho visual exato, estados vazios e responsividade, respeitando o contrato de UI que será produzido antes da implementação frontend.

### Deferred Ideas (OUT OF SCOPE)

## Ideias adiadas

- política que bloqueie ou limite apostas de atletas em eventos próprios;
- confirmação financeira obrigatória por uma segunda pessoa distinta;
- motores completos para provas com vários competidores;
- estatísticas específicas por modalidade;
- titulares e reservas por partida;
- importação XLSX;
- cálculo automático de classificação e desempate por regulamento/modalidade;
- integração automática com fonte oficial;
- verificação documental de atleta e armazenamento privado de comprovantes, se um caso futuro realmente justificar a coleta.

</user_constraints>

<phase_requirements>
## Phase Requirements

As descrições são literais de `REQUIREMENTS.md`. [VERIFIED: .planning/REQUIREMENTS.md:12,29-44]

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | Visitante pode consultar competições, agenda, partidas, equipes e resultados públicos sem criar conta. | Namespace REST público explícito, shell Angular público fora do guard e testes anônimos/falha de sessão. |
| SPRT-01 | Administrador autorizado pode cadastrar esporte, competição, edição, categoria, fase e local reutilizável com espaço interno opcional dentro do escopo atribuído; a Olimpíada UFU 2026 é a primeira edição real de um catálogo genérico. | Raízes/versionamento do catálogo, local/espaço, bootstrap geral e autorização por competição. |
| SPRT-02 | Administrador da competição pode cadastrar organizações esportivas, equipes por edição, atletas e vínculos de elenco por modalidade, categoria e período, individualmente ou por CSV oficial validado de forma atômica. | Modelo normalizado, importação em duas etapas, uma transação e constraints de corrida. |
| SPRT-03 | Um atleta pode existir no catálogo sem possuir conta BETUFU. | `athlete` independente de `identity` e vínculo opcional histórico. |
| SPRT-04 | Para cadastrar um atleta, o administrador informa a matrícula somente como entrada transitória; o servidor normaliza, calcula HMAC versionado e descarta o valor bruto. A coincidência não comprova vínculo oficial, titularidade ou legitimidade esportiva. | Porta consumer-owned que reutiliza o comportamento da Fase 2 e contrato de erro sem eco. |
| SPRT-05 | O cadastro do atleta pode guardar o mesmo HMAC versionado usado na verificação da conta, sem expor nem conservar o número consultável e sem receber comprovante documental no MVP. | Tabela isolada de fingerprint, digest/version e provas arquiteturais de privacidade. |
| SPRT-06 | Quando uma conta verificada produzir HMAC igual ao de um atleta ainda sem usuário, o sistema propõe vínculo único e auditável entre os registros. | Evento pós-commit sem digest e reconciliação idempotente nos dois sentidos. |
| SPRT-07 | HMAC já ligado a outra conta, múltiplos candidatos ou divergência de dados bloqueiam o vínculo automático e abrem revisão manual. | Caso de revisão/candidatos, locks/uniqueness e não enumeração entre escopos. |
| SPRT-08 | A pessoa recebe a proposta de vínculo, precisa aceitá-la explicitamente e pode rejeitar ou contestar uma associação de atleta incorreta; foto e campos pessoais do atleta só são liberados após o aceite. | Máquina de estados histórica e endpoints `/me` autorizados pelo `identityId` da sessão. |
| SPRT-09 | Elenco nasce como rascunho, é publicado manualmente antes da abertura do primeiro mercado afetado e não possui escalação por partida no MVP; alteração ou vínculo tardio exige motivo e auditoria. | Snapshot versionado do elenco, sem propriedade titular/reserva, contrato futuro de leitura. |
| SPRT-10 | Administrador pode cadastrar evento com participantes, horário canônico, fuso, fase, fonte e local opcionalmente detalhado por espaço interno. | Evento/revisão/slots, instante UTC + zona IANA, fonte e FK composta local-espaço. |
| SPRT-11 | Administrador pode manter evento e resultado como rascunho, publicar, suspender, adiar, cancelar, informar e corrigir com fonte, motivo, versões e auditoria; o mesmo administrador pode confirmar no MVP ou pedir revisão opcional. | Estados ortogonais, resultado imutável por versão e concorrência otimista. |
| SPRT-12 | Pessoa pode navegar primeiro pela agenda e filtrar por data, esporte, competição/edição, categoria, fase e estado, abrindo a página completa do evento. | Tracer começa na agenda pública, query paginada estável e filtros URL-addressable. |
| SPRT-13 | Pessoa pode abrir páginas de organização, equipe e atleta com equipes da edição, elenco público autorizado, próximos eventos, resultados e estatísticas genéricas confiáveis; campos pessoais opcionais são controlados pelo atleta vinculado. | Projeções públicas separadas, agregações derivadas e perfil esportivo self-owned. |
| SPRT-14 | O piloto opera detalhadamente eventos de confronto direto confiáveis, incluindo chaveamento derivado das partidas; outras modalidades podem aparecer no catálogo e agenda básica, e classificações de grupos/pontos corridos são transcritas da fonte oficial sem motor automático de desempate. | Formato no evento, bracket por dependências e classificação manual versionada. |
| SPRT-15 | Usuário pode escolher como favorito uma organização esportiva permanente, que agrega suas equipes de diferentes modalidades e edições; a Fase 3 reconcilia a referência técnica de equipe criada na Fase 2 sem perder o perfil existente. | Migration de rename preservadora, endpoint dedicado e FK validada após inspeção de runtime. |
| SPRT-16 | Após aceitar o vínculo, somente o atleta controla foto, curso, biografia e redes sociais opcionais; mídia e links recebem validação segura, campos removíveis não incluem dados privados e estatísticas públicas permanecem genéricas no MVP. | Perfil esportivo separado, pipeline de imagem privado/reencodado e DTO público allowlist. |

</phase_requirements>

## Summary

A Fase 3 deve ser planejada como um conjunto de fatias verticais sobre um novo módulo `sports`, e não como uma migration seguida por todas as telas. O primeiro tracer precisa provar a cadeia completa: `ADMINISTRADOR_GERAL` cria a primeira competição, atribui um administrador escopado, um evento nasce em rascunho e é publicado, e um visitante anônimo o vê na agenda `/`. Só depois entram organizações, atletas, elencos, CSV, resultados, chaveamento, classificação e mídia. Essa ordem preserva a regra MVP/tracer-first e revela cedo os riscos de escopo, publicação, OpenAPI e rota pública. [VERIFIED: .planning/ROADMAP.md:237-253; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md:9-78; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-UI-SPEC.md:133-156,472-512]

O contrato físico mais importante é separar identidade estável, revisão de trabalho concorrente e versão pública imutável. `lock_version` evita overwrite; `version_no`/UUID histórico identifica o fato publicado. A Fase 6 deve referenciar exatamente a versão confirmada do resultado, enquanto os atores de registro, confirmação e futura autorização para liquidação ficam em campos/registros distintos. Isso torna correção aditiva e evita redesenhar liquidação depois. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md:36-38,50-54; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-UI-SPEC.md:442-451]

Há risco real de compressão indevida: são 17 requisitos, 20 superfícies e 156 considerações de estado já aprovadas, além de duas dependências Maven candidatas e migrations one-way. O planejador não deve caber tudo em 3–5 planos. A recomendação é manter a Fase 3 no roadmap, mas usar aproximadamente 15 planos controlados; se o volume operacional for inaceitável, a divisão 3A–3E precisa de aprovação explícita porque altera o roadmap. [VERIFIED: .planning/REQUIREMENTS.md:12,29-44; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-UI-SPEC.md:516-531,545-554; AGENTS.md:3-46]

**Primary recommendation:** aprovar primeiro o grafo físico D-53/D-60/D-68 e entregar o tracer `admin escopado → rascunho/publicação → agenda pública` antes de expandir o domínio.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|--------------|----------------|-----------|
| Catálogo, publicação e versões | API / Backend (`sports`) | PostgreSQL | Backend é autoridade; banco preserva FKs, corrida e história. |
| Escopo administrativo | API / Backend (`sports` + portas de identidade) | PostgreSQL | O UUID real da competição vem do agregado; capability do browser é só descoberta. |
| Agenda e páginas públicas | API / Backend | Browser / Client | Backend filtra somente versão publicada; Angular apresenta e mantém filtros na URL. |
| Vínculo por HMAC | API / Backend (`sports` com adapter `verification`) | PostgreSQL | Matrícula existe só durante o comando; proposta e revisão são persistidas sem valor bruto. |
| CSV | API / Backend | PostgreSQL | Parser produz linha lógica; validação total antecede uma única transação. |
| Foto do atleta | API / Backend + armazenamento privado | Browser / Client | Backend autentica, decodifica/reencoda e publica por handler BETUFU; browser não fala com bucket. |
| Chaveamento e classificação | API / Backend | Browser / Client | Bracket deriva de eventos/resultados; classificação é snapshot manual da fonte. |
| Roteamento público/protegido | Browser / Client | API / Backend | Angular separa shells; Spring Security continua autoridade de endpoint. |

O mapa é uma recomendação arquitetural derivada das decisões e do stack existente. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md:22-78; backend/src/main/java/br/com/betufu/sports/package-info.java:1-4; backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:216-260]

## Project Constraints (from AGENTS.md)

- Antes de cada plano, onda ou bloco material, explicar objetivo, abordagem, arquivos/comandos, impactos, riscos e resultado; obter aprovação específica antes de arquitetura, dependência, scaffold, código, banco, infraestrutura ou ação externa. [VERIFIED: AGENTS.md:3-18]
- Stage, cada commit, push, issue, PR, release, integração e limpeza exigem aprovações separadas; aprovação não se propaga. [VERIFIED: AGENTS.md:6-13,35-46]
- Manter modo interativo, avanço automático desligado e executar fase/onda com `--interactive`; não usar `--auto`, `--all`, `--force` ou autonomia sem autorização literal. [VERIFIED: AGENTS.md:20-26; .planning/config.json:1-45]
- Ensinar cada passo em linguagem acessível a desenvolvedor Java júnior, explicar comando antes e interpretar saída depois; `resumo` encurta e `aprofundar` expande. [VERIFIED: AGENTS.md:12-18]
- Um worktree/branch neutro por plano; até três planos independentes por onda, nunca dois executores no mesmo checkout; worker não executa Git, instalação, rede, Docker/Testcontainers ou suíte agregada. [VERIFIED: AGENTS.md:28-38]
- O orquestrador repete testes focais, executa as provas materiais, reconcilia allowlist e congela HEAD/status/hashes antes da revisão; qualquer correção invalida o parecer. [VERIFIED: AGENTS.md:35-41]
- As duas suítes agregadas obrigatórias são, verbatim, `node scripts/maven.mjs -f backend/pom.xml verify` e `node --test scripts/*.test.mjs`; prova parcial ou teste que não executou o alvo não vale. [VERIFIED: CLAUDE.md:71-111,160-176]
- Toda alegação de comportamento de framework precisa de prova empírica; toda regressão relevante precisa de mutação discriminante que falhe contra o comportamento antigo. [VERIFIED: CLAUDE.md:42-69]

## Current Repository Baseline

O módulo existe apenas como marcador:

> `@ApplicationModule(displayName = "Esportes")`

[VERIFIED: backend/src/main/java/br/com/betufu/sports/package-info.java:1-4]

O projeto usa Spring Boot `"4.1.0"`, Java `"25"`, Spring Modulith `"2.1.0"` e ArchUnit `"1.4.2"`; ainda não há entidade, repository, service ou controller esportivo. [VERIFIED: backend/pom.xml:12-27,88-123; backend/src/main/java/br/com/betufu/sports/package-info.java:1-4]

Os named interfaces hoje são, verbatim, `"api"`, `"identity"`, `"operations"` e `"events"`. A verificação existente chama `ApplicationModules.verify()`, e o inventário literal de módulos inclui `"identity", "verification", "sports", "odds", "betting", "ledger", "settlement", "ranking", "chat", "audit"` mais `"platform"`. [VERIFIED: backend/src/main/java/br/com/betufu/platform/api/package-info.java:1-4; backend/src/main/java/br/com/betufu/platform/identity/package-info.java:1-4; backend/src/main/java/br/com/betufu/platform/operations/package-info.java:1-4; backend/src/main/java/br/com/betufu/platform/session/events/package-info.java:1-4; backend/src/test/java/br/com/betufu/ModulithArchitectureTests.java:15-48]

Os papéis vigentes são, verbatim:

> `ADMINISTRADOR_GERAL(false)`
> `ADMINISTRADOR_COMPETICAO(true)`
> `MODERADOR(true)`

[VERIFIED: backend/src/main/java/br/com/betufu/identity/OperationalRole.java:11-20]

As capabilities de navegação vigentes são, verbatim:

> `VIEW_OPERATIONAL_PANEL`
> `MANAGE_ROLES`
> `CORRECT_ENROLLMENT`

[VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:16-20]

`OperationalIdentityLookup` preserva o escopo como `GrantedRole(String roleName, UUID scopeRef)`, mas a autoridade Spring reduz o grant ao nome do papel. Portanto nenhum endpoint esportivo pode autorizar somente por `ROLE_ADMINISTRADOR_COMPETICAO`, `hasRole(...)` sem escopo ou capability. [VERIFIED: backend/src/main/java/br/com/betufu/platform/identity/OperationalIdentityLookup.java:18-55; backend/src/main/java/br/com/betufu/platform/security/BetufuRoleAuthorityContributor.java:72-87; backend/src/main/java/br/com/betufu/identity/OperationalRoleQuery.java:40-67]

O schema aplicado termina em V8/V5.1: `local_identity`, `platform_guard`, `betufu_profile`, `audit_event`, `ufu_enrollment`, `password_reset_event_inbox` e `operational_role_grant`. DDL vem só de Flyway com `ddl-auto: validate`; migration aplicada não é editada, e novas migrations exigem atualizar o inventário/checksum testado. [VERIFIED: backend/src/main/resources/db/migration/V1__create_local_identity.sql:1-28; backend/src/main/resources/db/migration/V2__add_platform_guards.sql:1-27; backend/src/main/resources/db/migration/V3__create_betufu_profile.sql:1-31; backend/src/main/resources/db/migration/V4__create_audit_event.sql:1-128; backend/src/main/resources/db/migration/V5__create_ufu_enrollment.sql:1-42; backend/src/main/resources/db/migration/V5_1__create_password_reset_event_inbox.sql:1-54; backend/src/main/resources/db/migration/V6__create_operational_role_grant.sql:1-41; backend/src/main/resources/db/migration/V7__enforce_operational_role_scope.sql:1-16; backend/src/main/resources/db/migration/V8__add_password_reset_replay_audit_action.sql:1-18; backend/src/main/java/br/com/betufu/platform/persistence/FlywayPolicy.java:8-18,37-80; backend/src/test/java/br/com/betufu/platform/persistence/FlywayChecksumIT.java:66-95]

PostgreSQL está pinado como:

> `docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636`

[VERIFIED: infra/compose.yaml:1-4]

O perfil atual contém literalmente:

> `@Column(name = "favorite_team_id")`
> `private UUID favoriteTeamId;`

A coluna é anulável, não tem FK nem comando público; o PUT atual aceita somente nickname. [VERIFIED: backend/src/main/java/br/com/betufu/identity/internal/BetufuProfile.java:35-36,70-72; backend/src/main/resources/db/migration/V3__create_betufu_profile.sql:1-18; backend/src/main/java/br/com/betufu/identity/internal/ProfileUpdateRequest.java:6-15; backend/src/main/java/br/com/betufu/identity/internal/CurrentProfileResponse.java:6-10]

A rota Angular inteira está hoje sob `canActivateChild: [profileCompletionGuard]`, enquanto o layout autenticado só pinta o `<router-outlet>` após sessão válida. Logo `/` não pode ser implementada como mera exceção do guard: precisa de branch/shell público independente. [VERIFIED: frontend/src/app/app.routes.ts:1-11; frontend/src/app/core/layout/authenticated-layout.component.html:1-106; frontend/src/app/core/profile/profile-completion.guard.ts:5-40]

O gerador OpenAPI fixa motor Java `"7.24.0"`, inventário aprovado de 50 arquivos e digest literal `"BDF0CA8BDBC4118DA74FA0E5B0FFF47D4C0FF636DC9309751F550161F8EE4E71"`; qualquer expansão de contrato deve medir candidato e passar checkpoint humano antes de fixar lista/digest. [VERIFIED: frontend/scripts/generate-api.mjs:40-72,84-152,614-659,1508-1533; .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-VALIDATION.md:106-107]

## Standard Stack and Dependency Gates

### Núcleo já disponível

| Necessidade | Recurso existente | Diretriz para o planejamento |
|---|---|---|
| Domínio e persistência | Spring Boot 4.1, Spring Data JPA, PostgreSQL e Flyway | Manter `sports` como módulo de aplicação e criar migrations novas; nunca editar V1–V8. |
| Fronteiras | Spring Modulith + ArchUnit | Portas entre módulos precisam preservar o grafo acíclico e ser expostas por named interface somente quando outro módulo realmente consumir. |
| Segurança | Spring Security, sessão OIDC e `OperationalIdentityLookup` | GET público recebe matcher explícito; comando autenticado faz autorização escopada no serviço. |
| Concorrência | JPA `@Version`, constraints e transações | `lock_version` detecta edição concorrente; versão pública imutável preserva o fato publicado. |
| Privacidade da matrícula | normalizador e HMAC versionado da Fase 2 | Reutilizar o comportamento por uma porta, sem importar `verification.internal` nem duplicar chave/configuração. |
| Contrato web | OpenAPI + gerador Angular pinado | Alterar a fonte OpenAPI, regenerar pelo script oficial e parar no checkpoint de inventário/digest. |
| Frontend | Angular standalone, Signals, Reactive Forms e Vitest | Criar branch pública de rotas e manter APIs geradas atrás de adapters/stores. |
| Armazenamento | contrato S3 e SeaweedFS local | A infraestrutura existe, mas cliente, transformação e entrega de imagem ainda não existem. |

[VERIFIED: backend/pom.xml:12-27,44-127; backend/src/main/resources/application.yaml:31-63; backend/src/main/java/br/com/betufu/platform/identity/OperationalIdentityLookup.java:8-70; frontend/src/app/features/operations/operations-panel.store.ts:7-205; frontend/scripts/generate-api.mjs:84-152,592-659]

### Dependências que exigem checkpoint, não escolha implícita

O classpath atual não possui parser CSV dedicado, cliente S3 concreto nem biblioteca de decodificação/reencodificação de imagens além das APIs do JDK. A escolha foi auditada e aprovada pelo responsável em 2026-08-26; a aprovação fixa o desenho do plano, mas a edição futura do `pom.xml` continua sujeita ao checkpoint material de instalação exigido por `AGENTS.md`. [VERIFIED: backend/pom.xml:44-127; backend/src/main/java/br/com/betufu/platform/persistence/PrivateObjectStorageProbe.java:15-29; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-UI-SPEC.md:246-258,503-512]

Não há justificativa para adicionar XLSX, motor de classificação, CDN, SDK de mapas, editor rico, parser HTML ou biblioteca de bracket nesta fase. CSV, classificação transcrita, links simples e bracket semântico são suficientes para o MVP.

## Package Legitimacy Audit

Auditoria executada em 2026-08-26 contra o `backend/pom.xml`, cache Maven local, `maven-metadata.xml` e POMs publicados no Maven Central, além da documentação oficial dos projetos. Nenhum JAR foi instalado ou acrescentado ao cache durante a auditoria.

| Necessidade | Coordenada aprovada | Licença / manutenção | Superfície observada | Decisão |
|---|---|---|---|---|
| Parser CSV | `org.apache.commons:commons-csv:1.14.1` | Apache License 2.0; release 2025-07; projeto Apache ativo | JAR ~61 KB; produção depende de `commons-io:2.20.0` e `commons-codec:1.19.0`; Spring Boot 4.1 gerencia `commons-codec:1.21.0` | **INTEGRATE.** Parser robusto para quoting, newline e encoding; regras de negócio permanecem fora da biblioteca. |
| Alinhamento AWS | `software.amazon.awssdk:bom:2.54.5` em `dependencyManagement` | Apache License 2.0; versão release publicada em 2026-08-26 | BOM não entra no runtime; fixa todos os módulos AWS na mesma versão | **INTEGRATE.** Evita mistura silenciosa de versões. |
| Cliente de objetos | `software.amazon.awssdk:s3` + `software.amazon.awssdk:url-connection-client`, ambos governados pelo BOM `2.54.5` | Apache License 2.0; AWS SDK v2 ativamente mantido | módulo S3 ~4,37 MB antes dos transitivos; API modular; cliente HTTP explícito baseado em `HttpURLConnection` evita Netty/Apache HTTP | **INTEGRATE.** Usar `endpointOverride`, credenciais da configuração e `S3Configuration.pathStyleAccessEnabled(true)` para o SeaweedFS, mantendo compatibilidade S3 genérica. |
| Decodificação/reencodificação | APIs JDK `javax.imageio.ImageIO` e `BufferedImage` | Incluídas no Java 25 | zero dependência Maven | **INTEGRATE sem pacote.** Aceitar somente JPEG/PNG, limitar bytes e pixels antes/depois do decode e escrever uma imagem nova, sem metadados originais. |

### Alternativas auditadas e rejeitadas

| Alternativa | Evidência | Motivo da rejeição |
|---|---|---|
| `com.univocity:univocity-parsers:2.9.1` | Apache 2.0, JAR ~447 KB, sem dependência de produção, última release em 2021-01 | Boa biblioteca, mas manutenção menos recente que Commons CSV e superfície maior para uma necessidade simples. |
| `io.minio:minio:9.0.3` | Apache 2.0, release 2026-06, JAR ~568 KB e 10 dependências diretas | Pina Guava, OkHttp, Jackson 2, BouncyCastle, compressão e Snappy; aumenta superfície e carrega Jackson 2 ao lado do Jackson 3 gerenciado pelo Spring Boot 4. |
| parser CSV manual | nenhuma coordenada | `split(',')` não implementa CSV com aspas, newline, delimitadores e encoding; risco de escrita parcial e interpretação divergente. |
| biblioteca de imagem no primeiro corte | não necessária para JPEG/PNG reencodado | Criaria dependência antes de existir requisito para WebP/AVIF/GIF animado. Uma ampliação de formatos exige nova auditoria. |

### Contrato de adoção para os PLANs

- `pom.xml` só pode ser alterado numa task que leia esta auditoria, apresente o diff das coordenadas e tenha aprovação material específica.
- O plano deve executar `dependency:tree` após a instalação e bloquear versões snapshot, módulos HTTP adicionais inesperados ou divergência do BOM.
- CSV fica atrás de uma porta/parser próprio (`RosterImportParser` ou nome equivalente); nenhuma entidade/service conhece tipos do Commons CSV.
- S3 fica atrás de uma porta de storage pertencente ao consumidor; domínio e controller não expõem `S3Client`, bucket, object key ou URL direta.
- `ImageIO` não autoriza servir bytes originais. O pipeline cria um novo `BufferedImage`, reencoda e prova remoção de metadados com fixture adversarial.
- As coordenadas e versões acima são decisões aprovadas para a Fase 3; atualização posterior exige nova medição e aprovação.

[CITED: https://repo.maven.apache.org/maven2/org/apache/commons/commons-csv/1.14.1/commons-csv-1.14.1.pom; https://commons.apache.org/proper/commons-csv/; https://repo.maven.apache.org/maven2/software/amazon/awssdk/aws-sdk-java-pom/2.54.5/aws-sdk-java-pom-2.54.5.pom; https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html; https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/endpoint-config.html; https://repo.maven.apache.org/maven2/io/minio/minio/9.0.3/minio-9.0.3.pom; https://repo.maven.apache.org/maven2/com/univocity/univocity-parsers/2.9.1/univocity-parsers-2.9.1.pom]

## Architecture Patterns

### Pattern 1: módulo `sports` como autoridade esportiva

`sports` deve possuir catálogo, competição/edição, organização/equipe, atleta, elenco, evento, fonte, resultado, chaveamento, classificação e suas projeções públicas. Outros módulos não devem consultar repositories ou entidades internas. Se precisarem de um fato esportivo, consomem uma porta pública estreita. [VERIFIED: backend/src/main/java/br/com/betufu/sports/package-info.java:1-4; backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:34-65]

O grafo recomendado é unidirecional:

```text
identity.internal ──implements/consumes──► sports API estreita
verification.internal ──implements──► porta HMAC pertencente a sports
sports ──consumes──► platform.identity / platform.api / audit
frontend ──HTTP──► contratos públicos e operacionais
```

Há uma armadilha específica: se `sports` importa `platform`, `platform.security.SecurityConfiguration` não pode importar constantes Java de controllers de `sports`, pois isso fecharia `sports ↔ platform`. Os matchers podem usar um namespace literal validado por IT, ou um contrato de caminho pertencente ao próprio `platform`; o teste Modulith decide, não a intuição. O mesmo cuidado vale para capabilities: `platform` pode derivá-las dos papéis sem importar o módulo esportivo. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:206-262; backend/src/test/java/br/com/betufu/ModulithArchitectureTests.java:15-48]

### Pattern 2: portas pertencentes ao consumidor

O precedente da Wave 8 deve ser repetido para o HMAC: `sports` declara uma interface comportamental que recebe a matrícula transitória e devolve somente fingerprint/version ou uma conclusão segura; `verification.internal` implementa usando `MatriculaNormalizer`, `MatriculaHmac` e as chaves atuais. Assim não há import de `verification.internal`, segunda implementação criptográfica ou segunda origem de chave. [VERIFIED: backend/src/main/java/br/com/betufu/platform/identity/OperationalIdentityLookup.java:8-18; backend/src/main/java/br/com/betufu/identity/internal/OperationalIdentityLookupService.java:16-27; backend/src/main/java/br/com/betufu/verification/internal/MatriculaNormalizer.java:9-67; backend/src/main/java/br/com/betufu/verification/internal/MatriculaHmac.java:13-103]

O contrato não deve retornar matrícula normalizada nem aceitar `String` para transportar digest. Um value object/record opaco com versão e bytes/base64 canônico reduz troca acidental entre valor bruto e derivado. Mensagens e exceções permanecem sem eco do input.

### Pattern 3: identidade estável, rascunho concorrente e publicação imutável

Cada agregado publicável precisa distinguir:

1. identidade estável — UUID que URLs e referências usam;
2. estado de trabalho — editável, com `lock_version` para conflito otimista;
3. versão publicada — número/UUID imutável, ator e instante;
4. correção — nova versão, com fonte/motivo e ligação à anterior.

Essa separação vale principalmente para elenco, evento, resultado, chaveamento e classificação. `@Version` sozinho apenas impede overwrite; ele não preserva o conteúdo que a Fase 6 liquidará. A liquidação futura deve receber o ID da versão confirmada, não “o resultado atual”. [DERIVED: D-60, D-66–D-68, D-72]

### Pattern 4: autorização em duas guardas

A cadeia deve aplicar duas verificações independentes:

- **guarda de entrada:** usuário autenticado possui papel operacional compatível;
- **guarda de recurso:** o `scopeRef` vigente é exatamente o UUID da competição atingida.

`ROLE_ADMINISTRADOR_COMPETICAO` sozinho perde o UUID e não basta. A comparação escopada já existe em `OperationalRoleQuery.hasRole(identityId, role, scopeRef)`. O ID da competição vem do recurso carregado no banco, nunca de uma capability enviada pelo browser. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/BetufuRoleAuthorityContributor.java:65-87; backend/src/main/java/br/com/betufu/identity/OperationalRoleQuery.java:55-67]

Além disso, a concessão de `ADMINISTRADOR_COMPETICAO` precisa rejeitar UUID inexistente. Hoje V7 garante somente “papel escopado tem UUID”; não prova que o UUID representa competição. A validação deve usar uma porta estreita de existência pertencente ao consumidor adequado e teste de corrida/exclusão. [VERIFIED: backend/src/main/resources/db/migration/V7__enforce_operational_role_scope.sql:11-16; backend/src/main/java/br/com/betufu/identity/internal/RoleGrantService.java:315-349]

### Pattern 5: namespace público separado do shell autenticado

O tracer deve criar uma branch Angular pública real para `/`, agenda e páginas públicas. Ela não herda `profileCompletionGuard`, não depende de sessão e não renderiza o layout autenticado. No backend, somente GETs públicos e explicitamente enumerados ficam `permitAll`; preview, rascunho e comandos continuam autenticados. [VERIFIED: frontend/src/app/app.routes.ts:1-12; frontend/src/app/app.routes.production.ts:4-53; backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:206-262]

As mesmas entidades não devem ser serializadas diretamente nos dois lados. DTO público usa allowlist de campos publicados; DTO operacional inclui versão/estado apenas quando autorizado. Isso impede que “esconder no Angular” vire controle de segurança.

## Physical Data Model Recommendations

O planejador deve agrupar migrations por fatia entregue, não criar todo o esquema futuro em uma migration gigante. O esboço abaixo orienta FKs e limites; nomes finais são discrição do plano.

| Cluster | Identidades e relações mínimas | Invariantes essenciais |
|---|---|---|
| Catálogo | esporte, competição, edição, categoria, fase | slugs/nomes únicos no pai; edição pertence a competição; soft state não substitui histórico publicado. |
| Local | local e espaço interno | espaço pertence ao local; evento aceita ambos nulos (`LOCAL_A_DEFINIR`) ou par coerente. |
| Organização | organização permanente, equipe da edição | equipe referencia organização, edição, esporte/categoria compatíveis. |
| Atleta | atleta global, fingerprint versionado, vínculo de conta/proposta | matrícula bruta ausente; uma conta/atleta aceito por vez; estados históricos; ambiguidade não escolhe candidato. |
| Elenco | elenco da equipe, membro e revisão publicada | atleta não duplica no mesmo período/equipe; alteração publicada exige motivo; sem titular/reserva. |
| Evento | evento, participantes/slots, fonte e revisões | instante UTC + zona IANA; formato `HEAD_TO_HEAD` governa participantes; concorrência por versão. |
| Resultado | versão de resultado, confirmação/correção e fonte | versão confirmada é imutável; atores de registro, confirmação e autorização futura separados. |
| Derivados | dependências do bracket, snapshot/linhas da classificação | bracket referencia eventos; não copia placar/participante resolvido; classificação manual tem fonte e versão. |

O favorito atual exige migration preservadora: criar/referenciar organização, converter apenas valores cuja semântica possa ser provada e manter `NULL` quando não houver correspondência segura. Renomear a coluna sem reconciliar dados ou continuar expondo `favoriteTeamId` como organização são ambos incorretos. Antes da migration, medir o estado real e definir rollback/forward-fix. [VERIFIED: backend/src/main/java/br/com/betufu/identity/internal/BetufuProfile.java:32-36; backend/src/main/resources/db/migration/V3__create_betufu_profile.sql:1-18]

Novas ações de auditoria podem exigir ampliar o vocabulário fechado e o `CHECK` do banco por migration aditiva, seguindo V8. Não criar uma segunda tabela de “log esportivo” fora da trilha governada sem provar que o modelo atual é insuficiente. [VERIFIED: backend/src/main/java/br/com/betufu/audit/AuditEntry.java:7-55; backend/src/main/resources/db/migration/V8__add_password_reset_replay_audit_action.sql:1-18]

## Athlete Linking and Privacy Seam

### Fluxo recomendado

```text
admin envia matrícula em comando autorizado
  → porta normaliza + calcula HMAC versionado
  → sports persiste somente fingerprint/version
  → reconciliação procura correspondência única
  → cria proposta PENDENTE, nunca vínculo silencioso
  → pessoa autenticada ACEITA, REJEITA ou CONTESTA
  → aceite libera somente os campos pessoais self-service
```

A reconciliação precisa funcionar nos dois sentidos: atleta cadastrado depois da conta e conta verificada depois do atleta. O sinal pós-commit não transporta digest ou matrícula; ele transporta apenas um ID opaco que leva o consumidor autorizado a consultar sua própria base. O processo é idempotente para tolerar retry. [DERIVED: SPRT-04–08; D-69–D-71]

Estados mínimos recomendados para a associação são `PENDING`, `ACCEPTED`, `REJECTED`, `CONTESTED`, `AMBIGUOUS` e `SUPERSEDED`, com histórico de ator/instante. “Não encontrado” e “há mais de um candidato” não podem enumerar atletas de outra competição para administradores escopados.

`PrivacyArchitectureTest` hoje rejeita vocabulário/tipos de matrícula fora de `verification`. A fase precisa permitir somente o campo transitório no DTO de comando estritamente autorizado, mantendo as proibições em entity, response, URL/query, log e mensagem. Uma liberação ampla por pacote destruiria a prova de PRIV-01. [VERIFIED: backend/src/test/java/br/com/betufu/PrivacyArchitectureTest.java:63-143,151-292]

## CSV Import Architecture

Separar quatro responsabilidades:

1. `RosterImportParser` transforma bytes UTF-8 do modelo BETUFU em linhas lógicas, sem banco;
2. validador resolve referências e acumula todos os erros seguros;
3. preview guarda somente um token/estado governado ou exige revalidação determinística;
4. importador executa uma única transação e cria apenas rascunhos.

Uma linha inválida deve produzir zero inserts, inclusive de atleta criado antes de o erro aparecer. Extensão `.csv`, MIME do browser ou nome do arquivo não provam encoding/conteúdo. O parser precisa testar BOM, aspas, separadores, colunas ausentes/extras, linhas gigantes, duplicatas e datas/períodos. Erros exibem número da linha e campo permitido, nunca matrícula ou HMAC. [DERIVED: D-74–D-75; 03-UI-SPEC.md:303-314,392]

O modelo de linha comum deve sobreviver à futura troca de parser por XLSX. Portanto regras de negócio, HMAC, transação e auditoria não podem morar na biblioteca CSV.

## Media Architecture

Foto é uma capacidade separada de upload genérico. O pipeline recomendado é:

```text
multipart autenticado do próprio atleta
  → limite de bytes antes da decodificação
  → decodificação real e limite de pixels
  → reencodificação para formato aprovado, sem metadados
  → chave opaca em bucket privado
  → troca atômica da referência
  → entrega por handler BETUFU com tipo/cache controlados
```

Falha em qualquer etapa mantém a foto anterior. A remoção retira a referência pública primeiro e agenda limpeza segura; não expõe nome original, chave do objeto ou URL direta do bucket. URLs sociais são campos textuais validados por esquema/host seguro, sem buscar conteúdo remoto. [DERIVED: D-76–D-78; 03-UI-SPEC.md:246-258,431-438]

O cliente S3 e a estratégia de imagem são um checkpoint material. Até a aprovação, o plano pode entregar avatar padrão e perfil textual sem fingir que a mídia está pronta. Não usar `ImageIO.read()` isoladamente como prova de segurança: limites de dimensão, reencodificação e testes de arquivo truncado/poliglota continuam necessários.

## Public API and Angular Delivery

O contrato deve separar famílias previsíveis:

- `GET /api/v1/public/sports/...` — somente projeções publicadas, anônimo;
- `/api/v1/operations/sports/...` — comandos e previews escopados;
- `/api/v1/me/sports/...` — propostas de vínculo, favorito e perfil próprio.

Os nomes finais podem variar, mas misturar público e rascunho sob o mesmo DTO/endpoint aumenta o risco de cache e autorização. Paginação e ordenação precisam ser determinísticas; filtros da agenda devem ser representáveis na URL para navegação, compartilhamento e back/forward. [DERIVED: AUTH-01, SPRT-12–13; 03-UI-SPEC.md:129-146,158-205]

No Angular, cada família segue o padrão existente `generated service → adapter → signal store → componente`. A agenda pública não reutiliza estado privado do painel; uma resposta `401/403` em superfícies privadas limpa dados sensíveis. Tabelas de classificação e erro CSV viram cartões abaixo de 600px, e o bracket permanece compreensível sem conectores visuais. [VERIFIED: frontend/src/app/features/operations/operations-api.adapter.ts:1-110; frontend/src/app/features/operations/operations-panel.store.ts:7-205; 03-UI-SPEC.md:56-63,423-425,463]

O gerador oficial é obrigatório. A sequência correta é alterar OpenAPI, gerar em área temporária, medir inventário/digest, apresentar checkpoint humano e somente então fixar o novo digest. Nenhum arquivo em `frontend/src/app/api/generated/**` é editado manualmente.

## Tracer-First Decomposition

O primeiro plano de código deve provar uma fatia vertical pequena, mas real:

1. migration mínima para competição/edição/evento e publicação;
2. administrador geral cria a competição e concede escopo real;
3. administrador daquela competição cria rascunho e publica evento;
4. GET público retorna somente a versão publicada;
5. Angular anônimo abre `/` e vê o evento;
6. mutações provam que ordem do matcher, escopo errado e ausência de publicação deixam o teste vermelho.

Depois do tracer, a decomposição recomendada é aproximadamente:

1. fundação física e tracer público/escopado;
2. catálogo completo e locais;
3. organizações/equipes e reconciliação do favorito;
4. atleta global + seam HMAC;
5. propostas/aceite/contestação;
6. elenco e publicação;
7. importação CSV atômica;
8. ciclo de evento, fontes e resultado versionado;
9. agenda/páginas públicas completas;
10. chaveamento derivado;
11. classificação transcrita;
12. perfil esportivo textual/social;
13. mídia do atleta após checkpoint de dependência;
14. painel operacional e capacidades completas;
15. consolidação E2E, privacidade e documentação operacional.

Isto é orientação para o planejador, não uma allowlist pronta. Planos podem combinar fatias quando continuam verificáveis e com arquivos disjuntos; não devem comprimir 17 requisitos e 20 superfícies em poucas mudanças horizontais gigantes. [VERIFIED: .planning/ROADMAP.md:237-253; 03-UI-SPEC.md:516-531]

## Don't Hand-Roll

- Não reimplementar HMAC, normalização ou rotação de chaves em `sports`.
- Não criar parser CSV por `split(',')`; quoting, newline e encoding tornam isso incorreto.
- Não confiar em MIME/extensão para imagem nem servir arquivo original.
- Não criar motor genérico de torneio, pontuação ou desempate no MVP.
- Não calcular autoridade escopada no browser.
- Não duplicar participante/placar dentro do bracket.
- Não editar migrations V1–V8 nem arquivos do cliente OpenAPI gerado.
- Não usar lista em memória ou JSON sem constraints para publicação, vínculo ou concorrência.

## Common Pitfalls

### Pitfall 1: papel correto, competição errada

Um usuário pode possuir `ADMINISTRADOR_COMPETICAO` em mais de um `scopeRef`. Testar apenas o papel faz o cenário feliz passar e permite edição cruzada. Cada comando precisa de caso negativo com outro UUID real.

### Pitfall 2: publicação sobrescreve história

Atualizar a mesma linha de resultado elimina a versão que odds/liquidação precisam referenciar. Correção é inserção de nova versão e supersessão, não `UPDATE` destrutivo.

### Pitfall 3: matrícula transitória aparece no lugar errado

Binding error, `toString()`, Problem Detail, preview CSV e observabilidade podem ecoar o input mesmo quando o banco está limpo. Provas precisam inspecionar resposta, logs/DOM/storage e schema.

### Pitfall 4: atomicidade CSV testada só pelo retorno HTTP

Um `400` não prova zero salvamentos. O IT deve contar todas as tabelas afetadas após uma linha inválida e repetir com falha tardia.

### Pitfall 5: rota `/` “pública” ainda depende da sessão

Abrir exceção no guard sem separar o shell deixa inicialização OIDC, perfil ou capability bloquear a agenda. A prova deve usar navegador/contexto sem cookie e backend sem autenticação.

### Pitfall 6: `@Version` tratado como histórico

Optimistic locking detecta disputa, mas não preserva o conteúdo publicado. São mecanismos diferentes e precisam de testes diferentes.

### Pitfall 7: migração do favorito por nome

Converter `favoriteTeamId` para organização sem relação inequívoca pode associar usuário à organização errada. A migration deve partir de FK/mapeamento provado ou manter nulo e registrar reconciliação.

### Pitfall 8: foto segura só no caminho feliz

Arquivo pequeno pode descomprimir para dimensão enorme; JPEG/PNG podem carregar metadados; falha depois do upload pode deixar objeto órfão. Limites e rollback precisam de fixtures adversariais.

### Pitfall 9: cliente gerado alterado manualmente

Isso produz contrato local não reproduzível e digest enganoso. O checkpoint mede a saída do gerador oficial.

### Pitfall 10: teste verde sem mutação discriminante

Matcher amplo antes do específico, ausência de filtro `published` e guarda de escopo inerte são regressões capazes de continuar verdes se o teste só exercitar o cenário autorizado. Cada uma exige mutação negativa explícita.

## One-Way and Costly Decision Checkpoints

| Decisão | Classificação | Gate antes de implementar |
|---|---|---|
| Primeiro schema esportivo e IDs públicos | one-way | ER/DDL, constraints, rollback/forward-fix e prova de Modulith aprovados antes da migration. |
| Reconciliação `favoriteTeamId` → organização | costly | inventário de dados, semântica de conversão e contrato público aprovados. |
| Contrato público OpenAPI | costly | paths/DTOs e ausência de PII revisados; depois checkpoint de inventário/digest gerado. |
| Biblioteca CSV | costly | coordenada/licença/árvore/limites e fixtures aprovados. |
| Cliente S3 + pipeline de imagem | costly | dependência, formatos/limites, modelo de URL/cache e rollback aprovados. |
| Versão confirmada consumida pela Fase 6 | one-way | identidade da versão e atores separados aprovados antes de publicar o contrato. |
| Expansão de ações de auditoria | costly | vocabulário e migration do `CHECK` aprovados; sem editar migration aplicada. |

## Validation Architecture

### Test Framework

| Camada | Ferramenta/padrão | Responsabilidade |
|---|---|---|
| Domínio puro | JUnit 5 + AssertJ | estados, períodos, parser lógico, bracket e classificação sem Spring. |
| Persistência/API | Spring Boot Test + MockMvc + PostgreSQL Testcontainers/Failsafe | migrations, constraints, autorização, transação, concorrência e privacidade HTTP. |
| Arquitetura | Spring Modulith + ArchUnit | grafo acíclico, acesso a `internal`, seam de HMAC e ausência de matrícula fora do limite permitido. |
| Angular | Vitest via `ng test` | stores, adapters, estados da UI, acessibilidade e limpeza de estado privado. |
| Contrato | validadores Node + gerador OpenAPI | paridade backend/yaml, inventário/digest e fronteira de rotas. |
| Navegador | Playwright com config própria da Fase 3 | anônimo, teclado, responsividade, concorrência de duas sessões e inspeção DOM/storage/network. |

[VERIFIED: backend/pom.xml:91-126,164-168; backend/src/test/java/br/com/betufu/verification/EnrollmentControllerIT.java:71-236; backend/src/test/java/br/com/betufu/verification/UfuEnrollmentConcurrencyIT.java:174-281; frontend/package.json:4-13; frontend/tsconfig.spec.json:3-10]

As suítes materiais permanecem sob responsabilidade do orquestrador:

```powershell
node scripts/maven.mjs -f backend/pom.xml verify
node --test scripts/*.test.mjs
npm --prefix frontend run test -- --watch=false
```

Playwright atual possui configs limitadas a specs específicas; um plano deve criar/ampliar configuração executável antes de prometer E2E esportivo. [VERIFIED: frontend/playwright.config.ts:8-29; frontend/playwright.operations.config.ts:8-36]

### Phase Requirements → Test Map

| Requisito | Prova mínima automatizada |
|---|---|
| AUTH-01 | MockMvc anônimo + Playwright sem cookie abrem agenda/páginas publicadas; rascunho e endpoint operacional permanecem inacessíveis. |
| SPRT-01 | IT de catálogo/local com administrador geral e competição escopada; outro escopo falha; migration/constraints verdes. |
| SPRT-02 | cadastro individual e CSV válido; referências inexistentes e escopo errado falham; CSV inválido deixa zero linhas. |
| SPRT-03 | atleta sem `identityId` persiste e aparece somente com campos públicos permitidos. |
| SPRT-04 | teste de seam HMAC prova normalização igual à Fase 2 e ausência do valor bruto em entity/response/log. |
| SPRT-05 | schema armazena somente versão+fingerprint; ArchUnit/privacidade rejeita matrícula consultável. |
| SPRT-06 | reconciliação idempotente nos dois sentidos cria uma única proposta auditada. |
| SPRT-07 | múltiplos candidatos, fingerprint já aceito e divergência não ligam conta e abrem revisão sem enumeração. |
| SPRT-08 | somente a pessoa alvo aceita/rejeita/contesta; aceite concorrente tem um vencedor; campos pessoais bloqueados antes. |
| SPRT-09 | elenco rascunho/publicação e alteração com motivo; sem titular/reserva; snapshot publicado preservado. |
| SPRT-10 | evento valida participantes, UTC+zona, fase, fonte e coerência local/espaço. |
| SPRT-11 | matriz de estados, correção aditiva, fonte/motivo, conflito `409` e atores separados. |
| SPRT-12 | filtros paginados/ordenados + Playwright 320/1280px, teclado e deep link. |
| SPRT-13 | DTOs públicos de organização/equipe/atleta e agregações derivadas sem PII; vazios/parciais testados. |
| SPRT-14 | bracket deriva de eventos, `bye`/vencedor anterior; classificação manual versionada; modalidade não compatível fica só básica. |
| SPRT-15 | migration preservadora e perfil favorito por organização; ID inexistente falha; perfil anterior não é perdido. |
| SPRT-16 | owner-only para texto/social/foto; URL segura, imagem adversarial, falha preserva foto anterior e DTO público é allowlist. |

### Mutations and Negative Controls

As seguintes mutações devem ficar vermelhas antes de aceitar os planos correspondentes:

- mover matcher público depois de `/api/v1/**.authenticated()`;
- remover comparação de `scopeRef` ou substituí-la apenas por `hasRole`;
- remover filtro de versão publicada da query anônima;
- transformar correção de resultado em overwrite;
- salvar primeira linha do CSV antes de validar a última;
- ligar automaticamente proposta HMAC sem aceite;
- retornar entity de atleta em DTO público;
- servir bytes originais da foto;
- copiar participante/placar para o bracket em vez de derivar.

### Sampling Rate

- Cada task: teste focal rápido e discriminante.
- Cada plano: focais completos, arquitetura afetada, allowlist reconciliada e testes Node relacionados.
- Cada onda: backend `verify`, Node completo, Angular completo quando houver frontend e Playwright da superfície entregue.
- Snapshot final: contagens de relatórios, HEAD/status/hashes e revisão Codex somente leitura; qualquer correção gera novo snapshot.

### Wave 0 / lacunas que o planejamento deve fechar

1. Config Playwright capaz de executar specs da Fase 3.
2. Fixtures determinísticas CSV: válido, erro tardio, BOM/encoding, quoting, duplicata, referência ausente e limite.
3. Fixtures de mídia: válida, MIME falso, truncada, dimensão excessiva, metadados e falha de storage.
4. Porta comportamental para HMAC e prova de compatibilidade com a Fase 2.
5. Teste de existência de competição na concessão escopada.
6. Atualização estrita de `PrivacyArchitectureTest` para a entrada transitória.
7. Inventário Flyway atualizado para cada migration nova.
8. Gate do gerador OpenAPI e digest antes de publicar cliente.

## Security Domain

### Threats and required controls

| Ameaça | Controle exigido no plano |
|---|---|
| IDOR entre competições | carregar recurso, obter competição real e comparar `scopeRef`; negativos com dois escopos. |
| Rascunho/PII em endpoint público | query/projeção pública própria, allowlist de DTO e teste anônimo de ausência. |
| Fórmula CSV/arquivo hostil | tratar células como dados, limites estritos, parser robusto e nenhuma reexportação executável. |
| Matrícula em log/erro | DTO transitório estreito, redaction/proibição arquitetural e inspeção de resposta/log. |
| Corrida de publicação | optimistic lock + constraint + resposta `409`, sem last-write-wins. |
| Upload malicioso/DoS | limite pré-decode, dimensão, reencodificação, bucket privado e chave opaca. |
| SSRF/hotlink por rede social/foto | não buscar URL remota; links validados e imagem somente local processada. |
| XSS em biografia/fonte | texto puro ou sanitização allowlist, Angular binding normal e CSP existente. |
| Evento/resultado corrigido sem trilha | versão imutável, fonte/motivo e auditoria com ator/instante. |
| Favorito para objeto inexistente | FK/validação de organização publicada e migration preservadora. |

Administradores gerais continuam sujeitos à política de apostas já existente; administradores de competição são bloqueados no próprio escopo. O vínculo de atleta não adiciona bloqueio no MVP, conforme D-79, mas o modelo atleta–equipe–evento precisa permitir a política futura sem remontar histórico. Isso é decisão consciente de produto, não lacuna acidental.

## Likely Files and Plan Boundaries

Famílias prováveis, não allowlist definitiva:

- `backend/src/main/java/br/com/betufu/sports/**` e `backend/src/test/java/br/com/betufu/sports/**`;
- migrations novas e testes Flyway;
- porta/adapter estreitos em `verification` para HMAC e reconciliação;
- validação de escopo em `identity` sem importar `sports.internal`;
- `platform/security/SecurityConfiguration.java`, capabilities e contratos públicos;
- `audit`/migration de vocabulário quando ações esportivas entrarem;
- perfil/favorito em `identity` com migration preservadora;
- OpenAPI, cliente gerado pelo script oficial e adapters/stores/components Angular;
- scripts/validadores e configuração Playwright específica;
- storage/media e `backend/pom.xml` somente após checkpoint de dependência.

Arquivos compartilhados (`SecurityConfiguration`, OpenAPI, Flyway tests, capabilities, perfil) devem orientar ondas sequenciais ou propriedade única. Planos da mesma onda precisam de allowlists disjuntas; não “resolver” overlap permitindo dois executores no mesmo checkout.

## Assumptions Log

| Suposição | Confiança | Consequência se falsa |
|---|---|---|
| PostgreSQL continua autoridade de dados esportivos | alta | exigiria nova decisão de storage, fora do escopo atual. |
| IDs públicos são UUID opacos | alta | trocar por slug como identidade exigiria política de colisão/migration. Slug pode ser rota amigável secundária. |
| Keycloak/identity não armazenará atleta esportivo | alta | misturaria ciclo de vida de conta com catálogo e quebraria SPRT-03. |
| SeaweedFS/S3 será suficiente para foto do MVP | média | pipeline deve ficar atrás de porta para trocar implementação. |
| CSV cabe em processamento síncrono com limites pequenos | média | se limites aprovados forem altos, será necessário job/idempotência e outra superfície operacional. |
| Estatísticas genéricas derivam de resultados confirmados | média | modalidades sem semântica vitória/derrota precisam omitir métrica, não inventar zero. |

## Sources and Provenance

### Primárias — repositório e decisões aprovadas

- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-CONTEXT.md` — D-53–D-80 e limites do produto.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-UI-SPEC.md` — 20 superfícies, estados, acessibilidade, privacidade e gates.
- `.planning/REQUIREMENTS.md` e `.planning/ROADMAP.md` — AUTH-01, SPRT-01–16 e objetivo da fase.
- `backend/src/main/java/br/com/betufu/{sports,identity,verification,platform,audit}/**` — fronteiras e padrões atuais.
- `backend/src/test/java/br/com/betufu/**` — Modulith, ArchUnit, privacidade, concorrência e Testcontainers.
- `frontend/src/app/**`, `frontend/scripts/generate-api.mjs` e configs Playwright — padrões Angular e cadeia OpenAPI.
- `backend/src/main/resources/db/migration/**` e `infra/compose.yaml` — schema e autoridades de infraestrutura.

Esta continuação usou somente evidência local. Não transforma documentação externa ou comportamento presumido de biblioteca em fato do projeto. Escolhas de dependência e detalhes físicos permanecem checkpoints.

## Metadata

**Confidence breakdown:**

- decisões e requisitos: HIGH — copiados dos artefatos aprovados;
- baseline de código, testes e infraestrutura: HIGH — inspecionado no checkout atual;
- arquitetura recomendada: HIGH para fronteiras/risco de ciclo; MEDIUM para o desenho físico final;
- CSV e mídia: MEDIUM — limites e dependências ainda dependem de aprovação;
- decomposição em planos: MEDIUM — o planner ajustará allowlists, overlaps e ondas.

**Research date:** 2026-08-26
**Valid until:** antes de qualquer alteração relevante em `sports`, esquema Flyway, segurança, OpenAPI, perfil/favorito ou storage.

## RESEARCH COMPLETE
