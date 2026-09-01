package br.com.betufu.identity;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.identity.internal.LocalIdentity;
import br.com.betufu.identity.internal.LocalIdentityService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.Filter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-local-identity-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(LocalIdentityTracerIT.TestOidcConfiguration.class)
class LocalIdentityTracerIT {

  private static final String CURRENT_IDENTITY_PATH = "/api/v1/tracer/identity/current";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Autowired private LocalIdentityService localIdentityService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private Flyway flyway;

  @Autowired private EntityManagerFactory entityManagerFactory;

  @Autowired private Environment environment;

  @Autowired private WebApplicationContext applicationContext;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter springSecurityFilterChain;

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  private MockMvc mockMvc;

  @BeforeEach
  void prepareTest() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
  }

  @Test
  void appliesTheAuthoritativeSchemaOnPostgresql18() {
    assertThat(POSTGRESQL.getDockerImageName()).isEqualTo(POSTGRES_IMAGE_REFERENCE);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::integer / 10000", Integer.class))
        .isEqualTo(18);
    assertThat(flyway.info().applied())
        .anySatisfy(
            migration -> {
              assertThat(migration.getVersion()).isNotNull();
              assertThat(migration.getVersion().getVersion()).isEqualTo("1");
              assertThat(migration.getState()).isEqualTo(MigrationState.SUCCESS);
            });
    assertThat(entityManagerFactory.isOpen()).isTrue();
    assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("true");

    List<ColumnContract> columns =
        jdbcTemplate.query(
            """
                SELECT attribute.attname,
                       pg_catalog.format_type(attribute.atttypid, attribute.atttypmod),
                       collation_record.collname,
                       attribute.attnotnull,
                       pg_catalog.pg_get_expr(
                               default_record.adbin,
                               default_record.adrelid,
                               true)
                FROM pg_catalog.pg_attribute attribute
                JOIN pg_catalog.pg_class relation
                  ON relation.oid = attribute.attrelid
                JOIN pg_catalog.pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                LEFT JOIN pg_catalog.pg_collation collation_record
                  ON collation_record.oid = attribute.attcollation
                LEFT JOIN pg_catalog.pg_attrdef default_record
                  ON default_record.adrelid = attribute.attrelid
                 AND default_record.adnum = attribute.attnum
                WHERE namespace.nspname = 'public'
                  AND relation.relname = 'local_identity'
                  AND attribute.attnum > 0
                  AND NOT attribute.attisdropped
                ORDER BY attribute.attnum
                """,
            (resultSet, rowNumber) ->
                new ColumnContract(
                    resultSet.getString(1),
                    resultSet.getString(2),
                    resultSet.getString(3),
                    resultSet.getBoolean(4),
                    resultSet.getString(5)));

    assertThat(columns)
        .containsExactly(
            new ColumnContract("identity_id", "uuid", null, true, "gen_random_uuid()"),
            new ColumnContract("issuer", "character varying(2048)", "C", true, null),
            new ColumnContract("subject", "character varying(255)", "C", true, null),
            new ColumnContract(
                "created_at", "timestamp(6) with time zone", null, true, "CURRENT_TIMESTAMP(6)"),
            new ColumnContract(
                "updated_at", "timestamp(6) with time zone", null, true, "CURRENT_TIMESTAMP(6)"));

    List<ConstraintContract> constraints =
        jdbcTemplate.query(
            """
                        SELECT constraint_record.conname,
                               pg_catalog.pg_get_constraintdef(
                                       constraint_record.oid,
                                       true)
                        FROM pg_catalog.pg_constraint constraint_record
                        WHERE constraint_record.conrelid = 'public.local_identity'::regclass
                          AND constraint_record.contype IN ('p', 'u', 'c')
                        ORDER BY constraint_record.conname
                        """,
            (resultSet, rowNumber) ->
                new ConstraintContract(resultSet.getString(1), resultSet.getString(2)));

    assertThat(constraints)
        .containsExactly(
            new ConstraintContract(
                "ck_local_identity_issuer_not_empty", "CHECK (issuer::text <> ''::text)"),
            new ConstraintContract(
                "ck_local_identity_issuer_octets", "CHECK (octet_length(issuer::text) <= 2048)"),
            new ConstraintContract(
                "ck_local_identity_subject_not_empty", "CHECK (subject::text <> ''::text)"),
            new ConstraintContract(
                "ck_local_identity_subject_octets", "CHECK (octet_length(subject::text) <= 255)"),
            new ConstraintContract(
                "ck_local_identity_timestamp_order", "CHECK (updated_at >= created_at)"),
            new ConstraintContract("pk_local_identity", "PRIMARY KEY (identity_id)"),
            new ConstraintContract("uq_local_identity_issuer_subject", "UNIQUE (issuer, subject)"));
  }

  @Test
  void persistsTheOidcIdentityAndReturnsOnlyTheOpenApiFields() throws Exception {
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "http-contract-subject";

    performCurrentIdentityPut(issuer, subject, "first@example.invalid")
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", aMapWithSize(4)))
        .andExpect(jsonPath("$.identityId").value(matchesPattern(UUID_PATTERN)))
        .andExpect(jsonPath("$.status").value("PERSISTED"))
        .andExpect(jsonPath("$.createdAt").value(not(emptyOrNullString())))
        .andExpect(jsonPath("$.updatedAt").value(not(emptyOrNullString())))
        .andExpect(jsonPath("$.issuer").doesNotExist())
        .andExpect(jsonPath("$.subject").doesNotExist())
        .andExpect(jsonPath("$.email").doesNotExist())
        .andExpect(jsonPath("$.claims").doesNotExist())
        .andExpect(jsonPath("$.token").doesNotExist());

    assertThat(rowCount(issuer, subject)).isEqualTo(1L);
  }

  @Test
  void repeatedHttpCallsIgnoreEmailAndReturnTheSameRepresentation() throws Exception {
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "http-stable-subject";

    MvcResult first =
        performCurrentIdentityPut(issuer, subject, "first@example.invalid")
            .andExpect(status().isOk())
            .andReturn();
    MvcResult second =
        performCurrentIdentityPut(issuer, subject, "changed@example.invalid")
            .andExpect(status().isOk())
            .andReturn();

    PersistedIdentity persistedIdentity = findPersistedIdentity(issuer, subject);

    assertResponseMatches(first, persistedIdentity);
    assertResponseMatches(second, persistedIdentity);
    assertThat(responseBody(second)).isEqualTo(responseBody(first));
    assertThat(rowCount(issuer, subject)).isEqualTo(1L);
  }

  @Test
  void differentIssuerSubjectOrCaseProduceDifferentIdentities() throws Exception {
    performCurrentIdentityPut(
            "https://idp.invalid/realms/betufu", "case-subject", "case@example.invalid")
        .andExpect(status().isOk());
    performCurrentIdentityPut(
            "https://other-idp.invalid/realms/betufu", "case-subject", "case@example.invalid")
        .andExpect(status().isOk());
    performCurrentIdentityPut(
            "https://idp.invalid/realms/betufu", "other-subject", "case@example.invalid")
        .andExpect(status().isOk());
    performCurrentIdentityPut(
            "https://IDP.invalid/realms/BETUFU", "CASE-SUBJECT", "case@example.invalid")
        .andExpect(status().isOk());

    List<UUID> identityIds =
        jdbcTemplate.queryForList(
            "SELECT identity_id FROM public.local_identity ORDER BY identity_id", UUID.class);

    assertThat(identityIds).hasSize(4).doesNotHaveDuplicates();
  }

  @Test
  void concurrentHttpCallsConvergeOnOneRepresentation() throws Exception {
    int workers = 8;
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "http-concurrent-subject";
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MvcResult>> futures = new ArrayList<>();

    try {
      for (int worker = 0; worker < workers; worker++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  assertThat(start.await(10, SECONDS)).isTrue();
                  return performCurrentIdentityPut(issuer, subject, "concurrent@example.invalid")
                      .andReturn();
                }));
      }

      assertThat(ready.await(10, SECONDS)).isTrue();
      start.countDown();

      List<MvcResult> results = new ArrayList<>();
      for (Future<MvcResult> future : futures) {
        results.add(future.get(20, SECONDS));
      }

      PersistedIdentity persistedIdentity = findPersistedIdentity(issuer, subject);
      String expectedBody = responseBody(results.getFirst());

      assertThat(results)
          .allSatisfy(
              result -> {
                assertThat(result.getResponse().getStatus()).isEqualTo(200);
                assertThat(responseBody(result)).isEqualTo(expectedBody);
                assertResponseMatches(result, persistedIdentity);
              });
      assertThat(rowCount(issuer, subject)).isEqualTo(1L);
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void anonymousRequestsAreRedirectedWithoutPersisting() throws Exception {
    mockMvc.perform(put(CURRENT_IDENTITY_PATH).with(csrf())).andExpect(status().is3xxRedirection());

    assertThat(totalRowCount()).isZero();
  }

  @Test
  void requestsWithoutCsrfAreForbiddenWithoutPersisting() throws Exception {
    mockMvc
        .perform(
            put(CURRENT_IDENTITY_PATH)
                .with(
                    oidcPrincipal(
                        "https://idp.invalid/realms/betufu",
                        "missing-csrf",
                        "csrf@example.invalid")))
        .andExpect(status().isForbidden());

    assertThat(totalRowCount()).isZero();
  }

  @Test
  void authenticatedNonOidcPrincipalsReceiveGeneric401WithoutPersisting() throws Exception {
    mockMvc
        .perform(put(CURRENT_IDENTITY_PATH).with(user("non-oidc-principal")).with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));

    assertThat(totalRowCount()).isZero();
  }

  @Test
  void missingEmptyOrBlankIdentityClaimsReceiveGeneric401WithoutPersisting() throws Exception {
    assertRejectedIdentityClaims(null, "valid-subject");
    assertRejectedIdentityClaims("", "valid-subject");
    assertRejectedIdentityClaims("   ", "valid-subject");
    assertRejectedIdentityClaims("https://idp.invalid/realms/betufu", null);
    assertRejectedIdentityClaims("https://idp.invalid/realms/betufu", "");
    assertRejectedIdentityClaims("https://idp.invalid/realms/betufu", "   ");

    assertThat(totalRowCount()).isZero();
  }

  @Test
  void requestBodyQueryAndHeadersCannotSpoofTheOidcIdentity() throws Exception {
    String authenticatedIssuer = "https://idp.invalid/realms/betufu";
    String authenticatedSubject = "authenticated-subject";
    String spoofedIssuer = "https://attacker.invalid/realms/spoofed";
    String spoofedSubject = "spoofed-subject";

    mockMvc
        .perform(
            put(CURRENT_IDENTITY_PATH)
                .with(
                    oidcPrincipal(
                        authenticatedIssuer, authenticatedSubject, "authenticated@example.invalid"))
                .with(csrf())
                .queryParam("iss", spoofedIssuer)
                .queryParam("sub", spoofedSubject)
                .queryParam("email", "spoofed@example.invalid")
                .header("iss", spoofedIssuer)
                .header("sub", spoofedSubject)
                .header("email", "spoofed@example.invalid")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "iss": "https://attacker.invalid/realms/spoofed",
                                  "sub": "spoofed-subject",
                                  "email": "spoofed@example.invalid"
                                }
                                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", aMapWithSize(4)));

    assertThat(rowCount(authenticatedIssuer, authenticatedSubject)).isEqualTo(1L);
    assertThat(rowCount(spoofedIssuer, spoofedSubject)).isZero();
    assertThat(totalRowCount()).isEqualTo(1L);
  }

  @Test
  void userInfoCannotSupplyIdentityClaimsMissingFromTheIdToken() throws Exception {
    String idTokenIssuer = "https://idp.invalid/realms/betufu";
    String idTokenSubject = "id-token-subject";
    String userInfoIssuer = "https://attacker.invalid/realms/user-info";
    String userInfoSubject = "user-info-subject";

    mockMvc
        .perform(
            put(CURRENT_IDENTITY_PATH)
                .with(
                    oidcPrincipalWithUserInfo(
                        null, idTokenSubject, userInfoIssuer, userInfoSubject))
                .with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));

    mockMvc
        .perform(
            put(CURRENT_IDENTITY_PATH)
                .with(
                    oidcPrincipalWithUserInfo(idTokenIssuer, null, userInfoIssuer, userInfoSubject))
                .with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));

    assertThat(totalRowCount()).isZero();
  }

  @Test
  void rejectsDirectDuplicateNaturalKeysWithSqlState23505() {
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "duplicate-subject";
    directInsert(issuer, subject);

    DataIntegrityViolationException duplicate =
        catchThrowableOfType(
            () -> directInsert(issuer, subject), DataIntegrityViolationException.class);

    assertSqlState(duplicate, "23505");
  }

  @Test
  void enforcesTheApprovedUtf8ByteLimits() {
    DataIntegrityViolationException issuerTooLarge =
        catchThrowableOfType(
            () -> directInsert("é".repeat(1025), "valid-subject"),
            DataIntegrityViolationException.class);
    assertSqlState(issuerTooLarge, "23514");

    DataIntegrityViolationException subjectTooLarge =
        catchThrowableOfType(
            () -> directInsert("https://idp.invalid/realms/betufu", "é".repeat(128)),
            DataIntegrityViolationException.class);
    assertSqlState(subjectTooLarge, "23514");
  }

  @Test
  void enforcesTheApprovedNonEmptyAndTimestampChecks() {
    DataIntegrityViolationException emptyIssuer =
        catchThrowableOfType(
            () -> directInsert("", "valid-subject"), DataIntegrityViolationException.class);
    assertSqlStateAndConstraint(emptyIssuer, "23514", "ck_local_identity_issuer_not_empty");

    DataIntegrityViolationException emptySubject =
        catchThrowableOfType(
            () -> directInsert("https://idp.invalid/realms/betufu", ""),
            DataIntegrityViolationException.class);
    assertSqlStateAndConstraint(emptySubject, "23514", "ck_local_identity_subject_not_empty");

    DataIntegrityViolationException reversedTimestamps =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.local_identity (
                            issuer,
                            subject,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP - INTERVAL '1 second')
                        """,
                    "https://idp.invalid/realms/betufu",
                    "reversed-timestamps"),
            DataIntegrityViolationException.class);
    assertSqlStateAndConstraint(reversedTimestamps, "23514", "ck_local_identity_timestamp_order");
  }

  @Test
  void repeatedCallsReturnTheSameIdentityAndStableTimestamps() {
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "stable-subject";

    LocalIdentity first = localIdentityService.persistCurrentIdentity(issuer, subject);
    LocalIdentity second = localIdentityService.persistCurrentIdentity(issuer, subject);

    assertThat(second.getIdentityId()).isEqualTo(first.getIdentityId());
    assertThat(second.getCreatedAt()).isEqualTo(first.getCreatedAt());
    assertThat(second.getUpdatedAt()).isEqualTo(first.getUpdatedAt());
    assertThat(first.getUpdatedAt()).isEqualTo(first.getCreatedAt());
    assertThat(rowCount(issuer, subject)).isEqualTo(1L);
  }

  @Test
  void concurrentCallsConvergeOnOneIdentity() throws Exception {
    int workers = 8;
    String issuer = "https://idp.invalid/realms/betufu";
    String subject = "concurrent-subject";
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    CountDownLatch ready = new CountDownLatch(workers);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<LocalIdentity>> futures = new ArrayList<>();

    try {
      for (int worker = 0; worker < workers; worker++) {
        futures.add(
            executor.submit(
                () -> {
                  ready.countDown();
                  assertThat(start.await(10, SECONDS)).isTrue();
                  return localIdentityService.persistCurrentIdentity(issuer, subject);
                }));
      }

      assertThat(ready.await(10, SECONDS)).isTrue();
      start.countDown();

      List<LocalIdentity> identities = new ArrayList<>();
      for (Future<LocalIdentity> future : futures) {
        identities.add(future.get(20, SECONDS));
      }

      UUID expectedIdentityId = identities.getFirst().getIdentityId();
      Instant expectedCreatedAt = identities.getFirst().getCreatedAt();
      Instant expectedUpdatedAt = identities.getFirst().getUpdatedAt();

      assertThat(identities)
          .extracting(LocalIdentity::getIdentityId)
          .containsOnly(expectedIdentityId);
      assertThat(identities)
          .extracting(LocalIdentity::getCreatedAt)
          .containsOnly(expectedCreatedAt);
      assertThat(identities)
          .extracting(LocalIdentity::getUpdatedAt)
          .containsOnly(expectedUpdatedAt);
      assertThat(rowCount(issuer, subject)).isEqualTo(1L);
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void preservesTheNaturalKeyWithoutCaseWhitespaceOrUnicodeNormalization() {
    String upperIssuer = "https://IDP.invalid/realms/BETUFU";
    String lowerIssuer = "https://idp.invalid/realms/betufu";
    String upperSubject = "Opaque-Subject";
    String lowerSubject = "opaque-subject";
    String spacedSubject = " opaque-subject ";
    String composedSubject = "opaque-\u00e9";
    String decomposedSubject = "opaque-e\u0301";
    String spacedIssuer = " https://idp.invalid/realms/betufu ";
    String composedIssuer = "https://idp.invalid/realms/betuf\u00e9";
    String decomposedIssuer = "https://idp.invalid/realms/betufe\u0301";

    LocalIdentity upper = localIdentityService.persistCurrentIdentity(upperIssuer, upperSubject);
    LocalIdentity lower = localIdentityService.persistCurrentIdentity(lowerIssuer, lowerSubject);
    LocalIdentity spaced = localIdentityService.persistCurrentIdentity(lowerIssuer, spacedSubject);
    LocalIdentity composed =
        localIdentityService.persistCurrentIdentity(lowerIssuer, composedSubject);
    LocalIdentity decomposed =
        localIdentityService.persistCurrentIdentity(lowerIssuer, decomposedSubject);
    LocalIdentity spacedIssuerIdentity =
        localIdentityService.persistCurrentIdentity(spacedIssuer, "issuer-whitespace");
    LocalIdentity trimmedIssuerIdentity =
        localIdentityService.persistCurrentIdentity(lowerIssuer, "issuer-whitespace");
    LocalIdentity composedIssuerIdentity =
        localIdentityService.persistCurrentIdentity(composedIssuer, "issuer-unicode");
    LocalIdentity decomposedIssuerIdentity =
        localIdentityService.persistCurrentIdentity(decomposedIssuer, "issuer-unicode");

    assertThat(lower.getIdentityId()).isNotEqualTo(upper.getIdentityId());
    assertThat(upper.getIssuer()).isEqualTo(upperIssuer);
    assertThat(upper.getSubject()).isEqualTo(upperSubject);
    assertThat(lower.getIssuer()).isEqualTo(lowerIssuer);
    assertThat(lower.getSubject()).isEqualTo(lowerSubject);
    assertThat(spaced.getSubject()).isEqualTo(spacedSubject);
    assertThat(composed.getSubject()).isEqualTo(composedSubject);
    assertThat(decomposed.getSubject()).isEqualTo(decomposedSubject);
    assertThat(spaced.getIdentityId()).isNotEqualTo(lower.getIdentityId());
    assertThat(composed.getIdentityId()).isNotEqualTo(decomposed.getIdentityId());
    assertThat(spacedIssuerIdentity.getIssuer()).isEqualTo(spacedIssuer);
    assertThat(trimmedIssuerIdentity.getIssuer()).isEqualTo(lowerIssuer);
    assertThat(spacedIssuerIdentity.getIdentityId())
        .isNotEqualTo(trimmedIssuerIdentity.getIdentityId());
    assertThat(composedIssuerIdentity.getIssuer()).isEqualTo(composedIssuer);
    assertThat(decomposedIssuerIdentity.getIssuer()).isEqualTo(decomposedIssuer);
    assertThat(composedIssuerIdentity.getIdentityId())
        .isNotEqualTo(decomposedIssuerIdentity.getIdentityId());
    assertThat(
            jdbcTemplate.queryForObject("SELECT count(*) FROM public.local_identity", Long.class))
        .isEqualTo(9L);
  }

  private ResultActions performCurrentIdentityPut(String issuer, String subject, String email)
      throws Exception {
    return mockMvc.perform(
        put(CURRENT_IDENTITY_PATH).with(oidcPrincipal(issuer, subject, email)).with(csrf()));
  }

  private RequestPostProcessor oidcPrincipal(String issuer, String subject, String email) {
    OidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("OIDC_USER"),
            oidcIdToken(issuer, subject, email),
            "fixture_name");

    return oidcPrincipal(oidcUser);
  }

  private RequestPostProcessor oidcPrincipalWithUserInfo(
      String idTokenIssuer, String idTokenSubject, String userInfoIssuer, String userInfoSubject) {
    OidcUserInfo userInfo =
        OidcUserInfo.builder()
            .subject(userInfoSubject)
            .claim(IdTokenClaimNames.ISS, userInfoIssuer)
            .build();
    OidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("OIDC_USER"),
            oidcIdToken(idTokenIssuer, idTokenSubject, "id-token@example.invalid"),
            userInfo,
            "fixture_name");

    return oidcPrincipal(oidcUser);
  }

  private OidcIdToken oidcIdToken(String issuer, String subject, String email) {
    Instant now = Instant.now();
    OidcIdToken.Builder tokenBuilder =
        OidcIdToken.withTokenValue("local-identity-it-" + UUID.randomUUID())
            .issuedAt(now.minusSeconds(60))
            .expiresAt(now.plusSeconds(300))
            .claim("fixture_name", "local-identity-it-user");

    if (issuer != null) {
      tokenBuilder.claim(IdTokenClaimNames.ISS, issuer);
    }
    if (subject != null) {
      tokenBuilder.claim(IdTokenClaimNames.SUB, subject);
    }
    if (email != null) {
      tokenBuilder.claim("email", email);
    }

    return tokenBuilder.build();
  }

  private RequestPostProcessor oidcPrincipal(OidcUser oidcUser) {
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");

    assertThat(registration).isNotNull();

    return oidcLogin().oidcUser(oidcUser).clientRegistration(registration);
  }

  private void assertRejectedIdentityClaims(String issuer, String subject) throws Exception {
    performCurrentIdentityPut(issuer, subject, "ignored@example.invalid")
        .andExpect(status().isUnauthorized())
        .andExpect(content().string(""));
  }

  private PersistedIdentity findPersistedIdentity(String issuer, String subject) {
    return jdbcTemplate.queryForObject(
        """
                SELECT identity_id, created_at, updated_at
                FROM public.local_identity
                WHERE issuer = ?
                  AND subject = ?
                """,
        (resultSet, rowNumber) ->
            new PersistedIdentity(
                resultSet.getObject("identity_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()),
        issuer,
        subject);
  }

  private static void assertResponseMatches(MvcResult result, PersistedIdentity persistedIdentity) {
    assertThat(responseBody(result))
        .contains("\"identityId\":\"" + persistedIdentity.identityId() + "\"")
        .contains("\"status\":\"PERSISTED\"")
        .contains("\"createdAt\":\"" + persistedIdentity.createdAt() + "\"")
        .contains("\"updatedAt\":\"" + persistedIdentity.updatedAt() + "\"")
        .doesNotContain("issuer", "subject", "email", "claims", "token");
  }

  private static String responseBody(MvcResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private int directInsert(String issuer, String subject) {
    return jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)", issuer, subject);
  }

  private long rowCount(String issuer, String subject) {
    return jdbcTemplate.queryForObject(
        """
                SELECT count(*)
                FROM public.local_identity
                WHERE issuer = ?
                  AND subject = ?
                """,
        Long.class,
        issuer,
        subject);
  }

  private long totalRowCount() {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM public.local_identity", Long.class);
  }

  private static void assertSqlState(
      DataIntegrityViolationException failure, String expectedSqlState) {
    assertThat(failure).isNotNull();
    assertThat(failure.getMostSpecificCause()).isInstanceOf(PSQLException.class);

    PSQLException postgresFailure = (PSQLException) failure.getMostSpecificCause();
    assertThat(postgresFailure.getSQLState()).isEqualTo(expectedSqlState);
  }

  private static void assertSqlStateAndConstraint(
      DataIntegrityViolationException failure, String expectedSqlState, String expectedConstraint) {
    assertSqlState(failure, expectedSqlState);

    PSQLException postgresFailure = (PSQLException) failure.getMostSpecificCause();
    assertThat(postgresFailure.getServerErrorMessage()).isNotNull();
    assertThat(postgresFailure.getServerErrorMessage().getConstraint())
        .isEqualTo(expectedConstraint);
  }

  private record ColumnContract(
      String name, String type, String collation, boolean notNull, String defaultExpression) {}

  private record ConstraintContract(String name, String definition) {}

  private record PersistedIdentity(UUID identityId, Instant createdAt, Instant updatedAt) {}

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-local-identity-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Local Identity IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
