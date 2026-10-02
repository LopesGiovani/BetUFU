-- Rebuildable read model. Settlement and prediction facts remain the source of truth.
CREATE TABLE public.ranking_projection (
    identity_id uuid NOT NULL,
    wins bigint NOT NULL,
    losses bigint NOT NULL,
    rebuilt_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_ranking_projection PRIMARY KEY (identity_id),
    CONSTRAINT fk_ranking_projection_identity FOREIGN KEY (identity_id)
        REFERENCES public.local_identity (identity_id),
    CONSTRAINT ck_ranking_projection_counts CHECK (wins >= 0 AND losses >= 0 AND wins + losses > 0)
);

CREATE INDEX idx_ranking_projection_order
    ON public.ranking_projection (wins DESC, identity_id);

DO $$
DECLARE runtime_role text := current_setting('betufu.runtime_role', true);
BEGIN
    IF runtime_role IS NULL OR btrim(runtime_role) = '' THEN
        RAISE EXCEPTION 'betufu.runtime_role must identify the application runtime role';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = runtime_role) THEN
        RAISE EXCEPTION 'configured betufu.runtime_role does not exist';
    END IF;
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.ranking_projection TO %I', runtime_role);
END $$;
