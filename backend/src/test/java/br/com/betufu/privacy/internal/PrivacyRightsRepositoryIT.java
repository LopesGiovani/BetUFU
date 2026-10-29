package br.com.betufu.privacy.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PrivacyRightsRepositoryIT {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("docker.io/library/postgres:18.4-trixie")
              .asCompatibleSubstituteFor("postgres"));

  @Test
  void caseLockWorksWithoutUpdatePrivilegeOnImmutableFacts() {
    var owner =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    owner.execute("CREATE ROLE privacy_lock_runtime LOGIN PASSWORD 'test-only'");
    owner.execute(
        "CREATE TABLE privacy_rights_case_fact(case_id uuid PRIMARY KEY, subject_kind text, subject_ref uuid, owner_identity_id uuid, request_kind text, requested_at timestamptz DEFAULT now())");
    owner.execute("GRANT USAGE ON SCHEMA public TO privacy_lock_runtime");
    owner.execute("GRANT SELECT ON privacy_rights_case_fact TO privacy_lock_runtime");
    UUID caseId = UUID.randomUUID();
    owner.update(
        "INSERT INTO privacy_rights_case_fact(case_id,subject_kind,owner_identity_id,request_kind) VALUES (?,'ACCOUNT',?,'NOTICE')",
        caseId,
        UUID.randomUUID());
    var source =
        new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "privacy_lock_runtime", "test-only");
    var jdbc = new JdbcTemplate(source);
    assertThat(
            jdbc.queryForObject(
                "SELECT has_table_privilege(current_user,'privacy_rights_case_fact','UPDATE')",
                Boolean.class))
        .isFalse();
    var repository = new PrivacyRightsRepository(jdbc);
    new TransactionTemplate(new DataSourceTransactionManager(source))
        .executeWithoutResult(
            status -> {
              assertThat(repository.lockCase(caseId)).isPresent();
              assertThat(repository.lockCase(UUID.randomUUID())).isEmpty();
            });
  }
}
