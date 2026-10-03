---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "08"
status: complete
completed: 2026-10-03
technical_commit: 0a5f02587e77535e42d43c0e407e380249bb41d8
---

# Plan 06-08 Summary

Certified Phase 6 with PostgreSQL concurrency, populated real-browser journeys
and the complete repository verification ladder. All eight plans are complete.

## Delivered

- Four-worker PostgreSQL/Testcontainers races prove authorization/payment retry
  convergence, spent-payout cancellation/refund recovery, nonnegative balances,
  ranking reconstruction, and single achievement grant/revocation lineage.
- Exact/null/stale-version and permission checks reject authorization without
  financial effects. Cancellation preserves original WON history. Temporal odds
  replay preserves the past cut while excluding cancelled input at knownAt.
- Privacy tests compose real adapters and serialize nonempty controller DTOs;
  service group isolation and no-credit effects are checked. These are not full
  HTTP filter-chain tests; existing ControllerIT and real browser/API runs
  provide complementary evidence. Internal imports are test composition only.
- Genuine Keycloak sessions and API-created facts support 16 Chrome journeys:
  history, public lifetime ranking, groups, achievements, corrections, refresh,
  browser history, loading/offline, 401/403/404 and session-store expiration at
  desktop/320px. Distinct administrators deny self-approval and approve once
  financially despite retries.
- Approved FormsModule import fixes native group submission. Three new DOM
  regressions failed before the correction and passed after it; focal is 9/9.
- The isolated runner owns explicitly named/labelled volumes, containers and
  network. Source guards check five closed schemas, routes, migration hashes
  and common mock/auth/skip/credential/UUID shortcuts in specs and setup.

## Exact Final Verification

| Gate | Result |
|---|---|
| Maven clean verify | BUILD SUCCESS; Surefire 361/361 in 37 classes; Failsafe 613/613 in 123 classes; no failures/errors/skips |
| Focal concurrency/certification/privacy/odds/Modulith | 20/20 XML cases; no failures/errors/skips |
| Angular aggregate | 455/455 in 75 files |
| Node aggregate | 812/812, 9 suites; no failures/cancelled/skipped/todo |
| Isolation/source guards focal | 47/47: 16 isolation and 31 source regression/mutation tests |
| Real isolated Playwright | 16/16, zero skips/retries; 52.0 seconds excluding setup |
| Generator anti-drift, lint, production build, Spotless, scoped Prettier, source validator, diff check | Passed; existing CSS-budget warnings remain nonfatal |

Maven's wrapper doubles count lines (1948); authoritative XML total is 974.
Full clean verify supersedes the earlier formatting failure. Angular, generator,
lint/build, Spotless and focal Node guards were repeated at closeout. Detailed
commands and requirement evidence are in 06-VALIDATION.md.

## Remediation And Frozen Review

Initial certification was honestly blocked by missing business facts and native
group GET submission. The owner approved five additional paths, recorded as T2.
Technical commit contains exactly 14 reconciled paths. Harness corrections
included dev public origin, HMAC version v1, real Redis namespace/TTL wait,
CommonJS-safe CLI invocation, explicit volume isolation and operator profiles.
Neither business responses nor authentication were injected. Business facts
were created through actual APIs, not SQL-filled WON/REVOKED/correction rows.

Read-only review used RepoWise and manual inspection of all 14 files. Its
whole-diff analysis covered only three tracked files; test recommendations are
inferred, not measured coverage. Duplicated odds-test assertion advisories
refer to unchanged displaced blocks. Script complexity remains a maintenance
concern, not a demonstrated blocking defect. No independent external review
PASS is claimed; no blocking functional finding remained after final gates.

Frozen base: bf31d518a69333d85636f0169952cc42069fedc9.
Technical manifest digest:
F5DB48D471CE84C2FFEA57D84457A7153D604FECAC35E03C30F8C26F98552BAF.
Format: sorted relative path, space, uppercase raw-file SHA-256, LF joins/final
LF. Hashes were rechecked before stage. A PowerShell comparison precedence
error initially blocked the stage check; Compare-Object confirmed the exact
14-path index before commit. No file was reverted.

## Preservation, Reproduction And Limits

- Owner documents retain baseline hashes recorded in verification and remain
  excluded. Existing tmp/CLI artifacts remain unstaged.
- Seven owner services are healthy at V38. Final read-only counts match
  baseline: predictions 2, settlements/corrections/awards/groups 0.
- Final isolated run betufu-phase6-e2e-ab539bbc8557f51c removed its containers,
  network, volumes and generated private configuration. No labelled isolated
  runtime resources remain. Fourteen build-image tags from seven runs remain;
  no broad prune or owner-volume reset occurred.
- Reproduce sequentially: Maven clean verify, Angular production build, then
  node scripts/phase6-e2e.mjs. Dockerfiles consume prebuilt JAR/dist; do not
  run Maven clean concurrently with image building. Docker, approved existing
  dependencies/Chrome and free loopback ports 4300/8281 are prerequisites.
- TTL shortening in isolated Redis proves server expiration reaction, not a
  natural eight-hour wait. Inactive enrollment is controlled isolated substrate;
  it does not alter accepted prediction/settlement history.
- Administrative corrections are HTTP-tested through distinct browser sessions,
  not a new editor UI. Bounded correctness races do not certify launch-load
  SLOs; focus/overflow checks are not a full WCAG/screen-reader audit or pentest.
- Existing read caps, projection costs, script complexity and CSS warnings
  remain pilot-scale risks. No dependencies, historical migrations, OpenAPI
  or generated output were changed in this plan. No push/worktree/Antigravity.
- REQUIREMENTS.md was outside this documentary allowlist; its Phase 6 Pending
  tracking cells need later scoped reconciliation. Certified evidence for
  every assigned ID is mapped in 06-VALIDATION.md.
- Phase 7 discussion/planning is next, requiring its own approval; unstarted.
