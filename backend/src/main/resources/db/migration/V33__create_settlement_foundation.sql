CREATE TABLE public.settlement_authorization (
    authorization_id uuid NOT NULL,
    event_id uuid NOT NULL,
    outcome_fact_id uuid NOT NULL,
    confirmed_version_id uuid NULL,
    authorized_by_identity_id uuid NOT NULL,
    decision varchar(16) COLLATE "C" NOT NULL,
    authorized_at timestamptz(6) NOT NULL,
    idempotency_key varchar(255) COLLATE "C" NOT NULL,
    CONSTRAINT pk_settlement_authorization PRIMARY KEY (authorization_id),
    CONSTRAINT uq_settlement_authorization_event_fact_id UNIQUE (event_id, outcome_fact_id, authorization_id),
    CONSTRAINT uq_settlement_authorization_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_settlement_authorization_outcome FOREIGN KEY (event_id, outcome_fact_id)
        REFERENCES public.sports_outcome_fact (event_id, fact_id),
    CONSTRAINT fk_settlement_authorization_version FOREIGN KEY (event_id, confirmed_version_id)
        REFERENCES public.sports_result_version (event_id, version_id),
    CONSTRAINT fk_settlement_authorization_actor FOREIGN KEY (authorized_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_settlement_authorization_decision CHECK (decision IN ('APPROVED', 'REJECTED')),
    CONSTRAINT ck_settlement_authorization_key CHECK (btrim(idempotency_key) <> '')
);

CREATE TABLE public.settlement_case (
    case_id uuid NOT NULL,
    authorization_id uuid NOT NULL,
    event_id uuid NOT NULL,
    outcome_fact_id uuid NOT NULL,
    processing_version integer NOT NULL,
    rule_version varchar(64) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_settlement_case PRIMARY KEY (case_id),
    CONSTRAINT uq_settlement_case_outcome_processing UNIQUE (event_id, outcome_fact_id, processing_version),
    CONSTRAINT fk_settlement_case_authorization FOREIGN KEY (event_id, outcome_fact_id, authorization_id)
        REFERENCES public.settlement_authorization (event_id, outcome_fact_id, authorization_id),
    CONSTRAINT ck_settlement_case_processing_version CHECK (processing_version > 0),
    CONSTRAINT ck_settlement_case_rule CHECK (btrim(rule_version) <> '')
);

CREATE TABLE public.prediction_settlement (
    settlement_id uuid NOT NULL,
    case_id uuid NOT NULL,
    prediction_id uuid NOT NULL,
    identity_id uuid NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL,
    reason varchar(40) COLLATE "C" NOT NULL,
    amount numeric(20,2) NOT NULL,
    supersedes_settlement_id uuid NULL,
    idempotency_key varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_prediction_settlement PRIMARY KEY (settlement_id),
    CONSTRAINT uq_prediction_settlement_case_prediction UNIQUE (case_id, prediction_id),
    CONSTRAINT uq_prediction_settlement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_prediction_settlement_case FOREIGN KEY (case_id)
        REFERENCES public.settlement_case (case_id),
    CONSTRAINT fk_prediction_settlement_prediction FOREIGN KEY (prediction_id)
        REFERENCES public.prediction (prediction_id),
    CONSTRAINT fk_prediction_settlement_identity FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_prediction_settlement_supersedes FOREIGN KEY (supersedes_settlement_id)
        REFERENCES public.prediction_settlement (settlement_id),
    CONSTRAINT ck_prediction_settlement_state CHECK (state IN ('PENDING', 'WON', 'LOST', 'REFUNDED', 'ANNULLED')),
    CONSTRAINT ck_prediction_settlement_reason CHECK (reason IN ('CONFIRMED_RESULT', 'CORRECTED_RESULT', 'ADMINISTRATIVE_WALKOVER', 'NO_VALID_RESULT', 'EVENT_CANCELLED')),
    CONSTRAINT ck_prediction_settlement_amount CHECK (scale(amount) = 2 AND amount >= 0.00),
    CONSTRAINT ck_prediction_settlement_key CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_prediction_settlement_not_self_superseding CHECK (supersedes_settlement_id IS NULL OR supersedes_settlement_id <> settlement_id)
);

CREATE INDEX idx_settlement_case_event ON public.settlement_case (event_id, processing_version DESC);
CREATE INDEX idx_prediction_settlement_prediction ON public.prediction_settlement (prediction_id, created_at DESC);

CREATE OR REPLACE FUNCTION public.validate_settlement_authorization()
RETURNS TRIGGER AS $$
DECLARE
    outcome_kind varchar(40);
    published_status varchar(16);
    current_fact_id uuid;
    current_version_id uuid;
    payload_version_id uuid;
    version_status varchar(32);
BEGIN
    SELECT f.outcome_kind, e.status, h.fact_id, e.confirmed_version_id
    INTO outcome_kind, published_status, current_fact_id, current_version_id
    FROM public.sports_outcome_fact f
    JOIN public.sports_event e ON e.event_id = f.event_id
    LEFT JOIN public.sports_outcome_head h ON h.event_id = f.event_id
    WHERE f.event_id = NEW.event_id AND f.fact_id = NEW.outcome_fact_id;
    IF published_status IS DISTINCT FROM 'PUBLICADO'
        OR current_fact_id IS DISTINCT FROM NEW.outcome_fact_id THEN
        RAISE EXCEPTION 'settlement authorization requires current published outcome';
    END IF;
    IF outcome_kind = 'SPORTING_RESULT' THEN
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

CREATE TRIGGER trg_validate_settlement_authorization
BEFORE INSERT ON public.settlement_authorization FOR EACH ROW
EXECUTE FUNCTION public.validate_settlement_authorization();

CREATE OR REPLACE FUNCTION public.validate_settlement_case()
RETURNS TRIGGER AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.settlement_authorization a
        WHERE a.authorization_id = NEW.authorization_id AND a.decision = 'APPROVED'
    ) THEN
        RAISE EXCEPTION 'settlement case requires approved authorization';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_settlement_case
BEFORE INSERT ON public.settlement_case FOR EACH ROW
EXECUTE FUNCTION public.validate_settlement_case();

CREATE OR REPLACE FUNCTION public.validate_prediction_settlement()
RETURNS TRIGGER AS $$
DECLARE
    prediction_event_id uuid;
    prediction_identity_id uuid;
    case_event_id uuid;
    previous_prediction_id uuid;
BEGIN
    SELECT m.event_id, p.identity_id INTO prediction_event_id, prediction_identity_id
    FROM public.prediction p JOIN public.odds_market m ON m.market_id = p.market_id
    WHERE p.prediction_id = NEW.prediction_id;
    SELECT event_id INTO case_event_id FROM public.settlement_case WHERE case_id = NEW.case_id;
    IF prediction_event_id IS DISTINCT FROM case_event_id
        OR prediction_identity_id IS DISTINCT FROM NEW.identity_id THEN
        RAISE EXCEPTION 'settlement unit must match prediction owner and event';
    END IF;
    IF NEW.supersedes_settlement_id IS NOT NULL THEN
        SELECT prediction_id INTO previous_prediction_id
        FROM public.prediction_settlement WHERE settlement_id = NEW.supersedes_settlement_id;
        IF previous_prediction_id IS DISTINCT FROM NEW.prediction_id THEN
            RAISE EXCEPTION 'settlement supersession must preserve prediction';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_prediction_settlement
BEFORE INSERT ON public.prediction_settlement FOR EACH ROW
EXECUTE FUNCTION public.validate_prediction_settlement();

CREATE OR REPLACE FUNCTION public.prevent_settlement_fact_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'settlement history is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_settlement_authorization_modification
BEFORE UPDATE OR DELETE ON public.settlement_authorization FOR EACH ROW
EXECUTE FUNCTION public.prevent_settlement_fact_modification();
CREATE TRIGGER trg_prevent_settlement_case_modification
BEFORE UPDATE OR DELETE ON public.settlement_case FOR EACH ROW
EXECUTE FUNCTION public.prevent_settlement_fact_modification();
CREATE TRIGGER trg_prevent_prediction_settlement_modification
BEFORE UPDATE OR DELETE ON public.prediction_settlement FOR EACH ROW
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
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.settlement_authorization, public.settlement_case, public.prediction_settlement TO %I', runtime_role);
    EXECUTE format('REVOKE UPDATE, DELETE ON TABLE public.settlement_authorization, public.settlement_case, public.prediction_settlement FROM %I', runtime_role);
END $$;
