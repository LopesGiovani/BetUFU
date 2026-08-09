# Fase 1: Fundação Segura da Plataforma — Pesquisa

**Pesquisado em:** 2026-08-01
**Atualizado em:** 2026-08-09 — decisões de infraestrutura local refinadas antes do Plano 01-03
**Domínio:** fundação de monólito modular Java/Spring, BFF OIDC, contratos OpenAPI e ambiente local reproduzível
**Confiança:** MÉDIA — a compatibilidade principal foi verificada em documentação oficial; duas bordas de versão exigem gate de compilação.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Entrega visível da fase

- **D-01:** O resultado visível será a estrutura inicial da interface BETUFU em Angular, acompanhada de uma tela de diagnóstico protegida. Nos documentos e na comunicação com o usuário, usar “estrutura inicial da interface BETUFU” em vez do jargão “Shell Angular”.
- **D-02:** A tela de diagnóstico poderá ser aberta somente por uma conta técnica autenticada nos ambientes de desenvolvimento e homologação. A rota não deverá existir no build de produção.
- **D-03:** O diagnóstico exibirá, de forma resumida e segura, versão/build, ambiente e estado da API, sessão BFF, Keycloak, PostgreSQL, Redis e armazenamento. Cada componente será classificado como disponível, degradado ou indisponível, e a tela mostrará um correlation ID copiável.
- **D-04:** Quando uma dependência falhar, o diagnóstico continuará aberto em modo degradado, identificará o componente afetado e oferecerá nova tentativa. Não exibirá segredos, endereços internos ou stack traces.

#### Comportamento da sessão

- **D-05:** Toda sessão expirará após 8 horas sem atividade. A mesma regra valerá para usuários comuns, administradores e contas técnicas, e cada atividade válida renovará o prazo de inatividade.
- **D-06:** Não haverá duração máxima absoluta no MVP. O contrato de sessão deverá representar explicitamente essa ausência, sem inventar outro limite durante o planejamento.
- **D-07:** O MFA feito no login administrativo permanecerá válido durante a sessão ativa; não haverá nova confirmação de MFA por operação sensível no MVP.
- **D-08:** Um usuário poderá manter sessões simultâneas e independentes em vários dispositivos. A inatividade e a expiração serão calculadas separadamente para cada sessão.
- **D-09:** A ação voluntária “Sair” encerrará somente a sessão do dispositivo atual. O MVP não terá uma opção para o usuário encerrar todas as suas sessões.

#### Ambiente local

- **D-10:** O ambiente local terá dois modos suportados. O modo diário padrão será híbrido: PostgreSQL, Keycloak, Redis, SeaweedFS como implementação S3 exclusivamente local e o serviço de e-mail de desenvolvimento rodarão em contêineres, enquanto Spring Boot e Angular rodarão pelo terminal ou pela IDE. Um perfil completo de validação também executará Spring em contêiner e servirá o Angular compilado por Nginx, permitindo verificar a composição reproduzível usada fora do ciclo rápido de desenvolvimento.
- **D-11:** A primeira inicialização será automática e reproduzível. Ela preparará bancos separados, Keycloak com cliente confidencial e somente o papel BETUFU `TECHNICAL`, Redis, armazenamento S3 local, serviço de e-mail de desenvolvimento e uma conta técnica exclusivamente local. Nenhuma credencial real será versionada no Git; wrappers, runtimes e imagens terão versões aprovadas e fixadas antes da implementação.
- **D-12:** Parar e iniciar o ambiente preservará os dados locais. Haverá uma reinicialização separada e explícita que apagará somente os dados locais da BETUFU e recriará o ambiente, sempre após confirmação clara.
- **D-13:** Windows 11 com Docker Desktop/WSL2 e Linux serão as plataformas principais de desenvolvimento do MVP; Linux também atenderá à integração contínua. macOS será compatível quando possível, mas não constituirá requisito obrigatório. Os comandos principais deverão ser independentes do sistema operacional sempre que possível.

#### Refinamentos do Plano 01-03

- SeaweedFS é somente o serviço S3 gratuito local; o backend usa `BETUFU_S3_*` e a solução de produção permanece aberta.
- Repositórios, tags e digests das cinco imagens exigem Gate 1 humano antes de qualquer arquivo de infraestrutura ou download; SeaweedFS publica apenas 8333 e usa volume persistente.
- Papéis administrativos e de domínio permanecem na BETUFU/PostgreSQL; `TECHNICAL` é o único papel BETUFU do realm local.
- Compose sempre recebe `--env-file infra/.env.example`; `infra/.env` será ignorado somente durante a implementação.
- O bootstrap `init-databases.sh` roda dentro do contêiner PostgreSQL e o realm recebe validador/teste Node sem dependências.
- No PowerShell 5.1, verificações são comandos separados, fail-fast, propagam `$LASTEXITCODE` e não usam operador de encadeamento incompatível.

#### Erros da aplicação

- **D-14:** Erros apresentados ao usuário usarão linguagem simples em português, explicarão o que não funcionou, indicarão a próxima ação possível e fornecerão um código de referência copiável. Detalhes técnicos, endereços internos e stack traces não aparecerão na interface.
- **D-15:** Erros de formulário preservarão os valores digitados, marcarão cada campo inválido com uma explicação específica e levarão o foco ao primeiro erro. Uma mensagem geral será usada somente quando o problema não pertencer a um campo.
- **D-16:** Apenas consultas que carregam informações poderão ser repetidas automaticamente. Ações que alteram dados nunca serão repetidas silenciosamente; durante o envio, o controle de ação ficará bloqueado e, diante de uma falha, o usuário receberá uma opção segura de tentar novamente.
- **D-17:** Uma falha isolada afetará somente a área correspondente da página, mantendo o restante e a navegação funcionais. Uma tela de erro completa será usada apenas quando a BETUFU não puder iniciar ou continuar com segurança.

### the agent's Discretion

- Definir quais eventos técnicos contam como atividade válida para renovar o prazo ocioso, mantendo a regra aprovada de 8 horas.
- Definir regeneração de sessão e revogação administrativa ou de segurança exigidas por `IDEN-04`. Essas regras não foram decididas na discussão e não devem ser confundidas com o logout voluntário do dispositivo atual.
- Definir wrappers adicionais de inicialização e reinicialização local, preservando `--env-file infra/.env.example`, comandos separados e a limitação de qualquer remoção aos recursos locais da BETUFU.
- Definir códigos estáveis, estrutura completa de Problem Details, quantidade de novas tentativas automáticas e estratégia de espera, respeitando as regras de apresentação e repetição aprovadas.
- Definir detalhes visuais da estrutura inicial e dos estados de carregamento, vazio e sucesso que não contrariem as decisões registradas.

### Deferred Ideas (OUT OF SCOPE)

- Exigir nova confirmação de MFA antes de operações administrativas sensíveis — melhoria pós-MVP. No MVP, o MFA realizado no login permanece válido durante a sessão ativa.
</user_constraints>

## Phase Requirements

| ID | Descrição | Suporte da pesquisa |
|----|-----------|---------------------|
| ARCH-01 | O produto usa monorepo com `backend/`, `frontend/`, `infra/` e `docs/`, frontend Angular 22/TypeScript 6.0.x e backend Java 25/Spring Boot 4.1/Maven. | Matriz Angular, requisitos Boot e auditoria de ambiente. [VERIFIED: .planning/REQUIREMENTS.md:150-150] |
| ARCH-02 | O backend é um monólito modular Spring Modulith 2.1 com limites explícitos entre identidade, verificação, esportes, odds, apostas, ledger, liquidação, ranking, chat e auditoria. | Padrão package-by-feature, APIs nomeadas e `ApplicationModules.verify()`. [VERIFIED: .planning/REQUIREMENTS.md:151-151] |
| ARCH-03 | Testes automatizados impedem dependências não autorizadas entre módulos; microsserviços, Kafka e Kubernetes não integram o MVP. | Modulith Test e ArchUnit no gate Maven. [VERIFIED: .planning/REQUIREMENTS.md:152-152] |
| IDEN-01 | Keycloak administra autenticação, credenciais, recuperação, verificação de e-mail, sessões e MFA; a BETUFU administra perfil, papéis de domínio, vínculo UFU e elegibilidade. | Limite de responsabilidade Keycloak/BFF. [VERIFIED: .planning/REQUIREMENTS.md:153-153] |
| IDEN-02 | A aplicação correlaciona o par estável `(iss, sub)` do Keycloak ao usuário BETUFU e não usa e-mail como identificador imutável. | Modelo de identidade no BFF e contrato `/api/v1`. [VERIFIED: .planning/REQUIREMENTS.md:154-154] |
| IDEN-03 | Spring atua como BFF OIDC, mantém tokens e sessão no servidor e nunca exige que o frontend armazene access ou refresh token. | Authorization Code + BFF, cookie de sessão e endpoint CSRF. [VERIFIED: .planning/REQUIREMENTS.md:155-155] |
| IDEN-04 | Contrato de sessão BFF define TTL ocioso e absoluto, regeneração, logout e revogação, state, nonce, PKCE, política SameSite e CSRF inclusive no STOMP CONNECT. | Contrato de sessão explícito, rotação/regeneração e testes de segurança. [VERIFIED: .planning/REQUIREMENTS.md:156-156] |
| DATA-01 | PostgreSQL 18 é a fonte oficial de saldo, elegibilidade, odds, aposta, resultado, liquidação, ranking e chat. | Fonte autoritativa e limites de Redis. [VERIFIED: .planning/REQUIREMENTS.md:158-158] |
| DATA-02 | Toda alteração de schema ocorre por migration Flyway versionada e validada. | Convenção imutável de migrations e teste com PostgreSQL real. [VERIFIED: .planning/REQUIREMENTS.md:159-159] |
| DATA-06 | Redis permanece auxiliar; comprovantes usam armazenamento S3 privado; BETUFU e Keycloak usam bancos e usuários separados. | Isolamento de bancos/roles e armazenamento não público. [VERIFIED: .planning/REQUIREMENTS.md:163-163] |
| API-01 | O backend publica REST JSON versionado em `/api/v1`, contrato OpenAPI e erros compatíveis com Problem Details. | RFC 9457 / `ProblemDetail` e OpenAPI como fonte do cliente. [VERIFIED: .planning/REQUIREMENTS.md:169-169] |
| API-02 | O frontend consome cliente TypeScript gerado do OpenAPI e o pipeline detecta divergência de contrato. | Geração determinística e diff limpo obrigatório. [VERIFIED: .planning/REQUIREMENTS.md:170-170] |
| OPS-01 | Desenvolvimento oferece dois modos reproduzíveis. No modo diário padrão, Docker Compose executa PostgreSQL, Redis, Keycloak, SeaweedFS como implementação S3 exclusivamente local e serviço de e-mail, enquanto Spring Boot e Angular rodam pelo terminal ou pela IDE. No perfil completo de validação, Spring também roda em contêiner e o Angular compilado é servido por Nginx. Ambos usam wrappers, versões de runtime e imagens aprovados e fixados antes da implementação correspondente. | Compose profiles, health checks, volumes nomeados e lockfiles/wrappers. [VERIFIED: .planning/REQUIREMENTS.md:176-176] |
| OPS-02 | Desenvolvimento, homologação e produção possuem configurações separadas, e segredos nunca são versionados. | Arquivos de exemplo não secretos e injeção externa. [VERIFIED: .planning/REQUIREMENTS.md:178-178] |
| OPS-03 | Aplicação fornece health checks, logs estruturados, correlation ID e métricas sem dados sensíveis. | Actuator mínimo protegido + Micrometer/OTLP. [VERIFIED: .planning/REQUIREMENTS.md:179-179] |
| TEST-01 | Regras de domínio possuem testes JUnit e transações integradas usam PostgreSQL e dependências reais via Testcontainers. | JUnit + Testcontainers + migrations reais. [VERIFIED: .planning/REQUIREMENTS.md:181-181] |
| TEST-02 | Spring Modulith Test e ArchUnit verificam limites modulares e dependências permitidas. | Teste de estrutura e testes por módulo. [VERIFIED: .planning/REQUIREMENTS.md:182-182] |

## Summary

Planejar esta fase como uma fundação integrada, mas com um único fluxo vertical verificável: ambiente sobe, Keycloak inicia a conta técnica local, o navegador entra via Authorization Code no BFF, recebe somente a sessão por cookie e abre o diagnóstico protegido. O backend oferece health agregado e seguro, correlation ID e Problem Details; o frontend só consome o cliente gerado do contrato. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html]

Use PostgreSQL como único estado autoritativo desde a primeira migration. Redis, SeaweedFS local e Keycloak são dependências com estado próprio, mas não substituem a verdade de domínio; o adapter de objetos fala somente S3 e Keycloak recebe banco e usuário PostgreSQL próprios. Migrações versionadas não são editadas depois de aplicadas: correções sempre avançam o schema. [CITED: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations] [VERIFIED: .planning/PROJECT.md]

**Recomendação principal:** implemente primeiro o esqueleto monorepo e seu gate de ambiente/compilação; em seguida feche BFF + contrato + diagnóstico em uma jornada testada. Não prossiga para a implementação da base antes de elevar Java para 25, Node para uma versão aceita pelo Angular 22 e iniciar o daemon Docker. [CITED: https://angular.dev/reference/versions] [VERIFIED: ambiente local, 2026-08-01]

## Architectural Responsibility Map

| Capacidade | Camada primária | Camada secundária | Razão |
|------------|-----------------|-------------------|-------|
| Estrutura inicial da interface BETUFU e diagnóstico seguro | Browser / Client | API / Backend | A UI renderiza estados seguros; a API decide se há autorização e quais dados técnicos sanitizados podem sair. [ASSUMED] |
| OIDC, tokens e ciclo de sessão | Frontend Server (BFF) | Keycloak | O BFF é o cliente confidencial que guarda tokens; Keycloak autentica e mantém a sessão de identidade. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] |
| CSRF, origem e STOMP CONNECT | API / Backend | Browser / Client | Spring valida token/origem/autorização; o cliente envia o token de CSRF no CONNECT sem conhecer tokens OIDC. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html] |
| Dados de domínio e migrations | Database / Storage | API / Backend | PostgreSQL aplica transações, constraints e schema; a API é a única via de escrita. [VERIFIED: .planning/PROJECT.md:109-113] |
| Cliente gerado do contrato | Browser / Client | API / Backend | O contrato é publicado pelo backend; o artefato gerado é consumido pelo Angular e não é editado manualmente. [ASSUMED] |
| Health, logs, métricas e traces | API / Backend | Infraestrutura | Spring produz dados sanitizados; o operador/infra coleta e expõe somente o necessário. [CITED: https://docs.spring.io/spring-boot/reference/actuator/observability.html] |

## Standard Stack

### Core

| Biblioteca / runtime | Versão a fixar | Propósito | Por que usar |
|----------------------|----------------|-----------|--------------|
| Java + Maven Wrapper | Java 25 LTS; Maven Wrapper gerado pelo projeto | runtime e build backend | Spring Boot 4.1.0 aceita Java até 26 e Maven 3.6.3+, portanto Java 25 é compatível. [CITED: https://docs.spring.io/spring-boot/system-requirements.html] |
| Spring Boot | `4.1.0` | REST, Security, Actuator, JPA e configuração | Release estável atual; traz Micrometer/Actuator e suporte de tracing. [CITED: https://docs.spring.io/spring-boot/documentation.html] |
| Spring Modulith BOM | `2.1.0` | módulos, verificação e testes por módulo | O BOM 2.1.0 é oficialmente publicado e a API fornece suporte Actuator para módulos. [CITED: https://docs.spring.io/spring-modulith/reference/] |
| PostgreSQL | `18.x` com tag e digest imutáveis | fonte autoritativa | PostgreSQL 18 é release estável; Flyway certifica suporte a PostgreSQL 18. [CITED: https://www.postgresql.org/about/news/postgresql-18-released-3142/] [CITED: https://documentation.red-gate.com/fd/supported-database-versions-143754067.html?force_isolation=true] |
| Flyway | versão gerida pelo BOM Boot ou explicitamente fixada após teste | migrations | migrations versionadas são ordenadas e protegidas por checksum. [CITED: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations] |
| Angular / CLI / TypeScript | Angular `22.1.x`; TypeScript `>=6.0.0 <6.1.0`; Node `^22.22.3` | interface, build e testes de componente | Esta é a faixa oficialmente declarada para Angular 22. [CITED: https://angular.dev/reference/versions] |
| Keycloak | tag estável e digest explicitamente fixados no Compose | IdP OIDC, MFA, e-mail e sessões de identidade | É a fronteira aprovada para credenciais e autenticação; suporta discovery, Authorization Code e logout OIDC. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] |

### Supporting

| Biblioteca / ferramenta | Versão a fixar | Propósito | Quando usar |
|-------------------------|----------------|-----------|-------------|
| `spring-modulith-starter-test` | gerida pelo BOM 2.1.0 | `@ApplicationModuleTest` e testes de módulo | Em cada módulo que possuir comportamento integrado. [CITED: https://docs.spring.io/spring-modulith/reference/testing.html] |
| ArchUnit JUnit 5 | gerida pelo BOM Modulith quando aplicável; conferir árvore Maven | regra arquitetural complementar | Para proibir pacotes, dependências e convenções que o modelo Modulith não expressar. [CITED: https://docs.spring.io/spring-modulith/reference/verification.html] |
| Testcontainers + `spring-boot-testcontainers` | versões geridas pelo BOM Boot | PostgreSQL/Redis reais nos testes | Testes de transaction, migration e integração; `@ServiceConnection` evita URLs hard-coded. [CITED: https://docs.spring.io/spring-boot/reference/testing/testcontainers.html] |
| `springdoc-openapi` | linha `3.x`, fixada após resolução Maven | emissão do contrato OpenAPI | A matriz oficial declara `springdoc-openapi 3.x` compatível com Spring Boot `4.x.x`; ainda assim, o build deve fixar uma release estável concreta e executar o contrato. [CITED: https://springdoc.org/faq.html] |
| OpenAPI Generator `typescript-angular` | versão do gerador imutável, validada por compile do Angular 22 | cliente TypeScript gerado | O gerador é estável, mas a página oficial ainda declara suporte de Angular 9 a 21: é um risco de compatibilidade, não uma garantia para Angular 22. [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/] |
| Micrometer + OpenTelemetry/OTLP | versões geridas pelo BOM Boot | traces e correlação | Spring Boot recomenda API Micrometer e fornece auto-configuração OTLP. [CITED: https://docs.spring.io/spring-boot/reference/actuator/tracing.html] |
| Docker Compose | `5.1.3` disponível; imagens por digest | modos diário e completo | Profiles, health checks e volumes nomeados atendem os dois modos aprovados. [CITED: https://docs.docker.com/compose/how-tos/profiles/] |
| SeaweedFS | repositório, tag e digest a aprovar no Gate 1 do 01-03 | serviço S3 gratuito somente local | Implementa o contrato S3 no desenvolvimento sem escolher o armazenamento de produção; publica apenas 8333 e usa volume persistente. [APPROVED: decisão do responsável, 2026-08-09] |

### Alternatives Considered

| Em vez de | Poderia usar | Trade-off |
|-----------|--------------|-----------|
| BFF com sessão no servidor | SPA com tokens OIDC no browser | Contradiz `IDEN-03` e amplia a superfície de roubo de token; não usar. [VERIFIED: .planning/REQUIREMENTS.md:155-155] |
| Monólito modular | microsserviços/Kafka/Kubernetes | Está explicitamente fora do MVP; não pesquisar/planejar como alternativa nesta fase. [VERIFIED: .planning/REQUIREMENTS.md:152-152] |
| Cliente `typescript-angular` gerado | outro gerador TypeScript | Só avaliar se o gate de compile demonstrar incompatibilidade com Angular 22; a decisão exige confirmação técnica documentada, não troca silenciosa. [ASSUMED] |

**Instalação:** use Maven Wrapper e lockfile npm criados pelo scaffolding; fixe imagens em digest e dependências npm em lockfile. A execução só pode usar versões que tenham passado o gate humano abaixo, pois os pacotes npm observados foram classificados `SUS` exclusivamente por serem muito recentes. [VERIFIED: package-legitimacy, 2026-08-01]

## Package Legitimacy Audit

| Pacote | Registry | Idade observada | Downloads semanais | Repositório | Veredito | Disposição |
|---------|----------|-----------------|--------------------|-------------|----------|------------|
| `@angular/core` [WARNING: flagged as suspicious — verify before using.] | npm | 2 dias | 5,829,115 | `github.com/angular/angular` | SUS (`too-new`) | Gate humano antes de instalar; versão observada `22.1.0`. [CITED: https://angular.dev/reference/versions] |
| `@angular/cli` [WARNING: flagged as suspicious — verify before using.] | npm | 2 dias | 5,350,317 | `github.com/angular/angular-cli` | SUS (`too-new`) | Gate humano antes de instalar; versão observada `22.1.2`. [CITED: https://angular.dev/reference/versions] |
| `@angular/material` [WARNING: flagged as suspicious — verify before using.] | npm | 2 dias | 2,376,747 | `github.com/angular/components` | SUS (`too-new`) | Gate humano antes de instalar; versão observada `22.1.0`. [CITED: https://angular.dev/reference/versions] |
| `@angular/cdk` [WARNING: flagged as suspicious — verify before using.] | npm | 2 dias | 4,019,644 | `github.com/angular/components` | SUS (`too-new`) | Gate humano antes de instalar; versão observada `22.1.0`. [CITED: https://angular.dev/reference/versions] |
| `@openapitools/openapi-generator-cli` [WARNING: flagged as suspicious — verify before using.] | npm | 8 dias | 1,575,944 | `github.com/OpenAPITools/openapi-generator-cli` | SUS (`too-new`) | Não instalar sem `checkpoint:human-verify`; há `postinstall` `opencollective || exit 0`; versão observada `2.40.1`. [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/] |
| `@playwright/test` [WARNING: flagged as suspicious — verify before using.] | npm | 2 dias | 50,775,638 | `github.com/microsoft/playwright` | SUS (`too-new`) | Gate humano antes de instalar; versão observada `1.62.1`. [ASSUMED] |

**Pacotes removidos por veredito SLOP:** nenhum.
**Pacotes SUS:** todos os seis acima; o planejador deve incluir `checkpoint:human-verify` antes de cada instalação npm. Os nomes foram conferidos em documentação dos respectivos projetos, mas o gate não pode ser ignorado. [CITED: https://angular.dev/reference/versions] [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/]

## Architecture Patterns

### System Architecture Diagram

```text
Angular 22 browser
  │ GET /login, /api/v1/* + XSRF header; no OAuth tokens in storage
  ▼
Spring Boot BFF ── Authorization Code + state/nonce/PKCE ──► Keycloak
  │        │                                                        │
  │        └── server-side authorized client / session ◄────────────┘
  │
  ├── /api/v1 OpenAPI + Problem Details ──► generated TypeScript client
  ├── protected diagnostic projection ────► health aggregation / retry
  ├── metrics, structured logs, trace/correlation ID ──► operator-only endpoints
  ├── JPA + Flyway ───────────────────────► BETUFU PostgreSQL database/user
  ├── session/rate-limit/realtime support ─► Redis (auxiliary only)
  └── private object adapter (BETUFU_S3_*) ─► SeaweedFS local via S3 private bucket

Keycloak ─────────────────────────────────► separate PostgreSQL database/user
```

O diagrama expressa a divisão recomendada de responsabilidades e preserva a regra de que nenhum access/refresh token viaja para `localStorage` ou `sessionStorage`. [VERIFIED: .planning/PROJECT.md:101-113]

### Estrutura recomendada do projeto

```text
backend/                         # Maven + Spring Boot / módulos por capacidade
frontend/                        # Angular e cliente OpenAPI gerado
infra/                           # Compose, imports Keycloak e exemplos sem segredos
docs/                            # contratos e operação
```

Esta estrutura é a convenção obrigatória já aprovada; os nomes internos de módulos permanecem a definir no plano, sem criar serviços separados. [VERIFIED: .planning/REQUIREMENTS.md:150-152]

### Pattern 1: módulo por capacidade com API nomeada

**O quê:** trate cada pacote de primeiro nível do backend como módulo de domínio; publique somente tipos/serviços explicitamente autorizados e mantenha implementação em subpacotes internos. O teste estrutural deve chamar `ApplicationModules.of(Application.class).verify()` e declarar dependências permitidas quando elas existirem. [CITED: https://docs.spring.io/spring-modulith/reference/verification.html]

**Quando usar:** desde o commit inicial; o teste não é uma limpeza futura. Para testes integrados, use `@ApplicationModuleTest` no pacote do módulo e prefira mock de dependência externa a incluir módulos não necessários. [CITED: https://docs.spring.io/spring-modulith/reference/testing.html]

### Pattern 2: BFF OIDC de sessão, não SPA OAuth

**O quê:** Spring inicia Authorization Code, guarda tokens do cliente autorizado no lado servidor e emite apenas seu cookie de sessão `HttpOnly`, `Secure` e `SameSite` compatível. Em login/reautenticação, regenere o identificador de sessão; logout local encerra a sessão corrente e dispara logout OIDC padronizado quando aplicável. Para invalidação administrativa/de segurança, o contrato do 01-07 captura o refresh token server-side, revoga-o pelo endpoint descoberto com hint explícito, mantém a sessão local terminal e testa `invalid_grant` no refresh posterior. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] [CITED: https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/06-Session_Management_Testing/04-Testing_for_Exposed_Session_Variables]

**Quando usar:** toda rota autenticada e toda conexão STOMP. Não aceite Direct Grant/password grant; a documentação Keycloak registra que ele expõe credenciais à aplicação e não é recomendado pelas práticas atuais OAuth. [CITED: https://www.keycloak.org/securing-apps/oidc-layers]

### Pattern 3: projeção de diagnóstico, não espelho bruto do Actuator

**O quê:** mantenha Actuator e endpoints de gestão fora da superfície pública; exponha para a tela um DTO próprio, com estados `disponível`, `degradado`, `indisponível`, código/correlation ID e ação segura de retry. Não serialize exceções, URLs internas, propriedades, headers nem detalhes de health de dependência. Acesso é apenas técnico em dev/homologação e a rota é removida no build de produção. [VERIFIED: .planning/phases/01-funda-o-segura-da-plataforma/01-CONTEXT.md:21-24]

**Quando usar:** health agregado do diagnóstico; o operador pode consultar endpoints de gestão separados, autorizados e restritos. Spring Boot expõe apenas `/health` por HTTP por padrão e orienta proteger cuidadosamente o que for adicionalmente exposto. [CITED: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html]

### Anti-patterns to avoid

- **Token OIDC no Angular:** viola `IDEN-03`; não use SDK SPA, interceptor bearer nem storage de access/refresh token. [VERIFIED: .planning/REQUIREMENTS.md:155-155]
- **Desabilitar CSRF por causa da API JSON:** cookies tornam endpoints mutáveis suscetíveis a CSRF; mantenha token sincronizador e valide também o CONNECT STOMP. [CITED: https://docs.spring.io/spring-security/reference/features/exploits/csrf.html] [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html]
- **Confiar em `depends_on` sem healthcheck:** Compose só ordena start, não disponibilidade; use `service_healthy`. [CITED: https://docs.docker.com/compose/how-tos/startup-order/]
- **Editar migration aplicada:** altera checksum e quebra reprodução; crie sempre migration nova. [CITED: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations]
- **Expor `env`, `configprops`, stack traces ou health detalhado à UI:** endpoints Actuator podem carregar dados sensíveis; projete dados mínimos. [CITED: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html]

## Don't Hand-Roll

| Problema | Não construir | Usar | Motivo |
|----------|---------------|------|--------|
| Login, senha, recuperação, MFA e sessão de identidade | telas e fluxos de credenciais próprios | Keycloak + OIDC Authorization Code | O provider entrega os endpoints OIDC e fluxos de identidade; Direct Grant é desaconselhado. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] |
| Session ID, CSRF e segurança STOMP | tokens caseiros, CORS como controle de acesso | Spring Security + sessão servlet + CSRF | WebSocket ignora Same Origin por padrão e Spring exige CSRF no CONNECT. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html] |
| Controle de schema | `ddl-auto`, scripts manuais e migrations mutáveis | Flyway | Versionamento, ordem, checksum e validação já existem. [CITED: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations] |
| Isolamento de módulos | convenção documental sem teste | Spring Modulith Test + ArchUnit | `verify()` rejeita ciclos, acesso a internos e dependências não permitidas. [CITED: https://docs.spring.io/spring-modulith/reference/verification.html] |
| Correlação/telemetria | IDs e exporters ad hoc | Micrometer Observation/Tracing + Actuator | Boot integra logs, métricas e traces; o trace fornece correlação nos logs. [CITED: https://docs.spring.io/spring-boot/reference/actuator/tracing.html] |
| Cliente da API | serviços Angular escritos à mão | geração a partir do OpenAPI | Mantém assinatura, modelos e Problem Details alinhados ao contrato; validar compatibilidade Angular 22 antes do lock. [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/] |

## Common Pitfalls

### Pitfall 1: versões aprovadas, mas ambiente de execução incompatível

**O que falha:** o scaffold ou o build Angular falha/tem comportamento fora da matriz.
**Por quê:** o host possui Node `v22.20.0`, abaixo de `^22.22.3` exigido por Angular 22; possui Java `21.0.10`, enquanto a decisão travada exige Java 25; o daemon Docker Desktop não está ativo. [VERIFIED: ambiente local, 2026-08-01] [CITED: https://angular.dev/reference/versions]
**Como evitar:** o plano 01-01 bloqueia até Java 25, Node 22.22.3+ e Docker Desktop/WSL2 estarem disponíveis; 01-02/01-17, 01-03 e 01-04 executam os smokes Maven/Spring, Compose e Angular sem downgrade.
**Sinal cedo:** `java -version`, `node --version` e `docker info` não correspondem ao gate.

### Pitfall 2: Spring Modulith 2.1/Boot 4.1 sem prova local de compatibilidade

**O que falha:** a resolução Maven ou os testes Modulith falham após o projeto já estar estruturado.
**Por quê:** a documentação atual publica BOM 2.1.0, mas a matriz de compatibilidade exibida não traz uma linha final explícita para 2.1; o repositório do projeto registra a preparação contra marcos Boot 4.1. [CITED: https://docs.spring.io/spring-modulith/reference/] [CITED: https://docs.spring.io/spring-modulith/reference/appendix.html]
**Como evitar:** primeiro commit backend deve resolver BOM `2.1.0`, compilar sob Java 25 e executar um teste mínimo `ApplicationModules.verify()`; bloquear avanço caso falhe.
**Sinal cedo:** conflito de versões Spring Framework, falha no contexto ou ausência do starter de teste.

### Pitfall 3: gerar cliente Angular sem prova de Angular 22

**O que falha:** o cliente é gerado, mas não compila no frontend.
**Por quê:** a página oficial do gerador `typescript-angular` o marca estável, porém declara Angular 9.x–21.x e default `ngVersion` 21.0.0, não 22. [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/]
**Como evitar:** o plano 01-20 gera a spec mínima, configura explicitamente Angular/TypeScript e compila antes de aceitar o gerador; 01-26 regenera a saída final e 01-16 verifica drift sem regenerar.
**Sinal cedo:** imports obsoletos, peer dependency incompatível ou erro TypeScript no código gerado.

### Pitfall 4: misturar sessão do BFF, sessão do Keycloak e política de inatividade

**O que falha:** logout não encerra apenas o dispositivo, sessão dura mais/menos que 8h ociosas ou a renovação ocorre por tráfego irrelevante.
**Por quê:** há ao menos duas camadas de sessão; o tempo ocioso aprovado é da sessão BETUFU e Keycloak possui configuração própria. [VERIFIED: .planning/phases/01-funda-o-segura-da-plataforma/01-CONTEXT.md:28-32]
**Como evitar:** criar um contrato de sessão com campos explícitos para `idleTimeout=8h`, `absoluteTimeout=null`, instante da última atividade válida, `sessionId` opaco, motivo de término e semântica de logout/revogação. O plano 01-06 fecha uma allowlist: request autenticado/intencional ou navegação protegida renovam; Actuator, assets, consulta de sessão, polling, preflight e heartbeats não renovam. [RESOLVED: 01-06-PLAN.md]
**Sinal cedo:** qualquer endpoint/ping renova TTL sem passar por uma política central.

### Pitfall 5: health/telemetria tornam-se vazamento de segredo

**O que falha:** diagnóstico, Actuator, log JSON ou trace inclui token, URL interna, senha, matrícula ou payload.
**Por quê:** Actuator possui endpoints ricos e structured logging pode capturar campos adicionados indiscriminadamente. [CITED: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html] [CITED: https://docs.spring.io/spring-boot/reference/features/logging.html]
**Como evitar:** allowlist de campos no DTO de diagnóstico, redaction de logs, nenhum payload de autenticação em trace e testes que procuram campos proibidos.
**Sinal cedo:** `env`, `configprops`, header `Authorization`, cookie, URL de banco ou stack trace aparecem no browser/log normal.

## Code Examples

### Verificação estrutural do monólito

```java
// Fonte: https://docs.spring.io/spring-modulith/reference/verification.html
class ModulithArchitectureTests {
  @Test
  void verifiesModuleBoundaries() {
    ApplicationModules.of(BetufuApplication.class).verify();
  }
}
```

### Contrato de erro RFC 9457 sem detalhe técnico na interface

```java
// Fonte: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, mensagemPublica);
problem.setProperty("code", codigoEstavel);
problem.setProperty("correlationId", correlationId);
return problem;
```

O handler central deve produzir mensagens em português e nunca preencher propriedades com exceção, segredo, URL interna ou stack trace. A capacidade de estender `ProblemDetail` com propriedades é prevista oficialmente; a lista de campos seguros é recomendação desta fase. [CITED: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html] [ASSUMED]

### Dependência Compose pronta, não apenas iniciada

```yaml
# Fonte: https://docs.docker.com/compose/how-tos/startup-order/
depends_on:
  postgres:
    condition: service_healthy
```

### Segurança STOMP

O cliente obtém token CSRF por endpoint BFF seguro e o envia no header do frame `CONNECT`; o servidor valida CSRF, origem permitida e autorização de destino. Spring Security requer CSRF em `CONNECT` por padrão, pois WebSocket não aplica Same Origin Policy por si só. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html]

## State of the Art

| Abordagem anterior | Abordagem atual | Impacto para a fase |
|--------------------|----------------|---------------------|
| Implicit flow ou password/direct grant | Authorization Code; password/direct grant é removido do caminho OAuth 2.1 e não recomendado | BFF deve usar Authorization Code, `state`, `nonce` e PKCE; não criar tela de senha BETUFU. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] |
| Tracing direto pela API OpenTelemetry | Micrometer Observation/Tracing com exporter OTLP | Preferir a API recomendada pelo Boot; isso preserva logs, métricas e traces correlacionados. [CITED: https://docs.spring.io/spring-boot/reference/actuator/observability.html] |
| `typescript-angular` sem gate de versão | geração com smoke de compilação Angular | A própria documentação ainda anuncia suporte até Angular 21, então Angular 22 precisa de validação explícita. [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/] |

## Assumptions Log

| # | Afirmação | Seção | Risco se estiver errada |
|---|-----------|-------|-------------------------|
| A1 | A projeção de diagnóstico pode usar um DTO BFF separado e estados agregados em vez de expor Actuator. | Architecture Patterns | Implementação pode acoplar a UI a dados sensíveis de gestão. |
| A2 | O contrato de sessão proposto com `absoluteTimeout=null` é a melhor representação técnica da ausência de TTL absoluto. | Common Pitfalls | Divergência entre contrato e política de sessão. |
| A3 | Montar `init-databases.sh` dentro do PostgreSQL basta para compatibilidade Windows/Linux. | Infrastructure | `core.autocrlf=true` pode produzir CRLF e quebrar o entrypoint; o 01-03 deve prever `.gitattributes` e provar `eol: lf`. |
| A4 | Provisionar `TECHNICAL` no realm basta para autorização Spring. | Authentication | Sem claim de ID token e mapper allowlistado, o 01-26 pode negar o usuário técnico ou conceder autoridade por fonte errada. |
| A5 | `up --wait`/`ps` provam isolamento e privacidade. | Validation | Containers saudáveis não demonstram privilégio PostgreSQL, negação anônima S3, persistência nem tema renderizado. |

## Open Questions — Plano 01-03 (BLOCKING)

As respostas pertencem a `01-03-GATE.md`, exigem aprovação humana e não podem ser inferidas pelo executor:

1. Quais referências oficiais completas e healthchecks compatíveis serão usados nas cinco imagens?
2. Quais portas/binds e qual versão mínima do Compose sustentam o modo híbrido sem exposição silenciosa?
3. Qual topologia SeaweedFS, bucket privado, autenticação e contrato `BETUFU_S3_*` serão adotados somente no local?
4. Como credenciais locais descartáveis, SMTP, import do realm e a claim `TECHNICAL` serão provisionados e validados?
5. Qual parent/template e estratégia de ativos/fontes permitem um tema compatível e um smoke HTTP real?
6. Quais comandos exatos provam isolamento PostgreSQL, SMTP local, S3 autenticado/anônimo/persistente e tema renderizado?

## Open Questions resolvidas

1. **[RESOLVED — decisão fixada; evidência de execução pendente] Qual versão/ferramenta produz o cliente Angular 22?**
   - Decisão: `@openapitools/openapi-generator-cli@2.40.1` com `typescript-angular`, Angular 22.1.x e TypeScript 6.0.x, conforme 01-01/01-20.
   - Prova: 01-20 gera e compila a saída inicial; 01-26 regenera a saída final; 01-16 executa apenas o check read-only de drift.
   - Gate: se o smoke falhar, a execução para e retorna ao usuário. Não há autorização para trocar gerador, versão ou stack silenciosamente. [CITED: https://openapi-generator.tech/docs/generators/typescript-angular/]

2. **[RESOLVED — stack aprovada; validação bloqueante em 01-02] Boot 4.1.0 + Spring Modulith 2.1.0.**
   - Decisão: manter exatamente as versões canônicas já aprovadas.
   - Prova: 01-02 resolve o BOM e compila em Java 25; 01-17 faz `BetufuApplicationSmokeTest` executar um `ApplicationModules.of(...).verify()` mínimo; 01-09/01-23 declaram e verificam os dez módulos completos.
   - Gate: qualquer incompatibilidade interrompe a execução e exige decisão explícita, sem downgrade. [CITED: https://docs.spring.io/spring-boot/system-requirements.html] [CITED: https://docs.spring.io/spring-modulith/reference/appendix.html]

3. **[RESOLVED — política definida em 01-06] Quais eventos renovam a inatividade de 8h?**
   - Renovam: request autenticado/intencional em `/api/v1/**` ou navegação protegida iniciada pelo usuário.
   - Não renovam: Actuator, assets, `GET /api/v1/session`, polling, preflight, heartbeat e STOMP heartbeat.
   - Prova: `SessionPolicyTest`, `RedisSessionIT` e a exclusão de heartbeat em 01-08; o TTL permanece PT8H e sem limite absoluto.

## Environment Availability

| Dependência | Exigida por | Disponível | Versão/estado | Fallback |
|-------------|-------------|------------|---------------|----------|
| Java 25 | backend aprovado | ✗ | Java `21.0.10` encontrado | Instalar JDK 25 e apontar `JAVA_HOME`; não reduzir para Java 21. [VERIFIED: ambiente local, 2026-08-01] |
| Maven | build backend | ✓ | `3.9.16` no host | O plano 01-02 criará e fixará o Maven Wrapper; ele ainda não existe no repositório. [VERIFIED: ambiente local, 2026-08-01] |
| Node para Angular 22 | frontend | ✗ | `v22.20.0`, abaixo de `^22.22.3` | Atualizar Node; não há fallback aprovado. [VERIFIED: ambiente local, 2026-08-01] [CITED: https://angular.dev/reference/versions] |
| npm | frontend | ✓ | `10.9.3` | lockfile npm. [VERIFIED: ambiente local, 2026-08-01] |
| Docker CLI / Compose | infra e Testcontainers | Parcial | CLI e Compose `v5.1.3` presentes, daemon Docker Desktop indisponível | Iniciar Docker Desktop/WSL2; sem daemon, Compose/Testcontainers bloqueiam. [VERIFIED: ambiente local, 2026-08-01] |
| PostgreSQL, Redis, Keycloak, SeaweedFS e e-mail dev | Compose | ✗ localmente | Não sondáveis sem daemon | Aprovar primeiro as cinco imagens no Gate 1; depois subir pelo Compose com `--env-file infra/.env.example`. [VERIFIED: ambiente local, 2026-08-01; decisão atualizada em 2026-08-09] |

**Dependências ausentes sem fallback:** JDK 25, Node suportado pelo Angular e daemon Docker.
**Dependências ausentes com fallback:** nenhuma.

## Validation Architecture

### Test Framework

| Propriedade | Valor |
|-------------|-------|
| Framework backend | JUnit 5, Spring Boot Test, Testcontainers, Spring Modulith Test e ArchUnit. [VERIFIED: .planning/PROJECT.md:127-131] |
| Framework frontend | testes de componente Angular; Playwright fica preparado para jornadas posteriores, não substitui testes de unidade desta fase. [VERIFIED: .planning/PROJECT.md:127-131] |
| Configuração | ainda não existe; 01-02 cria Maven Wrapper/POM e 01-04 cria o workspace Angular. [VERIFIED: .planning/phases/01-funda-o-segura-da-plataforma/01-CONTEXT.md:105-117] |
| Quick run | `node scripts/maven.mjs -f backend/pom.xml test` e `npm --prefix frontend test -- --watch=false` após 01-02/01-04. [ASSUMED] |
| Suite completa | smoke Compose + backend + frontend build + geração/diff OpenAPI. [ASSUMED] |

### Phase Requirements → Test Map

| Req ID | Comportamento | Tipo | Comando automatizado | Arquivo existe? |
|--------|---------------|------|----------------------|-----------------|
| ARCH-01 | runtime e build usam versões aprovadas | smoke | `node scripts/maven.mjs -f backend/pom.xml test` + build Angular | ❌ 01-01/02/04 |
| ARCH-02, ARCH-03, TEST-02 | módulo só acessa API permitida e sem ciclos | arquitetura | `node scripts/maven.mjs -f backend/pom.xml test -Dtest=*ArchitectureTest,*ModuleTest` | ❌ 01-09/23 |
| IDEN-01..04 | login BFF, cookie, CSRF, logout e STOMP CONNECT | integração/security | `node scripts/maven.mjs -f backend/pom.xml test -Dtest=*SessionIT,*SecurityIT` | ❌ 01-17/21/06/07/22/08 |
| DATA-01, DATA-02, DATA-06, TEST-01 | migration e escrita passam por PostgreSQL real; Redis não é fonte oficial | integração Testcontainers | `node scripts/maven.mjs -f backend/pom.xml test -Dtest=*PersistenceIT` | ❌ 01-05/10 |
| API-01, API-02 | contrato `/api/v1`, Problem Details e cliente gerado sem diff | contrato/build | `node scripts/validate-phase1.mjs --contract` | ❌ 01-20/12/26/16 |
| OPS-01..03 | Compose, health seguro, log JSON, métrica e correlation | smoke/manual assistido | `node scripts/dev.mjs verify daily` e gate agregado | ❌ 01-03/11/12/16 |

### Sampling Rate

- **Por commit de tarefa:** teste Maven/Angular relativo ao módulo alterado. [ASSUMED]
- **Por conclusão de plano/onda:** suite completa correspondente, check OpenAPI read-only e `docker compose ... config` quando aplicável. [ASSUMED]
- **Gate da fase:** ambiente completo sobe, diagnóstico técnico autenticado demonstra estado degradado seguro e os requisitos acima ficam verdes. [ASSUMED]

### Lacunas pré-execução roteadas aos planos

- [ ] **01-01:** JDK 25, Node compatível, daemon Docker e legitimidade npm disponíveis/aprovados.
- [ ] **01-02/01-17:** Maven Wrapper/POM/launcher e primeiro `ApplicationModules.verify()`.
- [ ] **01-03:** Compose mínimo com healthchecks, volumes nomeados e realm Keycloak exato.
- [ ] **01-04/01-20/01-26:** workspace Angular e geração inicial/final OpenAPI compilam sem troca silenciosa.
- [ ] **01-05/01-10:** Testcontainers PostgreSQL, migrations Flyway e tracer real passam.
- [ ] **01-09/01-23/01-16:** módulos completos, gates arquiteturais e gate agregado encerram verdes.

## Security Domain

O ASVS 5.0.0 é a referência estável atual; os rótulos abaixo preservam os focos V2–V6 usados pelo projeto, mas o plano deve citar requisitos com versão ASVS explícita para não confundir numeração entre revisões. [CITED: https://github.com/OWASP/ASVS] [CITED: https://owasp.org/www-project-developer-guide/assets/exports/OWASP_Developer_Guide.pdf]

### Applicable ASVS Categories

| Categoria ASVS | Aplica | Controle padrão |
|----------------|--------|-----------------|
| V2 Authentication | sim | Keycloak, Authorization Code, discovery OIDC e MFA administrativo. [CITED: https://www.keycloak.org/securing-apps/oidc-layers] |
| V3 Session Management | sim | sessão BFF server-side, cookie `HttpOnly`/`Secure`/SameSite, regeneração e logout por dispositivo. [CITED: https://docs.spring.io/spring-security/reference/features/exploits/csrf.html] |
| V4 Access Control | sim | Spring Security no BFF/API; endpoint diagnóstico só para conta técnica em dev/homologação. [VERIFIED: .planning/phases/01-funda-o-segura-da-plataforma/01-CONTEXT.md:21-24] |
| V5 Validation, Sanitization and Encoding | sim | Bean Validation + handler Problem Details sanitizado; cliente não decide autorização. [ASSUMED] |
| V6 Cryptography | sim | TLS, segredos externos e criptografia de objeto privada configurada pelo provedor; não implementar criptografia própria. [VERIFIED: .planning/PROJECT.md:111-113] |

### Known Threat Patterns for this stack

| Padrão | STRIDE | Mitigação padrão |
|--------|--------|------------------|
| Roubo de token em browser | Information Disclosure | BFF guarda tokens; cookie é `HttpOnly`; não usar storage de tokens. [VERIFIED: .planning/REQUIREMENTS.md:155-155] |
| Login CSRF / callback OIDC forjado | Spoofing | `state`, `nonce`, Authorization Code e PKCE; validar redirect URI. [VERIFIED: .planning/REQUIREMENTS.md:156-156] |
| CSRF em mutação ou STOMP CONNECT | Tampering | CSRF sincronizador, origem permitida e autorização de destino. [CITED: https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html] |
| Session fixation | Spoofing | regenerar sessão em autenticação/reautenticação e testar token anterior inválido. [CITED: https://cornucopia.owasp.org/taxonomy/asvs-5.0/07-session-management/02-fundamental-session-management-security] |
| Vazar segredo por operação/diagnóstico | Information Disclosure | DTO allowlist, redaction e Actuator protegido; não expor `env`/`configprops`. [CITED: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html] |
| Alterar schema/histórico | Tampering | Flyway checksum, migrations novas e PostgreSQL com usuário específico. [CITED: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations] |

## Sources

### Primary (oficiais)

- https://docs.spring.io/spring-boot/system-requirements.html — Java/Maven suportados pelo Boot 4.1.
- https://docs.spring.io/spring-modulith/reference/ e https://docs.spring.io/spring-modulith/reference/verification.html — BOM, limites e testes modulith.
- https://angular.dev/reference/versions — matriz Angular 22 / Node / TypeScript / RxJS.
- https://www.keycloak.org/securing-apps/oidc-layers e https://www.keycloak.org/server/importExport — OIDC e bootstrap de realm.
- https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html — CSRF e origem no STOMP.
- https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations — migrations imutáveis.
- https://docs.docker.com/compose/how-tos/startup-order/ e https://docs.docker.com/compose/how-tos/profiles/ — Compose.
- https://openapi-generator.tech/docs/generators/typescript-angular/ — estado do gerador Angular.
- https://docs.spring.io/spring-boot/reference/actuator/tracing.html e https://docs.spring.io/spring-boot/reference/features/logging.html — correlação e observabilidade.

## Metadata

**Confidence breakdown:**

- Standard stack: MÉDIA — versões principais verificadas, mas Modulith/Boot e gerador Angular 22 exigem smoke gate.
- Architecture: MÉDIA — BFF, Modulith, Flyway, Compose e segurança STOMP foram verificados em documentação oficial pelo provedor `websearch` classificado como MÉDIA.
- Pitfalls: MÉDIA — derivados de documentação oficial e da auditoria real do host.

**Research date:** 2026-08-01
**Valid until:** 2026-08-08, porque as versões de Angular/Boot/Modulith estão em ciclo rápido.
