CREATE TABLE public.local_identity (
    identity_id uuid NOT NULL DEFAULT gen_random_uuid(),
    issuer varchar(2048) COLLATE "C" NOT NULL,
    subject varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_local_identity
        PRIMARY KEY (identity_id),

    CONSTRAINT uq_local_identity_issuer_subject
        UNIQUE (issuer, subject),

    CONSTRAINT ck_local_identity_issuer_not_empty
        CHECK (issuer <> ''),

    CONSTRAINT ck_local_identity_subject_not_empty
        CHECK (subject <> ''),

    CONSTRAINT ck_local_identity_issuer_octets
        CHECK (octet_length(issuer) <= 2048),

    CONSTRAINT ck_local_identity_subject_octets
        CHECK (octet_length(subject) <= 255),

    CONSTRAINT ck_local_identity_timestamp_order
        CHECK (updated_at >= created_at)
);
