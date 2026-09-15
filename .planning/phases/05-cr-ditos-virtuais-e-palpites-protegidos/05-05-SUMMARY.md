---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
plan: "05"
status: complete
completed: 2026-09-14
snapshot_date: 2026-09-14
snapshot_head: d263f0b63cf36c20dcecb671ddd236d56cc71813
---

# Plan 05-05 Summary

Plan 05-05 closes the automated certification gaps for virtual credits and
protected predictions. Backend, frontend, contract, formatting, browser and
negative-mutation gates are green. Independent read-only review returned
`PASS`, and the responsible developer approved human checkpoint `05-05/T3`.

## Delivered Evidence

- PostgreSQL/Testcontainers proves idempotent grants, numeric scale/HALF_UP,
  pessimistic locking, append-only facts, atomic prediction/debit persistence,
  divergent retry rejection, rollback and non-negative concurrent balances.
- Public odds and prediction acceptance use authoritative market, publication,
  competition, deadline, rule and selection snapshots.
- The anonymous public view follows only the active version ancestry, ignores
  publications from divergent branches and fails closed on cyclic or ambiguous
  nearest-ancestor publication histories.
- The Angular Signals flow uses the generated OpenAPI client, preserves the own
  ticket across reload and handles stale refreshes fail-closed before allowing
  a newly confirmed POST.
- Privacy evidence covers HTTP metadata/bodies, DOM, console and Web Storage
  without PII, tokens, roles, capabilities or third-party tickets.
- Chromium and Firefox cover the seven pilot journeys at desktop and 320 px.
- Repository Spotless and fatal style-budget blockers from the prior snapshot
  were corrected without changing product behavior.

## Verification Outcome

- Repository Maven: PASS 861/861 (`327` Surefire + `534` Failsafe).
- Backend recertification focus: PASS 88/88; Modulith: PASS 3/3.
- Negative harness: PASS 7/7 with restored source and bytecode.
- Node: PASS 747/747; OpenAPI generator: PASS 73/73; anti-drift: PASS.
- Angular: PASS 384/384 across 62 files after an unrelated timeout at 383/384,
  an isolated 3/3 pass and a clean full repetition; lint and build: PASS.
- Playwright: PASS 14/14 across Chromium and Firefox.
- Spotless, allowlist reconciliation and `git diff --check`: PASS.

## Checkpoint

Human checkpoint `05-05/T3` was approved on 2026-09-14. Plan 05-05 and Phase 5
are complete. Technical commit `82afb96` contains the certified 63-path
snapshot. No integration, cleanup or push has been performed.
