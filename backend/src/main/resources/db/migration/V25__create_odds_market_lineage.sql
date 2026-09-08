-- V25 is additive: applied migrations V1-V24 remain byte-for-byte unchanged.
CREATE TABLE public.odds_market (
    market_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    market_type varchar(64) COLLATE "C" NOT NULL,
    current_version_id uuid NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_market PRIMARY KEY (market_id),
    CONSTRAINT uq_odds_market_identity UNIQUE (event_id, market_type),
    CONSTRAINT ck_odds_market_type CHECK (btrim(market_type) <> '' AND octet_length(market_type) <= 64),
    CONSTRAINT ck_odds_market_lock_version CHECK (lock_version >= 0)
);

CREATE TABLE public.odds_market_version (
    market_version_id uuid NOT NULL DEFAULT gen_random_uuid(),
    market_id uuid NOT NULL,
    version_no bigint NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL,
    close_deadline timestamptz(6) NULL,
    supersedes_version_id uuid NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_market_version PRIMARY KEY (market_version_id),
    CONSTRAINT uq_odds_market_version_market_id UNIQUE (market_id, market_version_id),
    CONSTRAINT uq_odds_market_version_no UNIQUE (market_id, version_no),
    CONSTRAINT fk_odds_market_version_market FOREIGN KEY (market_id) REFERENCES public.odds_market (market_id),
    CONSTRAINT fk_odds_market_version_supersedes FOREIGN KEY (market_id, supersedes_version_id)
        REFERENCES public.odds_market_version (market_id, market_version_id),
    CONSTRAINT ck_odds_market_version_no CHECK (version_no > 0),
    CONSTRAINT ck_odds_market_version_state CHECK (state IN ('DRAFT', 'OPEN', 'SUSPENDED', 'CLOSED', 'LOCKED', 'VOID')),
    CONSTRAINT ck_odds_market_version_not_self_superseding
        CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> market_version_id)
);

ALTER TABLE public.odds_market
    ADD CONSTRAINT fk_odds_market_current_version
    FOREIGN KEY (market_id, current_version_id)
    REFERENCES public.odds_market_version (market_id, market_version_id);

CREATE TABLE public.odds_calculation (
    calculation_id uuid NOT NULL DEFAULT gen_random_uuid(),
    market_version_id uuid NOT NULL,
    algorithm_version varchar(64) COLLATE "C" NOT NULL,
    configuration_digest varchar(128) COLLATE "C" NOT NULL,
    input_snapshot jsonb NOT NULL,
    confirmed_version_id uuid NULL,
    fair_probability numeric(20, 10) NOT NULL,
    confidence numeric(20, 10) NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    known_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_calculation PRIMARY KEY (calculation_id),
    CONSTRAINT fk_odds_calculation_market_version FOREIGN KEY (market_version_id)
        REFERENCES public.odds_market_version (market_version_id),
    CONSTRAINT ck_odds_calculation_algorithm CHECK (btrim(algorithm_version) <> ''),
    CONSTRAINT ck_odds_calculation_digest CHECK (btrim(configuration_digest) <> ''),
    CONSTRAINT ck_odds_calculation_probability CHECK (fair_probability >= 0 AND fair_probability <= 1),
    CONSTRAINT ck_odds_calculation_confidence CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1))
);

CREATE TABLE public.odds_publication (
    publication_id uuid NOT NULL DEFAULT gen_random_uuid(),
    calculation_id uuid NOT NULL,
    margin numeric(10, 6) NOT NULL,
    rounding_mode varchar(32) COLLATE "C" NOT NULL,
    published_odds numeric(20, 10) NOT NULL,
    supersedes_publication_id uuid NULL,
    base_lock_version bigint NOT NULL DEFAULT 0,
    published_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_publication PRIMARY KEY (publication_id),
    CONSTRAINT fk_odds_publication_calculation FOREIGN KEY (calculation_id)
        REFERENCES public.odds_calculation (calculation_id),
    CONSTRAINT fk_odds_publication_supersedes FOREIGN KEY (supersedes_publication_id)
        REFERENCES public.odds_publication (publication_id),
    CONSTRAINT ck_odds_publication_margin CHECK (margin >= 0 AND margin < 1),
    CONSTRAINT ck_odds_publication_rounding CHECK (btrim(rounding_mode) <> ''),
    CONSTRAINT ck_odds_publication_odds CHECK (published_odds > 0),
    CONSTRAINT ck_odds_publication_lock CHECK (base_lock_version >= 0)
);

CREATE TABLE public.odds_lifecycle (
    lifecycle_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    market_id uuid NOT NULL,
    from_state varchar(16) COLLATE "C" NULL,
    to_state varchar(16) COLLATE "C" NOT NULL,
    reason varchar(1024) COLLATE "C" NULL,
    actor_id uuid NULL,
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_lifecycle PRIMARY KEY (lifecycle_fact_id),
    CONSTRAINT fk_odds_lifecycle_market FOREIGN KEY (market_id) REFERENCES public.odds_market (market_id),
    CONSTRAINT ck_odds_lifecycle_from_state CHECK (from_state IS NULL OR from_state IN ('DRAFT', 'OPEN', 'SUSPENDED', 'CLOSED', 'LOCKED', 'VOID')),
    CONSTRAINT ck_odds_lifecycle_to_state CHECK (to_state IN ('DRAFT', 'OPEN', 'SUSPENDED', 'CLOSED', 'LOCKED', 'VOID')),
    CONSTRAINT ck_odds_lifecycle_reason CHECK (reason IS NULL OR (btrim(reason) <> '' AND octet_length(reason) <= 1024))
);

CREATE TABLE public.odds_override (
    override_id uuid NOT NULL DEFAULT gen_random_uuid(),
    market_id uuid NOT NULL,
    calculation_id uuid NULL,
    proposer_id uuid NOT NULL,
    approver_id uuid NULL,
    reason varchar(1024) COLLATE "C" NOT NULL,
    decision varchar(16) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_override PRIMARY KEY (override_id),
    CONSTRAINT fk_odds_override_market FOREIGN KEY (market_id) REFERENCES public.odds_market (market_id),
    CONSTRAINT fk_odds_override_calculation FOREIGN KEY (calculation_id) REFERENCES public.odds_calculation (calculation_id),
    CONSTRAINT ck_odds_override_actors CHECK (approver_id IS NULL OR approver_id <> proposer_id),
    CONSTRAINT ck_odds_override_reason CHECK (btrim(reason) <> '' AND octet_length(reason) <= 1024),
    CONSTRAINT ck_odds_override_decision CHECK (decision IN ('PROPOSED', 'APPROVED', 'REJECTED'))
);

CREATE TABLE public.odds_audit_event (
    audit_event_id uuid NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type varchar(64) COLLATE "C" NOT NULL,
    aggregate_id uuid NOT NULL,
    action varchar(64) COLLATE "C" NOT NULL,
    actor_id uuid NULL,
    payload_digest varchar(128) COLLATE "C" NOT NULL,
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_audit_event PRIMARY KEY (audit_event_id),
    CONSTRAINT ck_odds_audit_aggregate_type CHECK (btrim(aggregate_type) <> ''),
    CONSTRAINT ck_odds_audit_action CHECK (btrim(action) <> ''),
    CONSTRAINT ck_odds_audit_digest CHECK (btrim(payload_digest) <> '')
);

CREATE TABLE public.odds_idempotency_command (
    command_id uuid NOT NULL DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL,
    command_type varchar(64) COLLATE "C" NOT NULL,
    idempotency_key varchar(128) COLLATE "C" NOT NULL,
    payload_digest varchar(128) COLLATE "C" NOT NULL,
    result_reference uuid NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_idempotency_command PRIMARY KEY (command_id),
    CONSTRAINT uq_odds_idempotency_command UNIQUE (actor_id, command_type, idempotency_key),
    CONSTRAINT ck_odds_idempotency_command_type CHECK (btrim(command_type) <> ''),
    CONSTRAINT ck_odds_idempotency_key CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_odds_idempotency_digest CHECK (btrim(payload_digest) <> '')
);

CREATE INDEX idx_odds_market_version_order ON public.odds_market_version (market_id, version_no DESC);
CREATE INDEX idx_odds_calculation_market_version ON public.odds_calculation (market_version_id, created_at DESC);
CREATE INDEX idx_odds_publication_calculation ON public.odds_publication (calculation_id, published_at DESC);
CREATE INDEX idx_odds_lifecycle_market_order ON public.odds_lifecycle (market_id, occurred_at ASC, lifecycle_fact_id ASC);

CREATE OR REPLACE FUNCTION public.prevent_odds_history_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'odds history is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_odds_market_version_modification
BEFORE UPDATE OR DELETE ON public.odds_market_version FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();
CREATE TRIGGER trg_prevent_odds_calculation_modification
BEFORE UPDATE OR DELETE ON public.odds_calculation FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();
CREATE TRIGGER trg_prevent_odds_publication_modification
BEFORE UPDATE OR DELETE ON public.odds_publication FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();
CREATE TRIGGER trg_prevent_odds_lifecycle_modification
BEFORE UPDATE OR DELETE ON public.odds_lifecycle FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();
CREATE TRIGGER trg_prevent_odds_override_modification
BEFORE UPDATE OR DELETE ON public.odds_override FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();
CREATE TRIGGER trg_prevent_odds_audit_modification
BEFORE UPDATE OR DELETE ON public.odds_audit_event FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();
CREATE TRIGGER trg_prevent_odds_idempotency_modification
BEFORE UPDATE OR DELETE ON public.odds_idempotency_command FOR EACH ROW
EXECUTE FUNCTION public.prevent_odds_history_modification();

DO $$
DECLARE
    runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured BETUFU runtime role does not exist';
    END IF;
    EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.odds_market, public.odds_market_version, public.odds_calculation, public.odds_publication, public.odds_lifecycle, public.odds_override, public.odds_audit_event, public.odds_idempotency_command FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.odds_market_version, public.odds_calculation, public.odds_publication, public.odds_lifecycle, public.odds_override, public.odds_audit_event, public.odds_idempotency_command TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.odds_market TO %I', runtime_role);
END $$;
