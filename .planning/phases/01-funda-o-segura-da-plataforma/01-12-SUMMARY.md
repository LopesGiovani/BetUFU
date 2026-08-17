---
phase: 01-funda-o-segura-da-plataforma
plan: "12"
subsystem: api-observability
tags: [problem-details, rfc9457, correlation-id, logging, logback, sanitization, mdc]

requires:
  - phase: 01-24
    provides: "Estrutura do módulo platform e isolamento de segurança"
provides:
  - "Tratamento centralizado de erros em RFC 9457 Problem Details (ApiErrorCode, FieldViolation, ProblemDetailsHandler)"
  - "Filtro Servlet de correlação X-Correlation-ID com injeção no MDC e isolamento de threads (CorrelationIdFilter)"
  - "Sanitizador centralizado de dados sensíveis e caracteres de controle (LogSanitizer)"
  - "Conversores e Encoders Logback para sanitização automática em console e JSON estruturado (SanitizingMessageConverter, SanitizingExtendedThrowableProxyConverter, SanitizingJsonEncoder)"
  - "Configuração Logback multi-perfil com fallback robusto (logback-spring.xml)"
affects: [01-15, 01-26, api, observability]

actuals:
  tasks: 2
  implementation_paths: 11
  mutations_executed: 11
  mutations_effective: 11

tech-stack:
  added: []
  patterns:
    - "Problem Details RFC 9457 com códigos e ações em português sem expor stack traces ou mensagens internas"
    - "Ordenação determinística de violações de campos (fieldErrors) por nome de campo"
    - "Filtro de correlação @Order(HIGHEST_PRECEDENCE + 10) garantindo MDC ativo antes da cadeia de segurança"
    - "Sanitização de logs em nível de layout/encoder do Logback cobrindo mensagens, argumentos, MDC, pares chave-valor e exceções (%wEx)"

patterns-established:
  - "Logs estruturados em JSON e console devem passar por wrappers de encoder/converter para evitar que logging parametrizado ou stack traces vazem credenciais."
  - "O Correlation ID deve ser estritamente validado contra caracteres de controle e comprimento antes de ser aceito no MDC."
  - "Erros de segurança (401/403) devem ter rethrow explícito no catch-all de exceptions do @RestControllerAdvice para preservar a semântica do Spring Security."
---

# Plano 01-12 — Problem Details Centralizado e Observabilidade com Correlação Segura

`BASE_HEAD`: `3c79651`.

## Resumo da Execução

O Plano 01-12 entregou a infraestrutura de tratamento de erros padronizado (RFC 9457) e observabilidade estruturada com rastreamento de requisições e mitigação completa contra vazamento de credenciais em logs (Threat T-01-12A).

### 1. Task 1 — RFC 9457 Problem Details Centralizado (`platform.api`)
- Implementado [`ApiErrorCode`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/api/ApiErrorCode.java) com códigos estáveis, títulos, detalhes e ações sugeridas em português.
- Implementado [`FieldViolation`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/api/FieldViolation.java), record com ordenação alfabética determinística por nome do campo (D-15) e sem eco de dados sensíveis.
- Implementado [`ProblemDetailsHandler`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/api/ProblemDetailsHandler.java), `@RestControllerAdvice` estendendo `ResponseEntityExceptionHandler`, com sanitização de caracteres de controle Unicode (D-16), enriquecimento com correlation ID do MDC e rethrow explícito de `AccessDeniedException` e `AuthenticationException`.
- Criado [`ProblemDetailsIT`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/test/java/br/com/betufu/platform/api/ProblemDetailsIT.java) com 7 testes de integração provando validações, erros 405, 500 sem vazamento de stack/detalhes internos, injeção de correlation ID e sanitização Unicode.

### 2. Task 2 — Correlação e Observabilidade com Sanitização de Logs (`platform.observability`)
- Implementado [`CorrelationIdFilter`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/CorrelationIdFilter.java), filtro Servlet `@Order(HIGHEST_PRECEDENCE + 10)` com validação ASCII 1-64 caracteres, geração de UUID seguro, injeção no MDC e eco no cabeçalho `X-Correlation-ID` da resposta, com limpeza estrita no `finally` para evitar contaminação entre threads.
- Implementado [`LogSanitizer`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/LogSanitizer.java), utilitário estático regex-based contra Bearer tokens, cookies, senhas, chaves e controle Unicode.
- Implementados conversores e encoders Logback:
  - [`SanitizingMessageConverter`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/SanitizingMessageConverter.java) para `%msg`.
  - [`SanitizingExtendedThrowableProxyConverter`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/SanitizingExtendedThrowableProxyConverter.java) para `%wEx` (stack traces e exceções).
  - [`SanitizingJsonEncoder`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/SanitizingJsonEncoder.java), encoder que decora `ILoggingEvent` e `IThrowableProxy` sanitizando mensagens, argumentos parametrizados, pares chave-valor e contexto MDC em JSON estruturado nativo.
- Configurado [`logback-spring.xml`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/resources/logback-spring.xml) com appender human-readable para `!runtime & !integrated` e JSON estruturado sanitizado para `runtime | integrated`.
- Criado [`ObservabilityIT`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/test/java/br/com/betufu/platform/observability/ObservabilityIT.java) com 12 testes de integração cobrindo todas as EDGEs OPS-03, concorrência de correlation ID, isolamento de MDC e asserção de sanitização ponta a ponta em JSON e stack traces.

---

## Mutações de Discriminação Executadas (11/11 Comprovadas)

| Mutação | Alvo | Comportamento Induzido | Resultado |
|---|---|---|---|
| **M1** | `ProblemDetailsHandler.java` | Remoção do `@RestControllerAdvice` | **VERMELHO** (7 falhas) |
| **M2** | `ProblemDetailsHandler.java` | Vazamento de `ex.getMessage()` no 500 | **VERMELHO** (3 falhas) |
| **M3** | `ProblemDetailsHandler.java` | Remoção de `.sorted()` em `fieldErrors` | **VERMELHO** (1 falha) |
| **M4** | `ProblemDetailsHandler.java` | Omissão da leitura de `MDC` para correlationId | **VERMELHO** (1 falha) |
| **M5** | `CorrelationIdFilter.java` | Omissão de `MDC.remove()` no `finally` | **VERMELHO** (1 falha) |
| **M6** | `CorrelationIdFilter.java` | Aceite de `X-Correlation-ID` sem validação regex | **VERMELHO** (2 falhas) |
| **M7** | `CorrelationIdFilter.java` | Omissão de `response.setHeader` | **VERMELHO** (6 falhas + 1 erro) |
| **M8** | `LogSanitizer.java` | Retorno de string crua sem redaction | **VERMELHO** (1 falha) |
| **M8b** | `SanitizingMessageConverter.java` | Bypass de sanitização no converter de mensagem | **VERMELHO** (1 falha) |
| **M8c** | `SanitizingJsonEncoder.java` | Bypass de sanitização no encoder JSON | **VERMELHO** (1 falha) |
| **M8d** | `SanitizingExtendedThrowableProxyConverter.java` | Bypass de sanitização no converter de exceção | **VERMELHO** (1 falha) |

---

## Ratificações e Reconciliação da Allowlist

1. **`backend/openapi/betufu.yaml`:** A inclusão do schema RFC 9457 de Problem Details foi ratificada e formalmente atribuída à Task 2 do Plano 01-26 (onde todas as superfícies de contrato OpenAPI são convergidas e validadas), registrado no `STATE.md`.
2. **`backend/src/main/resources/application.yaml`:** Dispensada alteração em prol da separação de responsabilidades, isolando a configuração de logging no `logback-spring.xml`.
3. **`backend/src/main/java/br/com/betufu/platform/observability/package-info.java`:** Criado para documentação e integridade do subpacote no Spring Modulith.

---

## Verificação Final

- **Surefire:** 9 classes / 57 testes (0 falhas, 0 erros, 0 ignorados).
- **Failsafe:** 14 classes / 95 testes (0 falhas, 0 erros, 0 ignorados).
- **Node Test Suite:** 481/481 testes passando.
- **Revisão Independente:** Aprovada com **VERDICT: PASS** (zero P0–P2).
