ALTER TABLE public.sports_athlete_link_proposal
    ADD COLUMN expires_at timestamptz(6) NOT NULL
        DEFAULT (CURRENT_TIMESTAMP(6) + INTERVAL '30 days'),
    ADD COLUMN decided_by_identity_id uuid NULL,
    ADD COLUMN decided_at timestamptz(6) NULL;

UPDATE public.sports_athlete_link_proposal
SET decided_by_identity_id = identity_id,
    decided_at = updated_at
WHERE status IN ('ACEITA', 'REJEITADA', 'CONTESTADA');

UPDATE public.sports_athlete_link_proposal
SET decided_at = updated_at
WHERE status = 'EXPIRADA';

ALTER TABLE public.sports_athlete_link_proposal
    ADD CONSTRAINT fk_sports_athlete_link_proposal_decided_by
        FOREIGN KEY (decided_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    ADD CONSTRAINT ck_sports_athlete_link_proposal_expiry
        CHECK (expires_at >= created_at),
    ADD CONSTRAINT ck_sports_athlete_link_proposal_decision_metadata
        CHECK (
            (status = 'PENDENTE' AND decided_at IS NULL AND decided_by_identity_id IS NULL)
            OR (status = 'EXPIRADA' AND decided_at IS NOT NULL AND decided_by_identity_id IS NULL)
            OR (status IN ('ACEITA', 'REJEITADA', 'CONTESTADA')
                AND decided_at IS NOT NULL
                AND decided_by_identity_id IS NOT NULL)
        );

CREATE INDEX idx_sports_athlete_link_proposal_pending_expiry
    ON public.sports_athlete_link_proposal (expires_at)
    WHERE status = 'PENDENTE';

ALTER TABLE public.audit_event
    DROP CONSTRAINT ck_audit_event_action_values;

ALTER TABLE public.audit_event
    ADD CONSTRAINT ck_audit_event_action_values
        CHECK (action IN (
            'PAPEL_CONCEDIDO',
            'PAPEL_REVOGADO',
            'AUTOCONCESSAO_PAPEL',
            'ULTIMO_ADMINISTRADOR_GERAL',
            'REVOGACAO_ULTIMO_ADMINISTRADOR',
            'REGISTRO_MATRICULA',
            'CORRECAO_MATRICULA',
            'LOGIN_EFETUADO',
            'CONSULTA_OPERACIONAL',
            'SEMENTE_ADMINISTRADOR_INICIAL',
            'REPROCESSAMENTO_RESET_SENHA',
            'VINCULO_ATLETA_DECIDIDO',
            'REVISAO_VINCULO_ATLETA_CRIADA'
        ));
