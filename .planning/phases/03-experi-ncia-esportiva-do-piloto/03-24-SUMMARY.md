---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 24
wave: 24
status: complete
completed: 2026-09-03
subsystem: sports-roster-tabular-import
tags: [sports, roster, csv, xlsx, flyway, openapi, angular]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: 03-23 competition-scoped authorization and existing roster correction flow
provides:
  - fixed temporal CSV/XLSX roster import contract
  - forward-only V23 affiliation period persistence with legacy null preservation
  - atomic import and PUBLICADO guard with versioned correction boundary
  - governed OpenAPI candidate and Angular roster temporal models
affects: [sports-roster, sports-import, sports-catalog, phase-3]

actuals:
  tasks: 5
  commits: 2
  changed_technical_paths: 39

tech-stack:
  added: []
  patterns:
    - shared logical ImportedRosterRow produced by fixed CSV and OOXML parsers
    - nullable affiliation dates validated against edition bounds without inventing legacy values
    - PUBLICADO import rejected before writes; correction remains on the existing versioned endpoint
    - OpenAPI candidate digest approved before governed Angular generation

key-files:
  created:
    - backend/src/main/resources/db/migration/V23__add_roster_affiliation_period.sql
    - frontend/target/api-candidate/03-24/inventory.json
    - frontend/target/api-candidate/03-24/digest.sha256
    - frontend/target/api-candidate/03-24/governed-before.sha256
    - frontend/target/api-candidate/03-24/governed-after.sha256
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/ImportedRosterRow.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsCsvParser.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsXlsxParser.java
    - backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsTabularImportService.java
    - backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterService.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/model/sports-roster-member-item.ts
    - frontend/src/app/api/generated/model/sports-roster-member-request.ts

key-decisions:
  - A1 keeps organizations and athletes global; competition administrators use existing team, edition and roster relationships.
  - B1 rejects import commit for PUBLICADO without writes; correction stays on the existing versioned endpoint.
  - L1 preserves null legacy dates and validates only supplied dates within the edition.
  - The temporal OpenAPI candidate digest was approved as D093B4368DA5F438B199C24A64CE7DC70AEED9D2638D7A5925C95FCD3ADEADC1.

patterns-established:
  - "Fixed tabular contract: Template sheet, nine ordered columns, textual enrollment and ISO dates."
  - "Validate the complete batch before transactional persistence."
  - "Use generated client output only after governed before/after hashes match."

requirements-completed: [SPRT-02, SPRT-03, SPRT-04, SPRT-05, SPRT-06, SPRT-07, SPRT-09, SPRT-10, SPRT-13, SPRT-15, SPRT-16]

coverage:
  - id: D1
    description: "CSV and XLSX fixed contracts produce equivalent temporal roster rows, including textual enrollment values."
    requirement: SPRT-03
    verification:
      - kind: unit
        ref: "SportsCsvParserTest + SportsXlsxParserTest via focal Maven command"
        status: pass
    human_judgment: false
  - id: D2
    description: "V23 persists affiliation period per roster edition and preserves legacy null dates."
    requirement: SPRT-04
    verification:
      - kind: integration
        ref: "FlywayMigrationIT + SportsRosterIT via focal Maven command"
        status: pass
    human_judgment: false
  - id: D3
    description: "Tabular import validates the complete batch atomically and rejects PUBLICADO commit without writes."
    requirement: SPRT-05
    verification:
      - kind: integration
        ref: "SportsTabularImportIT via focal Maven command"
        status: pass
    human_judgment: false
  - id: D4
    description: "The approved temporal OpenAPI candidate and governed Angular client remain in sync."
    requirement: SPRT-15
    verification:
      - kind: other
        ref: "node frontend/scripts/generate-api.mjs --check"
        status: pass
      - kind: unit
        ref: "generated-client.contract.spec.ts"
        status: pass
    human_judgment: true
    rationale: "The candidate digest required explicit owner approval before generation."

duration: multi-session
---

# Phase 3: Experiencia Esportiva do Piloto - Plan 03-24 Summary

**Importacao temporal de roster em CSV/XLSX com V23, atomicidade, bloqueio de PUBLICADO e cliente Angular governado**

## Performance

- **Duration:** multi-session
- **Completed:** 2026-09-03
- **Tasks:** 5
- **Files modified:** 39 technical paths

## Accomplishments

- Fixed the CSV/XLSX contract to nine ordered columns, a single `Template` sheet for XLSX, textual enrollment values and ISO `yyyy-MM-dd` dates.
- Replaced mock XLSX fixtures with real OOXML and covered late errors, duplicate rows, formulas, merged cells, extra columns, invalid types/dates and missing references.
- Added forward-only V23 affiliation dates, persisted them in draft/publication records, preserved legacy nulls and enforced edition bounds.
- Made valid imports atomic and rejected commits against `PUBLICADO` before writes; the existing versioned correction endpoint remains the mutation path.
- Updated the governed OpenAPI contract and generated client after approval of the candidate digest.

## Verification Evidence

- `node frontend/scripts/generate-api.mjs --check`: passed.
- `node --test frontend/scripts/generate-api.test.mjs`: `72/72` passed.
- `cmd.exe /d /c "node scripts/maven.mjs -f backend/pom.xml -Dtest=SportsCsvParserTest,SportsXlsxParserTest,SportsTabularImportIT,SportsRosterIT,FlywayMigrationIT test"`: `57/57`, `BUILD SUCCESS`.
- `npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts`: `12/12` passed.
- Candidate digest matched the approved value; governed-before and governed-after matched.
- `git diff --check`: passed; only expected Windows LF/CRLF normalization warnings were emitted.

## Decisions and Deviations

- A1, B1 and L1 were approved and implemented; no persistent per-competition catalog association was introduced.
- A duplicate period-validation call in `SportsRosterService` was removed after review; focal tests were rerun afterward and remained green.
- RepoWise was queried before structural work. Its index matched the pre-commit HEAD during certification; after the local commit it is behind and is treated as orientation only.
- No dependency, infrastructure, external service, push, integration or cleanup operation was performed.

## Issues Encountered

- Angular emitted existing `NG8102` warnings in an unrelated events-results template; the generated-client test still passed.
- Maven emitted existing Mockito/Redis/logging warnings during Testcontainers shutdown; all focal tests passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 03-24 is implemented, verified and documented locally.
- The broader preserved-capability certification remains assigned to plan 03-27.
- Plan 03-25 is the next blocking checkpoint.

---
*Phase: 03-experi-ncia-esportiva-do-piloto*
*Plan: 03-24*
*Completed: 2026-09-03*
