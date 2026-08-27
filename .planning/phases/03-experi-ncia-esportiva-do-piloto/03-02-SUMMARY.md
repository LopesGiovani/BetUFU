---
phase: 03-experiencia-esportiva-do-piloto
plan: 02
subsystem: sports-api
tags: [spring-boot, postgresql, flyway, spring-security, modulith, openapi]

requires:
  - phase: 03-experiencia-esportiva-do-piloto
    provides: ER/DDL esportivo, versionamento publicado e raiz de escopo aprovados no plano 03-01
  - phase: 02-contas-verificacao-e-operacoes-governadas
    provides: identidade operacional, papeis escopados, Problem Details e seguranca BFF
provides:
  - migration V9 com competicao, edicao, evento em rascunho e snapshot publicado imutavel
  - autorizacao por papel operacional e UUID persistido da competicao
  - API operacional do tracer e GET publico anonimo limitado ao snapshot publicado
  - contrato OpenAPI e erros estaveis NOT_FOUND e STALE_VERSION
affects: [03-03, 03-04, 03-10, public-sports-api, competition-scoped-authorization]

actuals:
  tokens: 57121
  tasks: 2
  commits: 2

tech-stack:
  added: []
  patterns:
    - contrato consumidor em platform para evitar ciclo sports para identity
    - porta publica estreita em sports para validacao de escopo por identity
    - snapshot publicado append-only separado do lock otimista do rascunho

key-files:
  created:
    - backend/src/main/resources/db/migration/V9__create_sports_tracer.sql
    - backend/src/main/java/br/com/betufu/sports/CompetitionScopeCatalog.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerController.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerService.java
    - backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-02-SUMMARY.md
  modified:
    - backend/src/main/java/br/com/betufu/identity/internal/RoleGrantService.java
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
    - backend/src/main/java/br/com/betufu/platform/api/ApiErrorCode.java
    - backend/openapi/betufu.yaml

key-decisions:
  - "O competitionId usado na autorizacao sempre vem do recurso persistido, nunca do payload do cliente."
  - "ADMINISTRADOR_COMPETICAO exige GrantedRole com scopeRef exatamente igual ao UUID da competicao."
  - "Identity valida a existencia do escopo pela porta publica CompetitionScopeCatalog, sem importar sports.internal."
  - "Somente GET /api/v1/public/sports/** e anonimo; comandos e rascunhos permanecem autenticados."
  - "Publicacao copia um snapshot imutavel e concorrencia obsoleta responde BETUFU-409-STALE_VERSION."

patterns-established:
  - "Autorizacao por traversal: carregar edicao/evento, derivar competitionId e somente entao consultar papeis."
  - "Fronteira publica: DTO allowlist le somente a publicacao mais recente e nunca serializa o rascunho."
  - "Flyway forward-only: V9 adiciona o dominio esportivo sem editar V1-V8."

requirements-completed: [AUTH-01, SPRT-01, SPRT-10, SPRT-11]

coverage:
  - id: D1
    description: "V9 persiste competicao, edicao, evento e publicacoes imutaveis sem alterar migrations anteriores."
    requirement: SPRT-11
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java#v9KeepsPublishedSnapshotsImmutable"
        status: pass
      - kind: integration
        ref: "node scripts/maven.mjs -f backend/pom.xml clean verify"
        status: pass
    human_judgment: false
  - id: D2
    description: "Comandos administrativos usam o UUID persistido da competicao e recusam papel concedido em outro escopo."
    requirement: SPRT-01
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java#rejectsPublicationByAdministratorFromAnotherScope"
        status: pass
      - kind: integration
        ref: "mutacao: remover requireCompetitionAdministrator produziu esperado 403, observado 201"
        status: pass
    human_judgment: false
  - id: D3
    description: "GET publico funciona sem sessao somente depois da publicacao; rascunho e comandos continuam privados."
    requirement: AUTH-01
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java#keepsCommandsPrivateAndPublicGetAnonymous"
        status: pass
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java#completesScopedPublicationTracer"
        status: pass
    human_judgment: false
  - id: D4
    description: "O tracer publica evento minimo com participantes ordenados, instante, zona, fonte e local a definir no contrato OpenAPI."
    requirement: SPRT-10
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java (9/9)"
        status: pass
      - kind: other
        ref: "node --test scripts/*.test.mjs (623/623)"
        status: pass
    human_judgment: false

duration: 5h13m
completed: 2026-08-27
status: complete
---

# Fase 3 Plano 02: Tracer backend esportivo

**V9, autorizacao escopada por competicao, publicacao imutavel e leitura publica anonima entregues em uma fatia backend completa**

## Performance

- **Duration:** 5h13 de tempo decorrido, incluindo pausas e revisao externa
- **Started:** 2026-08-27T16:06:21Z
- **Completed:** 2026-08-27T21:19:10Z
- **Tasks:** 2
- **Files modified:** 15 paths do snapshot, sendo 14 paths de implementacao e a emenda aprovada do PLAN

## Accomplishments

- Criou a primeira migration esportiva com competicao, edicao, evento de trabalho e publicacao append-only.
- Fechou IDOR de escopo ao derivar `competitionId` do recurso persistido e exigir papel com `scopeRef` exato.
- Publicou cinco operacoes HTTP: quatro comandos autenticados e um GET anonimo que so enxerga snapshot publicado.
- Preservou a arquitetura Modulith por contratos pertencentes ao consumidor e publicou o contrato OpenAPI correspondente.

## Task Commits

1. **Task 1: aprovar V9 e contrato backend** - checkpoint humano, sem commit proprio.
2. **Task 2: entregar tracer backend escopado** - `2be1b0f` (`feat`).

**Plan metadata:** commit documental corrente (`docs(03-02)`); o hash nao e autorreferenciado.

## Files Created/Modified

- `V9__create_sports_tracer.sql` - tabelas, constraints, snapshots e triggers de imutabilidade.
- `SportsTracerController.java` - quatro comandos operacionais e um GET publico enumerado.
- `SportsTracerService.java` - traversal de escopo, autorizacao, validacao e publicacao transacional.
- `SportsTracerRepository.java` - persistencia JDBC do rascunho e da publicacao.
- `SportsTracerModels.java` - requests e responses allowlist do tracer.
- `CompetitionScopeCatalog.java` - porta publica estreita para validar UUID de competicao.
- `RoleGrantService.java` - recusa concessao escopada para competicao inexistente.
- `SecurityConfiguration.java` - libera somente GET sob `/api/v1/public/sports/**`.
- `ApiErrorCode.java` - codigos estaveis `NOT_FOUND` e `STALE_VERSION`.
- `betufu.yaml` - paths, schemas e Problem Details da fatia esportiva.
- `SportsTracerIT.java` - nove cenarios funcionais, de seguranca, concorrencia e privacidade.

## Verification Evidence

- Focal: `SportsTracerIT` 9/9 e `ModulithArchitectureTests` 2/2.
- Backend agregado com `clean`: Surefire 23 classes, 251/251; Failsafe 44 classes, 342/342; zero falhas, erros ou ignorados.
- Validadores Node: 8 suites, 623/623, zero falhas.
- Maven `clean verify`: `BUILD SUCCESS` em 8m35s.
- Mutacao discriminante: remover a guarda de escopo fez `rejectsPublicationByAdministratorFromAnotherScope()` falhar com 201 no lugar de 403; a guarda foi restaurada e o hash do service voltou a `552921E19D2B9ED51DE813470DE8EA90F67E65947C2B88C59A71CBAEDBB05261`.
- Revisao Codex somente leitura: `PASS`, sem P0, P1 ou P2; os 15 hashes permaneceram estaveis.

## Decisions Made

- A autorizacao administrativa nao aceita `competitionId` enviado como prova: o UUID e obtido da edicao ou evento persistido.
- O modulo `sports` consome `platform.identity.OperationalIdentityLookup`; `identity` consome apenas a porta publica `sports.CompetitionScopeCatalog`.
- Publicar nao sobrescreve historico: cria snapshot separado e torna a versao visivel ao visitante.
- A rota anonima e enumerada por metodo HTTP; qualquer mutacao sob o mesmo prefixo continua autenticada.

## Deviations from Plan

None - o plano foi executado conforme a emenda arquitetural aprovada antes da implementacao.

## Issues Encountered

- A allowlist declarou 15 arquivos de implementacao/teste. Foram alterados 14; `ModulithArchitectureTests.java` foi executado, mas nao precisou de edicao.
- O tratamento amigavel de slug duplicado permanece no catalogo completo do plano 03-04; a constraint da V9 ja protege a unicidade.
- Rotas futuras sob `/api/v1/public/sports/**` devem continuar estritamente publicas, pois o matcher libera GETs nesse prefixo.
- Avisos LF para CRLF do Git permanecem conhecidos e nao alteraram o resultado de `git diff --check`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- O plano 03-03 pode gerar o cliente e levar o tracer ao Angular e ao navegador depois da limpeza exata da worktree do 03-02.
- Os requisitos compartilhados continuam pendentes no mapa global ate os planos posteriores completarem catalogo, lifecycle e experiencia publica.
- O snapshot tecnico esta revisado e congelado; qualquer correcao posterior exige nova revisao.

## Self-Check: PASSED

- Todos os artefatos declarados existem.
- Allowlist reconciliada em 14/15 paths alterados, sem path de implementacao extra.
- Testes focais, suites agregadas, mutacao e revisao externa estao verdes.
- Os commits tecnico e documental e a integracao linear foram autorizados separadamente; limpeza e push continuam explicitamente nao executados.

---
*Phase: 03-experiencia-esportiva-do-piloto*
*Completed: 2026-08-27*
