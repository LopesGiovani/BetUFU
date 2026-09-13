-- V31 preserves every published outcome while keeping the legacy HOME column compatible.
CREATE TABLE public.odds_publication_selection (
    publication_id uuid NOT NULL,
    selection_no smallint NOT NULL,
    selection_key varchar(16) COLLATE "C" NOT NULL,
    published_odds numeric(20, 10) NOT NULL,
    CONSTRAINT pk_odds_publication_selection PRIMARY KEY (publication_id, selection_no),
    CONSTRAINT fk_odds_publication_selection_publication FOREIGN KEY (publication_id)
        REFERENCES public.odds_publication (publication_id),
    CONSTRAINT ck_odds_publication_selection_no CHECK (selection_no > 0),
    CONSTRAINT ck_odds_publication_selection_key CHECK (selection_key IN ('HOME', 'DRAW', 'AWAY')),
    CONSTRAINT ck_odds_publication_selection_odds CHECK (published_odds > 0)
);

DO $$
DECLARE
    publication record;
    odds_vector jsonb;
    vector_size integer;
    position integer;
    odds_value numeric;
BEGIN
    FOR publication IN
        SELECT p.publication_id, c.input_snapshot
          FROM public.odds_publication p
          JOIN public.odds_calculation c ON c.calculation_id = p.calculation_id
    LOOP
        odds_vector := publication.input_snapshot -> 'publishedOdds';
        IF jsonb_typeof(odds_vector) IS DISTINCT FROM 'array' THEN
            RAISE EXCEPTION 'legacy odds publication cannot be migrated safely';
        END IF;

        vector_size := jsonb_array_length(odds_vector);
        IF vector_size NOT IN (2, 3) THEN
            RAISE EXCEPTION 'legacy odds publication cannot be migrated safely';
        END IF;

        FOR position IN 0..vector_size - 1 LOOP
            IF jsonb_typeof(odds_vector -> position) IS DISTINCT FROM 'number' THEN
                RAISE EXCEPTION 'legacy odds publication cannot be migrated safely';
            END IF;
            odds_value := (odds_vector ->> position)::numeric;
            IF odds_value <= 0 THEN
                RAISE EXCEPTION 'legacy odds publication cannot be migrated safely';
            END IF;
        END LOOP;
    END LOOP;
END $$;

INSERT INTO public.odds_publication_selection
    (publication_id, selection_no, selection_key, published_odds)
SELECT p.publication_id, 1, 'HOME', p.published_odds
  FROM public.odds_publication p
UNION ALL
SELECT p.publication_id,
       2,
       CASE jsonb_array_length(c.input_snapshot -> 'publishedOdds')
           WHEN 2 THEN 'AWAY'
           ELSE 'DRAW'
       END,
       ((c.input_snapshot -> 'publishedOdds') ->> 1)::numeric
  FROM public.odds_publication p
  JOIN public.odds_calculation c ON c.calculation_id = p.calculation_id
UNION ALL
SELECT p.publication_id,
       3,
       'AWAY',
       ((c.input_snapshot -> 'publishedOdds') ->> 2)::numeric
  FROM public.odds_publication p
  JOIN public.odds_calculation c ON c.calculation_id = p.calculation_id
 WHERE jsonb_array_length(c.input_snapshot -> 'publishedOdds') = 3;

CREATE OR REPLACE FUNCTION public.enforce_odds_publication_selection_vector()
RETURNS TRIGGER AS $$
DECLARE
    checked_publication_id uuid;
    legacy_home numeric;
    odds_vector jsonb;
    vector_size integer;
    actual_size integer;
    position integer;
    odds_value numeric;
BEGIN
    checked_publication_id := CASE
        WHEN TG_TABLE_NAME = 'odds_publication' THEN NEW.publication_id
        WHEN TG_OP = 'DELETE' THEN OLD.publication_id
        ELSE NEW.publication_id
    END;

    SELECT p.published_odds, c.input_snapshot -> 'publishedOdds'
      INTO legacy_home, odds_vector
      FROM public.odds_publication p
      JOIN public.odds_calculation c ON c.calculation_id = p.calculation_id
     WHERE p.publication_id = checked_publication_id;

    IF NOT FOUND OR jsonb_typeof(odds_vector) IS DISTINCT FROM 'array' THEN
        RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
    END IF;

    vector_size := jsonb_array_length(odds_vector);
    IF vector_size NOT IN (2, 3) THEN
        RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
    END IF;

    FOR position IN 0..vector_size - 1 LOOP
        IF jsonb_typeof(odds_vector -> position) IS DISTINCT FROM 'number' THEN
            RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
        END IF;
        odds_value := (odds_vector ->> position)::numeric;
        IF odds_value <= 0 THEN
            RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
        END IF;
    END LOOP;

    SELECT count(*) INTO actual_size
      FROM public.odds_publication_selection
     WHERE publication_id = checked_publication_id;
    IF actual_size <> vector_size THEN
        RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM public.odds_publication_selection
         WHERE publication_id = checked_publication_id
           AND selection_no = 1
           AND selection_key = 'HOME'
           AND published_odds = legacy_home
    ) THEN
        RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
    END IF;

    IF vector_size = 2 THEN
        IF NOT EXISTS (
            SELECT 1 FROM public.odds_publication_selection
             WHERE publication_id = checked_publication_id
               AND selection_no = 2
               AND selection_key = 'AWAY'
               AND published_odds = ((odds_vector ->> 1)::numeric)
        ) THEN
            RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
        END IF;
    ELSE
        IF NOT EXISTS (
            SELECT 1 FROM public.odds_publication_selection
             WHERE publication_id = checked_publication_id
               AND selection_no = 2
               AND selection_key = 'DRAW'
               AND published_odds = ((odds_vector ->> 1)::numeric)
        ) OR NOT EXISTS (
            SELECT 1 FROM public.odds_publication_selection
             WHERE publication_id = checked_publication_id
               AND selection_no = 3
               AND selection_key = 'AWAY'
               AND published_odds = ((odds_vector ->> 2)::numeric)
        ) THEN
            RAISE EXCEPTION 'odds publication selection vector is incomplete or invalid';
        END IF;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_enforce_odds_publication_selection_parent
AFTER INSERT ON public.odds_publication
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.enforce_odds_publication_selection_vector();

CREATE CONSTRAINT TRIGGER trg_enforce_odds_publication_selection_child
AFTER INSERT OR DELETE ON public.odds_publication_selection
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION public.enforce_odds_publication_selection_vector();

CREATE TRIGGER trg_prevent_odds_publication_selection_modification
BEFORE UPDATE OR DELETE ON public.odds_publication_selection
FOR EACH ROW EXECUTE FUNCTION public.prevent_odds_history_modification();

DO $$
DECLARE
    runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured BETUFU runtime role does not exist';
    END IF;
    EXECUTE format(
        'REVOKE ALL PRIVILEGES ON TABLE public.odds_publication_selection FROM %I',
        runtime_role
    );
    EXECUTE format(
        'GRANT SELECT, INSERT ON TABLE public.odds_publication_selection TO %I',
        runtime_role
    );
END $$;
