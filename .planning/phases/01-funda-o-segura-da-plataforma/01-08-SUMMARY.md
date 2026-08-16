---
phase: 01-funda-o-segura-da-plataforma
plan: "08"
subsystem: boundary-security-csrf-stomp
tags: [security, csrf, xsrf, cors, websocket, stomp, problem-details, session]

requires:
  - phase: 01-29
    provides: "Contrato XSRF SPA oficial e cookie XSRF-TOKEN com double-submit"
  - phase: 01-21
    provides: "Contexto de proxy confiável e origens de frontend"
  - phase: 01-22
    provides: "Endpoints de sessão e logout protegidos"
provides:
  - "Hardening HTTP com CORS restrito via allowlist sem wildcards"
  - "Problem Details (RFC 7807/9457) sanitizados para 401 e 403 para o Angular SPA (Accept com application/json/problem+json ou XHR) e rotas /api/**"
  - "Proteção CSRF estrita em mutações HTTP (POST, PUT, DELETE, PATCH)"
  - "Handshake WebSocket restrito à allowlist de origens públicas"
  - "Gate STOMP CONNECT exigindo sessão autenticada (com suporte a Base64 e session IDs crus) e Double-Submit XSRF"
  - "Proibição expressa e case-insensitive de tokens Bearer/Authorization nos frames STOMP"
  - "Proibição de subscrições fora de /user/** e de envio de mensagens de chat na Fase 1"
affects: [security, realtime, websocket, frontend]

actuals:
  tasks: 2
  implementation_paths: 5
  documentation_paths: 2
  mutations_executed: 8
  mutations_effective: 8
  external_review_rounds: 2

tech-stack:
  patterns:
    - "CORS com CorsConfigurationSource explícito e setAllowedOrigins sem wildcard (*)"
    - "Problem Details application/problem+json para AuthenticationEntryPoint e AccessDeniedHandler casando Accept do Angular e /api/**"
    - "Double-Submit XSRF no STOMP CONNECT validando header X-XSRF-TOKEN contra cookie capturado no handshake"
    - "DefaultHandshakeHandler customizado com determineUser para herdar autenticação de sessão no WebSocket"
    - "ChannelInterceptor preSend validando frames STOMP e rejeitando mensagens de negócio na Fase 1"

patterns-established:
  - "O matcher de 401 Problem Details responde a requisições de API (Accept contendo application/json, application/problem+json, text/plain ou XMLHttpRequest e rotas /api/** sem text/html), enquanto navegações diretas do navegador recebem redirecionamento 302 para o Keycloak."
  - "O navegador nunca envia Bearer tokens no STOMP; a sessão HTTP é a única âncora de identidade."
---

# Plano 01-08 — Proteções de fronteira: CSRF, CORS, Problem Details e STOMP CONNECT

`BASE_HEAD`: `f6452ec`. Nada foi commitado; stage, commit e push continuam dependendo de autorização própria.

## Task 1 — Hardening HTTP: CSRF, CORS Allowlist e Problem Details Sanitizados

A Task 1 aplicou o endurecimento da camada HTTP no `SecurityConfiguration.java` sem quebrar o contrato SPA do 01-29:

1. **CORS Allowlist Explícita**:
   - `CorsConfigurationSource` configurado com `${betufu.web.public-origin:http://localhost:4200}`.
   - Proibição absoluta de wildcards (`*`) quando credenciais (`allowCredentials = true`) estão habilitadas.
   - Métodos autorizados restritos.

2. **Problem Details 401 e 403 Sanitizados**:
   - `AuthenticationEntryPoint` para requisições de API (`/api/**` com `Accept: application/json, text/plain, */*` ou `application/problem+json` e XHR) emitindo `401 Unauthorized` com `application/problem+json`, `title: "Unauthorized"`, `detail: "Authentication is required to access this resource"`.
   - `AccessDeniedHandler` emitindo `403 Forbidden` com `application/problem+json`, `title: "Forbidden"`, `detail: "Access denied or invalid CSRF token"`.
   - Nenhuma mensagem de exceção de baixo nível (como nomes de classes, stack traces ou causas internas) é vazada no corpo da resposta.

3. **Validação e Provas de Discriminação**:
   - Criado `CsrfSecurityIT.java` cobrindo ausência de token, token divergente, token válido, origens não permitidas, safe requests (`GET`), chamadas SPA Angular não autenticadas recebendo 401, navegações de browser recebendo 302 para o IdP, proteção do logout e sanitização de respostas.
   - 5 mutações de discriminação foram executadas e observadas vermelhas (RED):
     - *M1*: Desabilitar CSRF -> 7 testes falharam.
     - *M2*: Configurar CORS com wildcard -> falha de origem não autorizada.
     - *M3*: Vazar exceção interna no Problem Details -> falha de asserção de sanitização.
     - *M4*: Ignorar CSRF no logout -> falha no teste de proteção do logout.
     - *M-P1*: Sabotar o matcher de 401 para ignorar `application/json` -> `unauthenticatedApiRequestWithAngularAcceptHeaderReturns401InsteadOf302` falhou com 302 em vez de 401.

## Task 2 — Gate STOMP CONNECT e Hardening de WebSockets

A Task 2 implementou a infraestrutura de tempo real e o controle de segurança no STOMP:

1. **Configuração de WebSocket e STOMP**:
   - Criado `WebSocketConfiguration.java` configurando endpoint `/ws` com allowlist de origens públicas.
   - Handshake HTTP intercepta e armazena os cookies `SESSION` (com suporte a IDs crus e decodificação Base64 do Spring Session) e `XSRF-TOKEN` nos atributos da sessão WebSocket.
   - `DefaultHandshakeHandler.determineUser` customizado para mapear o `SecurityContext` da sessão no `Principal` da conexão WebSocket.
   - Broker simples registrado apenas para filas técnicas (`/queue`) e prefixo de usuário (`/user`), sem tópicos abertos de chat (`/topic`).

2. **Interceptor de Segurança STOMP**:
   - Criado `StompConnectSecurityInterceptor.java`.
   - No frame `CONNECT`:
     - Rejeita explicitamente e de forma case-insensitive qualquer cabeçalho `Authorization` ou `Bearer`.
     - Exige sessão autenticada válida (`Principal` presente e não vazio).
     - Exige header `X-XSRF-TOKEN` e valida contra o cookie `XSRF-TOKEN` obtido no handshake via comparação em tempo constante (`MessageDigest.isEqual`).
   - No frame `SEND`:
     - Rejeita qualquer envio de mensagem de aplicação / negócio com `AccessDeniedException` na Fase 1.
   - No frame `SUBSCRIBE`:
     - Permite apenas destinos técnicos iniciados por `/user/` (ex: `/user/queue/session`), rejeitando qualquer tópico de chat ou destino global compartilhado.

3. **Validação e Provas de Discriminação**:
   - Criado `StompConnectSecurityIT.java` com 9 testes de integração cobrindo todas as fronteiras de segurança.
   - 3 mutações de discriminação foram executadas e observadas vermelhas (RED):
     - *M1*: Desabilitar validação XSRF no CONNECT -> testes falharam.
     - *M2*: Permitir Bearer header -> `rejectsConnectWithBearerOrAuthorizationHeader` falhou.
     - *M3*: Permitir subscrição em `/topic/chat` -> `rejectsSubscriptionsToUnapprovedDestinations` falhou.

## Revisão Externa e Correções Aplicadas

Duas rodadas de revisão externa foram concluídas:

- **P1-1 (Matcher 401 Problem Details)**: Fechado na Rodada 2. O matcher casa `Accept: application/json` do Angular SPA e rotas `/api/**` (sem Accept HTML), garantindo que sessões expiradas retornem `401 Unauthorized` padronizado. Comprovado com o teste `unauthenticatedApiRequestWithAngularAcceptHeaderReturns401InsteadOf302` e mutação `M-P1` observada vermelha.
- **P2-2 (Base64 Decode no Handshake)**: Fechado na Rodada 2. O `XsrfCookieHandshakeInterceptor` suporta decodificação Base64 de session IDs e strings brutas de sessão, com fallback fail-safe.
- **P2-3 (Restrição Estrita de SUBSCRIBE)**: Fechado na Rodada 2. Destinos de subscrição são estritamente restritos a `/user/**`, proibindo `/queue/session` compartilhado.
- **P3-1 (Case-Insensitive Authorization Check)**: Fechado na Rodada 2 via `toNativeHeaderMap()` e verificação case-insensitive de chaves.
- **P3-2 (Sanitização da Mensagem de Erro)**: Fechado na Rodada 2 para mensagem estática sem eco de inputs.
- **P2 Documental (Emenda de PLAN e Destinatário Verificável)**: Fechado na Rodada 2. `01-08-PLAN.md` foi emendado (retirado `walking-skeleton.spec.ts` de `files_modified` e `<files>`), e a seção `## Bloco material adiado` foi criada com destinatário rastreável.

## Bloco Material Adiado — Roteamento do Proxy e E2E no Navegador para STOMP

- **Itens adiados**:
  1. Adição da regra de WebSocket (`/ws` com `ws: true` e repasse de `Origin`) no `frontend/proxy.conf.json`.
  2. Extensão do teste Playwright `walking-skeleton.spec.ts` para conexão WebSocket real no navegador.
- **Justificativa técnica da restrição na Fase 1:**
  - O validador de supply chain e ambiente hermético `scripts/validate-tracer.mjs:775-776` trava as regras do `proxy.conf.json` aos três contextos BFF originais (`^/api(?:/|$)`, `^/oauth2(?:/|$)`, `^/login(?:/|$)`), reprovando com `BROWSER_PROXY` diante de qualquer path adicional.
  - Como `validate-tracer.mjs` não pertence à allowlist do 01-08 e não há telas ou mensagens de chat ativas na Fase 1, o gate de segurança do backend foi completamente validado e provado via Testcontainers in-process em `StompConnectSecurityIT` (9/9 testes GREEN).
- **Destinatário formal rastreável:**
  - **No ROADMAP:** Fase 7 do ROADMAP (`Phase 7: Comunidade Moderada e Prontidão do Piloto`), sob os requisitos de chat em tempo real por partida (`CHAT-01`/`CHAT-02`), onde o proxy WebSocket e as telas com conexão STOMP serão implementados e validados no navegador.
  - **No STATE.md:** Entrada formal de pendência sob "Limitações conhecidas e adiamentos ratificados da Fase 1".

## Verificação Final

- Suíte de testes backend: 42 testes de unidade (Surefire) + 68 testes de integração com Testcontainers (Failsafe) = **110 testes, BUILD SUCCESS**.
- `node scripts/validate-tracer.mjs`: **PASS em todos os 8 critérios**.
- `git diff --check`: 0 erros de formatação.
- Auditoria de escopo: arquivos alterados estritamente dentro da allowlist do Plano 01-08.
