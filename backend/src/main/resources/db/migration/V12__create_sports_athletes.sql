CREATE TABLE public.sports_athlete (
    athlete_id uuid NOT NULL DEFAULT gen_random_uuid(),
    name varchar(160) COLLATE "C" NOT NULL,
    slug varchar(96) COLLATE "C" NOT NULL,
    matricula_hmac bytea NULL,
    hmac_version smallint NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'RASCUNHO',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_athlete PRIMARY KEY (athlete_id),
    CONSTRAINT uq_sports_athlete_slug UNIQUE (slug),
    CONSTRAINT ck_sports_athlete_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_sports_athlete_name_octets CHECK (octet_length(name) <= 160),
    CONSTRAINT ck_sports_athlete_slug_format
        CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_sports_athlete_slug_octets CHECK (octet_length(slug) <= 96),
    CONSTRAINT ck_sports_athlete_status CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    CONSTRAINT ck_sports_athlete_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_athlete_timestamp_order CHECK (updated_at >= created_at),
    CONSTRAINT ck_sports_athlete_hmac_octets
        CHECK (matricula_hmac IS NULL OR octet_length(matricula_hmac) = 32),
    CONSTRAINT ck_sports_athlete_hmac_version
        CHECK ((matricula_hmac IS NULL AND hmac_version IS NULL) OR (matricula_hmac IS NOT NULL AND hmac_version >= 1))
);

CREATE UNIQUE INDEX uq_sports_athlete_name_normalized
    ON public.sports_athlete (lower(btrim(name)));

CREATE UNIQUE INDEX uq_sports_athlete_matricula_hmac
    ON public.sports_athlete (matricula_hmac)
    WHERE matricula_hmac IS NOT NULL;

COMMENT ON TABLE public.sports_athlete IS
    'Registro esportivo global de atletas com HMAC versionado (D-58, D-69, V12).';
