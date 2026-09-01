CREATE TABLE public.sports_athlete_profile_media (
    athlete_id uuid NOT NULL,
    object_key varchar(512) COLLATE "C" NULL,
    content_type varchar(32) COLLATE "C" NULL,
    byte_size integer NULL,
    width integer NULL,
    height integer NULL,
    etag varchar(128) COLLATE "C" NULL,
    media_version bigint NOT NULL DEFAULT 0,
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_athlete_profile_media
        PRIMARY KEY (athlete_id),

    CONSTRAINT fk_sports_athlete_profile_media_athlete
        FOREIGN KEY (athlete_id)
        REFERENCES public.sports_athlete (athlete_id),

    CONSTRAINT ck_sports_athlete_profile_media_reference_tuple
        CHECK (
            (object_key IS NULL
                AND content_type IS NULL
                AND byte_size IS NULL
                AND width IS NULL
                AND height IS NULL
                AND etag IS NULL
                AND media_version = 0)
            OR (object_key IS NOT NULL
                AND content_type IN ('image/jpeg', 'image/png')
                AND byte_size IS NOT NULL
                AND byte_size > 0
                AND width IS NOT NULL
                AND width > 0
                AND height IS NOT NULL
                AND height > 0
                AND etag IS NOT NULL
                AND btrim(etag) <> ''
                AND media_version > 0)
        ),

    CONSTRAINT ck_sports_athlete_profile_media_object_key
        CHECK (object_key IS NULL OR (btrim(object_key) <> '' AND octet_length(object_key) <= 512)),

    CONSTRAINT ck_sports_athlete_profile_media_content_type
        CHECK (content_type IS NULL OR content_type IN ('image/jpeg', 'image/png')),

    CONSTRAINT ck_sports_athlete_profile_media_byte_size
        CHECK (byte_size IS NULL OR byte_size BETWEEN 1 AND 5242880),

    CONSTRAINT ck_sports_athlete_profile_media_dimensions
        CHECK (
            (width IS NULL AND height IS NULL)
            OR (width BETWEEN 1 AND 4000000
                AND height BETWEEN 1 AND 4000000
                AND (width::bigint * height::bigint) <= 4000000)
        ),

    CONSTRAINT ck_sports_athlete_profile_media_etag
        CHECK (etag IS NULL OR (btrim(etag) <> '' AND octet_length(etag) <= 128)),

    CONSTRAINT ck_sports_athlete_profile_media_version
        CHECK (media_version >= 0),

    CONSTRAINT ck_sports_athlete_profile_media_lock_version
        CHECK (lock_version >= 0),

    CONSTRAINT ck_sports_athlete_profile_media_timestamp_order
        CHECK (updated_at >= created_at)
);

COMMENT ON TABLE public.sports_athlete_profile_media IS
    'Private reference for processed sports-profile media; the key is never a public representation (D-76, SPRT-16, V20).';
