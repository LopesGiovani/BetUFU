-- 1. Create sports_standings table
CREATE TABLE public.sports_standings (
    standings_id uuid NOT NULL DEFAULT gen_random_uuid(),
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

    CONSTRAINT pk_sports_standings PRIMARY KEY (standings_id),
    CONSTRAINT uq_sports_standings_edition_id UNIQUE (edition_id, standings_id),
    CONSTRAINT uq_sports_standings_edition_slug UNIQUE (edition_id, slug),
    CONSTRAINT fk_sports_standings_edition_scope
        FOREIGN KEY (competition_id, edition_id)
        REFERENCES public.sports_edition (competition_id, edition_id),
    CONSTRAINT fk_sports_standings_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id),
    CONSTRAINT fk_sports_standings_category
        FOREIGN KEY (category_id)
        REFERENCES public.sports_category (category_id),
    CONSTRAINT fk_sports_standings_phase
        FOREIGN KEY (phase_id)
        REFERENCES public.sports_phase (phase_id),
    CONSTRAINT ck_sports_standings_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_standings_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_standings_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_standings_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_standings_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_standings_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_standings_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_standings_edition_name_normalized
    ON public.sports_standings (edition_id, lower(btrim(name)));

CREATE INDEX idx_sports_standings_edition
    ON public.sports_standings (edition_id, status);

-- 2. Create sports_standings_column table
CREATE TABLE public.sports_standings_column (
    standings_id uuid NOT NULL,
    column_order smallint NOT NULL,
    label varchar(64) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_standings_column PRIMARY KEY (standings_id, column_order),
    CONSTRAINT fk_sports_standings_column_standings
        FOREIGN KEY (standings_id)
        REFERENCES public.sports_standings (standings_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_standings_col_order CHECK (column_order > 0),
    CONSTRAINT ck_sports_standings_col_label
        CHECK (btrim(label) <> '' AND octet_length(label) <= 64)
);

-- 3. Create sports_standings_row table
CREATE TABLE public.sports_standings_row (
    row_id uuid NOT NULL DEFAULT gen_random_uuid(),
    standings_id uuid NOT NULL,
    row_order smallint NOT NULL,
    position smallint NOT NULL,
    team_id uuid NULL,
    participant_name varchar(160) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_standings_row PRIMARY KEY (row_id),
    CONSTRAINT uq_sports_standings_row_order UNIQUE (standings_id, row_order),
    CONSTRAINT uq_sports_standings_row_id UNIQUE (standings_id, row_id),
    CONSTRAINT fk_sports_standings_row_standings
        FOREIGN KEY (standings_id)
        REFERENCES public.sports_standings (standings_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sports_standings_row_team
        FOREIGN KEY (team_id)
        REFERENCES public.sports_team (team_id)
        ON DELETE SET NULL,
    CONSTRAINT ck_sports_standings_row_order CHECK (row_order > 0),
    CONSTRAINT ck_sports_standings_row_position CHECK (position > 0),
    CONSTRAINT ck_sports_standings_row_participant_name
        CHECK (btrim(participant_name) <> '' AND octet_length(participant_name) <= 160)
);

CREATE INDEX idx_sports_standings_row_standings_order
    ON public.sports_standings_row (standings_id, row_order);

-- 4. Create sports_standings_row_cell table
CREATE TABLE public.sports_standings_row_cell (
    row_id uuid NOT NULL,
    column_order smallint NOT NULL,
    cell_value varchar(64) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_standings_row_cell PRIMARY KEY (row_id, column_order),
    CONSTRAINT fk_sports_standings_row_cell_row
        FOREIGN KEY (row_id)
        REFERENCES public.sports_standings_row (row_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_standings_row_cell_column_order CHECK (column_order > 0),
    CONSTRAINT ck_sports_standings_row_cell_value
        CHECK (octet_length(cell_value) <= 64)
);

-- 5. Create sports_standings_publication table
CREATE TABLE public.sports_standings_publication (
    publication_id uuid NOT NULL DEFAULT gen_random_uuid(),
    standings_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    sport_id uuid NULL,
    category_id uuid NULL,
    phase_id uuid NULL,
    version_no bigint NOT NULL,
    standings_name varchar(160) COLLATE "C" NOT NULL,
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

    CONSTRAINT pk_sports_standings_publication PRIMARY KEY (publication_id),
    CONSTRAINT uq_sports_standings_publication_version UNIQUE (standings_id, version_no),
    CONSTRAINT uq_sports_standings_publication_id UNIQUE (standings_id, publication_id),
    CONSTRAINT fk_sports_standings_publication_standings
        FOREIGN KEY (standings_id)
        REFERENCES public.sports_standings (standings_id),
    CONSTRAINT fk_sports_standings_publication_identity
        FOREIGN KEY (published_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_sports_standings_publication_supersedes
        FOREIGN KEY (standings_id, supersedes_publication_id)
        REFERENCES public.sports_standings_publication (standings_id, publication_id),
    CONSTRAINT ck_sports_standings_pub_version CHECK (version_no > 0),
    CONSTRAINT ck_sports_standings_pub_base_lock CHECK (base_lock_version >= 0),
    CONSTRAINT ck_sports_standings_pub_source
        CHECK (btrim(source_reference) <> '' AND octet_length(source_reference) <= 2048),
    CONSTRAINT ck_sports_standings_pub_reason_octets
        CHECK (reason IS NULL OR octet_length(reason) <= 1024),
    CONSTRAINT ck_sports_standings_pub_reason_corrections
        CHECK (
            (version_no = 1 AND supersedes_publication_id IS NULL)
            OR (version_no > 1 AND supersedes_publication_id IS NOT NULL AND reason IS NOT NULL AND btrim(reason) <> '')
        ),
    CONSTRAINT ck_sports_standings_pub_not_self_superseding
        CHECK (supersedes_publication_id IS NULL OR supersedes_publication_id <> publication_id),
    CONSTRAINT ck_sports_standings_pub_standings_name
        CHECK (btrim(standings_name) <> '' AND octet_length(standings_name) <= 160),
    CONSTRAINT ck_sports_standings_pub_comp_name
        CHECK (btrim(competition_name) <> '' AND octet_length(competition_name) <= 160),
    CONSTRAINT ck_sports_standings_pub_edition_name
        CHECK (btrim(edition_name) <> '' AND octet_length(edition_name) <= 160),
    CONSTRAINT ck_sports_standings_pub_sport_name
        CHECK (sport_name IS NULL OR (btrim(sport_name) <> '' AND octet_length(sport_name) <= 160)),
    CONSTRAINT ck_sports_standings_pub_category_name
        CHECK (category_name IS NULL OR (btrim(category_name) <> '' AND octet_length(category_name) <= 160)),
    CONSTRAINT ck_sports_standings_pub_phase_name
        CHECK (phase_name IS NULL OR (btrim(phase_name) <> '' AND octet_length(phase_name) <= 160))
);

CREATE INDEX idx_sports_standings_publication_latest
    ON public.sports_standings_publication (standings_id, version_no DESC);

-- 6. Create sports_standings_publication_column table
CREATE TABLE public.sports_standings_publication_column (
    publication_id uuid NOT NULL,
    column_order smallint NOT NULL,
    label varchar(64) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_standings_pub_column PRIMARY KEY (publication_id, column_order),
    CONSTRAINT fk_sports_standings_pub_column_pub
        FOREIGN KEY (publication_id)
        REFERENCES public.sports_standings_publication (publication_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_standings_pub_col_order CHECK (column_order > 0),
    CONSTRAINT ck_sports_standings_pub_col_label
        CHECK (btrim(label) <> '' AND octet_length(label) <= 64)
);

-- 7. Create sports_standings_publication_row table
CREATE TABLE public.sports_standings_publication_row (
    publication_id uuid NOT NULL,
    row_order smallint NOT NULL,
    position smallint NOT NULL,
    team_id uuid NULL,
    participant_name varchar(160) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_standings_pub_row PRIMARY KEY (publication_id, row_order),
    CONSTRAINT fk_sports_standings_pub_row_pub
        FOREIGN KEY (publication_id)
        REFERENCES public.sports_standings_publication (publication_id)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_standings_pub_row_order CHECK (row_order > 0),
    CONSTRAINT ck_sports_standings_pub_row_pos CHECK (position > 0),
    CONSTRAINT ck_sports_standings_pub_row_part_name
        CHECK (btrim(participant_name) <> '' AND octet_length(participant_name) <= 160)
);

-- 8. Create sports_standings_publication_cell table
CREATE TABLE public.sports_standings_publication_cell (
    publication_id uuid NOT NULL,
    row_order smallint NOT NULL,
    column_order smallint NOT NULL,
    cell_value varchar(64) COLLATE "C" NOT NULL,

    CONSTRAINT pk_sports_standings_pub_cell PRIMARY KEY (publication_id, row_order, column_order),
    CONSTRAINT fk_sports_standings_pub_cell_row
        FOREIGN KEY (publication_id, row_order)
        REFERENCES public.sports_standings_publication_row (publication_id, row_order)
        ON DELETE CASCADE,
    CONSTRAINT ck_sports_standings_pub_cell_column_order CHECK (column_order > 0),
    CONSTRAINT ck_sports_standings_pub_cell_value
        CHECK (octet_length(cell_value) <= 64)
);

-- 9. Immutability triggers
CREATE TRIGGER trg_prevent_sports_standings_publication_modification
BEFORE UPDATE OR DELETE ON public.sports_standings_publication
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_standings_pub_col_modification
BEFORE UPDATE OR DELETE ON public.sports_standings_publication_column
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_standings_pub_row_modification
BEFORE UPDATE OR DELETE ON public.sports_standings_publication_row
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_standings_pub_cell_modification
BEFORE UPDATE OR DELETE ON public.sports_standings_publication_cell
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();
