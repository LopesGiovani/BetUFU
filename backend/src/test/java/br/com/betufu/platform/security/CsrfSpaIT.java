package br.com.betufu.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      CsrfSpaIT.TestApplication.class,
      CsrfSpaIT.CsrfProbeController.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
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
class CsrfSpaIT {

  private static final String COOKIE_NAME = "XSRF-TOKEN";
  private static final String HEADER_NAME = "X-XSRF-TOKEN";
  private static final String PROBE_PATH = "/api/v1/csrf-probe";

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
  void materializesTheApprovedCookieOnASafeRequest() throws Exception {
    CsrfCookie csrfCookie = requestCsrfCookie(get("/login"));

    assertThat(csrfCookie.setCookieHeader())
        .contains("Path=/")
        .doesNotContain("HttpOnly")
        .doesNotContain("Domain=")
        .doesNotContain("; Secure");
    assertThat(csrfCookie.cookie().getAttribute("SameSite")).isEqualTo("Lax");
    assertThat(csrfCookie.cookie().getValue()).isNotBlank();
  }

  @Test
  void derivesSecureFromTheMockRequestSecurityState() throws Exception {
    // MockMvc does not start Tomcat's RemoteIpValve. The runtime checkpoint owns the
    // trusted-proxy proof; this focal test verifies only CookieCsrfTokenRepository.
    CsrfCookie plainCookie = requestCsrfCookie(get("/login"));
    CsrfCookie secureCookie = requestCsrfCookie(get("/login").secure(true));

    assertThat(plainCookie.setCookieHeader()).doesNotContain("; Secure");
    assertThat(secureCookie.setCookieHeader()).contains("; Secure");
  }

  @Test
  void rejectsMissingOrDivergentDoubleSubmitValues() throws Exception {
    CsrfCookie csrfCookie = requestCsrfCookie(get("/login"));

    mockMvc.perform(put(PROBE_PATH).with(oidcPrincipal())).andExpect(status().isForbidden());

    mockMvc
        .perform(put(PROBE_PATH).with(oidcPrincipal()).cookie(csrfCookie.cookie()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put(PROBE_PATH)
                .with(oidcPrincipal())
                .header(HEADER_NAME, csrfCookie.cookie().getValue()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put(PROBE_PATH)
                .with(oidcPrincipal())
                .cookie(csrfCookie.cookie())
                .header(HEADER_NAME, "different-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void acceptsMatchingCookieAndHeaderForAnAuthenticatedMutation() throws Exception {
    CsrfCookie csrfCookie = requestCsrfCookie(get("/login"));

    performProbePut(csrfCookie.cookie(), csrfCookie.cookie().getValue())
        .andExpect(status().isNoContent());
  }

  @Test
  void modelsIndependentDoubleSubmitPairsWithoutClaimingAuthenticationRotation() throws Exception {
    // These two safe requests intentionally do not model OAuth2 success. The real hosted
    // login and its token transition belong to the later runtime checkpoint.
    CsrfCookie tokenA = requestCsrfCookie(get("/login"));
    CsrfCookie tokenB = requestCsrfCookie(get("/login"));

    assertThat(tokenB.cookie().getValue()).isNotEqualTo(tokenA.cookie().getValue());

    performProbePut(tokenB.cookie(), tokenA.cookie().getValue()).andExpect(status().isForbidden());
    performProbePut(tokenB.cookie(), tokenB.cookie().getValue()).andExpect(status().isNoContent());
  }

  private CsrfCookie requestCsrfCookie(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
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

  private org.springframework.test.web.servlet.ResultActions performProbePut(
      Cookie cookie, String headerValue) throws Exception {
    return mockMvc.perform(
        put(PROBE_PATH).with(oidcPrincipal()).cookie(cookie).header(HEADER_NAME, headerValue));
  }

  private RequestPostProcessor oidcPrincipal() {
    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");
    assertThat(registration).isNotNull();
    return oidcLogin().clientRegistration(registration);
  }

  private record CsrfCookie(Cookie cookie, String setCookieHeader) {}

  @RestController
  static class CsrfProbeController {

    @PutMapping(PROBE_PATH)
    ResponseEntity<Void> mutate() {
      return ResponseEntity.noContent().build();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-csrf-it")
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
              .clientName("BETUFU CSRF IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
