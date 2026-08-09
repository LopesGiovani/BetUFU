# Fase 1: Fundação Segura da Plataforma — Mapa de Padrões

**Mapeado em:** 2026-08-02
**Arquivos/módulos analisados:** 21
**Análogos no repositório:** 0 / 21

## Contexto da busca

O repositório contém somente artefatos de planejamento; `rg --files` não encontrou arquivos de aplicação sob `backend/`, `frontend/`, `infra/` ou `docs/`. Portanto, não há imports, classes, componentes, handlers, testes ou convenções de código existentes que possam ser copiados. Não foram fabricados trechos ou linhas de código inexistente.

Os padrões abaixo são restrições para a primeira implementação, derivadas exclusivamente dos artefatos aprovados: `01-CONTEXT.md`, `01-RESEARCH.md`, `01-UI-SPEC.md`, `PROJECT.md` e `REQUIREMENTS.md`. Os caminhos são propostos para o plano — o planejador pode ajustar nomes internos sem alterar os limites e contratos obrigatórios.

## Classificação de arquivos

| Novo/modificado | Papel | Fluxo de dados | Análogo mais próximo | Qualidade |
|---|---|---|---|---|
| `backend/pom.xml` e Maven Wrapper | config | batch/build | — | sem análogo |
| `backend/src/main/java/.../BetufuApplication.java` | config/boot | request-response | — | sem análogo |
| `backend/src/main/java/.../identity/` | módulo/model/service | request-response | — | sem análogo |
| `backend/src/main/java/.../platform/security/SecurityConfiguration.java` | config/middleware | request-response | — | sem análogo |
| `backend/src/main/java/.../platform/session/` | service/model | request-response | — | sem análogo |
| `backend/src/main/java/.../diagnostic/DiagnosticController.java` | controller/route | request-response | — | sem análogo |
| `backend/src/main/java/.../diagnostic/DiagnosticService.java` e DTOs | service/model | transform | — | sem análogo |
| `backend/src/main/java/.../platform/api/ProblemDetailsHandler.java` | middleware/utility | request-response | — | sem análogo |
| `backend/src/main/java/.../platform/observability/` | middleware/config | event-driven | — | sem análogo |
| `backend/src/main/resources/application*.yaml` | config | request-response | — | sem análogo |
| `backend/src/main/resources/db/migration/V1__*.sql` | migration | batch | — | sem análogo |
| `backend/src/test/java/.../ModulithArchitectureTests.java` | test | batch | — | sem análogo |
| `backend/src/test/java/.../*SessionIT.java`, `*PersistenceIT.java` | test | request-response/CRUD | — | sem análogo |
| `backend/openapi/` ou saída OpenAPI versionada | config/contract | transform | — | sem análogo |
| `frontend/package.json`, `angular.json`, lockfile | config | batch/build | — | sem análogo |
| `frontend/src/app/app.config.ts`, `app.routes.ts` | config/route | request-response | — | sem análogo |
| `frontend/src/app/core/session/` | service/store/guard | request-response | — | sem análogo |
| `frontend/src/app/layout/` | component | request-response | — | sem análogo |
| `frontend/src/app/features/diagnostic/` | component/service | request-response | — | sem análogo |
| `frontend/src/styles.scss` e ativos locais de fonte/tema | config/component | transform | — | sem análogo |
| `infra/compose.yaml`, Dockerfiles, Keycloak import, `.env.example` | config | event-driven/file-I/O | — | sem análogo |

## Atribuições de padrões

### Backend modular e configuração de build

**Arquivos:** `backend/pom.xml`, Maven Wrapper, `scripts/maven.mjs`, `BetufuApplication.java`, pacotes de primeiro nível dos módulos e `ModulithArchitectureTests.java`
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** monorepo com `backend/`, `frontend/`, `infra/` e `docs/`; Java 25, Spring Boot `4.1.0`, Maven e Spring Modulith BOM `2.1.0`. Todo comando Maven passa pelo launcher Node portátil, que seleciona o Wrapper adequado em Windows/Linux sem cadeia de shell. Organizar backend por capacidade em um monólito modular, não por microsserviço. Somente APIs nomeadas são públicas entre módulos; implementação fica em subpacotes internos. Já nesta fase, preparar limites para identidade, verificação, esportes, odds, apostas, ledger, liquidação, ranking, chat e auditoria, sem implementar o negócio futuro.

**Referência concreta de verificação** — `01-RESEARCH.md:269-276`:

```java
class ModulithArchitectureTests {
  @Test
  void verifiesModuleBoundaries() {
    ApplicationModules.of(BetufuApplication.class).verify();
  }
}
```

**Teste complementar:** usar `@ApplicationModuleTest` por módulo integrado e ArchUnit para regras que não couberem em Modulith. Cobrir `ARCH-02`, `ARCH-03` e `TEST-02` desde o primeiro build.

---

### Identidade, BFF OIDC e sessão

**Arquivos:** `identity/`, `platform/security/SecurityConfiguration.java`, `platform/session/` e configuração por ambiente
**Papel/fluxo:** módulo, middleware e serviço; request-response.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** Keycloak é o único responsável por login, credenciais, recuperação, verificação de e-mail, sessões de identidade e MFA. Spring é o BFF e inicia Authorization Code com `state`, `nonce` e PKCE; os tokens do cliente autorizado permanecem no servidor. O navegador recebe apenas cookie de sessão `HttpOnly`, `Secure` e `SameSite` compatível. Não criar telas de credencial no Angular, SDK SPA, bearer interceptor, `localStorage` ou `sessionStorage` para access/refresh token.

O vínculo BETUFU usa o par estável `(iss, sub)`, nunca e-mail. Modelar contrato de sessão com TTL ocioso de 8 horas, renovado apenas por uma allowlist explícita de atividade válida; `absoluteTimeout` deve representar explicitamente a ausência de limite absoluto (por exemplo, `null`), sem inventar prazo. Login/reautenticação regenera identificador de sessão. `Sair` invalida apenas a sessão do dispositivo atual e executa logout OIDC quando aplicável; sessões paralelas são independentes. Revogação administrativa/segurança é separada: captura o refresh token server-side, envia `token_type_hint=refresh_token`, mantém a sessão local terminal diante de falha e prova `invalid_grant` no refresh posterior, sem prometer rejeição imediata de access JWT.

**Segurança cruzada:** mutações recebem CSRF sincronizador; o futuro STOMP `CONNECT` também envia e valida CSRF, origem permitida e autorização de destino. Não desabilitar CSRF por JSON/cookies.

---

### Endpoint e projeção segura de diagnóstico

**Arquivos:** `diagnostic/DiagnosticController.java`, `DiagnosticService.java`, DTOs de resposta e testes
**Papel/fluxo:** controller, service, model e test; request-response/transform.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** publicar uma projeção BFF sanitizada em `/api/v1`, não um espelho do Actuator. A autorização de servidor aceita somente conta técnica em desenvolvimento/homologação; produção não registra endpoint nem rota Angular correspondente. Guardas Angular apenas melhoram UX — `401/403` do BFF é determinante.

O DTO é allowlist e contém somente: status agregado, seis componentes (`API`, `Sessão BFF`, `Keycloak`, `PostgreSQL`, `Redis`, `Armazenamento de objetos`), estado `Disponível`/`Degradado`/`Indisponível`, descrição pública curta, `correlationId` quando disponível e versão/build/ambiente somente se públicos. Não serializar exceção, stack trace, URL/host/porta, cabeçalho, propriedade, segredo ou health bruto. O resumo é `Disponível` apenas com os seis componentes disponíveis; é `Degradado` quando a projeção responde com um componente degradado/indisponível; é `Indisponível` quando a própria projeção segura não responde.

**Regra de tolerância a falha:** uma dependência falha sem fechar a tela; preservar componentes saudáveis e dados válidos anteriores. A consulta manual é segura para retry e bloqueia somente o próprio botão enquanto executa.

---

### Problem Details, validação e observabilidade

**Arquivos:** `platform/api/ProblemDetailsHandler.java`, validação Bean Validation, filtro/interceptor de correlação e configuração Actuator/Micrometer
**Papel/fluxo:** middleware, utility e config; request-response/event-driven.
**Análogo:** nenhum — primeira implementação.

**Referência concreta de contrato** — `01-RESEARCH.md:281-287`:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, mensagemPublica);
problem.setProperty("code", codigoEstavel);
problem.setProperty("correlationId", correlationId);
return problem;
```

**Padrão obrigatório:** respostas REST JSON versionadas em `/api/v1`, documentadas por OpenAPI e compatíveis com RFC 9457. Handler central produz mensagem pública em português, código estável e `correlationId`; nunca inclui exceção, segredo, URL interna, propriedade técnica ou stack trace. Bean Validation trata campos; UI preserva valores não sensíveis quando seguro, explica cada campo e move foco ao primeiro inválido, sem preservar senha/OTP. Consultas podem ter retry limitado; mutações nunca têm retry silencioso e desabilitam somente a ação em andamento.

Logs são estruturados e métricas/traces usam Micrometer/OTLP. Actuator é operador-only e não é fonte da UI. Toda telemetria deve excluir dados sensíveis; `OPS-03` exige health, logs, métricas e correlation ID seguros.

---

### Persistência e migrations

**Arquivos:** `application*.yaml`, `db/migration/V1__*.sql`, configuração JPA/Flyway e `*PersistenceIT.java`
**Papel/fluxo:** config, migration, service e test; CRUD/batch.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** PostgreSQL 18 é a fonte oficial. Schema muda exclusivamente por migration Flyway versionada, ordenada, validada por checksum e imutável após aplicação; jamais usar `ddl-auto`, scripts manuais ou editar migration aplicada. Mesmo a fundação deve provisionar bancos e usuários separados para BETUFU e Keycloak. Redis é somente auxiliar para sessão/rate-limit/tempo real; objetos usam contrato S3 privado por `BETUFU_S3_*`, com SeaweedFS apenas no ambiente local e sem expor o provedor pela projeção de diagnóstico.

**Testes:** integração de migration e persistência usa PostgreSQL real via Testcontainers e `@ServiceConnection`, não URLs hard-coded ou banco em memória substituto.

---

### Contrato OpenAPI e cliente Angular gerado

**Arquivos:** publicação OpenAPI no backend, configuração de geração e diretório gerado no frontend
**Papel/fluxo:** config/contract; transform.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** `backend/openapi/betufu.yaml` é a fonte canônica; frontend consome cliente `typescript-angular` gerado e não implementa serviços HTTP paralelos. Gerar deterministicamente no 01-20, regenerar atomicamente depois da última mutação no 01-26 e fazer o gate 01-16 executar apenas um check read-only que compara inclusão, remoção e conteúdo sem tocar a saída válida. Fixar versão do gerador somente após smoke de compilação Angular 22; incompatibilidade bloqueia e não autoriza troca silenciosa.

---

### Estrutura inicial Angular e sessão

**Arquivos:** `app.config.ts`, `app.routes.ts`, `core/session/`, `layout/` e testes de componente
**Papel/fluxo:** config, route, service/store, guard e component; request-response.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** Angular `22.1.x`, TypeScript `>=6.0.0 <6.1.0`, componentes standalone, Signals para estado local/derivado e RxJS para operações assíncronas. Usar Angular Router e carregamento por rota; sem NgRx, Nx ou microfrontends. A estrutura autenticada tem somente marca BETUFU, identidade não sensível da sessão e `Sair` num `mat-menu`; não renderizar links, abas ou placeholders futuros.

Na checagem inicial, reservar o cabeçalho e exibir `Preparando sua sessão…`; não mostrar conteúdo autenticado antes do BFF. Expiração por inatividade limpa o conteúdo protegido e exibe `Sua sessão expirou por inatividade. Entre novamente para continuar.` com `Entrar novamente`. Após login, foco vai para `main`; incluir link “Pular para o conteúdo”. Testes focais do store/layout provam esses estados, POST de logout único sem retry, ausência de polling/relógio PT8H no browser e zero token/storage antes do E2E.

---

### Feature Angular de diagnóstico

**Arquivos:** `features/diagnostic/diagnostic-page.component.*`, serviço adaptador do cliente gerado, modelos de view e teste de componente
**Papel/fluxo:** component/service/model/test; request-response/transform.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** registrar rotas técnicas somente em configurações de desenvolvimento/homologação; não compilar rota, link, URL divulgada, import ou lazy chunk alcançável de diagnóstico ou tracer em produção. Renderizar `h1` “Diagnóstico da plataforma”, resumo e exatamente seis cartões com o DTO sanitizado. A grade é 1 coluna (320–599px), 2 (600–899px), 3 (>=900px); preserva dados anteriores em falha e anuncia atualizações em `aria-live="polite"`.

Usar Angular Material/CDK: `mat-toolbar`, `mat-menu`, `mat-button`, `mat-card`, `mat-icon`, `mat-progress-spinner` e componentes standalone. `Verificar novamente` torna-se `Verificando…`, desabilita apenas esse controle e nunca apaga cartões válidos. Implementar cópia apenas de `{correlationId}` via Clipboard API, sem valor inventado, com retorno acessível. Para `401/403`, exibir somente as cópias públicas do contrato e não revelar regras de autorização ou existência da rota em produção.

---

### Tema, acessibilidade e ativos de interface

**Arquivos:** `frontend/src/styles.scss`, tema Angular Material/CDK, fontes auto-hospedadas e testes de componente
**Papel/fluxo:** config/component/test; transform.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** tema exclusivamente escuro, sem alternância clara; fontes Inter e Barlow Condensed auto-hospedadas, sem CDN/Google Fonts. Aplicar tokens do `01-UI-SPEC.md`: canvas `#0B1220`, surface `#142033`, accent `#FF8A3D`, foco `#FFC08A`; estados disponíveis/degradados/indisponíveis sempre por ícone, texto e cor. Usar espaço 4/8/16/24/32/48/64px, raio 8px (cartões/campos) e alvo mínimo 44px.

Atender WCAG 2.2 AA: ordem de foco coerente, rótulo acessível, operação por teclado e `outline: 3px solid #FFC08A; outline-offset: 3px`. Respeitar `prefers-reduced-motion: reduce`; não depender de hover, animação ou cor isolada.

---

### Infraestrutura local reproduzível

**Arquivos:** `.gitattributes`, `infra/compose.yaml`, Dockerfiles, configuração Nginx, import/reino Keycloak, `.env.example`, gates e comandos/wrappers documentados
**Papel/fluxo:** config; event-driven/file-I/O.
**Análogo:** nenhum — primeira implementação.

**Padrão obrigatório:** modo diário: Compose inicia PostgreSQL, Redis, Keycloak, SeaweedFS como S3 exclusivamente local e e-mail de desenvolvimento; Spring/Angular rodam por IDE/terminal. Perfil completo: Spring em contêiner e Angular compilado servido por Nginx. Ambos usam versões de runtime e imagens aprovadas em gate humano e fixadas por tag/digest, volumes nomeados preservados entre `stop/start`, configurações separadas dev/homologação/produção e nenhum segredo real no Git. Todo comando Compose recebe `--env-file infra/.env.example` e é executado separadamente com propagação de falha no PowerShell 5.1.

Primeira inicialização usa `init-databases.sh` dentro do contêiner PostgreSQL, provisiona cliente Keycloak com somente o papel BETUFU `TECHNICAL`, Redis, armazenamento S3 local, e-mail e conta técnica exclusivamente local. Papéis administrativos/de domínio ficam na BETUFU/PostgreSQL. Reset é comando distinto, pede confirmação clara e remove somente dados locais BETUFU. Serviços dependentes usam healthcheck — não somente ordem de start.

Antes do primeiro arquivo de implementação, `01-03-GATE.md` precisa registrar imagens e contrato local completos. Portas/binds, topologia SeaweedFS, bucket/autenticação, variáveis `BETUFU_S3_*`, credenciais locais descartáveis, SMTP, claim `TECHNICAL`, parent do tema e comandos de prova não possuem default implícito. `--env-file` alimenta interpolação do Compose, mas cada variável necessária ao processo do contêiner deve ser mapeada explicitamente.

Compatibilidade Windows/Linux exige `.gitattributes` com `infra/postgres/*.sh text eol=lf` e prova por `git check-attr`; montar o script no contêiner não corrige CRLF. Saúde de contêiner também não substitui prova comportamental: consultar privilégios PostgreSQL, fazer PUT/GET S3 autenticado, rejeitar anônimo, reler após stop/start, confirmar SMTP local e solicitar uma página Keycloak realmente renderizada.

O realm publica `TECHNICAL` somente pela claim de ID token aprovada no gate. O Plano 01-26 mapeia por allowlist apenas o valor exato para `ROLE_TECHNICAL`; e-mail, username, grupo, prefixo ou papel administrativo/de domínio nunca concedem autoridade.

**Referência concreta** — `01-RESEARCH.md:293-298`:

```yaml
depends_on:
  postgres:
    condition: service_healthy
```

## Padrões compartilhados

### Autorização e exposição mínima

**Aplicar a:** BFF, sessão, diagnóstico, rotas Angular e Compose.
**Regra:** servidor decide autenticação/autorização; Angular não contém token OIDC. Diagnóstico só para conta técnica em dev/homologação, removido de produção. Dados técnicos usam DTO allowlist; não expor detalhes de Actuator.

### Erros e correlation ID

**Aplicar a:** todos endpoints `/api/v1` e superfícies Angular.
**Regra:** `ProblemDetail` central com texto público em português, `code` estável e `correlationId`; UI oferece próxima ação e não mostra detalhes internos. Uma falha parcial preserva a área restante e dados válidos.

### Contrato primeiro

**Aplicar a:** controller de diagnóstico e consumidor Angular.
**Regra:** OpenAPI é a fronteira e cliente TypeScript é gerado. Não manter modelos/serviços manuais duplicados para o mesmo endpoint; build detecta diff do artefato gerado.

### Teste desde o primeiro incremento

**Aplicar a:** backend, persistência, segurança e frontend.
**Regra:** JUnit/Testcontainers para transações e migrations reais; Modulith/ArchUnit para limites; teste de componente Angular para estados/UX. Gates mínimos: arquitetura, sessão/segurança, persistência, contrato e `docker compose ... config`.

## Sem análogo encontrado

Todos os 21 itens classificados estão nesta condição porque não existe código de aplicação no repositório. O planejador deve usar os padrões documentais acima e os exemplos oficiais já citados em `01-RESEARCH.md`; não deve atribuir a arquivos inexistentes imports, linhas ou convenções legadas.

## Metadados

**Escopo de busca:** raiz do repositório, excluindo `.git`
**Arquivos de aplicação encontrados:** 0
**Data da extração:** 2026-08-02
