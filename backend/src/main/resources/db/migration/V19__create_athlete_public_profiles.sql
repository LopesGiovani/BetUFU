CREATE TABLE public.sports_athlete_public_profile (
    athlete_id uuid NOT NULL,
    course varchar(120) NULL,
    bio varchar(500) NULL,
    instagram_url varchar(255) NULL,
    x_url varchar(255) NULL,
    linkedin_url varchar(255) NULL,
    strava_url varchar(255) NULL,
    youtube_url varchar(255) NULL,
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_athlete_public_profile
        PRIMARY KEY (athlete_id),

    CONSTRAINT fk_sports_athlete_public_profile_athlete
        FOREIGN KEY (athlete_id)
        REFERENCES public.sports_athlete (athlete_id),

    CONSTRAINT ck_sports_athlete_public_profile_course
        CHECK (course IS NULL OR (btrim(course) <> '' AND octet_length(course) <= 120)),

    CONSTRAINT ck_sports_athlete_public_profile_bio
        CHECK (bio IS NULL OR (btrim(bio) <> '' AND octet_length(bio) <= 500)),

    CONSTRAINT ck_sports_athlete_public_profile_instagram_url
        CHECK (instagram_url IS NULL OR (instagram_url ~* '^https://((www\.)?instagram\.com|instagr\.am)/' AND octet_length(instagram_url) <= 255)),

    CONSTRAINT ck_sports_athlete_public_profile_x_url
        CHECK (x_url IS NULL OR (x_url ~* '^https://(www\.)?(x\.com|twitter\.com)/' AND octet_length(x_url) <= 255)),

    CONSTRAINT ck_sports_athlete_public_profile_linkedin_url
        CHECK (linkedin_url IS NULL OR (linkedin_url ~* '^https://(www\.)?linkedin\.com/' AND octet_length(linkedin_url) <= 255)),

    CONSTRAINT ck_sports_athlete_public_profile_strava_url
        CHECK (strava_url IS NULL OR (strava_url ~* '^https://(www\.)?strava\.com/' AND octet_length(strava_url) <= 255)),

    CONSTRAINT ck_sports_athlete_public_profile_youtube_url
        CHECK (youtube_url IS NULL OR (youtube_url ~* '^https://(www\.)?(youtube\.com|youtu\.be)/' AND octet_length(youtube_url) <= 255)),

    CONSTRAINT ck_sports_athlete_public_profile_lock_version
        CHECK (lock_version >= 0),

    CONSTRAINT ck_sports_athlete_public_profile_timestamp_order
        CHECK (updated_at >= created_at)
);

COMMENT ON TABLE public.sports_athlete_public_profile IS
    'Perfil esportivo público textual e social controlado exclusivamente pelo atleta vinculado (D-76, D-78, SPRT-16, V19).';
