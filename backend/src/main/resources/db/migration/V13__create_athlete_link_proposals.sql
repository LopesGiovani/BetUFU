CREATE TABLE public.sports_athlete_link_proposal (
    proposal_id uuid NOT NULL DEFAULT gen_random_uuid(),
    athlete_id uuid NOT NULL,
    identity_id uuid NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'PENDENTE',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_athlete_link_proposal PRIMARY KEY (proposal_id),
    CONSTRAINT fk_sports_athlete_link_proposal_athlete
        FOREIGN KEY (athlete_id) REFERENCES public.sports_athlete (athlete_id),
    CONSTRAINT fk_sports_athlete_link_proposal_identity
        FOREIGN KEY (identity_id) REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_sports_athlete_link_proposal_status
        CHECK (status IN ('PENDENTE', 'ACEITA', 'REJEITADA', 'EXPIRADA', 'CONTESTADA')),
    CONSTRAINT ck_sports_athlete_link_proposal_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_athlete_link_proposal_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_athlete_link_proposal_accepted_athlete
    ON public.sports_athlete_link_proposal (athlete_id)
    WHERE status = 'ACEITA';

CREATE UNIQUE INDEX uq_sports_athlete_link_proposal_accepted_identity
    ON public.sports_athlete_link_proposal (identity_id)
    WHERE status = 'ACEITA';

CREATE UNIQUE INDEX uq_sports_athlete_link_proposal_pending_pair
    ON public.sports_athlete_link_proposal (athlete_id, identity_id)
    WHERE status = 'PENDENTE';

CREATE INDEX idx_sports_athlete_link_proposal_identity_status
    ON public.sports_athlete_link_proposal (identity_id, status);

CREATE TABLE public.sports_athlete_link_review (
    review_id uuid NOT NULL DEFAULT gen_random_uuid(),
    proposal_id uuid NULL,
    athlete_id uuid NULL,
    identity_id uuid NULL,
    reason_code varchar(32) COLLATE "C" NOT NULL,
    status varchar(16) COLLATE "C" NOT NULL DEFAULT 'PENDENTE',
    lock_version bigint NOT NULL DEFAULT 0,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_sports_athlete_link_review PRIMARY KEY (review_id),
    CONSTRAINT fk_sports_athlete_link_review_proposal
        FOREIGN KEY (proposal_id) REFERENCES public.sports_athlete_link_proposal (proposal_id),
    CONSTRAINT fk_sports_athlete_link_review_athlete
        FOREIGN KEY (athlete_id) REFERENCES public.sports_athlete (athlete_id),
    CONSTRAINT fk_sports_athlete_link_review_identity
        FOREIGN KEY (identity_id) REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_sports_athlete_link_review_reason
        CHECK (reason_code IN ('DIVERGENT_DATA', 'MULTIPLE_CANDIDATES', 'ALREADY_LINKED', 'CONTESTED')),
    CONSTRAINT ck_sports_athlete_link_review_status
        CHECK (status IN ('PENDENTE', 'RESOLVIDA', 'IGNORADA')),
    CONSTRAINT ck_sports_athlete_link_review_lock_version CHECK (lock_version >= 0),
    CONSTRAINT ck_sports_athlete_link_review_timestamp_order CHECK (updated_at >= created_at)
);

CREATE UNIQUE INDEX uq_sports_athlete_link_review_proposal
    ON public.sports_athlete_link_review (proposal_id)
    WHERE proposal_id IS NOT NULL;

CREATE UNIQUE INDEX uq_sports_athlete_link_review_athlete_reason
    ON public.sports_athlete_link_review (athlete_id, reason_code)
    WHERE athlete_id IS NOT NULL AND identity_id IS NULL;

CREATE UNIQUE INDEX uq_sports_athlete_link_review_identity_reason
    ON public.sports_athlete_link_review (identity_id, reason_code)
    WHERE identity_id IS NOT NULL AND athlete_id IS NULL;

CREATE UNIQUE INDEX uq_sports_athlete_link_review_pair_reason
    ON public.sports_athlete_link_review (athlete_id, identity_id, reason_code)
    WHERE athlete_id IS NOT NULL AND identity_id IS NOT NULL;

COMMENT ON TABLE public.sports_athlete_link_proposal IS
    'Propostas de vínculo explícito entre atleta e conta com consentimento owner-only (D-71, SPRT-06, SPRT-08, V13).';

COMMENT ON TABLE public.sports_athlete_link_review IS
    'Fila operacional O8 de revisão de exceções e contestações de vínculo (D-71, SPRT-07, SPRT-08, V13).';
