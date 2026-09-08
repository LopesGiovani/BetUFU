-- V26 extends the closed audit vocabulary without editing V1-V25.
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
            'REVISAO_VINCULO_ATLETA_CRIADA',
            'ODDS_MARKET_VIEWED',
            'ODDS_MARKET_CREATED',
            'ODDS_MARKET_LIFECYCLE_CHANGED',
            'ODDS_CALCULATION_RECORDED',
            'ODDS_PUBLICATION_RECORDED',
            'ODDS_OVERRIDE_PROPOSED',
            'ODDS_OVERRIDE_APPROVED',
            'ODDS_OVERRIDE_REJECTED'
        ));
