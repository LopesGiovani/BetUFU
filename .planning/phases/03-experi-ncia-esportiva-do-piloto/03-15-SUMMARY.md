---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 15
wave: 16
status: complete-pending-git
completed: 2026-09-01
subsystem: sports-profile-media
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, s3, imageio, security]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: athlete link ownership, public athlete projection P6 and governed OpenAPI generation
provides:
  - secure owner-only athlete photo upload and removal
  - bounded JPEG/PNG decode, metadata-free re-encode and private object storage
  - V20 media persistence and failure-preserving put-then-swap storage boundary
  - governed OpenAPI client and isolated owner/public Angular media state
affects: [sports-profile, public-athlete, account-console, s3-storage, phase-03]

actuals:
  tasks: 5
  commits: 0

tech-stack:
  added: [AWS SDK for Java S3 2.54.5, AWS SDK URL connection client 2.54.5]
  patterns:
    - decode and re-encode image bytes before storage, with byte and pixel limits
    - narrow storage port with private S3 adapter and opaque object keys
    - put-then-swap and delete ordering that preserves the previous photo on failure
    - governed OpenAPI candidate digest before deterministic TypeScript generation
    - separate owner/public Signal stores with fail-closed 401/403 cleanup

key-files:
  created:
    - backend/src/main/java/br/com/betufu/platform/persistence/S3ObjectStorageAdapter.java
    - backend/src/main/java/br/com/betufu/platform/persistence/S3ObjectStorageProperties.java
    - backend/src/main/java/br/com/betufu/sports/internal/media/AthleteImageProcessor.java
    - backend/src/main/java/br/com/betufu/sports/internal/media/AthleteMediaService.java
    - backend/src/main/java/br/com/betufu/sports/internal/media/AthleteMediaController.java
    - backend/src/main/java/br/com/betufu/sports/internal/media/AthleteMediaStorage.java
    - backend/src/main/resources/db/migration/V20__create_athlete_profile_media.sql
    - backend/src/test/java/br/com/betufu/sports/AthleteProfileMediaIT.java
    - frontend/src/app/features/sports/athlete-profile/athlete-media-api.adapter.ts
    - frontend/src/app/features/sports/athlete-profile/athlete-photo-field.component.ts
    - frontend/src/app/api/generated/api/athlete-media.service.ts
    - frontend/src/app/api/generated/model/athlete-media-response.ts
  modified:
    - backend/pom.xml
    - backend/openapi/betufu.yaml
    - backend/src/main/resources/application.yaml
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/scripts/generate-api.test.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - frontend/src/app/features/sports/athlete-profile/athlete-profile.store.ts
    - frontend/src/app/features/sports/athlete-profile/athlete-profile-page.component.ts
    - frontend/src/app/features/sports/public-athlete/public-athlete-profile.store.ts
    - frontend/src/app/features/sports/public-athlete/public-athlete-page.component.ts

key-decisions:
  - Only the linked athlete account may upload or remove a personal photo; administrators do not publish it.
  - JPEG and PNG uploads are decoded, bounded by bytes and pixels, re-encoded without metadata and stored in a private bucket.
  - The public endpoint returns only processed bytes with safe cache metadata; it never exposes an object key, storage endpoint, presigned URL or original upload.
  - Storage failures happen before the database reference swap, preserving the previous photo.
  - AWS SDK resolution is fixed to 2.54.5, with S3 and URL connection client only; Apache and Netty HTTP clients and SNAPSHOT versions are excluded.
  - The approved OpenAPI candidate digest is 1BE6BAB88EA2F208A38BE115E3CB4D9B0DA2EBB8E04FBF038FD122E5A2B43F27.
  - Governed before and after hashes are both A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93.

requirements-completed: [SPRT-16]

coverage:
  - id: D1
    description: Secure media pipeline rejects malformed, spoofed, truncated, oversized, metadata-bearing and polyglot images before storage, then re-encodes accepted bytes without metadata.
    requirement: SPRT-16
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/sports/AthleteProfileMediaIT.java (7/7)
        status: pass
      - kind: other
        ref: valid-avatar, oversized-avatar, metadata-avatar and polyglot-avatar fixtures plus deterministic truncated/MIME-spoof cases
        status: pass
    human_judgment: false
  - id: D2
    description: V20 and the private S3 storage boundary preserve ownership, opaque keys and the previous photo when storage fails.
    requirement: SPRT-16
    verification:
      - kind: integration
        ref: AthleteProfileMediaIT storage-failure and owner/public authorization cases (7/7)
        status: pass
      - kind: other
        ref: S3 capability matrix audit: 6 INTEGRATE and 13 OPT-OUT, 19/19 nominal decisions
        status: pass
      - kind: other
        ref: AWS dependency tree: S3 and URL connection client at 2.54.5, no SNAPSHOT/Apache/Netty client
        status: pass
    human_judgment: true
    rationale: Private bucket configuration and supply-chain legitimacy require owner review in addition to automated tests.
  - id: D3
    description: The owner/public media contract is generated from the approved OpenAPI candidate and exposes no storage infrastructure fields.
    verification:
      - kind: other
        ref: approved candidate 1BE6BAB88EA2F208A38BE115E3CB4D9B0DA2EBB8E04FBF038FD122E5A2B43F27
        status: pass
      - kind: other
        ref: governed-before.sha256 equals governed-after.sha256 at A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93
        status: pass
      - kind: other
        ref: node frontend/scripts/generate-api.mjs --check
        status: pass
    human_judgment: false
  - id: D4
    description: Owner photo selection, preview, upload/remove progress and public fallback use separate Signal stores and clean sensitive local state on 401/403.
    requirement: SPRT-16
    verification:
      - kind: unit
        ref: athlete profile, photo field and public athlete Angular focal specs (14/14 focal assertions)
        status: pass
      - kind: unit
        ref: npm --prefix frontend run test -- --watch=false (346/346)
        status: pass
      - kind: other
        ref: npm --prefix frontend run build:development, build:homolog and build:production
        status: pass
    human_judgment: false
  - id: D5
    description: Repository-wide backend and Node regressions remain green with V20 and the media contract.
    verification:
      - kind: other
        ref: Maven verify: Surefire 266/266, Failsafe 431/431 in 60 classes, BUILD SUCCESS
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java (14/14)
        status: pass
      - kind: other
        ref: node --test scripts/*.test.mjs (625/625)
        status: pass
    human_judgment: false

validation:
  - Dependency tree verified the approved AWS SDK coordinates and excluded SNAPSHOT, apache-client and netty-nio-client.
  - S3 capability audit verified all 19 rows: 6 INTEGRATE and 13 OPT-OUT.
  - Candidate inventory, digest and governed before/after files were present and consistent.
  - Angular full suite passed 346/346 across 56 files; all three Angular builds passed.
  - Maven full verification passed 697/697 tests; Flyway focal passed 14/14 and media focal passed 7/7.
  - `git diff --check` passed with no whitespace errors.

deviations:
  - "FlywayMigrationIT had six stale expectations of 20 migrations; they were mechanically corrected to 21 after V20 was present. The focal Flyway run then passed 14/14."
  - "Angular CLI temporarily wrote analytics: false to frontend/angular.json during a build; the file was restored byte-for-byte and remains outside the changed-content allowlist."
  - "Angular emitted existing NG8102 diagnostics and historical CSS budget warnings; neither failed the build or changed the implementation scope."
  - "Maven/Testcontainers emitted Redis/PostgreSQL teardown warnings after green tests; the process exited 0 with BUILD SUCCESS."

issues:
  - "No known product, authorization, privacy or storage correctness issue remains in the certified snapshot."

git:
  status: complete-pending-git
  base_sha: 112ca13db1f09210fb52664b3c52531e91da0ff6
  branch: worktree-agent-03-15
  worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-15
  implementation_commit: pending-authorization
  documentation_commit: pending-authorization
  integration: pending-authorization
  cleanup: pending-authorization
  push: pending-authorization

next-phase-readiness:
  - "The worktree is ready for Codex read-only review with no further edits after the frozen snapshot."
  - "Stage, implementation commit, documentation commit, integration, cleanup and push each require separate owner authorization."
  - "The next technical plan is 03-16/W17 after this plan is reviewed and integrated."
---

# Fase 3 Plano 03-15: processamento seguro de foto

**Foto de atleta owner-only com decodificacao limitada, re-encode sem metadados, bucket S3 privado, contrato OpenAPI governado e fallback publico isolado.**

## Performance

- **Validation window:** 2026-09-01, execution completed before this documentation snapshot
- **Tasks:** 5 planned tasks completed
- **Changed paths:** 39 of 41 technical allowlist paths changed; 2 allowlisted generated paths remained byte-identical

## Accomplishments

- V20 stores the processed athlete media reference while the service preserves the previous reference if put/swap storage fails.
- The image processor accepts only decodable JPEG/PNG content, enforces byte/pixel bounds, removes metadata through re-encoding and rejects adversarial fixtures before storage.
- The S3 adapter is private and fail-closed; public responses contain processed media only and omit infrastructure details.
- The approved OpenAPI candidate generated the media service/model and the Angular adapter connects it to separate owner and public Signal stores.
- Owner 401/403 handling clears File/Blob, preview object URL, progress, errors and mutation state; public state never receives owner upload data.

## Verification Evidence

- AWS dependency tree: all resolved AWS SDK modules at `2.54.5`; S3 and URL connection client present; Apache HTTP, Netty HTTP and SNAPSHOT absent.
- S3 matrix: 6 `INTEGRATE` and 13 `OPT-OUT`, all 19 rows audited.
- Backend: Surefire `266/266`, Failsafe `431/431` in 60 classes, `BUILD SUCCESS`; media IT `7/7`; Flyway `14/14`.
- Node: `625/625`; Angular: `346/346` in 56 files; development, homolog and production builds passed.
- OpenAPI generator check passed; candidate digest `1BE6BAB88EA2F208A38BE115E3CB4D9B0DA2EBB8E04FBF038FD122E5A2B43F27`; governed before/after equal at `A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93`.
- `git diff --check` passed; no technical path exists outside the plan allowlist.

## Self-Check: PASSED

- All five tasks and their checkpoints are represented above.
- Candidate and governed hash artifacts exist and agree.
- The six Flyway expectation corrections are documented as a mechanical deviation.
- The temporary `frontend/angular.json` analytics change was restored and is not part of the certified content.
- No Git mutation has been performed; the snapshot remains frozen for read-only review.

## Operacoes Git

Nenhum stage, commit, integracao, cleanup ou push foi executado. Essas operacoes permanecem aguardando autorizacao especifica.

---
*Phase: 03-experi-ncia-esportiva-do-piloto*
*Plan: 03-15*
*Wave: 16*
