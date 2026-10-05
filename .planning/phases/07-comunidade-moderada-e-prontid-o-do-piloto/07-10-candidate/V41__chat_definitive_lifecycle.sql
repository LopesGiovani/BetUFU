CREATE TABLE public.settlement_chat_completion_fact (
    case_id uuid PRIMARY KEY REFERENCES public.settlement_case (case_id),
    completed_at timestamptz(6) NOT NULL
);

CREATE FUNCTION public.validate_settlement_chat_completion_fact() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE expected_units integer;
DECLARE recorded_units integer;
DECLARE completed boolean;
BEGIN
    SELECT p.status='COMPLETE' AND p.completed_units=p.total_units, p.total_units
    INTO completed, recorded_units FROM public.settlement_processing p WHERE p.case_id=NEW.case_id;
    SELECT count(*) INTO expected_units FROM public.prediction prediction
    JOIN public.odds_market market ON market.market_id=prediction.market_id
    JOIN public.settlement_case c ON c.event_id=market.event_id
    WHERE c.case_id=NEW.case_id AND prediction.state IN ('PENDING','ANNULLED');
    IF completed IS DISTINCT FROM true OR recorded_units <> expected_units
        OR (SELECT count(*) FROM public.prediction_settlement WHERE case_id=NEW.case_id) <> expected_units THEN
        RAISE EXCEPTION 'chat completion requires all accepted predictions to be processed';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_settlement_chat_completion_fact_validate
BEFORE INSERT ON public.settlement_chat_completion_fact
FOR EACH ROW EXECUTE FUNCTION public.validate_settlement_chat_completion_fact();

-- Lifecycle facts are producer-owned milestones. The current state is a rebuildable projection.
CREATE TABLE public.chat_event_lifecycle_fact (
    lifecycle_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES public.sports_event (event_id),
    milestone_kind varchar(40) COLLATE "C" NOT NULL,
    source_fact_id uuid NOT NULL,
    source_version_id uuid NULL,
    milestone_at timestamptz(6) NOT NULL,
    deadline_at timestamptz(6) NOT NULL,
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_event_lifecycle_fact PRIMARY KEY (lifecycle_fact_id),
    CONSTRAINT uq_chat_event_lifecycle_fact_source UNIQUE
        (event_id, milestone_kind, source_fact_id),
    CONSTRAINT ck_chat_event_lifecycle_fact_kind CHECK (
        milestone_kind IN ('RESULT_SETTLED', 'NO_PREDICTIONS_CONFIRMED', 'CANCELLATION_CONFIRMED')
    ),
    CONSTRAINT ck_chat_event_lifecycle_fact_deadline CHECK (
        deadline_at = milestone_at + INTERVAL '2 hours'
    )
);

CREATE INDEX idx_chat_event_lifecycle_fact_event
    ON public.chat_event_lifecycle_fact (event_id, milestone_at DESC, lifecycle_fact_id DESC);

-- One immutable close fact makes a terminal event survive retries, correction replay and restore.
CREATE TABLE public.chat_event_lifecycle_close (
    close_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL REFERENCES public.sports_event (event_id),
    lifecycle_fact_id uuid NOT NULL REFERENCES public.chat_event_lifecycle_fact (lifecycle_fact_id),
    closed_at timestamptz(6) NOT NULL,
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_event_lifecycle_close PRIMARY KEY (close_fact_id),
    CONSTRAINT uq_chat_event_lifecycle_close_event UNIQUE (event_id),
    CONSTRAINT uq_chat_event_lifecycle_close_milestone UNIQUE (lifecycle_fact_id),
    CONSTRAINT ck_chat_event_lifecycle_close_time CHECK (closed_at >= recorded_at)
);

CREATE TABLE public.chat_event_lifecycle_state (
    event_id uuid NOT NULL REFERENCES public.sports_event (event_id),
    state varchar(16) COLLATE "C" NOT NULL DEFAULT 'OPEN',
    milestone_kind varchar(40) COLLATE "C" NULL,
    lifecycle_fact_id uuid NULL REFERENCES public.chat_event_lifecycle_fact (lifecycle_fact_id),
    milestone_at timestamptz(6) NULL,
    deadline_at timestamptz(6) NULL,
    closed_at timestamptz(6) NULL,
    rebuilt_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_event_lifecycle_state PRIMARY KEY (event_id),
    CONSTRAINT ck_chat_event_lifecycle_state_value CHECK (state IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_chat_event_lifecycle_state_milestone CHECK (
        (state = 'OPEN' AND closed_at IS NULL)
        OR (state = 'CLOSED' AND closed_at IS NOT NULL)
    ),
    CONSTRAINT ck_chat_event_lifecycle_state_deadline CHECK (
        deadline_at IS NULL OR milestone_at IS NOT NULL
    )
);

CREATE INDEX idx_chat_event_lifecycle_state_deadline
    ON public.chat_event_lifecycle_state (state, deadline_at, event_id);

CREATE FUNCTION public.prevent_chat_lifecycle_fact_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'chat lifecycle history is immutable: UPDATE and DELETE are prohibited';
END $$;

CREATE TRIGGER trg_settlement_chat_completion_fact_immutable
BEFORE UPDATE OR DELETE ON public.settlement_chat_completion_fact
FOR EACH ROW EXECUTE FUNCTION public.prevent_chat_lifecycle_fact_modification();

CREATE TRIGGER trg_chat_event_lifecycle_fact_immutable
BEFORE UPDATE OR DELETE ON public.chat_event_lifecycle_fact
FOR EACH ROW EXECUTE FUNCTION public.prevent_chat_lifecycle_fact_modification();
CREATE TRIGGER trg_chat_event_lifecycle_close_immutable
BEFORE UPDATE OR DELETE ON public.chat_event_lifecycle_close
FOR EACH ROW EXECUTE FUNCTION public.prevent_chat_lifecycle_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('REVOKE ALL ON TABLE public.settlement_chat_completion_fact, public.chat_event_lifecycle_fact, public.chat_event_lifecycle_close, public.chat_event_lifecycle_state FROM PUBLIC');
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.settlement_chat_completion_fact, public.chat_event_lifecycle_fact, public.chat_event_lifecycle_close TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE ON TABLE public.chat_event_lifecycle_state TO %I', runtime_role);
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.settlement_chat_completion_fact, public.chat_event_lifecycle_fact, public.chat_event_lifecycle_close FROM %I', runtime_role);
END $$;
