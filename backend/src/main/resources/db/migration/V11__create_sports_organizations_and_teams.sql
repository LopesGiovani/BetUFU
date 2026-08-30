CREATE TABLE public.sports_organization (
    organization_id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'RASCUNHO',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_organization PRIMARY KEY (organization_id),
    CONSTRAINT uq_sports_organization_slug UNIQUE (slug),
    CONSTRAINT ck_sports_organization_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_organization_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_organization_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_organization_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_organization_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_organization_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_organization_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_organization_name_normalized
    ON public.sports_organization (lower(btrim(name)));

CREATE TABLE public.sports_team (
    team_id uuid NOT NULL DEFAULT gen_random_uuid(),
    organization_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    sport_id uuid NOT NULL,
    category_id uuid NOT NULL,
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'RASCUNHO',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_team PRIMARY KEY (team_id),
    CONSTRAINT uq_sports_team_scope_slug UNIQUE (edition_id, sport_id, category_id, slug),
    CONSTRAINT fk_sports_team_organization
        FOREIGN KEY (organization_id)
        REFERENCES public.sports_organization (organization_id),
    CONSTRAINT fk_sports_team_edition
        FOREIGN KEY (edition_id)
        REFERENCES public.sports_edition (edition_id),
    CONSTRAINT fk_sports_team_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id),
    CONSTRAINT fk_sports_team_category_scope
        FOREIGN KEY (edition_id, category_id)
        REFERENCES public.sports_category (edition_id, category_id),
    CONSTRAINT ck_sports_team_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_team_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_team_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_team_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_team_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_team_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_team_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_team_scope_name_normalized
    ON public.sports_team (edition_id, sport_id, category_id, lower(btrim(name)));

ALTER TABLE public.betufu_profile
    ADD COLUMN favorite_organization_id uuid NULL;

ALTER TABLE public.betufu_profile
    ADD CONSTRAINT fk_betufu_profile_favorite_organization
        FOREIGN KEY (favorite_organization_id)
        REFERENCES public.sports_organization (organization_id);

CREATE INDEX idx_sports_team_organization
    ON public.sports_team (organization_id, edition_id, sport_id, category_id, team_id);

CREATE INDEX idx_sports_team_edition
    ON public.sports_team (edition_id, sport_id, category_id, team_id);

COMMENT ON COLUMN public.betufu_profile.favorite_organization_id IS
    'V11 semantica canonica; favorite_team_id legado permanece inerte ate reconciliacao autoritativa futura.';

-- V3 stored arbitrary favorite_team_id UUIDs without an authoritative team relation.
-- Therefore no textual or UUID-based backfill is safe; the new column intentionally starts NULL.
