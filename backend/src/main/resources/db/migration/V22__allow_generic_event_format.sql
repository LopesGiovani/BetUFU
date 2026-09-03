ALTER TABLE public.sports_event
    DROP CONSTRAINT ck_sports_event_format;

ALTER TABLE public.sports_event
    ADD CONSTRAINT ck_sports_event_format
        CHECK (event_format IN ('HEAD_TO_HEAD', 'GENERIC'));

ALTER TABLE public.sports_event_publication
    DROP CONSTRAINT ck_sports_event_publication_format;

ALTER TABLE public.sports_event_publication
    ADD CONSTRAINT ck_sports_event_publication_format
        CHECK (event_format IN ('HEAD_TO_HEAD', 'GENERIC'));

CREATE OR REPLACE FUNCTION public.enforce_sports_event_publication_slot_count()
RETURNS TRIGGER AS $$
DECLARE
    persisted_slot_count integer;
BEGIN
    SELECT count(*)
    INTO persisted_slot_count
    FROM public.sports_event_publication_slot
    WHERE publication_id = NEW.publication_id;

    IF persisted_slot_count <> 2 THEN
        RAISE EXCEPTION
            'event publication snapshots must contain exactly two immutable slots';
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
