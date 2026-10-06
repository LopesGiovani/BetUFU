-- Stable lock rows serialize emergency commands with transaction-bound consumers.
CREATE TABLE public.emergency_pause_target (
    target_kind varchar(16) COLLATE "C" NOT NULL,
    target_id uuid NOT NULL,
    paused boolean NOT NULL DEFAULT false,
    current_pause_id uuid,
    CONSTRAINT pk_emergency_pause_target PRIMARY KEY (target_kind, target_id),
    CONSTRAINT ck_emergency_pause_target_kind CHECK (target_kind IN ('ACCOUNT','EVENT','MARKET','CHANNEL')),
    CONSTRAINT ck_emergency_pause_target_current CHECK (paused = (current_pause_id IS NOT NULL))
);

CREATE TABLE public.emergency_pause_fact (
    pause_fact_id uuid NOT NULL DEFAULT gen_random_uuid(),
    change_no bigint GENERATED ALWAYS AS IDENTITY,
    target_kind varchar(16) COLLATE "C" NOT NULL,
    target_id uuid NOT NULL,
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity(identity_id),
    action varchar(8) COLLATE "C" NOT NULL,
    reason varchar(280) NOT NULL,
    client_command_id uuid NOT NULL,
    resumed_pause_id uuid REFERENCES public.emergency_pause_fact(pause_fact_id),
    recorded_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_emergency_pause_fact PRIMARY KEY (pause_fact_id),
    CONSTRAINT uq_emergency_pause_fact_change UNIQUE (change_no),
    CONSTRAINT uq_emergency_pause_fact_command UNIQUE (actor_identity_id,client_command_id),
    CONSTRAINT ck_emergency_pause_fact_kind CHECK (target_kind IN ('ACCOUNT','EVENT','MARKET','CHANNEL')),
    CONSTRAINT ck_emergency_pause_fact_action CHECK (action IN ('PAUSE','RESUME')),
    CONSTRAINT ck_emergency_pause_fact_reason CHECK (btrim(reason) <> ''),
    CONSTRAINT ck_emergency_pause_fact_resume CHECK ((action='RESUME') = (resumed_pause_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_emergency_pause_fact_resume
    ON public.emergency_pause_fact(resumed_pause_id) WHERE resumed_pause_id IS NOT NULL;
CREATE INDEX idx_emergency_pause_fact_target
    ON public.emergency_pause_fact(target_kind,target_id,change_no DESC);

ALTER TABLE public.emergency_pause_target ADD CONSTRAINT fk_emergency_pause_target_current
    FOREIGN KEY (current_pause_id) REFERENCES public.emergency_pause_fact(pause_fact_id);

CREATE FUNCTION public.prevent_emergency_pause_fact_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'emergency pause history is immutable: UPDATE and DELETE are prohibited';
END $$;
CREATE TRIGGER trg_emergency_pause_fact_immutable
    BEFORE UPDATE OR DELETE ON public.emergency_pause_fact
    FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_emergency_pause_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role',true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role)='' OR NOT EXISTS(SELECT FROM pg_roles WHERE rolname=runtime_role) THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify an existing application runtime role';
    END IF;
    REVOKE ALL ON public.emergency_pause_target,public.emergency_pause_fact FROM PUBLIC;
    EXECUTE format('GRANT SELECT,INSERT,UPDATE ON public.emergency_pause_target TO %I',runtime_role);
    EXECUTE format('GRANT SELECT,INSERT ON public.emergency_pause_fact TO %I',runtime_role);
    EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE public.emergency_pause_fact_change_no_seq TO %I',runtime_role);
END $$;
