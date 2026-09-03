---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 23
wave: 23
status: complete-pending-git
completed: 2026-09-03
subsystem: sports-competition-scoped-authorization
tags: [sports, authorization, competition-scope, roster]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: 03-22 generic event baseline and 03-21 competition-to-sport association
provides:
  - A1 global organization and athlete catalogs with competition-scoped operations
  - resource-derived authorization for organization, athlete, roster and tabular import flows
  - negative matrix evidence for foreign competition access without database mutation
affects: [sports-organization, sports-athlete, sports-roster, sports-import, phase-3]

actuals:
  tasks: 4
  commits: 2
  changed_technical_paths: 10

tech-stack:
  added: []
  patterns:
    - derive competition from team, edition and roster relationships before capability checks
    - reserve global organization and athlete mutations for ADMINISTRADOR_GERAL
    - use relationship joins for scoped catalog reads and return empty when no grant reaches a resource

key-files:
  modified:
    - backend/src/main/java/br/com/betufu/sports/internal/organization/SportsOrganizationService.java
    - backend/src/main/java/br/com/betufu/sports/internal/organization/SportsOrganizationRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/athlete/SportsAthleteService.java
    - backend/src/main/java/br/com/betufu/sports/internal/athlete/SportsAthleteRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterService.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsTabularImportService.java
    - backend/src/test/java/br/com/betufu/sports/SportsOrganizationIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsAthleteIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsRosterIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsTabularImportIT.java

## Accomplishments

- The responsible owner approved A1: organization and athlete records remain global, while competition administrators operate only on resources reached through their competition relationships.
- Organization listing returns the global catalog to the general administrator and only organizations reached through granted competition teams to a competition administrator; global organization create/update/publish remain general-admin-only.
- Athlete listing is global for the general administrator and roster-reachable for competition administrators; athlete create derives competition from the team, while global athlete update/publish remain general-admin-only.
- Roster publication/read and tabular import validate the competition derived from the team or edition, without trusting a client-supplied competition identifier.
- Negative integration cases cover foreign competition access with `403` responses and unchanged database state.

## Verification Evidence

- A1 checkpoint command: `SPRT-02 ownership decision is required before scoped implementation`.
- `node scripts/maven.mjs -f backend/pom.xml -Dtest=SportsOrganizationIT test`: `3/3`, `BUILD SUCCESS`.
- `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsAthleteIT,SportsRosterIT,SportsTabularImportIT' test`: `19/19`, `BUILD SUCCESS`.
- `node scripts/maven.mjs -f backend/pom.xml '-Dtest=SportsOrganizationIT,SportsAthleteIT,SportsRosterIT,SportsTabularImportIT' test`: `22/22`, `BUILD SUCCESS`.
- `git diff --check`: passed; only expected Windows LF/CRLF normalization warnings were emitted.
- No migration, OpenAPI contract, generated client, dependency, infrastructure or external-service files changed.

## Decisions and Deviations

- A1 was selected explicitly; A2 persistent per-competition association was not introduced.
- `SportsRosterRepository.java` was listed as a possible task path but required no change; no allowlist expansion was needed.
- The athlete and organization scoped queries use `EXISTS` joins. The earlier `DISTINCT ... ORDER BY lower(name)` form was replaced after PostgreSQL rejected its ordering expression, preserving deduplication and ordering.
- RepoWise was queried first, but its index tracked the primary repository commit rather than this worktree; live files, tests and Git remained authoritative.
- The broader regression certification for SPRT-03, SPRT-04, SPRT-05, SPRT-06, SPRT-07, SPRT-10, SPRT-13, SPRT-15 and SPRT-16 remains assigned to the 03-27 matrix.

## Git

```yaml
status: complete-pending-git
base_sha: 8388bb365662c008279fc1a20e14a1cffdab1158
branch: worktree-agent-03-gap-exec
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-gap-exec
implementation_commit: 7cc769d6108d0c7f5e5b2c8ecb3b7d1f9c0153d9
documentation_commit: current documentation commit (hash intentionally omitted)
integration: not performed
cleanup: not performed
push: not performed
```

## Notes

The worktree preserves the previously completed 03-21 and 03-22 gap-closure commits. This plan adds no new data model association, migration or public contract field; it closes SPRT-02 through the existing team, edition and roster relationships.
