---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '28'
wave: 2
status: complete
subsystem: infra
requires: [07-01]
provides: [encrypted-three-substrate-backup, isolated-recurring-scheduler, authenticated-retention-dry-run]
affects: [07-29, 07-30, 07-32]
requirements-completed: []
actuals:
  tokens: 19250
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [quiesced-snapshots, authenticated-encryption, exact-resource-ownership]
coverage:
  - id: automatic-local-backup
    description: Real timer backs up complete BETUFU and Keycloak databases plus private object volume
    verification:
      - kind: integration
        ref: node scripts/phase7-backup.mjs --policy infra/backup/phase7-backup-policy.json --isolated --schedule-once
        status: pass
    human_judgment: false
started: '2026-10-05T00:17:00Z'
completed: '2026-10-05T00:54:48.407Z'
technical_commit: 188c577364e6ee81f72e846adc9e6f8a1192f35c
---

# Plan 07-28 Summary

A real automatic local timer creates authenticated encrypted backups of both
complete databases and the complete private object substrate in owned disposable
resources. Production deployment and full restore remain pending.

## Authority and scope

- Base 8048da6f7395c08335e6cfc8892c440ea333bdfa, inline master.
- Continuous owner authority: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
- Operational decision: conversation:2026-10-04:owner-local-backup-policy;
  owner answered Autorizado to private local destination, separate private key,
  AES-256-GCM and quiesced disposable writers. Public-pilot policy remains pending.
- Seven technical paths: original five plus the exact decision registry and PLAN
  reconciliation. No fabricated legal/institutional approvals; no dependency update.
- Protected owner docs, V1-V38, governed API/client and all 242 baseline hashes
  unchanged. No owner database, seed or Docker volume touched; no push/worktree.

## Delivered

- Pinned existing PostgreSQL18.4, Keycloak26.7.1 and SeaweedFS4.41 services,
  random owned project/volumes/network and loopback ephemeral ports.
- Real Flyway applies/validates 40 migration records through V39 on the disposable
  BETUFU database. A nonempty chat fact, imported Keycloak user/credential and S3
  private object are present; signed object read verifies exact bytes and anonymous
  access fails. Realm export is not used as the Keycloak backup.
- Stop only disposable Keycloak/SeaweedFS writers during both pg_dump custom-format
  databases and full offline SeaweedFS tar. No backend writer deployed in this proof.
- AES-256-GCM with fresh nonces and run/snapshot/component authenticated context;
  HMAC manifest, exact cipher/plain hashes, versions, Flyway/schema/last-fact metadata.
  Read-back and real pg_restore/tar inventories precede atomic manifest publication.
- Private Windows SID ACL/Unix modes, separate key outside snapshots, no plaintext
  dumps on host, sanitized diagnostics. Exclusive lock, real timers, recurring
  schedule option, failed/missing backup rejection and maximum gap measured BEFORE
  the last usable snapshot advances. An overdue previous gap cannot be hidden.
- Authenticated exact owned retention dry-run older than30days; no expiry deletion.
  Exact owned cleanup after checks; encrypted backups/key/report retained for29.

## Verification

- Initial RED: test file failed on absent runner; final focal33/33, no skips/failures.
- Real literal backup execution1: run betufu-phase7-backup-a1384beaaf8faca6,
  snapshot076b5b0f5010b41a, final snapshot age11.263s. Passed.
- Corrected scheduler execution2: run betufu-phase7-backup-b1341ff3c8826e96,
  final snapshot age29.810s; maximum observed gap150.988s including preparation.
  Three nonempty components,40Flyway records; manifest authenticates.
- Final evidence:
  C:/Users/giova/AppData/Local/Temp/betufu-phase7-backup-b1341ff3c8826e96-KIEjtr/report.json
  Separate keys/backup.key stays private, never included in repository evidence.
- Mutated actual cipher bytes in memory were rejected; encrypted original untouched.
  No owned containers/volumes remained; private root has no inherited Windows ACEs.
- Node aggregate916/916,9suites,0fail/skip,419800.5505ms.
- Literal Maven verify BUILD SUCCESS, exit0. Fresh Surefire361/37classes and
  Failsafe622/124classes =983,0fail/error/skip. Three stale focal Surefire IT reports
  excluded. Wrapper1966 duplicates summary lines; XML authoritative.
- Maven began2026-10-05T00:28:43Z; exited by2026-10-05T00:53:00Z. Success deletes its
  temporary log by design. No old-log inference used.
- Central approval CLI passed before implementation and immediately before commit;
  scoped diff check/staged scope and frozen working hashes checked.

## Deviations and review

- Windows icacls rejected a raw SID (exit1332); prefix '*' corrected SID resolution.
  No container had started in that first failed permission attempt.
- Frozen inline review after final formatting: no functional finding. RepoWise
  analyzed2/7code files; five documentary/config paths manually reviewed.22static
  medium/low advisories: method/guard complexity, bounded file/subprocess loops,
  size and parameter count. These are residual maintainability signals; repetitive
  I/O is inherent to exact ownership checks. No independent security certification.
- Scoped registry formatting restored after initial JSON serialization so only
  backup_operations changes; other gates untouched.

## Limits and next work

- This proves a local automatic execution and scheduler defenses, not sustained
  production RPO, public launch, month-long retention or disaster recovery.
- Max component512MiB; local memory buffering. Production scale streaming/provider,
  independent key custody, legal retention and monitoring remain pending.
- OPS-04/TEST-03 are not fully certified by this foundation.07-29 must restore real
  substrates and reconcile later suppression before serving;30/32 certify readiness.
- Wave2 complete5/35 plans. Advance directly to07-05/Wave3 under continuous authority.

## Frozen technical hashes

| Path | SHA-256 |
|---|---|
| .planning/phases/07-comunidade-moderada-e-prontid-o-do-piloto/07-28-PLAN.md | ff9072f35445a9d2e3476a78f942a6f98ea382eacf46fee0631b6dfe6f55b53e |
| docs/phase7-approvals/07-28.json | c098de63a6a86470bdd6a8fb16142729a86e3a69e27c0ac735a49d05e096411b |
| docs/phase7-backup-runbook.md | 0089d8ea9982241a558376468048e52455752c579c2506ea6d997518beec1b41 |
| docs/phase7-decisions.json | 1537c836471562ba33aac2c3ee9bdcbab62722dcaff88760f145320ba1959011 |
| infra/backup/phase7-backup-policy.json | bd064016986dbaec195ba3c2ad3461ff411ec3754a2ca2cb0952df1ed2b1b5c7 |
| scripts/phase7-backup.mjs | 2a57b4d73013e15084dc8f0c3ea845f2a845142c83dceeb20f87b7dad62145bc |
| scripts/phase7-backup.test.mjs | bda38e1b61a2a11a44f71b37968ae26512f01de5195df01547cfaf5420ec062f |
