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
            'REPROCESSAMENTO_RESET_SENHA'
        ));
