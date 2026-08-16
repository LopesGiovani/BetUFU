CREATE TABLE public.platform_guard (
    guard_id uuid NOT NULL DEFAULT gen_random_uuid(),
    guard_key varchar(128) COLLATE "C" NOT NULL,
    description varchar(512) COLLATE "C" NOT NULL,
    applied_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_platform_guard
        PRIMARY KEY (guard_id),

    CONSTRAINT uq_platform_guard_key
        UNIQUE (guard_key),

    CONSTRAINT ck_platform_guard_key_not_empty
        CHECK (guard_key <> ''),

    CONSTRAINT ck_platform_guard_key_octets
        CHECK (octet_length(guard_key) <= 128),

    CONSTRAINT ck_platform_guard_description_not_empty
        CHECK (description <> ''),

    CONSTRAINT ck_platform_guard_description_octets
        CHECK (octet_length(description) <= 512)
);

INSERT INTO public.platform_guard (guard_key, description)
VALUES ('PLATFORM_SCHEMA_AUTHORITATIVE', 'PostgreSQL 18 authoritative schema guarded by Flyway migrations and validated DDL');
