package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class ChatRulesPreferenceSchemaIT {
  private static final String RUNTIME = "chat_rules_preference_runtime";
  private static final DockerImageName IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_rules_schema")
          .withUsername("rules_owner")
          .withPassword("rules-owner-only");

  private static JdbcTemplate owner;
  private JdbcTemplate runtime;

  @BeforeAll
  static void role() {
    owner =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    owner.execute("CREATE ROLE " + RUNTIME + " LOGIN PASSWORD 'rules-runtime-only'");
  }

  @BeforeEach
  void migrate() {
    owner.execute(
        "DROP SCHEMA public CASCADE; CREATE SCHEMA public; GRANT USAGE ON SCHEMA public TO "
            + RUNTIME);
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .initSql("SET betufu.runtime_role='" + RUNTIME + "'")
        .load()
        .migrate();
    runtime =
        new JdbcTemplate(
            new DriverManagerDataSource(POSTGRES.getJdbcUrl(), RUNTIME, "rules-runtime-only"));
  }

  @Test
  void preferenceIsUniqueAndImmutableForTheRuntimeRole() {
    UUID identity =
        owner.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://schema.invalid',?) RETURNING identity_id",
            UUID.class,
            UUID.randomUUID().toString());
    runtime.update("INSERT INTO chat_rules_dismissal(owner_identity_id) VALUES (?)", identity);
    runtime.update(
        "INSERT INTO chat_rules_dismissal(owner_identity_id) VALUES (?) ON CONFLICT DO NOTHING",
        identity);
    assertThat(runtime.queryForObject("SELECT count(*) FROM chat_rules_dismissal", Integer.class))
        .isEqualTo(1);
    assertThatThrownBy(() -> runtime.update("UPDATE chat_rules_dismissal SET dismissed_at=now()"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> runtime.update("DELETE FROM chat_rules_dismissal"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> owner.update("DELETE FROM chat_rules_dismissal"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
