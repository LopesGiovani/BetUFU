package br.com.betufu.platform.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FlywayChecksumIT {

    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_checksum_test")
            .withUsername("betufu_checksum_user")
            .withPassword("betufu-checksum-pass");

    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(POSTGRESQL.getJdbcUrl());
        dataSource.setUsername(POSTGRESQL.getUsername());
        dataSource.setPassword(POSTGRESQL.getPassword());

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    }

    @Test
    @DisplayName("Flyway validate() falha com exceção quando migration aplicada sofre alteração de checksum")
    void verifiesChecksumMismatchFailsFlywayValidation(@TempDir Path tempDir) throws IOException {
        // 1. Aplica as migrações originais com sucesso (V1 e V2)
        Flyway originalFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        originalFlyway.migrate();

        // 2. Prepara diretório temporário contendo V1 adulterada e V2 intacta
        Path tamperedV1 = tempDir.resolve("V1__create_local_identity.sql");
        Files.writeString(tamperedV1, "CREATE TABLE public.local_identity (tampered_col int);");

        Path intactV2 = tempDir.resolve("V2__add_platform_guards.sql");
        String v2Content = new ClassPathResource("db/migration/V2__add_platform_guards.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(intactV2, v2Content);

        // 3. Executa validação apontando para a fixture com V1 adulterada
        Flyway tamperedFlyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + tempDir.toAbsolutePath())
                .load();

        assertThatThrownBy(tamperedFlyway::validate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Migration checksum mismatch");
    }
}
