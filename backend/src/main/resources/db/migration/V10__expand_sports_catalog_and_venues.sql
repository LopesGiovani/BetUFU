CREATE TABLE public.sports_sport (
    sport_id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'ATIVA',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_sport PRIMARY KEY (sport_id),
    CONSTRAINT uq_sports_sport_slug UNIQUE (slug),
    CONSTRAINT ck_sports_sport_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_sport_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_sport_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_sport_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_sport_status CHECK (status = 'ATIVA'),
    CONSTRAINT ck_sports_sport_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_sport_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_sport_name_normalized
    ON public.sports_sport (lower(btrim(name)));

ALTER TABLE public.sports_competition
    ADD COLUMN sport_id uuid NULL;

ALTER TABLE public.sports_competition
    ADD CONSTRAINT fk_sports_competition_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id);

CREATE INDEX idx_sports_competition_sport
    ON public.sports_competition (sport_id, name, competition_id);

CREATE TABLE public.sports_category (
    category_id uuid NOT NULL DEFAULT gen_random_uuid(),
    edition_id uuid NOT NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'ATIVA',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_category PRIMARY KEY (category_id),
    CONSTRAINT uq_sports_category_edition_id UNIQUE (edition_id, category_id),
    CONSTRAINT uq_sports_category_edition_slug UNIQUE (edition_id, slug),
    CONSTRAINT fk_sports_category_edition
        FOREIGN KEY (edition_id)
        REFERENCES public.sports_edition (edition_id),
    CONSTRAINT ck_sports_category_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_category_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_category_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_category_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_category_status CHECK (status = 'ATIVA'),
    CONSTRAINT ck_sports_category_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_category_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_category_edition_name_normalized
    ON public.sports_category (edition_id, lower(btrim(name)));

CREATE TABLE public.sports_phase (
    phase_id uuid NOT NULL DEFAULT gen_random_uuid(),
    edition_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'ATIVA',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_phase PRIMARY KEY (phase_id),
    CONSTRAINT uq_sports_phase_edition_id UNIQUE (edition_id, phase_id),
    CONSTRAINT uq_sports_phase_category_slug UNIQUE (category_id, slug),
    CONSTRAINT fk_sports_phase_category
        FOREIGN KEY (edition_id, category_id)
        REFERENCES public.sports_category (edition_id, category_id),
    CONSTRAINT ck_sports_phase_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_phase_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_phase_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_phase_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_phase_status CHECK (status = 'ATIVA'),
    CONSTRAINT ck_sports_phase_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_phase_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_phase_category_name_normalized
    ON public.sports_phase (category_id, lower(btrim(name)));

CREATE TABLE public.sports_venue (
    venue_id uuid NOT NULL DEFAULT gen_random_uuid(),
    competition_id uuid NOT NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    address varchar(320) COLLATE "C" NULL,
    map_url varchar(2048) COLLATE "C" NULL,
    notes varchar(2000) COLLATE "C" NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_venue PRIMARY KEY (venue_id),
    CONSTRAINT uq_sports_venue_competition_id UNIQUE (competition_id, venue_id),
    CONSTRAINT fk_sports_venue_competition
        FOREIGN KEY (competition_id)
        REFERENCES public.sports_competition (competition_id),
    CONSTRAINT ck_sports_venue_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_venue_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_venue_address_octets
        CHECK (address IS NULL OR octet_length(address) <= 320),
    CONSTRAINT ck_sports_venue_map_url_octets
        CHECK (map_url IS NULL OR octet_length(map_url) <= 2048),
    CONSTRAINT ck_sports_venue_notes_octets
        CHECK (notes IS NULL OR octet_length(notes) <= 2000),
    CONSTRAINT ck_sports_venue_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_venue_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_venue_identity_normalized
    ON public.sports_venue (
        competition_id,
        lower(btrim(name)),
        coalesce(lower(btrim(address)), '')
    );

CREATE TABLE public.sports_venue_space (
    venue_space_id uuid NOT NULL DEFAULT gen_random_uuid(),
    venue_id uuid NOT NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    notes varchar(1000) COLLATE "C" NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_venue_space PRIMARY KEY (venue_space_id),
    CONSTRAINT uq_sports_venue_space_venue_id UNIQUE (venue_id, venue_space_id),
    CONSTRAINT fk_sports_venue_space_venue
        FOREIGN KEY (venue_id)
        REFERENCES public.sports_venue (venue_id),
    CONSTRAINT ck_sports_venue_space_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_venue_space_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_venue_space_notes_octets
        CHECK (notes IS NULL OR octet_length(notes) <= 1000),
    CONSTRAINT ck_sports_venue_space_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_venue_space_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_venue_space_name_normalized
    ON public.sports_venue_space (venue_id, lower(btrim(name)));

ALTER TABLE public.sports_event
    ADD COLUMN sport_id uuid NULL,
    ADD COLUMN category_id uuid NULL,
    ADD COLUMN phase_id uuid NULL,
    ADD COLUMN venue_id uuid NULL,
    ADD COLUMN venue_space_id uuid NULL;

ALTER TABLE public.sports_event
    ADD CONSTRAINT fk_sports_event_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id),
    ADD CONSTRAINT fk_sports_event_category_scope
        FOREIGN KEY (edition_id, category_id)
        REFERENCES public.sports_category (edition_id, category_id),
    ADD CONSTRAINT fk_sports_event_phase_scope
        FOREIGN KEY (edition_id, phase_id)
        REFERENCES public.sports_phase (edition_id, phase_id),
    ADD CONSTRAINT fk_sports_event_venue
        FOREIGN KEY (competition_id, venue_id)
        REFERENCES public.sports_venue (competition_id, venue_id),
    ADD CONSTRAINT fk_sports_event_venue_space_scope
        FOREIGN KEY (venue_id, venue_space_id)
        REFERENCES public.sports_venue_space (venue_id, venue_space_id);

ALTER TABLE public.sports_event
    DROP CONSTRAINT ck_sports_event_venue_state;

ALTER TABLE public.sports_event
    ADD CONSTRAINT ck_sports_event_venue_state
        CHECK (
            (venue_state = 'LOCAL_A_DEFINIR'
                AND venue_id IS NULL
                AND venue_space_id IS NULL)
            OR (venue_state = 'LOCAL_DEFINIDO'
                AND venue_id IS NOT NULL)
        );

CREATE INDEX idx_sports_event_phase
    ON public.sports_event (phase_id, scheduled_at, event_id);

CREATE INDEX idx_sports_event_sport
    ON public.sports_event (sport_id, scheduled_at, event_id);

CREATE INDEX idx_sports_event_category
    ON public.sports_event (category_id, scheduled_at, event_id);

ALTER TABLE public.sports_event_publication
    ADD COLUMN sport_id uuid NULL,
    ADD COLUMN sport_name varchar(160) COLLATE "C" NULL,
    ADD COLUMN category_id uuid NULL,
    ADD COLUMN category_name varchar(160) COLLATE "C" NULL,
    ADD COLUMN phase_id uuid NULL,
    ADD COLUMN phase_name varchar(160) COLLATE "C" NULL,
    ADD COLUMN venue_id uuid NULL,
    ADD COLUMN venue_name varchar(160) COLLATE "C" NULL,
    ADD COLUMN venue_space_id uuid NULL,
    ADD COLUMN venue_space_name varchar(160) COLLATE "C" NULL;

ALTER TABLE public.sports_event_publication
    DROP CONSTRAINT ck_sports_event_publication_venue_state;

ALTER TABLE public.sports_event_publication
    ADD CONSTRAINT fk_sports_event_publication_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id),
    ADD CONSTRAINT fk_sports_event_publication_category
        FOREIGN KEY (edition_id, category_id)
        REFERENCES public.sports_category (edition_id, category_id),
    ADD CONSTRAINT fk_sports_event_publication_phase
        FOREIGN KEY (edition_id, phase_id)
        REFERENCES public.sports_phase (edition_id, phase_id),
    ADD CONSTRAINT fk_sports_event_publication_venue
        FOREIGN KEY (competition_id, venue_id)
        REFERENCES public.sports_venue (competition_id, venue_id),
    ADD CONSTRAINT fk_sports_event_publication_venue_space_scope
        FOREIGN KEY (venue_id, venue_space_id)
        REFERENCES public.sports_venue_space (venue_id, venue_space_id),
    ADD CONSTRAINT ck_sports_event_publication_venue_state
        CHECK (
            (venue_state = 'LOCAL_A_DEFINIR'
                AND venue_id IS NULL
                AND venue_space_id IS NULL)
            OR (venue_state = 'LOCAL_DEFINIDO'
                AND venue_id IS NOT NULL)
        );
