package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SportsOutcomeAuthorityIT {

  private static final DockerImageName IMAGE = DockerImageName.parse("postgres:18.4-trixie");

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  private JdbcTemplate jdbcTemplate;
  private UUID competitionId;
  private UUID editionId;
  private UUID eventId;
  private UUID identityId;

  @BeforeEach
  void setUp() {
    jdbcTemplate = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    Flyway.configure().dataSource(jdbcTemplate.getDataSource()).locations("classpath:db/migration").load().migrate();
    jdbcTemplate.execute("TRUNCATE TABLE public.sports_outcome_head, public.sports_outcome_administrative_payload, public.sports_outcome_sporting_payload, public.sports_outcome_fact, public.sports_event, public.sports_edition, public.sports_competition, public.local_identity CASCADE");
    identityId = jdbcTemplate.queryForObject("INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', 'outcome-authority') RETURNING identity_id", UUID.class);
    competitionId = jdbcTemplate.queryForObject("INSERT INTO public.sports_competition (name, slug) VALUES ('Comp outcome', 'comp-outcome') RETURNING competition_id", UUID.class);
    editionId = jdbcTemplate.queryForObject("INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Ed outcome', 'ed-outcome') RETURNING edition_id", UUID.class, competitionId);
    eventId = jdbcTemplate.queryForObject("INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, 'Event outcome', 'HEAD_TO_HEAD', 'PUBLICADO', CURRENT_TIMESTAMP, 'UTC', 'LOCAL_A_DEFINIR', 'https://source.invalid') RETURNING event_id", UUID.class, competitionId, editionId);
  }

  @Test
  @DisplayName("admin outcome nunca recebe score e o head permanece unico")
  void administrativePayloadHasNoScoreAndHeadIsUnique() {
    UUID factId = insertFact("ADMINISTRATIVE_WALKOVER", null);
    jdbcTemplate.update("INSERT INTO public.sports_outcome_administrative_payload (fact_id, event_id, administrative_reason, winner_slot_no) VALUES (?, ?, 'W.O. confirmado', 1)", factId, eventId);
    jdbcTemplate.update("INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?)", eventId, factId);
    assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?)", eventId, UUID.randomUUID()))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> jdbcTemplate.update("UPDATE public.sports_outcome_fact SET reason = 'mutated' WHERE fact_id = ?", factId))
        .hasMessageContaining("immutable");
  }

  private UUID insertFact(String kind, UUID supersedes) {
    return jdbcTemplate.queryForObject("INSERT INTO public.sports_outcome_fact (event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) VALUES (?, ?, ?, 1, ?, 'https://source.invalid/outcome', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?) RETURNING fact_id", UUID.class, eventId, competitionId, editionId, kind, identityId, supersedes);
  }
}
