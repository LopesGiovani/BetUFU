CREATE TABLE public.credit_account (
    identity_id uuid NOT NULL,
    balance numeric(20,2) NOT NULL DEFAULT 0.00,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_credit_account PRIMARY KEY (identity_id),
    CONSTRAINT fk_credit_account_identity FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_credit_account_balance_scale CHECK (scale(balance) = 2),
    CONSTRAINT ck_credit_account_balance_non_negative CHECK (balance >= 0.00),
    CONSTRAINT ck_credit_account_timestamp_order CHECK (updated_at >= created_at)
);

CREATE TABLE public.credit_ledger_entry (
    entry_id uuid NOT NULL DEFAULT gen_random_uuid(),
    identity_id uuid NOT NULL,
    entry_type varchar(32) COLLATE "C" NOT NULL,
    direction varchar(8) COLLATE "C" NOT NULL,
    amount numeric(20,2) NOT NULL,
    source_type varchar(32) COLLATE "C" NOT NULL,
    source_id uuid,
    idempotency_key varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_credit_ledger_entry PRIMARY KEY (entry_id),
    CONSTRAINT fk_credit_ledger_entry_identity FOREIGN KEY (identity_id)
        REFERENCES public.credit_account (identity_id),
    CONSTRAINT uq_credit_ledger_entry_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_credit_ledger_entry_type CHECK (entry_type IN ('GRANT_INITIAL', 'DEBIT', 'COMPENSATION')),
    CONSTRAINT ck_credit_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT ck_credit_ledger_entry_type_direction CHECK (
        (entry_type IN ('GRANT_INITIAL', 'COMPENSATION') AND direction = 'CREDIT')
        OR (entry_type = 'DEBIT' AND direction = 'DEBIT')
    ),
    CONSTRAINT ck_credit_ledger_amount_positive CHECK (amount > 0.00),
    CONSTRAINT ck_credit_ledger_amount_scale CHECK (scale(amount) = 2),
    CONSTRAINT ck_credit_ledger_source_type CHECK (source_type IN ('SYSTEM', 'PREDICTION', 'COMPENSATION')),
    CONSTRAINT ck_credit_ledger_source_octets CHECK (octet_length(source_type) <= 32),
    CONSTRAINT ck_credit_ledger_idempotency_not_empty CHECK (btrim(idempotency_key) <> '')
);

CREATE UNIQUE INDEX uq_credit_ledger_initial_grant_only
    ON public.credit_ledger_entry (identity_id)
    WHERE entry_type = 'GRANT_INITIAL';

CREATE INDEX idx_credit_ledger_entry_identity_created
    ON public.credit_ledger_entry (identity_id, created_at, entry_id);

CREATE OR REPLACE FUNCTION public.prevent_credit_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'credit ledger is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_credit_ledger_modification
BEFORE UPDATE OR DELETE ON public.credit_ledger_entry
FOR EACH ROW EXECUTE FUNCTION public.prevent_credit_ledger_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.credit_account TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.credit_ledger_entry TO %I', runtime_role);
END $$;
