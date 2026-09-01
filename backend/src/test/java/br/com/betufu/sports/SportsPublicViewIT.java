package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicEventDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-public-view-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsPublicViewIT.TestOidcConfiguration.class)
class SportsPublicViewIT {

  private static final String ISSUER = "https://idp.invalid";
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:"
                  + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Container static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerProperties(registry);
  }

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private WebApplicationContext applicationContext;
  @Autowired private CorrelationIdFilter correlationIdFilter;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy springSecurityFilterChain;

  @Autowired private SportsPublicViewService publicViewService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private TransactionTemplate transactionTemplate;

  private MockMvc mockMvc;

  private UUID competitionId;
  private UUID editionId;
  private UUID sportId;
  private UUID categoryId;
  private UUID phaseId;
  private UUID venueId;
  private UUID venueSpaceId;
  private UUID orgPublishedId;
  private UUID orgDraftId;
  private UUID teamPublishedId;
  private UUID teamDraftId;
  private UUID athletePublishedId;
  private UUID athleteDraftId;
  private UUID rosterPubId;
  private UUID eventId;
  private UUID eventPubId;
  private UUID resultVersion1Id;
  private UUID resultVersion2Id;
  private UUID publicViewIdentityId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_result_version_slot, public.sports_result_version,
                public.sports_event_publication_slot, public.sports_event_publication,
                public.sports_event_slot, public.sports_event,
                public.sports_roster_publication_member, public.sports_roster_publication,
                public.sports_roster_member, public.sports_roster,
                public.sports_team, public.sports_organization,
                public.sports_athlete,
                public.sports_venue_space, public.sports_venue,
                public.sports_phase, public.sports_category,
                public.sports_sport,
                public.sports_edition, public.sports_competition,
                public.betufu_profile, public.local_identity
                CASCADE
                """);

    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(correlationIdFilter)
            .addFilter(springSecurityFilterChain)
            .build();

    transactionTemplate.executeWithoutResult(status -> seedData());
  }

  private void seedData() {
    publicViewIdentityId = createIdentity("sports-public-view-it-sub", "public_it");
    competitionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition (competition_id, name, slug, status) VALUES (?, ?, ?, 'ATIVA')",
        competitionId,
        "Olimpíada UFU 2026",
        "olimpiada-ufu-2026");

    editionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_edition (edition_id, competition_id, name, slug, start_date, end_date, status) VALUES (?, ?, ?, ?, ?, ?, 'ATIVA')",
        editionId,
        competitionId,
        "Edição 2026",
        "2026",
        Date.valueOf(LocalDate.of(2026, 9, 1)),
        Date.valueOf(LocalDate.of(2026, 9, 30)));

    sportId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug, status) VALUES (?, ?, ?, 'ATIVA')",
        sportId,
        "Futsal",
        "futsal");

    categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug, status) VALUES (?, ?, ?, ?, 'ATIVA')",
        categoryId,
        editionId,
        "Masculino",
        "masculino");

    phaseId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_phase (phase_id, category_id, edition_id, name, slug, status) VALUES (?, ?, ?, ?, ?, 'ATIVA')",
        phaseId,
        categoryId,
        editionId,
        "Fase de Grupos",
        "fase-de-grupos");

    venueId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_venue (venue_id, competition_id, name, address, map_url, notes) VALUES (?, ?, ?, ?, ?, ?)",
        venueId,
        competitionId,
        "Ginásio Campus Santa Mônica",
        "Av. João Naves de Ávila, 2121",
        "https://maps.google.com/venue",
        "Entrada pelo bloco 1G");

    venueSpaceId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_venue_space (venue_space_id, venue_id, name, notes) VALUES (?, ?, ?, ?)",
        venueSpaceId,
        venueId,
        "Quadra 1",
        "Piso de madeira flutuante");

    // Organizations
    orgPublishedId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        orgPublishedId,
        "Atlética Computação",
        "atletica-computacao");

    orgDraftId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'RASCUNHO')",
        orgDraftId,
        "Atlética Rascunho",
        "atletica-rascunho");

    // Teams
    teamPublishedId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        teamPublishedId,
        orgPublishedId,
        editionId,
        sportId,
        categoryId,
        "Computação Futsal Masc",
        "computacao-futsal-masc");

    teamDraftId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'RASCUNHO')",
        teamDraftId,
        orgPublishedId,
        editionId,
        sportId,
        categoryId,
        "Computação Basquete",
        "computacao-basquete");

    // Athletes
    athletePublishedId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) VALUES (?, ?, ?, ?, 1, 'PUBLICADO')",
        athletePublishedId,
        "Carlos Silva",
        "carlos-silva",
        hmac(1));

    athleteDraftId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) VALUES (?, ?, ?, ?, 1, 'RASCUNHO')",
        athleteDraftId,
        "João Rascunho",
        "joao-rascunho",
        hmac(2));

    // Roster publication
    rosterPubId = UUID.randomUUID();
    UUID rosterId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_roster (roster_id, team_id, status) VALUES (?, ?, 'PUBLICADO')",
        rosterId,
        teamPublishedId);

    jdbcTemplate.update(
        "INSERT INTO public.sports_roster_publication (publication_id, roster_id, team_id, competition_id, edition_id, organization_id, sport_id, category_id, version_no, team_name, organization_name, edition_name, competition_name, sport_name, category_name, published_by_identity_id, base_lock_version, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, 0, ?)",
        rosterPubId,
        rosterId,
        teamPublishedId,
        competitionId,
        editionId,
        orgPublishedId,
        sportId,
        categoryId,
        "Computação Futsal Masc",
        "Atlética Computação",
        "Edição 2026",
        "Olimpíada UFU 2026",
        "Futsal",
        "Masculino",
        publicViewIdentityId,
        Timestamp.from(Instant.now()));

    jdbcTemplate.update(
        "INSERT INTO public.sports_roster_publication_member (publication_id, athlete_id, athlete_name, athlete_slug, jersey_number, position_name) VALUES (?, ?, ?, ?, 10, 'Fixo')",
        rosterPubId,
        athletePublishedId,
        "Carlos Silva",
        "carlos-silva");

    // Events & Publications
    eventId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_event (event_id, competition_id, edition_id, title, event_format, status, status_reason, scheduled_at, zone_id, venue_state, source_reference, sport_id, category_id, phase_id, venue_id, venue_space_id) VALUES (?, ?, ?, ?, 'HEAD_TO_HEAD', 'PUBLICADO', NULL, ?, 'America/Sao_Paulo', 'LOCAL_DEFINIDO', 'https://oficial.ufu.br/sumula-01', ?, ?, ?, ?, ?)",
        eventId,
        competitionId,
        editionId,
        "Computação x Engenharia",
        Timestamp.from(Instant.parse("2026-09-15T18:00:00Z")),
        sportId,
        categoryId,
        phaseId,
        venueId,
        venueSpaceId);

    eventPubId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_event_publication (publication_id, event_id, competition_id, edition_id, version_no, competition_name, edition_name, title, event_format, scheduled_at, zone_id, venue_state, source_reference, published_by_identity_id, base_lock_version, published_at, sport_id, sport_name, category_id, category_name, phase_id, phase_name, venue_id, venue_name, venue_space_id, venue_space_name) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 'HEAD_TO_HEAD', ?, 'America/Sao_Paulo', 'LOCAL_DEFINIDO', 'https://oficial.ufu.br/sumula-01', ?, 0, ?, ?, 'Futsal', ?, 'Masculino', ?, 'Fase de Grupos', ?, 'Ginásio Campus Santa Mônica', ?, 'Quadra 1')",
        eventPubId,
        eventId,
        competitionId,
        editionId,
        "Olimpíada UFU 2026",
        "Edição 2026",
        "Computação x Engenharia",
        Timestamp.from(Instant.parse("2026-09-15T18:00:00Z")),
        publicViewIdentityId,
        Timestamp.from(Instant.now()),
        sportId,
        categoryId,
        phaseId,
        venueId,
        venueSpaceId);

    jdbcTemplate.update(
        "INSERT INTO public.sports_event_publication_slot (publication_id, slot_no, display_label, team_id, team_name) VALUES (?, 1, 'Computação', ?, 'Computação Futsal Masc')",
        eventPubId,
        teamPublishedId);
    jdbcTemplate.update(
        "INSERT INTO public.sports_event_publication_slot (publication_id, slot_no, display_label, team_id, team_name) VALUES (?, 2, 'Engenharia', NULL, 'Engenharia Futsal Masc')",
        eventPubId);

    // Result versions (Version 1: initial source, Version 2: corrected official confirmed source)
    resultVersion1Id = UUID.randomUUID();
    resultVersion2Id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_result_version (version_id, event_id, competition_id, edition_id, version_no, status, source_reference, reason, recorded_by_identity_id, confirmed_by_identity_id, recorded_at, confirmed_at, base_lock_version) VALUES (?, ?, ?, ?, 1, 'CONFIRMADO', 'https://oficial.ufu.br/sumula-v1', 'Publicação inicial', ?, ?, ?, ?, 0)",
        resultVersion1Id,
        eventId,
        competitionId,
        editionId,
        publicViewIdentityId,
        publicViewIdentityId,
        Timestamp.from(Instant.parse("2026-09-15T20:00:00Z")),
        Timestamp.from(Instant.parse("2026-09-15T20:30:00Z")));

    jdbcTemplate.update(
        "INSERT INTO public.sports_result_version (version_id, event_id, competition_id, edition_id, version_no, status, source_reference, reason, recorded_by_identity_id, confirmed_by_identity_id, recorded_at, confirmed_at, supersedes_version_id, base_lock_version) VALUES (?, ?, ?, ?, 2, 'CONFIRMADO', 'https://oficial.ufu.br/sumula-v2', 'Correção de placar da mesa', ?, ?, ?, ?, ?, 0)",
        resultVersion2Id,
        eventId,
        competitionId,
        editionId,
        publicViewIdentityId,
        publicViewIdentityId,
        Timestamp.from(Instant.parse("2026-09-15T21:00:00Z")),
        Timestamp.from(Instant.parse("2026-09-15T21:15:00Z")),
        resultVersion1Id);

    jdbcTemplate.update(
        "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 1, 3, 'Computação', ?)",
        resultVersion1Id,
        teamPublishedId);
    jdbcTemplate.update(
        "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 2, 1, 'Engenharia', NULL)",
        resultVersion1Id);

    jdbcTemplate.update(
        "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 1, 4, 'Computação', ?)",
        resultVersion2Id,
        teamPublishedId);
    jdbcTemplate.update(
        "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 2, 2, 'Engenharia', NULL)",
        resultVersion2Id);

    jdbcTemplate.update(
        "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
        resultVersion2Id,
        eventId);
  }

  // ==========================================
  // P2: Competição
  // ==========================================

  @Test
  @DisplayName("P2: GET /competitions/{id} retorna 200 com detalhe e coleções completas")
  void getCompetitionDetail_whenPublished_returns200AndFullProjection() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/competitions/" + competitionId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.competitionId").value(competitionId.toString()))
        .andExpect(jsonPath("$.name").value("Olimpíada UFU 2026"))
        .andExpect(jsonPath("$.slug").value("olimpiada-ufu-2026"))
        .andExpect(jsonPath("$.editions.length()").value(1))
        .andExpect(jsonPath("$.editions[0].name").value("Edição 2026"))
        .andExpect(jsonPath("$.categories.length()").value(1))
        .andExpect(jsonPath("$.categories[0].name").value("Masculino"))
        .andExpect(jsonPath("$.phases.length()").value(1))
        .andExpect(jsonPath("$.phases[0].name").value("Fase de Grupos"))
        .andExpect(jsonPath("$.venues.length()").value(1))
        .andExpect(jsonPath("$.venues[0].name").value("Ginásio Campus Santa Mônica"))
        .andExpect(jsonPath("$.venues[0].spaces.length()").value(1))
        .andExpect(jsonPath("$.venues[0].spaces[0].name").value("Quadra 1"))
        .andExpect(jsonPath("$.teams.length()").value(1))
        .andExpect(jsonPath("$.teams[0].name").value("Computação Futsal Masc"))
        .andExpect(jsonPath("$.recentEvents.length()").value(1))
        .andExpect(jsonPath("$.recentEvents[0].title").value("Computação x Engenharia"));
  }

  @Test
  @DisplayName("P2: GET /competitions/{id} inexistente responde 404")
  void getCompetitionDetail_whenNotFound_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/competitions/" + UUID.randomUUID())
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  // ==========================================
  // P4: Organização Esportiva
  // ==========================================

  @Test
  @DisplayName("P4: GET /organizations/{id} publicado retorna 200 com equipes, elenco e eventos")
  void getOrganizationDetail_whenPublished_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/organizations/" + orgPublishedId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId").value(orgPublishedId.toString()))
        .andExpect(jsonPath("$.name").value("Atlética Computação"))
        .andExpect(jsonPath("$.teams.length()").value(1))
        .andExpect(jsonPath("$.teams[0].name").value("Computação Futsal Masc"))
        .andExpect(jsonPath("$.rosterMembers.length()").value(1))
        .andExpect(jsonPath("$.rosterMembers[0].athleteName").value("Carlos Silva"))
        .andExpect(jsonPath("$.rosterMembers[0].jerseyNumber").value(10))
        .andExpect(jsonPath("$.events.length()").value(1));
  }

  @Test
  @DisplayName("P4: GET /organizations/{id} em rascunho retorna 404")
  void getOrganizationDetail_whenDraft_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/organizations/" + orgDraftId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  // ==========================================
  // P5: Equipe
  // ==========================================

  @Test
  @DisplayName("P5: GET /teams/{id} publicado retorna 200 com elenco publicado e eventos")
  void getTeamDetail_whenPublished_returns200() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/teams/" + teamPublishedId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamId").value(teamPublishedId.toString()))
        .andExpect(jsonPath("$.name").value("Computação Futsal Masc"))
        .andExpect(jsonPath("$.organizationName").value("Atlética Computação"))
        .andExpect(jsonPath("$.roster.versionNo").value(1))
        .andExpect(jsonPath("$.roster.publicationId").doesNotExist())
        .andExpect(jsonPath("$.roster.members.length()").value(1))
        .andExpect(jsonPath("$.roster.members[0].athleteName").value("Carlos Silva"))
        .andExpect(jsonPath("$.roster.members[0].jerseyNumber").value(10))
        .andExpect(jsonPath("$.events.length()").value(1));
  }

  @Test
  @DisplayName("P5: GET /teams/{id} em rascunho retorna 404")
  void getTeamDetail_whenDraft_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/teams/" + teamDraftId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  // ==========================================
  // P6: Atleta
  // ==========================================

  @Test
  @DisplayName("P6: GET /athletes/{id} publicado retorna 200 sem PII e com estatísticas calculadas")
  void getAthleteDetail_whenPublished_returns200WithoutPiiAndWithStats() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/public/sports/athletes/" + athletePublishedId)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.athleteId").value(athletePublishedId.toString()))
            .andExpect(jsonPath("$.name").value("Carlos Silva"))
            .andExpect(jsonPath("$.slug").value("carlos-silva"))
            .andExpect(jsonPath("$.teams.length()").value(1))
            .andExpect(jsonPath("$.teams[0].teamName").value("Computação Futsal Masc"))
            .andExpect(jsonPath("$.teams[0].jerseyNumber").value(10))
            .andExpect(jsonPath("$.events.length()").value(1))
            .andExpect(jsonPath("$.stats.totalParticipations").value(1))
            .andExpect(jsonPath("$.stats.totalWins").value(1))
            .andExpect(jsonPath("$.stats.totalLosses").value(0))
            .andReturn();

    // Strict security assertion: NO PII in the JSON payload
    String content = result.getResponse().getContentAsString();
    JsonNode tree = objectMapper.readTree(content);

    assertThat(content).doesNotContain("matricula");
    assertThat(content).doesNotContain("secret_hmac");
    assertThat(content).doesNotContain("email");
    assertThat(tree.has("matricula")).isFalse();
    assertThat(tree.has("matriculaHmac")).isFalse();
  }

  @Test
  @DisplayName("P6: GET /athletes/{id} em rascunho retorna 404")
  void getAthleteDetail_whenDraft_returns404() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/public/sports/athletes/" + athleteDraftId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("P4/P6: time em rascunho nao atravessa snapshots publicos de elenco")
  void publicViews_hideRosterSnapshotWhenTeamReturnsToDraft() throws Exception {
    jdbcTemplate.update(
        "UPDATE public.sports_team SET status = 'RASCUNHO', updated_at = CURRENT_TIMESTAMP WHERE team_id = ?",
        teamPublishedId);

    mockMvc
        .perform(
            get("/api/v1/public/sports/organizations/" + orgPublishedId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teams.length()").value(0))
        .andExpect(jsonPath("$.rosterMembers.length()").value(0))
        .andExpect(jsonPath("$.events.length()").value(0));

    mockMvc
        .perform(
            get("/api/v1/public/sports/athletes/" + athletePublishedId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teams.length()").value(0))
        .andExpect(jsonPath("$.events.length()").value(0))
        .andExpect(jsonPath("$.stats.totalParticipations").value(0))
        .andExpect(jsonPath("$.stats.totalWins").value(0))
        .andExpect(jsonPath("$.stats.totalLosses").value(0));
  }

  @Test
  @DisplayName("P3/P5: respostas publicas nao expoem IDs de snapshots tecnicos")
  void publicDetails_doNotExposeTechnicalSnapshotIds() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/public/sports/events/" + eventId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode tree = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(tree.has("publicationId")).isFalse();
    assertThat(tree.path("result").has("versionId")).isFalse();
  }

  @Test
  @DisplayName("P6: estatisticas contam todas as participacoes publicadas")
  void getAthleteDetail_statsCountAllPublishedEventsBeyondDisplayWindow() throws Exception {
    for (int index = 0; index < 21; index++) {
      final int extraIndex = index;
      transactionTemplate.executeWithoutResult(
          transactionStatus -> {
            UUID extraEventId = UUID.randomUUID();
            UUID extraPublicationId = UUID.randomUUID();
            Timestamp scheduledAt =
                Timestamp.from(Instant.parse("2026-09-16T18:00:00Z").plusSeconds(extraIndex));

            jdbcTemplate.update(
                "INSERT INTO public.sports_event (event_id, competition_id, edition_id, title, event_format, status, status_reason, scheduled_at, zone_id, venue_state, source_reference, sport_id, category_id, phase_id) VALUES (?, ?, ?, ?, 'HEAD_TO_HEAD', 'PUBLICADO', NULL, ?, 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', ?, ?, ?, ?)",
                extraEventId,
                competitionId,
                editionId,
                "Evento extra " + extraIndex,
                scheduledAt,
                "https://oficial.ufu.br/extra-" + extraIndex,
                sportId,
                categoryId,
                phaseId);
            jdbcTemplate.update(
                "INSERT INTO public.sports_event_publication (publication_id, event_id, competition_id, edition_id, version_no, competition_name, edition_name, title, event_format, scheduled_at, zone_id, venue_state, source_reference, published_by_identity_id, base_lock_version, published_at, sport_id, sport_name, category_id, category_name, phase_id, phase_name) VALUES (?, ?, ?, ?, 1, ?, ?, ?, 'HEAD_TO_HEAD', ?, 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', ?, ?, 0, ?, ?, 'Futsal', ?, 'Masculino', ?, 'Fase de Grupos')",
                extraPublicationId,
                extraEventId,
                competitionId,
                editionId,
                "Olimpiada UFU 2026",
                "Edicao 2026",
                "Evento extra " + extraIndex,
                scheduledAt,
                "https://oficial.ufu.br/extra-" + extraIndex,
                publicViewIdentityId,
                Timestamp.from(Instant.now()),
                sportId,
                categoryId,
                phaseId);
            jdbcTemplate.update(
                "INSERT INTO public.sports_event_publication_slot (publication_id, slot_no, display_label, team_id, team_name) VALUES (?, 1, ?, ?, ?)",
                extraPublicationId,
                "Computacao " + extraIndex,
                teamPublishedId,
                "Computacao Futsal Masc");
            jdbcTemplate.update(
                "INSERT INTO public.sports_event_publication_slot (publication_id, slot_no, display_label, team_id, team_name) VALUES (?, 2, ?, NULL, ?)",
                extraPublicationId,
                "Engenharia " + extraIndex,
                "Engenharia Futsal Masc");
          });
    }

    mockMvc
        .perform(
            get("/api/v1/public/sports/athletes/" + athletePublishedId)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.events.length()").value(20))
        .andExpect(jsonPath("$.stats.totalParticipations").value(22));
  }

  // ==========================================
  // P3 / Service Query Verification
  // ==========================================

  @Test
  @DisplayName(
      "P3: Service findEventDetail retorna snapshot publicado com resultado e histórico de fontes")
  void getEventDetail_serviceDirectQuery_returnsPublishedSnapshotWithResultsAndSourceHistory() {
    PublicEventDetail detail = publicViewService.getEventDetail(eventId);

    assertThat(detail).isNotNull();
    assertThat(detail.eventId()).isEqualTo(eventId);
    assertThat(detail.title()).isEqualTo("Computação x Engenharia");
    assertThat(detail.venueName()).isEqualTo("Ginásio Campus Santa Mônica");
    assertThat(detail.venueAddress()).isEqualTo("Av. João Naves de Ávila, 2121");
    assertThat(detail.venueSpaceName()).isEqualTo("Quadra 1");
    assertThat(detail.slots()).hasSize(2);

    // Result verification
    assertThat(detail.result()).isNotNull();
    assertThat(detail.result().versionNo()).isEqualTo(2);
    assertThat(detail.result().status()).isEqualTo("CONFIRMADO");
    assertThat(detail.result().scores()).hasSize(2);
    assertThat(detail.result().scores().get(0).score()).isEqualTo(4);
    assertThat(detail.result().scores().get(1).score()).isEqualTo(2);

    // Source history verification
    assertThat(detail.sourceHistory()).hasSize(2);
    assertThat(detail.sourceHistory().get(0).versionNo()).isEqualTo(1);
    assertThat(detail.sourceHistory().get(0).sourceReference())
        .isEqualTo("https://oficial.ufu.br/sumula-v1");
    assertThat(detail.sourceHistory().get(1).versionNo()).isEqualTo(2);
    assertThat(detail.sourceHistory().get(1).reason()).isEqualTo("Correção de placar da mesa");
  }

  @Test
  @DisplayName("P3: Service findEventDetail em evento não publicado lança NOT_FOUND")
  void getEventDetail_whenUnpublished_throwsNotFound() {
    UUID unpubId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_event (event_id, competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference) VALUES (?, ?, ?, 'Evento Rascunho', 'HEAD_TO_HEAD', 'RASCUNHO', ?, 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://oficial.ufu.br/rascunho')",
        unpubId,
        competitionId,
        editionId,
        Timestamp.from(Instant.now()));

    assertThatThrownBy(() -> publicViewService.getEventDetail(unpubId))
        .isInstanceOf(ApiProblemException.class)
        .satisfies(
            ex -> assertThat(((ApiProblemException) ex).status()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  // ==========================================
  // Anonymous Access Verification
  // ==========================================

  @Test
  @DisplayName("Todos os endpoints públicos P2, P4, P5, P6 funcionam anonimamente sem credenciais")
  void publicEndpoints_areAnonymousAndPermitAll() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/sports/competitions/" + competitionId))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/public/sports/organizations/" + orgPublishedId))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/public/sports/teams/" + teamPublishedId))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/public/sports/athletes/" + athletePublishedId))
        .andExpect(status().isOk());
  }

  private static byte[] hmac(int marker) {
    byte[] value = new byte[32];
    value[0] = (byte) marker;
    return value;
  }

  private UUID createIdentity(String subject, String nickname) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)", ISSUER, subject);
    UUID id =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            UUID.class,
            ISSUER,
            subject);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)", id, nickname);
    return id;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-sports-public-view-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri(ISSUER + "/protocol/openid-connect/auth")
              .tokenUri(ISSUER + "/protocol/openid-connect/token")
              .jwkSetUri(ISSUER + "/protocol/openid-connect/certs")
              .issuerUri(ISSUER)
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
