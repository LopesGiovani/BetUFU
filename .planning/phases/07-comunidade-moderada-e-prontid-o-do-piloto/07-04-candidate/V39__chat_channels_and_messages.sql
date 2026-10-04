CREATE TABLE public.chat_channel (
    channel_id uuid NOT NULL DEFAULT gen_random_uuid(),
    kind varchar(16) COLLATE "C" NOT NULL,
    event_id uuid,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_channel PRIMARY KEY (channel_id),
    CONSTRAINT fk_chat_channel_event FOREIGN KEY (event_id)
        REFERENCES public.sports_event (event_id),
    CONSTRAINT ck_chat_channel_kind CHECK (
        (kind = 'GLOBAL' AND event_id IS NULL)
        OR (kind = 'EVENT' AND event_id IS NOT NULL))
);

CREATE UNIQUE INDEX uq_chat_channel_global ON public.chat_channel (kind)
    WHERE kind = 'GLOBAL';
CREATE UNIQUE INDEX uq_chat_channel_event ON public.chat_channel (event_id)
    WHERE kind = 'EVENT';

INSERT INTO public.chat_channel (kind) VALUES ('GLOBAL');

CREATE TABLE public.chat_message (
    message_id uuid NOT NULL DEFAULT gen_random_uuid(),
    channel_id uuid NOT NULL,
    author_identity_id uuid NOT NULL,
    body text NOT NULL,
    posted_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    change_cursor bigint GENERATED ALWAYS AS IDENTITY,
    CONSTRAINT pk_chat_message PRIMARY KEY (message_id),
    CONSTRAINT uq_chat_message_cursor UNIQUE (change_cursor),
    CONSTRAINT fk_chat_message_channel FOREIGN KEY (channel_id)
        REFERENCES public.chat_channel (channel_id),
    CONSTRAINT fk_chat_message_author FOREIGN KEY (author_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_chat_message_body CHECK (btrim(body) <> ''),
    CONSTRAINT ck_chat_message_cursor CHECK (change_cursor > 0)
);

CREATE INDEX idx_chat_message_keyset
    ON public.chat_message (channel_id, posted_at DESC, message_id DESC);
CREATE INDEX idx_chat_message_changes
    ON public.chat_message (channel_id, change_cursor);

-- The original is a fact; visibility is a rebuildable read model.
CREATE TABLE public.chat_message_projection (
    message_id uuid NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL DEFAULT 'VISIBLE',
    rebuilt_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_message_projection PRIMARY KEY (message_id),
    CONSTRAINT fk_chat_message_projection_fact FOREIGN KEY (message_id)
        REFERENCES public.chat_message (message_id),
    CONSTRAINT ck_chat_message_projection_state CHECK (state IN ('VISIBLE', 'REMOVED'))
);

CREATE FUNCTION public.prevent_chat_fact_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'chat history is immutable: UPDATE and DELETE are prohibited';
END $$;

CREATE TRIGGER trg_chat_channel_immutable
BEFORE UPDATE OR DELETE ON public.chat_channel
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_fact_modification();
CREATE TRIGGER trg_chat_message_immutable
BEFORE UPDATE OR DELETE ON public.chat_message
FOR EACH STATEMENT EXECUTE FUNCTION public.prevent_chat_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('REVOKE ALL ON TABLE public.chat_channel, public.chat_message FROM PUBLIC');
    EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON TABLE public.chat_channel, public.chat_message FROM %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.chat_channel, public.chat_message TO %I', runtime_role);
    EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE public.chat_message_change_cursor_seq TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.chat_message_projection TO %I', runtime_role);
END $$;
