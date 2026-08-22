CREATE TABLE public.ufu_enrollment (
    enrollment_id uuid NOT NULL DEFAULT gen_random_uuid(),
    identity_id uuid NOT NULL,
    matricula_hmac bytea NOT NULL,
    hmac_version smallint NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_ufu_enrollment
        PRIMARY KEY (enrollment_id),

    CONSTRAINT fk_ufu_enrollment_identity
        FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),

    CONSTRAINT ck_ufu_enrollment_hmac_octets
        CHECK (octet_length(matricula_hmac) = 32),

    CONSTRAINT ck_ufu_enrollment_hmac_version_range
        CHECK (hmac_version >= 1),

    CONSTRAINT ck_ufu_enrollment_status_not_empty
        CHECK (status <> ''),

    CONSTRAINT ck_ufu_enrollment_status_octets
        CHECK (octet_length(status) <= 16),

    CONSTRAINT ck_ufu_enrollment_status_values
        CHECK (status IN ('ATIVO', 'LIBERADO')),

    CONSTRAINT ck_ufu_enrollment_timestamp_order
        CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_ufu_enrollment_active_hmac
    ON public.ufu_enrollment (matricula_hmac)
    WHERE status = 'ATIVO';

CREATE UNIQUE INDEX uq_ufu_enrollment_active_identity
    ON public.ufu_enrollment (identity_id)
    WHERE status = 'ATIVO';
