CREATE TABLE public.password_reset_event_inbox (
    inbox_id uuid NOT NULL DEFAULT gen_random_uuid(),
    source_event_id varchar(255) COLLATE "C" NOT NULL,
    subject varchar(255) COLLATE "C" NOT NULL,
    realm_id varchar(255) COLLATE "C" NOT NULL,
    event_type varchar(100) COLLATE "C" NULL,
    status varchar(32) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    attempt_count integer NOT NULL DEFAULT 0,
    claimed_at timestamptz(6) NULL,
    claim_instance_id varchar(255) COLLATE "C" NULL,
    delivered_at timestamptz(6) NULL,
    last_error varchar(1024) COLLATE "C" NULL,

    CONSTRAINT pk_password_reset_event_inbox
        PRIMARY KEY (inbox_id),

    CONSTRAINT uq_password_reset_event_inbox_source_event
        UNIQUE (source_event_id),

    CONSTRAINT ck_password_reset_event_inbox_source_event_not_empty
        CHECK (source_event_id <> ''),

    CONSTRAINT ck_password_reset_event_inbox_source_event_octets
        CHECK (octet_length(source_event_id) <= 255),

    CONSTRAINT ck_password_reset_event_inbox_subject_not_empty
        CHECK (subject <> ''),

    CONSTRAINT ck_password_reset_event_inbox_subject_octets
        CHECK (octet_length(subject) <= 255),

    CONSTRAINT ck_password_reset_event_inbox_realm_id_not_empty
        CHECK (realm_id <> ''),

    CONSTRAINT ck_password_reset_event_inbox_realm_id_octets
        CHECK (octet_length(realm_id) <= 255),

    CONSTRAINT ck_password_reset_event_inbox_status_not_empty
        CHECK (status <> ''),

    CONSTRAINT ck_password_reset_event_inbox_status_octets
        CHECK (octet_length(status) <= 32),

    CONSTRAINT ck_password_reset_event_inbox_status_values
        CHECK (status IN ('PENDING', 'CLAIMED', 'DELIVERED', 'FAILED', 'PROCESSED')),

    CONSTRAINT ck_password_reset_event_inbox_timestamp_order
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_password_reset_event_inbox_status
    ON public.password_reset_event_inbox (status);
