package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-sports-tracer-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsTracerIT.TestOidcConfiguration.class)
class SportsTracerIT {

    private static final String ISSUER = "https://idp.invalid";
    private static final String COMPETITIONS = "/api/v1/operations/sports/competitions";
    private static final String ROLE_GRANTS = "/api/v1/operations/roles/grants";
    private static final String PUBLIC_EVENTS = "/api/v1/public/sports/events";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private BetufuRoleAuthorityContributor authorityContributor;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private UUID globalAdminId;
    private UUID competitionAdminId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.sports_competition, public.audit_event, "
                        + "public.operational_role_grant, public.betufu_profile, "
                        + "public.ufu_enrollment, public.local_identity CASCADE");
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();

        globalAdminId = createIdentity("global-admin-sub", "global_admin");
        competitionAdminId = createIdentity("competition-admin-sub", "competition_admin");
        createIdentity("regular-sub", "regular_user");
        grantRoleDirect(globalAdminId, "ADMINISTRADOR_GERAL", null);
    }

    @Test
    @DisplayName("Tracer completo cria escopo, publica snapshot e permite leitura anonima")
    void completesScopedPublicationTracer() throws Exception {
        UUID competitionId = createCompetition("Olimpiada Universitaria UFU", "olimpiada-ufu");
        UUID editionId = createEdition(competitionId, "Edicao 2026", "2026");

        mockMvc.perform(post(ROLE_GRANTS)
                        .with(oidcPrincipal("global-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + competitionAdminId
                                + "\",\"role\":\"ADMINISTRADOR_COMPETICAO\",\"scopeRef\":\""
                                + competitionId + "\"}"))
                .andExpect(status().isOk());

        UUID eventId = createEvent(editionId, "competition-admin-sub", "Direito x Computacao");

        mockMvc.perform(get("/api/v1/public/sports/events/" + eventId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/operations/sports/events/" + eventId + "/publications")
                        .with(oidcPrincipal("competition-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.competitionId").value(competitionId.toString()))
                .andExpect(jsonPath("$.versionNo").value(1));

        MvcResult publicResult = mockMvc.perform(get("/api/v1/public/sports/events/" + eventId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.competitionId").value(competitionId.toString()))
                .andExpect(jsonPath("$.editionId").value(editionId.toString()))
                .andExpect(jsonPath("$.title").value("Direito x Computacao"))
                .andExpect(jsonPath("$.format").value("HEAD_TO_HEAD"))
                .andExpect(jsonPath("$.venueState").value("LOCAL_A_DEFINIR"))
                .andExpect(jsonPath("$.slots.length()").value(2))
                .andExpect(jsonPath("$.publishedByIdentityId").doesNotExist())
                .andExpect(jsonPath("$.baseLockVersion").doesNotExist())
                .andReturn();

        JsonNode publicBody = objectMapper.readTree(publicResult.getResponse().getContentAsByteArray());
        assertThat(publicBody.size())
                .as("the anonymous DTO must contain only the approved public fields")
                .isEqualTo(16);
    }

    @Test
    @DisplayName("Agenda publica lista somente publicados com filtros e paginação estáveis")
    void listsPublishedEventsWithStableFiltersAndPagination() throws Exception {
        UUID competitionA = createCompetition("Competicao Agenda A", "competicao-agenda-a");
        UUID editionA = createEdition(competitionA, "Edicao Agenda A", "edicao-agenda-a");
        UUID competitionB = createCompetition("Competicao Agenda B", "competicao-agenda-b");
        UUID editionB = createEdition(competitionB, "Edicao Agenda B", "edicao-agenda-b");
        grantRoleDirect(competitionAdminId, "ADMINISTRADOR_COMPETICAO", competitionA);
        grantRoleDirect(competitionAdminId, "ADMINISTRADOR_COMPETICAO", competitionB);

        UUID first = createEvent(
                editionA,
                "competition-admin-sub",
                "Primeiro evento",
                "2026-08-22T16:00:00Z");
        UUID second = createEvent(
                editionA,
                "competition-admin-sub",
                "Segundo evento",
                "2026-08-23T16:00:00Z");
        UUID otherCompetition = createEvent(
                editionB,
                "competition-admin-sub",
                "Outra competição",
                "2026-08-22T12:00:00Z");
        createEvent(
                editionA,
                "competition-admin-sub",
                "Rascunho invisivel",
                "2026-08-22T10:00:00Z");
        publishEvent(first);
        publishEvent(second);
        publishEvent(otherCompetition);

        MvcResult firstPage = mockMvc.perform(get(PUBLIC_EVENTS)
                        .param("competitionId", competitionA.toString())
                        .param("editionId", editionA.toString())
                        .param("from", "2026-08-22T00:00:00Z")
                        .param("to", "2026-08-23T23:59:59Z")
                        .param("page", "0")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventId").value(first.toString()))
                .andExpect(jsonPath("$.items[0].title").value("Primeiro evento"))
                .andExpect(jsonPath("$.items[0].slots.length()").value(2))
                .andExpect(jsonPath("$.items[0].publicationId").doesNotExist())
                .andExpect(jsonPath("$.items[0].versionNo").doesNotExist())
                .andExpect(jsonPath("$.items[0].sourceReference").doesNotExist())
                .andReturn();

        JsonNode summary = objectMapper.readTree(firstPage.getResponse().getContentAsByteArray())
                .path("items")
                .get(0);
        assertThat(summary.size())
                .as("the agenda summary must contain only approved public fields")
                .isEqualTo(11);

        mockMvc.perform(get(PUBLIC_EVENTS)
                        .param("competitionId", competitionA.toString())
                        .param("editionId", editionA.toString())
                        .param("page", "1")
                        .param("size", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventId").value(second.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get(PUBLIC_EVENTS)
                        .param("competitionId", competitionA.toString())
                        .param("from", "2030-01-01T00:00:00Z")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("Administrador de outra competicao recebe 403 usando o escopo derivado da edicao")
    void rejectsCompetitionAdministratorFromAnotherScope() throws Exception {
        UUID competitionA = createCompetition("Competicao A", "competicao-a");
        UUID competitionB = createCompetition("Competicao B", "competicao-b");
        createEdition(competitionA, "Edicao A", "edicao-a");
        UUID editionB = createEdition(competitionB, "Edicao B", "edicao-b");
        grantRoleDirect(competitionAdminId, "ADMINISTRADOR_COMPETICAO", competitionA);

        mockMvc.perform(post("/api/v1/operations/sports/editions/" + editionB + "/events")
                        .with(oidcPrincipal("competition-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody("Escopo alheio")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"));
    }

    @Test
    @DisplayName("Administrador de outra competicao nao publica evento fora do seu escopo")
    void rejectsPublicationByAdministratorFromAnotherScope() throws Exception {
        UUID competitionA = createCompetition("Competicao Publicadora A", "competicao-publicadora-a");
        UUID competitionB = createCompetition("Competicao Publicadora B", "competicao-publicadora-b");
        UUID editionB = createEdition(competitionB, "Edicao Publicadora B", "edicao-publicadora-b");

        UUID competitionBAdminId = createIdentity("competition-b-admin-sub", "competition_b_admin");
        grantRoleDirect(competitionAdminId, "ADMINISTRADOR_COMPETICAO", competitionA);
        grantRoleDirect(competitionBAdminId, "ADMINISTRADOR_COMPETICAO", competitionB);
        UUID eventB = createEvent(editionB, "competition-b-admin-sub", "Evento protegido da competicao B");

        mockMvc.perform(post("/api/v1/operations/sports/events/" + eventB + "/publications")
                        .with(oidcPrincipal("competition-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM public.sports_event_publication WHERE event_id = ?",
                        Integer.class,
                        eventB))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM public.sports_event WHERE event_id = ?",
                        String.class,
                        eventB))
                .isEqualTo("RASCUNHO");
    }

    @Test
    @DisplayName("Concessao de administrador de competicao rejeita UUID inexistente")
    void rejectsGrantForUnknownCompetition() throws Exception {
        UUID unknownCompetitionId = UUID.randomUUID();

        mockMvc.perform(post(ROLE_GRANTS)
                        .with(oidcPrincipal("global-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + competitionAdminId
                                + "\",\"role\":\"ADMINISTRADOR_COMPETICAO\",\"scopeRef\":\""
                                + unknownCompetitionId + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BETUFU-404-NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM public.audit_event "
                                + "WHERE actor_identity_id = ? AND target_identity_id = ? "
                                + "AND action = 'PAPEL_CONCEDIDO' AND outcome = 'BLOQUEADO' "
                                + "AND new_value = ?",
                        Integer.class,
                        globalAdminId,
                        competitionAdminId,
                        "ADMINISTRADOR_COMPETICAO;scope=" + unknownCompetitionId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Limite textual usa os mesmos octetos UTF-8 do PostgreSQL")
    void rejectsTextThatExceedsTheDatabaseUtf8Limit() throws Exception {
        String nameAboveTheDatabaseLimit = "\u00e1".repeat(81);

        mockMvc.perform(post(COMPETITIONS)
                        .with(oidcPrincipal("global-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + nameAboveTheDatabaseLimit
                                + "\",\"slug\":\"limite-utf8\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM public.sports_competition WHERE slug = 'limite-utf8'",
                        Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("Usuário autenticado sem papel geral não cria competição nem edição")
    void rejectsCatalogCommandsWithoutGlobalAdministratorRole() throws Exception {
        mockMvc.perform(post(COMPETITIONS)
                        .with(oidcPrincipal("regular-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sem permissao\",\"slug\":\"sem-permissao\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"));

        UUID competitionId = createCompetition("Competicao protegida", "competicao-protegida");

        mockMvc.perform(post(COMPETITIONS + "/" + competitionId + "/editions")
                        .with(oidcPrincipal("regular-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Edicao proibida\",\"slug\":\"edicao-proibida\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"));
    }

    @Test
    @DisplayName("Versao de rascunho obsoleta recebe 409 e nao publica o evento")
    void rejectsStaleDraftVersion() throws Exception {
        UUID competitionId = createCompetition("Competicao Concorrente", "competicao-concorrente");
        UUID editionId = createEdition(competitionId, "Edicao Concorrente", "edicao-concorrente");
        grantRoleDirect(competitionAdminId, "ADMINISTRADOR_COMPETICAO", competitionId);
        UUID eventId = createEvent(editionId, "competition-admin-sub", "Evento concorrente");

        mockMvc.perform(post("/api/v1/operations/sports/events/" + eventId + "/publications")
                        .with(oidcPrincipal("competition-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BETUFU-409-STALE_VERSION"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM public.sports_event_publication WHERE event_id = ?",
                        Integer.class,
                        eventId))
                .isZero();
    }

    @Test
    @DisplayName("Publicacao exige expectedLockVersion explicita e nao interpreta objeto vazio como zero")
    void rejectsMissingExpectedLockVersion() throws Exception {
        UUID competitionId = createCompetition("Competicao Validada", "competicao-validada");
        UUID editionId = createEdition(competitionId, "Edicao Validada", "edicao-validada");
        grantRoleDirect(competitionAdminId, "ADMINISTRADOR_COMPETICAO", competitionId);
        UUID eventId = createEvent(editionId, "competition-admin-sub", "Evento validado");

        mockMvc.perform(post("/api/v1/operations/sports/events/" + eventId + "/publications")
                        .with(oidcPrincipal("competition-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM public.sports_event_publication WHERE event_id = ?",
                        Integer.class,
                        eventId))
                .isZero();
    }

    @Test
    @DisplayName("Comandos exigem sessao e o unico GET publico continua anonimo")
    void keepsCommandsPrivateAndPublicGetAnonymous() throws Exception {
        mockMvc.perform(post(COMPETITIONS)
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sem sessao\",\"slug\":\"sem-sessao\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BETUFU-401-UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/public/sports/events/" + UUID.randomUUID())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    private UUID createCompetition(String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(COMPETITIONS)
                        .with(oidcPrincipal("global-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return uuid(result, "competitionId");
    }

    private UUID createEdition(UUID competitionId, String name, String slug) throws Exception {
        MvcResult result = mockMvc.perform(post(COMPETITIONS + "/" + competitionId + "/editions")
                        .with(oidcPrincipal("global-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug
                                + "\",\"startDate\":\"2026-08-01\",\"endDate\":\"2026-09-30\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return uuid(result, "editionId");
    }

    private UUID createEvent(UUID editionId, String subject, String title) throws Exception {
        return createEvent(editionId, subject, title, "2026-08-22T16:00:00Z");
    }

    private UUID createEvent(UUID editionId, String subject, String title, String scheduledAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/operations/sports/editions/" + editionId + "/events")
                        .with(oidcPrincipal(subject))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody(title, scheduledAt)))
                .andExpect(status().isCreated())
                .andReturn();
        return uuid(result, "eventId");
    }

    private static String eventBody(String title) {
        return eventBody(title, "2026-08-22T16:00:00Z");
    }

    private static String eventBody(String title, String scheduledAt) {
        return String.format(
                "{\"title\":\"%s\",\"format\":\"HEAD_TO_HEAD\","
                        + "\"scheduledAt\":\"%s\",\"zoneId\":\"America/Sao_Paulo\","
                        + "\"venueState\":\"LOCAL_A_DEFINIR\","
                        + "\"sourceReference\":\"https://example.invalid/boletim-1\","
                        + "\"slots\":["
                        + "{\"slotNo\":1,\"displayLabel\":\"Direito\"},"
                        + "{\"slotNo\":2,\"displayLabel\":\"Computacao\"}]}",
                title,
                scheduledAt);
    }

    private void publishEvent(UUID eventId) throws Exception {
        mockMvc.perform(post("/api/v1/operations/sports/events/" + eventId + "/publications")
                        .with(oidcPrincipal("competition-admin-sub"))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedLockVersion\":0}"))
                .andExpect(status().isCreated());
    }

    private UUID uuid(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return UUID.fromString(body.path(field).asText());
    }

    private UUID createIdentity(String subject, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)",
                ISSUER,
                subject);
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                ISSUER,
                subject);
        jdbcTemplate.update(
                "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
                identityId,
                nickname);
        return identityId;
    }

    private void grantRoleDirect(UUID identityId, String roleName, UUID scopeRef) {
        if (scopeRef == null) {
            jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, ?, 'VIGENTE')",
                    identityId,
                    roleName);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
                identityId,
                roleName,
                scopeRef);
    }

    private RequestPostProcessor oidcPrincipal(String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "sports-tracer-token-" + subject,
                now,
                now.plusSeconds(3600),
                Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, subject));

        Collection<GrantedAuthority> baseAuthorities = List.of(new OidcUserAuthority(idToken));
        Collection<? extends GrantedAuthority> mappedAuthorities =
                authorityContributor.mapAuthorities(baseAuthorities);
        OidcUser oidcUser = new DefaultOidcUser(mappedAuthorities, idToken);
        return oidcLogin().oidcUser(oidcUser);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-sports-tracer-it")
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
                    .clientName("BETUFU Sports Tracer Test")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
