package br.com.betufu.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.platform.session.ContextualLoginController;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Verifies the HTTP, CSRF double-submit, CORS origin allowlist and Problem Details security
 * contract of Plan 01-08, Task 1.
 */
@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      ContextualLoginController.class,
      CsrfSecurityIT.TestApplication.class,
      CsrfSecurityIT.CsrfProbeController.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "betufu.web.public-origin=http://localhost:4200",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration"
    })
class CsrfSecurityIT {

  private static final String COOKIE_NAME = "XSRF-TOKEN";
  private static final String HEADER_NAME = "X-XSRF-TOKEN";
  private static final String ALLOWED_ORIGIN = "http://localhost:4200";
  private static final String DISALLOWED_ORIGIN = "http://evil.attacker.invalid";
  private static final String PROBE_PATH = "/api/v1/csrf-probe";
  private static final String PUBLIC_ODDS_PROBE_PATH = "/api/v1/public/odds/security-probe";
  private static final String LOGOUT_PATH = "/api/v1/session/logout";

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private Filter springSecurityFilterChain;

  private MockMvc mockMvc;

  @BeforeEach
  void configureMockMvc() {
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
  }

  @Test
  void rejectsMutationsWithoutCsrfTokenWithSanitizedProblemDetails() throws Exception {
    mockMvc
        .perform(put(PROBE_PATH).with(oidcPrincipal()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"))
        .andExpect(jsonPath("$.title").value("Sem autorização"))
        .andExpect(jsonPath("$.detail").value("Você não tem acesso a esta área."))
        .andExpect(jsonPath("$.action").value("Retorne à página inicial."));

    mockMvc
        .perform(post(PROBE_PATH).with(oidcPrincipal()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"));

    mockMvc
        .perform(patch(PROBE_PATH).with(oidcPrincipal()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));

    mockMvc
        .perform(delete(PROBE_PATH).with(oidcPrincipal()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  void permitsAnonymousReadOfThePublicOddsBoundary() throws Exception {
    mockMvc
        .perform(get(PUBLIC_ODDS_PROBE_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PUBLIC_ODDS_OK"));
  }

  @Test
  void rejectsMutationsWithDivergentTokenValues() throws Exception {
    CsrfCookie tokenA = requestCsrfCookie();
    CsrfCookie tokenB = requestCsrfCookie();

    assertThat(tokenA.cookie().getValue()).isNotEqualTo(tokenB.cookie().getValue());

    // Header only (no cookie)
    mockMvc
        .perform(
            put(PROBE_PATH).with(oidcPrincipal()).header(HEADER_NAME, tokenA.cookie().getValue()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    // Cookie only (no header)
    mockMvc
        .perform(put(PROBE_PATH).with(oidcPrincipal()).cookie(tokenA.cookie()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    // Cookie A and Header B
    mockMvc
        .perform(
            put(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(tokenA.cookie())
                .header(HEADER_NAME, tokenB.cookie().getValue()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"))
        .andExpect(jsonPath("$.title").value("Sem autorização"))
        .andExpect(jsonPath("$.detail").value("Você não tem acesso a esta área."))
        .andExpect(jsonPath("$.action").value("Retorne à página inicial."));
  }

  @Test
  void acceptsMutationsWithMatchingCookieAndHeader() throws Exception {
    CsrfCookie token = requestCsrfCookie();

    mockMvc
        .perform(
            put(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(token.cookie())
                .header(HEADER_NAME, token.cookie().getValue()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(token.cookie())
                .header(HEADER_NAME, token.cookie().getValue()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            patch(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(token.cookie())
                .header(HEADER_NAME, token.cookie().getValue()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(token.cookie())
                .header(HEADER_NAME, token.cookie().getValue()))
        .andExpect(status().isNoContent());
  }

  @Test
  void rejectsMutationsFromDisallowedOrigin() throws Exception {
    CsrfCookie token = requestCsrfCookie();

    // Allowed origin succeeds
    mockMvc
        .perform(
            put(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(token.cookie())
                .header(HEADER_NAME, token.cookie().getValue())
                .header("Origin", ALLOWED_ORIGIN))
        .andExpect(status().isNoContent());

    // Disallowed origin is rejected (403)
    mockMvc
        .perform(
            put(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(token.cookie())
                .header(HEADER_NAME, token.cookie().getValue())
                .header("Origin", DISALLOWED_ORIGIN))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowsSafeRequestsWithoutCsrfHeader() throws Exception {
    mockMvc.perform(get("/login")).andExpect(status().isOk());

    mockMvc
        .perform(get(PROBE_PATH).with(oidcPrincipal()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("READ_OK"));
  }

  @Test
  void allowsAnonymousContextualLoginStartOnlyAfterStoringTheValidatedTarget() throws Exception {
    mockMvc
        .perform(get("/login/start").queryParam("returnTo", "/odds/mercados/market-1"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/oauth2/authorization/keycloak"))
        .andExpect(
            request()
                .sessionAttribute(
                    "betufu.session.contextual-login-target",
                    "/odds/mercados/market-1"));
  }

  @Test
  void logoutMutationRequiresCsrfProtection() throws Exception {
    // Without CSRF token: 403 Forbidden with Problem Details
    mockMvc
        .perform(post(LOGOUT_PATH).with(oidcPrincipal()))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    // With valid CSRF token: passes CSRF gate (controller handles logout or returns 200/502)
    CsrfCookie token = requestCsrfCookie();
    MvcResult result =
        mockMvc
            .perform(
                post(LOGOUT_PATH)
                    .with(oidcPrincipal())
                    .cookie(token.cookie())
                    .header(HEADER_NAME, token.cookie().getValue()))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
  }

  @Test
  void unauthenticatedProtectedEndpointReturnsSanitized401ProblemDetails() throws Exception {
    CsrfCookie token = requestCsrfCookie();

    MvcResult result =
        mockMvc
            .perform(
                put(PROBE_PATH)
                    .accept(MediaType.APPLICATION_PROBLEM_JSON)
                    .cookie(token.cookie())
                    .header(HEADER_NAME, token.cookie().getValue()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.code").value("BETUFU-401-UNAUTHORIZED"))
            .andExpect(jsonPath("$.title").value("Sessão expirada"))
            .andExpect(jsonPath("$.detail").value("Sua sessão expirou."))
            .andExpect(jsonPath("$.action").value("Entre novamente para continuar."))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("org.springframework")
        .doesNotContain("Exception")
        .doesNotContain("stackTrace")
        .doesNotContain("SESSION")
        .doesNotContain("token");
  }

  @Test
  void unauthenticatedApiRequestWithAngularAcceptHeaderReturns401InsteadOf302() throws Exception {
    CsrfCookie token = requestCsrfCookie();

    // Angular HttpClient standard Accept header
    mockMvc
        .perform(
            get(PROBE_PATH)
                .header("Accept", "application/json, text/plain, */*")
                .cookie(token.cookie()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("BETUFU-401-UNAUTHORIZED"))
        .andExpect(jsonPath("$.title").value("Sessão expirada"))
        .andExpect(jsonPath("$.detail").value("Sua sessão expirou."))
        .andExpect(jsonPath("$.action").value("Entre novamente para continuar."));
  }

  @Test
  void unauthenticatedBrowserNavigationReceives302ToIdp() throws Exception {
    mockMvc
        .perform(
            get("/dashboard")
                .header(
                    "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            header()
                .string(
                    "Location",
                    org.hamcrest.Matchers.containsString("/oauth2/authorization/keycloak")));
  }

  @Test
  void forbiddenResponseNeverLeaksInternalDetailsOrSecrets() throws Exception {
    MvcResult result =
        mockMvc
            .perform(put(PROBE_PATH).with(oidcPrincipal()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("org.springframework")
        .doesNotContain("InvalidCsrfTokenException")
        .doesNotContain("MissingCsrfTokenException")
        .doesNotContain("stackTrace")
        .doesNotContain("SESSION")
        .doesNotContain("XSRF");
  }

  private CsrfCookie requestCsrfCookie() throws Exception {
    MvcResult result = mockMvc.perform(get("/login")).andExpect(status().isOk()).andReturn();
    Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
    List<String> setCookieHeaders = result.getResponse().getHeaders("Set-Cookie");
    String setCookieHeader =
        setCookieHeaders.stream()
            .filter(header -> header.startsWith(COOKIE_NAME + "="))
            .findFirst()
            .orElseThrow(() -> new AssertionError("cookie XSRF esperado nao foi emitido"));

    assertThat(cookie).isNotNull();
    return new CsrfCookie(cookie, setCookieHeader);
  }

  private RequestPostProcessor oidcPrincipal() {
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");
    assertThat(registration).isNotNull();
    return oidcLogin().clientRegistration(registration);
  }

  private record CsrfCookie(Cookie cookie, String setCookieHeader) {}

  @RestController
  static class CsrfProbeController {

    @GetMapping(PROBE_PATH)
    ResponseEntity<Map<String, String>> readGet() {
      return ResponseEntity.ok(Map.of("status", "READ_OK"));
    }

    @GetMapping(PUBLIC_ODDS_PROBE_PATH)
    ResponseEntity<Map<String, String>> readPublicOdds() {
      return ResponseEntity.ok(Map.of("status", "PUBLIC_ODDS_OK"));
    }

    @PutMapping(PROBE_PATH)
    ResponseEntity<Void> mutatePut() {
      return ResponseEntity.noContent().build();
    }

    @PostMapping(PROBE_PATH)
    ResponseEntity<Void> mutatePost() {
      return ResponseEntity.noContent().build();
    }

    @PatchMapping(PROBE_PATH)
    ResponseEntity<Void> mutatePatch() {
      return ResponseEntity.noContent().build();
    }

    @DeleteMapping(PROBE_PATH)
    ResponseEntity<Void> mutateDelete() {
      return ResponseEntity.noContent().build();
    }

    @PostMapping(LOGOUT_PATH)
    ResponseEntity<Map<String, String>> logout() {
      return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-csrf-security-it")
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
              .clientName("BETUFU CSRF SECURITY IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
