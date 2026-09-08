CREATE TABLE public.sports_outcome_fact (
    fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    version_no bigint NOT NULL,
    outcome_kind varchar(40) COLLATE "C" NOT NULL,
    source_reference varchar(2048) COLLATE "C" NOT NULL,
    reason varchar(1024) COLLATE "C" NULL,
    recorded_by_identity_id uuid NOT NULL,
    sport_time timestamptz(6) NOT NULL,
    known_at timestamptz(6) NOT NULL,
    supersedes_fact_id uuid NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_sports_outcome_fact PRIMARY KEY (fact_id),
    CONSTRAINT uq_sports_outcome_fact_event_version UNIQUE (event_id, version_no),
    CONSTRAINT uq_sports_outcome_fact_event_id UNIQUE (event_id, fact_id),
    CONSTRAINT fk_sports_outcome_fact_event_scope FOREIGN KEY (competition_id, edition_id, event_id)
        REFERENCES public.sports_event (competition_id, edition_id, event_id),
    CONSTRAINT fk_sports_outcome_fact_actor FOREIGN KEY (recorded_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_sports_outcome_fact_supersedes FOREIGN KEY (event_id, supersedes_fact_id)
        REFERENCES public.sports_outcome_fact (event_id, fact_id),
    CONSTRAINT ck_sports_outcome_fact_version CHECK (version_no > 0),
    CONSTRAINT ck_sports_outcome_fact_kind CHECK (outcome_kind IN ('SPORTING_RESULT', 'ADMINISTRATIVE_WALKOVER', 'ABANDONED_NO_VALID_RESULT')),
    CONSTRAINT ck_sports_outcome_fact_source CHECK (btrim(source_reference) <> '' AND octet_length(source_reference) <= 2048),
    CONSTRAINT ck_sports_outcome_fact_reason CHECK (reason IS NULL OR (btrim(reason) <> '' AND octet_length(reason) <= 1024)),
    CONSTRAINT ck_sports_outcome_fact_not_self_superseding CHECK (supersedes_fact_id IS NULL OR supersedes_fact_id <> fact_id)
);

CREATE TABLE public.sports_outcome_sporting_payload (
    fact_id uuid NOT NULL,
    event_id uuid NOT NULL,
    confirmed_version_id uuid NOT NULL,
    confirmed_version_status varchar(32) COLLATE "C" NOT NULL,
    confirmed_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_sports_outcome_sporting_payload PRIMARY KEY (fact_id),
    CONSTRAINT fk_sports_outcome_sporting_fact FOREIGN KEY (event_id, fact_id)
        REFERENCES public.sports_outcome_fact (event_id, fact_id),
    CONSTRAINT fk_sports_outcome_sporting_v16 FOREIGN KEY (event_id, confirmed_version_id)
        REFERENCES public.sports_result_version (event_id, version_id),
    CONSTRAINT ck_sports_outcome_sporting_status CHECK (confirmed_version_status IN ('CONFIRMADO', 'CORRIGIDO'))
);

CREATE TABLE public.sports_outcome_administrative_payload (
    fact_id uuid NOT NULL,
    event_id uuid NOT NULL,
    administrative_reason varchar(1024) COLLATE "C" NOT NULL,
    winner_slot_no smallint NULL,
    CONSTRAINT pk_sports_outcome_administrative_payload PRIMARY KEY (fact_id),
    CONSTRAINT fk_sports_outcome_admin_fact FOREIGN KEY (event_id, fact_id)
        REFERENCES public.sports_outcome_fact (event_id, fact_id),
    CONSTRAINT ck_sports_outcome_admin_reason CHECK (btrim(administrative_reason) <> '' AND octet_length(administrative_reason) <= 1024),
    CONSTRAINT ck_sports_outcome_admin_winner CHECK (winner_slot_no IS NULL OR winner_slot_no BETWEEN 1 AND 2)
);

CREATE TABLE public.sports_outcome_head (
    event_id uuid NOT NULL,
    fact_id uuid NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_sports_outcome_head PRIMARY KEY (event_id),
    CONSTRAINT uq_sports_outcome_head_fact UNIQUE (event_id, fact_id),
    CONSTRAINT fk_sports_outcome_head_event FOREIGN KEY (event_id) REFERENCES public.sports_event (event_id),
    CONSTRAINT fk_sports_outcome_head_fact FOREIGN KEY (event_id, fact_id)
        REFERENCES public.sports_outcome_fact (event_id, fact_id),
    CONSTRAINT ck_sports_outcome_head_lock CHECK (lock_version >= 0)
);

CREATE INDEX idx_sports_outcome_fact_event_time
    ON public.sports_outcome_fact (event_id, sport_time ASC, known_at ASC, fact_id ASC);

CREATE OR REPLACE FUNCTION public.enforce_sports_outcome_payload_coherence()
RETURNS TRIGGER AS $$
DECLARE
    fact_kind varchar(40);
    sporting_count integer;
    administrative_count integer;
BEGIN
    SELECT outcome_kind INTO fact_kind FROM public.sports_outcome_fact WHERE fact_id = NEW.fact_id;
    IF fact_kind IS NULL THEN
        RETURN NULL;
    END IF;
    SELECT count(*) INTO sporting_count FROM public.sports_outcome_sporting_payload WHERE fact_id = NEW.fact_id;
    SELECT count(*) INTO administrative_count FROM public.sports_outcome_administrative_payload WHERE fact_id = NEW.fact_id;
    IF sporting_count + administrative_count <> 1 THEN
        RAISE EXCEPTION 'sports outcome fact must have exactly one payload';
    END IF;
    IF fact_kind = 'SPORTING_RESULT' AND sporting_count <> 1 THEN
        RAISE EXCEPTION 'SPORTING_RESULT requires sporting payload';
    END IF;
    IF fact_kind <> 'SPORTING_RESULT' AND administrative_count <> 1 THEN
        RAISE EXCEPTION 'administrative outcome requires administrative payload';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_sports_outcome_sporting_payload_coherence
AFTER INSERT ON public.sports_outcome_sporting_payload
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_outcome_payload_coherence();

CREATE CONSTRAINT TRIGGER trg_sports_outcome_admin_payload_coherence
AFTER INSERT ON public.sports_outcome_administrative_payload
DEFERRABLE INITIALLY DEFERRED FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_outcome_payload_coherence();

CREATE OR REPLACE FUNCTION public.prevent_sports_outcome_fact_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'sports outcome history is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_sports_outcome_fact_modification
BEFORE UPDATE OR DELETE ON public.sports_outcome_fact FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_outcome_fact_modification();

CREATE TRIGGER trg_prevent_sports_outcome_sporting_modification
BEFORE UPDATE OR DELETE ON public.sports_outcome_sporting_payload FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_outcome_fact_modification();

CREATE TRIGGER trg_prevent_sports_outcome_admin_modification
BEFORE UPDATE OR DELETE ON public.sports_outcome_administrative_payload FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_outcome_fact_modification();

CREATE OR REPLACE FUNCTION public.enforce_sports_outcome_head_coherence()
RETURNS TRIGGER AS $$
DECLARE
    fact_kind varchar(40);
BEGIN
    SELECT outcome_kind INTO fact_kind FROM public.sports_outcome_fact WHERE event_id = NEW.event_id AND fact_id = NEW.fact_id;
    IF fact_kind IS NULL THEN
        RAISE EXCEPTION 'outcome head must reference an event fact';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_sports_outcome_head_coherence
BEFORE INSERT OR UPDATE ON public.sports_outcome_head FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_outcome_head_coherence();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NOT NULL AND btrim(runtime_role) <> '' THEN
        EXECUTE format('GRANT SELECT, INSERT ON TABLE public.sports_outcome_fact, public.sports_outcome_sporting_payload, public.sports_outcome_administrative_payload, public.sports_outcome_head TO %I', runtime_role);
    END IF;
END $$;
