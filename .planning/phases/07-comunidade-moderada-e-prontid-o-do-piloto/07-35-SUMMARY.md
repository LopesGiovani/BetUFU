---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '35'
wave: 14
status: complete
subsystem: chat-rules-ui
technical_commit: c11d2e2704133b5ae0cd46680402896b74bc2f61
completed: '2026-10-29'
requires: [07-08, 07-15, 07-23, 07-34]
provides: [accessible-rules-popup, account-synchronized-dismissal, honest-sync-failures]
affects: [07-30, 07-31, 07-32]
requirements-completed: [CHAT-01, TEST-03, UX-01, UX-02]
actuals:
  tasks: 2
  technical_paths: 12
  commits: 2
tech-stack:
  added: []
  patterns: [official-generated-client, signals-session-epoch, native-dialog]
---

# Plan 07-35 Summary

The first authenticated Community visit opens an accessible rules dialog unless
its account preference was already dismissed. The official generated client reads
and saves that preference on the server, so refresh and another authenticated
browser context preserve it. Regras remains available manually, including to
visitors on public match pages without private preference requests.

Closing by button, Escape or backdrop never constitutes legal acceptance. Native
dialog focus, Tab/Shift+Tab containment and trigger restoration work on desktop
and 320px. GET failures offer retry without claiming dismissal; POST failures
close the dialog while explicitly reporting that synchronization failed. Signals,
cancellation and session epochs prevent late responses crossing account changes.
No persistent browser storage or manually edited generated client is used.

## Authority and scope

Continuous owner authorization covers inline master, tests/self-healing, staging,
two local commits and advancement without push, worktrees or Antigravity.
The nine planned paths plus three recorded mechanical support paths (browser
runner/registration test and report-dialog test selectors) reconcile the twelve
technical paths in docs/phase7-approvals/07-35.json. Existing report assertions
remain intact and explicitly select their report dialog.

## Verification

- Real isolated browser: 2/2 desktop/320px, explicit process exit0. Two accounts,
  two contexts, refresh, manual opening, focus/Escape/backdrop, real offline GET
  and POST failures/retry, actual OIDC logout, account replacement and actual
  Redis expiry passed. Anonymous match viewing issued zero preference requests.
- Angular rules/panel focal:14/14 in3 files; aggregate:533/533 in85 files.
- Node aggregate:946/946 in9 suites, no failures/cancellations/skips.
- Literal Maven verify:1136 fresh XML tests,361 Surefire/37 classes plus775
  Failsafe/150 classes; zero failures/errors/skips, timeout=false,flakes=0.
  Wrapper2272 duplicates aggregation; XML counts are authoritative.
- Generator anti-drift, lint, production build, Spotless and git diff --check passed.
- Twelve technical paths frozen;61 protected baseline hashes preserved in
  .git/gsd/phase7/07-35-frozen.json. Inline source/evidence review found no
  remaining defect. RepoWise tools were unavailable.

## Self-healing and evidence

A real Shift+Tab failure exposed Angular's unmodified key filter; a keyboard
handler now covers both directions, with a discriminating component regression.
The browser tracer now preserves the loaded route during real offline checks,
waits for actual OIDC logout confirmation, opens the account menu after navigation,
and scrolls the deferred match chat into view. No business/auth response is mocked.

A stale compiled PrivacyRetentionIT class blocked test discovery; Maven clean
removed only build output. The subsequent attempt was externally interrupted
at SportsCatalogIT and was not counted as success. The final full literal verify
completed successfully; its own start timestamp and fresh XML reports were checked.
An unchanged Angular timeout passed focal and aggregate repeats without weakening
its timeout or source. Existing production style-budget warnings remain.

Final browser evidence:07-35-browser-seventh.log; Maven:07-35-maven-verify-resume.log;
Angular:07-35-angular-aggregate-repeat.log; Node:07-35-node-aggregate.log;
all under .git/gsd/phase7. V1-V47, append-only facts, owner document hashes and the
owner's absent manual-testing.md state, seeds and owner volumes remain unchanged.

Phase7 is32/35;global167/170. Wave14 is complete; continue07-30 inWave15, then31/32.
The owner ratified50 simultaneous users/300s,60%chat reads/20%navigation/20%predictions,
p95<=1000ms,errors<=1%,zero integrity violations on the isolated local stack.
The owner also explicitly restricted remaining completion to local technical work;
publication/public pilot stay blocked pending legitimate identity/domain/contact/
infrastructure responsibility. These replies are preserved in07-30-proposal.json
and must now be transcribed into governed gates without approving institutional facts.
Rules-preference composition into restricted identity exports remains final integration
work; no public readiness is claimed by this UI closeout.
