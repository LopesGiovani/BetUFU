package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-event-result-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsEventResultIT.TestOidcConfiguration.class)
class SportsEventResultIT {

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

  @Autowired private BetufuRoleAuthorityContributor authorityContributor;

  private MockMvc mockMvc;
  private UUID competitionA;
  private UUID competitionB;
  private UUID editionA;
  private UUID sportA;
  private UUID categoryA;
  private UUID organizationA;
  private UUID team1;
  private UUID team2;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_result_version_slot, public.sports_result_version,
                public.sports_event_publication_slot, public.sports_event_publication,
                public.sports_event_slot, public.sports_event,
                public.sports_roster_publication_member, public.sports_roster_publication,
                public.sports_roster_member, public.sports_roster, public.sports_athlete,
                public.sports_team, public.sports_organization, public.sports_phase,
                public.sports_category, public.sports_sport, public.sports_edition,
                public.sports_competition, public.audit_event, public.operational_role_grant,
                public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE
                """);
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID globalAdmin = createIdentity("global-admin-sub", "global_admin");
    UUID competitionAAdmin = createIdentity("competition-a-admin-sub", "competition_a_admin");
    UUID competitionBAdmin = createIdentity("competition-b-admin-sub", "competition_b_admin");
    grantRole(globalAdmin, "ADMINISTRADOR_GERAL", null);

    competitionA = createCompetition("Competição A", "competicao-a");
    grantRole(competitionAAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);

    competitionB = createCompetition("Competição B", "competicao-b");
    grantRole(competitionBAdmin, "ADMINISTRADOR_COMPETICAO", competitionB);

    editionA = createEdition(competitionA, "Edição A", "edicao-a");

    sportA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportA,
        "Futsal",
        "futsal");
    jdbcTemplate.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportA,
        competitionA);

    categoryA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryA,
        editionA,
        "Livre",
        "livre");

    organizationA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        organizationA,
        "Atlética A",
        "atletica-a");

    team1 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        team1,
        organizationA,
        editionA,
        sportA,
        categoryA,
        "Engenharia",
        "engenharia");

    team2 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        team2,
        organizationA,
        editionA,
        sportA,
        categoryA,
        "Medicina",
        "medicina");
  }

  @Test
  @DisplayName(
      "Ciclo completo de evento: RASCUNHO -> PUBLICADO -> SUSPENSO -> ADIADO -> PUBLICADO -> CANCELADO")
  void fullEventLifecycleTransitionsAndSnapshots() throws Exception {
    // 1. Criar evento em RASCUNHO
    String createJson =
        String.format(
            """
                {
                  "title": "Engenharia vs Medicina - Final",
                  "format": "HEAD_TO_HEAD",
                  "scheduledAt": "2026-09-15T18:00:00Z",
                  "zoneId": "America/Sao_Paulo",
                  "venueState": "LOCAL_A_DEFINIR",
                  "sourceReference": "https://boletim.ufu.br/edital/1",
                  "sportId": "%s",
                  "categoryId": "%s",
                  "slots": [
                    { "slotNo": 1, "displayLabel": "Engenharia", "teamId": "%s" },
                    { "slotNo": 2, "displayLabel": "Medicina", "teamId": "%s" }
                  ]
                }
                """,
            sportA, categoryA, team1, team2);

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/editions/" + editionA + "/events")
                    .with(oidcPrincipal("competition-a-admin-sub"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("RASCUNHO"))
            .andExpect(jsonPath("$.lockVersion").value(0))
            .andExpect(jsonPath("$.slots[0].displayLabel").value("Engenharia"))
            .andExpect(jsonPath("$.slots[1].displayLabel").value("Medicina"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID eventId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createResponse, "$.eventId"));

    Map<String, Object> initialLifecycle =
        jdbcTemplate.queryForMap(
            """
                    SELECT previous_status, new_status, actor_identity_id, source_reference,
                           base_lock_version, lock_version, sequence_no
                    FROM public.sports_event_lifecycle_history
                    WHERE event_id = ? AND sequence_no = 1
                    """,
            eventId);
    assertThat(initialLifecycle.get("previous_status")).isNull();
    assertThat(initialLifecycle.get("new_status")).isEqualTo("RASCUNHO");
    assertThat(initialLifecycle.get("actor_identity_id")).isNotNull();
    assertThat(initialLifecycle.get("source_reference"))
        .isEqualTo("https://boletim.ufu.br/edital/1");
    assertThat(((Number) initialLifecycle.get("base_lock_version")).longValue()).isZero();
    assertThat(((Number) initialLifecycle.get("lock_version")).longValue()).isZero();
    assertThat(((Number) initialLifecycle.get("sequence_no")).longValue()).isEqualTo(1L);

    // 2. Atualizar rascunho com optimistic locking
    String updateJson =
        String.format(
            """
                {
                  "title": "Engenharia vs Medicina - Grande Final",
                  "format": "HEAD_TO_HEAD",
                  "scheduledAt": "2026-09-15T19:00:00Z",
                  "zoneId": "America/Sao_Paulo",
                  "venueState": "LOCAL_A_DEFINIR",
                  "sourceReference": "https://boletim.ufu.br/edital/1-rev",
                  "sportId": "%s",
                  "categoryId": "%s",
                  "slots": [
                    { "slotNo": 1, "displayLabel": "Engenharia UFU", "teamId": "%s" },
                    { "slotNo": 2, "displayLabel": "Medicina UFU", "teamId": "%s" }
                  ]
                }
                """,
            sportA, categoryA, team1, team2);

    mockMvc
        .perform(
            put("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Engenharia vs Medicina - Grande Final"))
        .andExpect(jsonPath("$.lockVersion").value(1));

    // Versão stale retorna 409
    mockMvc
        .perform(
            put("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
        .andExpect(status().isConflict());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_event_lifecycle_history WHERE event_id = ?",
                Integer.class,
                eventId))
        .isEqualTo(1);

    // 3. Publicar evento (RASCUNHO -> PUBLICADO)
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLICADO"))
        .andExpect(jsonPath("$.lockVersion").value(2));

    // Snapshot imutável de publicação criado
    Integer pubCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_event_publication WHERE event_id = ?",
            Integer.class,
            eventId);
    assertThat(pubCount).isEqualTo(1);

    // 4. Suspender evento (PUBLICADO -> SUSPENSO)
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/suspend")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                { "reason": "Forte chuva na quadra externa" }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENSO"))
        .andExpect(jsonPath("$.statusReason").value("Forte chuva na quadra externa"))
        .andExpect(jsonPath("$.lockVersion").value(3));

    // 5. Adiar evento (SUSPENSO -> ADIADO)
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/postpone")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "scheduledAt": "2026-09-20T14:00:00Z",
                                  "zoneId": "America/Sao_Paulo",
                                  "reason": "Reagendado para o próximo sábado"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ADIADO"))
        .andExpect(jsonPath("$.statusReason").value("Reagendado para o próximo sábado"))
        .andExpect(jsonPath("$.lockVersion").value(4));

    // 6. Re-publicar evento (ADIADO -> PUBLICADO)
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "4"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLICADO"))
        .andExpect(jsonPath("$.lockVersion").value(5));

    // 7. Cancelar evento (PUBLICADO -> CANCELADO)
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/cancel")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "5")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                { "reason": "Cancelamento oficial determinado pela comissão disciplinar" }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELADO"))
        .andExpect(
            jsonPath("$.statusReason")
                .value("Cancelamento oficial determinado pela comissão disciplinar"))
        .andExpect(jsonPath("$.lockVersion").value(6));

    // 8. Estado CANCELADO é terminal e rejeita novas transições
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "6"))
        .andExpect(status().isBadRequest());

    List<Map<String, Object>> lifecycleRows =
        jdbcTemplate.queryForList(
            """
                    SELECT previous_status, new_status, base_lock_version, lock_version, sequence_no
                    FROM public.sports_event_lifecycle_history
                    WHERE event_id = ?
                    ORDER BY sequence_no ASC, occurred_at ASC, lifecycle_id ASC
                    """,
            eventId);
    assertThat(lifecycleRows).hasSize(6);
    assertThat(lifecycleRows.stream().map(row -> row.get("previous_status")).toList())
        .containsExactly(null, "RASCUNHO", "PUBLICADO", "SUSPENSO", "ADIADO", "PUBLICADO");
    assertThat(lifecycleRows.stream().map(row -> row.get("new_status")).toList())
        .containsExactly("RASCUNHO", "PUBLICADO", "SUSPENSO", "ADIADO", "PUBLICADO", "CANCELADO");
    assertThat(
            lifecycleRows.stream()
                .map(row -> ((Number) row.get("base_lock_version")).longValue())
                .toList())
        .containsExactly(0L, 1L, 2L, 3L, 4L, 5L);
    assertThat(
            lifecycleRows.stream()
                .map(row -> ((Number) row.get("lock_version")).longValue())
                .toList())
        .containsExactly(0L, 2L, 3L, 4L, 5L, 6L);

    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId + "/lifecycle")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(6))
        .andExpect(jsonPath("$[0].previousStatus").doesNotExist())
        .andExpect(jsonPath("$[0].newStatus").value("RASCUNHO"))
        .andExpect(jsonPath("$[1].previousStatus").value("RASCUNHO"))
        .andExpect(jsonPath("$[5].newStatus").value("CANCELADO"))
        .andExpect(jsonPath("$[0].actorIdentityId").exists())
        .andExpect(jsonPath("$[0].issuer").doesNotExist())
        .andExpect(jsonPath("$[0].subject").doesNotExist())
        .andExpect(jsonPath("$[0].nickname").doesNotExist());

    UUID lifecycleId =
        jdbcTemplate.queryForObject(
            "SELECT lifecycle_id FROM public.sports_event_lifecycle_history WHERE event_id = ? LIMIT 1",
            UUID.class,
            eventId);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_event_lifecycle_history SET reason = 'hacked' WHERE lifecycle_id = ?",
                    lifecycleId))
        .hasMessageContaining("sports event lifecycle history is immutable");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_event_lifecycle_history WHERE lifecycle_id = ?",
                    lifecycleId))
        .hasMessageContaining("sports event lifecycle history is immutable");
  }

  @Test
  @DisplayName("Consulta de lifecycle valida limite, autenticação, escopo e evento")
  void lifecycleEndpointValidatesLimitAndScope() throws Exception {
    UUID eventId = createAndPublishEvent();
    String lifecyclePath = "/api/v1/operations/sports/events/" + eventId + "/lifecycle";

    mockMvc
        .perform(
            get(lifecyclePath).with(oidcPrincipal("competition-a-admin-sub")).param("limit", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mockMvc
        .perform(
            get(lifecyclePath).with(oidcPrincipal("competition-a-admin-sub")).param("limit", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));

    mockMvc
        .perform(
            get(lifecyclePath).with(oidcPrincipal("competition-a-admin-sub")).param("limit", "0"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(lifecyclePath).with(oidcPrincipal("competition-a-admin-sub")).param("limit", "501"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            get(lifecyclePath)
                .with(oidcPrincipal("competition-a-admin-sub"))
                .param("limit", "not-a-number"))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(get(lifecyclePath).with(oidcPrincipal("competition-b-admin-sub")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(get(lifecyclePath).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + UUID.randomUUID() + "/lifecycle")
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Evento GENERIC pode ser publicado, mas nao aceita ciclo de placar")
  void genericEventRejectsDetailedResultWorkflow() throws Exception {
    UUID eventId = createAndPublishEvent("GENERIC");

    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/results/inform")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "sourceReference": "https://boletim.ufu.br/generico",
                                  "scores": [
                                    { "slotNo": 1, "score": 1 },
                                    { "slotNo": 2, "score": 0 }
                                  ]
                                }
                                """))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId + "/results")
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Ciclo completo de resultados: INFORMADO -> AGUARDANDO_REVISAO -> CONFIRMADO -> CORRIGIDO e auditoria")
  void fullResultLifecycleAndAuditability() throws Exception {
    // Cria e publica evento
    UUID eventId = createAndPublishEvent();

    // 1. Informar rascunho de placar (INFORMADO) - NÃO publica placar no evento
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/results/inform")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "sourceReference": "https://boletim.ufu.br/previa/1",
                                  "scores": [
                                    { "slotNo": 1, "score": 3 },
                                    { "slotNo": 2, "score": 1 }
                                  ]
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("INFORMADO"))
        .andExpect(jsonPath("$.versionNo").value(1))
        .andExpect(jsonPath("$.scores[0].score").value(3))
        .andExpect(jsonPath("$.scores[1].score").value(1));

    // Evento ainda não tem confirmedVersionId
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmedVersionId").doesNotExist())
        .andExpect(jsonPath("$.lockVersion").value(2));

    // 2. Enviar para revisão (AGUARDANDO_REVISAO) - NÃO publica placar no evento
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/results/review")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "sourceReference": "https://boletim.ufu.br/sumula/1",
                                  "scores": [
                                    { "slotNo": 1, "score": 3 },
                                    { "slotNo": 2, "score": 2 }
                                  ]
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("AGUARDANDO_REVISAO"))
        .andExpect(jsonPath("$.versionNo").value(2));

    // 3. Confirmar resultado (CONFIRMADO) - atualiza confirmedVersionId no evento
    String confirmResponse =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/events/" + eventId + "/results/confirm")
                    .with(oidcPrincipal("competition-a-admin-sub"))
                    .with(csrf())
                    .param("expectedLockVersion", "3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "sourceReference": "https://boletim.ufu.br/boletim-oficial/12",
                                  "scores": [
                                    { "slotNo": 1, "score": 3 },
                                    { "slotNo": 2, "score": 2 }
                                  ]
                                }
                                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMADO"))
            .andExpect(jsonPath("$.versionNo").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID confirmedVersionId =
        UUID.fromString(com.jayway.jsonpath.JsonPath.read(confirmResponse, "$.versionId"));

    // Evento agora tem confirmedVersionId apontando para a versão confirmada
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmedVersionId").value(confirmedVersionId.toString()))
        .andExpect(jsonPath("$.lockVersion").value(4));

    // 4. Corrigir resultado confirmado (CORRIGIDO com motivo obrigatório)
    // Sem motivo -> 400
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/results/correct")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "reason": "",
                                  "sourceReference": "https://boletim.ufu.br/errata/1",
                                  "scores": [
                                    { "slotNo": 1, "score": 2 },
                                    { "slotNo": 2, "score": 2 }
                                  ]
                                }
                                """))
        .andExpect(status().isBadRequest());

    // Com motivo -> Sucesso
    String correctResponse =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/events/" + eventId + "/results/correct")
                    .with(oidcPrincipal("competition-a-admin-sub"))
                    .with(csrf())
                    .param("expectedLockVersion", "4")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "reason": "Retificação de súmula oficial após conferência dos mesários",
                                  "sourceReference": "https://boletim.ufu.br/errata/1",
                                  "scores": [
                                    { "slotNo": 1, "score": 2 },
                                    { "slotNo": 2, "score": 2 }
                                  ]
                                }
                                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CORRIGIDO"))
            .andExpect(jsonPath("$.versionNo").value(4))
            .andExpect(
                jsonPath("$.reason")
                    .value("Retificação de súmula oficial após conferência dos mesários"))
            .andExpect(jsonPath("$.supersedesVersionId").value(confirmedVersionId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID correctedVersionId =
        UUID.fromString(com.jayway.jsonpath.JsonPath.read(correctResponse, "$.versionId"));

    // Evento atualizou confirmedVersionId para a nova versão corrigida
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmedVersionId").value(correctedVersionId.toString()))
        .andExpect(jsonPath("$.lockVersion").value(5));

    // 5. Timeline de versões retorna histórico completo ordenado
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId + "/results")
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].versionNo").value(1))
        .andExpect(jsonPath("$[0].status").value("INFORMADO"))
        .andExpect(jsonPath("$[1].versionNo").value(2))
        .andExpect(jsonPath("$[1].status").value("AGUARDANDO_REVISAO"))
        .andExpect(jsonPath("$[2].versionNo").value(3))
        .andExpect(jsonPath("$[2].status").value("CONFIRMADO"))
        .andExpect(jsonPath("$[3].versionNo").value(4))
        .andExpect(jsonPath("$[3].status").value("CORRIGIDO"));

    // 6. Imutabilidade: versões gravadas não podem ser alteradas ou excluídas diretamente no banco
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_result_version SET source_reference = 'hacked' WHERE version_id = ?",
                    confirmedVersionId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_result_version WHERE version_id = ?",
                    confirmedVersionId))
        .hasMessageContaining("snapshots are immutable");
  }

  @Test
  @DisplayName(
      "Autorização: ADMINISTRADOR_GERAL acessa qualquer competição; ADMINISTRADOR_COMPETICAO acessa apenas seu escopo")
  void authorizationRulesEnforced() throws Exception {
    UUID eventId = createAndPublishEvent();

    // Admin da Competição B tenta acessar evento da Competição A -> 403
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("competition-b-admin-sub")))
        .andExpect(status().isForbidden());

    // Admin da Competição B tenta alterar evento da Competição A -> 403
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/suspend")
                .with(oidcPrincipal("competition-b-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                { "reason": "Tentativa indevida" }
                                """))
        .andExpect(status().isForbidden());

    // Admin Geral acessa qualquer evento -> 200
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId)
                .with(oidcPrincipal("global-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.eventId").value(eventId.toString()));

    // Não autenticado -> 401
    mockMvc
        .perform(
            get("/api/v1/operations/sports/events/" + eventId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  private UUID createAndPublishEvent() throws Exception {
    return createAndPublishEvent("HEAD_TO_HEAD");
  }

  private UUID createAndPublishEvent(String format) throws Exception {
    String createJson =
        String.format(
            """
                {
                  "title": "Jogo Teste",
                  "format": "%s",
                  "scheduledAt": "2026-09-15T18:00:00Z",
                  "zoneId": "America/Sao_Paulo",
                  "venueState": "LOCAL_A_DEFINIR",
                  "sourceReference": "https://boletim.ufu.br/edital/1",
                  "sportId": "%s",
                  "categoryId": "%s",
                  "slots": [
                    { "slotNo": 1, "displayLabel": "Time 1", "teamId": "%s" },
                    { "slotNo": 2, "displayLabel": "Time 2", "teamId": "%s" }
                  ]
                }
                """,
            format, sportA, categoryA, team1, team2);

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/editions/" + editionA + "/events")
                    .with(oidcPrincipal("competition-a-admin-sub"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createJson))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID eventId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(createResponse, "$.eventId"));

    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0"))
        .andExpect(status().isOk());

    return eventId;
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

  private void grantRole(UUID identityId, String role, UUID scope) {
    if (scope == null) {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, ?, 'VIGENTE')",
          identityId,
          role);
    } else {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
          identityId,
          role,
          scope);
    }
  }

  private UUID createCompetition(String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition (competition_id, name, slug) VALUES (?, ?, ?)",
        id,
        name,
        slug);
    return id;
  }

  private UUID createEdition(UUID compId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
        id,
        compId,
        name,
        slug);
    return id;
  }

  private RequestPostProcessor oidcPrincipal(String subject) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        new OidcIdToken(
            "sports-event-result-token-" + subject,
            now,
            now.plusSeconds(3600),
            Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, subject));
    Collection<GrantedAuthority> authorities = List.of(new OidcUserAuthority(idToken));
    OidcUser oidcUser =
        new DefaultOidcUser(authorityContributor.mapAuthorities(authorities), idToken);
    return oidcLogin().oidcUser(oidcUser);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-sports-event-result-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri(ISSUER)
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Sports Event Result Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
