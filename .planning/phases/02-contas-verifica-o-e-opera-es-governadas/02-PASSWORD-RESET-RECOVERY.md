# Runbook Operacional: Recuperação e Resiliência da Redefinição de Senha (D-40)

## 1. Identificação e Responsabilidades Operacionais

| Papel | Responsável | Atribuições |
|---|---|---|
| **Owner Principal** | Administrador Geral (Responsável pelo Projeto BETUFU / `ADMINISTRADOR_GERAL`) | Governança geral da integração D-40, autorização de rotação de chaves HMAC, decisão sobre expurgo de falhas persistentes e liberação do checkpoint de ativação. |
| **Backup Operacional** | Administrador Geral Substituto / Suporte N2 de Infraestrutura | Monitoramento de alertas de entrega, execução de procedimentos de reprocessamento (replay) de eventos `FAILED` e triagem de indisponibilidade de serviços dependentes (PostgreSQL, Redis, Keycloak). |
| **Escalonamento** | Equipe de Engenharia / Operação de Plataforma | Atendimento a incidentes críticos (ex.: falha sistêmica de término de sessões, vazamento suspeito de chave HMAC). |

---

## 2. Arquitetura da Integração D-40 (Keycloak Outbox → BETUFU Inbox)

A integração de encerramento compulsório de sessões após redefinição de senha (`D-40`, `AUTH-04`, `IDEN-05`, `PRIV-01`) conecta a origem de autenticação ao destino da plataforma através de um padrão transacional de outbox/inbox desacoplado:

```
[Keycloak IdP]
      │
      ▼ (evento UPDATE_CREDENTIAL / RESET_PASSWORD)
[Tabela public.betufu_password_reset_outbox]
      │
      ▼ (worker com claim/lease e backoff)
[PasswordResetOutboxDispatcher]
      │
      ▼ (HTTP POST assinado com HMAC-SHA256, path /api/v1/internal/events/password-reset)
[PasswordResetEventEndpoint] (Cadeia isolada: STATELESS, sem cookie/sessão, verificação HMAC)
      │
      ▼ (canonicalização literal, chave current/previous, janela ±5 min)
[PasswordResetSignedEnvelopeVerifier]
      │
      ▼ (insert-first com unicidade por source_event_id no PostgreSQL)
[PasswordResetEventInboxService] ──► [public.password_reset_event_inbox]
      │
      ▼ (motivo SECURITY)
[IdentitySessionTerminationService] ──► [Redis / Sessões BFF encerradas]
```

### 2.1 Contrato de Transporte M2M
- **Endpoint:** `/api/v1/internal/events/password-reset`
- **Método HTTP:** `POST`
- **Cabeçalhos Obrigatórios:**
  - `X-Key-Id`: Identificador da versão de chave HMAC utilizada (ex.: `v1`, `v2`);
  - `X-Timestamp`: Instante ISO-8601 UTC da assinatura (ex.: `2026-08-23T19:00:00Z`);
  - `X-Signature`: Digest HMAC-SHA256 codificado em Base64;
  - `X-Source-Event-Id`: Identificador único de correlação do evento emitido pela outbox.
- **Corpo da Mensagem:** JSON canônico contendo `sourceEventId`, `subject`, `realmId`, `eventType` e `timestamp`.
- **Cadeia de Segurança do Endpoint M2M:** Implementada em `PasswordResetEventSecurityConfiguration.java`. Permanece com matcher restrito e exato, `SessionCreationPolicy.STATELESS`, sem CSRF (não se aplica a M2M sem credenciais de navegador), e recusa terminantemente qualquer autenticação por sessão ou cookie.

---

## 3. Rotação de Chaves HMAC e Janela de Replay (D-40)

### 3.1 Especificação e Guarda das Chaves
- **Algoritmo:** HMAC-SHA256 (`HmacSHA256`).
- **Entropia Mínima:** 32 bytes (256 bits) decodificados em Base64.
- **Guarda:** Injetadas exclusivamente via variáveis de ambiente (`BETUFU_SESSION_PASSWORD_RESET_KEYS_*`), nunca persistidas no PostgreSQL e nunca rastreadas no Git.
- **Suporte Multi-Versão Estrito:** O destino aceita exatamente duas versões ativas: **chave corrente (`current`)** e **chave anterior (`previous`)**. Qualquer requisição com `keyId` não mapeado é rejeitada de imediato (*fail-closed*).

### 3.2 Janela de Tolerância Temporal (Anti-Replay)
- O verificador de envelope (`PasswordResetSignedEnvelopeVerifier`) impõe uma janela de tolerância máxima estrita de **±5 minutos (±300 segundos)** entre o cabeçalho `X-Timestamp` e o relógio autoritativo do backend.
- Envelopes com timestamp adiantado ou atrasado em mais de 5 minutos são sumariamente rejeitados antes de qualquer consulta ou escrita no inbox.

### 3.3 Protocolo de Rotação de Chave em Duas Etapas
1. **Etapa 1 (Adição Passiva):**
   - Configurar a nova chave na variável correspondente à nova versão (ex.: `v2`).
   - Mapear `v2` nas propriedades da aplicação mantendo `current-version=v1` e `previous-version=v1`.
   - Realizar deploy; a aplicação valida e carrega ambas as chaves.
2. **Etapa 2 (Promoção da Nova Chave):**
   - No Keycloak e no Backend, promover a versão corrente para `v2` e a anterior para `v1`:
     - `BETUFU_SESSION_PASSWORD_RESET_CURRENT_VERSION=v2`
     - `BETUFU_SESSION_PASSWORD_RESET_PREVIOUS_VERSION=v1`
   - Reiniciar os serviços e verificar resposta HTTP 200 nos healthchecks.

---

## 4. Resiliência, Idempotência e Política de Retenção

### 4.1 Idempotência e Arbitragem no Banco
- A tabela `public.password_reset_event_inbox` possui constraint `UNIQUE` na coluna `source_event_id`.
- Reenvios do mesmo evento (duplicata de rede ou replay legítimo) são capturados pela restrição única (`insertIfAbsent` retorna 0). O serviço responde com ACK idempotente sem disparar um segundo encerramento compulsório de sessões.

### 4.2 Crash Consistency e Lease de Recuperação
- Caso ocorra interrupção abrupta (crash/restart do processo) após a inserção do evento e antes da conclusão do encerramento de sessões, o registro permanece no estado `PENDING` ou `CLAIMED`.
- O `PasswordResetEventMaintenanceJob` aciona `recoverPendingEvents` em lotes de no máximo 100 registros. O claim é commitado em transação curta antes do efeito externo; após 5 minutos, outro worker pode retomar um claim abandonado. Como PostgreSQL e Redis/IdP não compartilham transação, a garantia é **at-least-once**: o encerramento precisa permanecer idempotente.
- O driver nasce fail-closed com `betufu.session.password-reset.maintenance-enabled=false` implícito. Somente o checkpoint 02-29 pode habilitá-lo por ambiente.

### 4.3 Política Estrita de Retenção e Expurgos
- **Registros `DELIVERED`:** Mantidos para fins de auditoria e resolução de duplicatas por **30 dias**. Quando o driver for habilitado pelo 02-29, registros entregues anteriores a 30 dias (`delivered_at < now - 30d`) serão removidos pela rotina `cleanupDeliveredOlderThan`.
- **Registros `FAILED`:** **Nunca são apagados automaticamente.** Permanecem indefinidamente na base de dados até que um operador autorizado realize o diagnóstico, execute o reprocessamento ou declare resolução manual.
- **Registros `PENDING` / `CLAIMED`:** Preservados para recuperação automática de workers.
- **Monitoramento:** o mesmo ciclo atualiza a métrica de baixa cardinalidade `betufu.password_reset.inbox.failed` e emite aviso operacional quando o total de `FAILED` for maior que zero, sem incluir `sourceEventId`, subject ou payload como tag.

---

## 5. Procedimento Operacional de Recovery (Recovery Humano)

### 5.1 Endpoint Administrativo
A consulta e o reprocessamento manual de eventos no estado `FAILED` são expostos através do controller `PasswordResetEventRecoveryController.java` sob o prefixo `/api/v1/operations/password-reset-events/`.

- **Cadeia de Segurança:** Executa na `SecurityFilterChain` principal do OIDC (com sessão autenticada e CSRF ativo).
- **Duas Guardas Independentes de Autorização:**
  1. **Guarda 1 (Filtro):** Matcher da cadeia de segurança `.requestMatchers("/api/v1/operations/password-reset-events/**").hasRole("ADMINISTRADOR_GERAL")`.
  2. **Guarda 2 (Controller):** O controller invoca diretamente o contrato `OperationalIdentityLookup` em tempo de execução, verificando se o par `(issuer, subject)` do token autenticado possui concessão ativa global de `ADMINISTRADOR_GERAL` (`scopeRef == null`). Concessões escopadas ou ausência de papel resultam em HTTP 403 Forbidden.

### 5.2 Não Vazamento de Dados e Trilha de Auditoria
- O `identityId` do operador é extraído estritamente do `OperationalIdentityLookup` baseado no ID token OIDC, **nunca** de parâmetros ou corpos de requisição enviados pelo cliente.
- Toda operação de reprocessamento grava um registro na tabela `public.audit_event` (`ACTION_CONSULTA_OPERACIONAL`, `OUTCOME_PERMITTED`) contendo o `actorIdentityId`, o `sourceEventId` e o status final.
- **Proibição Rígida:** Nenhum segredo, senha, cookie, token de sessão ou dado pessoal do titular é exposto no corpo da resposta ou gravado em arquivos de log.

---

## 6. Monitoramento, Alertas e Diagnóstico

### 6.1 Alertas Operacionais Configurados
1. **Alerta de Falha na Origem (Keycloak Outbox):** Emitido na **5ª tentativa consecutiva** de entrega sem sucesso (`attempt_count >= 5`).
2. **Alerta de Eventos FAILED no Inbox:** Disparado quando a consulta de monitoramento detecta registros com status `FAILED` na tabela `public.password_reset_event_inbox` (`countByStatus('FAILED') > 0`).
3. **Alerta de Downstream Indisponível (`TERMINATION_FAILED`):** Indica que a conexão com o Redis ou o serviço de sessão falhou durante o encerramento compulsório.

### 6.2 Procedimento Passo a Passo de Diagnóstico e Reprocessamento
Quando um alerta de evento `FAILED` for recebido pelo operador:

1. **Passo 1 — Autenticação como Administrador Geral:**
   - O operador deve efetuar login na plataforma BETUFU com uma conta detentora do papel global `ADMINISTRADOR_GERAL`.
2. **Passo 2 — Listagem de Eventos com Falha:**
   - Executar uma requisição autenticada:
     ```http
     GET /api/v1/operations/password-reset-events/failed
     Accept: application/json
     ```
   - O retorno conterá a lista de eventos pendentes de resolução com o código de erro registrado (ex.: `TERMINATION_FAILED`).
3. **Passo 3 — Verificação de Saúde dos Serviços Dependentes:**
   - Verificar conectividade do Redis de sessões e do PostgreSQL da aplicação.
   - Confirmar que `/actuator/health` responde com status `UP`.
4. **Passo 4 — Disparo de Reprocessamento Autorizado (Replay):**
   - Executar o POST de reprocessamento informando o `sourceEventId` alvo:
     ```http
     POST /api/v1/operations/password-reset-events/{sourceEventId}/reprocess
     Accept: application/json
     X-XSRF-TOKEN: <token_csrf_obtido_do_cookie>
     ```
   - Resposta esperada: HTTP 200 com corpo `{"sourceEventId":"...","status":"DELIVERED","effectExecuted":true,"terminatedSessionCount":N,"actorIdentityId":"...","processedAt":"..."}`.
5. **Passo 5 — Confirmação e Fechamento do Incidente:**
   - Verificar que a sessão antiga do usuário afetado foi destruída no Redis.
   - Confirmar que o evento passou para o estado `DELIVERED` no banco e que a trilha de auditoria foi gerada.

---

## 7. Matriz de Escalonamento e Contingência

| Cenário de Falha | Ação Imediata | Responsável | Nível de Severidade |
|---|---|---|---|
| Falha pontual de reprocessamento em evento isolado | Reexecutar após validação de conectividade do Redis | Suporte N2 / Backup Operacional | Baixa (P3) |
| Acúmulo sustentado de eventos `FAILED` (> 10 eventos em 15 min) | Investigar saturação ou timeout do cluster Redis / PostgreSQL | Suporte N2 + Engenharia | Média (P2) |
| Suspeita de dessincronização de relógio (> 5 min) | Verificar status do serviço NTP nos nós de backend e Keycloak | Engenharia de Infraestrutura | Alta (P1) |
| Suspeita de comprometimento da chave HMAC | Executar procedimento de rotação emergencial e abertura de janela de reinformação (conforme `02-CHAVE-HMAC-PROCEDIMENTO.md`) | Owner Principal | Crítica (P0) |

---

## 8. Checkpoint de Ativação e Prontidão de Produção (Plano 02-29)

- **Estado Atual:** A integração D-40, o dispatcher do Keycloak e as propriedades de runtime permanecem **estritamente desabilitados por padrão** em ambientes produtivos.
- **Gate de Ativação (Plano 02-29):** A ativação final em produção ocorrerá exclusivamente após a conclusão do Plano 02-29, que submeterá a integração a uma suíte abrangente de testes de integração com Keycloak real (incluindo validação de assinatura com chaves reais, expiração forçada, issuer inválido e autorização via cliente `realm-management`).
