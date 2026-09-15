package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.ledger.internal.CreditBalanceController;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      CreditBalanceController.class,
      CreditBalanceControllerIT.TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "management.health.redis.enabled=false",
      "BETUFU_REDIS_HOST=localhost",
      "BETUFU_REDIS_PORT=6379",
      "BETUFU_REDIS_PASSWORD=unused",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=credit-balance-controller-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
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
@ActiveProfiles("integrated")
class CreditBalanceControllerIT {
  private static final String PATH = "/api/v1/me/credits";
  private static final String ISSUER = "https://idp.invalid";
  private static final String SUBJECT = "credit-balance-controller-it";

  @Autowired private WebApplicationContext applicationContext;
  @Autowired private CreditLedgerPort ledger;
  @Autowired private OperationalIdentityLookup identities;
  @Autowired private ClientRegistrationRepository clientRegistrations;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy springSecurityFilterChain;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Mockito.reset(ledger, identities);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
  }

  @Test
  void anonymousBalanceAccessIsUnauthorized() throws Exception {
    mockMvc
        .perform(get(PATH).accept("application/json"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("BETUFU-401-UNAUTHORIZED"));

    verify(ledger, never()).getBalance(Mockito.any());
  }

  @Test
  void unknownAuthenticatedIdentityIsUnauthorized() throws Exception {
    when(identities.findIdentityId(ISSUER, SUBJECT)).thenReturn(Optional.empty());

    mockMvc.perform(get(PATH).with(oidcUser())).andExpect(status().isUnauthorized());

    verify(identities).findIdentityId(ISSUER, SUBJECT);
    verify(ledger, never()).getBalance(Mockito.any());
  }

  @Test
  void authenticatedOwnerReceivesOnlyTheGovernedBalanceProjection() throws Exception {
    UUID identityId = UUID.randomUUID();
    when(identities.findIdentityId(ISSUER, SUBJECT)).thenReturn(Optional.of(identityId));
    when(ledger.getBalance(identityId))
        .thenReturn(new CreditBalance(identityId, new BigDecimal("1000.00")));

    String body =
        mockMvc
            .perform(get(PATH).with(oidcUser()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.balance").value(1000.00))
            .andExpect(jsonPath("$.scale").value(2))
            .andExpect(jsonPath("$.minimumStake").value(10.00))
            .andExpect(jsonPath("$.rulesVersion").value("credits-v1"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    verify(identities).findIdentityId(ISSUER, SUBJECT);
    verify(ledger).getBalance(identityId);
    assertThat(body).doesNotContain(identityId.toString(), ISSUER, SUBJECT);
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor oidcUser() {
    ClientRegistration registration = clientRegistrations.findByRegistrationId("keycloak");
    assertThat(registration).isNotNull();
    return oidcLogin()
        .clientRegistration(registration)
        .idToken(
            token ->
                token.claim(IdTokenClaimNames.ISS, ISSUER).claim(IdTokenClaimNames.SUB, SUBJECT));
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {
    @Bean
    CreditLedgerPort creditLedgerPort() {
      return Mockito.mock(CreditLedgerPort.class);
    }

    @Bean
    OperationalIdentityLookup operationalIdentityLookup() {
      return Mockito.mock(OperationalIdentityLookup.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("credit-balance-controller-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri(ISSUER + "/authorize")
              .tokenUri(ISSUER + "/token")
              .jwkSetUri(ISSUER + "/jwks")
              .issuerUri(ISSUER)
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Credit Balance Controller IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
