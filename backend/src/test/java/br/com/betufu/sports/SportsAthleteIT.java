package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-sports-athlete-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsAthleteIT.TestOidcConfiguration.class)
class SportsAthleteIT {

    private static final String ISSUER = "https://idp.invalid";
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse(
                    "docker.io/library/postgres:18.4-trixie@sha256:"
                            + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
            .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_app")
            .withUsername("betufu_app")
            .withPassword("betufu-test-only")
            .withInitScript("postgres-audit-runtime-role.sql");

    @Container
    static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerProperties(registry);
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private WebApplicationContext applicationContext;
    @Autowired private CorrelationIdFilter correlationIdFilter;
    @Autowired @Qualifier("springSecurityFilterChain") private FilterChainProxy springSecurityFilterChain;
    @Autowired private BetufuRoleAuthorityContributor authorityContributor;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private UUID competitionA;
    private UUID competitionB;
    private UUID editionA;
    private UUID sportA;
    private UUID categoryA;
    private UUID organizationA;
    private UUID teamA;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.sports_athlete, public.sports_team, public.sports_organization, "
                        + "public.sports_category, public.sports_sport, public.sports_edition, public.sports_competition, "
                        + "public.audit_event, public.operational_role_grant, public.betufu_profile, "
                        + "public.ufu_enrollment, public.local_identity CASCADE");
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();

        UUID globalAdmin = createIdentity("global-admin-sub", "global_admin");
        UUID competitionAAdmin = createIdentity("competition-a-admin-sub", "competition_a_admin");
        UUID competitionBAdmin = createIdentity("competition-b-admin-sub", "competition_b_admin");
        createIdentity("regular-sub", "regular_user");
        grantRole(globalAdmin, "ADMINISTRADOR_GERAL", null);

        competitionA = createCompetition("Competição A", "competicao-a");
        grantRole(competitionAAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);
        editionA = createEdition(competitionA, "Edição A", "edicao-a");
        sportA = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
                sportA, "Futsal", "futsal");
        jdbcTemplate.update("UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
                sportA, competitionA);
        categoryA = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
                categoryA, editionA, "Livre", "livre");

        organizationA = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
                organizationA, "Atlética Engenharia", "atletica-engenharia");

        teamA = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
                teamA, organizationA, editionA, sportA, categoryA, "Engenharia Futsal", "engenharia-futsal");

        competitionB = createCompetition("Competição B", "competicao-b");
        grantRole(competitionBAdmin, "ADMINISTRADOR_COMPETICAO", competitionB);
    }

    @Test
    @DisplayName("Cadastro de atleta deriva competição da equipe, persiste HMAC sem expor matrícula e rejeita escopo divergente")
    void createsAthleteScopedToTeamAndDerivesCompetition() throws Exception {
        // 1. Criação com sucesso sob administrador da competição A
        MvcResult result = mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Carlos Silva\",\"slug\":\"carlos-silva\",\"matricula\":\"12345ABC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Carlos Silva"))
                .andExpect(jsonPath("$.slug").value("carlos-silva"))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.lockVersion").value(0))
                .andExpect(jsonPath("$.matricula").doesNotExist())
                .andExpect(jsonPath("$.matriculaHmac").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(jsonPath("$.fingerprint").doesNotExist())
                .andReturn();

        UUID athleteId = uuid(result, "athleteId");

        // 2. Prova direta de persistência do HMAC de 32 bytes e versão 1 no PostgreSQL
        byte[] hmacBytes = jdbcTemplate.queryForObject(
                "SELECT matricula_hmac FROM public.sports_athlete WHERE athlete_id = ?",
                byte[].class,
                athleteId);
        Short hmacVersion = jdbcTemplate.queryForObject(
                "SELECT hmac_version FROM public.sports_athlete WHERE athlete_id = ?",
                Short.class,
                athleteId);
        assertThat(hmacBytes).isNotNull().hasSize(32);
        assertThat(hmacVersion).isEqualTo((short) 1);

        // 3. Rejeição de escopo divergente (administrador da competição B tentando operar em equipe da competição A)
        mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-b-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Outro Atleta\",\"slug\":\"outro-atleta\",\"matricula\":\"98765XYZ\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Cadastro de atleta rejeita formato inválido de matrícula com 400 Problem Details")
    void rejectsInvalidMatriculaFormat() throws Exception {
        mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Matricula Invalida\",\"slug\":\"matricula-invalida\",\"matricula\":\"@invalida@\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cadastro de atleta sem matrícula é suportado per D-69")
    void createsAthleteWithoutMatricula() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Atleta Sem Matricula\",\"slug\":\"atleta-sem-matricula\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andReturn();

        UUID athleteId = uuid(result, "athleteId");
        byte[] hmacBytes = jdbcTemplate.queryForObject(
                "SELECT matricula_hmac FROM public.sports_athlete WHERE athlete_id = ?",
                byte[].class,
                athleteId);
        assertThat(hmacBytes).isNull();
    }

    @Test
    @DisplayName("Atualização e publicação de atleta usam optimistic locking")
    void updateAndPublishUseOptimisticLocking() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nome Original\",\"slug\":\"nome-original\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        UUID athleteId = uuid(result, "athleteId");

        // Atualização bem-sucedida
        mockMvc.perform(put("/api/v1/operations/sports/athletes/" + athleteId)
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .param("expectedLockVersion", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nome Atualizado\",\"slug\":\"nome-atualizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Atualizado"))
                .andExpect(jsonPath("$.lockVersion").value(1));

        // Atualização com lock_version defasada resulta em 409 Conflict
        mockMvc.perform(put("/api/v1/operations/sports/athletes/" + athleteId)
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .param("expectedLockVersion", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Stale\",\"slug\":\"stale\"}"))
                .andExpect(status().isConflict());

        // Publicação bem-sucedida
        mockMvc.perform(post("/api/v1/operations/sports/athletes/" + athleteId + "/publish")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .param("expectedLockVersion", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.lockVersion").value(2));
    }

    @Test
    @DisplayName("Listagem de atletas retorna ordenado por nome e sem expor dados sensíveis")
    void listsAthletesWithoutSensitiveData() throws Exception {
        mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Zulmira Atleta\",\"slug\":\"zulmira-atleta\",\"matricula\":\"11111ABC\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/operations/sports/teams/" + teamA + "/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ana Atleta\",\"slug\":\"ana-atleta\",\"matricula\":\"22222ABC\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/operations/sports/athletes")
                        .with(oidcPrincipal("competition-a-admin-sub"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Ana Atleta"))
                .andExpect(jsonPath("$[1].name").value("Zulmira Atleta"))
                .andExpect(jsonPath("$[0].matricula").doesNotExist())
                .andExpect(jsonPath("$[0].matriculaHmac").doesNotExist())
                .andExpect(jsonPath("$[1].matricula").doesNotExist())
                .andExpect(jsonPath("$[1].matriculaHmac").doesNotExist());
    }

    private UUID createCompetition(String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.sports_competition (competition_id, name, slug) VALUES (?, ?, ?)",
                id, name, slug);
        return id;
    }

    private UUID createEdition(UUID competitionId, String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
                id, competitionId, name, slug);
        return id;
    }

    private UUID createIdentity(String subject, String nickname) {
        jdbcTemplate.update("INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)", ISSUER, subject);
        UUID id = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class, ISSUER, subject);
        jdbcTemplate.update("INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)", id, nickname);
        return id;
    }

    private void grantRole(UUID identityId, String role, UUID scope) {
        if (scope == null) {
            jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, ?, 'VIGENTE')",
                    identityId, role);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
                    identityId, role, scope);
        }
    }

    private UUID uuid(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return UUID.fromString(body.path(field).asText());
    }

    private RequestPostProcessor oidcPrincipal(String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "sports-athlete-token-" + subject,
                now,
                now.plusSeconds(3600),
                Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, subject));
        Collection<GrantedAuthority> authorities = List.of(new OidcUserAuthority(idToken));
        OidcUser oidcUser = new DefaultOidcUser(authorityContributor.mapAuthorities(authorities), idToken);
        return oidcLogin().oidcUser(oidcUser);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-sports-athlete-it")
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
                    .clientName("BETUFU Sports Athlete Test")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
