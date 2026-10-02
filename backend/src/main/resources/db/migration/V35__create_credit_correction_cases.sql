ALTER TABLE public.credit_ledger_entry DROP CONSTRAINT ck_credit_ledger_entry_type;
ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_entry_type
    CHECK (entry_type IN (
        'GRANT_INITIAL', 'DEBIT', 'COMPENSATION', 'PAYOUT', 'REFUND',
        'CORRECTION_CREDIT', 'CORRECTION_RECOVERY'
    ));

ALTER TABLE public.credit_ledger_entry DROP CONSTRAINT ck_credit_ledger_entry_type_direction;
ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_entry_type_direction
    CHECK (
        (entry_type IN ('GRANT_INITIAL', 'COMPENSATION', 'PAYOUT', 'REFUND', 'CORRECTION_CREDIT')
            AND direction = 'CREDIT')
        OR (entry_type IN ('DEBIT', 'CORRECTION_RECOVERY') AND direction = 'DEBIT')
    );

ALTER TABLE public.credit_ledger_entry DROP CONSTRAINT ck_credit_ledger_source_type;
ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_source_type
    CHECK (source_type IN ('SYSTEM', 'PREDICTION', 'COMPENSATION', 'SETTLEMENT', 'CORRECTION'));

ALTER TABLE public.credit_ledger_entry ADD CONSTRAINT ck_credit_ledger_correction_source
    CHECK (
        (entry_type IN ('CORRECTION_CREDIT', 'CORRECTION_RECOVERY') AND source_type = 'CORRECTION')
        OR (entry_type NOT IN ('CORRECTION_CREDIT', 'CORRECTION_RECOVERY') AND source_type <> 'CORRECTION')
    );

ALTER TABLE public.audit_event DROP CONSTRAINT ck_audit_event_action_values;
ALTER TABLE public.audit_event ADD CONSTRAINT ck_audit_event_action_values
    CHECK (action IN (
        'PAPEL_CONCEDIDO', 'PAPEL_REVOGADO', 'AUTOCONCESSAO_PAPEL',
        'ULTIMO_ADMINISTRADOR_GERAL', 'REVOGACAO_ULTIMO_ADMINISTRADOR',
        'REGISTRO_MATRICULA', 'CORRECAO_MATRICULA', 'LOGIN_EFETUADO',
        'CONSULTA_OPERACIONAL', 'SEMENTE_ADMINISTRADOR_INICIAL',
        'REPROCESSAMENTO_RESET_SENHA', 'VINCULO_ATLETA_DECIDIDO',
        'REVISAO_VINCULO_ATLETA_CRIADA', 'ODDS_MARKET_VIEWED',
        'ODDS_MARKET_CREATED', 'ODDS_MARKET_LIFECYCLE_CHANGED',
        'ODDS_CALCULATION_RECORDED', 'ODDS_PUBLICATION_RECORDED',
        'ODDS_OVERRIDE_PROPOSED', 'ODDS_OVERRIDE_APPROVED', 'ODDS_OVERRIDE_REJECTED',
        'CREDIT_CORRECTION_PROPOSED', 'CREDIT_CORRECTION_APPROVED',
        'CREDIT_CORRECTION_REJECTED', 'CREDIT_CORRECTION_SETTLEMENT',
        'CREDIT_CORRECTION_RECOVERED'
    ));

CREATE UNIQUE INDEX uq_credit_ledger_settlement_effect
    ON public.credit_ledger_entry (source_id)
    WHERE entry_type IN ('PAYOUT', 'REFUND');

CREATE TABLE public.credit_correction_case (
    case_id uuid NOT NULL,
    identity_id uuid NOT NULL,
    kind varchar(16) COLLATE "C" NOT NULL,
    origin varchar(16) COLLATE "C" NOT NULL,
    reason varchar(40) COLLATE "C" NOT NULL,
    amount numeric(20,2) NOT NULL,
    evidence_type varchar(16) COLLATE "C" NULL,
    evidence_id uuid NULL,
    enrollment_id uuid NULL,
    original_entry_id uuid NULL,
    previous_settlement_id uuid NULL,
    replacement_settlement_id uuid NULL,
    supersedes_case_id uuid NULL,
    proposed_by_identity_id uuid NULL,
    description varchar(1000) NULL,
    calculation varchar(1000) NULL,
    reference_code varchar(64) COLLATE "C" NOT NULL,
    idempotency_key varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_credit_correction_case PRIMARY KEY (case_id),
    CONSTRAINT fk_credit_correction_identity FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_credit_correction_original FOREIGN KEY (original_entry_id)
        REFERENCES public.credit_ledger_entry (entry_id),
    CONSTRAINT fk_credit_correction_previous_settlement FOREIGN KEY (previous_settlement_id)
        REFERENCES public.prediction_settlement (settlement_id),
    CONSTRAINT fk_credit_correction_replacement_settlement FOREIGN KEY (replacement_settlement_id)
        REFERENCES public.prediction_settlement (settlement_id),
    CONSTRAINT fk_credit_correction_supersedes FOREIGN KEY (supersedes_case_id)
        REFERENCES public.credit_correction_case (case_id),
    CONSTRAINT fk_credit_correction_proposer FOREIGN KEY (proposed_by_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT uq_credit_correction_reference UNIQUE (reference_code),
    CONSTRAINT uq_credit_correction_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uq_credit_correction_replacement UNIQUE (replacement_settlement_id),
    CONSTRAINT ck_credit_correction_kind CHECK (kind IN ('CREDIT', 'RECOVERY')),
    CONSTRAINT ck_credit_correction_origin CHECK (origin IN ('ADMINISTRATIVE', 'SETTLEMENT')),
    CONSTRAINT ck_credit_correction_reason CHECK (reason IN (
        'SETTLEMENT_CORRECTION', 'DUPLICATE_OR_MISSING_ENTRY',
        'PROVEN_TECHNICAL_FAILURE', 'ELIGIBILITY_CORRECTION'
    )),
    CONSTRAINT ck_credit_correction_amount CHECK (amount > 0.00 AND scale(amount) = 2),
    CONSTRAINT ck_credit_correction_evidence_type CHECK (
        evidence_type IS NULL OR evidence_type IN ('LEDGER_ENTRY', 'PREDICTION', 'SETTLEMENT')
    ),
    CONSTRAINT ck_credit_correction_key CHECK (btrim(idempotency_key) <> ''),
    CONSTRAINT ck_credit_correction_reference CHECK (btrim(reference_code) <> ''),
    CONSTRAINT ck_credit_correction_origin_fields CHECK (
        (origin = 'ADMINISTRATIVE' AND proposed_by_identity_id IS NOT NULL
            AND description IS NOT NULL AND btrim(description) <> ''
            AND calculation IS NOT NULL AND btrim(calculation) <> ''
            AND evidence_type IS NOT NULL AND evidence_id IS NOT NULL
            AND ((reason = 'ELIGIBILITY_CORRECTION') = (enrollment_id IS NOT NULL))
            AND replacement_settlement_id IS NULL)
        OR (origin = 'SETTLEMENT' AND kind = 'RECOVERY'
            AND reason = 'SETTLEMENT_CORRECTION'
            AND proposed_by_identity_id IS NULL
            AND evidence_type IS NULL AND evidence_id IS NULL AND enrollment_id IS NULL
            AND previous_settlement_id IS NOT NULL
            AND replacement_settlement_id IS NOT NULL)
    )
);

CREATE INDEX idx_credit_correction_fifo
    ON public.credit_correction_case (identity_id, created_at, case_id)
    WHERE kind = 'RECOVERY';

CREATE TABLE public.credit_correction_decision (
    decision_id uuid NOT NULL,
    case_id uuid NOT NULL,
    checker_identity_id uuid NOT NULL,
    decision varchar(16) COLLATE "C" NOT NULL,
    decided_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_credit_correction_decision PRIMARY KEY (decision_id),
    CONSTRAINT uq_credit_correction_decision_case UNIQUE (case_id),
    CONSTRAINT fk_credit_correction_decision_case FOREIGN KEY (case_id)
        REFERENCES public.credit_correction_case (case_id),
    CONSTRAINT fk_credit_correction_checker FOREIGN KEY (checker_identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_credit_correction_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE OR REPLACE FUNCTION public.validate_credit_correction_decision()
RETURNS TRIGGER AS $$
DECLARE proposer uuid;
DECLARE correction_origin varchar(16);
DECLARE correction_kind varchar(16);
DECLARE correction_evidence_type varchar(16);
DECLARE correction_evidence_id uuid;
DECLARE correction_original_entry_id uuid;
BEGIN
    SELECT proposed_by_identity_id, origin, kind, evidence_type, evidence_id, original_entry_id
    INTO proposer, correction_origin, correction_kind, correction_evidence_type,
         correction_evidence_id, correction_original_entry_id
    FROM public.credit_correction_case WHERE case_id = NEW.case_id;
    IF correction_origin IS DISTINCT FROM 'ADMINISTRATIVE'
        OR proposer IS NOT DISTINCT FROM NEW.checker_identity_id THEN
        RAISE EXCEPTION 'credit correction requires a distinct checker';
    END IF;
    IF NEW.decision = 'APPROVED' THEN
        PERFORM pg_advisory_xact_lock(
            hashtext(coalesce(correction_original_entry_id::text,
                              correction_evidence_type || correction_evidence_id::text)),
            hashtext(correction_kind)
        );
        IF EXISTS (
            SELECT 1 FROM public.credit_correction_case c
            JOIN public.credit_correction_decision d ON d.case_id = c.case_id
            WHERE c.case_id <> NEW.case_id AND c.origin = 'ADMINISTRATIVE'
              AND ((c.evidence_type = correction_evidence_type
                    AND c.evidence_id = correction_evidence_id)
                   OR (correction_original_entry_id IS NOT NULL
                       AND c.original_entry_id = correction_original_entry_id))
              AND c.kind = correction_kind AND d.decision = 'APPROVED'
        ) THEN
            RAISE EXCEPTION 'monetary evidence already has an approved correction of this kind';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_credit_correction_decision
BEFORE INSERT ON public.credit_correction_decision FOR EACH ROW
EXECUTE FUNCTION public.validate_credit_correction_decision();

CREATE TABLE public.credit_correction_allocation (
    allocation_id uuid NOT NULL,
    case_id uuid NOT NULL,
    source_entry_id uuid NULL,
    source_key varchar(255) COLLATE "C" NOT NULL,
    recovery_entry_id uuid NOT NULL,
    amount numeric(20,2) NOT NULL,
    created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_credit_correction_allocation PRIMARY KEY (allocation_id),
    CONSTRAINT uq_credit_correction_allocation_source UNIQUE (case_id, source_key),
    CONSTRAINT uq_credit_correction_allocation_recovery UNIQUE (recovery_entry_id),
    CONSTRAINT fk_credit_correction_allocation_case FOREIGN KEY (case_id)
        REFERENCES public.credit_correction_case (case_id),
    CONSTRAINT fk_credit_correction_allocation_source FOREIGN KEY (source_entry_id)
        REFERENCES public.credit_ledger_entry (entry_id),
    CONSTRAINT fk_credit_correction_allocation_recovery FOREIGN KEY (recovery_entry_id)
        REFERENCES public.credit_ledger_entry (entry_id),
    CONSTRAINT ck_credit_correction_allocation_amount CHECK (amount > 0.00 AND scale(amount) = 2),
    CONSTRAINT ck_credit_correction_allocation_key CHECK (btrim(source_key) <> '')
);

CREATE OR REPLACE FUNCTION public.validate_credit_correction_allocation()
RETURNS TRIGGER AS $$
DECLARE owner_id uuid;
DECLARE correction_amount numeric(20,2);
DECLARE correction_kind varchar(16);
DECLARE correction_origin varchar(16);
DECLARE approved boolean;
DECLARE already_recovered numeric(20,2);
DECLARE recovery public.credit_ledger_entry%ROWTYPE;
DECLARE funding public.credit_ledger_entry%ROWTYPE;
BEGIN
    SELECT identity_id, amount, kind, origin
    INTO owner_id, correction_amount, correction_kind, correction_origin
    FROM public.credit_correction_case WHERE case_id = NEW.case_id FOR NO KEY UPDATE;
    SELECT EXISTS (
        SELECT 1 FROM public.credit_correction_decision
        WHERE case_id = NEW.case_id AND decision = 'APPROVED'
    ) INTO approved;
    SELECT * INTO recovery FROM public.credit_ledger_entry WHERE entry_id = NEW.recovery_entry_id;
    SELECT coalesce(sum(amount), 0.00) INTO already_recovered
    FROM public.credit_correction_allocation WHERE case_id = NEW.case_id;
    IF correction_kind IS DISTINCT FROM 'RECOVERY'
        OR (correction_origin <> 'SETTLEMENT' AND NOT approved)
        OR recovery.identity_id IS DISTINCT FROM owner_id
        OR recovery.entry_type IS DISTINCT FROM 'CORRECTION_RECOVERY'
        OR recovery.source_id IS DISTINCT FROM NEW.allocation_id
        OR recovery.amount IS DISTINCT FROM NEW.amount
        OR already_recovered + NEW.amount > correction_amount THEN
        RAISE EXCEPTION 'invalid credit correction allocation';
    END IF;
    IF NEW.source_entry_id IS NOT NULL THEN
        SELECT * INTO funding FROM public.credit_ledger_entry WHERE entry_id = NEW.source_entry_id;
        IF funding.identity_id IS DISTINCT FROM owner_id OR funding.direction IS DISTINCT FROM 'CREDIT' THEN
            RAISE EXCEPTION 'correction funding entry does not belong to owner';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_validate_credit_correction_allocation
BEFORE INSERT ON public.credit_correction_allocation FOR EACH ROW
EXECUTE FUNCTION public.validate_credit_correction_allocation();

CREATE TRIGGER trg_prevent_credit_correction_case_modification
BEFORE UPDATE OR DELETE ON public.credit_correction_case FOR EACH ROW
EXECUTE FUNCTION public.prevent_credit_ledger_modification();
CREATE TRIGGER trg_prevent_credit_correction_decision_modification
BEFORE UPDATE OR DELETE ON public.credit_correction_decision FOR EACH ROW
EXECUTE FUNCTION public.prevent_credit_ledger_modification();
CREATE TRIGGER trg_prevent_credit_correction_allocation_modification
BEFORE UPDATE OR DELETE ON public.credit_correction_allocation FOR EACH ROW
EXECUTE FUNCTION public.prevent_credit_ledger_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.credit_correction_case, public.credit_correction_decision, public.credit_correction_allocation TO %I', runtime_role);
    EXECUTE format('REVOKE UPDATE, DELETE ON TABLE public.credit_correction_case, public.credit_correction_decision, public.credit_correction_allocation FROM %I', runtime_role);
END $$;
