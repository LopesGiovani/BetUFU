---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '30'
wave: 15
status: complete
scope: isolated-local-technical-only
subsystem: local-quality-security-load
technical_commit: 32b6f925ec1bed220e79b161e0eda5ebd6480e2d
completed: '2026-10-30'
requires: [07-02, 07-03, 07-23, 07-26, 07-27, 07-29]
provides: [serial-quality-gates, resolved-dependency-security-scan, measured-local50-user-load, fail-closed-publication]
affects: [07-31, 07-32]
requirements-completed: [TEST-05, TEST-03]
actuals:
  tasks: 2
  technical_paths: 18
  commits: 2
tech-stack:
  added: []
  updated: [Spring-Boot4.1.2, Angular22.2.4, Angular-CLI-build22.2.5, OpenAPI-wrapper2.42.0, Vitest4.1.12, basic-ftp6.2.4]
---

# Plan 07-30 Summary

The local quality pipeline stops on Maven, Node, Angular, Flyway/Modulith,
generated-client drift, lint, production-build, load, diff or vulnerability failure.
GitHub Actions uses pinned existing actions and read-only repository permission.
The workflow definition was verified locally; no hosted workflow, deployment,
external application pentest or public launch was executed or certified.

## Authority and scope

The owner continuously authorized inline master implementation, self-healing,
testing, exact local stage/two commits and advancement. Replies at
request_user_input_async:call_eb33ae3b9af2403f80aef51d073a80a9 items0/1 ratified
the isolated50-user/300s profile and LOCAL TECHNICAL completion only. Publication,
institutional identity, domain, contact and infrastructure responsibility remain
pending. Their original publication obligations were retained separately.

Dependency remediation in backend/pom.xml, frontend/package.json and its lockfile
was expressly approved in request_user_input_async:call_e17e31501bfe4d25a5c33969e69d223f:item0.
Fourteen core paths plus these three bounded support paths and the reconciled PLAN
produce18 technical paths; the existing15-path PLAN validator was not weakened.
Java25, Node22.22.3 and the OpenAPI Java engine7.24.0 stay fixed.

## Delivered behavior and proof

- Real isolated OIDC preparation authenticates50 distinct accounts. The closed-loop
  workload measures300.9482859s with60%chat reads/20%navigation/20%predictions
  (8827/2900/2900 requests), including300 accepted predictions and2600 same-key
  real retries. Ledger balances and stored receipts were checked for all50 accounts.
- Actual14627 requests,48.6030requests/s,p95=28.3289ms,0%errors and zero integrity
  violations passed the ratified p95<=1000ms/error<=1%/zero-integrity limits.
  This bounded seeded-market local proof is neither distinct-market write
  saturation nor a cloud benchmark. Only its owned resources were removed.
- Resolved239 Maven and623 unique npm package/version queries against OSV found
  zero advisory matches, without severity exemptions. Missing, incomplete, failed,
  stale or changed-input scans block. npm audit also reported zero vulnerabilities.
  This dependency scan does not substitute for an application pentest.
- Native loopback FTP listing/download through the actual get-uri consumer passed
  before and after its narrowly scoped basic-ftp6.2.4 override. No force audit fix
  or direct major dependency replacement was used.
- Institutional publication validation returned the expected exit1 with
  INSTITUTIONAL_IDENTITY. No missing institutional fact was fabricated.

## Verification

- Literal Maven verify: BUILD SUCCESS,1136 fresh XML tests:361 Surefire/37classes
  and775 Failsafe/150classes; zero failures/errors/skips. The wrapper's2272 count
  duplicates aggregation; XML counts are authoritative.
- Final Node aggregate:960/960 in9suites, zero failure/cancellation/skip/todo;
  focal quality/load/publication:14/14.
- Final literal Angular command:533/533 in85files,exit0. Generator anti-drift,
  lint, production build, Spotless and git diff --check passed.
- Eighteen technical paths and58 protected baseline hashes were frozen and
  reviewed inline in .git/gsd/phase7/07-30-frozen.json. RepoWise was unavailable;
  no independent graph or external review is claimed.

## Self-healing and limits

The load runner now completes module evaluation before dynamic authority import,
reads event detail for market identifiers, and selects only OPEN markets with
sufficient remaining deadline. Rejected closed-market requests were not hidden;
the runner was corrected and the complete five-minute run repeated.

Angular aggregate failures migrated between files; reducing workers alone then
exposed use of a destroyed ApplicationRef. The installed Angular runner defaults
to non-isolated files. Native --isolate plus two Vitest workers passed the full
suite twice, including the final literal command. The npm test script and quality
executor preserve that configuration. Assertions and five-second limits remain.

Final logs under .git/gsd/phase7:07-30-quality-remediated.log (successful Maven,
initial Node and the interrupted-at-failure Angular stage),07-30-quality-remaining.log
(successful generator/lint/build/load/security),07-30-final-verification.log
(final14/960/533/Spotless/diff),07-30-publication-blocked.log. All process exits were
observed. Old failed or pre-remediation load/security evidence was superseded.

V1-V47, append-only facts, owner document hashes/absent manual-testing.md state,
seeds and owner volumes are preserved. No push/worktree/Antigravity was used.
Phase7 is33/35;global168/170. Continue07-31/Wave16 then07-32. Public-pilot and legal
publication readiness remain blocked. Rules-preference composition into restricted
IDENTITY exports remains final integration work.
