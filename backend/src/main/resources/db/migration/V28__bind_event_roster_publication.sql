CREATE TABLE public.odds_market_roster_binding (
    market_version_id uuid NOT NULL,
    event_id uuid NOT NULL,
    slot_no smallint NOT NULL,
    team_id uuid NOT NULL,
    competition_id uuid NOT NULL,
    edition_id uuid NOT NULL,
    roster_publication_id uuid NOT NULL,
    roster_version_no bigint NOT NULL,
    roster_known_at timestamptz(6) NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_odds_market_roster_binding PRIMARY KEY (market_version_id, slot_no),
    CONSTRAINT uq_odds_market_roster_binding_team UNIQUE (market_version_id, team_id),
    CONSTRAINT fk_odds_market_roster_binding_market_version
        FOREIGN KEY (market_version_id) REFERENCES public.odds_market_version (market_version_id),
    CONSTRAINT fk_odds_market_roster_binding_event
        FOREIGN KEY (event_id) REFERENCES public.sports_event (event_id),
    CONSTRAINT fk_odds_market_roster_binding_slot
        FOREIGN KEY (event_id, slot_no) REFERENCES public.sports_event_slot (event_id, slot_no),
    CONSTRAINT fk_odds_market_roster_binding_team
        FOREIGN KEY (team_id) REFERENCES public.sports_team (team_id),
    CONSTRAINT fk_odds_market_roster_binding_publication
        FOREIGN KEY (roster_publication_id) REFERENCES public.sports_roster_publication (publication_id),
    CONSTRAINT ck_odds_market_roster_binding_slot CHECK (slot_no BETWEEN 1 AND 2),
    CONSTRAINT ck_odds_market_roster_binding_version CHECK (roster_version_no > 0),
    CONSTRAINT ck_odds_market_roster_binding_known_at CHECK (roster_known_at IS NOT NULL)
);

CREATE INDEX idx_odds_market_roster_binding_event
    ON public.odds_market_roster_binding (event_id, slot_no);

CREATE OR REPLACE FUNCTION public.enforce_odds_market_roster_binding_coherence()
RETURNS TRIGGER AS $$
DECLARE
    event_competition uuid;
    event_edition uuid;
    slot_team uuid;
    publication_team uuid;
    publication_competition uuid;
    publication_edition uuid;
    publication_version bigint;
BEGIN
    SELECT competition_id, edition_id INTO event_competition, event_edition
      FROM public.sports_event WHERE event_id = NEW.event_id;
    SELECT team_id INTO slot_team
      FROM public.sports_event_slot
      WHERE event_id = NEW.event_id AND slot_no = NEW.slot_no;
    SELECT team_id, competition_id, edition_id, version_no
      INTO publication_team, publication_competition, publication_edition, publication_version
      FROM public.sports_roster_publication
      WHERE publication_id = NEW.roster_publication_id;
    IF event_competition IS NULL OR event_edition IS NULL OR slot_team IS NULL
       OR publication_team IS NULL THEN
        RAISE EXCEPTION 'roster binding references an unknown event, slot or publication';
    END IF;
    IF slot_team <> NEW.team_id OR publication_team <> NEW.team_id
       OR event_competition <> NEW.competition_id OR event_edition <> NEW.edition_id
       OR publication_competition <> NEW.competition_id
       OR publication_edition <> NEW.edition_id
       OR publication_version <> NEW.roster_version_no THEN
        RAISE EXCEPTION 'roster binding scope or version mismatch';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM public.sports_roster_publication p
        JOIN public.sports_roster r ON r.roster_id = p.roster_id
        WHERE p.publication_id = NEW.roster_publication_id
          AND r.status = 'PUBLICADO') THEN
        RAISE EXCEPTION 'roster binding requires a published roster';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_enforce_odds_market_roster_binding_coherence
BEFORE INSERT ON public.odds_market_roster_binding
FOR EACH ROW EXECUTE FUNCTION public.enforce_odds_market_roster_binding_coherence();

CREATE OR REPLACE FUNCTION public.prevent_odds_market_roster_binding_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'roster market binding is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_odds_market_roster_binding_modification
BEFORE UPDATE OR DELETE ON public.odds_market_roster_binding
FOR EACH ROW EXECUTE FUNCTION public.prevent_odds_market_roster_binding_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NOT NULL AND btrim(runtime_role) <> '' THEN
        EXECUTE format('GRANT SELECT, INSERT ON TABLE public.odds_market_roster_binding TO %I', runtime_role);
    END IF;
END $$;
