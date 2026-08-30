package br.com.betufu.sports;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.docker.compose.enabled=false",
        "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
        "BETUFU_OIDC_CLIENT_ID=betufu-sports-catalog-it",
        "BETUFU_OIDC_CLIENT_SECRET=unused",
        "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
        "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
})
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsCatalogIT.TestOidcConfiguration.class)
class SportsCatalogIT {

    private static final String ISSUER = "https://idp.invalid";
    private static final String ROOT = "/api/v1/operations/sports/catalog";
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

    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired WebApplicationContext context;
    @Autowired CorrelationIdFilter correlation;
    @Autowired @Qualifier("springSecurityFilterChain") FilterChainProxy security;
    @Autowired BetufuRoleAuthorityContributor authorities;
    @Autowired ObjectMapper json;

    private MockMvc mockMvc;
    private UUID globalAdmin;
    private UUID competitionAdmin;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE public.sports_sport, public.sports_competition, public.audit_event, "
                + "public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, "
                + "public.local_identity CASCADE");
        try (var connection = redis.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(correlation, security).build();
        globalAdmin = identity("global-admin-sub", "global_admin");
        competitionAdmin = identity("competition-admin-sub", "competition_admin");
        grant(globalAdmin, "ADMINISTRADOR_GERAL", null);
    }

    @Test
    @DisplayName("Bootstrap e O1 devolvem somente competições autorizadas por nome")
    void bootstrapsAndScopesCompetitionsServerSide() throws Exception {
        mockMvc.perform(get(ROOT + "/scope").with(user("global-admin-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bootstrapAllowed").value(true))
                .andExpect(jsonPath("$.competitions").isEmpty());

        UUID competitionA = createCompetition("Olimpíada UFU", "olimpiada-ufu");
        UUID competitionB = createCompetition("Intercursos", "intercursos");
        grant(competitionAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);

        mockMvc.perform(get(ROOT + "/scope").with(user("competition-admin-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bootstrapAllowed").value(false))
                .andExpect(jsonPath("$.competitions.length()").value(1))
                .andExpect(jsonPath("$.competitions[0].competitionId").value(competitionA.toString()))
                .andExpect(jsonPath("$.competitions[0].name").value("Olimpíada UFU"));

        mockMvc.perform(get(ROOT + "/competitions/" + competitionB).with(user("competition-admin-sub")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Catálogo hierárquico usa recurso persistido como autoridade e local reutilizável")
    void createsCatalogAndRejectsCrossScopeResourceIds() throws Exception {
        UUID competitionA = createCompetition("Olimpíada UFU", "olimpiada-ufu");
        UUID competitionB = createCompetition("Intercursos", "intercursos");
        grant(competitionAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);

        UUID edition = uuid(mockMvc.perform(post(ROOT + "/competitions/" + competitionA + "/editions")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Edição 2026\",\"slug\":\"2026\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isCreated()).andReturn(), "editionId");
        UUID category = uuid(mockMvc.perform(post(ROOT + "/editions/" + edition + "/categories")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Masculino\",\"slug\":\"masculino\"}"))
                .andExpect(status().isCreated()).andReturn(), "categoryId");
        mockMvc.perform(post(ROOT + "/categories/" + category + "/phases")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Grupos\",\"slug\":\"grupos\"}"))
                .andExpect(status().isCreated());
        UUID venue = uuid(mockMvc.perform(post(ROOT + "/competitions/" + competitionA + "/venues")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Centro Esportivo UFU\",\"address\":\"Campus Santa Mônica\"}"))
                .andExpect(status().isCreated()).andReturn(), "venueId");
        mockMvc.perform(post(ROOT + "/venues/" + venue + "/spaces")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ginásio 05\"}"))
                .andExpect(status().isCreated());

        UUID otherEdition = directEdition(competitionB, "Edição B", "edicao-b");
        mockMvc.perform(post(ROOT + "/editions/" + otherEdition + "/categories")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Inválida\",\"slug\":\"invalida\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(ROOT + "/competitions/" + competitionA).with(user("competition-admin-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editions[0].name").value("Edição 2026"))
                .andExpect(jsonPath("$.categories[0].name").value("Masculino"))
                .andExpect(jsonPath("$.phases[0].name").value("Grupos"))
                .andExpect(jsonPath("$.venues[0].spaces[0].name").value("Ginásio 05"));
    }

    @Test
    @DisplayName("Optimistic locking preserva a primeira alteração e devolve 409 para versão stale")
    void rejectsStaleVenueUpdateWithoutOverwrite() throws Exception {
        UUID competition = createCompetition("Olimpíada UFU", "olimpiada-ufu");
        grant(competitionAdmin, "ADMINISTRADOR_COMPETICAO", competition);
        UUID venue = uuid(mockMvc.perform(post(ROOT + "/competitions/" + competition + "/venues")
                        .with(user("competition-admin-sub")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ginásio\"}"))
                .andExpect(status().isCreated()).andReturn(), "venueId");

        mockMvc.perform(put(ROOT + "/venues/" + venue).with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ginásio Central\",\"expectedLockVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockVersion").value(1));

        mockMvc.perform(put(ROOT + "/venues/" + venue).with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sobrescrita\",\"expectedLockVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BETUFU-409-STALE_VERSION"));

        mockMvc.perform(get(ROOT + "/competitions/" + competition).with(user("competition-admin-sub")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venues[0].name").value("Ginásio Central"));
    }

    @Test
    @DisplayName("Evento catalogado preserva LOCAL_DEFINIDO e filtros publicos usam snapshots reais")
    void createsCatalogedEventAndFiltersPublicAgenda() throws Exception {
        UUID competition = createCompetition("Olimpiada Catalogada", "olimpiada-catalogada");
        grant(competitionAdmin, "ADMINISTRADOR_COMPETICAO", competition);

        UUID sport = uuid(mockMvc.perform(post(ROOT + "/competitions/" + competition + "/sports")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Volei\",\"slug\":\"volei\"}"))
                .andExpect(status().isCreated()).andReturn(), "sportId");
        UUID edition = uuid(mockMvc.perform(post(ROOT + "/competitions/" + competition + "/editions")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Edicao 2026\",\"slug\":\"2026\"}"))
                .andExpect(status().isCreated()).andReturn(), "editionId");
        UUID category = uuid(mockMvc.perform(post(ROOT + "/editions/" + edition + "/categories")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Misto\",\"slug\":\"misto\"}"))
                .andExpect(status().isCreated()).andReturn(), "categoryId");
        UUID phase = uuid(mockMvc.perform(post(ROOT + "/categories/" + category + "/phases")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Fase final\",\"slug\":\"fase-final\"}"))
                .andExpect(status().isCreated()).andReturn(), "phaseId");
        UUID venue = uuid(mockMvc.perform(post(ROOT + "/competitions/" + competition + "/venues")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ginasio Central\"}"))
                .andExpect(status().isCreated()).andReturn(), "venueId");
        UUID space = uuid(mockMvc.perform(post(ROOT + "/venues/" + venue + "/spaces")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Quadra A\"}"))
                .andExpect(status().isCreated()).andReturn(), "venueSpaceId");

        UUID event = uuid(mockMvc.perform(post("/api/v1/operations/sports/editions/" + edition + "/events")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"
                                + "\"title\":\"Volei final\",\"format\":\"HEAD_TO_HEAD\","
                                + "\"scheduledAt\":\"2026-08-22T16:00:00Z\","
                                + "\"zoneId\":\"America/Sao_Paulo\","
                                + "\"venueState\":\"LOCAL_DEFINIDO\","
                                + "\"sourceReference\":\"https://example.invalid/boletim-volei\","
                                + "\"slots\":[{\"slotNo\":1,\"displayLabel\":\"UFU A\"},"
                                + "{\"slotNo\":2,\"displayLabel\":\"UFU B\"}],"
                                + "\"sportId\":\"" + sport + "\","
                                + "\"categoryId\":\"" + category + "\","
                                + "\"phaseId\":\"" + phase + "\","
                                + "\"venueId\":\"" + venue + "\","
                                + "\"venueSpaceId\":\"" + space + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sportId").value(sport.toString()))
                .andExpect(jsonPath("$.categoryId").value(category.toString()))
                .andExpect(jsonPath("$.phaseId").value(phase.toString()))
                .andExpect(jsonPath("$.venueState").value("LOCAL_DEFINIDO"))
                .andExpect(jsonPath("$.venueSpaceId").value(space.toString()))
                .andReturn(), "eventId");

        mockMvc.perform(post("/api/v1/operations/sports/events/" + event + "/publications")
                        .with(user("competition-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sportName").value("Volei"))
                .andExpect(jsonPath("$.venueName").value("Ginasio Central"));

        mockMvc.perform(get("/api/v1/public/sports/events")
                        .param("sportId", sport.toString())
                        .param("categoryId", category.toString())
                        .param("phaseId", phase.toString())
                        .param("state", "LOCAL_DEFINIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value(event.toString()))
                .andExpect(jsonPath("$.items[0].sportName").value("Volei"))
                .andExpect(jsonPath("$.items[0].categoryName").value("Misto"))
                .andExpect(jsonPath("$.items[0].phaseName").value("Fase final"))
                .andExpect(jsonPath("$.items[0].venueState").value("LOCAL_DEFINIDO"))
                .andExpect(jsonPath("$.items[0].venueSpaceName").value("Quadra A"));
    }

    private UUID createCompetition(String name, String slug) throws Exception {
        return uuid(mockMvc.perform(post(ROOT + "/competitions").with(user("global-admin-sub")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated()).andReturn(), "competitionId");
    }

    private UUID directEdition(UUID competition, String name, String slug) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
                id, competition, name, slug);
        return id;
    }

    private UUID uuid(MvcResult result, String field) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());
        return UUID.fromString(body.path(field).asText());
    }

    private UUID identity(String subject, String nickname) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, ?, ?)", id, ISSUER, subject);
        jdbc.update("INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)", id, nickname);
        return id;
    }

    private void grant(UUID identity, String role, UUID scope) {
        jdbc.update("INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
                identity, role, scope);
    }

    private RequestPostProcessor user(String subject) {
        Instant now = Instant.now();
        OidcIdToken token = new OidcIdToken("sports-catalog-" + subject, now, now.plusSeconds(3600),
                Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, subject));
        Collection<GrantedAuthority> base = List.of(new OidcUserAuthority(token));
        Collection<? extends GrantedAuthority> mapped = authorities.mapAuthorities(base);
        OidcUser user = new DefaultOidcUser(mapped, token);
        return oidcLogin().oidcUser(user);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-sports-catalog-it").clientSecret("unused")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}").scope("openid")
                    .authorizationUri("https://idp.invalid/authorize").tokenUri("https://idp.invalid/token")
                    .jwkSetUri("https://idp.invalid/jwks").issuerUri(ISSUER)
                    .userNameAttributeName(IdTokenClaimNames.SUB).clientName("BETUFU Sports Catalog Test").build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
