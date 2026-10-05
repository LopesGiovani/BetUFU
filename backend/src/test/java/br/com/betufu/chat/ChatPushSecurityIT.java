package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatPushService;
import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ChatPushSecurityIT extends ChatPushFixture {
  @Test
  @SuppressWarnings({"rawtypes", "unchecked"})
  void changedRoleSnapshotInRedisTerminatesOldSocket() throws Exception {
    Session session = session("reader");
    try (Wire wire = connected(session)) {
      wire.subscribe("/chat/global", "0");
      wire.message();
      Session persisted = sessions.findById(session.getId());
      org.springframework.security.core.context.SecurityContext original =
          persisted.getAttribute("SPRING_SECURITY_CONTEXT");
      var principal =
          (org.springframework.security.oauth2.core.oidc.user.OidcUser)
              original.getAuthentication().getPrincipal();
      var updated = SecurityContextHolder.createEmptyContext();
      updated.setAuthentication(
          new OAuth2AuthenticationToken(
              principal,
              List.of(
                  new org.springframework.security.core.authority.SimpleGrantedAuthority(
                      "ROLE_MODERADOR")),
              "keycloak"));
      persisted.setAttribute("SPRING_SECURITY_CONTEXT", updated);
      ((SessionRepository) sessions).save(persisted);
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
  }

  @Test
  void originAndHandshakeCsrfCookieAreRequired() {
    assertThatThrownBy(() -> open(null, "https://evil.invalid", TOKEN))
        .isInstanceOf(Exception.class);
    assertThatThrownBy(() -> open(null, null, TOKEN)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> open(null, ORIGIN, null)).isInstanceOf(Exception.class);
  }

  @Test
  void connectRequiresMatchingCsrfAndRejectsCredentialsInFrames() throws Exception {
    try (Wire wire = open(null, ORIGIN, TOKEN)) {
      wire.send("CONNECT\naccept-version:1.2\nX-XSRF-TOKEN:wrong\n\n\0");
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
    try (Wire wire = open(null, ORIGIN, TOKEN)) {
      wire.send(
          "CONNECT\naccept-version:1.2\nAuthorization:Bearer secret\nX-XSRF-TOKEN:"
              + TOKEN
              + "\n\n\0");
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
  }

  @Test
  void anonymousGlobalAliasDraftAndArbitraryDestinationFailClosed() throws Exception {
    for (String destination :
        List.of(
            "/chat/global",
            "/chat/events/" + global,
            "/chat/events/" + event(false),
            "/topic/anything")) {
      try (Wire wire = connected(null)) {
        wire.subscribe(destination, "0");
        assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
        assertThat(wire.frames.toString()).doesNotContain("private-global");
      }
    }
  }

  @Test
  void anonymousPublishedEventReadsButCannotSendOrUseBadCursor() throws Exception {
    UUID event = event(true);
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event, "0");
      assertThat(wire.message()).contains("resyncRequired", event.toString());
      wire.send("SEND\ndestination:/chat/events/" + event + "\n\nattack\0");
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event, "-1");
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
  }

  @Test
  void idlePushDoesNotRenewRedisSessionAndDeletionTerminatesConnection() throws Exception {
    Session session = session("reader");
    Instant before = sessions.findById(session.getId()).getLastAccessedTime();
    Long ttl = redis.getExpire("betufu:session:sessions:" + session.getId());
    try (Wire wire = connected(session)) {
      wire.subscribe("/chat/global", "0");
      assertThat(wire.message()).contains("GLOBAL");
      Thread.sleep(1200);
      assertThat(sessions.findById(session.getId()).getLastAccessedTime()).isEqualTo(before);
      assertThat(redis.getExpire("betufu:session:sessions:" + session.getId()))
          .isLessThanOrEqualTo(ttl);
      sessions.deleteById(session.getId());
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
  }

  @Test
  void fifthConnectionFromSameClientIsRejected() throws Exception {
    for (int index = 0; index < 4; index++) connected(null);
    try (Wire fifth = open(null, ORIGIN, TOKEN)) {
      assertThat(fifth.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
  }

  @Test
  void expiryOrChangedPrincipalTerminatesExistingSubscription() throws Exception {
    for (boolean expire : List.of(true, false)) {
      Session session = session("reader");
      try (Wire wire = connected(session)) {
        wire.subscribe("/chat/global", "0");
        wire.message();
        String key = "betufu:session:sessions:" + session.getId();
        if (expire) redis.expire(key, Duration.ofMillis(1));
        else redis.opsForHash().delete(key, "sessionAttr:SPRING_SECURITY_CONTEXT");
        assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
      }
    }
  }

  @Test
  void oversizedFramesAndSecondSubscriptionAreBounded() throws Exception {
    try (Wire wire = connected(null)) {
      wire.send("x".repeat(20000));
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isIn(1008, 1009);
    }
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event(true), "0");
      wire.message();
      wire.subscribe("/chat/global", "0");
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(1008);
    }
  }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false", "spring.session.data.redis.configure-action=none",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid", "BETUFU_OIDC_CLIENT_ID=chat-push-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused", "betufu.web.public-origin=http://localhost:4200"
    })
@ActiveProfiles("integrated")
@Import(ChatReadIT.TestOidcConfiguration.class)
abstract class ChatPushFixture {
  static final String ORIGIN = "http://localhost:4200";
  static final String TOKEN = "chat-push-csrf-fixture";

  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("push-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  static final GenericContainer<?> REDIS =
      new GenericContainer<>(
              DockerImageName.parse(
                  "docker.io/library/redis:8.10.0-trixie@sha256:344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366"))
          .withExposedPorts(6379);

  static {
    POSTGRESQL.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry registry) {
    registry.add("BETUFU_REDIS_HOST", REDIS::getHost);
    registry.add("BETUFU_REDIS_PORT", () -> REDIS.getMappedPort(6379));
    registry.add("BETUFU_REDIS_PASSWORD", () -> "");
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired SessionRepository<?> sessions;
  @Autowired ChatPushService push;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
  UUID author;
  UUID global;
  final java.util.ArrayList<Wire> wires = new java.util.ArrayList<>();

  @BeforeEach
  void data() {
    jdbc.execute(
        "TRUNCATE public.local_identity, public.sports_competition, public.chat_channel CASCADE");
    author =
        jdbc.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://idp.invalid','reader') RETURNING identity_id",
            UUID.class);
    jdbc.update(
        "INSERT INTO betufu_profile(identity_id,nickname) VALUES (?, 'push_reader')", author);
    global =
        jdbc.queryForObject(
            "INSERT INTO chat_channel(kind) VALUES ('GLOBAL') RETURNING channel_id", UUID.class);
  }

  @AfterEach
  void closeWires() {
    wires.forEach(Wire::close);
    wires.clear();
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  Session session(String subject) {
    Session session = sessions.createSession();
    Instant now = Instant.now();
    var token =
        new OidcIdToken(
            "fixture-not-a-live-token",
            now,
            now.plusSeconds(3600),
            Map.of("iss", "https://idp.invalid", "sub", subject));
    var principal = new DefaultOidcUser(List.of(), token);
    var auth = new OAuth2AuthenticationToken(principal, List.of(), "keycloak");
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(auth);
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    session.setAttribute("betufu.session.lastValidActivityAt.epochSecond", now.getEpochSecond());
    session.setAttribute("betufu.session.lastValidActivityAt.nano", now.getNano());
    session.setAttribute("betufu.session.authentication.amr.count", 0);
    session.setAttribute(
        SelectiveTouchSessionRepository.class.getName() + ".approvedActivity", now);
    ((SessionRepository) sessions).save(session);
    return session;
  }

  Wire connected(Session session) throws Exception {
    Wire wire = open(session, ORIGIN, TOKEN);
    wire.send("CONNECT\naccept-version:1.2\nheart-beat:0,0\nX-XSRF-TOKEN:" + TOKEN + "\n\n\0");
    assertThat(wire.frames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED\n");
    return wire;
  }

  Wire open(Session session, String origin, String csrf) throws Exception {
    Wire wire = new Wire();
    var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    wire.client = client;
    var builder = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5));
    if (origin != null) builder.header("Origin", origin);
    String cookie = csrf == null ? "" : "XSRF-TOKEN=" + csrf;
    if (session != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder()
                  .encodeToString(
                      session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if (!cookie.isEmpty()) builder.header("Cookie", cookie);
    try {
      wire.socket =
          builder
              .buildAsync(URI.create("ws://localhost:" + port + "/api/v1/chat/push"), wire)
              .get(7, TimeUnit.SECONDS);
    } catch (Exception failure) {
      client.shutdownNow();
      throw failure;
    }
    wires.add(wire);
    return wire;
  }

  UUID event(boolean published) {
    UUID competition =
        jdbc.queryForObject(
            "INSERT INTO sports_competition(name,slug) VALUES ('Push fixture',?) RETURNING competition_id",
            UUID.class,
            "push-" + UUID.randomUUID());
    UUID edition =
        jdbc.queryForObject(
            "INSERT INTO sports_edition(competition_id,name,slug) VALUES (?, 'Edition', 'edition') RETURNING edition_id",
            UUID.class,
            competition);
    UUID event =
        jdbc.queryForObject(
            "INSERT INTO sports_event(competition_id,edition_id,title,event_format,scheduled_at,zone_id,venue_state,source_reference) VALUES (?,?,'Push fixture','HEAD_TO_HEAD',now(),'America/Sao_Paulo','LOCAL_A_DEFINIR','https://fixture.invalid') RETURNING event_id",
            UUID.class,
            competition,
            edition);
    if (published)
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              s -> {
                jdbc.update("UPDATE sports_event SET status='PUBLICADO' WHERE event_id=?", event);
                UUID publication =
                    jdbc.queryForObject(
                        "INSERT INTO sports_event_publication(event_id,competition_id,edition_id,version_no,competition_name,edition_name,title,event_format,scheduled_at,zone_id,venue_state,source_reference,published_by_identity_id,base_lock_version) VALUES (?,?,?,1,'Fixture','Edition','Push fixture','HEAD_TO_HEAD',now(),'America/Sao_Paulo','LOCAL_A_DEFINIR','https://fixture.invalid',?,0) RETURNING publication_id",
                        UUID.class,
                        event,
                        competition,
                        edition,
                        author);
                jdbc.update(
                    "INSERT INTO sports_event_publication_slot(publication_id,slot_no,display_label) VALUES (?,1,'Home'),(?,2,'Away')",
                    publication,
                    publication);
              });
    return event;
  }

  UUID channel(UUID event) {
    return jdbc.queryForObject(
        "INSERT INTO chat_channel(kind,event_id) VALUES ('EVENT',?) RETURNING channel_id",
        UUID.class,
        event);
  }

  UUID message(UUID channel, String body) {
    return jdbc.queryForObject(
        "INSERT INTO chat_message(channel_id,author_identity_id,body) VALUES (?,?,?) RETURNING message_id",
        UUID.class,
        channel,
        author,
        body);
  }

  static final class Wire implements WebSocket.Listener, AutoCloseable {
    final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
    final CompletableFuture<Integer> closed = new CompletableFuture<>();
    final StringBuilder buffer = new StringBuilder();
    WebSocket socket;
    HttpClient client;

    @Override
    public void onOpen(WebSocket ws) {
      ws.request(1);
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onText(
        WebSocket ws, CharSequence text, boolean last) {
      buffer.append(text);
      if (last) {
        String frame = buffer.toString();
        buffer.setLength(0);
        if (!frame.isBlank()) frames.add(frame);
      }
      ws.request(1);
      return null;
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onClose(
        WebSocket ws, int status, String reason) {
      closed.complete(status);
      return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable failure) {
      closed.completeExceptionally(failure);
    }

    void send(String frame) {
      socket.sendText(frame, true).join();
    }

    void subscribe(String destination, String cursor) {
      send(
          "SUBSCRIBE\nid:chat\ndestination:" + destination + "\nchangeCursor:" + cursor + "\n\n\0");
    }

    String message() throws InterruptedException {
      String frame = frames.poll(5, TimeUnit.SECONDS);
      assertThat(frame).startsWith("MESSAGE\n");
      return frame;
    }

    @Override
    public void close() {
      try {
        if (socket != null && !socket.isOutputClosed()) {
          socket.sendClose(1000, "done").join();
          closed.get(3, TimeUnit.SECONDS);
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        if (socket != null) socket.abort();
      } catch (Exception transportGone) {
        if (socket != null) socket.abort();
      } finally {
        if (client != null) client.shutdownNow();
      }
    }
  }
}
