CREATE TABLE public.achievement_catalog (
    code varchar(32) COLLATE "C" NOT NULL,
    edition varchar(16) COLLATE "C" NOT NULL,
    name varchar(80) NOT NULL,
    description varchar(240) NOT NULL,
    rule_text varchar(320) NOT NULL,
    CONSTRAINT pk_achievement_catalog PRIMARY KEY (code, edition),
    CONSTRAINT ck_achievement_catalog_text CHECK (
        btrim(name) <> '' AND btrim(description) <> '' AND btrim(rule_text) <> '')
);

INSERT INTO public.achievement_catalog (code, edition, name, description, rule_text) VALUES
    ('STREAK_3', 'v1', 'Trinca de acertos', 'Tres palpites definitivos corretos em sequencia.',
     'Tres resultados WON consecutivos, sem LOST entre eles, na ordem de aceite.'),
    ('UNDERDOG_3', 'v1', 'Olho no azarao', 'Acerto em uma selecao de odd alta.',
     'Um palpite WON com odd aceita maior ou igual a 3,00.'),
    ('ROUND_3', 'v1', 'Destaque da fase', 'Tres acertos na mesma fase de uma edicao.',
     'Tres palpites WON em eventos diferentes da mesma fase e edicao.'),
    ('FAVORITE', 'v1', 'Torcida certeira', 'Acerto apoiando a organizacao favorita.',
     'Um palpite WON no time da organizacao favorita registrada na avaliacao.');

CREATE TABLE public.achievement_award (
    award_id uuid NOT NULL DEFAULT gen_random_uuid(),
    identity_id uuid NOT NULL,
    code varchar(32) COLLATE "C" NOT NULL,
    catalog_edition varchar(16) COLLATE "C" NOT NULL,
    sports_edition_id uuid NOT NULL,
    origin_key varchar(255) COLLATE "C" NOT NULL,
    origin_settlement_id uuid NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL DEFAULT 'AWARDED',
    awarded_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    revoked_at timestamptz(6),
    revocation_reason varchar(240),
    CONSTRAINT pk_achievement_award PRIMARY KEY (award_id),
    CONSTRAINT uq_achievement_award_origin UNIQUE (identity_id, code, catalog_edition, origin_key),
    CONSTRAINT fk_achievement_award_identity FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT fk_achievement_award_catalog FOREIGN KEY (code, catalog_edition)
        REFERENCES public.achievement_catalog (code, edition),
    CONSTRAINT fk_achievement_award_edition FOREIGN KEY (sports_edition_id)
        REFERENCES public.sports_edition (edition_id),
    CONSTRAINT fk_achievement_award_origin FOREIGN KEY (origin_settlement_id)
        REFERENCES public.prediction_settlement (settlement_id),
    CONSTRAINT ck_achievement_award_key CHECK (btrim(origin_key) <> ''),
    CONSTRAINT ck_achievement_award_state CHECK (state IN ('AWARDED', 'REVOKED')),
    CONSTRAINT ck_achievement_award_revocation CHECK (
        (state = 'AWARDED' AND revoked_at IS NULL AND revocation_reason IS NULL)
        OR (state = 'REVOKED' AND revoked_at IS NOT NULL
            AND revocation_reason IS NOT NULL AND btrim(revocation_reason) <> ''))
);

CREATE INDEX idx_achievement_award_identity
    ON public.achievement_award (identity_id, awarded_at DESC);

CREATE FUNCTION public.guard_achievement_award_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'achievement award history cannot be deleted';
    END IF;
    IF OLD.state <> 'AWARDED' OR NEW.state <> 'REVOKED'
        OR NEW.award_id IS DISTINCT FROM OLD.award_id
        OR NEW.identity_id IS DISTINCT FROM OLD.identity_id
        OR NEW.code IS DISTINCT FROM OLD.code
        OR NEW.catalog_edition IS DISTINCT FROM OLD.catalog_edition
        OR NEW.sports_edition_id IS DISTINCT FROM OLD.sports_edition_id
        OR NEW.origin_key IS DISTINCT FROM OLD.origin_key
        OR NEW.origin_settlement_id IS DISTINCT FROM OLD.origin_settlement_id
        OR NEW.awarded_at IS DISTINCT FROM OLD.awarded_at
        OR NEW.revoked_at IS NULL OR NEW.revocation_reason IS NULL
        OR btrim(NEW.revocation_reason) = '' THEN
        RAISE EXCEPTION 'achievement award may only transition to REVOKED';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_achievement_award_guard
BEFORE UPDATE OR DELETE ON public.achievement_award FOR EACH ROW
EXECUTE FUNCTION public.guard_achievement_award_mutation();

CREATE TABLE public.achievement_revocation (
    revocation_id uuid NOT NULL DEFAULT gen_random_uuid(),
    award_id uuid NOT NULL,
    reason varchar(240) NOT NULL,
    revoked_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_achievement_revocation PRIMARY KEY (revocation_id),
    CONSTRAINT uq_achievement_revocation_award UNIQUE (award_id),
    CONSTRAINT fk_achievement_revocation_award FOREIGN KEY (award_id)
        REFERENCES public.achievement_award (award_id),
    CONSTRAINT ck_achievement_revocation_reason CHECK (btrim(reason) <> '')
);

CREATE TRIGGER trg_achievement_revocation_immutable
BEFORE UPDATE OR DELETE ON public.achievement_revocation FOR EACH ROW
EXECUTE FUNCTION public.prevent_settlement_fact_modification();

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('GRANT SELECT ON TABLE public.achievement_catalog TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE (state, revoked_at, revocation_reason) ON TABLE public.achievement_award TO %I', runtime_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE public.achievement_revocation TO %I', runtime_role);
END $$;
