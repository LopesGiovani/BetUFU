package br.com.betufu.chat.internal;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.BufferingStompDecoder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

/** Read-only STOMP 1.2 endpoint. Domain commands stay on authenticated HTTP APIs. */
@Component
@Profile("runtime | integrated")
public class ChatPushEndpoint extends TextWebSocketHandler {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ChatPushEndpoint.class);
  private static final int FRAME_LIMIT = 16384;
  private static final int CONNECTION_LIMIT = 256;
  private static final int CONNECTIONS_PER_CLIENT = 4;
  private final Map<String, Connection> connections = new ConcurrentHashMap<>();
  private final ObjectProvider<SessionRepository<?>> repositories;
  private final ChatReadService reads;
  private final ObjectMapper mapper;
  private final StompEncoder encoder = new StompEncoder();
  private final ThreadPoolExecutor deliveries =
      new ThreadPoolExecutor(
          2,
          4,
          30,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(256),
          Thread.ofPlatform().daemon().name("chat-delivery-", 0).factory());
  private final ScheduledExecutorService guard =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("chat-session-guard").factory());

  public ChatPushEndpoint(
      ObjectProvider<SessionRepository<?>> repositories,
      ChatReadService reads,
      ChatPushService push,
      ObjectMapper mapper) {
    this.repositories = repositories;
    this.reads = reads;
    this.mapper = mapper;
    push.listen(this::changed);
    guard.scheduleWithFixedDelay(this::checkConnections, 1, 1, TimeUnit.SECONDS);
  }

  String resolveSession(String cookie) {
    if (cookie.length() > 256) return null;
    SessionRepository<?> repository = repositories.getIfAvailable();
    if (repository == null) return null;
    try {
      String id = new String(Base64.getDecoder().decode(cookie), StandardCharsets.UTF_8);
      Session found = repository.findById(id);
      return principal(found) == null ? null : id;
    } catch (RuntimeException failure) {
      return null;
    }
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession socket) throws Exception {
    socket.setTextMessageSizeLimit(FRAME_LIMIT);
    String id = (String) socket.getAttributes().get("chat.session");
    PrincipalSnapshot principal = id == null ? null : principal(repositorySession(id));
    if (id != null && principal == null) {
      socket.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    String bucket =
        id == null
            ? "ip:"
                + Objects.toString(socket.getRemoteAddress(), "unknown").replaceFirst(":\\d+$", "")
            : "session:" + id;
    synchronized (connections) {
      if (connections.size() >= CONNECTION_LIMIT
          || connections.values().stream().filter(c -> c.bucket.equals(bucket)).count()
              >= CONNECTIONS_PER_CLIENT) {
        socket.close(CloseStatus.POLICY_VIOLATION);
        return;
      }
      connections.put(socket.getId(), new Connection(socket, id, principal, bucket));
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
    Connection connection = connections.get(socket.getId());
    if (connection == null) return;
    try {
      requireCurrent(connection);
      for (Message<byte[]> frame : connection.decoder.decode(ByteBuffer.wrap(message.asBytes())))
        handleFrame(connection, frame);
    } catch (RuntimeException | IOException failure) {
      log.debug(
          "Read-only chat frame rejected: {} at {}",
          failure.getClass().getSimpleName(),
          failure.getStackTrace()[0]);
      close(connection, CloseStatus.POLICY_VIOLATION);
    }
  }

  private void handleFrame(Connection connection, Message<byte[]> frame) throws IOException {
    StompHeaderAccessor headers = StompHeaderAccessor.wrap(frame);
    StompCommand command = headers.getCommand();
    if (command == null) return;
    if (frame.getPayload().length != 0) throw new IllegalArgumentException("Read-only protocol");
    for (String name : headers.toNativeHeaderMap().keySet())
      if (List.of("authorization", "cookie", "login", "passcode")
          .contains(name.toLowerCase(java.util.Locale.ROOT)))
        throw new IllegalArgumentException("Client credentials forbidden");
    if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
      connect(connection, headers);
      return;
    }
    if (!connection.connected) throw new IllegalArgumentException("CONNECT required");
    switch (command) {
      case SUBSCRIBE -> subscribe(connection, headers);
      case UNSUBSCRIBE -> unsubscribe(connection, headers);
      case DISCONNECT -> close(connection, CloseStatus.NORMAL);
      default -> throw new IllegalArgumentException("Unsupported frame");
    }
  }

  private void connect(Connection connection, StompHeaderAccessor headers) throws IOException {
    String versions = headers.getFirstNativeHeader("accept-version");
    if (connection.connected || versions == null || !List.of(versions.split(",")).contains("1.2"))
      throw new IllegalArgumentException("Invalid connect");
    String cookie = (String) connection.socket.getAttributes().get("chat.csrf");
    String token = headers.getFirstNativeHeader("X-XSRF-TOKEN");
    if (token == null
        || !MessageDigest.isEqual(
            cookie.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
      throw new IllegalArgumentException("CSRF mismatch");
    connection.connected = true;
    send(
        connection,
        StompCommand.CONNECTED,
        Map.of("version", "1.2", "heart-beat", "10000,0"),
        new byte[0]);
  }

  private void unsubscribe(Connection connection, StompHeaderAccessor headers) {
    if (!Objects.equals(connection.subscription, headers.getSubscriptionId()))
      throw new IllegalArgumentException("Unknown subscription");
    connection.destination = null;
    connection.subscription = null;
  }

  private void subscribe(Connection connection, StompHeaderAccessor headers) throws IOException {
    if (connection.subscription != null)
      throw new IllegalArgumentException("One subscription per connection");
    String id = headers.getSubscriptionId(), destination = headers.getDestination();
    if (id == null
        || !id.matches("[A-Za-z0-9_-]{1,32}")
        || destination == null
        || !(headers.getFirstNativeHeader("ack") == null
            || "auto".equals(headers.getFirstNativeHeader("ack"))))
      throw new IllegalArgumentException("Invalid subscription");
    String cursor = headers.getFirstNativeHeader("changeCursor");
    if (cursor != null && (!cursor.matches("[0-9]{1,19}") || Long.parseLong(cursor) < 0))
      throw new IllegalArgumentException("Invalid cursor");
    UUID eventId;
    if ("/chat/global".equals(destination)) eventId = null;
    else if (destination.startsWith("/chat/events/")) {
      String raw = destination.substring("/chat/events/".length());
      eventId = UUID.fromString(raw);
      if (!eventId.toString().equals(raw)) throw new IllegalArgumentException("Invalid event");
    } else throw new IllegalArgumentException("Unknown destination");
    reads.read(eventId, connection.principal != null, null, null, 1);
    connection.eventId = eventId;
    connection.subscription = id;
    connection.destination = destination;
    refresh(connection);
  }

  private void changed(ChatPushService.Change change) {
    for (Connection connection : connections.values())
      if (connection.destination != null && matches(connection, change))
        deliver(
            connection,
            () -> {
              if (connection.destination != null) refresh(connection);
            });
  }

  private boolean matches(Connection connection, ChatPushService.Change change) {
    if (change.issuer() == null) return Objects.equals(change.eventId(), connection.eventId);
    return connection.principal != null
        && change.issuer().equals(connection.principal.issuer())
        && change.subject().equals(connection.principal.subject());
  }

  private void deliver(Connection connection, Delivery task) {
    try {
      deliveries.execute(
          () -> {
            try {
              task.run();
            } catch (RuntimeException | IOException failure) {
              close(connection, CloseStatus.POLICY_VIOLATION);
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException full) {
      close(connection, CloseStatus.SESSION_NOT_RELIABLE);
    }
  }

  @FunctionalInterface
  private interface Delivery {
    void run() throws IOException;
  }

  private void refresh(Connection connection) throws IOException {
    requireCurrent(connection);
    var projection = reads.read(connection.eventId, connection.principal != null, null, null, 1);
    requireCurrent(connection);
    ChatChangeCursor hint =
        new ChatChangeCursor(
            projection.kind(),
            projection.eventId(),
            projection.changeCursor(),
            true,
            projection.state(),
            projection.deadline());
    send(
        connection,
        StompCommand.MESSAGE,
        Map.of(
            "subscription",
            connection.subscription,
            "destination",
            connection.destination,
            "message-id",
            UUID.randomUUID().toString(),
            "content-type",
            "application/json"),
        mapper.writeValueAsBytes(hint));
  }

  private void checkConnections() {
    connections.values().forEach(this::checkConnection);
  }

  private void checkConnection(Connection connection) {
    try {
      requireCurrent(connection);
      Instant now = Instant.now();
      if (!connection.connected && connection.opened.plusSeconds(10).isBefore(now)) {
        close(connection, CloseStatus.POLICY_VIOLATION);
        return;
      }
      if (connection.connected
          && connection.lastSent.plusSeconds(10).isBefore(now)
          && connection.heartbeatPending.compareAndSet(false, true))
        deliver(connection, () -> heartbeat(connection));
    } catch (RuntimeException failure) {
      close(connection, CloseStatus.POLICY_VIOLATION);
    }
  }

  private void heartbeat(Connection connection) throws IOException {
    try {
      // Transport I/O stays off the guard; repository reads never renew idle time.
      requireCurrent(connection);
      connection.socket.sendMessage(new TextMessage("\n"));
      connection.lastSent = Instant.now();
    } finally {
      connection.heartbeatPending.set(false);
    }
  }

  private void requireCurrent(Connection connection) {
    if (!connection.socket.isOpen()
        || (connection.sessionId != null
            && !Objects.equals(
                connection.principal, principal(repositorySession(connection.sessionId)))))
      throw new IllegalStateException("Session revoked or expired");
  }

  private Session repositorySession(String id) {
    SessionRepository<?> repository = repositories.getIfAvailable();
    return repository == null ? null : repository.findById(id);
  }

  private PrincipalSnapshot principal(Session session) {
    if (session == null
        || session.isExpired()
        || !Instant.now()
            .isBefore(session.getLastAccessedTime().plus(session.getMaxInactiveInterval())))
      return null;
    Object attribute = session.getAttribute("SPRING_SECURITY_CONTEXT");
    if (!(attribute instanceof SecurityContext context)) return null;
    Authentication auth = context.getAuthentication();
    if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof OidcUser user))
      return null;
    return new PrincipalSnapshot(
        user.getIssuer().toString(),
        user.getSubject(),
        auth.getAuthorities().stream().map(Object::toString).sorted().toList());
  }

  private void send(
      Connection connection, StompCommand command, Map<String, String> headers, byte[] body)
      throws IOException {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    headers.forEach(accessor::setNativeHeader);
    connection.socket.sendMessage(
        new TextMessage(
            new String(
                encoder.encode(accessor.getMessageHeaders(), body), StandardCharsets.UTF_8)));
    connection.lastSent = Instant.now();
  }

  @Override
  public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
    connections.remove(socket.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession socket, Throwable exception) {
    Connection connection = connections.get(socket.getId());
    if (connection != null) close(connection, CloseStatus.SERVER_ERROR);
  }

  private void close(Connection connection, CloseStatus status) {
    connections.remove(connection.socket.getId());
    try {
      connection.socket.close(status);
    } catch (IOException ignored) {
      /* Transport already gone. */
    }
  }

  @PreDestroy
  void shutdown() {
    guard.shutdownNow();
    deliveries.shutdownNow();
    connections.values().forEach(c -> close(c, CloseStatus.GOING_AWAY));
  }

  private record PrincipalSnapshot(String issuer, String subject, List<String> authorities) {}

  private static final class Connection {
    final WebSocketSession socket;
    final String sessionId;
    final PrincipalSnapshot principal;
    final String bucket;
    final Instant opened = Instant.now();
    final BufferingStompDecoder decoder =
        new BufferingStompDecoder(new StompDecoder(), FRAME_LIMIT);
    volatile boolean connected;
    volatile String subscription;
    volatile String destination;
    volatile UUID eventId;
    volatile Instant lastSent = Instant.now();
    final java.util.concurrent.atomic.AtomicBoolean heartbeatPending =
        new java.util.concurrent.atomic.AtomicBoolean();

    Connection(
        WebSocketSession socket, String sessionId, PrincipalSnapshot principal, String bucket) {
      this.socket = new ConcurrentWebSocketSessionDecorator(socket, 1000, 65536);
      this.sessionId = sessionId;
      this.principal = principal;
      this.bucket = bucket;
    }
  }
}
