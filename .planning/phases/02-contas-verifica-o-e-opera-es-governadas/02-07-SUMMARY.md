---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 07
subsystem: verification
tags: [java, spring-boot, postgresql, redis, hmac, audit]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: Normalização e HMAC versionado, auditoria append-only e proteção estrutural de privacidade
provides:
  - Registro e consulta do vínculo voluntário da matrícula UFU sem persistir o número original
  - Unicidade por conta e por matrícula, com busca sob todas as versões HMAC ativas
  - Limite atômico de tentativas por conta e origem, com auditoria sanitizada
  - Predicado público de elegibilidade para fases consumidoras
affects: [02-08, 02-16, 02-24, VERI-06, VERI-07, VERI-09, AUDT-01]

actuals:
  tasks: 3
  commits: 1

tech-stack:
  added: []
  patterns:
    - Reserva atômica de dois limites no Redis por script Lua
    - Digest binário de 32 bytes acompanhado por versão numérica da chave
    - Índices únicos parciais como árbitro final de concorrência no PostgreSQL

key-files:
  created:
    - backend/src/main/resources/db/migration/V5__create_ufu_enrollment.sql
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentService.java
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentAttemptLimiter.java
    - backend/src/main/java/br/com/betufu/verification/internal/EnrollmentController.java
    - backend/src/main/java/br/com/betufu/verification/EnrollmentEligibility.java
  modified:
    - backend/src/main/java/br/com/betufu/platform/session/ValidActivityFilter.java
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java

key-decisions:
  - "Limites aprovados: cinco recusas por conta em quinze minutos e cinquenta por origem em uma hora."
  - "Recusas de negócio consomem a reserva; sucesso e falhas técnicas liberam a reserva."
  - "A origem usa getRemoteAddr(), após a política existente de proxies confiáveis."

requirements-completed: [VERI-06, VERI-07, VERI-09, AUDT-01]

coverage:
  - id: D1
    description: O vínculo persiste somente digest binário, versão e estado, com unicidade por identidade e matrícula.
    requirement: VERI-06; VERI-07
    verification:
      - kind: integration
        ref: "UfuEnrollmentUniquenessIT e FlywayMigrationIT"
        status: pass
    human_judgment: false
  - id: D2
    description: Registro, consulta, busca multiversão, limites duplos e auditoria funcionam sem revelar dado privado.
    requirement: VERI-07; AUDT-01
    verification:
      - kind: integration
        ref: "EnrollmentControllerIT e EnrollmentAttemptLimiterIT"
        status: pass
    human_judgment: false
  - id: D3
    description: Consumidores consultam somente o predicado público de matrícula ativa.
    requirement: VERI-09
    verification:
      - kind: unit
        ref: "EnrollmentEligibilityTest"
        status: pass
    human_judgment: false

completed: 2026-08-22
status: complete
---

# Phase 2 Plan 07: Registro governado da matrícula UFU

**Uma pessoa autenticada agora pode registrar e consultar o vínculo da matrícula, enquanto a BETUFU guarda somente um HMAC versionado e bloqueia duplicidades sem expor o dado privado.**

## Accomplishments

- Criou a migration V5 com digest binário de 32 bytes, versão numérica, estado e dois índices únicos parciais.
- Implementou `GET` e `PUT` do vínculo, pesquisa sob todas as versões ativas e o predicado público `EnrollmentEligibility`.
- Aplicou os limites aprovados de 5 recusas por conta/15 min e 50 por origem/1 h em uma única operação Lua atômica no Redis.
- Auditou registros e bloqueios com correlation ID, identidade, ação e resultado, sem matrícula, digest ou versão de chave.
- Incluiu a atividade do vínculo na allowlist que renova a sessão autenticada.

## Task Commits

- `44d7c24` — `feat(02-07): implement enrollment registration` — contém os 19 arquivos técnicos do plano.

Este summary forma o commit documental subsequente. O hash desse próprio commit não é registrado dentro dele porque só existe depois da gravação.

## Decisions Made

- Uma recusa de negócio conserva a reserva para encarecer enumeração; sucesso e erro técnico a liberam para não punir a pessoa por indisponibilidade interna.
- Redis ausente, contador corrompido ou TTL inválido falham fechados. Os dois eixos são verificados e reservados atomicamente.
- O banco continua sendo o árbitro final da unicidade. A aplicação traduz somente as constraints esperadas; outra violação permanece erro interno.
- A origem é obtida por `getRemoteAddr()`, confiando somente no `RemoteIpValve` já governado pela allowlist de proxies.

## Deviations from Plan

### Auto-fixed Issues

1. A primeira versão do limitador fazia verificação e incremento em operações separadas. Um script Lua passou a reservar conta e origem atomicamente.
2. Contador/TTL inválido tolerava continuidade. O caminho foi alterado para falhar fechado.
3. A versão HMAC aceitava formatos permissivos. O parser agora exige literalmente `v[1-9][0-9]*`.
4. Erros 500 genéricos consumiam cota. A reserva agora é liberada antes de propagar falhas técnicas.
5. Uma falha de auditoria dentro do handler de Bean Validation também consumia cota. O próprio handler libera a reserva e devolve Problem Details 500 sanitizado.

Nenhuma correção ampliou o produto ou adicionou dependências.

## Verification Evidence

- Task 2: 184 testes unitários e 57 de integração verdes após migration e persistência.
- Gate Maven final: 187/187 unitários e 196/196 integrações; `BUILD SUCCESS`.
- Gate Node final: 536/536.
- Mutações discriminantes observaram vermelho ao remover índices/contratos, quebrar a atomicidade dos eixos e omitir a liberação em 500 genérico ou falha de auditoria na validação.
- `git diff --check`: sem erros; somente avisos esperados de LF/CRLF no Windows.
- Revisão Codex final: `PASS`, nenhum P0, P1 ou P2.

## Issues Encountered

- A chave HMAC de teste precisou ser gerada de forma efêmera na memória do processo, conforme o procedimento do projeto; nenhum valor foi impresso ou persistido.
- O Spring MVC não reencaminha automaticamente uma exceção lançada dentro de um `@ExceptionHandler` ao handler genérico. Por isso o handler de validação constrói diretamente a resposta 500 sanitizada após liberar a reserva.
- Avisos de reconnect do Lettuce e conexões encerradas do Hikari ocorreram no teardown dos containers e não representaram falha.
- P3 não bloqueante: o método `appliesV1AndV2MigrationsSequentially` já cobre V1–V5 e deve receber nome atualizado em manutenção futura.

## User Setup Required

Os ambientes continuam obrigados a fornecer a versão atual e a chave HMAC correspondente conforme `02-CHAVE-HMAC-PROCEDIMENTO.md`. Valores reais nunca entram no Git, banco, logs ou documentação.

## Next Phase Readiness

- A Wave 5 está concluída e libera a Wave 6: planos 02-08, 02-16 e 02-24.
- O Plano 02-16 ainda deve provar rotação e corrida concorrente de ponta a ponta.
- Nenhuma dependência foi adicionada e nenhum push foi realizado.

## Self-Check: PASSED

- Os 19 arquivos técnicos e este summary existem.
- Maven, Node, mutações e revisão independente passaram no snapshot final.
- Nenhuma matrícula, digest ou chave foi registrada neste documento.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-22*
