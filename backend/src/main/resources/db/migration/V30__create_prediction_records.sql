CREATE TABLE public.prediction (
    prediction_id uuid NOT NULL DEFAULT gen_random_uuid(),
    identity_id uuid NOT NULL,
    market_id uuid NOT NULL,
    market_version_id uuid NOT NULL,
    publication_id uuid NOT NULL,
    rule_version varchar(64) COLLATE "C" NOT NULL,
    selection varchar(64) COLLATE "C" NOT NULL,
    stake numeric(20,2) NOT NULL,
    accepted_odds numeric(20,10) NOT NULL,
    potential_return numeric(20,2) NOT NULL,
    net_profit numeric(20,2) NOT NULL,
    close_deadline timestamptz(6) NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL DEFAULT 'PENDING',
    idempotency_key varchar(255) COLLATE "C" NOT NULL,
    payload_digest varchar(128) COLLATE "C" NOT NULL,
    accepted_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_prediction PRIMARY KEY (prediction_id),
    CONSTRAINT fk_prediction_identity FOREIGN KEY (identity_id) REFERENCES public.local_identity(identity_id),
    CONSTRAINT uq_prediction_identity_market UNIQUE (identity_id, market_id),
    CONSTRAINT uq_prediction_idempotency UNIQUE (identity_id, idempotency_key),
    CONSTRAINT ck_prediction_stake_scale CHECK (scale(stake) = 2 AND stake >= 10.00),
    CONSTRAINT ck_prediction_odds_positive CHECK (accepted_odds > 0),
    CONSTRAINT ck_prediction_return_scale CHECK (scale(potential_return) = 2 AND potential_return >= 0),
    CONSTRAINT ck_prediction_profit_scale CHECK (scale(net_profit) = 2),
    CONSTRAINT ck_prediction_state CHECK (state IN ('PENDING', 'ANNULLED')),
    CONSTRAINT ck_prediction_text CHECK (btrim(rule_version) <> '' AND btrim(selection) <> '' AND btrim(idempotency_key) <> '' AND btrim(payload_digest) <> '')
);

CREATE TABLE public.prediction_selection (
    prediction_id uuid NOT NULL,
    selection_no smallint NOT NULL,
    selection_key varchar(64) COLLATE "C" NOT NULL,
    CONSTRAINT pk_prediction_selection PRIMARY KEY (prediction_id, selection_no),
    CONSTRAINT fk_prediction_selection_prediction FOREIGN KEY (prediction_id) REFERENCES public.prediction(prediction_id),
    CONSTRAINT ck_prediction_selection_no CHECK (selection_no = 1)
);

CREATE INDEX idx_prediction_identity_accepted ON public.prediction(identity_id, accepted_at DESC);

CREATE OR REPLACE FUNCTION public.prevent_prediction_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'prediction history is immutable: UPDATE and DELETE are prohibited';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_prediction_modification
BEFORE UPDATE OR DELETE ON public.prediction
FOR EACH ROW EXECUTE FUNCTION public.prevent_prediction_modification();
CREATE TRIGGER trg_prevent_prediction_selection_modification
BEFORE UPDATE OR DELETE ON public.prediction_selection
FOR EACH ROW EXECUTE FUNCTION public.prevent_prediction_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.prediction, public.prediction_selection TO %I', runtime_role);
END $$;
