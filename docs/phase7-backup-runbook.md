# Phase 7 local backup proof

## Scope and authority

The owner approved this isolated local configuration on 2026-10-04
(`conversation:2026-10-04:owner-local-backup-policy`). Continuous Phase 7
execution authority covers the runner, Docker proof, tests and local commits.
The production provider, destination and operational key custody remain pending.
This does not approve public launch or certify OPS-04 before the restore proof.

## Execute

Prerequisites: the existing Docker daemon and pinned images in
`infra/compose.yaml`, Java/JAR and the packaged backend from the approved Maven
build. No new dependency or cloud tool is installed.

```powershell
node scripts/validate-phase7-approvals.mjs --plan 07-28 --stage implementation
node --test scripts/phase7-backup.test.mjs
node scripts/phase7-backup.mjs --policy infra/backup/phase7-backup-policy.json --isolated --schedule-once
```

Approval is bound to the pre-execution HEAD and policy hash; after the plan's
commits it is historical. A later execution needs an updated approved scope
record under actual owner authority, rather than reuse of stale evidence.

The runner creates three services with random project names, no shared volumes
and only temporary loopback ports. It applies the real Flyway migrations to the
new BETUFU database, imports the real realm and an isolated Keycloak user, and
writes/verifies a private S3 object (anonymous access must fail). No existing
database, seed or volume is copied, altered or deleted.

A real Node timer triggers the first backup after preparation. `--schedule-once`
observes one automatic execution. The policy specifies an 1800-second interval
with margin below the 3600-second RPO. The optional `--schedule` mode repeats
the timer until SIGINT/SIGTERM and uses the same lock and RPO checks; stopping
between ticks produces the report and cleans only owned resources. The maximum
observed gap is checked before advancing the last usable snapshot, so a new
backup cannot hide a previously missed deadline. `--schedule-once` does not
install an ongoing production scheduler. Production scheduling and continuous monitoring remain
part of deployment readiness. A single short execution cannot prove a month of
retention or a sustained one-hour RPO guarantee.

## Consistency and encryption

Only disposable Keycloak and SeaweedFS writers are stopped during all snapshots;
the application backend is absent from this proof, so it cannot concurrently
write the BETUFU database. PostgreSQL remains up for two complete custom-format
database dumps. SeaweedFS's complete persistent volume is copied as a tar archive
while stopped, including metadata and object data. This requires the same pinned
SeaweedFS version for recovery. A realm JSON export is not the Keycloak backup.

Each component is encrypted in memory using AES-256-GCM with a fresh 96-bit nonce.
Run, snapshot and substrate identifiers are authenticated additional data.
Manifest metadata, versions, schema hashes, Flyway checksums, last chat cursor,
private object hash and cipher hashes are HMAC authenticated. Read-back verifies
exact decrypted bytes and real `pg_restore --list`/tar inventories before an
atomic manifest publication. No plaintext dump is written to the host.

The runner prints only its private report location, run ID and measured gap.
Docker/Java output is captured and never forwarded because it may contain secrets.
Errors produce a sanitized alert file and nonzero exit code. An exclusive lock
rejects overlap. An unusable or corrupted backup never advances the usable
snapshot timestamp. RPO uses snapshot time, including time spent copying and
validating, rather than the time a scheduler is labelled hourly.

## Artifact custody and restore handoff

The runner-created temporary root is restricted to the current OS account.
On Windows it removes inherited ACLs and grants the current SID; on Unix it uses
0700 directories and 0600 files. The key is stored separately in `keys/backup.key`,
outside `snapshots/`. Keep both under their restricted permissions for 07-29;
do not copy keys into reports, Git, tickets or the encrypted artifact directory.
Local separation is not independent off-site custody or a production key vault.

Layout (under the private path printed by the runner):

```text
owner.json
keys/backup.key
snapshots/snapshot-<random-id>/
  manifest.json
  betufu.aesgcm
  keycloak.aesgcm
  private-objects.aesgcm
report.json
```

The runner checks full ownership before deleting only its exact disposable
containers, volumes, network and scratch directory. Encrypted artifacts and keys
are retained for the subsequent restore plan. It never uses broad Docker prune
or volume deletion commands.

Retention is 30 days, independent of backup frequency. `retentionCandidates`
performs a dry run: it requires authenticated manifests, the exact run and snapshot
name, exactly three component files and no links or foreign paths. It lists only
snapshots older than 30 days; it deletes none. Actual expiry requires an explicitly
scoped operation and the final legal/operational policy. Owner data is excluded.

07-29 must restore into separately owned resources, reconcile the current
suppression register before serving, and verify users, private objects and facts.
The 07-28 report explicitly says restore has not been executed. Later readiness
must also exercise missed/failed runs and sustained RPO/RTO monitoring.
The local runner limits each captured component to 512 MiB and fails with an
alert above that bound; production-scale streaming is not certified by this proof.
