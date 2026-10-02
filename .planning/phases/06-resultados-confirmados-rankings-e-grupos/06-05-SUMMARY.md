---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "05"
status: complete
completed: 2026-10-02
technical_commit: 25f88f1d5955d5ee7a74dfbcfa793a01cde7d31d
---

# Plan 06-05 Summary

Implemented private social groups without any group wallet, pooled bet or prize distribution.

## Delivered

- V37 creates groups, historical memberships, one-use invitations and append-only audit events. The 32-byte random invitation secret is returned once; only its SHA-256 digest is persisted. Runtime grants allow the necessary lifecycle writes but no update or delete on the audit table. V37 SHA-256: `CF1034E0F13BEC8C5B4F9D1D04771BA50707E651FF9E383126FDFE573D89F4E7`.
- `PrivateGroupPort` and `GroupQueryPort` resolve the authenticated OIDC issuer/subject through the public `OperationalIdentityLookup` port. Owner creates, renames, promotes or demotes, removes and closes; admin can invite and remove members; members can leave. The owner remains until closing the group. Invitations are single-use and may expire within seven days. Closed groups remain readable to existing members, but accept no new commands.
- Nonmember and unknown-identity reads return no group data. Roster authorization and projection run under one group-row lock and transaction so removal cannot interleave with the read. Commands return sanitized unavailable errors for unauthorized groups or invalid invitations.
- `RankingQueryPort.rankMembers` applies the ranking's existing decisive-result sort and tie positions to the active member set. Unranked members have position 0 and zero metrics. The group view exposes membership-scoped IDs, nickname, role and metrics, but not account UUIDs, enrollment records or identity-provider keys. Group operations do not mutate prediction, odds, ledger or the underlying ranking rules.

## Verification

- Final focal command for `PrivateGroupIT`, `PrivateGroupAuthorizationIT`, `PrivateGroupMetricsIT` and `ModulithArchitectureTests` passed 20/20, including a concurrent member-removal read.
- `node scripts/maven.mjs -f backend/pom.xml verify` passed. XML reports: Surefire 367/367 across 40 classes and Failsafe 585/585 across 111 classes, with zero failures, errors or skipped cases. The Maven wrapper's 1,890 count includes repeated Maven summaries; the 952 XML cases are authoritative.
- `node --test scripts/*.test.mjs` passed 759/759 across 9 suites. `spotless:apply`, `spotless:check`, `git diff --check` and staged diff check passed. Flyway V37 ran only in isolated Testcontainers PostgreSQL; no persistent local migration was applied.

## Reconciliation And Remaining Work

- The plan allowlist now includes the requested `PrivateGroupPort`, the shared ranking-port extension, and the mechanical Flyway/odds migration-count updates. No production `ranking` class imports another module's `internal` package.
- The first focal pass found an unknown-identity read that threw instead of returning empty; it was fixed and repeated. The first aggregate pass ran tests without functional failures but failed Spotless because a concurrent-read fix was edited after formatting. The test fixture also initially used two datasource objects, weakening transaction evidence. Both issues were corrected; final focal and aggregate passes are green.
- HTTP/OpenAPI/Angular presentation and binding the caller principal to these ports remain assigned to 06-07. Achievements are in 06-06. Group size and read-time ranking rebuild costs may need a limit or refresh strategy if the pilot grows.
- Technical commit `25f88f1d5955d5ee7a74dfbcfa793a01cde7d31d` contains 15 plan-approved paths. The owner's three documentation files remain untouched and excluded. No push, Docker reset, seed or real prediction occurred.
