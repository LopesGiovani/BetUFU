---
phase: 02-contas-verifica-o-e-opera-es-governadas
wave: 8
reviewed: 2026-08-24T02:06:16Z
depth: deep
files_reviewed: 48
findings:
  critical: 0
  warning: 0
  info: 0
  total: 0
status: clean
---

# Phase 2 — Wave 8: Final Code Review

## Verdict

**VERDICT: PASS**

The final Wave 8 snapshot has no P0, P1 or P2 findings. The twelve findings from the
previous review were corrected and rechecked through source inspection, focused
discriminating tests, complete suites and cross-file call-chain analysis.

## Snapshot Integrity

- Worktree: `C:/Users/giova/gsd-workspaces/phase-02/wave-08/02-17`
- Branch: `worktree-agent-02-17`
- Base: `c2277a92113e46af2203991e4e63337ea705e38d`
- Reviewed HEAD: `565040931ed0ef512c9ed5f3d66b419542e6bcf7`
- Scope: 52 changed or untracked paths against the base; 48 source, configuration,
  migration and test paths reviewed after excluding planning artifacts.
- Freeze check: HEAD, `git status -uall` and all 52 SHA-256 values were identical
  before and after the read-only review.
- This report was rewritten only after that comparison and is not part of the code
  snapshot whose integrity was checked.

## Verification Results

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| Backend Surefire | 217 | 0 | 0 |
| Backend Failsafe | 276 | 0 | 0 |
| Keycloak module | 56 | 0 | 0 |
| Node validators | 580 | 0 | 0 |

The backend aggregate was executed with `clean verify`. The Node suite and Keycloak
module were also executed from the reviewed worktree. XML reports, rather than only
console summaries, supplied the backend counts.

## Resolved Findings

1. The real `ApplicationRunner` bootstrap now wraps grant and audit in one transaction;
   a failing audit proves the grant rolls back.
2. HMAC material now requires at least 32 decoded bytes in properties and signer; the
   31/32-byte boundary is tested.
3. Partial session termination remains `FAILED` in direct delivery, lease recovery and
   administrative replay.
4. A claim commits before the external effect; a crash seam proves `CLAIMED` survives and
   can be reclaimed after lease expiry.
5. A disabled-by-default scheduled production driver performs bounded recovery, retention
   cleanup and FAILED-count monitoring.
6. Replay audit intent is committed with the claim, while result audit is committed with
   the final state; audit failure prevents `DELIVERED`.
7. Malformed and extreme timestamps fail closed with HTTP 401 instead of overflowing to 500.
8. The OpenAPI status vocabulary matches the runtime states.
9. FAILED-event listing is stably paginated and capped at 100 entries.
10. The recovery validator inspects the real controller, exact mappings, both authorization
    guards, maintenance driver and security-chain separation.
11. OpenAPI parity is bidirectional and derived from Spring mapping annotations.
12. Generated-client credential checks also reject literal bracket-property access.

## Additional Security Checks

- The recovery endpoints have two independent guards: the Spring filter requires
  `ROLE_ADMINISTRADOR_GERAL`, then the controller resolves the exact `(issuer, subject)`
  pair and requires an active global grant with `scopeRef == null`.
- The password-reset machine-to-machine endpoint remains in its dedicated stateless HMAC
  chain; an OIDC session cannot substitute for the signed envelope.
- `GrantedAuthoritiesMapper` wiring was confirmed against the local Spring Security 7.1.0
  bytecode: `OAuth2LoginConfigurer` obtains the unique mapper bean and installs it on both
  OAuth2 and OIDC authentication providers.
- The platform-owned identity lookup keeps the Modulith dependency direction
  `identity -> platform`; architecture tests remain green.
- Retention deletes only old `DELIVERED` records. `FAILED` evidence is retained.
- Replay uses a closed audit action introduced by Flyway V8 and covered by migration and
  audit integration tests.

## Declared Boundaries

Wave 8 builds and verifies the structure but does not activate password-reset delivery in
production. The real Keycloak-to-backend cryptographic E2E gate remains assigned to Plan
02-29 before activation, as already recorded in the phase planning artifacts. This is a
declared future activation gate, not an omitted Wave 8 assertion.

The Keycloak build reports a deprecation warning for `AuthenticationManager.AuthResult#getUser()`;
the module is unchanged by Wave 8 and all 56 tests pass. Git also reports the repository's
existing LF/CRLF normalization warnings; `git diff --check` found no whitespace errors.
