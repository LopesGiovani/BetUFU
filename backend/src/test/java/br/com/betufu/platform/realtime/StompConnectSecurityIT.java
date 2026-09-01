package br.com.betufu.platform.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.security.SecurityConfiguration;
import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
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
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(
    classes = {
      SecurityConfiguration.class,
      WebSocketConfiguration.class,
      StompConnectSecurityInterceptor.class,
      StompConnectSecurityIT.TestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "betufu.web.public-origin=http://localhost:4200",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
class StompConnectSecurityIT {

  private static final String ALLOWED_ORIGIN = "http://localhost:4200";
  private static final String DISALLOWED_ORIGIN = "http://evil.attacker.invalid";

  @LocalServerPort private int port;

  @Autowired private SessionRepository<MapSession> sessionRepository;

  private WebSocketStompClient stompClient;
  private StompSession stompSession;

  static {
    System.setProperty("java.net.useSystemProxies", "false");
    System.clearProperty("http.proxyHost");
    System.clearProperty("http.proxyPort");
    System.clearProperty("https.proxyHost");
    System.clearProperty("https.proxyPort");
    System.setProperty("http.nonProxyHosts", "*");
    System.setProperty("https.nonProxyHosts", "*");
    java.net.ProxySelector.setDefault(
        new java.net.ProxySelector() {
          @Override
          public java.util.List<java.net.Proxy> select(java.net.URI uri) {
            return java.util.List.of(java.net.Proxy.NO_PROXY);
          }

          @Override
          public void connectFailed(
              java.net.URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {}
        });
  }

  @BeforeEach
  void setUp() {
    StandardWebSocketClient wsClient = new StandardWebSocketClient();
    wsClient.getUserProperties().put("org.apache.tomcat.websocket.proxyHost", "DIRECT");
    stompClient = new WebSocketStompClient(wsClient);
    stompClient.setMessageConverter(new StringMessageConverter());
  }

  @AfterEach
  void tearDown() {
    if (stompSession != null && stompSession.isConnected()) {
      stompSession.disconnect();
    }
    if (stompClient != null && stompClient.isRunning()) {
      stompClient.stop();
    }
  }

  @Test
  void rejectsHandshakeFromDisallowedOrigin() {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(DISALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);

    assertThatThrownBy(() -> connect(handshakeHeaders, connectHeaders))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void acceptsHandshakeAndConnectWithMatchingSessionAndXsrf() throws Exception {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);

    stompSession = connect(handshakeHeaders, connectHeaders);
    assertThat(stompSession.isConnected()).isTrue();
  }

  @Test
  void rejectsConnectWithoutSessionAuthentication() {
    String xsrfToken = UUID.randomUUID().toString();

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);

    assertThatThrownBy(() -> connect(handshakeHeaders, connectHeaders))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void rejectsConnectWithoutXsrfHeader() {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders(); // no X-XSRF-TOKEN

    assertThatThrownBy(() -> connect(handshakeHeaders, connectHeaders))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void rejectsConnectWithDivergentXsrfToken() {
    String cookieToken = UUID.randomUUID().toString();
    String headerToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + cookieToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", headerToken);

    assertThatThrownBy(() -> connect(handshakeHeaders, connectHeaders))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void rejectsConnectWithBearerOrAuthorizationHeader() {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);
    connectHeaders.add("Authorization", "Bearer eyJhbGciOi...");

    assertThatThrownBy(() -> connect(handshakeHeaders, connectHeaders))
        .isInstanceOf(ExecutionException.class);
  }

  @Test
  void rejectsSubscriptionsToUnapprovedDestinations() throws Exception {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);

    stompSession = connect(handshakeHeaders, connectHeaders);
    assertThat(stompSession.isConnected()).isTrue();

    // Attempt subscribing to unapproved chat topic
    stompSession.subscribe(
        "/topic/chat",
        new StompFrameHandler() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return String.class;
          }

          @Override
          public void handleFrame(StompHeaders headers, Object payload) {}
        });

    long deadline = System.currentTimeMillis() + 3000;
    while (stompSession.isConnected() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(stompSession.isConnected()).isFalse();
  }

  @Test
  void acceptsSubscriptionsToApprovedUserDestination() throws Exception {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);

    stompSession = connect(handshakeHeaders, connectHeaders);
    assertThat(stompSession.isConnected()).isTrue();

    // Subscribing to approved technical user destination
    StompSession.Subscription sub =
        stompSession.subscribe(
            "/user/queue/session",
            new StompFrameHandler() {
              @Override
              public Type getPayloadType(StompHeaders headers) {
                return String.class;
              }

              @Override
              public void handleFrame(StompHeaders headers, Object payload) {}
            });

    Thread.sleep(200);
    assertThat(stompSession.isConnected()).isTrue();
    assertThat(sub.getSubscriptionId()).isNotNull();
  }

  @Test
  void rejectsSendingApplicationMessages() throws Exception {
    String xsrfToken = UUID.randomUUID().toString();
    String sessionId = createAuthenticatedSession("user-1");

    WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
    handshakeHeaders.setOrigin(ALLOWED_ORIGIN);
    handshakeHeaders.add("Cookie", "SESSION=" + sessionId + "; XSRF-TOKEN=" + xsrfToken);

    StompHeaders connectHeaders = new StompHeaders();
    connectHeaders.add("X-XSRF-TOKEN", xsrfToken);

    stompSession = connect(handshakeHeaders, connectHeaders);
    assertThat(stompSession.isConnected()).isTrue();

    // Attempt sending an application / chat message
    stompSession.send("/app/chat", "Hello world");

    long deadline = System.currentTimeMillis() + 3000;
    while (stompSession.isConnected() && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertThat(stompSession.isConnected()).isFalse();
  }

  private StompSession connect(WebSocketHttpHeaders handshakeHeaders, StompHeaders connectHeaders)
      throws Exception {
    String wsUrl = "ws://127.0.0.1:" + port + "/ws";
    CompletableFuture<StompSession> future = new CompletableFuture<>();

    stompClient.connectAsync(
        wsUrl,
        handshakeHeaders,
        connectHeaders,
        new StompSessionHandlerAdapter() {
          @Override
          public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            future.complete(session);
          }

          @Override
          public void handleTransportError(StompSession session, Throwable exception) {
            future.completeExceptionally(exception);
          }

          @Override
          public void handleException(
              StompSession session,
              StompCommand command,
              StompHeaders headers,
              byte[] payload,
              Throwable exception) {
            future.completeExceptionally(exception);
          }
        });

    return future.get(5, TimeUnit.SECONDS);
  }

  private String createAuthenticatedSession(String username) {
    MapSession session = sessionRepository.createSession();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        UsernamePasswordAuthenticationToken.authenticated(
            username, "unused", AuthorityUtils.createAuthorityList("ROLE_USER")));
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    sessionRepository.save(session);
    return session.getId();
  }

  @TestConfiguration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.EnableAutoConfiguration
  static class TestConfig {

    @Bean
    SessionRepository<MapSession> sessionRepository() {
      return new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>());
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-stomp-it")
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
              .clientName("BETUFU STOMP IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
