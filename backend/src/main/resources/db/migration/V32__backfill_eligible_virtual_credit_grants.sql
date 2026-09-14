INSERT INTO public.credit_account (identity_id, balance)
SELECT DISTINCT enrollment.identity_id, 0.00
FROM public.ufu_enrollment enrollment
WHERE enrollment.status = 'ATIVO'
ON CONFLICT (identity_id) DO NOTHING;

WITH inserted_grants AS (
    INSERT INTO public.credit_ledger_entry (
        identity_id,
        entry_type,
        direction,
        amount,
        source_type,
        source_id,
        idempotency_key
    )
    SELECT DISTINCT
        enrollment.identity_id,
        'GRANT_INITIAL',
        'CREDIT',
        1000.00,
        'SYSTEM',
        enrollment.identity_id,
        'credit:initial-grant:' || enrollment.identity_id::text
    FROM public.ufu_enrollment enrollment
    WHERE enrollment.status = 'ATIVO'
    ON CONFLICT DO NOTHING
    RETURNING identity_id
)
UPDATE public.credit_account account
SET balance = (account.balance + 1000.00)::numeric(20,2),
    updated_at = CURRENT_TIMESTAMP(6)
FROM inserted_grants grant_entry
WHERE account.identity_id = grant_entry.identity_id;
