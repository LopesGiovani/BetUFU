---
phase: 06-resultados-confirmados-rankings-e-grupos
verified: 2026-10-03T19:20:57-03:00
status: passed
score: "5/5 plan must-haves; all mandatory final gates passed"
overrides_applied: 0
technical_commit: 0a5f02587e77535e42d43c0e407e380249bb41d8
---

# Phase 6 Verification

Base: bf31d518a69333d85636f0169952cc42069fedc9. Direct inline master;
no worktrees or Antigravity. Technical commit has exactly 14 approved paths.
No failed gate was overridden.

## Must-Have Verdicts

| Claim | Verdict | Evidence |
|---|---|---|
| Concurrent settlement/refund/correction/ranking/achievement retries converge | PASS | Phase6ConcurrencyIT3/3, four workers on PostgreSQL and aggregate predecessor recovery tests |
| Common-user history/ranking/group/achievement journey without business mocks | PASS | Genuine Keycloak and API-created populated facts; Chrome16/16 desktop/320px, separate nonmember and maker-checker sessions |
| Aggregate repository/contract/build/browser/migration gates reproduce | PASS | Backend974/974 XML, Angular455/455, Node812/812, focal guards47/47, generator/lint/build/Spotless/source validator/diff passed |
| Corrections affect future odds input without rewriting past replay | PASS | OddsReplayCorrectionTest10/10, exact cancellation-knownAt and historical/future corrected cut |
| Every mandatory gate supports passed | PASS | Final clean Maven lifecycle and all browser cases passed after approved remediation |

The phase goal is supported by V33-V38 foundations, idempotent settlement and
corrections, rebuildable lifetime ranking, private groups, nonfinancial derived
revocable achievements, governed HTTP/Angular journeys and final real execution.
All assigned requirement IDs have evidence mapped in 06-VALIDATION.md.
REQUIREMENTS.md Pending tracking cells were not in the documentary allowlist
and still need scoped reconciliation; no requirements were silently rewritten.

## Frozen Technical Snapshot

Freeze: 2026-10-03T19:19:44-03:00, base/HEAD
bf31d518a69333d85636f0169952cc42069fedc9; empty index, one master checkout.
Owner docs, tmp and CLI artifacts were explicitly excluded. Source was frozen
through review/stage. Format: sorted relative path, space, uppercase raw-file
SHA-256, LF joins and final LF. Manifest digest:
F5DB48D471CE84C2FFEA57D84457A7153D604FECAC35E03C30F8C26F98552BAF.

| Path | SHA-256 |
|---|---|
| backend/src/test/java/br/com/betufu/odds/OddsReplayCorrectionTest.java | 351B7ED21ECD0E25B81393A216F34978256E01AEE3F6C50B3B6E3410BADD32C6 |
| backend/src/test/java/br/com/betufu/phase6/Phase6CertificationIT.java | B39B91F96DCF3902C767A7D527C4CD8A517F890D7029E2901C38065680714542 |
| backend/src/test/java/br/com/betufu/phase6/Phase6ConcurrencyIT.java | 5381703426C69BA4FFBDCF03CBCF523DDF9A517AD2EA10CC83BBE4829E2B21A6 |
| backend/src/test/java/br/com/betufu/phase6/Phase6PrivacyIT.java | A3868E42367B560DD2ACD2C2583BDC93C808FB22774EB7AFB70F3708F73F72BF |
| frontend/e2e/phase6.setup.ts | 77634773712608BEA4F2EE10966D9915EFAE635D94B39B737884764E38D1D664 |
| frontend/e2e/phase6-corrections.spec.ts | 36945C71B46B1B6DFEA17411C94C63F48DA81A0B3BD6A8397DC449A4283F0BF6 |
| frontend/e2e/phase6-results-ranking.spec.ts | 2343CAAC23EDC518A71EF1549DAA769FC853930B2E9833BA907262FFE345AC72 |
| frontend/playwright.phase6.config.ts | 8EC39E32BFECF573502FE78EB64C3ADD7F0E4C4FDE5C49BFC0E9A2F95E6C5688 |
| frontend/src/app/features/ranking/group-page.component.spec.ts | C135CA9BDB97EEE492355295672E3D34DCDB661AC79CC9F662A3E502C2746F24 |
| frontend/src/app/features/ranking/group-page.component.ts | AFD6CB043D142D3EEEB4AC23376D229A1F58EF943E33BB959FA756AB4EEE37DA |
| scripts/phase6-e2e.mjs | FBA281DB3F3ED6BD7034E875B56D394C5116D81CD8060C1177CE74FBBCB1C6C4 |
| scripts/phase6-e2e.test.mjs | 9A74ABF38230F5F0D8D775E24BFC99FACA4BB30C9CBC26361B4BA0233F373EEE |
| scripts/validate-phase6.mjs | A4E0C1C704294C26CBAFFEFE848732EF590F0332ECB0D47B9A8F3C2101C466AF |
| scripts/validate-phase6.test.mjs | 8600707E73D53013309FF0377FF430D8D486397BB858FBD237C49AEDBEF7B731 |

## Review And Limits

RepoWise review was complemented by manual inspection of all 14 paths.
Whole-diff analysis covered three tracked source files; recommendations are
inferred, not measured coverage. Duplicated odds-test assertion advisories
concern unchanged displaced blocks. Runner/validator complexity is a maintenance
risk; no blocking functional finding remained. No independent external full
snapshot PASS is claimed. Production change is the approved FormsModule
import; migrations, OpenAPI/generated output and dependencies did not change.

Phase6PrivacyIT checks actual DTO serialization and service isolation, not
the whole HTTP filter chain. Existing ControllerIT and real API responses
complement it. Source scans are finite patterns, not universal security proofs.
Four-worker races certify bounded invariants, not all schedules or load/SLOs.

Redis TTL shortening proves real server-store expiry/401/context recovery,
not natural eight-hour waiting. Inactive enrollment is controlled isolated
substrate, not a new admin operation. Real maker-checker corrections are HTTP
operations in separate browser sessions, not a new editor UI. Focus/single-main/
overflow/320px checks are not a full WCAG/screen-reader audit or pentest.
Read caps, projection costs and CSS warnings remain pilot-scale risks.

## Preserved Files And Environment

| Owner path | Unchanged SHA-256 |
|---|---|
| docs/development.md | DEAF6470ACF46C158D53EC66784916DD021CD8464336FF583EE780E0F9898FE8 |
| docs/local-seed.md | EF1DA4213B92E1DB908CCF370390C24E412C0891E3EF7F8D17C5D2092A925FCC |
| docs/manual-testing.md | D4E1DF426A38B6C680A0A864997F01D5692B687308FD944F8EE6161491F3307F |

Seven original services remain healthy at V38. Final read-only counts match
baseline: predictions2 and settlements/corrections/awards/groups0. Owner
predictions/results/financial/group facts were not changed by E2E.
Final isolated run removed only its containers/network/volumes and generated
private config. No labelled isolated runtime resources remain. Fourteen image
tags from seven runs remain; no prune occurred.

Preexisting tmp and .playwright-cli remain unstaged. Traces/screenshots/video
are disabled; failed output is not retained. Credentials/private response bodies
are not included here. Transient XML/output supplied the literal counts.
Two local commits are authorized; no push occurred. Phase 6 is 8/8 and passed.
Phase 7 is unstarted, pending separate discussion/planning approval.
