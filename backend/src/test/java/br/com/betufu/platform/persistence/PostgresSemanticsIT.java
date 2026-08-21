package br.com.betufu.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PostgresSemanticsIT {

    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_semantics_test")
            .withUsername("betufu_semantics_user")
            .withPassword("betufu-semantics-pass");

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRESQL.getJdbcUrl());
        dataSource.setUsername(POSTGRESQL.getUsername());
        dataSource.setPassword(POSTGRESQL.getPassword());

        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    }

    @Test
    @DisplayName("A-7: CHECK com operador POSIX '~' aceita ASCII estrito e recusa homoglifo cirílico com nome da constraint")
    void posixRegexCheckConstraintRejectsHomoglyphAndIdentifiesConstraint() {
        jdbcTemplate.execute("""
            CREATE TABLE public.test_profile (
                profile_id uuid NOT NULL DEFAULT gen_random_uuid(),
                nickname varchar(20) COLLATE "C" NOT NULL,
                CONSTRAINT pk_test_profile PRIMARY KEY (profile_id),
                CONSTRAINT ck_test_profile_nickname_format CHECK (nickname ~ '^[A-Za-z0-9_-]{3,20}$')
            );
        """);

        // 1. Valor válido dentro do formato aceito (D-44)
        String validNickname = "giovani_123";
        int inserted = jdbcTemplate.update(
                "INSERT INTO public.test_profile (nickname) VALUES (?)",
                validNickname);
        assertThat(inserted).isEqualTo(1);

        Integer validCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.test_profile WHERE nickname = ?",
                Integer.class,
                validNickname);
        assertThat(validCount).isEqualTo(1);

        // 2. Homoglifo cirílico da letra 'a' (U+0430: \u0430) em apelido de comprimento 7
        String cyrillicA = "\u0430";
        String homoglyphNickname = "giov" + cyrillicA + "ni";
        assertThat(homoglyphNickname).hasSize(7);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO public.test_profile (nickname) VALUES (?)",
                        homoglyphNickname))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_test_profile_nickname_format");

        // 3. Garante que apenas o registro válido permanece na tabela
        Integer totalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.test_profile",
                Integer.class);
        assertThat(totalCount).isEqualTo(1);
    }

    @Test
    @DisplayName("A-5: Índice único parcial permite valores duplicados quando predicado é falso e recusa quando verdadeiro")
    void partialUniqueIndexAllowsDuplicatesAcrossInactiveStatesAndRejectsActiveConflict() {
        jdbcTemplate.execute("""
            CREATE TABLE public.test_ufu_enrollment (
                enrollment_id uuid NOT NULL DEFAULT gen_random_uuid(),
                matricula_hmac bytea NOT NULL,
                status varchar(16) COLLATE "C" NOT NULL,
                CONSTRAINT pk_test_ufu_enrollment PRIMARY KEY (enrollment_id)
            );

            CREATE UNIQUE INDEX uq_test_ufu_enrollment_active_hmac
                ON public.test_ufu_enrollment (matricula_hmac)
                WHERE status = 'ATIVO';
        """);

        byte[] sampleHmac = "sample-32-byte-hmac-for-test-001".getBytes(StandardCharsets.UTF_8);

        // 1. Primeira linha com status ATIVO (satisfaz o predicado do índice parcial)
        int firstInsert = jdbcTemplate.update(
                "INSERT INTO public.test_ufu_enrollment (matricula_hmac, status) VALUES (?, ?)",
                sampleHmac,
                "ATIVO");
        assertThat(firstInsert).isEqualTo(1);

        // 2. Segunda linha com o MESMO hmac, mas status LIBERADO (não satisfaz o predicado)
        int secondInsert = jdbcTemplate.update(
                "INSERT INTO public.test_ufu_enrollment (matricula_hmac, status) VALUES (?, ?)",
                sampleHmac,
                "LIBERADO");
        assertThat(secondInsert).isEqualTo(1);

        // Ambas as linhas devem coexistir na tabela
        Integer totalForHmac = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.test_ufu_enrollment WHERE matricula_hmac = ?",
                Integer.class,
                (Object) sampleHmac);
        assertThat(totalForHmac).isEqualTo(2);

        // 3. Terceira linha com o MESMO hmac e status ATIVO (conflito no índice parcial)
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO public.test_ufu_enrollment (matricula_hmac, status) VALUES (?, ?)",
                        sampleHmac,
                        "ATIVO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_test_ufu_enrollment_active_hmac");

        // Total de linhas permanece 2 (a terceira foi rejeitada)
        Integer finalCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.test_ufu_enrollment WHERE matricula_hmac = ?",
                Integer.class,
                (Object) sampleHmac);
        assertThat(finalCount).isEqualTo(2);
    }

    @Test
    @DisplayName("A-9: Trigger BEFORE UPDATE/DELETE bloqueia com exceção observável; RULE DO INSTEAD falha em silêncio")
    void triggerBlocksWithObservableExceptionWhileRuleFailsSilently() {
        // Cenário 1: Tabela com trigger que lança exceção (falha ruidosa)
        jdbcTemplate.execute("""
            CREATE TABLE public.test_audit_trigger (
                event_id uuid NOT NULL DEFAULT gen_random_uuid(),
                action varchar(64) COLLATE "C" NOT NULL,
                payload text NOT NULL,
                CONSTRAINT pk_test_audit_trigger PRIMARY KEY (event_id)
            );

            CREATE OR REPLACE FUNCTION public.prevent_audit_trigger_modification()
            RETURNS TRIGGER AS $$
            BEGIN
                RAISE EXCEPTION 'audit_event is immutable: UPDATE and DELETE are prohibited';
            END;
            $$ LANGUAGE plpgsql;

            CREATE TRIGGER trg_prevent_audit_trigger_modification
            BEFORE UPDATE OR DELETE ON public.test_audit_trigger
            FOR EACH ROW
            EXECUTE FUNCTION public.prevent_audit_trigger_modification();
        """);

        UUID triggerRowId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.test_audit_trigger (event_id, action, payload) VALUES (?, ?, ?)",
                triggerRowId,
                "ROLE_GRANTED",
                "{\"role\":\"MODERATOR\"}");

        // UPDATE no trigger falha com exceção contendo a mensagem definida e SQLSTATE P0001
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "UPDATE public.test_audit_trigger SET action = ? WHERE event_id = ?",
                        "ROLE_REVOKED",
                        triggerRowId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("audit_event is immutable: UPDATE and DELETE are prohibited")
                .satisfies(ex -> {
                    UncategorizedSQLException uncategorized = (UncategorizedSQLException) ex;
                    SQLException sqlEx = uncategorized.getSQLException();
                    assertNotNull(sqlEx);
                    assertThat(sqlEx.getSQLState()).isEqualTo("P0001");
                    assertThat(sqlEx.getMessage()).contains("audit_event is immutable: UPDATE and DELETE are prohibited");
                });

        // DELETE no trigger falha com exceção contendo a mensagem definida e SQLSTATE P0001
        assertThatThrownBy(() -> jdbcTemplate.update(
                        "DELETE FROM public.test_audit_trigger WHERE event_id = ?",
                        triggerRowId))
                .isInstanceOf(UncategorizedSQLException.class)
                .hasMessageContaining("audit_event is immutable: UPDATE and DELETE are prohibited")
                .satisfies(ex -> {
                    UncategorizedSQLException uncategorized = (UncategorizedSQLException) ex;
                    SQLException sqlEx = uncategorized.getSQLException();
                    assertNotNull(sqlEx);
                    assertThat(sqlEx.getSQLState()).isEqualTo("P0001");
                    assertThat(sqlEx.getMessage()).contains("audit_event is immutable: UPDATE and DELETE are prohibited");
                });

        // Linha permanece intacta
        String triggerAction = jdbcTemplate.queryForObject(
                "SELECT action FROM public.test_audit_trigger WHERE event_id = ?",
                String.class,
                triggerRowId);
        assertThat(triggerAction).isEqualTo("ROLE_GRANTED");

        // Cenário 2: Tabela com RULE DO INSTEAD NOTHING (falha silenciosa)
        jdbcTemplate.execute("""
            CREATE TABLE public.test_audit_rule (
                event_id uuid NOT NULL DEFAULT gen_random_uuid(),
                action varchar(64) COLLATE "C" NOT NULL,
                payload text NOT NULL,
                CONSTRAINT pk_test_audit_rule PRIMARY KEY (event_id)
            );

            CREATE RULE rule_prevent_audit_rule_update AS ON UPDATE TO public.test_audit_rule
            DO INSTEAD NOTHING;

            CREATE RULE rule_prevent_audit_rule_delete AS ON DELETE TO public.test_audit_rule
            DO INSTEAD NOTHING;
        """);

        UUID ruleRowId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.test_audit_rule (event_id, action, payload) VALUES (?, ?, ?)",
                ruleRowId,
                "ORIGINAL_ACTION",
                "{\"data\":\"original\"}");

        // UPDATE retorna 0 linhas afetadas sem lançar exceção
        int ruleUpdatedRows = jdbcTemplate.update(
                "UPDATE public.test_audit_rule SET action = ? WHERE event_id = ?",
                "TAMPERED_ACTION",
                ruleRowId);
        assertThat(ruleUpdatedRows).isEqualTo(0);

        // DELETE retorna 0 linhas afetadas sem lançar exceção
        int ruleDeletedRows = jdbcTemplate.update(
                "DELETE FROM public.test_audit_rule WHERE event_id = ?",
                ruleRowId);
        assertThat(ruleDeletedRows).isEqualTo(0);

        // Linha permanece inalterada na tabela
        String ruleAction = jdbcTemplate.queryForObject(
                "SELECT action FROM public.test_audit_rule WHERE event_id = ?",
                String.class,
                ruleRowId);
        assertThat(ruleAction).isEqualTo("ORIGINAL_ACTION");
    }
}
