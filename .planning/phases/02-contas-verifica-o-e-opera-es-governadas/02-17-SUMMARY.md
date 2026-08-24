---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 17
subsystem: identity-security
tags: [operational-roles, spring-security, oidc, seed, openapi]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: papéis operacionais do Plano 02-23, origem de identidade do 02-24 e contrato gerado do 02-16
provides:
  - Papéis vigentes do PostgreSQL convertidos em authorities do Spring Security
  - Contrato somente leitura pertencente ao platform, sem ciclo entre módulos
  - Semente idempotente e auditada do primeiro ADMINISTRADOR_GERAL
  - Matriz de autorização, OpenAPI e cliente gerado para administração de papéis
affects: [02-18, 02-19, 02-26, 02-31, OPER-01, OPER-02, OPER-03, OPER-04, AUDT-01]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - Contrato consumidor declarado pelo módulo platform e implementado por identity
    - GrantedAuthoritiesMapper único para preservar a autoridade técnica e acrescentar papéis BETUFU
    - Bootstrap por variável externa, sem endpoint privilegiado e sem valor real no Git

key-files:
  created:
    - backend/src/main/java/br/com/betufu/platform/identity/OperationalIdentityLookup.java
    - backend/src/main/java/br/com/betufu/identity/internal/OperationalIdentityLookupService.java
    - backend/src/main/java/br/com/betufu/platform/security/BetufuRoleAuthorityContributor.java
    - backend/src/main/java/br/com/betufu/identity/internal/AdminSeedInitializer.java
    - backend/src/test/java/br/com/betufu/identity/RoleAuthorizationMatrixIT.java
  modified:
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
    - backend/src/main/java/br/com/betufu/identity/internal/RoleGrantController.java
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs

key-decisions:
  - "O contrato OperationalIdentityLookup pertence ao platform; identity o implementa, preservando a única seta modular identity -> platform."
  - "Papéis vêm exclusivamente das concessões vigentes no PostgreSQL; claims, grupos e papéis homônimos do Keycloak não concedem autoridade operacional."
  - "A semente recebe somente o identificador público por BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER e não cria identidade nem endpoint de bootstrap."
  - "O matcher específico de recovery foi reservado antes de /api/v1/**; o contrato público desse recovery é publicado junto do Plano 02-31 para manter cada commit bisectável."

requirements-completed: [OPER-01, OPER-02, OPER-03, OPER-04, AUDT-01]

coverage:
  - id: D1
    description: Papéis e escopos vigentes geram apenas as authorities operacionais permitidas.
    requirement: OPER-02
    verification:
      - kind: integration
        ref: RoleAuthorizationMatrixIT e RoleScopeTest
        status: pass
    human_judgment: false
  - id: D2
    description: A semente do primeiro administrador é idempotente e não reescreve a concessão.
    requirement: OPER-01
    verification:
      - kind: integration
        ref: AdminSeedIdempotencyIT
        status: pass
      - kind: other
        ref: mutação M3 observada vermelha e restaurada por hash
        status: pass
    human_judgment: false
  - id: D3
    description: O contrato de papéis e o cliente gerado permanecem mínimos e sincronizados.
    requirement: OPER-04
    verification:
      - kind: unit
        ref: validate-phase1 e validate-angular-route-boundary
        status: pass
    human_judgment: false
---

# Plano 02-17 — Authorities, semente e contrato de papéis

## Verification Evidence

- Commit técnico `037f018`: Surefire focal `36/36`, Failsafe focal `22/22` e Node `552/552`, todos sem falhas.
- Snapshot final combinado da Wave 8: Surefire `217/217`, Failsafe `276/276`, módulo Keycloak `56/56` e Node `580/580`.
- Mutações M1 (ordem do matcher), M3 (idempotência da semente) e M4 (mapper concorrente) ficaram vermelhas; restaurações foram conferidas por SHA-256.
- A ausência de ciclo foi comprovada por `ApplicationModules.verify()` e pela regra que mantém zero imports `platform -> identity`.
- Digest role-only aprovado: `B112196AE1F024DD4D28CF7EDCA1EFB7D236617EB5505EE9057937DCBD3C73E5`.

## Decisions Made

- `OperationalIdentityLookup` usa apenas tipos neutros e resolve o par OIDC exato sem criar cadastro. O módulo `identity` implementa o contrato que pertence a `platform`, evitando o ciclo que quebraria o Spring Modulith.
- O Spring instala o único `GrantedAuthoritiesMapper` no provider OIDC. O contributor soma authorities BETUFU às authorities técnicas existentes; não as substitui.
- O primeiro administrador é semeado após a identidade existir. Repetir a inicialização não duplica auditoria, não cria nova concessão e não altera o carimbo original.

## Deviations from Plan

- O plano inicialmente atribuía ao 02-17 também o OpenAPI e o cliente do recovery do 02-31. No histórico final, apenas o contrato de papéis permanece neste commit; o contrato de recovery acompanha o controller no `feat(02-31)`. A árvore final é a mesma, mas cada commit intermediário passa a ser executável e adequado a `git bisect`.
- `RoleGrantController.java` e os validadores de contrato foram ratificados durante a execução para remover aliases não contratados e distinguir mappings reais de constantes de prefixo.

## Issues Encountered

- O adaptador Antigravity tratava `frontend/src/app/api/generated/**` como texto literal. O casamento de glob foi corrigido em `.agents/`, que é ignorado pelo Git, sem ampliar o código do produto.
- A mutação M2 não discriminou a fiação automática do mapper. Ela não foi contada como prova; a propriedade foi confirmada pelo comportamento do contexto e pela inspeção da fiação do Spring Security 7.1.

## User Setup Required

Definir `BETUFU_BOOTSTRAP_ADMIN_IDENTIFIER` com o identificador público real do primeiro administrador em cada ambiente. O valor não deve ser commitado.

## Next Phase Readiness

- O Plano 02-31 pode consumir `OperationalIdentityLookup` e o matcher de recovery já reservado na cadeia OIDC principal.
- A Wave 9 continua responsável pela ativação e pelas provas reais do fluxo Keycloak → backend; nada deste plano ativa produção.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-23*
