package br.com.betufu.sports;

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
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCorrectRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCreateRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketMatchConfigureRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketPublishRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSlotConfigureRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSourceType;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketUpdateRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-bracket-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsBracketIT.TestOidcConfiguration.class)
class SportsBracketIT {

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
  @Autowired private DataSource dataSource;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private WebApplicationContext applicationContext;
  @Autowired private CorrelationIdFilter correlationIdFilter;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy springSecurityFilterChain;

  @Autowired private BetufuRoleAuthorityContributor authorityContributor;
  @Autowired private ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private UUID competitionA;
  private UUID competitionB;
  private UUID editionA;
  private UUID editionB;
  private UUID sportA;
  private UUID categoryA;
  private UUID phaseA;
  private UUID event1;
  private UUID event2;
  private UUID event3;
  private UUID event4;
  private UUID team1;
  private UUID team2;
  private UUID team3;
  private UUID team4;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_bracket_publication_node, public.sports_bracket_publication,
                public.sports_bracket_match_slot, public.sports_bracket_match, public.sports_bracket,
                public.sports_result_version_slot, public.sports_result_version, public.sports_event_slot,
                public.sports_event_publication_slot, public.sports_event_publication, public.sports_event,
                public.sports_roster_publication_member, public.sports_roster_publication,
                public.sports_roster_member, public.sports_roster, public.sports_athlete,
                public.sports_team, public.sports_organization, public.sports_phase,
                public.sports_category, public.sports_sport, public.sports_edition, public.sports_competition,
                public.audit_event, public.operational_role_grant, public.betufu_profile,
                public.ufu_enrollment, public.local_identity CASCADE
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
    editionA = createEdition(competitionA, "Edição A", "edicao-a");

    competitionB = createCompetition("Competição B", "competicao-b");
    grantRole(competitionBAdmin, "ADMINISTRADOR_COMPETICAO", competitionB);
    editionB = createEdition(competitionB, "Edição B", "edicao-b");

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
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition_sport (competition_id, sport_id) VALUES (?, ?)",
        competitionA,
        sportA);

    categoryA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryA,
        editionA,
        "Livre",
        "livre");

    phaseA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_phase (phase_id, edition_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?)",
        phaseA,
        editionA,
        categoryA,
        "Eliminatória",
        "eliminatoria");

    UUID org1 = UUID.randomUUID();
    UUID org2 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug) VALUES (?, ?, ?)",
        org1,
        "Atlética Engenharia",
        "eng");
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug) VALUES (?, ?, ?)",
        org2,
        "Atlética Medicina",
        "med");

    team1 = UUID.randomUUID();
    team2 = UUID.randomUUID();
    team3 = UUID.randomUUID();
    team4 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
        team1,
        org1,
        editionA,
        sportA,
        categoryA,
        "Engenharia Futsal",
        "eng-futsal");
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
        team2,
        org2,
        editionA,
        sportA,
        categoryA,
        "Medicina Futsal",
        "med-futsal");
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
        team3,
        org1,
        editionA,
        sportA,
        categoryA,
        "Engenharia B",
        "eng-b");
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
        team4,
        org2,
        editionA,
        sportA,
        categoryA,
        "Medicina B",
        "med-b");

    event1 =
        createEvent(
            competitionA,
            editionA,
            "Semifinal 1: Engenharia x Medicina",
            team1,
            "Engenharia Futsal",
            team2,
            "Medicina Futsal");
    event2 =
        createEvent(
            competitionA,
            editionA,
            "Semifinal 2: Engenharia B x Medicina B",
            team3,
            "Engenharia B",
            team4,
            "Medicina B");
    event3 = createEvent(competitionA, editionA, "Final", null, "A definir", null, "A definir");
    event4 =
        createEvent(
            competitionA, editionA, "Disputa de 3º Lugar", null, "A definir", null, "A definir");
  }

  @Test
  @DisplayName(
      "Cria, configura, deriva resultados confirmados e publica chaveamento no ciclo completo")
  void fullBracketLifecycleDerivationAndPublication() throws Exception {
    // 1. Criar chaveamento em rascunho
    SportsBracketCreateRequest createReq =
        new SportsBracketCreateRequest(
            "Chaveamento Futsal", "chaveamento-futsal", sportA, categoryA, phaseA);

    String createJson =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/editions/{editionId}/brackets", editionA)
                    .with(user("competition-a-admin-sub"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Chaveamento Futsal"))
            .andExpect(jsonPath("$.status").value("RASCUNHO"))
            .andExpect(jsonPath("$.lockVersion").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID bracketId = UUID.fromString(objectMapper.readTree(createJson).get("bracketId").asText());

    // 2. Rascunho não aparece no endpoint público P7
    mockMvc
        .perform(get("/api/v1/public/sports/brackets/{bracketId}", bracketId))
        .andExpect(status().isNotFound());

    // 3. Configurar grafo de confrontos
    UUID match1Id = UUID.randomUUID();
    UUID match2Id = UUID.randomUUID();
    UUID match3Id = UUID.randomUUID(); // Final
    UUID match4Id = UUID.randomUUID(); // 3º Lugar

    SportsBracketMatchConfigureRequest m1 =
        new SportsBracketMatchConfigureRequest(
            match1Id,
            1,
            1,
            "Semifinais",
            event1,
            false,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.DIRECT, null, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.DIRECT, null, null)));

    SportsBracketMatchConfigureRequest m2 =
        new SportsBracketMatchConfigureRequest(
            match2Id,
            1,
            2,
            "Semifinais",
            event2,
            false,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.DIRECT, null, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.DIRECT, null, null)));

    SportsBracketMatchConfigureRequest m3 =
        new SportsBracketMatchConfigureRequest(
            match3Id,
            2,
            1,
            "Final",
            event3,
            false,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.PREDECESSOR_WINNER, match1Id, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.PREDECESSOR_WINNER, match2Id, null)));

    SportsBracketMatchConfigureRequest m4 =
        new SportsBracketMatchConfigureRequest(
            match4Id,
            2,
            2,
            "Disputa de 3º Lugar",
            event4,
            true,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.PREDECESSOR_LOSER, match1Id, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.PREDECESSOR_LOSER, match2Id, null)));

    SportsBracketUpdateRequest updateReq =
        new SportsBracketUpdateRequest(
            "Chaveamento Futsal",
            "chaveamento-futsal",
            sportA,
            categoryA,
            phaseA,
            List.of(m1, m2, m3, m4));

    mockMvc
        .perform(
            put("/api/v1/operations/sports/brackets/{bracketId}", bracketId)
                .param("expectedLockVersion", "0")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds").isArray())
        .andExpect(jsonPath("$.rounds.length()").value(2))
        .andExpect(
            jsonPath("$.rounds[1].matches[0].slots[0].displayLabel")
                .value("Vencedor de Semifinal 1: Engenharia x Medicina"));

    // 4. Confirmar resultado do event1 (Engenharia vence Medicina por 3x1)
    confirmResult(event1, 3, 1);

    // 5. Verificar projeção derivada (o vencedor propaga sem editar chaveamento)
    mockMvc
        .perform(
            get("/api/v1/operations/sports/brackets/{bracketId}", bracketId)
                .with(user("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds[0].matches[0].slots[0].score").value(3))
        .andExpect(jsonPath("$.rounds[0].matches[0].slots[1].score").value(1))
        .andExpect(jsonPath("$.rounds[0].matches[0].winnerSlotNo").value(1))
        .andExpect(
            jsonPath("$.rounds[1].matches[0].slots[0].displayLabel").value("Engenharia Futsal"))
        .andExpect(
            jsonPath("$.rounds[1].matches[1].slots[0].displayLabel").value("Medicina Futsal"));

    // 6. Publicar chaveamento com fonte oficial
    SportsBracketPublishRequest pubReq =
        new SportsBracketPublishRequest("https://proae.ufu.br/boletim-05");

    mockMvc
        .perform(
            post("/api/v1/operations/sports/brackets/{bracketId}/publish", bracketId)
                .param("expectedLockVersion", "1")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pubReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLICADO"))
        .andExpect(jsonPath("$.versionNo").value(1))
        .andExpect(jsonPath("$.sourceReference").value("https://proae.ufu.br/boletim-05"));

    // 7. Agora P7 retorna a versão publicada
    mockMvc
        .perform(get("/api/v1/public/sports/brackets/{bracketId}", bracketId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bracketId").value(bracketId.toString()))
        .andExpect(jsonPath("$.status").value("PUBLICADO"))
        .andExpect(jsonPath("$.versionNo").value(1))
        .andExpect(jsonPath("$.rounds[0].matches[0].slots[0].score").value(3));

    jdbcTemplate.update(
        "UPDATE public.sports_event SET status = 'RASCUNHO' WHERE event_id = ?", event1);
    mockMvc
        .perform(get("/api/v1/public/sports/brackets/{bracketId}", bracketId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds[0].matches[0].eventTitle").doesNotExist())
        .andExpect(jsonPath("$.rounds[0].matches[0].slots[0].displayLabel").value("A definir"));

    // 8. Published graph is immutable; corrections use the versioned endpoint.
    mockMvc
        .perform(
            put("/api/v1/operations/sports/brackets/{bracketId}", bracketId)
                .param("expectedLockVersion", "2")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            post("/api/v1/operations/sports/brackets/{bracketId}/publish", bracketId)
                .param("expectedLockVersion", "2")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pubReq)))
        .andExpect(status().isBadRequest());

    // 9. Corrigir chaveamento criando versão 2
    SportsBracketCorrectRequest correctReq =
        new SportsBracketCorrectRequest(
            "Ajuste da fonte oficial", "https://proae.ufu.br/boletim-05-retificado", null);

    mockMvc
        .perform(
            post("/api/v1/operations/sports/brackets/{bracketId}/correct", bracketId)
                .param("expectedLockVersion", "2")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(correctReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLICADO"))
        .andExpect(jsonPath("$.versionNo").value(2))
        .andExpect(
            jsonPath("$.sourceReference").value("https://proae.ufu.br/boletim-05-retificado"));
  }

  @Test
  @DisplayName("Validação de grafo rejeita ciclos e dependências impossíveis")
  void rejectsCyclesAndInvalidDependencies() throws Exception {
    UUID sportFromOtherCompetition = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportFromOtherCompetition,
        "Basquete",
        "basquete-outro");
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition_sport (competition_id, sport_id) VALUES (?, ?)",
        competitionB,
        sportFromOtherCompetition);

    SportsBracketCreateRequest foreignSportRequest =
        new SportsBracketCreateRequest(
            "Modalidade alheia",
            "modalidade-alheia",
            sportFromOtherCompetition,
            categoryA,
            phaseA);
    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/{editionId}/brackets", editionA)
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(foreignSportRequest)))
        .andExpect(status().isNotFound());

    SportsBracketCreateRequest createReq =
        new SportsBracketCreateRequest("Chave com Ciclo", "chave-ciclo", sportA, categoryA, phaseA);

    String createJson =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/editions/{editionId}/brackets", editionA)
                    .with(user("competition-a-admin-sub"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    UUID bracketId = UUID.fromString(objectMapper.readTree(createJson).get("bracketId").asText());

    UUID m1Id = UUID.randomUUID();
    UUID m2Id = UUID.randomUUID();

    // Ciclo real entre rodadas diferentes: m1 depende de m2, e m2 depende de m1.
    SportsBracketMatchConfigureRequest c1 =
        new SportsBracketMatchConfigureRequest(
            m1Id,
            2,
            1,
            "R2",
            null,
            false,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.PREDECESSOR_WINNER, m2Id, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.DIRECT, null, null)));

    SportsBracketMatchConfigureRequest c2 =
        new SportsBracketMatchConfigureRequest(
            m2Id,
            1,
            2,
            "R1",
            null,
            false,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.PREDECESSOR_WINNER, m1Id, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.DIRECT, null, null)));

    SportsBracketUpdateRequest cycleUpdate =
        new SportsBracketUpdateRequest(
            "Chave com Ciclo", "chave-ciclo", sportA, categoryA, phaseA, List.of(c1, c2));

    mockMvc
        .perform(
            put("/api/v1/operations/sports/brackets/{bracketId}", bracketId)
                .param("expectedLockVersion", "0")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cycleUpdate)))
        .andExpect(status().isBadRequest());

    // Evento fora do escopo (evento da Edição B em chave da Edição A)
    UUID eventB = createEvent(competitionB, editionB, "Evento Outro Escopo", null, "A", null, "B");
    SportsBracketMatchConfigureRequest outOfScope =
        new SportsBracketMatchConfigureRequest(
            m1Id,
            1,
            1,
            "R1",
            eventB,
            false,
            List.of(
                new SportsBracketSlotConfigureRequest(
                    1, SportsBracketSourceType.DIRECT, null, null),
                new SportsBracketSlotConfigureRequest(
                    2, SportsBracketSourceType.DIRECT, null, null)));

    SportsBracketUpdateRequest scopeUpdate =
        new SportsBracketUpdateRequest(
            "Chave com Ciclo", "chave-ciclo", sportA, categoryA, phaseA, List.of(outOfScope));

    mockMvc
        .perform(
            put("/api/v1/operations/sports/brackets/{bracketId}", bracketId)
                .param("expectedLockVersion", "0")
                .with(user("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(scopeUpdate)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Administrador de outra competição recebe 403 Forbidden")
  void enforcesScopeAuthorization() throws Exception {
    SportsBracketCreateRequest createReq =
        new SportsBracketCreateRequest("Chave A", "chave-a", sportA, categoryA, phaseA);

    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/{editionId}/brackets", editionA)
                .with(user("competition-b-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
        .andExpect(status().isForbidden());
  }

  private void confirmResult(UUID eventId, int score1, int score2) {
    UUID identityId =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE subject = 'global-admin-sub'",
            UUID.class);
    UUID resultVersionId = UUID.randomUUID();
    new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        .executeWithoutResult(
            status -> {
              jdbcTemplate.update(
                  """
                    INSERT INTO public.sports_result_version (
                        version_id, event_id, competition_id, edition_id, version_no, status,
                        source_reference, recorded_by_identity_id, confirmed_by_identity_id,
                        confirmed_at, base_lock_version
                    ) VALUES (?, ?, ?, ?, 1, 'CONFIRMADO', 'https://boletim.ufu.br', ?, ?, CURRENT_TIMESTAMP(6), 0)
                    """,
                  resultVersionId,
                  eventId,
                  competitionA,
                  editionA,
                  identityId,
                  identityId);

              jdbcTemplate.update(
                  "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 1, ?, 'Slot 1')",
                  resultVersionId,
                  score1);
              jdbcTemplate.update(
                  "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 2, ?, 'Slot 2')",
                  resultVersionId,
                  score2);

              jdbcTemplate.update(
                  "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
                  resultVersionId,
                  eventId);
            });
  }

  private UUID createEvent(
      UUID compId, UUID edId, String title, UUID t1, String label1, UUID t2, String label2) {
    UUID eventId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_event (
                    event_id, competition_id, edition_id, title, event_format, status,
                    scheduled_at, zone_id, venue_state, source_reference, lock_version
                ) VALUES (?, ?, ?, ?, 'HEAD_TO_HEAD', 'PUBLICADO', CURRENT_TIMESTAMP(6), 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://betufu.ufu.br', 0)
                """,
        eventId,
        compId,
        edId,
        title);

    jdbcTemplate.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 1, ?, ?)",
        eventId,
        label1,
        t1);
    jdbcTemplate.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 2, ?, ?)",
        eventId,
        label2,
        t2);

    return eventId;
  }

  private UUID createIdentity(String subject, String nickname) {
    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            ISSUER,
            subject);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
        identityId,
        nickname);
    return identityId;
  }

  private UUID createCompetition(String name, String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
        UUID.class,
        name,
        slug);
  }

  private UUID createEdition(UUID compId, String name, String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, ?, ?) RETURNING edition_id",
        UUID.class,
        compId,
        name,
        slug);
  }

  private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
    jdbcTemplate.update(
        "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
        identityId,
        roleName,
        scopeRef);
  }

  private RequestPostProcessor user(String subject) {
    OidcIdToken idToken =
        new OidcIdToken(
            "mock-token-value",
            Instant.now(),
            Instant.now().plusSeconds(3600),
            Map.of(
                IdTokenClaimNames.ISS, ISSUER,
                IdTokenClaimNames.SUB, subject));
    Collection<GrantedAuthority> baseAuthorities = List.of(new OidcUserAuthority(idToken));
    Collection<? extends GrantedAuthority> authorities =
        authorityContributor.mapAuthorities(baseAuthorities);
    OidcUser oidcUser = new DefaultOidcUser(authorities, idToken);
    return oidcLogin().oidcUser(oidcUser);
  }

  @TestConfiguration
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-sports-bracket-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://idp.invalid/auth")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/certs")
              .userNameAttributeName("sub")
              .clientName("Keycloak")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
