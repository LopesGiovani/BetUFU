---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '29'
wave: 11
status: complete
subsystem: isolated-restore
technical_commit: 53e5aac
completed: '2026-10-07'
requires: [07-28, 07-27]
provides: [three-substrate-restore, post-snapshot-suppression-reconciliation, rpo-rto-proof]
affects: [07-30, 07-32]
requirements: [OPS-04, PRIV-05, TEST-03]
actuals:
  tasks: 2
  files: 6
  commits: 2
tech-stack:
  added: []
  patterns: [owned-compose-restore, authenticated-aes-gcm-readback, fail-closed-serving-gate]
coverage:
  - id: isolated-three-substrate-restore
    description: BETUFU, Keycloak and private object data are restored into disposable owned resources.
    verification:
      - kind: integration
        ref: docs/phase7-restore-report.json
        status: pass
    human_judgment: false
  - id: post-snapshot-suppression
    description: An independent sanitized suppression captured after the snapshot is applied before serving.
    verification:
      - kind: integration
        ref: docs/phase7-restore-report.json
        status: pass
    human_judgment: false
---

# Plan 07-29 Summary

The restore foundation now recovers all three backup substrates in a disposable
Compose project, verifies authenticated encrypted artifacts and holds serving
until an independently captured suppression reference has been reapplied.

## Authority and scope

- Inline `master`, technical commit `53e5aac`, continuous owner authority and
  isolated-local-proof operational policy. No production destination, cloud
  availability, legal retention or public-pilot approval is inferred.
- Six technical paths are frozen: approval, restore runner/tests, restore
  Compose file, private restore report and sanitized suppression register.
  Existing owner documents, migrations V1-V46, seeds, volumes and backup runner
  remain unchanged.
- The restore project uses exact generated run IDs, labels and named volumes;
  cleanup is restricted to that project. It never touches the owner's services.

## Delivered behavior

- A fresh isolated backup is created, its AES-256-GCM manifest/HMAC and all
  three component hashes are checked, then PostgreSQL databases and SeaweedFS
  private objects are restored before services are released.
- Keycloak's restored database contains users; no password or credential is
  copied into the report. Login/production provider availability remains a
  separate operational gate.
- A sanitized current register records one post-snapshot isolated suppression.
  The fact and projection are inserted append-only into the restored database,
  and the serving query returns zero for that message before the runner reports
  the restored state usable.
- Missing register, malformed records, authentication failure, corrupt dumps,
  incomplete substrates and RPO/RTO overflow fail closed. Restore output is
  written only after reconciliation succeeds.

## Verification

| Evidence | Final result |
| --- | --- |
| Restore focal tests | 5/5,0fail/cancel/skip |
| Isolated restore | PASS; 1 BETUFU chat fact, 3 Keycloak users, nonempty private objects |
| Suppression gate | PASS; captured after snapshot, applied before serving, served count 0 |
| Measured targets | RPO 18.351s <= 3600s; RTO 12.48s <= 14400s |
| Node aggregate | 943/943,9 suites; 0 fail/cancel/skip; 303064.0688ms |
| Maven verify | BUILD SUCCESS; Surefire392 + Failsafe765 = 1157; 0 fail/error/skip |
| Compose, approval and scope checks | PASS; exact six paths, diff check clean |

## Self-healing and review

- The first restore attempt exposed the backup's `B7G1` envelope prefix; the
  parser now validates it and reads the nonce at the correct offset.
- The next attempt exposed that `execFile` did not close stdin for binary
  `pg_restore`; the command helper now uses `spawn` and closes stdin explicitly.
- RPO initially used the timestamp before snapshot creation; measurement now
  starts when restore receives the completed snapshot. Failed runs restore the
  original register bytes automatically, preventing a stale approval hash.
- RepoWise found only a maintainability advisory for the bounded restore method;
  no security finding or cross-module internal import was present.

## Limits and next work

This is an isolated local proof. It does not certify sustained cloud RPO, key
custody, genuine OIDC password login, legal retention or 50-user load. Continue
Wave 11 with plan 07-33; later plans retain the pending external gates.

## Frozen technical hashes

See `.git/gsd/phase7/07-29-frozen.json` for the six-path digest set and the
captured report values.
