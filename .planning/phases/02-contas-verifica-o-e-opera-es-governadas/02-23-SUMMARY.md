---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 23
subsystem: identity
tags: [papeis-operacionais, autorizacao, escopo, flyway, auditoria, sessao]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: identidade local do 02-07, auditoria do 02-11 e encerramento de sessões do 02-09
provides:
  - Concessão e revogação de papéis operacionais com vocabulário fechado de três papéis
  - Guardas D-48 de autoconcessão e de último administrador geral
  - Invariante papel/escopo no DTO, no serviço e como CHECK no banco
  - Encerramento de sessões pós-commit por mudança de papel (D-49)
affects: [02-29, 02-31, OPER-01, OPER-06, IDEN-05]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 1

tech-stack:
  added: []
  patterns:
    - Invariante de domínio repetido no DTO, no serviço e no banco, porque o serviço não é a única porta
    - Migration nova em vez de edição de migration aplicada, preservando o checksum do Flyway
    - Auditoria de tentativa bloqueada sobrevive ao rollback

key-files:
  created:
    - backend/src/main/resources/db/migration/V6__create_operational_role_grant.sql
    - backend/src/main/resources/db/migration/V7__enforce_operational_role_scope.sql
    - backend/src/main/java/br/com/betufu/identity/OperationalRole.java
    - backend/src/main/java/br/com/betufu/identity/OperationalRoleQuery.java
    - backend/src/main/java/br/com/betufu/identity/internal/RoleGrantService.java
    - backend/src/main/java/br/com/betufu/identity/internal/RoleGrantRequest.java
    - backend/src/main/java/br/com/betufu/platform/session/events/OperationalRoleChanged.java
    - backend/src/test/java/br/com/betufu/identity/RoleGuardTest.java
    - backend/src/test/java/br/com/betufu/identity/RoleGrantControllerIT.java
    - backend/src/test/java/br/com/betufu/identity/RoleChangeSessionTerminationIT.java
  modified:
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java

key-decisions:
  - "Papel escopado exige scopeRef; o papel global o proíbe. Não há combinação livre."
  - "hasRole de papel global só aceita concessão com scopeRef nulo, para que um ADMINISTRADOR_GERAL escopado não confira autoridade irrestrita."
  - "A regra vale no DTO, no serviço e como CHECK no banco: script operacional e correção manual também escrevem na tabela."
  - "A V6 não foi editada. Alterar migration já aplicada invalida o checksum do Flyway, garantia que o próprio FlywayChecksumIT testa."
  - "Concessão duplicada é resolvida por ON CONFLICT DO NOTHING. A versão anterior capturava exceção e seguia usando uma transação PostgreSQL já abortada."
  - "A consulta devolve pares de papel e escopo; antes descartava scope_ref, tornando invisível a diferença entre concessão global e escopada."
  - "O evento OperationalRoleChanged vive em platform/session/events com @NamedInterface, resolvendo o ciclo identity -> platform -> identity acusado pelo ArchUnit. O listener consome após o commit, sem fallbackExecution."
  - "A auditoria de tentativa bloqueada sobrevive ao rollback: antes a exceção desfazia o registro junto com a transação, e as guardas não eram auditadas na prática, contra D-30 e D-48."

requirements-completed: [OPER-01, OPER-06, IDEN-05]

coverage:
  - id: D1
    description: Autoconcessão é bloqueada para qualquer papel e a tentativa fica auditada.
    requirement: OPER-06
    verification:
      - kind: unit
        ref: RoleGuardTest — guarda (a)
        status: pass
    human_judgment: false
  - id: D2
    description: Revogação do próprio papel de administrador geral é recusada quando ele for o último vigente.
    requirement: OPER-06
    verification:
      - kind: unit
        ref: RoleGuardTest — guarda (b), contagem sob SELECT FOR UPDATE
        status: pass
    human_judgment: false
  - id: D3
    description: Combinação papel/escopo inconsistente é recusada na concessão e na revogação.
    requirement: OPER-01
    verification:
      - kind: unit
        ref: RoleGuardTest#grantScopedRoleWithoutScopeIsRejected, grantGlobalRoleWithScopeIsRejected, revokeWithInconsistentScopeIsRejected
        status: pass
      - kind: other
        ref: mutação M12 tornando requireConsistentScope inerte observada vermelha em 3 provas
        status: pass
    human_judgment: false
  - id: D4
    description: ADMINISTRADOR_GERAL concedido dentro de um escopo não confere autoridade global.
    requirement: OPER-06
    verification:
      - kind: unit
        ref: RoleGuardTest#scopedAdminGrantDoesNotConferGlobalAuthority e o controle positivo
        status: pass
      - kind: other
        ref: mutação M13 restaurando a aceitação de qualquer escopo observada vermelha
        status: pass
    human_judgment: false
  - id: D5
    description: O banco recusa papel escopado sem escopo e papel global com escopo, independentemente do serviço.
    requirement: OPER-01
    verification:
      - kind: integration
        ref: FlywayMigrationIT#v7EnforcesRoleScopeConsistencyAtDatabaseLevel, com controle positivo das duas combinações válidas
        status: pass
      - kind: other
        ref: mutação M14 neutralizando o CHECK observada vermelha
        status: pass
    human_judgment: false

---

# Plano 02-23 — Papéis operacionais governados e guardas D-48

## Verification Evidence

- Árvore deste commit: backend Surefire `201/201`, Failsafe `241/241`.
- Árvore combinada da onda: Surefire `201/201`, Failsafe `241/241`, módulo Keycloak `56/56`, Node `540/540`.
- Mutações M12, M13 e M14 observadas vermelhas, cada uma conferida quanto a ter compilado; restaurações verificadas por SHA-256 com `touch` forçado.
- `FlywayMigrationIT` reconciliado em **8** migrations, cobrindo `5.1`, `6` e `7`, com a ordem de versões asseverada posição a posição.
- Hash final do serviço: `659629D6A16BFE060B43D87BE9884A80EDE08D9BE37D148898FDBC48C16E9316`.
- Hash final da `V7`: `5FE19F7043DE03EBFC0B39558DD186B64D79DEEDD50440DC2B89774EF9280471`.

## Decisions Made

- O invariante papel/escopo é repetido em três camadas de propósito. O DTO antecipa a recusa ao binding, o serviço decide com auditoria, e o `CHECK` da `V7` protege a tabela de qualquer outra porta — script operacional, correção manual, futura rota administrativa.
- A `V7` é migration nova porque a `V6` já foi aplicada. Editar migration aplicada invalida o checksum do Flyway, e este repositório mantém um `FlywayChecksumIT` cuja razão de existir é justamente provar essa garantia.

## Deviations from Plan

- **Emenda de allowlist ratificada:** a migration `V7__enforce_operational_role_scope.sql`, que não constava do plano original.
- **Perda de certificação declarada:** o `FlywayMigrationIT` saiu da lista de arquivos dispensados de reauditoria pela 2ª revisão, porque a contagem subiu de 7 para 8. O custo foi aceito pelo responsável: invalidar certificação parcial é barato perto de publicar checksum divergente.
- **Mudança de contrato observável:** a validação do DTO roda antes da autorização, então um não-admin que envie corpo com combinação papel/escopo inválida recebe `400`, não `403`. Não há vazamento — 400 sobre corpo malformado não revela autoridade —, mas é comportamento novo e foi apresentado à revisão.

## Issues Encountered

- Quatro ITs **truncavam `local_identity` sem citar a tabela nova**, o que o PostgreSQL recusa por chave estrangeira depois que a `V6` existe. Ajuste por consequência direta do schema novo, não por mudança de escopo.
- Dois testes existentes **concediam papel escopado sem escopo** e passaram a falhar. Eram a forma que o plano agora proíbe; foram ajustados para passar escopo.
- Um teste de integração assertava a trilha de auditoria sem o escopo. O valor auditado passou a ser `MODERADOR;scope=<uuid>`, que é o registro correto de uma concessão escopada.
- Uma chave HMAC aleatória gerada por `randomBytes(32).toString('base64')` quebrou a suíte uma vez com `Illegal base64 character 3a`; com chave determinística de alfabeto seguro, passou. A causa exata não foi isolada e fica registrada como risco antes de automatizar em CI.

## User Setup Required

Nenhuma. As execuções exigem as variáveis de chave HMAC da matrícula, conforme `02-CHAVE-HMAC-PROCEDIMENTO.md`.

## Next Phase Readiness

- A autoridade de papéis está disponível para consumidores por `OperationalRoleQuery`, sem depender do 02-17.
- Nenhuma decisão de produto sobre papéis adicionais foi tomada; o vocabulário permanece fechado em três.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-23*
