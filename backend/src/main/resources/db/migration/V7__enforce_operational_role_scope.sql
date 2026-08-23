-- Escopo deixa de ser sempre opcional em operational_role_grant.
--
-- A V6 declarou scope_ref como uuid NULL sem qualquer regra, então um papel escopado podia nascer
-- global e o papel global podia nascer "escopado", aparentando autoridade limitada enquanto
-- conferia autoridade total. A regra vive no serviço, mas o serviço não é a única porta para a
-- tabela: script operacional, correção manual e futura rota administrativa também escrevem aqui.
--
-- A V6 já foi aplicada e não pode ser editada — alterar migration aplicada invalida o checksum do
-- Flyway, garantia que o próprio FlywayChecksumIT deste repositório testa. Por isso, migration nova.

ALTER TABLE public.operational_role_grant
    ADD CONSTRAINT ck_operational_role_grant_scope
    CHECK (
        (role_name = 'ADMINISTRADOR_GERAL' AND scope_ref IS NULL)
        OR (role_name <> 'ADMINISTRADOR_GERAL' AND scope_ref IS NOT NULL)
    );
