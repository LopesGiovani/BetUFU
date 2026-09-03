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
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-roster-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsRosterIT.TestOidcConfiguration.class)
class SportsRosterIT {

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
  private UUID teamA;
  private UUID athlete1;
  private UUID athlete2;
  private UUID athlete3;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_roster_publication_member, public.sports_roster_publication,
                public.sports_roster_member, public.sports_roster, public.sports_athlete,
                public.sports_team, public.sports_organization, public.sports_category,
                public.sports_sport, public.sports_edition, public.sports_competition,
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
        "Atlética Engenharia",
        "atletica-engenharia");

    teamA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        teamA,
        organizationA,
        editionA,
        sportA,
        categoryA,
        "Engenharia Futsal",
        "engenharia-futsal");

    athlete1 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        athlete1,
        "Bruno Atleta",
        "bruno-atleta");

    athlete2 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        athlete2,
        "Alice Atleta",
        "alice-atleta");

    athlete3 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        athlete3,
        "Carlos Atleta",
        "carlos-atleta");

    competitionB = createCompetition("Competição B", "competicao-b");
    grantRole(competitionBAdmin, "ADMINISTRADOR_COMPETICAO", competitionB);
  }

  @Test
  @DisplayName(
      "Criação e edição de rascunho de elenco usam optimistic locking e ordenam membros por nome")
  void createsAndUpdatesRosterDraft() throws Exception {
    // 1. Obter rascunho inicial cria automaticamente se inexistente
    mockMvc
        .perform(
            get("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamId").value(teamA.toString()))
        .andExpect(jsonPath("$.teamName").value("Engenharia Futsal"))
        .andExpect(jsonPath("$.status").value("RASCUNHO"))
        .andExpect(jsonPath("$.lockVersion").value(0))
        .andExpect(jsonPath("$.members").isEmpty());

    // 2. Atualizar rascunho adicionando atletas
    String updatePayload =
        String.format(
            """
                {
                  "members": [
                    { "athleteId": "%s", "jerseyNumber": 10, "positionName": "Ala" },
                    { "athleteId": "%s", "jerseyNumber": 1, "positionName": "Goleira" }
                  ]
                }
                """,
            athlete1, athlete2);

    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockVersion").value(1))
        .andExpect(jsonPath("$.members[0].athleteName").value("Alice Atleta"))
        .andExpect(jsonPath("$.members[0].jerseyNumber").value(1))
        .andExpect(jsonPath("$.members[1].athleteName").value("Bruno Atleta"))
        .andExpect(jsonPath("$.members[1].jerseyNumber").value(10));

    // 3. Atualização concorrente/defasada com lockVersion 0 resulta em 409 Conflict
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Publicação de elenco cria snapshot imutável versão 1 e impede adulteração")
  void publishesRosterAndCreatesImmutableSnapshot() throws Exception {
    // Prepara rascunho com atletas
    String updatePayload =
        String.format(
            """
                {
                  "members": [
                    { "athleteId": "%s", "jerseyNumber": 7, "positionName": "Fixo" }
                  ]
                }
                """,
            athlete3);

    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload))
        .andExpect(status().isOk());

    // Publica elenco
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/" + teamA + "/roster/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNo").value(1))
        .andExpect(jsonPath("$.teamName").value("Engenharia Futsal"))
        .andExpect(jsonPath("$.members[0].athleteName").value("Carlos Atleta"))
        .andExpect(jsonPath("$.members[0].jerseyNumber").value(7))
        .andExpect(jsonPath("$.reason").doesNotExist());

    // Consulta snapshot publicado
    mockMvc
        .perform(
            get("/api/v1/operations/sports/teams/" + teamA + "/roster/publication")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNo").value(1))
        .andExpect(jsonPath("$.members[0].athleteName").value("Carlos Atleta"));

    // O estado do rascunho agora é PUBLICADO e lockVersion avançou
    mockMvc
        .perform(
            get("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLICADO"))
        .andExpect(jsonPath("$.lockVersion").value(2));

    // Um elenco publicado so pode mudar pelo endpoint de correcao versionada.
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 99 }
                                  ]
                                }
                                """,
                        athlete1)))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/api/v1/operations/sports/teams/" + teamA + "/roster/publication")
                .with(oidcPrincipal("competition-a-admin-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNo").value(1))
        .andExpect(jsonPath("$.members[0].athleteName").value("Carlos Atleta"))
        .andExpect(jsonPath("$.members[0].jerseyNumber").value(7));
  }

  @Test
  @DisplayName(
      "Correção de elenco publicado cria nova versão com motivo e preserva histórico imutável")
  void correctsPublishedRosterWithAuditReasonAndVersionHistory() throws Exception {
    // 1. Prepara e publica versão 1
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 10 }
                                  ]
                                }
                                """,
                        athlete1)))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/" + teamA + "/roster/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "1"))
        .andExpect(status().isOk());

    // 2. Correção com motivo cria versão 2
    String correctPayload =
        String.format(
            """
                {
                  "reason": "Substituição de atleta por lesão comprovada em boletim oficial",
                  "members": [
                    { "athleteId": "%s", "jerseyNumber": 10 },
                    { "athleteId": "%s", "jerseyNumber": 11 }
                  ]
                }
                """,
            athlete1, athlete2);

    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/" + teamA + "/roster/correct")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctPayload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionNo").value(2))
        .andExpect(
            jsonPath("$.reason")
                .value("Substituição de atleta por lesão comprovada em boletim oficial"))
        .andExpect(jsonPath("$.members").isArray())
        .andExpect(jsonPath("$.members.length()").value(2));

    // 3. Prova no banco que versão 1 e versão 2 coexistem intactas
    Integer publicationCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_roster_publication WHERE team_id = ?",
            Integer.class,
            teamA);
    assertThat(publicationCount).isEqualTo(2);

    UUID supersedesId =
        jdbcTemplate.queryForObject(
            "SELECT supersedes_publication_id FROM public.sports_roster_publication WHERE team_id = ? AND version_no = 2",
            UUID.class,
            teamA);
    UUID version1Id =
        jdbcTemplate.queryForObject(
            "SELECT publication_id FROM public.sports_roster_publication WHERE team_id = ? AND version_no = 1",
            UUID.class,
            teamA);
    assertThat(supersedesId).isEqualTo(version1Id);

    // 4. Tentativa de correção sem motivo falha com 400
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/" + teamA + "/roster/correct")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "reason": "   ",
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 10 }
                                  ]
                                }
                                """,
                        athlete1)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Operações de elenco rejeitam acesso fora do escopo da competição (IDOR)")
  void rejectsUnauthorizedOrCrossCompetitionAccess() throws Exception {
    Integer rosterCountBefore =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.sports_roster", Integer.class);

    // Administrador da competição B tenta atualizar elenco da equipe da competição A
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-b-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 10 }
                                  ]
                                }
                                """,
                        athlete1)))
        .andExpect(status().isForbidden());

    // Administrador da competição B tenta publicar elenco da equipe da competição A
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/" + teamA + "/roster/publish")
                .with(oidcPrincipal("competition-b-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/operations/sports/teams/" + teamA + "/roster/publication")
                .with(oidcPrincipal("competition-b-admin-sub")))
        .andExpect(status().isForbidden());

    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM public.sports_roster", Integer.class))
        .isEqualTo(rosterCountBefore);
  }

  @Test
  @DisplayName("Projeções de elenco excluem matrícula, HMAC ou PII")
  void protectsSensitiveDataAndExcludesMatriculaOrHmac() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 5, "positionName": "Ala" }
                                  ]
                                }
                                """,
                        athlete1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members[0].matricula").doesNotExist())
        .andExpect(jsonPath("$.members[0].matriculaHmac").doesNotExist())
        .andExpect(jsonPath("$.members[0].hmac").doesNotExist())
        .andExpect(jsonPath("$.members[0].email").doesNotExist());
  }

  @Test
  @DisplayName("Validação rejeita atleta inexistente ou atletas duplicados no request")
  void requiresValidDomainAndReferences() throws Exception {
    UUID fakeAthlete = UUID.randomUUID();
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 5 }
                                  ]
                                }
                                """,
                        fakeAthlete)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamA + "/roster")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 5 },
                                    { "athleteId": "%s", "jerseyNumber": 7 }
                                  ]
                                }
                                """,
                        athlete1, athlete1)))
        .andExpect(status().isBadRequest());
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

  private UUID createEdition(UUID competitionId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
        id,
        competitionId,
        name,
        slug);
    return id;
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

  private RequestPostProcessor oidcPrincipal(String subject) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        new OidcIdToken(
            "sports-roster-token-" + subject,
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
              .clientId("betufu-sports-roster-it")
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
              .clientName("BETUFU Sports Roster Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
