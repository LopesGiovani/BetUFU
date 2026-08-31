-- 1. Alter sports_event to support expanded statuses, reason, and confirmed_version_id
ALTER TABLE public.sports_event
    DROP CONSTRAINT ck_sports_event_status;

ALTER TABLE public.sports_event
    ADD CONSTRAINT ck_sports_event_status
        CHECK (status IN ('RASCUNHO', 'PUBLICADO', 'SUSPENSO', 'ADIADO', 'CANCELADO'));

ALTER TABLE public.sports_event
    ADD COLUMN status_reason varchar(1024) COLLATE "C" NULL,
    ADD COLUMN confirmed_version_id uuid NULL;

ALTER TABLE public.sports_event
    ADD CONSTRAINT ck_sports_event_status_reason_octets
        CHECK (status_reason IS NULL OR (btrim(status_reason) <> '' AND octet_length(status_reason) <= 1024));

-- 2. Alter sports_event_slot to link optional team_id
ALTER TABLE public.sports_event_slot
    ADD COLUMN team_id uuid NULL;

ALTER TABLE public.sports_event_slot
    ADD CONSTRAINT fk_sports_event_slot_team
        FOREIGN KEY (team_id)
        REFERENCES public.sports_team (team_id);

-- 3. Alter sports_event_publication_slot to link optional team_id and snapshot team_name
ALTER TABLE public.sports_event_publication_slot
    ADD COLUMN team_id uuid NULL,
    ADD COLUMN team_name varchar(160) COLLATE "C" NULL;

ALTER TABLE public.sports_event_publication_slot
    ADD CONSTRAINT fk_sports_event_publication_slot_team
        FOREIGN KEY (team_id)
        REFERENCES public.sports_team (team_id);

ALTER TABLE public.sports_event_publication_slot
    ADD CONSTRAINT ck_sports_event_publication_slot_team_name
        CHECK (team_name IS NULL OR (btrim(team_name) <> '' AND octet_length(team_name) <= 160));

-- 4. Create sports_result_version table for append-only, immutable result versions
CREATE TABLE public.sports_result_version (
    version_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    version_no bigint NOT NULL,
    status varchar(32) COLLATE "C" NOT NULL,
    source_reference varchar(2048) COLLATE "C" NOT NULL,
    reason varchar(1024) COLLATE "C" NULL,
    recorded_by_identity_id uuid NOT NULL,
    confirmed_by_identity_id uuid NULL,
    recorded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    confirmed_at timestamptz(6) NULL,
    supersedes_version_id uuid NULL,
    base_lock_version bigint NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_result_version PRIMARY KEY (version_id),
    CONSTRAINT uq_sports_result_version_event_no UNIQUE (event_id, version_no),
    CONSTRAINT uq_sports_result_version_event_id UNIQUE (event_id, version_id),
    CONSTRAINT fk_sports_result_version_event
        FOREIGN KEY (competition_id, edition_id, event_id)
        REFERENCES public.sports_event (competition_id, edition_id, event_id),
    CONSTRAINT fk_sports_result_version_recorded_by
        FOREIGN KEY (recorded_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_sports_result_version_confirmed_by
        FOREIGN KEY (confirmed_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_sports_result_version_supersedes
        FOREIGN KEY (event_id, supersedes_version_id)
        REFERENCES public.sports_result_version (event_id, version_id),
    CONSTRAINT ck_sports_result_version_no CHECK (version_no > 0),
    CONSTRAINT ck_sports_result_version_status
        CHECK (status IN ('INFORMADO', 'AGUARDANDO_REVISAO', 'CONFIRMADO', 'CORRIGIDO')),
    CONSTRAINT ck_sports_result_version_source
        CHECK (btrim(source_reference) <> '' AND octet_length(source_reference) <= 2048),
    CONSTRAINT ck_sports_result_version_reason
        CHECK (reason IS NULL OR (btrim(reason) <> '' AND octet_length(reason) <= 1024)),
    CONSTRAINT ck_sports_result_version_correct_reason
        CHECK (status <> 'CORRIGIDO' OR (reason IS NOT NULL AND btrim(reason) <> '')),
    CONSTRAINT ck_sports_result_version_confirmed_actor
        CHECK (status NOT IN ('CONFIRMADO', 'CORRIGIDO') OR (confirmed_by_identity_id IS NOT NULL AND confirmed_at IS NOT NULL)),
    CONSTRAINT ck_sports_result_version_base_lock CHECK (base_lock_version >= 0),
    CONSTRAINT ck_sports_result_version_not_self_superseding
        CHECK (supersedes_version_id IS NULL OR supersedes_version_id <> version_id)
);

CREATE INDEX idx_sports_result_version_event_latest
    ON public.sports_result_version (event_id, version_no DESC);

-- 5. Create sports_result_version_slot table
CREATE TABLE public.sports_result_version_slot (
    version_id uuid NOT NULL,
    slot_no smallint NOT NULL,
    score integer NOT NULL,
    display_label varchar(160) COLLATE "C" NOT NULL,
    team_id uuid NULL,

    CONSTRAINT pk_sports_result_version_slot PRIMARY KEY (version_id, slot_no),
    CONSTRAINT fk_sports_result_version_slot_version
        FOREIGN KEY (version_id)
        REFERENCES public.sports_result_version (version_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sports_result_version_slot_team
        FOREIGN KEY (team_id)
        REFERENCES public.sports_team (team_id),
    CONSTRAINT ck_sports_result_version_slot_no CHECK (slot_no BETWEEN 1 AND 32),
    CONSTRAINT ck_sports_result_version_slot_score CHECK (score >= 0),
    CONSTRAINT ck_sports_result_version_slot_label
        CHECK (btrim(display_label) <> '' AND octet_length(display_label) <= 160)
);

-- 6. Add foreign key from sports_event.confirmed_version_id to sports_result_version
ALTER TABLE public.sports_event
    ADD CONSTRAINT fk_sports_event_confirmed_version
        FOREIGN KEY (event_id, confirmed_version_id)
        REFERENCES public.sports_result_version (event_id, version_id);

-- 7. Trigger to enforce exactly two slots per result version (HEAD_TO_HEAD format)
CREATE OR REPLACE FUNCTION public.enforce_sports_result_version_slot_count()
RETURNS TRIGGER AS $$
DECLARE
    persisted_slot_count integer;
BEGIN
    SELECT count(*)
    INTO persisted_slot_count
    FROM public.sports_result_version_slot
    WHERE version_id = NEW.version_id;

    IF persisted_slot_count <> 2 THEN
        RAISE EXCEPTION
            'HEAD_TO_HEAD result version must contain exactly two immutable slots';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_enforce_sports_result_slot_count_parent
AFTER INSERT ON public.sports_result_version
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_result_version_slot_count();

CREATE CONSTRAINT TRIGGER trg_enforce_sports_result_slot_count_child
AFTER INSERT ON public.sports_result_version_slot
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_result_version_slot_count();

-- 8. Immutability triggers on sports_result_version and sports_result_version_slot
CREATE TRIGGER trg_prevent_sports_result_version_modification
BEFORE UPDATE OR DELETE ON public.sports_result_version
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_result_version_slot_modification
BEFORE UPDATE OR DELETE ON public.sports_result_version_slot
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();
