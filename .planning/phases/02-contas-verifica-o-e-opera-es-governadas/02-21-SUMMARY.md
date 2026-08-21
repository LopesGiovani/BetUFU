---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 21
subsystem: audit-security
tags: [postgresql, flyway, spring-data-jpa, audit, least-privilege]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Decisões aprovadas sobre campos, segregação, retenção e reconciliação da auditoria
provides:
  - Trilha append-only com vocabulário fechado e idempotência opcional
  - Identidades PostgreSQL distintas para Flyway e runtime
  - Duas barreiras contra alteração, por privilégio e por trigger
  - Procedimento de acesso, retenção, reconciliação e incidente D-37
affects: [02-07, 02-10, 02-12, 02-17, 02-23, AUDT-01, AUDT-02]

actuals:
  tokens: 27632
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - API pública mínima do módulo e persistência escondida em pacote internal
    - Papel migrador dono do schema e papel runtime limitado por GRANT explícito
    - Imutabilidade em profundidade por privilégio e trigger PostgreSQL

key-files:
  created:
    - backend/src/main/resources/db/migration/V4__create_audit_event.sql
    - backend/src/main/java/br/com/betufu/audit/AuditEntry.java
    - backend/src/main/java/br/com/betufu/audit/AuditRecorder.java
    - backend/src/main/java/br/com/betufu/audit/internal/AuditService.java
    - backend/src/test/java/br/com/betufu/audit/AuditEventIT.java
    - backend/src/test/java/br/com/betufu/audit/AuditImmutabilityIT.java
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-AUDITORIA-PROCEDIMENTO.md
  modified:
    - backend/src/main/resources/application.yaml
    - infra/postgres/init-databases.sh
    - infra/compose.yaml
    - scripts/validate-runtime-prerequisites.mjs
    - backend/src/main/java/br/com/betufu/identity/internal/BetufuProfileService.java

key-decisions:
  - "audit_event nunca armazena número de matrícula ou HMAC; ações de matrícula usam somente referência e versão."
  - "Flyway e runtime usam papéis e senhas distintos; o runtime recebe somente SELECT e INSERT em audit_event."
  - "Eventos permanecem por toda a vida do ambiente no MVP fechado; PRIV-05 bloqueia lançamento público sem política definitiva."
  - "Instantes de mudança de apelido são limitados inferiormente por created_at para tolerar pequena diferença entre relógios do host e do banco."

requirements-completed: [AUDT-01, AUDT-02]

coverage:
  - id: D1
    description: Eventos permitidos e bloqueados são gravados com vocabulário fechado, correlação e idempotência sem matrícula ou HMAC.
    requirement: AUDT-01
    verification:
      - kind: integration
        ref: AuditEventIT
        status: pass
      - kind: integration
        ref: AuditImmutabilityIT#databaseRejectsActionOutsideClosedVocabulary
        status: pass
    human_judgment: false
  - id: D2
    description: O runtime lê e insere, mas não altera, remove, trunca ou cria objetos; o migrador também é bloqueado pelo trigger.
    requirement: AUDT-02
    verification:
      - kind: integration
        ref: AuditImmutabilityIT
        status: pass
      - kind: integration
        ref: FlywayMigrationIT
        status: pass
    human_judgment: false
  - id: D3
    description: O procedimento define acesso agregado, retenção do MVP, reconciliação e resposta ao comprometimento D-37.
    requirement: AUDT-02
    verification:
      - kind: manual_procedural
        ref: 02-AUDITORIA-PROCEDIMENTO.md#revisão-codex
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 21: Trilha de auditoria append-only

**A BETUFU agora possui uma trilha PostgreSQL de vocabulário fechado, gravável pelo runtime sem capacidade de alteração e acompanhada por um procedimento explícito de retenção e reconciliação.**

## Accomplishments

- Criou `public.audit_event` com idempotência opcional, referências de ator/alvo, resultados permitido/bloqueado e nenhuma coluna de matrícula ou HMAC.
- Publicou `AuditRecorder` como fronteira mínima do módulo; entidade e repositório permanecem internos.
- Separou as credenciais do Flyway das credenciais do runtime no Spring e no Compose.
- Provou `SELECT`/`INSERT` e recusas de `UPDATE`, `DELETE`, `TRUNCATE` e DDL, além do trigger que também bloqueia o dono migrador.
- Documentou acesso agregado, reconciliação, retenção do MVP e o incidente de chave D-37.

## Decisions Made

- O evento guarda estados e referências operacionais, nunca a matrícula nem seu digest.
- A identidade migradora é dona do banco/schema; a identidade da aplicação recebe privilégios mínimos e distintos.
- A retenção cobre toda a vida do ambiente fechado. Uma política jurídica por classe de dado continua obrigatória antes do lançamento público.
- Diferença de poucos milissegundos entre Windows e container não pode fazer `updated_at` retroceder em relação a `created_at`.

## Deviations from Plan

### Auto-fixed Issues

**1. Cinco ITs legados não provisionavam a nova configuração obrigatória do papel runtime**

- **Issue:** a V4 falhava fechado quando esses containers antigos não definiam `betufu.runtime_role`; depois, o novo FK exigiu incluir `audit_event` no cleanup.
- **Fix:** foi criado `postgres-audit-runtime-role.sql`, aplicado somente nos cinco containers legados, e seus truncates passaram a limpar as três tabelas em ordem explícita.
- **Verification:** os cinco ITs passaram `44/44`, e o Maven completo passou `161/161` integrações.

**2. A suíte completa revelou diferença real entre relógios do host e do PostgreSQL**

- **Issue:** o banco criou `created_at` 4,5 ms à frente do `Instant.now()` do Java; uma troca rápida de apelido violou `updated_at >= created_at`.
- **Fix:** `BetufuProfileService` usa o maior instante entre `Instant.now()` e `created_at`; uma regressão simula o banco um minuto à frente.
- **Verification:** `ProfileControllerIT` passou `10/10`, e o Maven completo permaneceu verde.

**3. O detector textual do plano confundia ação pública com coluna sensível**

- **Issue:** procurar `matricula` em toda a migration reprovava legitimamente `REGISTRO_MATRICULA` e `CORRECAO_MATRICULA`.
- **Fix:** o gate foi restringido a declarações de coluna contendo matrícula, enrollment ou HMAC.
- **Verification:** gate de coluna passou; V4 conservou SHA-256 `B95A43BA32E5084E6139EDFF05D848CB2EFE3BCFB59C6A634FE76BFD3233BA8D`.

**Total deviations:** 3 correções necessárias de compatibilidade, consistência temporal e qualidade do detector; nenhuma dependência ou superfície de produto foi adicionada.

## Issues Encountered

- O `agy` foi tentado primeiro, mas recusou chamadas de terminal em execução não interativa; o Codex executou o fallback autorizado.
- A primeira suíte completa não iniciou testes porque um PowerShell órfão do GSD consumia 6,23 GB de RAM. Somente o PID validado foi encerrado; a memória livre subiu de 0,60 GB para 7,87 GB.
- Avisos conhecidos de LF/CRLF, Mockito/Byte Buddy, Logback e reconexão Lettuce no teardown não causaram falhas.

## Verification Evidence

- Mutações observadas vermelhas: remoção do trigger e inclusão de ação fora do vocabulário; V4 restaurada pelo hash esperado.
- Maven completo: Surefire `172/172`; Failsafe `161/161`; zero falhas, erros ou skips.
- Node completo: `536/536`.
- V1, V2 e V3 byte a byte inalteradas contra `HEAD`.
- Gate de colunas sensíveis e `git diff --check`: aprovados.
- Revisão Codex do snapshot final: sem achado bloqueante.

## User Setup Required

- Antes de iniciar o Compose local, criar `infra/.env` a partir de `.env.example` com `BETUFU_FLYWAY_USER` e `BETUFU_FLYWAY_PASSWORD`, distintos do runtime.
- Um volume PostgreSQL já inicializado não recebe o novo papel pelo init script retroativamente. Reprovisionar/resetar o ambiente local ou criar os papéis manualmente exige uma ação separada; nenhuma dessas operações destrutivas foi executada neste plano.

## Next Phase Readiness

- `AuditRecorder` está pronto para os consumidores de matrícula, papéis e painel operacional.
- A Wave 3 está tecnicamente concluída no worktree; integração, stage, commits e limpeza dependem das aprovações Git separadas.
- O plano ainda não possui commit e nenhum push foi realizado.

## Self-Check: PASSED

- Todos os arquivos declarados existem e os desvios foram incorporados ao allowlist.
- As contagens foram extraídas dos XML de Surefire/Failsafe e do resumo TAP do Node.
- O índice Git permaneceu vazio; nenhuma operação de stage, commit ou push ocorreu.
- O HEAD técnico permaneceu em `5b1320f4cbf355135a7e3e8d794009c77f83b494`.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
