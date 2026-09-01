package br.com.betufu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
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
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
      "betufu.verification.matricula-hmac.current-version=v1",
      "betufu.verification.matricula-hmac.keys.v1=" + "dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@Import(BetufuApplicationSmokeTest.TestOidcConfiguration.class)
class BetufuApplicationSmokeTest {

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private SecurityFilterChain securityFilterChain;

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
  void loadsWithAConfidentialInMemoryOidcClient() {
    assertThat(securityFilterChain).isNotNull();
    assertThat(clientRegistrationRepository)
        .isInstanceOf(InMemoryClientRegistrationRepository.class);

    ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");

    assertThat(registration).isNotNull();
    assertThat(registration.getClientAuthenticationMethod())
        .isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
    assertThat(registration.getAuthorizationGrantType())
        .isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
    assertThat(registration.getScopes()).containsExactly("openid");
  }

  @Test
  void keepsTheOidcRedirectServerSideAndUsesStandardProtections() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/oauth2/authorization/keycloak"))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String location = result.getResponse().getRedirectedUrl();

    assertThat(location)
        .startsWith("https://idp.invalid/authorize?")
        .contains("response_type=code")
        .contains("state=")
        .contains("nonce=")
        .contains("code_challenge=")
        .contains("code_challenge_method=S256");
  }

  @Test
  void exposesOnlyTheApprovedPublicSurface() throws Exception {
    mockMvc.perform(get("/login")).andExpect(status().isOk());

    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/missing")).andExpect(status().is3xxRedirection());

    MockHttpSession authenticatedSession = authenticatedSession();

    mockMvc
        .perform(get("/api/v1/missing").session(authenticatedSession))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/unlisted").session(authenticatedSession))
        .andExpect(status().isForbidden());
  }

  @Test
  void verifiesTheInitialModulithBoundaries() {
    ApplicationModules.of(BetufuApplication.class).verify();
  }

  private static MockHttpSession authenticatedSession() {
    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            "smoke-user", "unused", AuthorityUtils.createAuthorityList("ROLE_USER")));

    MockHttpSession session = new MockHttpSession();
    session.setAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
    return session;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-smoke")
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
              .clientName("BETUFU Smoke")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
