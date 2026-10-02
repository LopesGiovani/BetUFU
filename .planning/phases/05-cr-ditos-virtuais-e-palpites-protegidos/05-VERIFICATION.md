---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
verified: 2026-10-02T15:08:38-03:00
status: passed
score: "5/5 original must-haves verified; 05-08 seed reconciliation recertified"
behavior_unverified: 0
overrides_applied: 0
---

# Phase 5 Verification Snapshot

Snapshot date: 2026-09-14
Base and HEAD: `d263f0b63cf36c20dcecb671ddd236d56cc71813`
Branch: `worktree-agent-05-05-recert`
Worktree: `C:\Users\giova\gsd-workspaces\phase-05\05-05-recert`

## Integrity Anchors

- V29 ledger: `D84F77372EAF859E25B09EE976FA52AB16596EA65DC522C349AF8714ECA64E61`
- V30 predictions: `BCB9731DAC52FE362EE4381FF58AA784AF73B6F1C3536B29A2CC0443B0482C4D`
- V31 publication selections: `1DB6E1CE197DFF9F12ED9F293B78956BBFD9DE8E6E17772B07BC66662A4AB204`
- V32 retroactive grants: `850EB3DE83BD308D2B9E301BBC9F1BC6E2485653DF3FC7343B6C548A125F91D7`
- `PredictionService.java`: `CB0D049776474F1800594F41F9D3E6507FBBF7A4CDE4146E84BBC76BAA59BBB6`
- `OddsPublicViewRepository.java`: `6757E0663342BCB1F2F6650552AA175C56038721B234B492D8C5EABB8D96D9D4`
- `OddsPublicViewIT.java`: `5DFBE3471DD2DC2933A843930323E717BAF43EB1F06C3C65B31040FF7DE6E028`
- OpenAPI contract: `5888C6A2946B87D22190B7BF7B8F40EAE0F2F18A53EEBEA6C840EDB54DE3577F`
- Approved OpenAPI Generator 7.24.0 JAR: `4B83CCC6FD43056C8C631CD0195E5100BD0550912502527BAB09AC76152DAB0C`
- Prediction Playwright spec: `76D2FA1F2C3812249C19DDF879E445E34066B86AF76673E6252745C6DFF5F251`
- Prediction Playwright config: `4A65579C5A78196D859EA511739445DF8C5BB04768AAAE5ECD72DC2F91FCB7BD`
- Negative harness: `E23828B59A787F8605C2C8E25D665064AED22D4125215B592678EC0BDC119796`
- Negative harness unit: `4818288A94B2D15441AD1702F84E958F672FD4EE2FDBB876034051731D8C47B0`
- Technical manifest, 63 non-planning changed/untracked paths: `20AC9D8375B46EEC19FE7ECD6DA4B81DF018456BBF63774192BD9DC24F62C381`

### External Test Reports

- `frontend/tmp/prediction-playwright-reports/results.xml`: `A11C18C15CE21265FC79A0370012D046762F0EB7F65AE6EFBBE8EC071FC9DB2B`
- `frontend/tmp/prediction-playwright-reports/results.json`: `87ED1C6283E8C25950F5FA6BCFD2A0E67E173BE33A64987908540DA2B9219CEE`

These reports and other files under `frontend/tmp/` are ignored local evidence,
not versioned snapshot paths. Documentation hashes are captured externally
after finalization to avoid self-reference.

## Gate Status

- PASS: repository Maven lifecycle, Surefire 327/327 plus Failsafe 534/534,
  total 861/861, with Spotless completing successfully.
- PASS: focused PostgreSQL/Testcontainers and architecture suite 88/88,
  including Modulith 3/3 and privacy architecture 15/15.
- PASS: public-view lineage regressions prove that divergent publications are
  ignored and ambiguous nearest-ancestor publications fail closed.
- PASS: seven discriminating negative mutations and restored source/bytecode.
- PASS: Node aggregate 747/747; OpenAPI generator 73/73; client anti-drift.
- PASS: Angular 384/384 across 62 files after one unrelated timeout at 383/384,
  followed by the affected spec at 3/3 and a clean aggregate repetition.
- PASS: Playwright 14/14 in Chromium and Firefox, desktop and 320 px.
- PASS: `git diff --check`; 70 snapshot paths covered by the approved final
  allowlist; stage empty.

## Snapshot Rule

All automated blockers from the previous snapshot are resolved. Any technical
or documentation correction after this file is finalized invalidates the
frozen hashes and requires the affected checks, manifest and read-only review
to be repeated.

The technical manifest is reproducible by sorting the 63 non-`.planning`
snapshot paths ordinally, emitting uppercase `SHA256  path` lines encoded as
UTF-8 without BOM with LF endings and exactly one final LF, then hashing those
bytes with SHA-256. The seven documentation hashes and the corresponding full
70-path manifest are captured externally after this file is finalized to avoid
self-reference.

## Human Checkpoint

The responsible developer approved checkpoint `05-05/T3` on 2026-09-14 after
the corrected snapshot received independent read-only review `PASS`. Phase 5
is complete. No stage, commit, integration, worktree cleanup or push is
authorized by this checkpoint.

## Post-05-08 Supplement - 2026-10-02

This supplement addresses the additional 05-08 local-seed plan completed after
the original 2026-09-14 snapshot. The original integrity anchors and human
checkpoint above remain historical evidence, not hashes of the current checkout.
The 05-08 summary records a disposable local reset, full seed twice, five
complete publications and one `1000.00` initial grant per eligible identity on
2026-09-15. Those persistent-environment actions were not repeated today.

At HEAD `573ba71c3115592ee12068d02aac18e4bf0ec696` on `master`, fresh
commands passed:

- `node --test scripts/seed-local.test.mjs`: 11/11.
- `node --test scripts/*.test.mjs`: 759/759.
- `node scripts/maven.mjs -f backend/pom.xml clean verify`: 360/360 Surefire
  cases across 37 classes and 573/573 Failsafe cases across 105 classes.
- `git diff --check`: passed with only LF-to-CRLF checkout warnings.

All tests reported zero failures, errors and skips. The Maven wrapper's
1,866 count duplicates summary lines; the 933 XML cases are authoritative.

Current SHA-256 anchors: `scripts/seed-local.mjs`
`CEA2BDA49E6111FC7EB90409880E8EC9ED13DA05A8F9E4F4DF28D0721F22197D`;
`scripts/seed-local.test.mjs`
`CB567277A89970028D3D87ED83654771E183E99F8B6D620212F85418D0D50764`.
V31 and V32 retain their recorded migration hashes. Angular and Playwright were
not rerun in this targeted supplement; their original snapshot and the later
05.1 integrated verification remain separate evidence. No current full
70-path manifest or new independent review is claimed.
