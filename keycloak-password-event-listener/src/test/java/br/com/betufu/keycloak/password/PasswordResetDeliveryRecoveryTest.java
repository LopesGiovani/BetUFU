package br.com.betufu.keycloak.password;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashSet;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import jakarta.persistence.Column;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.keycloak.Config;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.TokenManager;
import org.keycloak.models.UserModel;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.resource.RealmResourceProviderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetDeliveryRecoveryTest {

    private static StandardServiceRegistry serviceRegistry;
    private static SessionFactory sessionFactory;

    private EntityManager entityManager;
    private PasswordResetOutboxStore store;
    private KeycloakSession sessionProxy;

    @BeforeAll
    static void setUpAll() {
        serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:keycloak_recovery_tracer;DB_CLOSE_DELAY=-1")
                .applySetting("jakarta.persistence.jdbc.user", "sa")
                .applySetting("jakarta.persistence.jdbc.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.show_sql", "false")
                .build();

        MetadataSources metadataSources = new MetadataSources(serviceRegistry);
        metadataSources.addAnnotatedClass(PasswordResetOutboxEntity.class);

        Metadata metadata = metadataSources.getMetadataBuilder().build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
    }

    @AfterAll
    static void tearDownAll() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (serviceRegistry != null) {
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }

    @BeforeEach
    void setUp() {
        entityManager = sessionFactory.createEntityManager();
        store = new PasswordResetOutboxStore(entityManager);
        sessionProxy = createKeycloakSessionProxy(entityManager);
    }

    @AfterEach
    void tearDown() {
        if (entityManager != null && entityManager.isOpen()) {
            EntityTransaction tx = entityManager.getTransaction();
            if (tx.isActive()) {
                tx.rollback();
            }
            tx.begin();
            entityManager.createQuery("DELETE FROM PasswordResetOutboxEntity").executeUpdate();
            tx.commit();
            entityManager.close();
        }
    }

    private KeycloakSession createKeycloakSessionProxy(EntityManager em) {
        JpaConnectionProvider jpaProvider = new JpaConnectionProvider() {
            @Override
            public EntityManager getEntityManager() {
                return em;
            }

            @Override
            public void close() {
            }
        };

        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("getProvider".equals(methodName) && args != null && args.length == 1) {
                if (JpaConnectionProvider.class.equals(args[0])) {
                    return jpaProvider;
                }
                return null;
            }
            if ("equals".equals(methodName) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName) && (args == null || args.length == 0)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName) && (args == null || args.length == 0)) {
                return "KeycloakSessionDynamicProxy@" + Integer.toHexString(System.identityHashCode(proxy));
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.equals(boolean.class)) {
                return false;
            } else if (returnType.equals(byte.class)) {
                return (byte) 0;
            } else if (returnType.equals(short.class)) {
                return (short) 0;
            } else if (returnType.equals(int.class)) {
                return 0;
            } else if (returnType.equals(long.class)) {
                return 0L;
            } else if (returnType.equals(float.class)) {
                return 0.0f;
            } else if (returnType.equals(double.class)) {
                return 0.0d;
            } else if (returnType.equals(char.class)) {
                return '\0';
            }
            return null;
        };

        return (KeycloakSession) Proxy.newProxyInstance(
                KeycloakSession.class.getClassLoader(),
                new Class<?>[]{KeycloakSession.class},
                handler);
    }

    private KeycloakSession createAuthorizedSessionProxy(
            EntityManager em, UserModel user, RealmModel realm, String authHeader) {
        JpaConnectionProvider jpaProvider = new JpaConnectionProvider() {
            @Override
            public EntityManager getEntityManager() {
                return em;
            }

            @Override
            public void close() {
            }
        };

        HttpHeaders httpHeaders = (HttpHeaders) Proxy.newProxyInstance(
                HttpHeaders.class.getClassLoader(),
                new Class<?>[]{HttpHeaders.class},
                (proxy, method, args) -> {
                    if ("getHeaderString".equals(method.getName()) && args != null && args.length == 1) {
                        if ("Authorization".equalsIgnoreCase((String) args[0])) {
                            return authHeader;
                        }
                    }
                    if ("getRequestHeader".equals(method.getName()) && args != null && args.length == 1) {
                        if ("Authorization".equalsIgnoreCase((String) args[0]) && authHeader != null) {
                            return List.of(authHeader);
                        }
                    }
                    return null;
                });

        HttpRequest httpRequest = (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getHttpHeaders".equals(method.getName())) {
                        return httpHeaders;
                    }
                    return null;
                });

        KeycloakContext context = (KeycloakContext) Proxy.newProxyInstance(
                KeycloakContext.class.getClassLoader(),
                new Class<?>[]{KeycloakContext.class},
                (proxy, method, args) -> {
                    if ("getRealm".equals(method.getName())) {
                        return realm;
                    }
                    if ("getUser".equals(method.getName())) {
                        return user;
                    }
                    if ("getHttpRequest".equals(method.getName())) {
                        return httpRequest;
                    }
                    if ("getHttpHeaders".equals(method.getName())) {
                        return httpHeaders;
                    }
                    return null;
                });

        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("getProvider".equals(methodName) && args != null && args.length == 1) {
                if (JpaConnectionProvider.class.equals(args[0])) {
                    return jpaProvider;
                }
                return null;
            }
            if ("getContext".equals(methodName)) {
                return context;
            }
            if ("equals".equals(methodName) && args != null && args.length == 1) {
                return proxy == args[0];
            }
            if ("hashCode".equals(methodName) && (args == null || args.length == 0)) {
                return System.identityHashCode(proxy);
            }
            if ("toString".equals(methodName) && (args == null || args.length == 0)) {
                return "KeycloakSessionAuthorizedProxy@" + Integer.toHexString(System.identityHashCode(proxy));
            }
            return null;
        };

        return (KeycloakSession) Proxy.newProxyInstance(
                KeycloakSession.class.getClassLoader(),
                new Class<?>[]{KeycloakSession.class},
                handler);
    }

    private RealmModel createMockRealm(String realmName, String... availableRoles) {
        List<String> rolesList = List.of(availableRoles);
        ClientModel realmManagementClient = (ClientModel) Proxy.newProxyInstance(
                ClientModel.class.getClassLoader(),
                new Class<?>[]{ClientModel.class},
                (proxy, method, args) -> {
                    if ("getClientId".equals(method.getName())) {
                        return PasswordResetOutboxRecoveryResourceProvider.REALM_MANAGEMENT_CLIENT;
                    }
                    if ("getRole".equals(method.getName()) && args != null && args.length == 1) {
                        String roleName = (String) args[0];
                        if (rolesList.contains(roleName)) {
                            return createMockRole(roleName);
                        }
                        return null;
                    }
                    return null;
                });

        return (RealmModel) Proxy.newProxyInstance(
                RealmModel.class.getClassLoader(),
                new Class<?>[]{RealmModel.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return realmName;
                    }
                    if ("getId".equals(method.getName())) {
                        return realmName;
                    }
                    if ("getRole".equals(method.getName()) && args != null && args.length == 1) {
                        String roleName = (String) args[0];
                        if (rolesList.contains(roleName)) {
                            return createMockRole(roleName);
                        }
                        return null;
                    }
                    if ("getClientByClientId".equals(method.getName()) && args != null && args.length == 1) {
                        if (PasswordResetOutboxRecoveryResourceProvider.REALM_MANAGEMENT_CLIENT.equals(args[0])) {
                            return realmManagementClient;
                        }
                        return null;
                    }
                    return null;
                });
    }

    private RoleModel createMockRole(String roleName) {
        return (RoleModel) Proxy.newProxyInstance(
                RoleModel.class.getClassLoader(),
                new Class<?>[]{RoleModel.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) {
                        return roleName;
                    }
                    return null;
                });
    }

    private UserModel createMockUser(String username, String... grantedRoles) {
        List<String> grantedList = List.of(grantedRoles);
        return (UserModel) Proxy.newProxyInstance(
                UserModel.class.getClassLoader(),
                new Class<?>[]{UserModel.class},
                (proxy, method, args) -> {
                    if ("getUsername".equals(method.getName())) {
                        return username;
                    }
                    if ("getId".equals(method.getName())) {
                        return "usr-" + username;
                    }
                    if ("hasRole".equals(method.getName()) && args != null && args.length == 1) {
                        RoleModel role = (RoleModel) args[0];
                        return role != null && grantedList.contains(role.getName());
                    }
                    return null;
                });
    }

    private static String createSignedJwtToken(String... roles) {
        StringBuilder rolesJson = new StringBuilder();
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) rolesJson.append(",");
            rolesJson.append("\"").append(roles[i]).append("\"");
        }
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"exp\":" + (Instant.now().getEpochSecond() + 3600) +
                ",\"iat\":" + Instant.now().getEpochSecond() +
                ",\"sub\":\"admin-sub-123\"" +
                ",\"realm_access\":{\"roles\":[" + rolesJson + "]}" +
                ",\"resource_access\":{\"realm-management\":{\"roles\":[" + rolesJson + "]}}}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;
        byte[] dummyKey = "test-secret-signing-key-for-test".getBytes(StandardCharsets.UTF_8);
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(dummyKey, "HmacSHA256"));
            byte[] sigBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);
        } catch (Exception e) {
            return signingInput + ".fakesig";
        }
    }

    private static String createUnsignedJwtToken(String... roles) {
        StringBuilder rolesJson = new StringBuilder();
        for (int i = 0; i < roles.length; i++) {
            if (i > 0) rolesJson.append(",");
            rolesJson.append("\"").append(roles[i]).append("\"");
        }
        String header = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String payload = "{\"exp\":" + (Instant.now().getEpochSecond() + 3600) +
                ",\"iat\":" + Instant.now().getEpochSecond() +
                ",\"sub\":\"admin-sub-123\"" +
                ",\"realm_access\":{\"roles\":[" + rolesJson + "]}" +
                ",\"resource_access\":{\"realm-management\":{\"roles\":[" + rolesJson + "]}}}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedHeader + "." + encodedPayload + ".";
    }

    @Test
    @DisplayName("Duas instâncias concorrentes não entregam simultaneamente o mesmo sourceEventId (Claim/Lease)")
    void concurrentWorkers_doNotDeliverSameEventSimultaneously() throws Exception {
        String eventId = "evt-concurrent-" + UUID.randomUUID();
        Instant now = Instant.parse("2026-08-22T12:00:00Z");

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity entity = store.persistPending(
                eventId, "usr-concurrent-test", "betufu-local", "UPDATE_CREDENTIAL", now);
        tx.commit();

        AtomicInteger deliveryCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PasswordResetOutboxDispatcher.DEFAULT_PATH, exchange -> {
            deliveryCount.incrementAndGet();
            byte[] responseBytes = "{\"status\":\"ACCEPTED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String endpointUri = "http://127.0.0.1:" + port + PasswordResetOutboxDispatcher.DEFAULT_PATH;
            byte[] hmacKey = Base64.getEncoder().encode("secret-key-for-concurrent-claim-test-123".getBytes(StandardCharsets.UTF_8));

            PasswordResetOutboxDispatcher workerA = PasswordResetOutboxDispatcher.builder()
                    .instanceId("worker-instance-A")
                    .endpointUri(endpointUri)
                    .hmacKey(hmacKey)
                    .keyId("v1")
                    .leaseDuration(Duration.ofSeconds(60))
                    .build();

            PasswordResetOutboxDispatcher workerB = PasswordResetOutboxDispatcher.builder()
                    .instanceId("worker-instance-B")
                    .endpointUri(endpointUri)
                    .hmacKey(hmacKey)
                    .keyId("v1")
                    .leaseDuration(Duration.ofSeconds(60))
                    .build();

            int threadCount = 2;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            List<Integer> dispatchedCounts = Collections.synchronizedList(new ArrayList<>());

            executor.submit(() -> {
                try {
                    startLatch.await();
                    EntityManager emA = sessionFactory.createEntityManager();
                    try {
                        EntityTransaction txA = emA.getTransaction();
                        txA.begin();
                        PasswordResetOutboxStore storeA = new PasswordResetOutboxStore(emA);
                        int count = workerA.dispatchEligible(storeA, 10);
                        txA.commit();
                        dispatchedCounts.add(count);
                    } finally {
                        emA.close();
                    }
                } catch (Exception e) {
                    dispatchedCounts.add(-1);
                } finally {
                    doneLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    EntityManager emB = sessionFactory.createEntityManager();
                    try {
                        EntityTransaction txB = emB.getTransaction();
                        txB.begin();
                        PasswordResetOutboxStore storeB = new PasswordResetOutboxStore(emB);
                        int count = workerB.dispatchEligible(storeB, 10);
                        txB.commit();
                        dispatchedCounts.add(count);
                    } finally {
                        emB.close();
                    }
                } catch (Exception e) {
                    dispatchedCounts.add(-1);
                } finally {
                    doneLatch.countDown();
                }
            });

            startLatch.countDown();
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(finished).isTrue();

            assertThat(deliveryCount.get())
                    .as("O endpoint receptor deve ter recebido a entrega exatamente uma vez")
                    .isEqualTo(1);

            int totalDispatched = dispatchedCounts.stream().mapToInt(Integer::intValue).sum();
            assertThat(totalDispatched)
                    .as("Exatamente uma instância deve ter reivindicado e despachado o evento")
                    .isEqualTo(1);

            entityManager.clear();
            Optional<PasswordResetOutboxEntity> delivered = store.findBySourceEventId(eventId);
            assertThat(delivered).isPresent();
            assertThat(delivered.get().getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_DELIVERED);
            assertThat(delivered.get().getDeliveredAt()).isNotNull();

        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Timeouts de conexão (2 s) e resposta (5 s) são configurados e falhas são registradas")
    void timeouts_connection2s_andResponse5s_areEnforced() {
        byte[] base64Key = Base64.getEncoder().encode("test-key-32-bytes-length-required".getBytes(StandardCharsets.UTF_8));
        PasswordResetOutboxDispatcher dispatcher = PasswordResetOutboxDispatcher.builder()
                .endpointUri("http://127.0.0.1:8080" + PasswordResetOutboxDispatcher.DEFAULT_PATH)
                .hmacKey(base64Key)
                .build();

        assertThat(dispatcher.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(dispatcher.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(PasswordResetOutboxDispatcher.DEFAULT_CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(2));
        assertThat(PasswordResetOutboxDispatcher.DEFAULT_READ_TIMEOUT).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Timeout de resposta simula atraso do servidor e agenda retry com contador incrementado")
    void responseTimeout_triggersFailureHandling_andIncrementsAttemptCount() throws Exception {
        HttpServer slowServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowServer.createContext(PasswordResetOutboxDispatcher.DEFAULT_PATH, exchange -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] resp = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        slowServer.start();

        try {
            int port = slowServer.getAddress().getPort();
            String endpoint = "http://127.0.0.1:" + port + PasswordResetOutboxDispatcher.DEFAULT_PATH;
            byte[] hmacKey = Base64.getEncoder().encode("test-secret-key-1234567890123456".getBytes(StandardCharsets.UTF_8));

            PasswordResetOutboxDispatcher fastTimeoutDispatcher = PasswordResetOutboxDispatcher.builder()
                    .endpointUri(endpoint)
                    .hmacKey(hmacKey)
                    .readTimeout(Duration.ofMillis(200))
                    .build();

            String eventId = "evt-timeout-" + UUID.randomUUID();
            EntityTransaction tx = entityManager.getTransaction();
            tx.begin();
            PasswordResetOutboxEntity entity = store.persistPending(
                eventId, "usr-timeout-test", "betufu-local", "UPDATE_CREDENTIAL", Instant.now());
            tx.commit();

            EntityTransaction tx2 = entityManager.getTransaction();
            tx2.begin();
            boolean success = fastTimeoutDispatcher.dispatchOne(entity, store);
            tx2.commit();

            assertThat(success).isFalse();

            Optional<PasswordResetOutboxEntity> found = store.findBySourceEventId(eventId);
            assertThat(found).isPresent();
            PasswordResetOutboxEntity updated = found.get();
            assertThat(updated.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_PENDING);
            assertThat(updated.getAttemptCount()).isEqualTo(1);
            assertThat(updated.getLastError()).contains("timeout");

        } finally {
            slowServer.stop(0);
        }
    }

    @Test
    @DisplayName("Retry tem backoff exponencial com jitter e teto estrito de 30 s")
    void retryBackoff_withJitter_enforcesExponentialGrowth_andStrict30sCap() {
        PasswordResetOutboxDispatcher dispatcher = PasswordResetOutboxDispatcher.builder()
                .maxRetryDelay(Duration.ofSeconds(30))
                .build();

        assertThat(dispatcher.getMaxRetryDelay()).isEqualTo(Duration.ofSeconds(30));

        Random seededRandom = new Random(42L);

        for (int attempt = 1; attempt <= 20; attempt++) {
            Duration delay = dispatcher.computeBackoffWithJitter(attempt, seededRandom);
            assertThat(delay.toMillis())
                    .as("Tentativa %d não pode exceder 30 segundos (30.000 ms)", attempt)
                    .isLessThanOrEqualTo(30000L);
            assertThat(delay.toMillis())
                    .as("Tentativa %d deve ser positiva", attempt)
                    .isGreaterThanOrEqualTo(1L);
        }

        Random maxJitterRandom = new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };

        Duration attempt1Max = dispatcher.computeBackoffWithJitter(1, maxJitterRandom);
        Duration attempt2Max = dispatcher.computeBackoffWithJitter(2, maxJitterRandom);
        Duration attempt3Max = dispatcher.computeBackoffWithJitter(3, maxJitterRandom);
        Duration attempt4Max = dispatcher.computeBackoffWithJitter(4, maxJitterRandom);
        Duration attempt5Max = dispatcher.computeBackoffWithJitter(5, maxJitterRandom);
        Duration attempt10Max = dispatcher.computeBackoffWithJitter(10, maxJitterRandom);

        assertThat(attempt1Max.toMillis()).isEqualTo(1000L);
        assertThat(attempt2Max.toMillis()).isEqualTo(2000L);
        assertThat(attempt3Max.toMillis()).isEqualTo(4000L);
        assertThat(attempt4Max.toMillis()).isEqualTo(8000L);
        assertThat(attempt5Max.toMillis()).isEqualTo(16000L);
        assertThat(attempt10Max.toMillis()).isEqualTo(30000L);

        Random zeroJitterRandom = new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        Duration attempt1Min = dispatcher.computeBackoffWithJitter(1, zeroJitterRandom);
        Duration attempt2Min = dispatcher.computeBackoffWithJitter(2, zeroJitterRandom);
        assertThat(attempt1Min.toMillis()).isEqualTo(500L);
        assertThat(attempt2Min.toMillis()).isEqualTo(1000L);
    }

    @Test
    @DisplayName("Claim abandonado volta a ser elegível após expiração de lease/reinício e conclui em RTO <= 60 s")
    void abandonedClaim_becomesEligibleAfterLeaseExpiration_andRecoversWithinRTO() throws Exception {
        String eventId = "evt-abandoned-claim-" + UUID.randomUUID();
        Instant baseTime = Instant.parse("2026-08-22T10:00:00Z");

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity entity = store.persistPending(
                eventId, "usr-abandoned-test", "betufu-local", "UPDATE_CREDENTIAL", baseTime);
        entity.setStatus(PasswordResetOutboxEntity.STATUS_CLAIMED);
        entity.setClaimInstanceId("dead-worker-instance-crashed");
        entity.setClaimedAt(baseTime);
        tx.commit();

        AtomicInteger deliveryCount = new AtomicInteger(0);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PasswordResetOutboxDispatcher.DEFAULT_PATH, exchange -> {
            deliveryCount.incrementAndGet();
            byte[] responseBytes = "{\"status\":\"DELIVERED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String endpointUri = "http://127.0.0.1:" + port + PasswordResetOutboxDispatcher.DEFAULT_PATH;
            byte[] hmacKey = Base64.getEncoder().encode("secret-key-for-recovery-test-12345".getBytes(StandardCharsets.UTF_8));

            Instant recoveryTime = baseTime.plusSeconds(65);
            Clock recoveryClock = Clock.fixed(recoveryTime, ZoneOffset.UTC);

            PasswordResetOutboxDispatcher newWorker = PasswordResetOutboxDispatcher.builder()
                    .instanceId("active-worker-after-restart")
                    .endpointUri(endpointUri)
                    .hmacKey(hmacKey)
                    .leaseDuration(Duration.ofSeconds(60))
                    .clock(recoveryClock)
                    .build();

            Instant earlyTime = baseTime.plusSeconds(30);
            List<PasswordResetOutboxEntity> notEligibleYet = store.findEligibleForClaim(earlyTime, Duration.ofSeconds(60), 10);
            assertThat(notEligibleYet)
                    .as("Claim com lease de 60s não deve ser elegível aos 30s")
                    .isEmpty();

            List<PasswordResetOutboxEntity> eligibleAfterExpiration = store.findEligibleForClaim(recoveryTime, Duration.ofSeconds(60), 10);
            assertThat(eligibleAfterExpiration)
                    .as("Claim abandonado deve voltar a ser elegível após expiração do lease")
                    .hasSize(1);
            assertThat(eligibleAfterExpiration.get(0).getSourceEventId()).isEqualTo(eventId);

            EntityTransaction txRecovery = entityManager.getTransaction();
            txRecovery.begin();
            int dispatched = newWorker.dispatchEligible(store, 10);
            txRecovery.commit();

            assertThat(dispatched).isEqualTo(1);
            assertThat(deliveryCount.get()).isEqualTo(1);

            Optional<PasswordResetOutboxEntity> recoveredEntity = store.findBySourceEventId(eventId);
            assertThat(recoveredEntity).isPresent();
            assertThat(recoveredEntity.get().getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_DELIVERED);
            assertThat(recoveredEntity.get().getDeliveredAt()).isEqualTo(recoveryTime);

            Duration healthyRto = Duration.between(recoveryTime, recoveredEntity.get().getDeliveredAt());
            assertThat(healthyRto.getSeconds())
                    .as("Entrega saudável deve concluir em até 60 s")
                    .isLessThanOrEqualTo(60L);

        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Envelope HMAC assina canonicalString exata com keyId, timestamp, method, path, sourceEventId e bodySHA256")
    void hmacSignature_signsExactCanonicalEnvelope_andSetsRequiredHeaders() throws Exception {
        String eventId = "evt-hmac-test-" + UUID.randomUUID();
        String subject = "usr-hmac-subject-77";
        String realmId = "betufu-local";
        String eventType = "UPDATE_CREDENTIAL";
        Instant createdAt = Instant.parse("2026-08-22T14:30:00Z");

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity entity = store.persistPending(
                eventId, subject, realmId, eventType, createdAt);
        tx.commit();

        AtomicReference<String> receivedKeyId = new AtomicReference<>();
        AtomicReference<String> receivedTimestamp = new AtomicReference<>();
        AtomicReference<String> receivedSignature = new AtomicReference<>();
        AtomicReference<String> receivedSourceEventId = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PasswordResetOutboxDispatcher.DEFAULT_PATH, exchange -> {
            receivedKeyId.set(exchange.getRequestHeaders().getFirst(PasswordResetOutboxDispatcher.HEADER_KEY_ID));
            receivedTimestamp.set(exchange.getRequestHeaders().getFirst(PasswordResetOutboxDispatcher.HEADER_TIMESTAMP));
            receivedSignature.set(exchange.getRequestHeaders().getFirst(PasswordResetOutboxDispatcher.HEADER_SIGNATURE));
            receivedSourceEventId.set(exchange.getRequestHeaders().getFirst(PasswordResetOutboxDispatcher.HEADER_SOURCE_EVENT_ID));

            try {
                byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
                receivedBody.set(new String(bodyBytes, StandardCharsets.UTF_8));
            } catch (Exception e) {
                receivedBody.set("");
            }

            byte[] resp = "{\"status\":\"OK\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String endpoint = "http://127.0.0.1:" + port + PasswordResetOutboxDispatcher.DEFAULT_PATH;
            byte[] hmacKey = Base64.getEncoder().encode("betufu-super-secret-hmac-key-v1-32b!".getBytes(StandardCharsets.UTF_8));
            String keyId = "v1";

            Clock fixedClock = Clock.fixed(createdAt, ZoneOffset.UTC);
            PasswordResetOutboxDispatcher dispatcher = PasswordResetOutboxDispatcher.builder()
                    .endpointUri(endpoint)
                    .hmacKey(hmacKey)
                    .keyId(keyId)
                    .clock(fixedClock)
                    .build();

            EntityTransaction txDispatch = entityManager.getTransaction();
            txDispatch.begin();
            boolean success = dispatcher.dispatchOne(entity, store);
            txDispatch.commit();

            assertThat(success).isTrue();

            assertThat(receivedKeyId.get()).isEqualTo("v1");
            assertThat(receivedTimestamp.get()).isEqualTo(String.valueOf(createdAt.toEpochMilli()));
            assertThat(receivedSourceEventId.get()).isEqualTo(eventId);

            String body = receivedBody.get();
            assertThat(body).contains("\"sourceEventId\":\"" + eventId + "\"");
            assertThat(body).contains("\"subject\":\"" + subject + "\"");
            assertThat(body).contains("\"realmId\":\"" + realmId + "\"");
            assertThat(body).doesNotContain("\"eventType\"");
            assertThat(body).contains("\"timestamp\":\"" + createdAt.toEpochMilli() + "\"");

            String bodySha256 = PasswordResetOutboxDispatcher.sha256Hex(body);
            String expectedCanonical = PasswordResetOutboxDispatcher.buildCanonicalString(
                    keyId,
                    receivedTimestamp.get(),
                    "POST",
                    PasswordResetOutboxDispatcher.DEFAULT_PATH,
                    eventId,
                    bodySha256);

            String expectedSignature = PasswordResetOutboxDispatcher.calculateHmacSha256(
                    PasswordResetOutboxDispatcher.decodeBase64Key(hmacKey), expectedCanonical);
            assertThat(receivedSignature.get()).isEqualTo(expectedSignature);

            String tamperedBodyCanonical = PasswordResetOutboxDispatcher.buildCanonicalString(
                    keyId,
                    receivedTimestamp.get(),
                    "POST",
                    PasswordResetOutboxDispatcher.DEFAULT_PATH,
                    eventId,
                    PasswordResetOutboxDispatcher.sha256Hex(body + "tampered"));
            String tamperedSignature = PasswordResetOutboxDispatcher.calculateHmacSha256(hmacKey, tamperedBodyCanonical);
            assertThat(receivedSignature.get()).isNotEqualTo(tamperedSignature);

        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Payload JSON transporta somente dados permitidos sem vazar senha, e-mail ou token (PRIV-01)")
    void payload_containsOnlyAllowlistedFields_noSensitiveData() {
        PasswordResetOutboxEntity entity = new PasswordResetOutboxEntity(
                "source-123",
                "usr-subject-456",
                "realm-betufu",
                "UPDATE_CREDENTIAL",
                Instant.parse("2024-08-22T10:15:30Z"));

        String payload = PasswordResetOutboxDispatcher.buildPayloadJson(entity);

        assertThat(payload).contains("\"sourceEventId\":\"source-123\"");
        assertThat(payload).contains("\"subject\":\"usr-subject-456\"");
        assertThat(payload).contains("\"realmId\":\"realm-betufu\"");
        assertThat(payload).doesNotContain("\"eventType\"");
        assertThat(payload)
                .as("o destino recusa qualquer valor que nao seja VALUE_STRING, inclusive o timestamp")
                .contains("\"timestamp\":\"1724321730000\"");

        assertThat(payload).doesNotContain("password");
        assertThat(payload).doesNotContain("email");
        assertThat(payload).doesNotContain("token");
        assertThat(payload).doesNotContain("cookie");
        assertThat(payload).doesNotContain("secret");
        assertThat(payload).doesNotContain("username");
    }

    @Test
    @DisplayName("Factory gerencia o ciclo de vida do dispatcher (init, postInit, close)")
    void factoryLifecycle_managesDispatcherState() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();

        Config.Scope scope = new Config.Scope() {
            @Override
            public String get(String key) {
                if ("endpoint".equals(key)) return "http://127.0.0.1:8080/api/v1/internal/events/password-reset";
                if ("hmacKey".equals(key)) return java.util.Base64.getEncoder().encodeToString("secret-key-12345678901234567890".getBytes(StandardCharsets.UTF_8));
                if ("keyId".equals(key)) return "v1";
                return null;
            }

            @Override
            public String get(String key, String defaultValue) {
                String val = get(key);
                return val != null ? val : defaultValue;
            }

            @Override
            public String[] getArray(String key) {
                return null;
            }

            @Override
            public Integer getInt(String key) {
                return null;
            }

            @Override
            public Integer getInt(String key, Integer defaultValue) {
                return defaultValue;
            }

            @Override
            public Long getLong(String key) {
                return null;
            }

            @Override
            public Long getLong(String key, Long defaultValue) {
                return defaultValue;
            }

            @Override
            public Boolean getBoolean(String key) {
                return null;
            }

            @Override
            public Boolean getBoolean(String key, Boolean defaultValue) {
                return defaultValue;
            }

            @Override
            public Config.Scope scope(String... scope) {
                return this;
            }

            @Override
            public Set<String> getPropertyNames() {
                return Set.of("endpoint", "hmacKey", "keyId");
            }

            @Override
            public Config.Scope root() {
                return this;
            }
        };

        factory.init(scope);
        PasswordResetOutboxDispatcher dispatcher = factory.getDispatcher();
        assertThat(dispatcher).isNotNull();
        assertThat(dispatcher.getEndpointUri()).isEqualTo("http://127.0.0.1:8080/api/v1/internal/events/password-reset");
        assertThat(dispatcher.getKeyId()).isEqualTo("v1");

        factory.close();
        assertThat(dispatcher.isRunning()).isFalse();
    }

    @Test
    @DisplayName("A quinta falha emite alerta uma vez, transiciona registro para FAILED e não o exclui (D-40)")
    void fifthFailure_emitsStructuredAlertOnce_transitionsToFailed_andNeverDeletesRecord() {
        String eventId = "evt-fifth-failure-" + UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-22T08:00:00Z");

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity entity = store.persistPending(
                eventId, "usr-subject-fail", "betufu-local", "UPDATE_CREDENTIAL", createdAt);
        tx.commit();

        AtomicInteger alertCallCount = new AtomicInteger(0);
        AtomicReference<PasswordResetOutboxDispatcher.AlertEvent> capturedAlert = new AtomicReference<>();

        PasswordResetOutboxDispatcher.AlertListener listener = alert -> {
            alertCallCount.incrementAndGet();
            capturedAlert.set(alert);
        };

        PasswordResetOutboxDispatcher dispatcher = PasswordResetOutboxDispatcher.builder()
                .endpointUri("http://127.0.0.1:1/invalid-unreachable-port")
                .hmacKey(Base64.getEncoder().encode("test-hmac-key-12345678901234567890".getBytes(StandardCharsets.UTF_8)))
                .maxAttempts(5)
                .alertListener(listener)
                .build();

        // 4 primeiras falhas: contador incrementa, status continua PENDING, nenhum alerta
        for (int i = 1; i <= 4; i++) {
            EntityTransaction txFail = entityManager.getTransaction();
            txFail.begin();
            boolean result = dispatcher.dispatchOne(entity, store);
            txFail.commit();

            assertThat(result).isFalse();
            assertThat(alertCallCount.get()).as("Não deve emitir alerta antes da 5ª falha").isZero();

            Optional<PasswordResetOutboxEntity> current = store.findBySourceEventId(eventId);
            assertThat(current).isPresent();
            assertThat(current.get().getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_PENDING);
            assertThat(current.get().getAttemptCount()).isEqualTo(i);
        }

        // 5ª falha: transiciona para FAILED, emite alerta estruturado exatamente 1 vez
        EntityTransaction txFifth = entityManager.getTransaction();
        txFifth.begin();
        boolean fifthResult = dispatcher.dispatchOne(entity, store);
        txFifth.commit();

        assertThat(fifthResult).isFalse();
        assertThat(alertCallCount.get()).as("Deve emitir alerta exatamente uma vez na 5ª falha").isEqualTo(1);

        PasswordResetOutboxDispatcher.AlertEvent alert = capturedAlert.get();
        assertThat(alert).isNotNull();
        assertThat(alert.alertType()).isEqualTo("PASSWORD_RESET_DELIVERY_FAILED");
        assertThat(alert.sourceEventId()).isEqualTo(eventId);
        assertThat(alert.status()).isEqualTo(PasswordResetOutboxEntity.STATUS_FAILED);
        assertThat(alert.timestamp()).isNotNull();

        // Guard de propriedade. Assertar palavras genéricas como "password" passava por acaso: a
        // fixture nunca as continha, então o guard aprovava um alerta que vazava subject e realm.
        // O que precisa ser proibido são os valores reais do registro.
        String alertString = alert.toString();
        assertThat(alertString)
                .as("o alerta não pode transportar subject, realm, tipo do evento nem erro cru")
                .doesNotContain("usr-subject-fail")
                .doesNotContain("betufu-local")
                .doesNotContain("UPDATE_CREDENTIAL");

        // O registro persiste intacto no banco no estado FAILED (nunca excluído)
        Optional<PasswordResetOutboxEntity> failedEntity = store.findBySourceEventId(eventId);
        assertThat(failedEntity).isPresent();
        assertThat(failedEntity.get().getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_FAILED);
        assertThat(failedEntity.get().getAttemptCount()).isEqualTo(5);

        // Registro FAILED não é mais elegível para claim pelo worker comum
        List<PasswordResetOutboxEntity> eligible = store.findEligibleForClaim(Instant.now(), Duration.ofSeconds(60), 10);
        assertThat(eligible.stream().noneMatch(e -> e.getSourceEventId().equals(eventId))).isTrue();

        // 6ª chamada não re-emite alerta
        EntityTransaction txSixth = entityManager.getTransaction();
        txSixth.begin();
        dispatcher.dispatchOne(failedEntity.get(), store);
        txSixth.commit();
        assertThat(alertCallCount.get()).as("Alerta não deve ser disparado novamente além da 5ª falha").isEqualTo(1);
    }

    @Test
    @DisplayName("Cleanup remove somente registros DELIVERED com mais de 30 dias; PENDING, CLAIMED e FAILED permanecem (D-40)")
    void cleanup_removesOnlyDeliveredOlderThan30Days_preservesPendingFailedClaimedAndRecentDelivered() {
        Instant now = Instant.parse("2026-08-22T12:00:00Z");
        Instant thirtyDaysCutoff = now.minus(Duration.ofDays(30));

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        // 1. DELIVERED com 35 dias (mais antigo que 30 dias) -> DEVE SER REMOVIDO
        PasswordResetOutboxEntity delivered35d = store.persistPending(
                "evt-delivered-35d", "usr-1", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(40)));
        delivered35d.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        delivered35d.setDeliveredAt(now.minus(Duration.ofDays(35)));

        // 2. DELIVERED com 30 dias e 1 minuto (excede 30 dias) -> DEVE SER REMOVIDO
        PasswordResetOutboxEntity delivered30d1m = store.persistPending(
                "evt-delivered-30d-1m", "usr-2", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(32)));
        delivered30d1m.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        delivered30d1m.setDeliveredAt(thirtyDaysCutoff.minusSeconds(60));

        // 3. DELIVERED com 29 dias (recente) -> DEVE SER PRESERVADO
        PasswordResetOutboxEntity delivered29d = store.persistPending(
                "evt-delivered-29d", "usr-3", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(30)));
        delivered29d.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        delivered29d.setDeliveredAt(now.minus(Duration.ofDays(29)));

        // 4. DELIVERED há 1 hora -> DEVE SER PRESERVADO
        PasswordResetOutboxEntity delivered1h = store.persistPending(
                "evt-delivered-1h", "usr-4", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofHours(2)));
        delivered1h.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        delivered1h.setDeliveredAt(now.minus(Duration.ofHours(1)));

        // 5. FAILED há 60 dias -> DEVE SER PRESERVADO (FAILED nunca é excluído pelo cleanup)
        PasswordResetOutboxEntity failed60d = store.persistPending(
                "evt-failed-60d", "usr-5", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(60)));
        failed60d.setStatus(PasswordResetOutboxEntity.STATUS_FAILED);
        failed60d.setLastError("Connection failed after 5 retries");

        // 6. PENDING há 60 dias -> DEVE SER PRESERVADO
        PasswordResetOutboxEntity pending60d = store.persistPending(
                "evt-pending-60d", "usr-6", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(60)));

        // 7. CLAIMED há 60 dias -> DEVE SER PRESERVADO
        PasswordResetOutboxEntity claimed60d = store.persistPending(
                "evt-claimed-60d", "usr-7", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(60)));
        claimed60d.setStatus(PasswordResetOutboxEntity.STATUS_CLAIMED);
        claimed60d.setClaimedAt(now.minus(Duration.ofDays(60)));
        claimed60d.setClaimInstanceId("old-instance");

        tx.commit();

        EntityTransaction txCleanup = entityManager.getTransaction();
        txCleanup.begin();
        int deletedCount = store.deleteDeliveredOlderThan(thirtyDaysCutoff);
        txCleanup.commit();

        assertThat(deletedCount)
                .as("Exatamente os 2 registros DELIVERED anteriores a 30 dias devem ser removidos")
                .isEqualTo(2);

        assertThat(store.findBySourceEventId("evt-delivered-35d")).isEmpty();
        assertThat(store.findBySourceEventId("evt-delivered-30d-1m")).isEmpty();

        assertThat(store.findBySourceEventId("evt-delivered-29d")).isPresent();
        assertThat(store.findBySourceEventId("evt-delivered-1h")).isPresent();
        assertThat(store.findBySourceEventId("evt-failed-60d")).isPresent();
        assertThat(store.findBySourceEventId("evt-pending-60d")).isPresent();
        assertThat(store.findBySourceEventId("evt-claimed-60d")).isPresent();
    }

    @Test
    @DisplayName("Recovery Provider exige papel manage-realm; rejeita não autenticado (401) e sem autoridade (403)")
    void recoveryResourceProvider_authorization_strictManageRealmEnforcement() {
        RealmModel realm = createMockRealm("betufu-local", "manage-realm", "view-realm", "user");

        // 1. Sem token e sem usuário autenticado -> 401 UnauthorizedException
        KeycloakSession unauthSession = createAuthorizedSessionProxy(entityManager, null, realm, null);
        PasswordResetOutboxRecoveryResourceProvider unauthProvider =
                new PasswordResetOutboxRecoveryResourceProvider(unauthSession, store, Clock.systemUTC());

        assertThatThrownBy(unauthProvider::checkManageRealmPermission)
                .isInstanceOf(PasswordResetOutboxRecoveryResourceProvider.UnauthorizedException.class);

        // 2. Credencial forjada: um bearer nao assinado NAO e mais honrado como autoridade.
        //    Antes da revisao externa o provider decodificava o JWT sem verificar assinatura,
        //    e um token alg:none era aceito. Agora a autoridade vem do contexto autenticado,
        //    entao um bearer arbitrario sem usuario autenticado equivale a nao autenticado.
        KeycloakSession forgedBearerSession = createAuthorizedSessionProxy(
                entityManager, null, realm, "Bearer eyJhbGciOiJub25lIn0.eyJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsibWFuYWdlLXJlYWxtIl19fQ.");
        PasswordResetOutboxRecoveryResourceProvider forgedBearerProvider =
                new PasswordResetOutboxRecoveryResourceProvider(forgedBearerSession, store, Clock.systemUTC());

        assertThatThrownBy(forgedBearerProvider::checkManageRealmPermission)
                .isInstanceOf(PasswordResetOutboxRecoveryResourceProvider.UnauthorizedException.class);

        // 3. Usuário autenticado sem role manage-realm -> 403 ForbiddenException
        UserModel userWithoutManageRealm = createMockUser("standard-operator", "view-realm", "user");
        KeycloakSession userWithoutManageRealmSession =
                createAuthorizedSessionProxy(entityManager, userWithoutManageRealm, realm, null);
        PasswordResetOutboxRecoveryResourceProvider userWithoutManageRealmProvider =
                authorizedProvider(userWithoutManageRealmSession, userWithoutManageRealm, Clock.systemUTC());

        assertThatThrownBy(userWithoutManageRealmProvider::checkManageRealmPermission)
                .isInstanceOf(PasswordResetOutboxRecoveryResourceProvider.ForbiddenException.class);

        // 4. Usuario autenticado COM manage-realm -> Autorizado com sucesso
        UserModel userWithManageRealm = createMockUser("realm-admin", "manage-realm");
        KeycloakSession authSession =
                createAuthorizedSessionProxy(entityManager, userWithManageRealm, realm, null);
        PasswordResetOutboxRecoveryResourceProvider authProvider =
                authorizedProvider(authSession, userWithManageRealm, Clock.systemUTC());

        authProvider.checkManageRealmPermission();

        // 5. Usuário COM role manage-realm -> Autorizado com sucesso
        UserModel adminUser = createMockUser("realm-admin", "manage-realm");
        KeycloakSession adminUserSession =
                createAuthorizedSessionProxy(entityManager, adminUser, realm, null);
        PasswordResetOutboxRecoveryResourceProvider adminUserProvider =
                authorizedProvider(adminUserSession, adminUser, Clock.systemUTC());

        adminUserProvider.checkManageRealmPermission();
    }

    @Test
    @DisplayName("Replay autorizado com manage-realm restaura FAILED para PENDING preservando sourceEventId (D-40)")
    void recoveryResourceProvider_replayFailed_authorized_restoresToPending_andPreservesSourceEventId() {
        String eventId = "evt-replay-" + UUID.randomUUID();
        String subject = "usr-replay-subject-123";
        String realmId = "betufu-local";
        String eventType = "UPDATE_CREDENTIAL";
        Instant createdAt = Instant.parse("2026-08-22T06:00:00Z");

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity entity = store.persistPending(
                eventId, subject, realmId, eventType, createdAt);
        entity.setStatus(PasswordResetOutboxEntity.STATUS_FAILED);
        entity.setLastError("Failed 5 times");
        entity.setClaimInstanceId("dead-instance");
        entity.setClaimedAt(createdAt.plusSeconds(30));
        tx.commit();

        RealmModel realm = createMockRealm("betufu-local", "manage-realm");
        UserModel adminUser = createMockUser("realm-admin", "manage-realm");
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, adminUser, realm, null);

        PasswordResetOutboxRecoveryResourceProvider provider =
                authorizedProvider(session, adminUser, Clock.systemUTC());

        EntityTransaction txReplay = entityManager.getTransaction();
        txReplay.begin();
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult response = provider.executeReplay(eventId);
        txReplay.commit();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getEntity()).contains("\"status\":\"REPLAYED\"");
        assertThat(response.getEntity()).contains("\"sourceEventId\":\"" + eventId + "\"");
        assertThat(response.getEntity()).contains("\"newStatus\":\"PENDING\"");

        Optional<PasswordResetOutboxEntity> replayedEntity = store.findBySourceEventId(eventId);
        assertThat(replayedEntity).isPresent();
        PasswordResetOutboxEntity restored = replayedEntity.get();

        assertThat(restored.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_PENDING);
        assertThat(restored.getAttemptCount()).isEqualTo(0);
        assertThat(restored.getLastError()).isNull();
        assertThat(restored.getClaimInstanceId()).isNull();
        assertThat(restored.getClaimedAt()).isNull();

        assertThat(restored.getSourceEventId()).isEqualTo(eventId);
        assertThat(restored.getSubject()).isEqualTo(subject);
        assertThat(restored.getRealmId()).isEqualTo(realmId);
        assertThat(restored.getEventType()).isEqualTo(eventType);
        assertThat(restored.getCreatedAt()).isEqualTo(createdAt);

        List<PasswordResetOutboxEntity> eligible = store.findEligibleForClaim(Instant.now(), Duration.ofSeconds(60), 10);
        assertThat(eligible.stream().anyMatch(e -> e.getSourceEventId().equals(eventId))).isTrue();
    }

    @Test
    @DisplayName("Replay rejeita registros que não estão em FAILED (409 Conflict) e sourceEventId inexistente (404 Not Found)")
    void recoveryResourceProvider_replay_rejectsNonFailedRecordsAndUnknownIds() {
        String pendingEventId = "evt-pending-" + UUID.randomUUID();
        String deliveredEventId = "evt-delivered-" + UUID.randomUUID();

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        store.persistPending(pendingEventId, "usr-p", "betufu-local", "UPDATE_CREDENTIAL", Instant.now());
        PasswordResetOutboxEntity delivered = store.persistPending(
                deliveredEventId, "usr-d", "betufu-local", "UPDATE_CREDENTIAL", Instant.now());
        delivered.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        delivered.setDeliveredAt(Instant.now());
        tx.commit();

        RealmModel realm = createMockRealm("betufu-local", "manage-realm");
        UserModel adminUser = createMockUser("realm-admin", "manage-realm");
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, adminUser, realm, null);

        PasswordResetOutboxRecoveryResourceProvider provider =
                authorizedProvider(session, adminUser, Clock.systemUTC());

        // 1. Replay em evento inexistente -> 404 Not Found
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult notFoundResp = provider.executeReplay("evt-non-existent-id");
        assertThat(notFoundResp.getStatus()).isEqualTo(404);

        // 2. Replay em evento PENDING -> 409 Conflict
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult conflictPendingResp = provider.executeReplay(pendingEventId);
        assertThat(conflictPendingResp.getStatus()).isEqualTo(409);
        assertThat(conflictPendingResp.getEntity()).contains("not in FAILED status");

        // 3. Replay em evento DELIVERED -> 409 Conflict
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult conflictDeliveredResp = provider.executeReplay(deliveredEventId);
        assertThat(conflictDeliveredResp.getStatus()).isEqualTo(409);
        assertThat(conflictDeliveredResp.getEntity()).contains("not in FAILED status");

        // 4. Replay com sourceEventId nulo ou em branco -> 400 Bad Request
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult badRequestResp = provider.executeReplay("   ");
        assertThat(badRequestResp.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("ListFailed e Cleanup operam de ponta a ponta com DTOs seguros e sem vazamento de segredos")
    void recoveryResourceProvider_listFailed_and_cleanup_workEndToEnd() {
        Instant now = Instant.parse("2026-08-22T15:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity failed1 = store.persistPending(
                "evt-failed-1", "usr-failed-1", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofHours(5)));
        failed1.setStatus(PasswordResetOutboxEntity.STATUS_FAILED);
        failed1.setLastError("HTTP status 503");

        PasswordResetOutboxEntity deliveredOld = store.persistPending(
                "evt-delivered-old", "usr-del-old", "betufu-local", "UPDATE_CREDENTIAL", now.minus(Duration.ofDays(45)));
        deliveredOld.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        deliveredOld.setDeliveredAt(now.minus(Duration.ofDays(40)));
        tx.commit();

        RealmModel realm = createMockRealm("betufu-local", "manage-realm");
        UserModel adminUser = createMockUser("realm-admin", "manage-realm");
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, adminUser, realm, null);

        PasswordResetOutboxRecoveryResourceProvider provider =
                authorizedProvider(session, adminUser, fixedClock);

        // Testar listFailed
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult listResponse = provider.executeListFailed();
        assertThat(listResponse.getStatus()).isEqualTo(200);
        String listJson = listResponse.getEntity();
        assertThat(listJson).contains("evt-failed-1");
        assertThat(listJson)
                .as("o resumo operacional nao pode expor o subject do titular")
                .doesNotContain("usr-failed-1");
        assertThat(listJson).contains("FAILED");
        assertThat(listJson).doesNotContain("password");
        assertThat(listJson).doesNotContain("email");

        // Testar cleanup
        EntityTransaction txCleanup = entityManager.getTransaction();
        txCleanup.begin();
        PasswordResetOutboxRecoveryResourceProvider.RecoveryResult cleanupResponse = provider.executeCleanupDelivered();
        txCleanup.commit();

        assertThat(cleanupResponse.getStatus()).isEqualTo(200);
        String cleanupJson = cleanupResponse.getEntity();
        assertThat(cleanupJson).contains("\"deletedCount\":1");
        assertThat(cleanupJson).contains("\"retentionDays\":30");

        assertThat(store.findBySourceEventId("evt-delivered-old")).isEmpty();
        assertThat(store.findBySourceEventId("evt-failed-1")).isPresent();
    }

    @Test
    @DisplayName("SPI RealmResourceProviderFactory é descoberta pelo ServiceLoader com ID correto")
    void serviceLoader_discoversRecoveryResourceProviderFactory() {
        ServiceLoader<RealmResourceProviderFactory> loader =
                ServiceLoader.load(RealmResourceProviderFactory.class);

        List<RealmResourceProviderFactory> factories = new ArrayList<>();
        loader.forEach(factories::add);

        assertThat(factories)
                .as("RealmResourceProviderFactory deve estar registrada e detectável pelo ServiceLoader")
                .anyMatch(f -> f instanceof PasswordResetOutboxRecoveryResourceProvider
                        && PasswordResetOutboxRecoveryResourceProvider.ID.equals(f.getId()));
    }

    private Config.Scope createConfigScope(String endpoint, String hmacKey, String keyId, Boolean enabled) {
        return new Config.Scope() {
            @Override
            public String get(String key) {
                if ("endpoint".equals(key)) return endpoint;
                if ("hmacKey".equals(key)) return hmacKey;
                if ("keyId".equals(key)) return keyId;
                return null;
            }

            @Override
            public String get(String key, String defaultValue) {
                String val = get(key);
                return val != null ? val : defaultValue;
            }

            @Override
            public String[] getArray(String key) {
                return null;
            }

            @Override
            public Integer getInt(String key) {
                return null;
            }

            @Override
            public Integer getInt(String key, Integer defaultValue) {
                return defaultValue;
            }

            @Override
            public Long getLong(String key) {
                return null;
            }

            @Override
            public Long getLong(String key, Long defaultValue) {
                return defaultValue;
            }

            @Override
            public Boolean getBoolean(String key) {
                if ("enabled".equals(key)) return enabled;
                return null;
            }

            @Override
            public Boolean getBoolean(String key, Boolean defaultValue) {
                Boolean val = getBoolean(key);
                return val != null ? val : defaultValue;
            }

            @Override
            public Config.Scope scope(String... scope) {
                return this;
            }

            @Override
            public Set<String> getPropertyNames() {
                return Set.of("endpoint", "hmacKey", "keyId", "enabled");
            }

            @Override
            public Config.Scope root() {
                return this;
            }
        };
    }

    @Test
    @DisplayName("Factory fail-closed (a): sem a chave enabled na Config.Scope, activationAllowed é false por padrão")
    void factory_failClosed_withoutEnabledKey_activationAllowedIsFalseByDefault() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();
        String valid32bKey = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        Config.Scope scope = createConfigScope("https://backend.betufu.local/api/v1/internal/events/password-reset", valid32bKey, "v1", null);

        factory.init(scope);

        assertThat(factory.activationAllowed())
                .as("Sem enabled explícito, a ativação deve permanecer desabilitada (fail-closed)")
                .isFalse();
    }

    @Test
    @DisplayName("Factory fail-closed (b): com enabled=true e endpoint http não-seguro, activationAllowed é false")
    void factory_failClosed_withEnabledTrue_andHttpEndpoint_activationAllowedIsFalse() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();
        String valid32bKey = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        Config.Scope scope = createConfigScope("http://backend.betufu.local/api/v1/internal/events/password-reset", valid32bKey, "v1", true);

        factory.init(scope);

        assertThat(factory.activationAllowed())
                .as("Endpoint http:// não é seguro e deve impedir a ativação")
                .isFalse();
    }

    @Test
    @DisplayName("Factory fail-closed (c): com enabled=true, endpoint https e hmacKey ausente ou vazia, activationAllowed é false")
    void factory_failClosed_withEnabledTrue_httpsEndpoint_andMissingHmacKey_activationAllowedIsFalse() {
        BetufuPasswordEventListenerFactory factoryMissing = new BetufuPasswordEventListenerFactory();
        Config.Scope scopeMissing = createConfigScope("https://backend.betufu.local/api/v1/internal/events/password-reset", null, "v1", true);
        factoryMissing.init(scopeMissing);
        assertThat(factoryMissing.activationAllowed())
                .as("hmacKey ausente deve impedir a ativação")
                .isFalse();

        BetufuPasswordEventListenerFactory factoryEmpty = new BetufuPasswordEventListenerFactory();
        Config.Scope scopeEmpty = createConfigScope("https://backend.betufu.local/api/v1/internal/events/password-reset", "   ", "v1", true);
        factoryEmpty.init(scopeEmpty);
        assertThat(factoryEmpty.activationAllowed())
                .as("hmacKey vazia deve impedir a ativação")
                .isFalse();
    }

    @Test
    @DisplayName("Factory fail-closed (d): com enabled=true, endpoint https e chave Base64 com menos de 32 bytes decodificados, activationAllowed é false")
    void factory_failClosed_withEnabledTrue_httpsEndpoint_andKeyLessThan32Bytes_activationAllowedIsFalse() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();
        String short16bKey = Base64.getEncoder().encodeToString("1234567890123456".getBytes(StandardCharsets.UTF_8));
        Config.Scope scope = createConfigScope("https://backend.betufu.local/api/v1/internal/events/password-reset", short16bKey, "v1", true);

        factory.init(scope);

        assertThat(BetufuPasswordEventListenerFactory.MINIMUM_HMAC_KEY_BYTES).isEqualTo(32);
        assertThat(factory.activationAllowed())
                .as("Chave com menos de 32 bytes decodificados deve ser rejeitada pelo activationAllowed")
                .isFalse();
    }

    @Test
    @DisplayName("Factory fail-closed (e): com enabled=true, endpoint https e hmacKey com Base64 inválido, init não lança exceção e activationAllowed é false")
    void factory_failClosed_withEnabledTrue_httpsEndpoint_andInvalidBase64Key_initDoesNotThrow_andActivationAllowedIsFalse() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();
        String invalidBase64Key = "not-valid-base64-!@#$%^&*()_+=~`";
        Config.Scope scope = createConfigScope("https://backend.betufu.local/api/v1/internal/events/password-reset", invalidBase64Key, "v1", true);

        factory.init(scope);

        assertThat(factory.activationAllowed())
                .as("Chave com Base64 inválido não deve quebrar o boot e activationAllowed deve ser false")
                .isFalse();
    }

    @Test
    @DisplayName("Factory fail-closed (f): com enabled=true, endpoint https, keyId não vazio e chave Base64 de ao menos 32 bytes, activationAllowed é true")
    void factory_failClosed_withValidConfiguration_activationAllowedIsTrue() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();
        String valid32bKey = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        Config.Scope scope = createConfigScope("https://backend.betufu.local/api/v1/internal/events/password-reset", valid32bKey, "v1", true);

        factory.init(scope);

        assertThat(factory.activationAllowed())
                .as("Configuração completa e válida com HTTPS, keyId e 32 bytes deve autorizar a ativação")
                .isTrue();
    }

    @Test
    @DisplayName("Factory fail-closed (g): HTTPS com path diferente do contrato nao autoriza a ativacao")
    void factory_failClosed_withHttpsButWrongPath_activationAllowedIsFalse() {
        String valid32bKey = Base64.getEncoder().encodeToString(new byte[32]);

        BetufuPasswordEventListenerFactory wrongPath = new BetufuPasswordEventListenerFactory();
        wrongPath.init(createConfigScope("https://backend.betufu.local/events", valid32bKey, "v1", true));
        assertThat(wrongPath.activationAllowed())
                .as("endpoint HTTPS com path fora do contrato entregaria 404 em silencio")
                .isFalse();

        BetufuPasswordEventListenerFactory withQuery = new BetufuPasswordEventListenerFactory();
        withQuery.init(createConfigScope(
                "https://backend.betufu.local/api/v1/internal/events/password-reset?debug=1",
                valid32bKey, "v1", true));
        assertThat(withQuery.activationAllowed())
                .as("query string nao pertence ao contrato de transporte")
                .isFalse();

        BetufuPasswordEventListenerFactory withFragment = new BetufuPasswordEventListenerFactory();
        withFragment.init(createConfigScope(
                "https://backend.betufu.local/api/v1/internal/events/password-reset#x",
                valid32bKey, "v1", true));
        assertThat(withFragment.activationAllowed())
                .as("fragment nao pertence ao contrato de transporte")
                .isFalse();
    }

    @Test
    @DisplayName("Factory fail-closed (h): URI sem host ou com userInfo nao autoriza a ativacao")
    void factory_failClosed_withoutHostOrWithUserInfo_activationAllowedIsFalse() {
        String valid32bKey = Base64.getEncoder().encodeToString(new byte[32]);

        BetufuPasswordEventListenerFactory noHost = new BetufuPasswordEventListenerFactory();
        noHost.init(createConfigScope("https:/api/v1/internal/events/password-reset", valid32bKey, "v1", true));
        assertThat(noHost.activationAllowed())
                .as("uma URI sem autoridade nao identifica servidor algum e nao pode ativar a integracao")
                .isFalse();

        BetufuPasswordEventListenerFactory withUserInfo = new BetufuPasswordEventListenerFactory();
        withUserInfo.init(createConfigScope(
                "https://usuario@backend.betufu.local/api/v1/internal/events/password-reset",
                valid32bKey, "v1", true));
        assertThat(withUserInfo.activationAllowed())
                .as("credencial embutida na URI nao pertence ao contrato de transporte")
                .isFalse();

        BetufuPasswordEventListenerFactory approved = new BetufuPasswordEventListenerFactory();
        approved.init(createConfigScope(
                "https://backend.betufu.local/api/v1/internal/events/password-reset",
                valid32bKey, "v1", true));
        assertThat(approved.activationAllowed())
                .as("controle positivo: host e path exatos autorizam a ativacao")
                .isTrue();
    }

    @Test
    @DisplayName("Sem autenticacao valida o recurso falha fechado em 401, pelo caminho de producao do BearerTokenAuthenticator")
    void recoveryProvider_withoutAuthentication_failsClosedWith401() {
        RealmModel realm = createRealmWithRoles("betufu-local", List.of("manage-realm"), List.of("manage-realm"));
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, null, realm, null);

        // Provider sem override: authenticatedUser() percorre o BearerTokenAuthenticator de verdade.
        PasswordResetOutboxRecoveryResourceProvider provider =
                new PasswordResetOutboxRecoveryResourceProvider(session, store, Clock.systemUTC());

        assertThatThrownBy(provider::checkManageRealmPermission)
                .isInstanceOf(PasswordResetOutboxRecoveryResourceProvider.UnauthorizedException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    @DisplayName("Autenticador que devolve usuario nulo tambem resulta em 401, sem cair na checagem de papel")
    void recoveryProvider_authenticatorReturningNullUser_failsClosedWith401() {
        RealmModel realm = createRealmWithRoles("betufu-local", List.of(), List.of("manage-realm"));
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, null, realm, null);

        PasswordResetOutboxRecoveryResourceProvider provider =
                new PasswordResetOutboxRecoveryResourceProvider(session, store, Clock.systemUTC()) {
                    @Override
                    protected UserModel authenticatedUser() {
                        return null;
                    }
                };

        assertThatThrownBy(provider::checkManageRealmPermission)
                .as("ausencia de usuario autenticado e 401, nunca 403")
                .isInstanceOf(PasswordResetOutboxRecoveryResourceProvider.UnauthorizedException.class)
                .hasMessage("Authentication is required");
    }

    @Test
    @DisplayName("Papel de realm homonimo manage-realm NAO concede autoridade; so o papel do cliente realm-management")
    void recoveryProvider_realmLevelHomonymRole_isRejectedWith403() {
        // Papel manage-realm existe no realm, mas nao no cliente realm-management.
        RealmModel realm = createRealmWithRoles("betufu-local", List.of("manage-realm"), List.of());
        UserModel user = createMockUser("operador", "manage-realm");
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, user, realm, null);

        PasswordResetOutboxRecoveryResourceProvider provider =
                new PasswordResetOutboxRecoveryResourceProvider(session, store, Clock.systemUTC()) {
                    @Override
                    protected UserModel authenticatedUser() {
                        return user;
                    }
                };

        assertThatThrownBy(provider::checkManageRealmPermission)
                .as("um papel de realm com nome escolhido livremente nao pode virar autoridade administrativa")
                .isInstanceOf(PasswordResetOutboxRecoveryResourceProvider.ForbiddenException.class);
    }

    @Test
    @DisplayName("Controle positivo: papel manage-realm do cliente realm-management concede autoridade")
    void recoveryProvider_realmManagementClientRole_grantsAuthority() {
        RealmModel realm = createRealmWithRoles("betufu-local", List.of(), List.of("manage-realm"));
        UserModel user = createMockUser("admin", "manage-realm");
        KeycloakSession session = createAuthorizedSessionProxy(entityManager, user, realm, null);

        PasswordResetOutboxRecoveryResourceProvider provider =
                new PasswordResetOutboxRecoveryResourceProvider(session, store, Clock.systemUTC()) {
                    @Override
                    protected UserModel authenticatedUser() {
                        return user;
                    }
                };

        assertThatCode(provider::checkManageRealmPermission).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("O bytecode do provider nao referencia mais TokenVerifier nem TokenManager, e referencia AppAuthManager")
    void recoveryProvider_compiledClass_hasNoManualBearerParsing() throws Exception {
        byte[] bytecode;
        try (InputStream in = PasswordResetOutboxRecoveryResourceProvider.class.getResourceAsStream(
                "PasswordResetOutboxRecoveryResourceProvider.class")) {
            assertThat(in).as("a classe compilada precisa estar no classpath").isNotNull();
            bytecode = in.readAllBytes();
        }
        String constantPool = new String(bytecode, StandardCharsets.ISO_8859_1);

        assertThat(constantPool)
                .as("a autenticacao precisa passar pela API suportada do Keycloak")
                .contains("org/keycloak/services/managers/AppAuthManager");
        assertThat(constantPool)
                .as("nenhuma decodificacao manual de JWT pode restar no caminho de autorizacao")
                .doesNotContain("org/keycloak/TokenVerifier")
                .doesNotContain("org/keycloak/models/TokenManager");
    }

    /**
     * Provider com usuario ja autenticado. O seam substitui apenas a autenticacao; a autorizacao
     * por papel do cliente realm-management continua sendo a de producao.
     */
    private PasswordResetOutboxRecoveryResourceProvider authorizedProvider(
            KeycloakSession session, UserModel user, Clock clock) {
        return new PasswordResetOutboxRecoveryResourceProvider(session, store, clock) {
            @Override
            protected UserModel authenticatedUser() {
                return user;
            }
        };
    }

    private RealmModel createRealmWithRoles(String realmName, List<String> realmRoles, List<String> clientRoles) {
        ClientModel realmManagementClient = (ClientModel) Proxy.newProxyInstance(
                ClientModel.class.getClassLoader(),
                new Class<?>[]{ClientModel.class},
                (proxy, method, args) -> {
                    if ("getClientId".equals(method.getName())) {
                        return PasswordResetOutboxRecoveryResourceProvider.REALM_MANAGEMENT_CLIENT;
                    }
                    if ("getRole".equals(method.getName()) && args != null && args.length == 1) {
                        return clientRoles.contains((String) args[0]) ? createMockRole((String) args[0]) : null;
                    }
                    return null;
                });

        return (RealmModel) Proxy.newProxyInstance(
                RealmModel.class.getClassLoader(),
                new Class<?>[]{RealmModel.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName()) || "getId".equals(method.getName())) {
                        return realmName;
                    }
                    if ("getRole".equals(method.getName()) && args != null && args.length == 1) {
                        return realmRoles.contains((String) args[0]) ? createMockRole((String) args[0]) : null;
                    }
                    if ("getClientByClientId".equals(method.getName()) && args != null && args.length == 1) {
                        return PasswordResetOutboxRecoveryResourceProvider.REALM_MANAGEMENT_CLIENT.equals(args[0])
                                ? realmManagementClient : null;
                    }
                    return null;
                });
    }


    @Test
    @DisplayName("Canonicalização literal: buildCanonicalString preserva method em minúsculas e bodySha256 em maiúsculas byte a byte sem normalização")
    void canonicalization_buildCanonicalString_isLiteralAndDoesNotNormalizeCase() {
        String keyId = "v1";
        String timestamp = "1724321730000";
        String lowercaseMethod = "post";
        String path = "/api/v1/internal/events/password-reset";
        String sourceEventId = "evt-canonical-literal-test-99";
        String uppercaseSha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";

        String canonicalString = PasswordResetOutboxDispatcher.buildCanonicalString(
                keyId, timestamp, lowercaseMethod, path, sourceEventId, uppercaseSha256);

        String expectedCanonical = keyId + "\n" +
                timestamp + "\n" +
                lowercaseMethod + "\n" +
                path + "\n" +
                sourceEventId + "\n" +
                uppercaseSha256;

        assertThat(canonicalString)
                .as("buildCanonicalString deve ser estritamente literal e preservar exatamente os bytes fornecidos")
                .isEqualTo(expectedCanonical);

        assertThat(canonicalString).contains("\npost\n");
        assertThat(canonicalString).doesNotContain("\nPOST\n");
        assertThat(canonicalString).endsWith("\n" + uppercaseSha256);
        assertThat(canonicalString).doesNotContain(uppercaseSha256.toLowerCase());
    }

    @Test
    @DisplayName("Liquibase real cria toda coluna mapeada pela entidade, inclusive next_attempt_at")
    void liquibaseChangelog_createsEveryColumnMappedByEntity_includingNextAttemptAt() throws Exception {
        String dbName = "liquibase_columns_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                Liquibase liquibase = new Liquibase(
                        PasswordResetOutboxJpaEntityProvider.CHANGELOG_LOCATION,
                        new ClassLoaderResourceAccessor(getClass().getClassLoader()),
                        database);
                liquibase.update(new Contexts(), new LabelExpression());
                liquibase.update(new Contexts(), new LabelExpression());
            } finally {
                database.close();
            }
        }

        Set<String> schemaColumns = new HashSet<>();
        try (Connection verification = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            DatabaseMetaData metaData = verification.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "BETUFU_PASSWORD_RESET_OUTBOX", "%")) {
                while (rs.next()) {
                    schemaColumns.add(rs.getString("COLUMN_NAME").toUpperCase(java.util.Locale.ROOT));
                }
            }
        }

        Set<String> mappedColumns = new HashSet<>();
        for (Field field : PasswordResetOutboxEntity.class.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            if (column != null && !column.name().isBlank()) {
                mappedColumns.add(column.name().toUpperCase(java.util.Locale.ROOT));
            }
        }

        assertThat(mappedColumns)
                .as("a entidade precisa mapear colunas, senao este guard passaria por ausencia")
                .isNotEmpty()
                .contains("NEXT_ATTEMPT_AT");

        assertThat(schemaColumns)
                .as("toda coluna mapeada pela entidade deve existir no schema criado pelo Liquibase real")
                .containsAll(mappedColumns);
    }
}
