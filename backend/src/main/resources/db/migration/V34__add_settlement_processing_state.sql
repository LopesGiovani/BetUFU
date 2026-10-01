ALTER TABLE public.sports_outcome_fact DROP CONSTRAINT ck_sports_outcome_fact_kind;
ALTER TABLE public.sports_outcome_fact ADD CONSTRAINT ck_sports_outcome_fact_kind
    CHECK (outcome_kind IN (
        'SPORTING_RESULT', 'ADMINISTRATIVE_WALKOVER',
        'ABANDONED_NO_VALID_RESULT', 'EVENT_CANCELLED'
    ));

CREATE OR REPLACE FUNCTION public.validate_settlement_authorization()
RETURNS TRIGGER AS $$
DECLARE
    outcome_kind varchar(40);
    event_status varchar(16);
    current_fact_id uuid;
    current_version_id uuid;
    payload_version_id uuid;
    version_status varchar(32);
BEGIN
    SELECT f.outcome_kind, e.status, h.fact_id, e.confirmed_version_id
    INTO outcome_kind, event_status, current_fact_id, current_version_id
    FROM public.sports_outcome_fact f
    JOIN public.sports_event e ON e.event_id = f.event_id
    LEFT JOIN public.sports_outcome_head h ON h.event_id = f.event_id
    WHERE f.event_id = NEW.event_id AND f.fact_id = NEW.outcome_fact_id;
    IF current_fact_id IS DISTINCT FROM NEW.outcome_fact_id THEN
        RAISE EXCEPTION 'settlement authorization requires current outcome';
    END IF;
    IF outcome_kind = 'EVENT_CANCELLED' THEN
        IF event_status IS DISTINCT FROM 'CANCELADO' OR NEW.confirmed_version_id IS NOT NULL THEN
            RAISE EXCEPTION 'cancellation authorization requires cancelled event and no sporting version';
        END IF;
    ELSIF event_status IS DISTINCT FROM 'PUBLICADO' THEN
        RAISE EXCEPTION 'settlement authorization requires current published outcome';
    ELSIF outcome_kind = 'SPORTING_RESULT' THEN
        SELECT p.confirmed_version_id, v.status INTO payload_version_id, version_status
        FROM public.sports_outcome_sporting_payload p
        JOIN public.sports_result_version v ON v.event_id = p.event_id AND v.version_id = p.confirmed_version_id
        WHERE p.event_id = NEW.event_id AND p.fact_id = NEW.outcome_fact_id;
        IF NEW.confirmed_version_id IS NULL
            OR NEW.confirmed_version_id IS DISTINCT FROM payload_version_id
            OR NEW.confirmed_version_id IS DISTINCT FROM current_version_id
            OR version_status NOT IN ('CONFIRMADO', 'CORRIGIDO') THEN
            RAISE EXCEPTION 'settlement authorization requires exact confirmed version';
        END IF;
    ELSIF outcome_kind IN ('ADMINISTRATIVE_WALKOVER', 'ABANDONED_NO_VALID_RESULT') THEN
        IF NEW.confirmed_version_id IS NOT NULL THEN
            RAISE EXCEPTION 'administrative outcome cannot claim a sporting version';
        END IF;
    ELSE
        RAISE EXCEPTION 'settlement authorization requires recognized outcome';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE public.credit_ledger_entry DROP CONSTRAINT ck_credit_ledger_entry_type;
ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_entry_type
    CHECK (entry_type IN ('GRANT_INITIAL', 'DEBIT', 'COMPENSATION', 'PAYOUT', 'REFUND'));

ALTER TABLE public.credit_ledger_entry DROP CONSTRAINT ck_credit_ledger_entry_type_direction;
ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_entry_type_direction
    CHECK (
        (entry_type IN ('GRANT_INITIAL', 'COMPENSATION', 'PAYOUT', 'REFUND') AND direction = 'CREDIT')
        OR (entry_type = 'DEBIT' AND direction = 'DEBIT')
    );

ALTER TABLE public.credit_ledger_entry DROP CONSTRAINT ck_credit_ledger_source_type;
ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_source_type
    CHECK (source_type IN ('SYSTEM', 'PREDICTION', 'COMPENSATION', 'SETTLEMENT'));

ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_settlement_source
    CHECK (
        (entry_type IN ('PAYOUT', 'REFUND') AND source_type = 'SETTLEMENT')
        OR (entry_type NOT IN ('PAYOUT', 'REFUND') AND source_type <> 'SETTLEMENT')
    );

ALTER TABLE public.settlement_case ADD CONSTRAINT uq_settlement_case_authorization
    UNIQUE (authorization_id);
ALTER TABLE public.settlement_case ADD CONSTRAINT uq_settlement_case_outcome
    UNIQUE (event_id, outcome_fact_id);

CREATE TABLE public.settlement_processing (
    case_id uuid NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'PENDING',
    lease_owner uuid NULL,
    lease_expires_at timestamptz(6) NULL,
    total_units integer NOT NULL,
    completed_units integer NOT NULL DEFAULT 0,
    last_error varchar(255) NULL,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_settlement_processing PRIMARY KEY (case_id),
    CONSTRAINT fk_settlement_processing_case FOREIGN KEY (case_id)
        REFERENCES public.settlement_case (case_id),
    CONSTRAINT ck_settlement_processing_status CHECK (status IN ('PENDING', 'RUNNING', 'FAILED', 'COMPLETE')),
    CONSTRAINT ck_settlement_processing_counts CHECK (
        total_units >= 0 AND completed_units >= 0 AND completed_units <= total_units
    ),
    CONSTRAINT ck_settlement_processing_lease CHECK (
        (status = 'RUNNING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    ),
    CONSTRAINT ck_settlement_processing_complete CHECK (
        status <> 'COMPLETE' OR completed_units = total_units
    )
);

CREATE INDEX idx_settlement_processing_recovery
    ON public.settlement_processing (status, lease_expires_at, updated_at)
    WHERE status <> 'COMPLETE';

CREATE TABLE public.settlement_calculation (
    settlement_id uuid NOT NULL,
    selection varchar(8) COLLATE "C" NOT NULL,
    stake numeric(20,2) NOT NULL,
    accepted_odds numeric(20,10) NOT NULL,
    potential_return numeric(20,2) NOT NULL,
    outcome_kind varchar(40) COLLATE "C" NOT NULL,
    home_score integer NULL,
    away_score integer NULL,
    winner_slot_no smallint NULL,
    ledger_entry_id uuid NULL,
    CONSTRAINT pk_settlement_calculation PRIMARY KEY (settlement_id),
    CONSTRAINT fk_settlement_calculation_unit FOREIGN KEY (settlement_id)
        REFERENCES public.prediction_settlement (settlement_id),
    CONSTRAINT fk_settlement_calculation_ledger FOREIGN KEY (ledger_entry_id)
        REFERENCES public.credit_ledger_entry (entry_id),
    CONSTRAINT ck_settlement_calculation_selection CHECK (selection IN ('HOME', 'DRAW', 'AWAY')),
    CONSTRAINT ck_settlement_calculation_money CHECK (
        scale(stake) = 2 AND stake > 0.00
        AND scale(potential_return) = 2 AND potential_return >= 0.00
        AND accepted_odds > 0
    ),
    CONSTRAINT ck_settlement_calculation_kind CHECK (outcome_kind IN (
        'SPORTING_RESULT', 'ADMINISTRATIVE_WALKOVER',
        'ABANDONED_NO_VALID_RESULT', 'EVENT_CANCELLED'
    ))
);

CREATE TRIGGER trg_prevent_settlement_calculation_modification
BEFORE UPDATE OR DELETE ON public.settlement_calculation FOR EACH ROW
EXECUTE FUNCTION public.prevent_settlement_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.settlement_processing TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.settlement_calculation TO %I', runtime_role);
    EXECUTE format('GRANT UPDATE ON TABLE public.sports_outcome_head TO %I', runtime_role);
    EXECUTE format('REVOKE DELETE ON TABLE public.settlement_processing FROM %I', runtime_role);
    EXECUTE format('REVOKE UPDATE, DELETE ON TABLE public.settlement_calculation FROM %I', runtime_role);
END $$;
