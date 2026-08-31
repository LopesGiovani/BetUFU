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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCorrectRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCreateRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsPublishRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsRow;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsUpdateRequest;
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
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-sports-standings-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsStandingsIT.TestOidcConfiguration.class)
class SportsStandingsIT {

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
    @Autowired private DataSource dataSource;
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
    private UUID editionB;
    private UUID sportA;
    private UUID categoryA;
    private UUID categoryB;
    private UUID phaseA;
    private UUID team1;
    private UUID team2;
    private UUID team3;
    private UUID teamFromEditionB;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                """
                TRUNCATE TABLE public.sports_standings_publication_cell, public.sports_standings_publication_row,
                public.sports_standings_publication_column, public.sports_standings_publication,
                public.sports_standings_row_cell, public.sports_standings_row, public.sports_standings_column,
                public.sports_standings, public.sports_bracket_publication_node, public.sports_bracket_publication,
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
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
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
        jdbcTemplate.update("INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
                sportA, "Futsal", "futsal");
        jdbcTemplate.update("UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
                sportA, competitionA);

        categoryA = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
                categoryA, editionA, "Masculino", "masculino");

        categoryB = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
                categoryB, editionB, "Masculino", "masculino");

        phaseA = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_phase (phase_id, edition_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?)",
                phaseA, editionA, categoryA, "Fase de Grupos", "fase-de-grupos");

        UUID org1 = UUID.randomUUID();
        UUID org2 = UUID.randomUUID();
        UUID org3 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO public.sports_organization (organization_id, name, slug) VALUES (?, ?, ?)", org1, "Atlética Engenharia", "eng");
        jdbcTemplate.update("INSERT INTO public.sports_organization (organization_id, name, slug) VALUES (?, ?, ?)", org2, "Atlética Medicina", "med");
        jdbcTemplate.update("INSERT INTO public.sports_organization (organization_id, name, slug) VALUES (?, ?, ?)", org3, "Atlética Computação", "comp");

        team1 = UUID.randomUUID();
        team2 = UUID.randomUUID();
        team3 = UUID.randomUUID();
        teamFromEditionB = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
                team1, org1, editionA, sportA, categoryA, "Engenharia Futsal", "eng-futsal");
        jdbcTemplate.update("INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
                team2, org2, editionA, sportA, categoryA, "Medicina Futsal", "med-futsal");
        jdbcTemplate.update("INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
                team3, org3, editionA, sportA, categoryA, "Computação Futsal", "comp-futsal");
        jdbcTemplate.update("INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?, ?, ?)",
                teamFromEditionB, org1, editionB, sportA, categoryB, "Engenharia EdB", "eng-edb");
    }

    @Test
    @DisplayName("Cria, configura linhas/colunas, publica e corrige classificação no ciclo completo")
    void fullStandingsLifecyclePublicationAndCorrection() throws Exception {
        // 1. Criar classificação em rascunho com colunas declaradas
        List<String> declaredColumns = List.of("PTS", "J", "V", "E", "D", "GP", "GC", "SG");
        SportsStandingsCreateRequest createReq = new SportsStandingsCreateRequest(
                "Grupo A - Futsal", "grupo-a-futsal", sportA, categoryA, phaseA, declaredColumns);

        String createJson = mockMvc.perform(post("/api/v1/operations/sports/editions/{editionId}/standings", editionA)
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Grupo A - Futsal"))
                .andExpect(jsonPath("$.status").value("RASCUNHO"))
                .andExpect(jsonPath("$.lockVersion").value(0))
                .andReturn().getResponse().getContentAsString();

        UUID standingsId = UUID.fromString(objectMapper.readTree(createJson).get("standingsId").asText());

        // 2. Rascunho não aparece no endpoint público P8
        mockMvc.perform(get("/api/v1/public/sports/standings/{standingsId}", standingsId))
                .andExpect(status().isNotFound());

        // 3. Atualizar rascunho com linhas e valores transcritos (sem cálculo pelo sistema)
        SportsStandingsRow row1 = new SportsStandingsRow(
                1, team1, "Engenharia Futsal", List.of("6", "2", "2", "0", "0", "8", "2", "6"));
        SportsStandingsRow row2 = new SportsStandingsRow(
                2, team2, "Medicina Futsal", List.of("3", "2", "1", "0", "1", "5", "4", "1"));
        SportsStandingsRow row3 = new SportsStandingsRow(
                3, team3, "Computação Futsal", List.of("0", "2", "0", "0", "2", "1", "8", "-7"));

        SportsStandingsUpdateRequest updateReq = new SportsStandingsUpdateRequest(
                "Grupo A - Futsal", "grupo-a-futsal", sportA, categoryA, phaseA,
                declaredColumns, List.of(row1, row2, row3));

        mockMvc.perform(put("/api/v1/operations/sports/standings/{standingsId}", standingsId)
                        .param("expectedLockVersion", "0")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Grupo A - Futsal"))
                .andExpect(jsonPath("$.columns.length()").value(8))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andExpect(jsonPath("$.rows[0].position").value(1))
                .andExpect(jsonPath("$.rows[0].participantName").value("Engenharia Futsal"))
                .andExpect(jsonPath("$.rows[0].values[0]").value("6"))
                .andExpect(jsonPath("$.rows[0].values[7]").value("6"));

        // 4. Publicar classificação com fonte oficial obrigatória
        SportsStandingsPublishRequest pubReq = new SportsStandingsPublishRequest("https://proae.ufu.br/boletim-futsal-04");

        mockMvc.perform(post("/api/v1/operations/sports/standings/{standingsId}/publish", standingsId)
                        .param("expectedLockVersion", "1")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pubReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.sourceReference").value("https://proae.ufu.br/boletim-futsal-04"));

        // 5. Consulta pública P8 retorna o snapshot versionado publicado
        mockMvc.perform(get("/api/v1/public/sports/standings/{standingsId}", standingsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.standingsId").value(standingsId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.versionNo").value(1))
                .andExpect(jsonPath("$.sourceReference").value("https://proae.ufu.br/boletim-futsal-04"))
                .andExpect(jsonPath("$.columns[0]").value("PTS"))
                .andExpect(jsonPath("$.rows[0].participantName").value("Engenharia Futsal"))
                .andExpect(jsonPath("$.rows[0].values[0]").value("6"));

        // 6. Atualização direta no snapshot publicado é proibida (imutabilidade)
        mockMvc.perform(put("/api/v1/operations/sports/standings/{standingsId}", standingsId)
                        .param("expectedLockVersion", "2")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());

        // 7. Correção cria a versão 2 com justificativa auditada
        SportsStandingsRow correctedRow2 = new SportsStandingsRow(
                2, team2, "Medicina Futsal", List.of("4", "2", "1", "1", "0", "6", "4", "2"));
        SportsStandingsCorrectRequest correctReq = new SportsStandingsCorrectRequest(
                "Retificação do placar do jogo 3 conforme errata do Boletim 04",
                "https://proae.ufu.br/boletim-futsal-04-retificado",
                declaredColumns, List.of(row1, correctedRow2, row3));

        mockMvc.perform(post("/api/v1/operations/sports/standings/{standingsId}/correct", standingsId)
                        .param("expectedLockVersion", "2")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(correctReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLICADO"))
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.sourceReference").value("https://proae.ufu.br/boletim-futsal-04-retificado"))
                .andExpect(jsonPath("$.rows[1].values[0]").value("4"));

        // 8. Consulta pública P8 reflete a versão mais recente
        mockMvc.perform(get("/api/v1/public/sports/standings/{standingsId}", standingsId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNo").value(2))
                .andExpect(jsonPath("$.rows[1].values[0]").value("4"));
    }

    @Test
    @DisplayName("Validações de classificação rejeitam colunas vazias, células desalinhadas e equipes de outra edição")
    void rejectsInvalidStandingsConfiguration() throws Exception {
        SportsStandingsCreateRequest createReq = new SportsStandingsCreateRequest(
                "Grupo B", "grupo-b", sportA, categoryA, phaseA, List.of("PTS", "J"));

        String createJson = mockMvc.perform(post("/api/v1/operations/sports/editions/{editionId}/standings", editionA)
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID standingsId = UUID.fromString(objectMapper.readTree(createJson).get("standingsId").asText());

        // 1. Linha com contagem de valores divergente do número de colunas (3 valores para 2 colunas)
        SportsStandingsRow mismatchedRow = new SportsStandingsRow(
                1, team1, "Equipe A", List.of("3", "1", "1"));
        SportsStandingsUpdateRequest mismatchedUpdate = new SportsStandingsUpdateRequest(
                "Grupo B", "grupo-b", sportA, categoryA, phaseA,
                List.of("PTS", "J"), List.of(mismatchedRow));

        mockMvc.perform(put("/api/v1/operations/sports/standings/{standingsId}", standingsId)
                        .param("expectedLockVersion", "0")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mismatchedUpdate)))
                .andExpect(status().isBadRequest());

        // 2. Linha associando equipe de outra edição
        SportsStandingsRow foreignTeamRow = new SportsStandingsRow(
                1, teamFromEditionB, "Equipe Estrangeira", List.of("3", "1"));
        SportsStandingsUpdateRequest foreignTeamUpdate = new SportsStandingsUpdateRequest(
                "Grupo B", "grupo-b", sportA, categoryA, phaseA,
                List.of("PTS", "J"), List.of(foreignTeamRow));

        mockMvc.perform(put("/api/v1/operations/sports/standings/{standingsId}", standingsId)
                        .param("expectedLockVersion", "0")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(foreignTeamUpdate)))
                .andExpect(status().isBadRequest());

        // 3. Colunas duplicadas
        SportsStandingsUpdateRequest duplicateColumns = new SportsStandingsUpdateRequest(
                "Grupo B", "grupo-b", sportA, categoryA, phaseA,
                List.of("PTS", "pts"), List.of(new SportsStandingsRow(1, team1, "Equipe A", List.of("3", "3"))));

        mockMvc.perform(put("/api/v1/operations/sports/standings/{standingsId}", standingsId)
                        .param("expectedLockVersion", "0")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateColumns)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Controle de concorrência com optimistic locking e autorização por escopo")
    void optimisticLockingAndScopeAuthorization() throws Exception {
        SportsStandingsCreateRequest createReq = new SportsStandingsCreateRequest(
                "Grupo C", "grupo-c", sportA, categoryA, phaseA, List.of("PTS"));

        // Administrador da competição B tentando criar na edição A -> 403 Forbidden
        mockMvc.perform(post("/api/v1/operations/sports/editions/{editionId}/standings", editionA)
                        .with(user("competition-b-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden());

        // Administrador da competição A cria com sucesso
        String createJson = mockMvc.perform(post("/api/v1/operations/sports/editions/{editionId}/standings", editionA)
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID standingsId = UUID.fromString(objectMapper.readTree(createJson).get("standingsId").asText());

        // Update com expectedLockVersion obsoleto -> 409 Conflict
        SportsStandingsUpdateRequest updateReq = new SportsStandingsUpdateRequest(
                "Grupo C", "grupo-c", sportA, categoryA, phaseA,
                List.of("PTS"), List.of(new SportsStandingsRow(1, team1, "Equipe A", List.of("3"))));

        mockMvc.perform(put("/api/v1/operations/sports/standings/{standingsId}", standingsId)
                        .param("expectedLockVersion", "99")
                        .with(user("competition-a-admin-sub"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Banco de dados impõe imutabilidade em publicações de classificação e tabelas filhas")
    void immutabilityOfPublishedSnapshotsInDatabase() {
        UUID adminId = createIdentity("pub-admin-sub", "pub_admin");
        UUID standingsId = UUID.randomUUID();
        UUID pubId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings (
                    standings_id, competition_id, edition_id, name, slug, status, lock_version
                ) VALUES (?, ?, ?, 'Classificação Teste', 'class-teste', 'PUBLICADO', 1)
                """,
                standingsId, competitionA, editionA);

        jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication (
                    publication_id, standings_id, competition_id, edition_id, version_no,
                    standings_name, competition_name, edition_name, source_reference,
                    published_by_identity_id, base_lock_version
                ) VALUES (?, ?, ?, ?, 1, 'Classificação Teste', 'Competição A', 'Edição A',
                          'https://boletim.ufu.br', ?, 0)
                """,
                pubId, standingsId, competitionA, editionA, adminId);

        jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication_column (publication_id, column_order, label)
                VALUES (?, 1, 'PTS')
                """,
                pubId);

        jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication_row (publication_id, row_order, position, team_id, participant_name)
                VALUES (?, 1, 1, ?, 'Engenharia Futsal')
                """,
                pubId, team1);

        jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication_cell (publication_id, row_order, column_order, cell_value)
                VALUES (?, 1, 1, '10')
                """,
                pubId);

        // Tentativa de update na publicação -> rejeitada pelo trigger
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public.sports_standings_publication SET source_reference = 'https://hacked' WHERE publication_id = ?",
                pubId))
                .hasMessageContaining("snapshots are immutable");

        // Tentativa de delete na publicação -> rejeitada
        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM public.sports_standings_publication WHERE publication_id = ?",
                pubId))
                .hasMessageContaining("snapshots are immutable");

        // Tentativa de update em coluna da publicação -> rejeitada
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public.sports_standings_publication_column SET label = 'Alterado' WHERE publication_id = ?",
                pubId))
                .hasMessageContaining("snapshots are immutable");

        // Tentativa de update em linha da publicação -> rejeitada
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public.sports_standings_publication_row SET participant_name = 'Alterado' WHERE publication_id = ?",
                pubId))
                .hasMessageContaining("snapshots are immutable");

        // Tentativa de update em célula da publicação -> rejeitada
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE public.sports_standings_publication_cell SET cell_value = '99' WHERE publication_id = ?",
                pubId))
                .hasMessageContaining("snapshots are immutable");
    }

    private UUID createIdentity(String subject, String nickname) {
        UUID identityId = jdbcTemplate.queryForObject(
                "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
                UUID.class, ISSUER, subject);
        jdbcTemplate.update("INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
                identityId, nickname);
        return identityId;
    }

    private UUID createCompetition(String name, String slug) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
                UUID.class, name, slug);
    }

    private UUID createEdition(UUID compId, String name, String slug) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, ?, ?) RETURNING edition_id",
                UUID.class, compId, name, slug);
    }

    private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
        jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
                identityId, roleName, scopeRef);
    }

    private RequestPostProcessor user(String subject) {
        OidcIdToken idToken = new OidcIdToken(
                "mock-token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of(
                        IdTokenClaimNames.ISS, ISSUER,
                        IdTokenClaimNames.SUB, subject));
        Collection<GrantedAuthority> baseAuthorities = List.of(new OidcUserAuthority(idToken));
        Collection<? extends GrantedAuthority> authorities = authorityContributor.mapAuthorities(baseAuthorities);
        OidcUser oidcUser = new DefaultOidcUser(authorities, idToken);
        return oidcLogin().oidcUser(oidcUser);
    }

    @TestConfiguration
    static class TestOidcConfiguration {
        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-sports-standings-it")
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
