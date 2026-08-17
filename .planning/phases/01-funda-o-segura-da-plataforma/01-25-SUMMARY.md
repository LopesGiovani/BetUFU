# Plano 01-25 — Exposição de Health e Métricas Operacionais Mínimos e Seguros

## 1. Contexto e Objetivo

O Plano **01-25** estabelece a governança de observabilidade no nível da JVM e Actuator para a plataforma BETUFU, garantindo que métricas e indicadores de saúde operacionais não exponham credenciais, tokens, topologia de infraestrutura ou dados de alta cardinalidade.

---

## 2. Reconciliação e Ratificação de Escopo

Conforme a allowlist estrita deste plano (3 arquivos no pacote `br.com.betufu.platform.observability`):
- Os arquivos `pom.xml`, `application.yaml` e `SecurityConfiguration.java` permanecem intocados.
- **Prometheus / OTLP:** Não constam como dependências no classpath atual da Fase 1 e nenhum endpoint HTTP `/actuator/prometheus` é exposto publicamente. A proteção de telemetria é aplicada diretamente sobre os medidores da JVM no `MeterRegistry` através do `SafeMetricsConfiguration`.
- **Liveness vs Readiness:** O endpoint público para sondagem de saúde operacional da plataforma é exclusivamente `/actuator/health/liveness` (200 OK, `{"status":"UP"}` sem vazamento de detalhes de infraestrutura), enquanto o endpoint `/actuator/health` bruto e os demais endpoints do Actuator (`env`, `configprops`, `heapdump`, `beans`, `metrics`) permanecem estritamente bloqueados por autorização contra chamadas anônimas.

---

## 3. Entregas Realizadas

1. **[`SafeMetricsConfiguration.java`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/SafeMetricsConfiguration.java):**
   - Registra `MeterFilter` para suprimir tags de alta cardinalidade ou sensíveis (`correlationId`, `userId`, `user`, `username`, `sub`, `email`, `token`, `password`, `secret`, `cookie`, `session`).
   - Aplica `LogSanitizer.sanitize(...)` a valores de tags restantes.
   - Aplica `limitHttpServerRequestsUriCardinality` com teto de 100 valores únicos para a tag `uri` em `http.server.requests`.

2. **[`PlatformHealthConfiguration.java`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/main/java/br/com/betufu/platform/observability/PlatformHealthConfiguration.java):**
   - Registra `HealthIndicator` seguro com nome da plataforma (`BETUFU`), sem dependências ou conexões que exponham a infraestrutura no liveness.

3. **[`HealthMetricsIT.java`](file:///c:/Users/giova/Desktop/BetUFU/backend/src/test/java/br/com/betufu/platform/observability/HealthMetricsIT.java):**
   - 6 testes de integração cobrindo:
     - Liveness público e seguro.
     - Bloqueio (401 Unauthorized Problem Details) para `/actuator/health` bruto, `/actuator/env`, `/actuator/configprops`, `/actuator/heapdump`, `/actuator/beans`, `/actuator/metrics`.
     - Preservação da privacidade no `PlatformHealthIndicator`.
     - Remoção de tags de alta cardinalidade no `MeterRegistry`.
     - Sanitização de valores de tags via `LogSanitizer`.
     - Teto de 100 tags em `http.server.requests`.

---

## 4. Provas de Discriminação Executadas

- **M-25A:** Bypass de chaves sensíveis em `SafeMetricsConfiguration` → **VERMELHO** (`meterFilterStripsHighCardinalityAndSensitiveTags` falhou).
- **M-25B:** Bypass de `LogSanitizer` em tags → **VERMELHO** (`meterFilterSanitizesTagValues` falhou).
- **M-25C:** Injeção de credencial em `PlatformHealthIndicator` → **VERMELHO** (`platformHealthIndicatorIsSafe` falhou).
- **M-25D:** Aumento do teto `MAX_CARDINALITY_TAGS` de 100 para 200 → **VERMELHO** (`meterFilterCapsHttpUriCardinality` falhou com 105 counters).
