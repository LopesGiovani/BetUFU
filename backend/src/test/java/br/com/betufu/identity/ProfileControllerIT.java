package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.Filter;
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
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-profile-controller-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused"
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(ProfileControllerIT.TestOidcConfiguration.class)
class ProfileControllerIT {

    private static final String PROFILE_ME_PATH = "/api/v1/profile/me";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_app")
            .withUsername("betufu_app")
            .withPassword("betufu-test-only")
            .withInitScript("postgres-audit-runtime-role.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.audit_event, public.betufu_profile, public.local_identity");

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("Requisição não autenticada para GET ou PUT /api/v1/profile/me é redirecionada ou recusada")
    void unauthenticatedRequestIsRedirectedOrForbidden() throws Exception {
        mockMvc.perform(get(PROFILE_ME_PATH))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(put(PROFILE_ME_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"giovani\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Usuário autenticado sem perfil criado recebe HTTP 404 Not Found na leitura")
    void authenticatedUserWithoutProfileReceives404() throws Exception {
        mockMvc.perform(get(PROFILE_ME_PATH)
                        .with(oidcPrincipal("https://idp.invalid", "user-no-profile")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PROF-01: Usuário grava apelido via PUT e relê via GET com sucesso")
    void authenticatedUserCreatesAndReadsProfile() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-profile-crud";

        // 1. Grava o apelido
        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"giovani_99\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.identityId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.nickname").value("giovani_99"))
                .andExpect(jsonPath("$.nicknameUpdatedAt").value(emptyOrNullString()))
                .andExpect(jsonPath("$.matriculaStatus").value("NAO_REGISTRADA"));

        // 2. Relê o perfil
        mockMvc.perform(get(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.identityId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(jsonPath("$.nickname").value("giovani_99"))
                .andExpect(jsonPath("$.nicknameUpdatedAt").value(emptyOrNullString()))
                .andExpect(jsonPath("$.matriculaStatus").value("NAO_REGISTRADA"));
    }

    @Test
    @DisplayName("PROF-01: Espaços externos são removidos antes da validação e persistência")
    void trimsOuterWhitespaceBeforeValidationAndPersistence() throws Exception {
        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal("https://idp.invalid", "user-profile-trim"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\" giovani_99 \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("giovani_99"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT nickname FROM public.betufu_profile", String.class))
                .isEqualTo("giovani_99");
    }

    @Test
    @DisplayName("Idempotência: Gravar o mesmo apelido é inócuo e NÃO avança updated_at no banco")
    void savingSameNicknameIsIdempotentAndDoesNotAdvanceUpdatedAt() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-idempotent";

        // 1. Primeira gravação
        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"same_nick\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("same_nick"));

        Instant firstUpdatedAt = jdbcTemplate.queryForObject(
                """
                SELECT p.updated_at
                FROM public.betufu_profile p
                JOIN public.local_identity i ON i.identity_id = p.identity_id
                WHERE i.issuer = ? AND i.subject = ?
                """,
                Instant.class,
                issuer,
                subject);

        assertThat(firstUpdatedAt).isNotNull();

        // 2. Segunda gravação com o mesmo apelido
        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"same_nick\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("same_nick"));

        Instant secondUpdatedAt = jdbcTemplate.queryForObject(
                """
                SELECT p.updated_at
                FROM public.betufu_profile p
                JOIN public.local_identity i ON i.identity_id = p.identity_id
                WHERE i.issuer = ? AND i.subject = ?
                """,
                Instant.class,
                issuer,
                subject);

        // updated_at deve permanecer rigorosamente inalterado
        assertThat(secondUpdatedAt).isEqualTo(firstUpdatedAt);
    }

    @Test
    @DisplayName("Troca inicial é permitida, mas nova troca antes de 30 dias retorna erro estável 422")
    void nicknameCooldownReturnsStableProblemDetailsWithoutInternalMessage() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-nickname-cooldown";

        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"initial_nick\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"first_change\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("first_change"));

        MvcResult result = mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"blocked_change\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BETUFU-422-NICKNAME_COOLDOWN"))
                .andExpect(jsonPath("$.title").value("Intervalo de troca de apelido"))
                .andExpect(jsonPath("$.detail").value(
                        "O intervalo mínimo entre trocas de apelido ainda não foi cumprido."))
                .andExpect(jsonPath("$.action").value(
                        "Aguarde o prazo mínimo de 30 dias para realizar uma nova troca."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("minimum interval")
                .doesNotContain("Nickname change rejected");
    }

    @Test
    @DisplayName("Troca de apelido não retrocede updated_at quando o relógio do banco está adiantado")
    void nicknameChangeClampsTimestampToDatabaseCreatedAt() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-clock-skew";

        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"initial_nick\"}"))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                """
                UPDATE public.betufu_profile p
                SET created_at = CURRENT_TIMESTAMP + INTERVAL '1 minute',
                    updated_at = CURRENT_TIMESTAMP + INTERVAL '1 minute'
                FROM public.local_identity i
                WHERE i.identity_id = p.identity_id
                  AND i.issuer = ?
                  AND i.subject = ?
                """,
                issuer,
                subject);

        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"changed_nick\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("changed_nick"));

        Integer invalidTimestampRows = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.betufu_profile p
                JOIN public.local_identity i ON i.identity_id = p.identity_id
                WHERE i.issuer = ?
                  AND i.subject = ?
                  AND p.updated_at < p.created_at
                """,
                Integer.class,
                issuer,
                subject);

        assertThat(invalidTimestampRows).isZero();
    }

    @Test
    @DisplayName("Bean Validation: Recusa apelido vazio e fora do padrão com mensagens literais do UI-SPEC")
    void validationRejectsInvalidInputsWithPtBrMessages() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-validation";

        // 1. Apelido em branco
        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nickname')].message")
                        .value(org.hamcrest.Matchers.hasItem("Informe um apelido para continuar.")));

        // 2. Homoglifo cirílico / fora do padrão
        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"giov\\u0430ni\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'nickname')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).")));

        // 3. Garante que o valor rejeitado não é refletido na resposta de erro (PRIV-01)
        MvcResult errorResult = mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"rejected_secret_val!\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = new String(errorResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).doesNotContain("rejected_secret_val!");
    }

    @Test
    @DisplayName("PROF-02 / PRIV-01: Resposta do perfil não vaza email, matrícula, HMAC, papéis, time ou MFA")
    void responseDoesNotExposeForbiddenFields() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-allowlist-check";

        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"clean_user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.identityId").exists())
                .andExpect(jsonPath("$.nickname").value("clean_user"))
                .andExpect(jsonPath("$.nicknameUpdatedAt").doesNotExist())
                .andExpect(jsonPath("$.matriculaStatus").value("NAO_REGISTRADA"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.matricula").doesNotExist())
                .andExpect(jsonPath("$.hmac").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.favoriteTeamId").doesNotExist())
                .andExpect(jsonPath("$.favorite_team").doesNotExist())
                .andExpect(jsonPath("$.mfa").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.claims").doesNotExist())
                .andExpect(jsonPath("$.issuer").doesNotExist())
                .andExpect(jsonPath("$.subject").doesNotExist());
    }

    @Test
    @DisplayName("Corpo, query e headers da requisição não podem falsificar o titular do perfil")
    void requestBodyQueryAndHeadersCannotSpoofIdentity() throws Exception {
        String authenticatedIssuer = "https://idp.invalid";
        String authenticatedSubject = "legitimate-user";
        UUID spoofedIdentityId = UUID.randomUUID();

        mockMvc.perform(put(PROFILE_ME_PATH)
                        .with(oidcPrincipal(authenticatedIssuer, authenticatedSubject))
                        .with(csrf())
                        .queryParam("identityId", spoofedIdentityId.toString())
                        .queryParam("sub", "attacker-subject")
                        .header("identityId", spoofedIdentityId.toString())
                        .header("sub", "attacker-subject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "legit_nick",
                                  "identityId": "%s",
                                  "sub": "attacker-subject"
                                }
                                """.formatted(spoofedIdentityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("legit_nick"))
                .andExpect(jsonPath("$.identityId").value(not(spoofedIdentityId.toString())));

        UUID actualIdentityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                authenticatedIssuer,
                authenticatedSubject);

        assertThat(actualIdentityId).isNotNull().isNotEqualTo(spoofedIdentityId);
    }

    private RequestPostProcessor oidcPrincipal(String issuer, String subject) {
        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("OIDC_USER"),
                oidcIdToken(issuer, subject),
                "fixture_name");

        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId("keycloak");

        assertThat(registration).isNotNull();

        return oidcLogin()
                .oidcUser(oidcUser)
                .clientRegistration(registration);
    }

    private OidcIdToken oidcIdToken(String issuer, String subject) {
        Instant now = Instant.now();
        return OidcIdToken.withTokenValue("profile-it-" + UUID.randomUUID())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .claim(IdTokenClaimNames.ISS, issuer)
                .claim(IdTokenClaimNames.SUB, subject)
                .claim("fixture_name", "profile-it-user")
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-profile-controller-it")
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
                    .clientName("BETUFU Profile Controller IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
