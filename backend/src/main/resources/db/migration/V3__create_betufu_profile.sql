CREATE TABLE public.betufu_profile (
    profile_id uuid NOT NULL DEFAULT gen_random_uuid(),
    identity_id uuid NOT NULL,
    nickname varchar COLLATE "C" NOT NULL,
    favorite_team_id uuid NULL,
    nickname_updated_at timestamptz(6) NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_betufu_profile
        PRIMARY KEY (profile_id),

    CONSTRAINT uq_betufu_profile_identity_id
        UNIQUE (identity_id),

    CONSTRAINT fk_betufu_profile_identity
        FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),

    CONSTRAINT ck_betufu_profile_nickname_not_empty
        CHECK (nickname <> ''),

    CONSTRAINT ck_betufu_profile_nickname_pattern
        CHECK (nickname ~ '^[A-Za-z0-9_-]{3,20}$'),

    CONSTRAINT ck_betufu_profile_nickname_octets
        CHECK (octet_length(nickname) <= 20),

    CONSTRAINT ck_betufu_profile_timestamp_order
        CHECK (updated_at >= created_at)
);
