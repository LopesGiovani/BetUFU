---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
plan: "03"
status: complete_with_blocker
completed: 2026-09-13
---

# Plan 05-03 Summary

Implemented the governed REST surface for credits and predictions.

## Delivered

- `GET /api/v1/me/credits` returns only the authenticated user's balance, scale,
  minimum stake, and rules version.
- `POST /api/v1/me/predictions` requires `Idempotency-Key` and delegates to the
  public acceptance port; `GET /api/v1/me/predictions/markets/{marketId}` returns
  only the user's own ticket.
- OpenAPI schemas and paths were added without exposing tokens, roles, enrollment
  data, or third-party predictions.
- Controllers resolve identity server-side through `OperationalIdentityLookup`.

## Verification

- `node scripts/maven.mjs -f backend/pom.xml "-Dtest=CreditBalanceControllerIT,PredictionControllerIT" test` - 4/4.
- `node frontend/scripts/generate-api.mjs --check` - blocked by `GENERATOR_FAILED`.
- `git diff --check` - clean before commit.

## Git

- Implementation commit: `bfa5dc0`.
- Documentation commit: pending in this block.

## Follow-up

The approved OpenAPI generator must be repaired or made available before the
generated Angular client can be certified; generated files were intentionally
left untouched.
