---
phase: 01-funda-o-segura-da-plataforma
plan: "06"
subsystem: session-security
tags: [spring-session, redis, session-lifecycle, xsrf, testcontainers, runtime-validation]

requires:
  - phase: 01-29
    provides: "Contrato XSRF SPA, datasource externo e runner runtime fail-closed"
provides:
  - "Sessão BFF Redis com expiração ociosa seletiva de PT8H, sem timeout absoluto"
  - "Renovação somente por atividade autenticada, allowlisted e concluída em 2xx"
  - "Regeneração de ID de sessão e independência de sessões simultâneas"
  - "Validador 01-29 alinhado ao Redis sem duplicar a prova autoritativa Redis"
affects: [01-07, 01-21, backend, security, session, redis, runtime]

actuals:
  tokens: 45999
  tasks: 4
  implementation_paths: 17
  commits: 5

tech-stack:
  added: []
  patterns:
    - "Sessão BFF Redis com deadline ocioso seletivo e sem timeout absoluto"
    - "Compare-and-max atômico no Redis para preservar o maior instante válido sob concorrência"
    - "Allowlist exata de atividade HTTP, condicionada a autenticação e resposta 2xx"

patterns-established:
  - "O touch automático do Spring Session é técnico e não pode renovar a sessão por si só."
  - "Mudanças de atividade, expiry ou ID são coordenadas atomicamente no Redis; conflito esgota retry e falha fechado."
  - "Novos endpoints que possam renovar sessão exigem cadastro explícito; glob de rota não é autorização."

key-files:
  created:
    - "backend/src/main/java/br/com/betufu/platform/session/SessionPolicy.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SessionActivity.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SessionTerminationReason.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SessionContractDto.java"
    - "backend/src/test/java/br/com/betufu/platform/session/SessionPolicyTest.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SelectiveTouchSessionRepository.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SessionLifecycleService.java"
    - "backend/src/main/java/br/com/betufu/platform/session/SessionSecurityConfiguration.java"
    - "backend/src/main/java/br/com/betufu/platform/session/ValidActivityFilter.java"
    - "backend/src/test/java/br/com/betufu/platform/session/RedisSessionIT.java"
    - ".planning/phases/01-funda-o-segura-da-plataforma/01-06-SUMMARY.md"
  modified:
    - "backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java"
    - "backend/src/main/resources/application.yaml"
    - "backend/src/main/resources/application-dev.yaml"
    - "backend/src/test/java/br/com/betufu/platform/EnvironmentConfigurationTest.java"
    - "infra/.env.example"
    - "scripts/validate-runtime-prerequisites.mjs"
    - "scripts/validate-runtime-prerequisites.test.mjs"

key-decisions:
  - "A sessão BETUFU usa idle timeout PT8H e absoluteTimeout=null; sessão Keycloak e atividade válida são contratos distintos."
  - "O touch automático do Spring Session não é atividade válida: o repositório seletivo preserva o deadline Redis até uma atividade aprovada."
  - "A allowlist inicial de produção é somente PUT /api/v1/tracer/identity/current, com autenticação presente e resposta final 2xx."
  - "RedisSessionIT é a prova autoritativa do Redis; o runner 01-29 mantém sua exceção Redis apenas no processo filho test-only."

requirements-completed: [IDEN-03, IDEN-04]

coverage:
  - id: D1
    description: "A política central aplica PT8H sem duração absoluta e cobre limites, precisão, repetição e concorrência."
    requirement: IDEN-04
    verification:
      - kind: unit
        ref: "SessionPolicyTest — 12/12"
        status: pass
    human_judgment: false
  - id: D2
    description: "A configuração dev usa Spring Session Redis com propriedades externas e sem a exclusão provisória."
    requirement: IDEN-04
    verification:
      - kind: unit
        ref: "EnvironmentConfigurationTest — 11/11"
        status: pass
    human_judgment: false
  - id: D3
    description: "A renovação seletiva, a independência de sessões e a regeneração de ID foram comprovadas contra Redis real."
    requirement: IDEN-04
    verification:
      - kind: integration
        ref: "RedisSessionIT — 7/7"
        status: pass
    human_judgment: false
  - id: D4
    description: "O snapshot acr/amr permanece server-side, é reconstruído por escalares e não provoca step-up por operação."
    requirement: IDEN-03
    verification:
      - kind: integration
        ref: "RedisSessionIT#storesLifecycleStateAsScalarsWithoutSerializingSessionState"
        status: pass
      - kind: unit
        ref: "SessionPolicyTest#authenticationSnapshotIsImmutableAndDoesNotTriggerStepUp"
        status: pass
    human_judgment: false
  - id: D5
    description: "O validador runtime preserva as garantias de datasource e XSRF enquanto rejeita regressões do contrato Redis."
    requirement: IDEN-04
    verification:
      - kind: unit
        ref: "scripts/validate-runtime-prerequisites.test.mjs — Node 263/263"
        status: pass
    human_judgment: false

duration: "não mensurada — execução interativa com checkpoints, correções e aprovações por bloco"
completed: 2026-08-14
status: complete
---

# Phase 1 Plan 06: Sessões Redis seletivas Summary

**A sessão BFF passou de memória provisória para Redis com janela ociosa seletiva de oito horas, sem permitir que tráfego técnico prolongue a autenticação.**

## Desempenho

- **Conclusão:** 2026-08-14
- **Tasks:** 4
- **Paths de implementação/configuração/teste:** 17
- **Commits registrados:** 5
- **Escopo:** política temporal, configuração Redis, lifecycle seletivo e validação runtime alinhada.

## Entregas

- `SessionPolicy` modela `idleTimeout=PT8H`, `absoluteTimeout=null`, estado temporal monotônico e o snapshot `acr`/`amr` da autenticação.
- O profile `dev` deixa de excluir `SessionDataRedisAutoConfiguration`; host, porta e senha Redis continuam externos e o namespace de sessão é `betufu:session`.
- `SelectiveTouchSessionRepository` impede que o touch técnico automático renove a sessão. `SessionLifecycleService` e `ValidActivityFilter` renovam somente a atividade autenticada, explicitamente cadastrada e concluída em 2xx.
- A allowlist inicial de produção contém apenas `PUT /api/v1/tracer/identity/current`. Health, assets, consulta de sessão, polling, preflight, heartbeat e materialização XSRF não renovam a sessão. A política também classifica STOMP heartbeat como técnico, mas a prova runtime STOMP pertence ao 01-08.
- `SecurityConfiguration` declara a regeneração de ID de sessão no login/reautenticação; o ID anterior deixa de ser aceito e sessões simultâneas permanecem independentes.
- O validador do 01-29 agora rejeita regressões da configuração Redis. Sua exceção Redis sobrevive somente no processo filho criado pelo próprio runner para preservar a prova histórica de datasource/OIDC/XSRF, sem disputar a prova Redis real.

## Commits das tarefas

1. **Task 1 — política temporal seletiva:** `d8b3f61` (`feat(01-06): define selective session policy`).
2. **Task 2 — configuração Redis e teste de ambiente:** `26dac30` (`feat(01-06): configure Redis-backed sessions`).
3. **Task 3 — lifecycle seletivo e Redis real:** `292de8e` (`feat(01-06): enforce selective Redis session lifecycle`).
4. **Task 4 — validação runtime:** `ad0155c` (`test(01-06): harden session runtime validation`).
5. **Alinhamento documental do plano:** `5870d0c` (`docs(01-06): align selective session execution plan`).

## Evidências de verificação

| Prova | Resultado |
|---|---|
| Testes Node | PASS — `263/263`, sem failure, cancelled, skipped ou todo. |
| Maven offline agregado | PASS — `BUILD SUCCESS`; Surefire `27/27` (`4 + 11 + 12`) e Failsafe `29/29` (`17 + 5 + 7`), total `56/56`, sem failure, error ou skip. |
| Redis real | PASS — `RedisSessionIT 7/7` contra Testcontainers local, cobrindo expiração seletiva, concorrência, ID regenerado e sessões independentes. |
| Runner 01-29 | PASS — SHA-256 de `validate-runtime-prerequisites.mjs`: `90E88C98A676F696EF087EDAFC4A193DB63399765E5BF02713EEC080C3D97F91`; SHA-256 do teste: `AED1941EE678D5BA45252E0D88B490D009B89298B67915FABF1AC47D58A32C6A`. |
| Cleanup de containers | PASS — 34 containers restaurados; hash `BEEF1DEE18409C222A57A40699FB76D07CDCF5F5EB64B9D571A80BFF2D7F63B3`. |
| Cleanup de redes | PASS — 3 redes custom restauradas; hash `C833AF152095F4894130F740BCA105BA2B5A85291493558BB66A88E9AACD2F75`. O schema das redes builtin também foi restaurado; hash `A7FCEA326F6153ED82E64478505EDF9A5B14F6F2158E67C1BB36BF6AA612C16B`. |
| Resíduos Testcontainers | PASS — `0` recurso residual atribuível à execução. |
| Revisão externa | PASS — sem achados P0, P1 ou P2 no snapshot revisado. |

## Riscos residuais e observações

A revisão externa aceitou três achados P3, sem reabrir o snapshot já comprovado:

1. `pendingOriginalIds` pode reter uma entrada se uma persistência terminar em falha terminal; é um possível vazamento lento apenas no caminho de erro.
2. Os beans opcionais de lifecycle/filtro têm fallback silencioso caso estejam ausentes; a evidência atual é estrutural (component scan) e de testes smoke/Redis IT, sem comprovação de deployment em produção.
3. `hasApprovedActivity` não possui consumidor efetivo porque os nomes dos atributos escondem o marcador.

Os avisos do Mockito/Byte Buddy sobre agente dinâmico no Java 25 e a reconexão do Lettuce durante o teardown Redis foram observados. Ambos não impediram as provas, mas devem ser acompanhados antes de futuras mudanças de JDK ou do ciclo de testes.

## Requisitos e próximo passo

`IDEN-03` e `IDEN-04` aparecem no frontmatter somente como contribuição local deste plano. Eles **não estão encerrados globalmente**: `.planning/REQUIREMENTS.md` permanece `Pending` até as entregas compartilhadas da Fase 1.

A Wave 12 pode iniciar os planos **01-07** e **01-21**, cada um com seus próprios checkpoints, aprovações e provas. Este resumo não aprova Gate global nem cria um Gate específico inexistente.

## Autoavaliação: APROVADA

- [x] As quatro tasks e os 17 paths de implementação foram entregues nos commits registrados.
- [x] A sessão Redis prova o deadline seletivo de PT8H, sem timeout absoluto e sem renovação por tráfego técnico.
- [x] Maven offline, Node, Redis real e cleanup agregado passaram.
- [x] A revisão externa não encontrou P0–P2.
- [x] Requisitos compartilhados não foram encerrados prematuramente.

---
*Phase: 01-funda-o-segura-da-plataforma*
*Completed: 2026-08-14*
