package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.betting.PredictionAcceptancePort.SubmitPrediction;
import br.com.betufu.betting.internal.PredictionController;
import br.com.betufu.betting.internal.PredictionRepository;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.api.ProblemDetailsHandler;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.security.SecurityConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
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
      ProblemDetailsHandler.class,
      PredictionController.class,
      PredictionControllerIT.TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "management.health.redis.enabled=false",
      "BETUFU_REDIS_HOST=localhost",
      "BETUFU_REDIS_PORT=6379",
      "BETUFU_REDIS_PASSWORD=unused",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=prediction-controller-it",
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
class PredictionControllerIT {
  private static final String PATH = "/api/v1/me/predictions";
  private static final String ISSUER = "https://idp.invalid";
  private static final String SUBJECT = "prediction-controller-it";

  @Autowired private WebApplicationContext applicationContext;
  @Autowired private PredictionAcceptancePort acceptance;
  @Autowired private OperationalIdentityLookup identities;
  @Autowired private PredictionRepository repository;
  @Autowired private PredictionEmergencyPausePort pauses;
  @Autowired private ClientRegistrationRepository clientRegistrations;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy springSecurityFilterChain;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Mockito.reset(acceptance, identities, repository, pauses);
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(springSecurityFilterChain)
            .build();
  }

  @Test
  void anonymousSubmissionIsUnauthorized() throws Exception {
    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .header("Idempotency-Key", "anonymous-attempt")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(UUID.randomUUID())))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));

    verify(acceptance, never()).accept(Mockito.any(), Mockito.any());
  }

  @Test
  void authenticatedSubmissionRequiresIdempotencyHeader() throws Exception {
    UUID identityId = resolvedIdentity();

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .with(oidcUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(UUID.randomUUID())))
        .andExpect(status().isBadRequest());

    verify(identities).findIdentityId(ISSUER, SUBJECT);
    verify(acceptance, never()).accept(eq(identityId), Mockito.any());
  }

  @Test
  void authenticatedSubmissionMapsIdentityHeaderBodyAndCreatedResponse() throws Exception {
    UUID identityId = resolvedIdentity();
    UUID marketId = UUID.randomUUID();
    UUID marketVersionId = UUID.randomUUID();
    UUID publicationId = UUID.randomUUID();
    UUID predictionId = UUID.randomUUID();
    Instant deadline = Instant.parse("2026-09-15T12:00:00Z");
    Instant acceptedAt = Instant.parse("2026-09-14T12:00:00Z");
    PredictionTicket ticket =
        new PredictionTicket(
            predictionId,
            marketId,
            marketVersionId,
            publicationId,
            "DRAW",
            new BigDecimal("25.00"),
            new BigDecimal("3.20"),
            new BigDecimal("80.00"),
            new BigDecimal("55.00"),
            "rule-v7",
            deadline,
            "ACCEPTED",
            acceptedAt);
    when(acceptance.accept(eq(identityId), Mockito.any())).thenReturn(ticket);

    String body =
        mockMvc
            .perform(
                post(PATH)
                    .with(csrf())
                    .with(oidcUser())
                    .header("Idempotency-Key", "ticket-42")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"marketId\":\""
                            + marketId
                            + "\",\"marketVersionId\":\""
                            + marketVersionId
                            + "\",\"publicationId\":\""
                            + publicationId
                            + "\",\"ruleVersion\":\"rule-v7\",\"selection\":\"DRAW\",\"stake\":25.00}"))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.predictionId").value(predictionId.toString()))
            .andExpect(jsonPath("$.selection").value("DRAW"))
            .andExpect(jsonPath("$.state").value("ACCEPTED"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    ArgumentCaptor<SubmitPrediction> command = ArgumentCaptor.forClass(SubmitPrediction.class);
    verify(identities).findIdentityId(ISSUER, SUBJECT);
    verify(acceptance).accept(eq(identityId), command.capture());
    assertThat(command.getValue())
        .isEqualTo(
            new SubmitPrediction(
                marketId,
                marketVersionId,
                publicationId,
                "rule-v7",
                "DRAW",
                new BigDecimal("25.00"),
                "ticket-42"));
    assertThat(body).doesNotContain(identityId.toString(), ISSUER, SUBJECT, "ticket-42");
  }

  @Test
  void staleSubmissionReturnsStableConflictProblem() throws Exception {
    UUID identityId = resolvedIdentity();
    when(acceptance.accept(eq(identityId), Mockito.any()))
        .thenThrow(
            new ApiProblemException(
                org.springframework.http.HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION));

    mockMvc
        .perform(
            post(PATH)
                .with(csrf())
                .with(oidcUser())
                .header("Idempotency-Key", "stale-ticket")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(UUID.randomUUID())))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.code").value("BETUFU-409-STALE_VERSION"));
  }

  @Test
  void ticketLookupUsesTheAuthenticatedIdentityAndDoesNotReturnAnotherOwnersTicket()
      throws Exception {
    UUID identityId = resolvedIdentity();
    UUID marketId = UUID.randomUUID();
    when(repository.findByMarket(identityId, marketId)).thenReturn(Optional.empty());

    mockMvc
        .perform(get(PATH + "/markets/" + marketId).with(oidcUser()))
        .andExpect(status().isNotFound());

    verify(repository).findByMarket(identityId, marketId);
  }

  @Test
  void availabilityIncludesOnlyTheAuthenticatedAccountsPause() throws Exception {
    UUID owner = resolvedIdentity();
    UUID marketId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID revision = UUID.randomUUID();
    when(pauses.findMarket(marketId))
        .thenReturn(Optional.of(new PredictionEmergencyPausePort.Market(marketId, eventId, true)));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.MARKET, marketId))
        .thenReturn(new PredictionEmergencyPausePort.State(false, null));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.EVENT, eventId))
        .thenReturn(new PredictionEmergencyPausePort.State(false, null));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.ACCOUNT, owner))
        .thenReturn(new PredictionEmergencyPausePort.State(true, revision));
    String path = PATH + "/markets/" + marketId + "/availability";

    mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.predictionsPaused").value(false));
    String body =
        mockMvc
            .perform(get(path).with(oidcUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("PAUSED"))
            .andExpect(jsonPath("$.predictionsPaused").value(true))
            .andExpect(jsonPath("$.revision").value(revision.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(owner.toString(), ISSUER, SUBJECT);

    when(identities.findIdentityId(ISSUER, SUBJECT)).thenReturn(Optional.empty());
    mockMvc
        .perform(get(path).with(oidcUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.predictionsPaused").value(false));
  }

  private UUID resolvedIdentity() {
    UUID identityId = UUID.randomUUID();
    when(identities.findIdentityId(ISSUER, SUBJECT)).thenReturn(Optional.of(identityId));
    return identityId;
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

  private static String requestBody(UUID marketId) {
    return "{\"marketId\":\""
        + marketId
        + "\",\"marketVersionId\":\""
        + UUID.randomUUID()
        + "\",\"publicationId\":\""
        + UUID.randomUUID()
        + "\",\"ruleVersion\":\"rule-v1\",\"selection\":\"HOME\",\"stake\":10.00}";
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {
    @Bean
    PredictionEmergencyPausePort predictionEmergencyPausePort() {
      return Mockito.mock(PredictionEmergencyPausePort.class);
    }

    @Bean
    PredictionAcceptancePort predictionAcceptancePort() {
      return Mockito.mock(PredictionAcceptancePort.class);
    }

    @Bean
    OperationalIdentityLookup operationalIdentityLookup() {
      return Mockito.mock(OperationalIdentityLookup.class);
    }

    @Bean
    PredictionRepository predictionRepository() {
      return Mockito.mock(PredictionRepository.class);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("prediction-controller-it")
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
              .clientName("BETUFU Prediction Controller IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
