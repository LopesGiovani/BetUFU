-- Retention removes serving access only; historical facts and sanctions remain intact.
CREATE TABLE public.privacy_suppression_fact (
    suppression_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    data_class varchar(32) COLLATE "C" NOT NULL,
    record_id uuid NOT NULL,
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity(identity_id),
    inventory_digest varchar(64) COLLATE "C" NOT NULL CHECK (inventory_digest ~ '^[0-9a-f]{64}$'),
    policy_reference varchar(128) NOT NULL CHECK (btrim(policy_reference) <> ''),
    recorded_at timestamptz(6) NOT NULL DEFAULT clock_timestamp(),
    change_cursor bigint NOT NULL DEFAULT nextval('public.chat_message_change_cursor_seq'),
    UNIQUE(data_class, record_id),
    CHECK (data_class IN ('COMMON_MESSAGES', 'MODERATION_EVIDENCE'))
);
CREATE TABLE public.chat_privacy_suppression_projection (
 data_class varchar(32) COLLATE "C" NOT NULL CHECK(data_class IN ('COMMON_MESSAGES','MODERATION_EVIDENCE')),
 message_id uuid NOT NULL REFERENCES public.chat_message(message_id),
 change_cursor bigint NOT NULL CHECK(change_cursor>0),
 PRIMARY KEY(data_class,message_id)
);
CREATE INDEX idx_chat_privacy_suppression_cursor ON public.chat_privacy_suppression_projection(change_cursor);
CREATE INDEX idx_privacy_suppression_cursor ON public.privacy_suppression_fact(change_cursor);

CREATE TABLE public.privacy_retention_hold_fact (
    hold_id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    data_class varchar(32) COLLATE "C" NOT NULL,
    record_id uuid NOT NULL,
    action varchar(8) COLLATE "C" NOT NULL CHECK (action IN ('HOLD', 'RELEASE')),
    releases_hold_id uuid REFERENCES public.privacy_retention_hold_fact(hold_id),
    actor_identity_id uuid NOT NULL REFERENCES public.local_identity(identity_id),
    reason_reference varchar(128) NOT NULL CHECK (btrim(reason_reference) <> ''),
    recorded_at timestamptz(6) NOT NULL DEFAULT clock_timestamp(),
    CHECK ((action = 'HOLD' AND releases_hold_id IS NULL) OR (action = 'RELEASE' AND releases_hold_id IS NOT NULL)),
    UNIQUE(releases_hold_id),
    CHECK (data_class IN ('COMMON_MESSAGES', 'MODERATION_EVIDENCE'))
);

CREATE TABLE public.privacy_restricted_export_payload (
    export_id uuid PRIMARY KEY REFERENCES public.privacy_rights_export_fact(export_id),
    artifact jsonb NOT NULL CHECK (jsonb_typeof(artifact) = 'object'),
    complete boolean NOT NULL DEFAULT false
);

CREATE TRIGGER trg_privacy_suppression_immutable BEFORE UPDATE OR DELETE ON public.privacy_suppression_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();
CREATE TRIGGER trg_privacy_retention_hold_immutable BEFORE UPDATE OR DELETE ON public.privacy_retention_hold_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();
CREATE TRIGGER trg_privacy_export_payload_immutable BEFORE UPDATE OR DELETE ON public.privacy_restricted_export_payload
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_privacy_rights_fact_modification();

CREATE FUNCTION public.validate_privacy_hold_release() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.action = 'RELEASE' AND NOT EXISTS (
        SELECT 1 FROM public.privacy_retention_hold_fact h WHERE h.hold_id=NEW.releases_hold_id
        AND h.action='HOLD' AND h.record_id=NEW.record_id AND h.data_class=NEW.data_class
    ) THEN RAISE EXCEPTION 'release must identify the same historical hold'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_privacy_hold_release BEFORE INSERT ON public.privacy_retention_hold_fact
FOR EACH ROW EXECUTE FUNCTION public.validate_privacy_hold_release();

CREATE FUNCTION public.lock_privacy_retention_inputs() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 PERFORM pg_advisory_xact_lock_shared(724627);
 RETURN NULL;
END $$;
CREATE TRIGGER trg_privacy_retention_hold_fact_retention_lock BEFORE INSERT ON public.privacy_retention_hold_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_report_retention_lock BEFORE INSERT ON public.chat_report
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_moderation_case_retention_lock BEFORE INSERT OR UPDATE ON public.chat_moderation_case
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_moderation_review_fact_retention_lock BEFORE INSERT ON public.chat_moderation_review_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_moderation_decision_fact_retention_lock BEFORE INSERT ON public.chat_moderation_decision_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_moderation_sanction_fact_retention_lock BEFORE INSERT ON public.chat_moderation_sanction_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_message_removal_fact_retention_lock BEFORE INSERT ON public.chat_message_removal_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();

CREATE TRIGGER trg_chat_message_retention_lock BEFORE INSERT ON public.chat_message
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();
CREATE TRIGGER trg_chat_case_report_retention_lock BEFORE INSERT ON public.chat_moderation_case_report_fact
FOR EACH STATEMENT EXECUTE FUNCTION public.lock_privacy_retention_inputs();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role)='' OR NOT EXISTS(SELECT FROM pg_roles WHERE rolname=runtime_role)
        THEN RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role'; END IF;
    REVOKE ALL ON public.privacy_suppression_fact, public.privacy_retention_hold_fact, public.privacy_restricted_export_payload FROM PUBLIC;
    EXECUTE format('REVOKE ALL ON public.privacy_suppression_fact, public.privacy_retention_hold_fact, public.privacy_restricted_export_payload FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT ON public.privacy_suppression_fact, public.privacy_retention_hold_fact TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON public.privacy_restricted_export_payload TO %I', runtime_role);
    REVOKE ALL ON public.chat_privacy_suppression_projection FROM PUBLIC;
    EXECUTE format('REVOKE ALL ON public.chat_privacy_suppression_projection FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT ON public.chat_privacy_suppression_projection TO %I', runtime_role);
    -- Cluster roles must be provisioned by a DBA; Flyway remains NOCREATEROLE.
    IF EXISTS(SELECT FROM pg_roles WHERE rolname='betufu_privacy_retention') THEN
        GRANT USAGE ON SCHEMA public TO betufu_privacy_retention;
        GRANT SELECT, INSERT ON public.privacy_suppression_fact, public.privacy_retention_hold_fact TO betufu_privacy_retention;
        GRANT USAGE, SELECT ON public.chat_message_change_cursor_seq TO betufu_privacy_retention;
        GRANT SELECT, INSERT, UPDATE, DELETE ON public.chat_privacy_suppression_projection TO betufu_privacy_retention;
    END IF;
    -- No membership is granted to runtime; activation is a separate operational action.
END $$;
