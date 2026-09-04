package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.sports.internal.importtabular.SportsXlsxParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-tabular-import-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsTabularImportIT.TestOidcConfiguration.class)
class SportsTabularImportIT {

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
  private UUID competitionA;
  private UUID competitionB;
  private UUID editionA;
  private UUID sportFutsal;
  private UUID categoryMasc;
  private UUID orgEngenharia;
  private UUID orgMedicina;
  private UUID teamEngenharia;
  private UUID teamMedicina;

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
    UUID compAAdmin = createIdentity("comp-a-admin-sub", "comp_a_admin");
    UUID compBAdmin = createIdentity("comp-b-admin-sub", "comp_b_admin");
    grantRole(globalAdmin, "ADMINISTRADOR_GERAL", null);

    competitionA = createCompetition("Competição A", "competicao-a");
    competitionB = createCompetition("Competição B", "competicao-b");
    grantRole(compAAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);
    grantRole(compBAdmin, "ADMINISTRADOR_COMPETICAO", competitionB);

    editionA = createEdition(competitionA, "Edição 2026", "edicao-2026");
    sportFutsal = createSport(competitionA, "Futsal", "futsal");
    categoryMasc = createCategory(editionA, "Masculino", "masculino");

    orgEngenharia = createOrganization("Atlética Engenharia", "atletica-engenharia");
    orgMedicina = createOrganization("Atlética Medicina", "atletica-medicina");

    teamEngenharia =
        createTeam(
            orgEngenharia,
            editionA,
            sportFutsal,
            categoryMasc,
            "Engenharia Futsal Masc",
            "eng-futsal-masc");
    teamMedicina =
        createTeam(
            orgMedicina,
            editionA,
            sportFutsal,
            categoryMasc,
            "Medicina Futsal Masc",
            "med-futsal-masc");
  }

  @Test
  @DisplayName("Importação de CSV válido cria atletas, rascunhos de elenco e membros atomicamente")
  void importsValidCsvAtomically() throws Exception {
    byte[] csvBytes = readResource("/sports/csv/valid-roster.csv");
    MockMultipartFile file =
        new MockMultipartFile("file", "valid-roster.csv", "text/csv", csvBytes);

    MvcResult validateResult =
        mockMvc
            .perform(
                multipart(
                        "/api/v1/operations/sports/editions/"
                            + editionA
                            + "/tabular-import/validate")
                    .file(file)
                    .with(csrf())
                    .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readyToImport").value(true))
            .andExpect(jsonPath("$.totalRows").value(3))
            .andExpect(jsonPath("$.validRows").value(3))
            .andExpect(jsonPath("$.errorCount").value(0))
            .andExpect(jsonPath("$.importId").isNotEmpty())
            .andReturn();

    JsonNode validateJson =
        objectMapper.readTree(validateResult.getResponse().getContentAsString());
    String importId = validateJson.get("importId").asText();

    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/commit")
                .param("importId", importId)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONCLUIDO"))
        .andExpect(jsonPath("$.totalImportedAthletes").value(3))
        .andExpect(jsonPath("$.totalImportedRosters").value(2));

    Integer athleteCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_athlete", Integer.class);
    Integer rosterCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_roster", Integer.class);
    Integer memberCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_roster_member", Integer.class);

    assertThat(athleteCount).isEqualTo(3);
    assertThat(rosterCount).isEqualTo(2);
    assertThat(memberCount).isEqualTo(3);

    Map<String, Object> temporalMember =
        jdbcTemplate.queryForMap(
            "SELECT m.affiliation_start_date, m.affiliation_end_date "
                + "FROM sports_roster_member m JOIN sports_roster r ON r.roster_id = m.roster_id "
                + "WHERE r.team_id = ? ORDER BY m.athlete_id LIMIT 1",
            teamEngenharia);
    assertThat(temporalMember.get("affiliation_start_date"))
        .isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 8, 1)));
    assertThat(temporalMember.get("affiliation_end_date"))
        .isEqualTo(java.sql.Date.valueOf(LocalDate.of(2026, 9, 30)));

    Map<String, Object> legacyMember =
        jdbcTemplate.queryForMap(
            "SELECT m.affiliation_start_date, m.affiliation_end_date "
                + "FROM sports_roster_member m JOIN sports_roster r ON r.roster_id = m.roster_id "
                + "WHERE r.team_id = ? LIMIT 1",
            teamMedicina);
    assertThat(legacyMember.get("affiliation_start_date")).isNull();
    assertThat(legacyMember.get("affiliation_end_date")).isNull();
  }

  @Test
  @DisplayName("Atomicidade CSV: erro tardio na última linha salva zero atletas e zero membros")
  void provesLateErrorAtomicityZeroSavedCsv() throws Exception {
    byte[] csvBytes = readResource("/sports/csv/late-error-roster.csv");
    MockMultipartFile file =
        new MockMultipartFile("file", "late-error-roster.csv", "text/csv", csvBytes);

    mockMvc
        .perform(
            multipart("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/validate")
                .file(file)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readyToImport").value(false))
        .andExpect(jsonPath("$.errorCount").value(org.hamcrest.Matchers.greaterThan(0)));

    Integer athleteCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_athlete", Integer.class);
    Integer rosterCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_roster", Integer.class);
    Integer memberCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_roster_member", Integer.class);

    assertThat(athleteCount).isEqualTo(0);
    assertThat(rosterCount).isEqualTo(0);
    assertThat(memberCount).isEqualTo(0);
  }

  @Test
  @DisplayName("Importação de XLSX válido cria atletas e membros atomicamente")
  void importsValidXlsxAtomically() throws Exception {
    byte[] xlsxBytes = createValidWorkbookBytes();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "valid-roster.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    MvcResult validateResult =
        mockMvc
            .perform(
                multipart(
                        "/api/v1/operations/sports/editions/"
                            + editionA
                            + "/tabular-import/validate")
                    .file(file)
                    .with(csrf())
                    .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readyToImport").value(true))
            .andExpect(jsonPath("$.format").value("XLSX"))
            .andExpect(jsonPath("$.totalRows").value(2))
            .andReturn();

    JsonNode validateJson =
        objectMapper.readTree(validateResult.getResponse().getContentAsString());
    String importId = validateJson.get("importId").asText();

    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/commit")
                .param("importId", importId)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONCLUIDO"))
        .andExpect(jsonPath("$.totalImportedAthletes").value(2));

    Integer athleteCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_athlete", Integer.class);
    assertThat(athleteCount).isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT m.affiliation_start_date FROM sports_roster_member m "
                    + "JOIN sports_roster r ON r.roster_id = m.roster_id "
                    + "WHERE r.team_id = ? LIMIT 1",
                LocalDate.class,
                teamEngenharia))
        .isEqualTo(LocalDate.of(2026, 8, 1));
  }

  @Test
  @DisplayName("Importacao rejeita roster PUBLICADO sem gravar novos registros")
  void rejectsCommitForPublishedRosterWithoutWrites() throws Exception {
    UUID publishedRosterId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_roster (roster_id, team_id, status, lock_version) VALUES (?, ?, 'PUBLICADO', 3)",
        publishedRosterId,
        teamEngenharia);

    MockMultipartFile file =
        new MockMultipartFile(
            "file", "valid-roster.csv", "text/csv", readResource("/sports/csv/valid-roster.csv"));
    MvcResult validateResult =
        mockMvc
            .perform(
                multipart(
                        "/api/v1/operations/sports/editions/"
                            + editionA
                            + "/tabular-import/validate")
                    .file(file)
                    .with(csrf())
                    .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readyToImport").value(true))
            .andReturn();

    String importId =
        objectMapper
            .readTree(validateResult.getResponse().getContentAsString())
            .get("importId")
            .asText();
    int athletesBefore =
        jdbcTemplate.queryForObject("SELECT count(*) FROM sports_athlete", Integer.class);
    int rostersBefore =
        jdbcTemplate.queryForObject("SELECT count(*) FROM sports_roster", Integer.class);
    int membersBefore =
        jdbcTemplate.queryForObject("SELECT count(*) FROM sports_roster_member", Integer.class);

    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/commit")
                .param("importId", importId)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("BETUFU-409-STALE_VERSION"));

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sports_athlete", Integer.class))
        .isEqualTo(athletesBefore);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sports_roster", Integer.class))
        .isEqualTo(rostersBefore);
    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM sports_roster_member", Integer.class))
        .isEqualTo(membersBefore);
  }

  @Test
  @DisplayName("Validacao rejeita periodo de vinculo fora dos limites da edicao")
  void rejectsAffiliationPeriodOutsideEditionBounds() throws Exception {
    String csv =
        "organizacao_slug,modalidade_slug,categoria_slug,nome_atleta,matricula,numero_camisa,posicao,inicio_vinculo,fim_vinculo\n"
            + "atletica-engenharia,futsal,masculino,Atleta Fora,11821BCC003,9,Ala,2026-07-31,2026-09-30\n";
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "outside-period.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/validate")
                .file(file)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readyToImport").value(false))
        .andExpect(jsonPath("$.errors[0].field").value("inicio_vinculo"));
  }

  @Test
  @DisplayName("Atomicidade XLSX: erro na ultima linha nao salva nenhum registro")
  void provesLateErrorAtomicityZeroSavedXlsx() throws Exception {
    byte[] xlsxBytes = createLateErrorWorkbookBytes();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "late-error-roster.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(
            multipart("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/validate")
                .file(file)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readyToImport").value(false))
        .andExpect(jsonPath("$.errorCount").value(org.hamcrest.Matchers.greaterThan(0)));

    Integer athleteCount =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sports_athlete", Integer.class);
    assertThat(athleteCount).isEqualTo(0);
  }

  @Test
  @DisplayName(
      "Validação rejeita referências inexistentes (organização, modalidade, categoria, equipe)")
  void rejectsMissingReferences() throws Exception {
    byte[] csvBytes = readResource("/sports/csv/missing-reference-roster.csv");
    MockMultipartFile file =
        new MockMultipartFile("file", "missing-reference-roster.csv", "text/csv", csvBytes);

    mockMvc
        .perform(
            multipart("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/validate")
                .file(file)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readyToImport").value(false))
        .andExpect(jsonPath("$.errors[0].field").value("organizacao_slug"));
  }

  @Test
  @DisplayName("Validação rejeita linhas duplicadas no mesmo arquivo")
  void rejectsDuplicateRowInFile() throws Exception {
    byte[] csvBytes = readResource("/sports/csv/duplicate-row-roster.csv");
    MockMultipartFile file =
        new MockMultipartFile("file", "duplicate-row-roster.csv", "text/csv", csvBytes);

    mockMvc
        .perform(
            multipart("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/validate")
                .file(file)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readyToImport").value(false))
        .andExpect(jsonPath("$.errorCount").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  @Test
  @DisplayName("Permissão: administrador de outra competição não pode validar ou importar")
  void enforcesCompetitionScopePermissions() throws Exception {
    byte[] csvBytes = readResource("/sports/csv/valid-roster.csv");
    MockMultipartFile file =
        new MockMultipartFile("file", "valid-roster.csv", "text/csv", csvBytes);

    mockMvc
        .perform(
            multipart("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/validate")
                .file(file)
                .with(csrf())
                .with(oidcLoginUser("comp-b-admin-sub", "comp_b_admin")))
        .andExpect(status().isForbidden());

    MvcResult validateResult =
        mockMvc
            .perform(
                multipart(
                        "/api/v1/operations/sports/editions/"
                            + editionA
                            + "/tabular-import/validate")
                    .file(file)
                    .with(csrf())
                    .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readyToImport").value(true))
            .andReturn();

    String importId =
        objectMapper
            .readTree(validateResult.getResponse().getContentAsString())
            .get("importId")
            .asText();
    Integer athleteCountBefore =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.sports_athlete", Integer.class);
    Integer rosterCountBefore =
        jdbcTemplate.queryForObject("SELECT count(*) FROM public.sports_roster", Integer.class);
    Integer memberCountBefore =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_roster_member", Integer.class);

    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/commit")
                .param("importId", importId)
                .with(csrf())
                .with(oidcLoginUser("comp-b-admin-sub", "comp_b_admin")))
        .andExpect(status().isForbidden());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_athlete", Integer.class))
        .isEqualTo(athleteCountBefore);
    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM public.sports_roster", Integer.class))
        .isEqualTo(rosterCountBefore);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_roster_member", Integer.class))
        .isEqualTo(memberCountBefore);
  }

  @Test
  @DisplayName("Consulta de status: suporta recuperação indeterminada e rastreia estado")
  void checksImportStatusIndeterminateRecovery() throws Exception {
    byte[] csvBytes = readResource("/sports/csv/valid-roster.csv");
    MockMultipartFile file =
        new MockMultipartFile("file", "valid-roster.csv", "text/csv", csvBytes);

    MvcResult validateResult =
        mockMvc
            .perform(
                multipart(
                        "/api/v1/operations/sports/editions/"
                            + editionA
                            + "/tabular-import/validate")
                    .file(file)
                    .with(csrf())
                    .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode validateJson =
        objectMapper.readTree(validateResult.getResponse().getContentAsString());
    String importId = validateJson.get("importId").asText();

    mockMvc
        .perform(
            get("/api/v1/operations/sports/editions/"
                    + editionA
                    + "/tabular-import/status/"
                    + importId)
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VALIDADO_AGUARDANDO_CONFIRMACAO"));

    mockMvc
        .perform(
            post("/api/v1/operations/sports/editions/" + editionA + "/tabular-import/commit")
                .param("importId", importId)
                .with(csrf())
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/operations/sports/editions/"
                    + editionA
                    + "/tabular-import/status/"
                    + importId)
                .with(oidcLoginUser("comp-a-admin-sub", "comp_a_admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CONCLUIDO"));
  }

  private byte[] createValidWorkbookBytes() throws Exception {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("Template");
      Row header = sheet.createRow(0);
      for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
        header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
      }
      Row row1 = sheet.createRow(1);
      row1.createCell(0).setCellValue("atletica-engenharia");
      row1.createCell(1).setCellValue("futsal");
      row1.createCell(2).setCellValue("masculino");
      row1.createCell(3).setCellValue("Carlos Silva");
      row1.createCell(4).setCellValue("11821BCC001");
      row1.createCell(5).setCellValue(10);
      row1.createCell(6).setCellValue("Ala");
      row1.createCell(7).setCellValue("2026-08-01");
      row1.createCell(8).setCellValue("2026-09-30");

      Row row2 = sheet.createRow(2);
      row2.createCell(0).setCellValue("atletica-medicina");
      row2.createCell(1).setCellValue("futsal");
      row2.createCell(2).setCellValue("masculino");
      row2.createCell(3).setCellValue("Marcos Oliveira");
      row2.createCell(4).setCellValue("11821MED001");
      row2.createCell(5).setCellValue(1);
      row2.createCell(6).setCellValue("Goleiro");
      row2.createCell(7).setCellValue("2026-08-01");
      row2.createCell(8).setCellValue("2026-09-30");

      wb.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createLateErrorWorkbookBytes() throws Exception {
    try (Workbook wb = new XSSFWorkbook();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet("Template");
      Row header = sheet.createRow(0);
      for (int i = 0; i < SportsXlsxParser.EXPECTED_HEADERS.size(); i++) {
        header.createCell(i).setCellValue(SportsXlsxParser.EXPECTED_HEADERS.get(i));
      }
      Row row1 = sheet.createRow(1);
      row1.createCell(0).setCellValue("atletica-engenharia");
      row1.createCell(1).setCellValue("futsal");
      row1.createCell(2).setCellValue("masculino");
      row1.createCell(3).setCellValue("Carlos Silva");
      row1.createCell(4).setCellValue("11821BCC001");
      row1.createCell(5).setCellValue(10);
      row1.createCell(6).setCellValue("Ala");
      row1.createCell(7).setCellValue("2026-08-01");
      row1.createCell(8).setCellValue("2026-09-30");

      Row row2 = sheet.createRow(2);
      row2.createCell(0).setCellValue("atletica-inexistente");
      row2.createCell(1).setCellValue("futsal");
      row2.createCell(2).setCellValue("masculino");
      row2.createCell(3).setCellValue("Atleta Invalido");
      row2.createCell(4).setCellValue("11821BCC999");
      row2.createCell(5).setCellValue(99);
      row2.createCell(7).setCellValue("2026-08-01");
      row2.createCell(8).setCellValue("2026-09-30");
      row2.createCell(6).setCellValue("Pivô");

      wb.write(out);
      return out.toByteArray();
    }
  }

  private byte[] readResource(String path) throws Exception {
    try (InputStream is = getClass().getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalArgumentException("Resource not found: " + path);
      }
      return is.readAllBytes();
    }
  }

  private UUID createIdentity(String sub, String name) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)", ISSUER, sub);
    UUID id =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            UUID.class,
            ISSUER,
            sub);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)", id, name);
    return id;
  }

  private void grantRole(UUID identityId, String role, UUID competitionId) {
    if (competitionId == null) {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, ?, 'VIGENTE')",
          identityId,
          role);
    } else {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
          identityId,
          role,
          competitionId);
    }
  }

  private UUID createCompetition(String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_competition (competition_id, name, slug, status, lock_version, created_at, updated_at) "
            + "VALUES (?, ?, ?, 'ATIVA', 0, NOW(), NOW())",
        id,
        name,
        slug);
    return id;
  }

  private UUID createEdition(UUID compId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_edition (edition_id, competition_id, name, slug, start_date, end_date, status, lock_version, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, 'ATIVA', 0, NOW(), NOW())",
        id,
        compId,
        name,
        slug,
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 9, 30));
    return id;
  }

  private UUID createSport(UUID compId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_sport (sport_id, name, slug, created_at, updated_at) "
            + "VALUES (?, ?, ?, NOW(), NOW())",
        id,
        name,
        slug);
    jdbcTemplate.update(
        "UPDATE sports_competition SET sport_id = ? WHERE competition_id = ?", id, compId);
    return id;
  }

  private UUID createCategory(UUID editionId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_category (category_id, edition_id, name, slug, status, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, 'ATIVA', NOW(), NOW())",
        id,
        editionId,
        name,
        slug);
    return id;
  }

  private UUID createOrganization(String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_organization (organization_id, name, slug, status, lock_version, created_at, updated_at) "
            + "VALUES (?, ?, ?, 'PUBLICADO', 0, NOW(), NOW())",
        id,
        name,
        slug);
    return id;
  }

  private UUID createTeam(
      UUID orgId, UUID editionId, UUID sportId, UUID catId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status, lock_version, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO', 0, NOW(), NOW())",
        id,
        orgId,
        editionId,
        sportId,
        catId,
        name,
        slug);
    return id;
  }

  private RequestPostProcessor oidcLoginUser(String sub, String name) {
    return request -> {
      Collection<? extends GrantedAuthority> authorities =
          authorityContributor.mapAuthorities(
              List.of(
                  new OidcUserAuthority(
                      new OidcIdToken(
                          "test-token",
                          Instant.now(),
                          Instant.now().plusSeconds(3600),
                          Map.of(
                              IdTokenClaimNames.SUB,
                              sub,
                              IdTokenClaimNames.ISS,
                              ISSUER,
                              "name",
                              name,
                              "email",
                              sub + "@ufu.br")))));
      OidcUser oidcUser =
          new DefaultOidcUser(
              authorities,
              new OidcIdToken(
                  "test-token",
                  Instant.now(),
                  Instant.now().plusSeconds(3600),
                  Map.of(
                      IdTokenClaimNames.SUB,
                      sub,
                      IdTokenClaimNames.ISS,
                      ISSUER,
                      "name",
                      name,
                      "email",
                      sub + "@ufu.br")));
      request = csrf().postProcessRequest(request);
      return oidcLogin().oidcUser(oidcUser).postProcessRequest(request);
    };
  }

  @TestConfiguration
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("betufu-sports-tabular-import-it")
              .clientId("betufu-sports-tabular-import-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://idp.invalid/auth")
              .tokenUri("https://idp.invalid/token")
              .userInfoUri("https://idp.invalid/userinfo")
              .userNameAttributeName("sub")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
