---
phase: 01-funda-o-segura-da-plataforma
plan: "11"
type: execute
wave: 18
depends_on: [01-10]
requirements: [DATA-06, OPS-01, OPS-02]
files_modified:
  - backend/Dockerfile
  - frontend/Dockerfile
  - .dockerignore
  - frontend/.dockerignore
  - backend/src/main/resources/application.yaml
  - infra/compose.yaml
  - infra/nginx/nginx.conf
  - scripts/dev.mjs
  - scripts/lib/compose.mjs
  - scripts/validate-phase1.mjs
status: completed
---

# Sumário de Execução — Plano 01-11: Perfis daily/full e Orquestrador Cross-Platform

## 1. Visão Geral

O Plano 01-11 entregou os dois modos locais reproduzíveis da BETUFU (`daily` e `full`) e o orquestrador cross-platform em Node.js (`scripts/dev.mjs`, `scripts/lib/compose.mjs` e `scripts/validate-phase1.mjs`), garantindo paridade de origem estrita (`http://localhost:4200`), portabilidade Windows/Linux sem dependência de shell (D-13), preservação de dados e volumes (D-11), isolamento por lockfile TOCTOU-safe com prova empírica de colisão cross-profile, suporte a caminhos e logs Unicode, discovery OIDC integral com issuer unificado (`http://localhost:8081/realms/betufu-local`) preservando a unicidade da chave natural `(issuer, subject)` no PostgreSQL, e governança de segurança em Nginx e proxy BFF.

---

## 2. Entregas por Tarefa

### Task 1: Operar o modo daily preservando dados
- **`scripts/lib/compose.mjs`**: Biblioteca compartilhada de orquestração Docker Compose:
  - `composeArgs(profile)` — montagem segura de argumentos com `--env-file`, `-f`, `--project-name`, `--profile`.
  - `runCaptured()` / `runInherited()` — execução de processos sempre com `shell:false` (D-13) e propagação de exit codes.
  - `acquireLock()` — controle de concorrência com lockfile `O_CREAT|O_EXCL`, detecção e recuperação de lock órfão por verificação atômica de PID.
  - `waitHealthy(profile)` — polling estruturado de saúde filtrado por profile e contagem estrita de serviços.
  - `sanitizeOutput()` — redação de valores de variáveis de ambiente na saída (inclusive com suporte a caracteres Unicode UTF-8).
  - `readEnvPairs()` — leitura segura de variáveis do `.env.example` para mascaramento (valores sensíveis nunca impressos).
- **`scripts/dev.mjs`**: CLI de desenvolvimento com subcomandos `up`, `verify`, `status`, `stop` e `logs`:
  - `verify daily` — 6 etapas com provas empíricas discriminantes: subida de 5 serviços base healthy, idempotência de containers (contagem estrita de 5), sobrevivência de volumes a ciclo destrutivo `down + up` (sem `-v`), falha discriminante de variável ausente com mutação temporária segura (`mkdtemp` + `mode: 0o600`), teste empírico de colisão de lock cross-profile `daily` vs `daily` e `daily` vs `full` ([EDGE OPS-01/concurrency] e [EDGE OPS-02/concurrency]), e teste empírico de manipulação de caminhos e sanitização de logs com caracteres Unicode UTF-8 ([EDGE OPS-02/encoding]).
  - `logs` — captura e sanitização prévia antes de ecoar no terminal.
  - `stop` / `status` — passagem multi-profile (`--profile daily --profile full`) para operar em todos os containers gerenciados.

### Task 2: Validar a composição full com Spring Boot e Nginx
- **`backend/Dockerfile`**: Dockerfile multi-stage com digest OCI fixado (`eclipse-temurin:25-jre-noble@sha256:fbcf915c585659b30eb766ada4d6d7cfc9ec1040bf521e95bf61b10a25af73db`), usuário não-privilegiado `betufu:betufu` (UID 10001), `curl` e `socat` para healthcheck e roteamento loopback OIDC, e parâmetros ZGC otimizados para container.
- **`frontend/Dockerfile`**: Dockerfile multi-stage com digest OCI fixado (`nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0`), servindo a aplicação Angular compilada sem privilégios de root na porta `8080` (mapeada para `4200` no host).
- **`.dockerignore` & `frontend/.dockerignore`**: Exclusões estritas de artefatos temporários, `node_modules`, `target/classes` e `infra/.env*` para blindagem contra vazamento de segredos no daemon Docker.
- **`backend/src/main/resources/application.yaml`**: Preservação estrita de `issuer-uri: "${BETUFU_OIDC_ISSUER_URI}"` (garantindo que o Spring Security execute o discovery OIDC e popule `providerConfigurationMetadata` com `revocation_endpoint` e `end_session_endpoint`), sem defaults embutidos de desenvolvimento e sem sobrescrita arbitrária de `authorization-uri`.
- **`infra/nginx/nginx.conf`**: Configuração segura de proxy reverso com paridade exata para `http://localhost:4200`:
  - Bloqueio estrito de arquivos ocultos e segredos (`location ~ /\. { deny all; return 404; }`).
  - Bloqueio explícito de source maps em produção (`location ~* \.map$ { deny all; return 404; }`).
  - Proxy BFF para `/api`, `/oauth2` e `/login` com regex exata (`~ ^/(api|oauth2|login)(?:/|$)`) repassando `Host`, `X-Forwarded-Host`, `X-Forwarded-Port: 4200`, `X-Forwarded-Proto` e `X-Forwarded-For`.
  - Proibição de cache em respostas BFF (`Cache-Control: no-store, no-cache, must-revalidate, max-age=0` always).
  - Proibição de cache em respostas estáticas do Nginx em `location /` (`Cache-Control: no-store, no-cache, must-revalidate, max-age=0` always).
  - Suporte a WebSockets em `/ws` com bloco dinâmico `map $http_upgrade $connection_upgrade`.
  - HTML5 SPA routing fallback (`try_files $uri $uri/ /index.html`).
- **`infra/compose.yaml`**: Adição dos serviços `backend` e `frontend` no profile `[full]`, com healthchecks herméticos, Keycloak com `KC_HOSTNAME: "http://localhost:8081"`, backend com `BETUFU_OIDC_ISSUER_URI: http://localhost:8081/realms/betufu-local` e dependências encadeadas.
- **`scripts/validate-phase1.mjs`**: Orquestrador fail-fast do gate agregado da Fase 1, validando flags desconhecidas antecipadamente e executando `verify daily` e `verify full` sob o modo `--infra`.

---

## 3. Evidências de Verificação

### 1. Suíte Completa de Testes Backend (`.\mvnw verify -f backend/pom.xml`)
- **Resultado:** **PASS (BUILD SUCCESS, 0 erros, 0 falhas, 0 pulados)**

| Suíte | Classes | Testes | Falhas | Erros | Pulados |
|---|---|---|---|---|---|
| Surefire (Unitários) | 9 | 57 | 0 | 0 | 0 |
| Failsafe (Integração) | 12 | 76 | 0 | 0 | 0 |
| **Total** | **21** | **133** | **0** | **0** | **0** |

### 2. `node scripts/dev.mjs verify daily` (Exit Code 0)
- **Etapa 1/6 (Up):** 5 serviços base healthy (`postgres`, `redis`, `keycloak`, `seaweedfs`, `mailpit`).
- **Etapa 2/6 (Idempotência):** IDs dos 5 containers rigorosamente idênticos após 2º `up` com contagem estrita de 5.
- **Etapa 3/6 (Volumes):** Marcador PostgreSQL persistido e validado após `compose down` (destruição e recriação de containers com dados preservados no volume nomeado).
- **Etapa 4/6 (Variável ausente):** Falha discriminante comprovada com mutação temporária sem vazar segredos.
- **Etapa 5/6 (Concorrência empírica [EDGE OPS-01/concurrency] e [EDGE OPS-02/concurrency]):** Processos concorrentes `up daily` e `up full` disparados contra o lock ativo; ambos bloqueados com sucesso (exit codes !== 0 e mensagens de lock no stderr).
- **Etapa 6/6 (Encoding Unicode [EDGE OPS-02/encoding]):** Validação de `sanitizeOutput` com chaves/valores acentuados e manipulação de lockfile em caminho temporário Unicode (`ação-têst-✓`).

### 3. `node scripts/dev.mjs verify full` (Exit Code 0)
- **Etapa 1/5 (Build e Up):** 7 serviços compilados e healthy (`postgres`, `redis`, `keycloak`, `seaweedfs`, `mailpit`, `backend`, `frontend`).
- **Etapa 2/5 (Liveness Backend e Redirect OIDC 302 via Nginx):**
  - `GET http://127.0.0.1:4200/api/v1/session` respondeu 200 OK com contrato unauthenticated.
  - `GET http://127.0.0.1:4200/oauth2/authorization/keycloak` retornou HTTP 302 com `Location` apontando para `http://localhost:8081/realms/betufu-local/protocol/openid-connect/auth` contendo `client_id=betufu-bff`, `redirect_uri` público em `http://localhost:4200` e `response_type=code`.
- **Etapa 3/5 (Frontend Angular, SPA Fallback e Paridade Regex):**
  - `GET http://127.0.0.1:4200/` retornou `<app-root>` com 200 OK.
  - Rota inexistente `/rota-spa-teste-fallback` retornou SPA `index.html` com 200 OK.
  - Rota `/apidocs` caiu no SPA fallback (não interceptada pelo proxy), comprovando paridade regex com `proxy.conf.json`.
- **Etapa 4/5 (Segurança Nginx):** Bloqueio de `/.env` (404), bloqueio de `.map` (404), `Cache-Control: no-store` em resposta estática do Nginx (`/index.html`) e no endpoint BFF `/api/v1/session`.
- **Etapa 5/5 (Idempotência Full):** IDs de todos os 7 containers rigorosamente idênticos após 2º `up` com contagem estrita de 7.

### 4. `node scripts/validate-phase1.mjs --infra` (Exit Code 0)
- Execução sequencial fail-fast de `verify daily` (1/2) e `verify full` (2/2) aprovada com `PASS: --infra (daily e full)`.

---

## 4. Requisitos e Must-Haves Entregues

| Requisito / Must-Have | Status | Evidência |
|---|---|---|
| `daily` sobe infra e executa app pelo host (D-10) | ✅ | `dev.mjs up daily` ativa os 5 serviços base |
| `full` executa Spring e Angular/Nginx em containers (D-10) | ✅ | `dev.mjs up full` compila e orquestra os 7 serviços |
| Preservação de volumes em restarts e down (D-11) | ✅ | Marcador SQL persiste após recriação de containers |
| Cross-platform sem shell-ismos (D-13) | ✅ | `shell:false` e spawn via arrays em todos os scripts Node |
| Paridade de origem em `http://localhost:4200` | ✅ | Nginx replica exatamente cabeçalhos, rotas regex e portas |
| Segurança Nginx (sem segredos, sem source maps, no-cache Nginx e BFF) | ✅ | Verificado em `verify full` etapa 4/5 (estático e BFF) |
| Unicidade estrita do issuer OIDC em ambos os modos | ✅ | Issuer unificado em `http://localhost:8081/realms/betufu-local`, preservando chave natural `(issuer, subject)` no PostgreSQL |
| [EDGE OPS-01/idempotency] | ✅ | Comprovado com contagem estrita de 5 containers em `daily` e 7 em `full` |
| [EDGE OPS-01/concurrency] | ✅ | Bloqueio concorrente de `up daily` na etapa 5/6 |
| [EDGE OPS-02/empty] | ✅ | Mutação de env-file em diretório isolado (`0o600`) na etapa 4/6 |
| [EDGE OPS-02/encoding] | ✅ | Prova empírica de sanitização e caminhos Unicode (`ação-têst-✓`) na etapa 6/6 |
| [EDGE OPS-02/concurrency] | ✅ | Bloqueio concorrente cross-profile `daily` vs `full` na etapa 5/6 |

---

## 5. Reconciliação de Escopo e Allowlist

Conforme exigido pelo protocolo de governança do projeto, declaramos formalmente a ampliação de escopo e ratificação da allowlist de arquivos modificados/adicionados pelo Plano 01-11:

| Arquivo | Papel / Motivação da Inclusão |
|---|---|
| `backend/Dockerfile` | Multi-stage packaging do backend Java 25 / Spring Boot 4.1.0 com OCI fixado e socat loopback. |
| `frontend/Dockerfile` | Multi-stage packaging do frontend Angular zoneless com Nginx unprivileged e OCI fixado. |
| `.dockerignore` | Blindagem da raiz do repositório contra transmissão de `.git`, `.planning` e `infra/.env*` ao daemon Docker. |
| `frontend/.dockerignore` | Blindagem do workspace frontend contra envio de `node_modules` e artefatos de build. |
| `backend/src/main/resources/application.yaml` | Preservação de `issuer-uri` OIDC sem defaults embutidos de desenvolvimento. |
| `infra/compose.yaml` | Definição dos perfis `daily` e `full`, configuração do Keycloak e dos serviços Spring/Nginx. |
| `infra/nginx/nginx.conf` | Proxy reverso seguro com paridade de origem, regex BFF, WebSocket e no-cache headers. |
| `scripts/dev.mjs` | CLI de orquestração local com testes empíricos de idempotência, volumes, concorrência e encoding. |
| `scripts/lib/compose.mjs` | Biblioteca utilitária cross-platform (`shell:false`), lockfile atômico, sanitização e polling de saúde. |
| `scripts/validate-phase1.mjs` | Script de validação agregado da Fase 1 com execução fail-fast do modo `--infra`. |
