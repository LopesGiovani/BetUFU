CREATE TABLE public.sports_event_lifecycle_history (
    lifecycle_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    sequence_no bigint NOT NULL,
    previous_status varchar(16) COLLATE "C" NULL,
    new_status varchar(16) COLLATE "C" NOT NULL,
    actor_identity_id uuid NOT NULL,
    source_reference varchar(2048) COLLATE "C" NOT NULL,
    reason varchar(1024) COLLATE "C" NULL,
    base_lock_version bigint NOT NULL,
    lock_version bigint NOT NULL,
    occurred_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_event_lifecycle_history PRIMARY KEY (lifecycle_id),
    CONSTRAINT uq_sports_event_lifecycle_history_event_sequence
        UNIQUE (event_id, sequence_no),
    CONSTRAINT fk_sports_event_lifecycle_history_event_scope
        FOREIGN KEY (competition_id, edition_id, event_id)
        REFERENCES public.sports_event (competition_id, edition_id, event_id),
    CONSTRAINT fk_sports_event_lifecycle_history_actor
        FOREIGN KEY (actor_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_sports_event_lifecycle_history_sequence
        CHECK (sequence_no > 0),
    CONSTRAINT ck_sports_event_lifecycle_history_previous_status
        CHECK (previous_status IS NULL OR previous_status IN ('RASCUNHO', 'PUBLICADO', 'SUSPENSO', 'ADIADO', 'CANCELADO')),
    CONSTRAINT ck_sports_event_lifecycle_history_new_status
        CHECK (new_status IN ('RASCUNHO', 'PUBLICADO', 'SUSPENSO', 'ADIADO', 'CANCELADO')),
    CONSTRAINT ck_sports_event_lifecycle_history_source
        CHECK (btrim(source_reference) <> '' AND octet_length(source_reference) <= 2048),
    CONSTRAINT ck_sports_event_lifecycle_history_reason
        CHECK (reason IS NULL OR (btrim(reason) <> '' AND octet_length(reason) <= 1024)),
    CONSTRAINT ck_sports_event_lifecycle_history_versions
        CHECK (base_lock_version >= 0 AND lock_version >= base_lock_version),
    CONSTRAINT ck_sports_event_lifecycle_history_initial_entry
        CHECK (
            (previous_status IS NULL
                AND new_status = 'RASCUNHO'
                AND sequence_no = 1
                AND base_lock_version = 0
                AND lock_version = 0)
            OR
            (previous_status IS NOT NULL
                -- Events created before V24 start at their first observed transition.
                AND sequence_no >= 1
                AND lock_version = base_lock_version + 1)
        )
);

CREATE INDEX idx_sports_event_lifecycle_history_event_order
    ON public.sports_event_lifecycle_history (event_id, sequence_no ASC, occurred_at ASC, lifecycle_id ASC);

CREATE OR REPLACE FUNCTION public.prevent_sports_event_lifecycle_history_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'sports event lifecycle history is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_sports_event_lifecycle_history_modification
BEFORE UPDATE OR DELETE ON public.sports_event_lifecycle_history
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_event_lifecycle_history_modification();
