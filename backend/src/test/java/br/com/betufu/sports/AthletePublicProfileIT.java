package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-profile-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AthletePublicProfileIT.TestOidcConfiguration.class)
class AthletePublicProfileIT {

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

  private MockMvc mockMvc;

  private UUID athleteCarlosId;
  private UUID identityCarlosId;
  private UUID athleteAnaId;
  private UUID identityAnaId;
  private UUID identityUnlinkedId;
  private UUID identityAdminId;
  private UUID draftAthleteId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_athlete_public_profile, public.sports_athlete_link_review,
                public.sports_athlete_link_proposal, public.sports_athlete,
                public.operational_role_grant, public.betufu_profile, public.local_identity CASCADE
                """);

    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilter(correlationIdFilter)
            .addFilter(springSecurityFilterChain)
            .build();

    // 1. Carlos atleta publicado e vinculado
    athleteCarlosId = createAthlete("Carlos Silva", "carlos-silva", "PUBLICADO");
    identityCarlosId = createIdentity("carlos-sub", "carlos");
    createAcceptedLink(athleteCarlosId, identityCarlosId);

    // 2. Ana atleta publicada e vinculada
    athleteAnaId = createAthlete("Ana Santos", "ana-santos", "PUBLICADO");
    identityAnaId = createIdentity("ana-sub", "ana");
    createAcceptedLink(athleteAnaId, identityAnaId);

    // 3. Usuário sem vínculo esportivo
    identityUnlinkedId = createIdentity("unlinked-sub", "unlinked");

    // 4. Administrador geral
    identityAdminId = createIdentity("admin-sub", "admin");
    grantRole(identityAdminId, "ADMINISTRADOR_GERAL", null);

    // 5. Atleta em rascunho
    draftAthleteId = createAthlete("Atleta Rascunho", "atleta-rascunho", "RASCUNHO");
  }

  @Test
  @DisplayName(
      "Atleta vinculado consulta perfil próprio sem dados opcionais e recebe 200 com campos nulos")
  void ownerCanGetEmptyProfileWhenLinked() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/sports/athlete-profile").with(oidcUser("carlos-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.athleteId").value(athleteCarlosId.toString()))
        .andExpect(jsonPath("$.athleteName").value("Carlos Silva"))
        .andExpect(jsonPath("$.athleteSlug").value("carlos-silva"))
        .andExpect(jsonPath("$.course").doesNotExist())
        .andExpect(jsonPath("$.bio").doesNotExist())
        .andExpect(jsonPath("$.instagramUrl").doesNotExist())
        .andExpect(jsonPath("$.xUrl").doesNotExist())
        .andExpect(jsonPath("$.linkedinUrl").doesNotExist())
        .andExpect(jsonPath("$.stravaUrl").doesNotExist())
        .andExpect(jsonPath("$.youtubeUrl").doesNotExist())
        .andExpect(jsonPath("$.lockVersion").value(0));
  }

  @Test
  @DisplayName("Atleta vinculado atualiza curso, bio e redes sociais, relê e limpa com sucesso")
  void ownerCanUpdateAndClearProfile() throws Exception {
    // Atualiza perfil inicial
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "course": "Engenharia de Software",
                                  "bio": "Levantador do time de vôlei da UFU.",
                                  "instagramUrl": "https://instagram.com/carlos_ufu",
                                  "xUrl": "https://x.com/carlos_ufu",
                                  "linkedinUrl": "https://www.linkedin.com/in/carlos-ufu",
                                  "stravaUrl": "https://www.strava.com/athletes/123456",
                                  "youtubeUrl": "https://youtube.com/@carlos_ufu",
                                  "lockVersion": 0
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.athleteId").value(athleteCarlosId.toString()))
        .andExpect(jsonPath("$.course").value("Engenharia de Software"))
        .andExpect(jsonPath("$.bio").value("Levantador do time de vôlei da UFU."))
        .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/carlos_ufu"))
        .andExpect(jsonPath("$.xUrl").value("https://x.com/carlos_ufu"))
        .andExpect(jsonPath("$.linkedinUrl").value("https://www.linkedin.com/in/carlos-ufu"))
        .andExpect(jsonPath("$.stravaUrl").value("https://www.strava.com/athletes/123456"))
        .andExpect(jsonPath("$.youtubeUrl").value("https://youtube.com/@carlos_ufu"))
        .andExpect(jsonPath("$.lockVersion").value(1));

    // Consulta GET confirma persistência
    mockMvc
        .perform(get("/api/v1/me/sports/athlete-profile").with(oidcUser("carlos-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.course").value("Engenharia de Software"))
        .andExpect(jsonPath("$.bio").value("Levantador do time de vôlei da UFU."))
        .andExpect(jsonPath("$.lockVersion").value(1));

    // Limpa campos opcionais com DELETE
    mockMvc
        .perform(
            delete("/api/v1/me/sports/athlete-profile").with(csrf()).with(oidcUser("carlos-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.course").doesNotExist())
        .andExpect(jsonPath("$.bio").doesNotExist())
        .andExpect(jsonPath("$.instagramUrl").doesNotExist())
        .andExpect(jsonPath("$.lockVersion").value(2));
  }

  @Test
  @DisplayName("Accepts official short Instagram domain")
  void shortInstagramDomainIsAccepted() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instagramUrl\":\"https://instagr.am/carlos_ufu\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.instagramUrl").value("https://instagr.am/carlos_ufu"));
  }

  @Test
  @DisplayName("Edição com versão obsoleta de concorrência retorna 409 STALE_VERSION")
  void optimisticLockingRejectsConcurrentUpdate() throws Exception {
    // Primeira atualização (lockVersion 0 -> 1)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "course": "Medicina",
                                  "bio": "Atleta de atletismo.",
                                  "lockVersion": 0
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockVersion").value(1));

    // Segunda tentativa enviando lockVersion desatualizada (0) retorna 409
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "course": "Direito",
                                  "bio": "Nova bio concorrente.",
                                  "lockVersion": 0
                                }
                                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BETUFU-409-STALE_VERSION"));
  }

  @Test
  @DisplayName("Chamadas anônimas nas rotas owner retornam 401")
  void unauthenticatedCallsReturn401OnOwnerEndpoints() throws Exception {
    mockMvc.perform(get("/api/v1/me/sports/athlete-profile")).andExpect(status().isFound());

    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"course\":\"Teste\"}"))
        .andExpect(status().isFound());

    mockMvc
        .perform(delete("/api/v1/me/sports/athlete-profile").with(csrf()))
        .andExpect(status().isFound());
  }

  @Test
  @DisplayName("Usuário autenticado sem vínculo esportivo aceito recebe 404 nas rotas owner")
  void userWithoutLinkedAthleteReceives404OnOwnerEndpoints() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/sports/athlete-profile").with(oidcUser("unlinked-sub")))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("unlinked-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"course\":\"Teste\"}"))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete("/api/v1/me/sports/athlete-profile").with(csrf()).with(oidcUser("unlinked-sub")))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Administrador ou outro atleta não podem alterar perfil de terceiros via rota owner")
  void adminOrOtherUserCannotUpdateAthleteProfile() throws Exception {
    // Admin geral sem vínculo não pode alterar via owner endpoint
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("admin-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"course\":\"Admin Course\"}"))
        .andExpect(status().isNotFound());

    // Ana atualiza seu próprio perfil
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("ana-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"course\":\"Arquitetura\",\"bio\":\"Futsal feminino\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.athleteId").value(athleteAnaId.toString()))
        .andExpect(jsonPath("$.course").value("Arquitetura"));

    // Perfil do Carlos continua intocado
    mockMvc
        .perform(get("/api/v1/me/sports/athlete-profile").with(oidcUser("carlos-sub")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.athleteId").value(athleteCarlosId.toString()))
        .andExpect(jsonPath("$.course").doesNotExist());
  }

  @Test
  @DisplayName("Validação rejeita XSS na bio/curso e URLs maliciosas/fora da allowlist")
  void validationRejectsXssAndMaliciousUrls() throws Exception {
    // XSS na biografia (<script>)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"<script>alert('xss')</script>\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // XSS na biografia (<iframe>)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"<iframe src='https://malicious.invalid'></iframe>\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // HTML no curso (<b>)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"course\":\"<b>Ciência da Computação</b>\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // Protocolo não-HTTPS (http://)
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instagramUrl\":\"http://instagram.com/carlos\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // Protocolo javascript:
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instagramUrl\":\"javascript:alert(1)\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // Credenciais no userinfo da URL
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instagramUrl\":\"https://user:pass@instagram.com/carlos\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // Host não permitido na allowlist
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instagramUrl\":\"https://phishing-site.invalid/carlos\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // URL sem path de perfil
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"instagramUrl\":\"https://instagram.com/\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

    // Limite de tamanho de bio (> 500 caracteres)
    String longBio = "A".repeat(501);
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bio\":\"" + longBio + "\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Endpoint público retorna perfil do atleta publicado sem vazar PII ou internal IDs")
  void publicEndpointReturnsPublishedProfileWithoutPii() throws Exception {
    // Carlos configura seu perfil
    mockMvc
        .perform(
            put("/api/v1/me/sports/athlete-profile")
                .with(csrf())
                .with(oidcUser("carlos-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "course": "Engenharia Mecânica",
                                  "bio": "Atleta de basquete.",
                                  "instagramUrl": "https://instagram.com/carlos_mecanica"
                                }
                                """))
        .andExpect(status().isOk());

    // Consulta pública anônima
    MvcResult publicResult =
        mockMvc
            .perform(get("/api/v1/public/sports/athletes/{athleteId}/profile", athleteCarlosId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.athleteId").value(athleteCarlosId.toString()))
            .andExpect(jsonPath("$.athleteName").value("Carlos Silva"))
            .andExpect(jsonPath("$.athleteSlug").value("carlos-silva"))
            .andExpect(jsonPath("$.course").value("Engenharia Mecânica"))
            .andExpect(jsonPath("$.bio").value("Atleta de basquete."))
            .andExpect(jsonPath("$.instagramUrl").value("https://instagram.com/carlos_mecanica"))
            .andReturn();

    String responseBody = publicResult.getResponse().getContentAsString();
    assertThat(responseBody).doesNotContain("matricula");
    assertThat(responseBody).doesNotContain("hmac");
    assertThat(responseBody).doesNotContain(identityCarlosId.toString());
    assertThat(responseBody).doesNotContain("carlos-sub");
  }

  @Test
  @DisplayName("Endpoint público retorna 404 para atleta em rascunho ou inexistente")
  void publicEndpointReturns404ForDraftOrNonExistentAthlete() throws Exception {
    mockMvc
        .perform(get("/api/v1/public/sports/athletes/{athleteId}/profile", draftAthleteId))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/v1/public/sports/athletes/{athleteId}/profile", UUID.randomUUID()))
        .andExpect(status().isNotFound());
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

  private UUID createAthlete(String name, String slug, String status) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_athlete (name, slug, status) VALUES (?, ?, ?) RETURNING athlete_id",
        UUID.class,
        name,
        slug,
        status);
  }

  private void createAcceptedLink(UUID athleteId, UUID identityId) {
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_athlete_link_proposal (
                    athlete_id, identity_id, status, decided_by_identity_id, decided_at
                ) VALUES (?, ?, 'ACEITA', ?, CURRENT_TIMESTAMP(6))
                """,
        athleteId,
        identityId,
        identityId);
  }

  private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
    jdbcTemplate.update(
        "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
        identityId,
        roleName,
        scopeRef);
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
              .clientId("betufu-sports-profile-it")
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
