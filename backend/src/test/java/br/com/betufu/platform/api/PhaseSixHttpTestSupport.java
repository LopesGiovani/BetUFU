package br.com.betufu.platform.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.security.SecurityConfiguration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
      br.com.betufu.settlement.internal.SettlementController.class,
      br.com.betufu.ledger.internal.CreditCorrectionController.class,
      br.com.betufu.ranking.internal.RankingController.class,
      br.com.betufu.ranking.internal.GroupController.class,
      br.com.betufu.ranking.internal.AchievementController.class,
      ProblemDetailsHandler.class,
      PhaseSixHttpTestSupport.TestApplication.class
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
public abstract class PhaseSixHttpTestSupport {
  protected static final String ISSUER = "https://idp.invalid";
  protected static final String SUBJECT = "phase-six-http-it";
  protected final UUID owner = UUID.randomUUID();
  @Autowired protected WebApplicationContext applicationContext;
  @Autowired protected OperationalIdentityLookup identities;
  @Autowired protected br.com.betufu.settlement.SettlementReadPort settlements;
  @Autowired protected br.com.betufu.settlement.SettlementProcessPort processes;
  @Autowired protected br.com.betufu.ledger.CreditCorrectionPort corrections;
  @Autowired protected br.com.betufu.ranking.RankingQueryPort rankings;
  @Autowired protected br.com.betufu.ranking.GroupQueryPort groups;
  @Autowired protected br.com.betufu.ranking.PrivateGroupPort groupCommands;
  @Autowired protected br.com.betufu.ranking.AchievementQueryPort achievements;
  @Autowired protected ClientRegistrationRepository clientRegistrations;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy filterChain;

  protected MockMvc mockMvc;

  @BeforeEach
  void setUpHttp() {
    Mockito.reset(
        identities,
        settlements,
        processes,
        corrections,
        rankings,
        groups,
        groupCommands,
        achievements);
    when(identities.findIdentityId(ISSUER, SUBJECT)).thenReturn(Optional.of(owner));
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext).addFilters(filterChain).build();
  }

  protected org.springframework.test.web.servlet.request.RequestPostProcessor user() {
    return oidcLogin()
        .clientRegistration(clientRegistrations.findByRegistrationId("keycloak"))
        .idToken(
            token ->
                token.claim(IdTokenClaimNames.ISS, ISSUER).claim(IdTokenClaimNames.SUB, SUBJECT));
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    br.com.betufu.settlement.SettlementReadPort settlements() {
      return Mockito.mock(br.com.betufu.settlement.SettlementReadPort.class);
    }

    @Bean
    br.com.betufu.settlement.SettlementProcessPort processes() {
      return Mockito.mock(br.com.betufu.settlement.SettlementProcessPort.class);
    }

    @Bean
    br.com.betufu.ledger.CreditCorrectionPort corrections() {
      return Mockito.mock(br.com.betufu.ledger.CreditCorrectionPort.class);
    }

    @Bean
    br.com.betufu.ranking.RankingQueryPort rankings() {
      return Mockito.mock(br.com.betufu.ranking.RankingQueryPort.class);
    }

    @Bean
    br.com.betufu.ranking.GroupQueryPort groups() {
      return Mockito.mock(br.com.betufu.ranking.GroupQueryPort.class);
    }

    @Bean
    br.com.betufu.ranking.PrivateGroupPort groupCommands() {
      return Mockito.mock(br.com.betufu.ranking.PrivateGroupPort.class);
    }

    @Bean
    br.com.betufu.ranking.AchievementQueryPort achievements() {
      return Mockito.mock(br.com.betufu.ranking.AchievementQueryPort.class);
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
