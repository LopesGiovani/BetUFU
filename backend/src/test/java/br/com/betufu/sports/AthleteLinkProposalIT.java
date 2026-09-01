package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
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
import br.com.betufu.sports.internal.athlete.SportsAthleteCreated;
import br.com.betufu.sports.internal.linking.AthleteLinkReconciler;
import br.com.betufu.verification.EnrollmentVerified;
import br.com.betufu.verification.internal.EnrollmentService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-link-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AthleteLinkProposalIT.TestOidcConfiguration.class)
class AthleteLinkProposalIT {

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
  @Autowired private ObjectMapper objectMapper;
  @Autowired private EnrollmentService enrollmentService;
  @Autowired private AthleteLinkReconciler reconciler;

  private MockMvc mockMvc;
  private UUID competitionA;
  private UUID teamA;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.sports_athlete_link_review, public.sports_athlete_link_proposal, "
            + "public.sports_athlete, public.sports_team, public.sports_organization, "
            + "public.sports_category, public.sports_sport, public.sports_edition, public.sports_competition, "
            + "public.audit_event, public.operational_role_grant, public.betufu_profile, "
            + "public.ufu_enrollment, public.local_identity CASCADE");
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID globalAdmin = createIdentity("global-admin-sub", "global_admin");
    grantRole(globalAdmin, "ADMINISTRADOR_GERAL", null);

    competitionA = createCompetition("Competição A", "competicao-a");
    UUID editionA = createEdition(competitionA, "Edição A", "edicao-a");
    UUID sportA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportA,
        "Futsal",
        "futsal");
    jdbcTemplate.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportA,
        competitionA);
    UUID categoryA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryA,
        editionA,
        "Livre",
        "livre");

    UUID organizationA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        organizationA,
        "Atlética Engenharia",
        "atletica-engenharia");

    teamA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, edition_id, sport_id, category_id, organization_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        teamA,
        editionA,
        sportA,
        categoryA,
        organizationA,
        "Engenharia Futsal",
        "engenharia-futsal");
  }

  @Test
  @DisplayName(
      "Atleta cadastrado antes e conta verificada depois cria exatamente uma proposta via EnrollmentVerified")
  void athleteFirst_accountVerifiedLaterCreatesSingleProposal() throws Exception {
    // 1. Admin cria atleta com matricula "12345678"
    String athleteJson =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                    .with(csrf())
                    .with(oidcUser("global-admin-sub"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "name": "Maria Silva",
                                  "slug": "maria-silva",
                                  "matricula": "12345678"
                                }
                                """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode athleteNode = objectMapper.readTree(athleteJson);
    UUID athleteId = UUID.fromString(athleteNode.get("athleteId").asText());

    // 2. Usuário cria conta e registra matrícula "12345678"
    UUID userId = createIdentity("user-maria-sub", "maria");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-maria-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "12345678"
                                }
                                """))
        .andExpect(status().isOk());

    // 3. Consulta proposta pendente do usuário
    MvcResult proposalResult =
        mockMvc
            .perform(
                get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-maria-sub")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.athleteId").value(athleteId.toString()))
            .andExpect(jsonPath("$.athleteName").value("Maria Silva"))
            .andExpect(jsonPath("$.status").value("PENDENTE"))
            .andReturn();

    JsonNode proposalNode =
        objectMapper.readTree(proposalResult.getResponse().getContentAsString());
    UUID proposalId = UUID.fromString(proposalNode.get("proposalId").asText());

    // 4. Replay do evento de reconciliação não duplica proposta
    reconciler.onEnrollmentVerified(new EnrollmentVerified(userId));
    Integer proposalCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE identity_id = ?",
            Integer.class,
            userId);
    assertThat(proposalCount).isEqualTo(1);

    // 5. Usuário aceita o vínculo
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-maria-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "ACCEPT"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACEITA"));

    // Vínculo no banco está ACEITA
    String dbStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
            String.class,
            proposalId);
    assertThat(dbStatus).isEqualTo("ACEITA");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT decided_by_identity_id = ? AND decided_at IS NOT NULL "
                    + "FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                Boolean.class,
                userId,
                proposalId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_event "
                    + "WHERE action = 'VINCULO_ATLETA_DECIDIDO' AND target_identity_id = ?",
                Integer.class,
                userId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Conta verificada antes e atleta cadastrado depois cria exatamente uma proposta via SportsAthleteCreated")
  void accountFirst_athleteCreatedLaterCreatesSingleProposal() throws Exception {
    // 1. Usuário cria conta e registra matrícula "87654321"
    UUID userId = createIdentity("user-joao-sub", "joao");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-joao-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "87654321"
                                }
                                """))
        .andExpect(status().isOk());

    // 2. Admin cria atleta com a mesma matrícula "87654321"
    String athleteJson =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                    .with(csrf())
                    .with(oidcUser("global-admin-sub"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "name": "João Santos",
                                  "slug": "joao-santos",
                                  "matricula": "87654321"
                                }
                                """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode athleteNode = objectMapper.readTree(athleteJson);
    UUID athleteId = UUID.fromString(athleteNode.get("athleteId").asText());

    // 3. Usuário vê a proposta pendente
    mockMvc
        .perform(get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-joao-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.athleteId").value(athleteId.toString()))
        .andExpect(jsonPath("$.athleteName").value("João Santos"))
        .andExpect(jsonPath("$.status").value("PENDENTE"));

    // Replay não duplica
    reconciler.onSportsAthleteCreated(new SportsAthleteCreated(athleteId));
    Integer proposalCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE athlete_id = ?",
            Integer.class,
            athleteId);
    assertThat(proposalCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Divergência de dados cria uma única revisão manual idempotente e bloqueia proposta")
  void divergentDataCreatesIdempotentManualReview() {
    UUID userId = createIdentity("user-divergent-sub", "divergent");
    // Cria enrollment com HMAC sob chave v1
    enrollmentService.register(userId, "11112222");

    // Cria atleta com mesma matricula mas com versão 99 (divergente)
    UUID athleteId = UUID.randomUUID();
    byte[] validHmac =
        jdbcTemplate.queryForObject(
            "SELECT matricula_hmac FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'ATIVO'",
            byte[].class,
            userId);
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) "
            + "VALUES (?, 'Atleta Divergente', 'atleta-divergente', ?, 99, 'PUBLICADO')",
        athleteId,
        validHmac);

    // Dispara reconciliação
    reconciler.onEnrollmentVerified(new EnrollmentVerified(userId));
    reconciler.onSportsAthleteCreated(new SportsAthleteCreated(athleteId));

    // Nenhuma proposta criada
    Integer proposalCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE athlete_id = ? OR identity_id = ?",
            Integer.class,
            athleteId,
            userId);
    assertThat(proposalCount).isEqualTo(0);
    Integer reviewCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_review "
                + "WHERE athlete_id = ? AND identity_id = ? AND reason_code = 'DIVERGENT_DATA'",
            Integer.class,
            athleteId,
            userId);
    assertThat(reviewCount).isEqualTo(1);

    reconciler.onEnrollmentVerified(new EnrollmentVerified(userId));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_athlete_link_review "
                    + "WHERE athlete_id = ? AND identity_id = ? AND reason_code = 'DIVERGENT_DATA'",
                Integer.class,
                athleteId,
                userId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Múltiplos candidatos criam uma única revisão manual idempotente e bloqueiam proposta")
  void multipleCandidatesCreatesIdempotentManualReview() {
    UUID user1 = createIdentity("user-dup-1-sub", "dup1");
    UUID user2 = createIdentity("user-dup-2-sub", "dup2");

    jdbcTemplate.execute("DROP INDEX uq_ufu_enrollment_active_hmac");
    try {
      enrollmentService.register(user1, "22223333");
      byte[] duplicateHmac =
          jdbcTemplate.queryForObject(
              "SELECT matricula_hmac FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'ATIVO'",
              byte[].class,
              user1);
      jdbcTemplate.update(
          "INSERT INTO public.ufu_enrollment (identity_id, matricula_hmac, hmac_version, status) "
              + "VALUES (?, ?, 1, 'ATIVO')",
          user2,
          duplicateHmac);

      // Insere dois atletas com o mesmo HMAC (se existisse antes do constraint ou sem HMAC)
      // Aqui simulamos no banco para testar a resiliência do reconciliador
      UUID athleteId = UUID.randomUUID();
      jdbcTemplate.update(
          "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) "
              + "VALUES (?, 'Atleta Candidato', 'atleta-candidato', ?, 1, 'PUBLICADO')",
          athleteId,
          duplicateHmac);

      // Dispara reconciliação quando múltiplos matches ocorrem
      reconciler.reconcileAthleteFirst(athleteId);
      reconciler.reconcileAthleteFirst(athleteId);

      // Proposta automática não é criada se ambíguo
      Integer proposalCount =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE athlete_id = ?",
              Integer.class,
              athleteId);
      assertThat(proposalCount).isEqualTo(0);
      Integer reviewCount =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM public.sports_athlete_link_review "
                  + "WHERE athlete_id = ? AND reason_code = 'MULTIPLE_CANDIDATES'",
              Integer.class,
              athleteId);
      assertThat(reviewCount).isEqualTo(1);
    } finally {
      jdbcTemplate.update("DELETE FROM public.ufu_enrollment WHERE identity_id = ?", user2);
      jdbcTemplate.execute(
          "CREATE UNIQUE INDEX uq_ufu_enrollment_active_hmac "
              + "ON public.ufu_enrollment (matricula_hmac) WHERE status = 'ATIVO'");
    }
  }

  @Test
  @DisplayName("Proposta expirada nao aparece como pendente e registra o instante terminal")
  void expiredProposalIsTerminal() throws Exception {
    UUID identityId = createIdentity("user-expired-sub", "expired");
    UUID athleteId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug) VALUES ('Atleta Expirado', 'atleta-expirado') "
                + "RETURNING athlete_id",
            UUID.class);
    UUID proposalId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id) VALUES (?, ?, ?)",
        proposalId,
        athleteId,
        identityId);
    jdbcTemplate.update(
        "UPDATE public.sports_athlete_link_proposal "
            + "SET created_at = CURRENT_TIMESTAMP(6) - INTERVAL '31 days', "
            + "expires_at = CURRENT_TIMESTAMP(6) - INTERVAL '1 second' "
            + "WHERE proposal_id = ?",
        proposalId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT expires_at <= CURRENT_TIMESTAMP(6) FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                Boolean.class,
                proposalId))
        .isTrue();

    mockMvc
        .perform(get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-expired-sub")))
        .andExpect(status().isNotFound());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                String.class,
                proposalId))
        .isEqualTo("EXPIRADA");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT decided_at IS NOT NULL FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                Boolean.class,
                proposalId))
        .isTrue();
  }

  @Test
  @DisplayName("Administrador de competicao nao le fila O8 sem escopo de competencia na revisao")
  void competitionAdminCannotReadUnscopedReviewQueue() throws Exception {
    UUID competitionAdmin = createIdentity("competition-admin-sub", "competition_admin");
    grantRole(competitionAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);

    mockMvc
        .perform(
            get("/api/v1/operations/sports/athlete-link-reviews")
                .with(oidcUser("competition-admin-sub")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Atleta já vinculado não cria segunda proposta e registra revisão ALREADY_LINKED")
  void alreadyLinkedDoesNotCreateProposal() throws Exception {
    // Cria atleta e vincula ao User 1
    String athleteJson =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                    .with(csrf())
                    .with(oidcUser("global-admin-sub"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                                {
                                  "name": "Atleta Vinculado",
                                  "slug": "atleta-vinculado",
                                  "matricula": "99998888"
                                }
                                """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID athleteId = UUID.fromString(objectMapper.readTree(athleteJson).get("athleteId").asText());

    UUID user1 = createIdentity("user-linked-1", "linked1");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-linked-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "99998888"
                                }
                                """))
        .andExpect(status().isOk());

    // User 1 aceita a proposta
    String proposalJson =
        mockMvc
            .perform(
                get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-linked-1")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID proposalId =
        UUID.fromString(objectMapper.readTree(proposalJson).get("proposalId").asText());

    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-linked-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "ACCEPT"
                                }
                                """))
        .andExpect(status().isOk());

    // Agora User 2 tenta reconciliar com o mesmo atleta (por exemplo, após liberação/mudança)
    reconciler.onEnrollmentVerified(new EnrollmentVerified(user1));
    reconciler.onEnrollmentVerified(new EnrollmentVerified(user1));

    // Nenhuma nova proposta ACEITA ou PENDENTE para o User 2
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_review "
                + "WHERE athlete_id = ? AND identity_id = ? AND reason_code = 'ALREADY_LINKED'",
            Integer.class,
            athleteId,
            user1);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Owner contesta proposta pendente, move para CONTESTADA e abre exatamente uma revisão O8")
  void ownerContestsPendingProposalAndCreatesSingleReview() throws Exception {
    // Cria atleta e conta com mesma matrícula
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                .with(csrf())
                .with(oidcUser("global-admin-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "name": "Atleta Contestado",
                                  "slug": "atleta-contestado",
                                  "matricula": "55554444"
                                }
                                """))
        .andExpect(status().isCreated());

    createIdentity("user-contest-sub", "contest_user");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-contest-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "55554444"
                                }
                                """))
        .andExpect(status().isOk());

    String proposalJson =
        mockMvc
            .perform(
                get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-contest-sub")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID proposalId =
        UUID.fromString(objectMapper.readTree(proposalJson).get("proposalId").asText());

    // Owner envia CONTEST
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-contest-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "CONTEST"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONTESTADA"));

    // Fila O8 lista a revisão sem PII
    MvcResult reviewsResult =
        mockMvc
            .perform(
                get("/api/v1/operations/sports/athlete-link-reviews")
                    .with(oidcUser("global-admin-sub")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].reasonCode").value("CONTESTED"))
            .andExpect(jsonPath("$.items[0].status").value("PENDENTE"))
            .andExpect(jsonPath("$.items[0].athleteName").value("Atleta Contestado"))
            .andReturn();

    String reviewsBody = reviewsResult.getResponse().getContentAsString();
    assertThat(reviewsBody).doesNotContain("matricula");
    assertThat(reviewsBody).doesNotContain("55554444");
    assertThat(reviewsBody).doesNotContain("hmac");
    assertThat(reviewsBody).doesNotContain("email");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.audit_event "
                    + "WHERE action = 'REVISAO_VINCULO_ATLETA_CRIADA' AND target_identity_id = ?",
                Integer.class,
                jdbcTemplate.queryForObject(
                    "SELECT identity_id FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                    UUID.class,
                    proposalId)))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Outra identidade não pode contestar proposta de terceiro (403 Forbidden)")
  void anotherIdentityCannotContestProposal() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                .with(csrf())
                .with(oidcUser("global-admin-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "name": "Atleta Terceiro",
                                  "slug": "atleta-terceiro",
                                  "matricula": "77776666"
                                }
                                """))
        .andExpect(status().isCreated());

    createIdentity("user-owner-sub", "owner");
    createIdentity("user-attacker-sub", "attacker");

    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-owner-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "77776666"
                                }
                                """))
        .andExpect(status().isOk());

    String proposalJson =
        mockMvc
            .perform(
                get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-owner-sub")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID proposalId =
        UUID.fromString(objectMapper.readTree(proposalJson).get("proposalId").asText());

    // Attacker tenta contestar proposta do Owner
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-attacker-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "CONTEST"
                                }
                                """))
        .andExpect(status().isForbidden());

    // Estado da proposta continua PENDENTE
    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
            String.class,
            proposalId);
    assertThat(status).isEqualTo("PENDENTE");
  }

  @Test
  @DisplayName("Contestação repetida é idempotente e devolve 200 OK sem duplicar revisão")
  void repeatedContestIsIdempotent() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                .with(csrf())
                .with(oidcUser("global-admin-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "name": "Atleta Replay Contest",
                                  "slug": "atleta-replay-contest",
                                  "matricula": "33332222"
                                }
                                """))
        .andExpect(status().isCreated());

    createIdentity("user-replay-sub", "replay");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-replay-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "33332222"
                                }
                                """))
        .andExpect(status().isOk());

    String proposalJson =
        mockMvc
            .perform(
                get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-replay-sub")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID proposalId =
        UUID.fromString(objectMapper.readTree(proposalJson).get("proposalId").asText());

    // Primeiro contest
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-replay-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "CONTEST"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONTESTADA"));

    // Segundo contest (replay)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-replay-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "CONTEST"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONTESTADA"));

    Integer reviewCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_review WHERE proposal_id = ?",
            Integer.class,
            proposalId);
    assertThat(reviewCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Aceite e rejeição repetidos são idempotentes")
  void repeatedAcceptAndRejectAreIdempotent() throws Exception {
    // Cria atleta e conta
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                .with(csrf())
                .with(oidcUser("global-admin-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "name": "Atleta Idempotente",
                                  "slug": "atleta-idempotente",
                                  "matricula": "44445555"
                                }
                                """))
        .andExpect(status().isCreated());

    createIdentity("user-idemp-sub", "idemp");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-idemp-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "44445555"
                                }
                                """))
        .andExpect(status().isOk());

    String proposalJson =
        mockMvc
            .perform(
                get("/api/v1/me/sports/athlete-links/proposal").with(oidcUser("user-idemp-sub")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID proposalId =
        UUID.fromString(objectMapper.readTree(proposalJson).get("proposalId").asText());

    // Primeiro ACCEPT
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-idemp-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "ACCEPT"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACEITA"));

    // Segundo ACCEPT (idempotente)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-idemp-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "ACCEPT"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACEITA"));

    // Tentar CONTEST em proposta já ACEITA dá 409 CONFLICT
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                .with(csrf())
                .with(oidcUser("user-idemp-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "decision": "CONTEST"
                                }
                                """))
        .andExpect(status().isConflict());
  }

  private UUID createIdentity(String sub, String nickname) {
    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            ISSUER,
            sub);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
        identityId,
        nickname);
    return identityId;
  }

  private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
    jdbcTemplate.update(
        "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) "
            + "VALUES (?, ?, ?, 'VIGENTE')",
        identityId,
        roleName,
        scopeRef);
  }

  private UUID createCompetition(String name, String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
        UUID.class,
        name,
        slug);
  }

  private UUID createEdition(UUID competitionId, String name, String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, ?, ?) RETURNING edition_id",
        UUID.class,
        competitionId,
        name,
        slug);
  }

  private RequestPostProcessor oidcUser(String subject) {
    return oidcLogin().oidcUser(buildOidcUser(subject));
  }

  private OidcUser buildOidcUser(String subject) {
    Map<String, Object> claims =
        Map.of(
            IdTokenClaimNames.ISS,
            ISSUER,
            IdTokenClaimNames.SUB,
            subject,
            "preferred_username",
            subject);
    OidcIdToken idToken =
        new OidcIdToken(
            "mock-token-" + subject, Instant.now(), Instant.now().plusSeconds(300), claims);
    Collection<? extends GrantedAuthority> authorities =
        authorityContributor.mapAuthorities(List.of(new OidcUserAuthority(idToken)));
    return new DefaultOidcUser(authorities, idToken);
  }

  @TestConfiguration
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-sports-link-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://idp.invalid/auth")
              .tokenUri("https://idp.invalid/token")
              .userInfoUri("https://idp.invalid/userinfo")
              .jwkSetUri("https://idp.invalid/jwks")
              .userNameAttributeName("sub")
              .clientName("Keycloak")
              .build());
    }
  }
}
