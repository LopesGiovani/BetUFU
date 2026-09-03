CREATE TABLE public.sports_competition_sport (
    competition_id uuid NOT NULL,
    sport_id uuid NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_competition_sport
        PRIMARY KEY (competition_id, sport_id),

    CONSTRAINT fk_sports_competition_sport_competition
        FOREIGN KEY (competition_id)
        REFERENCES public.sports_competition (competition_id),

    CONSTRAINT fk_sports_competition_sport_sport
        FOREIGN KEY (sport_id)
        REFERENCES public.sports_sport (sport_id)
);

CREATE INDEX idx_sports_competition_sport_sport
    ON public.sports_competition_sport (sport_id, competition_id);

INSERT INTO public.sports_competition_sport (competition_id, sport_id)
SELECT competition_id, sport_id
FROM public.sports_competition
WHERE sport_id IS NOT NULL
ON CONFLICT (competition_id, sport_id) DO NOTHING;

COMMENT ON TABLE public.sports_competition_sport IS
    'Authoritative competition-to-sport association; sports_competition.sport_id remains legacy compatibility only.';
