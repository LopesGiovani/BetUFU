# Phase 5 Verification Snapshot

Snapshot date: 2026-09-13
HEAD: `bf6ad6cb94166c687ec70eaf4cc578c0f4861223`

## SHA-256

- `backend/src/main/resources/db/migration/V29__create_virtual_credit_ledger.sql`: `D84F77372EAF859E25B09EE976FA52AB16596EA65DC522C349AF8714ECA64E61`
- `backend/src/main/resources/db/migration/V30__create_prediction_records.sql`: `BCB9731DAC52FE362EE4381FF58AA784AF73B6F1C3536B29A2CC0443B0482C4D`
- `backend/openapi/betufu.yaml`: `D9541EF300997B4CA7C23F9F71C4BCF37BE478163CAACC4055F63B284D97FA48`
- `scripts/validate-phase5-negative-mutations.mjs`: `98858C0311FEBD1F570E8587D0951A462D809ADEB0D2FE41BAF08B7FEBD301FD`
- `frontend/playwright.prediction.config.ts`: `364AC472087AFAD5BD97E57FDFEAD7EE7E29009696491AEB5C681CC2D8F05D7D`
- `frontend/e2e/prediction-pilot.spec.ts`: `F1C95D2937A10CAF53E924FF7A5BE2291CEDAC7F5D60A8398C2A71CB02F9D0E0`

## Gate Status

Backend/unit/negative and Angular gates are green as listed in `05-VALIDATION.md`.
OpenAPI generation and browser execution are explicitly `BLOCKED`, not skipped.

## Human Checkpoint

Checkpoint `05-05/T3` remains pending. The Phase 5 status must not be marked
complete until the generator/browser blockers are resolved and the responsible
developer approves this snapshot.
