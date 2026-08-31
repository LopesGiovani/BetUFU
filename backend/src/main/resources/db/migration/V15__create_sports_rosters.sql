CREATE TABLE public.sports_roster (
    roster_id uuid NOT NULL DEFAULT gen_random_uuid(),
    team_id uuid NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'RASCUNHO',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_roster PRIMARY KEY (roster_id),
    CONSTRAINT uq_sports_roster_team UNIQUE (team_id),
    CONSTRAINT fk_sports_roster_team
        FOREIGN KEY (team_id)
        REFERENCES public.sports_team (team_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_sports_roster_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_roster_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_roster_timestamp_order CHECK (updated_at >= created_at)
);

CREATE TABLE public.sports_roster_member (
    roster_id uuid NOT NULL,
    athlete_id uuid NOT NULL,
    jersey_number integer NULL,
    position_name varchar(64) COLLATE "C" NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_roster_member PRIMARY KEY (roster_id, athlete_id),
    CONSTRAINT fk_sports_roster_member_roster
        FOREIGN KEY (roster_id)
        REFERENCES public.sports_roster (roster_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_sports_roster_member_athlete
        FOREIGN KEY (athlete_id)
        REFERENCES public.sports_athlete (athlete_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_sports_roster_member_jersey_number
        CHECK (jersey_number IS NULL OR (jersey_number BETWEEN 1 AND 99)),
    CONSTRAINT ck_sports_roster_member_position_octets
        CHECK (position_name IS NULL OR (btrim(position_name) <> '' AND octet_length(position_name) <= 64))
);

CREATE TABLE public.sports_roster_publication (
    publication_id uuid NOT NULL DEFAULT gen_random_uuid(),
    roster_id uuid NOT NULL,
    team_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    organization_id uuid NOT NULL,
    sport_id uuid NOT NULL,
    category_id uuid NOT NULL,
    version_no bigint NOT NULL,
    team_name varchar(160) COLLATE "C" NOT NULL,
    organization_name varchar(160) COLLATE "C" NOT NULL,
    edition_name varchar(160) COLLATE "C" NOT NULL,
    competition_name varchar(160) COLLATE "C" NOT NULL,
    sport_name varchar(160) COLLATE "C" NOT NULL,
    category_name varchar(160) COLLATE "C" NOT NULL,
    reason varchar(1024) COLLATE "C" NULL,
    published_by_identity_id uuid NOT NULL,
    published_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    supersedes_publication_id uuid NULL,
    base_lock_version bigint NOT NULL,

    CONSTRAINT pk_sports_roster_publication PRIMARY KEY (publication_id),
    CONSTRAINT fk_sports_roster_publication_roster
        FOREIGN KEY (roster_id)
        REFERENCES public.sports_roster (roster_id),
    CONSTRAINT fk_sports_roster_publication_team
        FOREIGN KEY (team_id)
        REFERENCES public.sports_team (team_id),
    CONSTRAINT fk_sports_roster_publication_identity
        FOREIGN KEY (published_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT uq_sports_roster_publication_version UNIQUE (roster_id, version_no),
    CONSTRAINT uq_sports_roster_publication_roster_pub UNIQUE (roster_id, publication_id),
    CONSTRAINT fk_sports_roster_publication_supersedes
        FOREIGN KEY (roster_id, supersedes_publication_id)
        REFERENCES public.sports_roster_publication (roster_id, publication_id),
    CONSTRAINT ck_sports_roster_publication_version CHECK (version_no > 0),
    CONSTRAINT ck_sports_roster_publication_base_lock CHECK (base_lock_version >= 0),
    CONSTRAINT ck_sports_roster_publication_not_self_superseding
        CHECK (supersedes_publication_id IS NULL OR supersedes_publication_id <> publication_id),
    CONSTRAINT ck_sports_roster_publication_reason_for_corrections
        CHECK ((version_no = 1 AND supersedes_publication_id IS NULL) OR (version_no > 1 AND supersedes_publication_id IS NOT NULL AND reason IS NOT NULL AND btrim(reason) <> '')),
    CONSTRAINT ck_sports_roster_publication_reason_octets
        CHECK (reason IS NULL OR octet_length(reason) <= 1024),
    CONSTRAINT ck_sports_roster_publication_team_name
        CHECK (btrim(team_name) <> '' AND octet_length(team_name) <= 160),
    CONSTRAINT ck_sports_roster_publication_org_name
        CHECK (btrim(organization_name) <> '' AND octet_length(organization_name) <= 160),
    CONSTRAINT ck_sports_roster_publication_edition_name
        CHECK (btrim(edition_name) <> '' AND octet_length(edition_name) <= 160),
    CONSTRAINT ck_sports_roster_publication_comp_name
        CHECK (btrim(competition_name) <> '' AND octet_length(competition_name) <= 160),
    CONSTRAINT ck_sports_roster_publication_sport_name
        CHECK (btrim(sport_name) <> '' AND octet_length(sport_name) <= 160),
    CONSTRAINT ck_sports_roster_publication_cat_name
        CHECK (btrim(category_name) <> '' AND octet_length(category_name) <= 160)
);

CREATE INDEX idx_sports_roster_publication_latest
    ON public.sports_roster_publication (roster_id, version_no DESC);

CREATE INDEX idx_sports_roster_publication_team
    ON public.sports_roster_publication (team_id, version_no DESC);

CREATE TABLE public.sports_roster_publication_member (
    publication_id uuid NOT NULL,
    athlete_id uuid NOT NULL,
    athlete_name varchar(160) COLLATE "C" NOT NULL,
    athlete_slug varchar(96) COLLATE "C" NOT NULL,
    jersey_number integer NULL,
    position_name varchar(64) COLLATE "C" NULL,

    CONSTRAINT pk_sports_roster_publication_member PRIMARY KEY (publication_id, athlete_id),
    CONSTRAINT fk_sports_roster_publication_member_publication
        FOREIGN KEY (publication_id)
        REFERENCES public.sports_roster_publication (publication_id),
    CONSTRAINT fk_sports_roster_publication_member_athlete
        FOREIGN KEY (athlete_id)
        REFERENCES public.sports_athlete (athlete_id),
    CONSTRAINT ck_sports_roster_pub_member_name
        CHECK (btrim(athlete_name) <> '' AND octet_length(athlete_name) <= 160),
    CONSTRAINT ck_sports_roster_pub_member_slug
        CHECK (athlete_slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND octet_length(athlete_slug) <= 96),
    CONSTRAINT ck_sports_roster_pub_member_jersey
        CHECK (jersey_number IS NULL OR (jersey_number BETWEEN 1 AND 99)),
    CONSTRAINT ck_sports_roster_pub_member_pos
        CHECK (position_name IS NULL OR (btrim(position_name) <> '' AND octet_length(position_name) <= 64))
);

CREATE TRIGGER trg_prevent_sports_roster_publication_modification
BEFORE UPDATE OR DELETE ON public.sports_roster_publication
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();

CREATE TRIGGER trg_prevent_sports_roster_publication_member_modification
BEFORE UPDATE OR DELETE ON public.sports_roster_publication_member
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_publication_modification();
