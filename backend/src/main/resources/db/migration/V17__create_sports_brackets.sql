-- 1. Create sports_bracket table
CREATE TABLE public.sports_bracket (
    bracket_id uuid NOT NULL DEFAULT gen_random_uuid(),
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    sport_id uuid NULL,
    category_id uuid NULL,
    phase_id uuid NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'RASCUNHO',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_bracket PRIMARY KEY (bracket_id),
    CONSTRAINT uq_sports_bracket_edition_id UNIQUE (edition_id, bracket_id),
    CONSTRAINT uq_sports_bracket_edition_slug UNIQUE (edition_id, slug),
    CONSTRAINT fk_sports_bracket_edition_scope
        FOREIGN KEY (competition_id, edition_id)
        REFERENCES public.sports_edition (competition_id, edition_id),
    CONSTRAINT fk_sports_bracket_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id),
    CONSTRAINT fk_sports_bracket_category
        FOREIGN KEY (category_id)
        REFERENCES public.sports_category (category_id),
    CONSTRAINT fk_sports_bracket_phase
        FOREIGN KEY (phase_id)
        REFERENCES public.sports_phase (phase_id),
    CONSTRAINT ck_sports_bracket_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_bracket_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_bracket_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_bracket_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_bracket_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_bracket_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_bracket_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_bracket_edition_name_normalized
    ON public.sports_bracket (edition_id, lower(btrim(name)));

CREATE INDEX idx_sports_bracket_edition
    ON public.sports_bracket (edition_id, status);

-- 2. Create sports_bracket_match table
CREATE TABLE public.sports_bracket_match (
    match_id uuid NOT NULL DEFAULT gen_random_uuid(),
    bracket_id uuid NOT NULL,
    round_no smallint NOT NULL,
    match_no smallint NOT NULL,
    round_name varchar(160) COLLATE "C" NOT NULL,
    event_id uuid NULL,
    is_third_place boolean NOT NULL DEFAULT false,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_bracket_match PRIMARY KEY (match_id),
    CONSTRAINT uq_sports_bracket_match_pos UNIQUE (bracket_id, round_no, match_no),
    CONSTRAINT uq_sports_bracket_match_id UNIQUE (bracket_id, match_id),
    CONSTRAINT fk_sports_bracket_match_bracket
        FOREIGN KEY (bracket_id)
        REFERENCES public.sports_bracket (bracket_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sports_bracket_match_event
        FOREIGN KEY (event_id)
        REFERENCES public.sports_event (event_id)
        ON DELETE SET NULL,
    CONSTRAINT ck_sports_bracket_match_round_no CHECK (round_no > 0),
    CONSTRAINT ck_sports_bracket_match_match_no CHECK (match_no > 0),
    CONSTRAINT ck_sports_bracket_match_round_name
        CHECK (btrim(round_name) <> '' AND octet_length(round_name) <= 160)
);

CREATE INDEX idx_sports_bracket_match_bracket_round
    ON public.sports_bracket_match (bracket_id, round_no, match_no);

-- 3. Create sports_bracket_match_slot table
CREATE TABLE public.sports_bracket_match_slot (
    match_id uuid NOT NULL,
    slot_no smallint NOT NULL,
    source_type varchar(32) COLLATE "C" NOT NULL,
    source_match_id uuid NULL,
    seed_label varchar(160) COLLATE "C" NULL,

    CONSTRAINT pk_sports_bracket_match_slot PRIMARY KEY (match_id, slot_no),
    CONSTRAINT fk_sports_bracket_match_slot_match
        FOREIGN KEY (match_id)
        REFERENCES public.sports_bracket_match (match_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sports_bracket_match_slot_source_match
        FOREIGN KEY (source_match_id)
        REFERENCES public.sports_bracket_match (match_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_sports_bracket_match_slot_no CHECK (slot_no IN (1, 2)),
    CONSTRAINT ck_sports_bracket_match_slot_source_type
        CHECK (source_type IN ('DIRECT', 'PREDECESSOR_WINNER', 'PREDECESSOR_LOSER', 'BYE')),
    CONSTRAINT ck_sports_bracket_match_slot_source_consistency
        CHECK (
            (source_type IN ('PREDECESSOR_WINNER', 'PREDECESSOR_LOSER') AND source_match_id IS NOT NULL)
            OR (source_type IN ('DIRECT', 'BYE') AND source_match_id IS NULL)
        ),
    CONSTRAINT ck_sports_bracket_match_slot_not_self
        CHECK (source_match_id IS NULL OR source_match_id <> match_id),
    CONSTRAINT ck_sports_bracket_match_slot_seed_label
        CHECK (seed_label IS NULL OR (btrim(seed_label) <> '' AND octet_length(seed_label) <= 160))
);

-- 4. Create sports_bracket_publication table
CREATE TABLE public.sports_bracket_publication (
    publication_id uuid NOT NULL DEFAULT gen_random_uuid(),
    bracket_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    sport_id uuid NULL,
    category_id uuid NULL,
    phase_id uuid NULL,
    version_no bigint NOT NULL,
    bracket_name varchar(160) COLLATE "C" NOT NULL,
    competition_name varchar(160) COLLATE "C" NOT NULL,
    edition_name varchar(160) COLLATE "C" NOT NULL,
    sport_name varchar(160) COLLATE "C" NULL,
    category_name varchar(160) COLLATE "C" NULL,
    phase_name varchar(160) COLLATE "C" NULL,
    source_reference varchar(2048) COLLATE "C" NOT NULL,
    reason varchar(1024) COLLATE "C" NULL,
    published_by_identity_id uuid NOT NULL,
    published_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    supersedes_publication_id uuid NULL,
    base_lock_version bigint NOT NULL,

    CONSTRAINT pk_sports_bracket_publication PRIMARY KEY (publication_id),
    CONSTRAINT uq_sports_bracket_publication_version UNIQUE (bracket_id, version_no),
    CONSTRAINT uq_sports_bracket_publication_id UNIQUE (bracket_id, publication_id),
    CONSTRAINT fk_sports_bracket_publication_bracket
        FOREIGN KEY (bracket_id)
        REFERENCES public.sports_bracket (bracket_id),
    CONSTRAINT fk_sports_bracket_publication_identity
        FOREIGN KEY (published_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_sports_bracket_publication_supersedes
        FOREIGN KEY (bracket_id, supersedes_publication_id)
        REFERENCES public.sports_bracket_publication (bracket_id, publication_id),
    CONSTRAINT ck_sports_bracket_pub_version CHECK (version_no > 0),
    CONSTRAINT ck_sports_bracket_pub_base_lock CHECK (base_lock_version >= 0),
    CONSTRAINT ck_sports_bracket_pub_source
        CHECK (btrim(source_reference) <> '' AND octet_length(source_reference) <= 2048),
    CONSTRAINT ck_sports_bracket_pub_reason_octets
        CHECK (reason IS NULL OR octet_length(reason) <= 1024),
    CONSTRAINT ck_sports_bracket_pub_reason_corrections
        CHECK (
            (version_no = 1 AND supersedes_publication_id IS NULL)
            OR (version_no > 1 AND supersedes_publication_id IS NOT NULL AND reason IS NOT NULL AND btrim(reason) <> '')
        ),
    CONSTRAINT ck_sports_bracket_pub_not_self_superseding
        CHECK (supersedes_publication_id IS NULL OR supersedes_publication_id <> publication_id),
    CONSTRAINT ck_sports_bracket_pub_bracket_name
        CHECK (btrim(bracket_name) <> '' AND octet_length(bracket_name) <= 160),
    CONSTRAINT ck_sports_bracket_pub_comp_name
        CHECK (btrim(competition_name) <> '' AND octet_length(competition_name) <= 160),
    CONSTRAINT ck_sports_bracket_pub_edition_name
        CHECK (btrim(edition_name) <> '' AND octet_length(edition_name) <= 160),
    CONSTRAINT ck_sports_bracket_pub_sport_name
        CHECK (sport_name IS NULL OR (btrim(sport_name) <> '' AND octet_length(sport_name) <= 160)),
    CONSTRAINT ck_sports_bracket_pub_category_name
        CHECK (category_name IS NULL OR (btrim(category_name) <> '' AND octet_length(category_name) <= 160)),
    CONSTRAINT ck_sports_bracket_pub_phase_name
        CHECK (phase_name IS NULL OR (btrim(phase_name) <> '' AND octet_length(phase_name) <= 160))
);

CREATE INDEX idx_sports_bracket_publication_latest
    ON public.sports_bracket_publication (bracket_id, version_no DESC);

-- 5. Create sports_bracket_publication_node table
CREATE TABLE public.sports_bracket_publication_node (
    publication_id uuid NOT NULL,
    match_id uuid NOT NULL,
    round_no smallint NOT NULL,
    match_no smallint NOT NULL,
    round_name varchar(160) COLLATE "C" NOT NULL,
    event_id uuid NULL,
    is_third_place boolean NOT NULL DEFAULT false,
    slot1_source_type varchar(32) COLLATE "C" NOT NULL,
    slot1_source_match_id uuid NULL,
    slot1_seed_label varchar(160) COLLATE "C" NULL,
    slot2_source_type varchar(32) COLLATE "C" NOT NULL,
    slot2_source_match_id uuid NULL,
    slot2_seed_label varchar(160) COLLATE "C" NULL,

    CONSTRAINT pk_sports_bracket_publication_node PRIMARY KEY (publication_id, match_id),
    CONSTRAINT uq_sports_bracket_pub_node_pos UNIQUE (publication_id, round_no, match_no),
    CONSTRAINT fk_sports_bracket_pub_node_publication
        FOREIGN KEY (publication_id)
        REFERENCES public.sports_bracket_publication (publication_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_bracket_pub_node_round_no CHECK (round_no > 0),
    CONSTRAINT ck_sports_bracket_pub_node_match_no CHECK (match_no > 0),
    CONSTRAINT ck_sports_bracket_pub_node_round_name
        CHECK (btrim(round_name) <> '' AND octet_length(round_name) <= 160),
    CONSTRAINT ck_sports_bracket_pub_node_slot1_type
        CHECK (slot1_source_type IN ('DIRECT', 'PREDECESSOR_WINNER', 'PREDECESSOR_LOSER', 'BYE')),
    CONSTRAINT ck_sports_bracket_pub_node_slot2_type
        CHECK (slot2_source_type IN ('DIRECT', 'PREDECESSOR_WINNER', 'PREDECESSOR_LOSER', 'BYE')),
    CONSTRAINT ck_sports_bracket_pub_node_slot1_consistency
        CHECK (
            (slot1_source_type IN ('PREDECESSOR_WINNER', 'PREDECESSOR_LOSER') AND slot1_source_match_id IS NOT NULL)
            OR (slot1_source_type IN ('DIRECT', 'BYE') AND slot1_source_match_id IS NULL)
        ),
    CONSTRAINT ck_sports_bracket_pub_node_slot2_consistency
        CHECK (
            (slot2_source_type IN ('PREDECESSOR_WINNER', 'PREDECESSOR_LOSER') AND slot2_source_match_id IS NOT NULL)
            OR (slot2_source_type IN ('DIRECT', 'BYE') AND slot2_source_match_id IS NULL)
        )
);

-- 6. Immutability triggers
CREATE TRIGGER trg_prevent_sports_bracket_publication_modification
BEFORE UPDATE OR DELETE ON public.sports_bracket_publication
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_bracket_pub_node_modification
BEFORE UPDATE OR DELETE ON public.sports_bracket_publication_node
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();
