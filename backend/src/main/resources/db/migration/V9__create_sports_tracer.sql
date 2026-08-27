CREATE TABLE public.sports_competition (
    competition_id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'ATIVA',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_competition PRIMARY KEY (competition_id),
    CONSTRAINT uq_sports_competition_slug UNIQUE (slug),
    CONSTRAINT ck_sports_competition_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_competition_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_competition_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_competition_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_competition_status CHECK (status = 'ATIVA'),
    CONSTRAINT ck_sports_competition_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_competition_timestamp_order CHECK (updated_at >= created_at)
);

CREATE TABLE public.sports_edition (
    edition_id uuid NOT NULL DEFAULT gen_random_uuid(),
    competition_id uuid NOT NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    start_date date NULL,
    end_date date NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'ATIVA',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_edition PRIMARY KEY (edition_id),
    CONSTRAINT fk_sports_edition_competition
        FOREIGN KEY (competition_id)
        REFERENCES public.sports_competition (competition_id),
    CONSTRAINT uq_sports_edition_competition_slug UNIQUE (competition_id, slug),
    CONSTRAINT uq_sports_edition_competition_id UNIQUE (competition_id, edition_id),
    CONSTRAINT ck_sports_edition_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_edition_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_edition_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_edition_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_edition_date_order
        CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_sports_edition_status CHECK (status = 'ATIVA'),
    CONSTRAINT ck_sports_edition_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_edition_timestamp_order CHECK (updated_at >= created_at)
);

CREATE TABLE public.sports_event (
    event_id uuid NOT NULL DEFAULT gen_random_uuid(),
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    title varchar(200) COLLATE "C" NOT NULL,
    event_format varchar(32) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'RASCUNHO',
    scheduled_at timestamptz(6) NOT NULL,
    zone_id varchar(64) COLLATE "C" NOT NULL,
    venue_state varchar(32) COLLATE "C" NOT NULL,
    source_reference varchar(2048) COLLATE "C" NOT NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_event PRIMARY KEY (event_id),
    CONSTRAINT uq_sports_event_scope_id UNIQUE (competition_id, edition_id, event_id),
    CONSTRAINT fk_sports_event_edition_scope
        FOREIGN KEY (competition_id, edition_id)
        REFERENCES public.sports_edition (competition_id, edition_id),
    CONSTRAINT ck_sports_event_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT ck_sports_event_title_octets CHECK (octet_length(title) <= 200),
    CONSTRAINT ck_sports_event_format CHECK (event_format = 'HEAD_TO_HEAD'),
    CONSTRAINT ck_sports_event_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_event_zone_not_blank CHECK (btrim(zone_id) <> ''),
    CONSTRAINT ck_sports_event_zone_octets CHECK (octet_length(zone_id) <= 64),
    CONSTRAINT ck_sports_event_venue_state CHECK (venue_state = 'LOCAL_A_DEFINIR'),
    CONSTRAINT ck_sports_event_source_not_blank CHECK (btrim(source_reference) <> ''),
    CONSTRAINT ck_sports_event_source_octets CHECK (octet_length(source_reference) <= 2048),
    CONSTRAINT ck_sports_event_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_event_timestamp_order CHECK (updated_at >= created_at)
);

CREATE INDEX idx_sports_event_edition
    ON public.sports_event (edition_id, scheduled_at, event_id);

CREATE TABLE public.sports_event_slot (
    event_id uuid NOT NULL,
    slot_no smallint NOT NULL,
    display_label varchar(160) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_event_slot PRIMARY KEY (event_id, slot_no),
    CONSTRAINT fk_sports_event_slot_event
        FOREIGN KEY (event_id)
        REFERENCES public.sports_event (event_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_event_slot_no CHECK (slot_no BETWEEN 1 AND 32),
    CONSTRAINT ck_sports_event_slot_label_not_blank CHECK (btrim(display_label) <> ''),
    CONSTRAINT ck_sports_event_slot_label_octets CHECK (octet_length(display_label) <= 160)
);

CREATE TABLE public.sports_event_publication (
    publication_id uuid NOT NULL DEFAULT gen_random_uuid(),
    event_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    version_no bigint NOT NULL,
    competition_name varchar(160) COLLATE "C" NOT NULL,
    edition_name varchar(160) COLLATE "C" NOT NULL,
    title varchar(200) COLLATE "C" NOT NULL,
    event_format varchar(32) COLLATE "C" NOT NULL,
    scheduled_at timestamptz(6) NOT NULL,
    zone_id varchar(64) COLLATE "C" NOT NULL,
    venue_state varchar(32) COLLATE "C" NOT NULL,
    source_reference varchar(2048) COLLATE "C" NOT NULL,
    published_by_identity_id uuid NOT NULL,
    published_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    supersedes_publication_id uuid NULL,
    base_lock_version bigint NOT NULL,

    CONSTRAINT pk_sports_event_publication PRIMARY KEY (publication_id),
    CONSTRAINT fk_sports_event_publication_event_scope
        FOREIGN KEY (competition_id, edition_id, event_id)
        REFERENCES public.sports_event (competition_id, edition_id, event_id),
    CONSTRAINT fk_sports_event_publication_identity
        FOREIGN KEY (published_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT uq_sports_event_publication_version UNIQUE (event_id, version_no),
    CONSTRAINT uq_sports_event_publication_event_id UNIQUE (event_id, publication_id),
    CONSTRAINT fk_sports_event_publication_supersedes
        FOREIGN KEY (event_id, supersedes_publication_id)
        REFERENCES public.sports_event_publication (event_id, publication_id),
    CONSTRAINT ck_sports_event_publication_version CHECK (version_no > 0),
    CONSTRAINT ck_sports_event_publication_base_lock CHECK (base_lock_version >= 0),
    CONSTRAINT ck_sports_event_publication_not_self_superseding
        CHECK (supersedes_publication_id IS NULL OR supersedes_publication_id <> publication_id),
    CONSTRAINT ck_sports_event_publication_competition_name CHECK (btrim(competition_name) <> ''),
    CONSTRAINT ck_sports_event_publication_competition_name_octets
        CHECK (octet_length(competition_name) <= 160),
    CONSTRAINT ck_sports_event_publication_edition_name CHECK (btrim(edition_name) <> ''),
    CONSTRAINT ck_sports_event_publication_edition_name_octets
        CHECK (octet_length(edition_name) <= 160),
    CONSTRAINT ck_sports_event_publication_title CHECK (btrim(title) <> ''),
    CONSTRAINT ck_sports_event_publication_title_octets CHECK (octet_length(title) <= 200),
    CONSTRAINT ck_sports_event_publication_format CHECK (event_format = 'HEAD_TO_HEAD'),
    CONSTRAINT ck_sports_event_publication_zone CHECK (btrim(zone_id) <> ''),
    CONSTRAINT ck_sports_event_publication_zone_octets CHECK (octet_length(zone_id) <= 64),
    CONSTRAINT ck_sports_event_publication_venue_state CHECK (venue_state = 'LOCAL_A_DEFINIR'),
    CONSTRAINT ck_sports_event_publication_source CHECK (btrim(source_reference) <> ''),
    CONSTRAINT ck_sports_event_publication_source_octets
        CHECK (octet_length(source_reference) <= 2048)
);

CREATE INDEX idx_sports_event_publication_latest
    ON public.sports_event_publication (event_id, version_no DESC);

CREATE TABLE public.sports_event_publication_slot (
    publication_id uuid NOT NULL,
    slot_no smallint NOT NULL,
    display_label varchar(160) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_event_publication_slot PRIMARY KEY (publication_id, slot_no),
    CONSTRAINT fk_sports_event_publication_slot_publication
        FOREIGN KEY (publication_id)
        REFERENCES public.sports_event_publication (publication_id),
    CONSTRAINT ck_sports_event_publication_slot_no CHECK (slot_no BETWEEN 1 AND 32),
    CONSTRAINT ck_sports_event_publication_slot_label CHECK (btrim(display_label) <> ''),
    CONSTRAINT ck_sports_event_publication_slot_label_octets
        CHECK (octet_length(display_label) <= 160)
);

CREATE OR REPLACE FUNCTION public.enforce_sports_event_publication_slot_count()
RETURNS TRIGGER AS $$
DECLARE
    persisted_slot_count integer;
BEGIN
    SELECT count(*)
    INTO persisted_slot_count
    FROM public.sports_event_publication_slot
    WHERE publication_id = NEW.publication_id;

    IF persisted_slot_count <> 2 THEN
        RAISE EXCEPTION
            'HEAD_TO_HEAD publication snapshots must contain exactly two immutable slots';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_enforce_sports_publication_slot_count_parent
AFTER INSERT ON public.sports_event_publication
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_event_publication_slot_count();

CREATE CONSTRAINT TRIGGER trg_enforce_sports_publication_slot_count_child
AFTER INSERT ON public.sports_event_publication_slot
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION public.enforce_sports_event_publication_slot_count();

CREATE OR REPLACE FUNCTION public.prevent_sports_publication_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'sports publication snapshots are immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_sports_event_publication_modification
BEFORE UPDATE OR DELETE ON public.sports_event_publication
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_event_publication_slot_modification
BEFORE UPDATE OR DELETE ON public.sports_event_publication_slot
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();
