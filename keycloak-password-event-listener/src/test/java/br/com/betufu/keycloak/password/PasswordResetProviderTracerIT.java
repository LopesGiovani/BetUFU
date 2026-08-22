package br.com.betufu.keycloak.password;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
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
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordResetProviderTracerIT {

    private static StandardServiceRegistry serviceRegistry;
    private static SessionFactory sessionFactory;

    private EntityManager entityManager;
    private PasswordResetOutboxStore store;
    private BetufuPasswordEventListener listener;
    private KeycloakSession sessionProxy;

    @BeforeAll
    static void setUpAll() {
        serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting("jakarta.persistence.jdbc.driver", "org.h2.Driver")
                .applySetting("jakarta.persistence.jdbc.url", "jdbc:h2:mem:keycloak_outbox_tracer;DB_CLOSE_DELAY=-1")
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
        listener = new BetufuPasswordEventListener(sessionProxy, store);
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
            } else if (returnType.equals(void.class)) {
                return null;
            }
            return null;
        };

        return (KeycloakSession) Proxy.newProxyInstance(
                KeycloakSession.class.getClassLoader(),
                new Class<?>[]{KeycloakSession.class},
                handler);
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

    @Test
    @DisplayName("UPDATE_CREDENTIAL com Details.CREDENTIAL_TYPE exatamente password persiste linha PENDING única com payload mínimo")
    void updateCredentialPassword_persistsPendingOutboxRow_withMinimalPayload() {
        String eventId = UUID.randomUUID().toString();
        String userId = "usr-betufu-42";
        String realmId = "betufu-local";
        long eventTime = 1724345678000L;

        Event event = new Event();
        event.setId(eventId);
        event.setType(EventType.UPDATE_CREDENTIAL);
        event.setUserId(userId);
        event.setRealmId(realmId);
        event.setTime(eventTime);
        event.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(event);
        tx.commit();

        Optional<PasswordResetOutboxEntity> found = store.findBySourceEventId(eventId);
        assertThat(found).isPresent();

        PasswordResetOutboxEntity entity = found.get();
        assertThat(entity.getSourceEventId()).isEqualTo(eventId);
        assertThat(entity.getSubject()).isEqualTo(userId);
        assertThat(entity.getRealmId()).isEqualTo(realmId);
        assertThat(entity.getEventType()).isEqualTo("UPDATE_CREDENTIAL");
        assertThat(entity.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_PENDING);
        assertThat(entity.getCreatedAt()).isEqualTo(Instant.ofEpochMilli(eventTime));
        assertThat(entity.getAttemptCount()).isZero();
        assertThat(entity.getClaimedAt()).isNull();
        assertThat(entity.getDeliveredAt()).isNull();
        assertThat(entity.getLastError()).isNull();
    }

    @Test
    @DisplayName("Rollback da transação do Keycloak não deixa linha na outbox")
    void transactionRollback_leavesNoOutboxRow() {
        String eventId = UUID.randomUUID().toString();

        Event event = new Event();
        event.setId(eventId);
        event.setType(EventType.UPDATE_CREDENTIAL);
        event.setUserId("usr-betufu-rollback");
        event.setRealmId("betufu-local");
        event.setTime(System.currentTimeMillis());
        event.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(event);
        tx.rollback();

        EntityManager verificationEm = sessionFactory.createEntityManager();
        try {
            PasswordResetOutboxStore verificationStore = new PasswordResetOutboxStore(verificationEm);
            Optional<PasswordResetOutboxEntity> found = verificationStore.findBySourceEventId(eventId);
            assertThat(found).isEmpty();
        } finally {
            verificationEm.close();
        }
    }

    @Test
    @DisplayName("Eventos duplicados com mesmo sourceEventId são idempotentes e persistem linha única")
    void duplicateEvent_isIdempotent_persistsSingleRow() {
        String eventId = UUID.randomUUID().toString();

        Event event1 = new Event();
        event1.setId(eventId);
        event1.setType(EventType.UPDATE_CREDENTIAL);
        event1.setUserId("usr-betufu-dup");
        event1.setRealmId("betufu-local");
        event1.setTime(1724345678000L);
        event1.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));

        Event event2 = new Event();
        event2.setId(eventId);
        event2.setType(EventType.UPDATE_CREDENTIAL);
        event2.setUserId("usr-betufu-dup");
        event2.setRealmId("betufu-local");
        event2.setTime(1724345678000L);
        event2.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(event1);
        listener.onEvent(event2);
        tx.commit();

        List<PasswordResetOutboxEntity> pending = store.findPending(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getSourceEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("UPDATE_PASSWORD depreciado/legado não produz linha na outbox (D-40)")
    void legacyUpdatePassword_doesNotProduceOutboxRow() {
        String eventId = UUID.randomUUID().toString();

        Event event = new Event();
        event.setId(eventId);
        event.setType(EventType.UPDATE_PASSWORD);
        event.setUserId("usr-betufu-legacy");
        event.setRealmId("betufu-local");
        event.setTime(System.currentTimeMillis());
        event.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(event);
        tx.commit();

        Optional<PasswordResetOutboxEntity> found = store.findBySourceEventId(eventId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Tipos de credencial diferentes de password (totp, otp, webauthn) não produzem linha")
    void otherCredentialTypes_doNotProduceOutboxRow() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        for (String credType : List.of("totp", "otp", "webauthn", "recovery-codes")) {
            String eventId = UUID.randomUUID().toString();
            Event event = new Event();
            event.setId(eventId);
            event.setType(EventType.UPDATE_CREDENTIAL);
            event.setUserId("usr-betufu-" + credType);
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());
            event.setDetails(Map.of(Details.CREDENTIAL_TYPE, credType));

            listener.onEvent(event);
            assertThat(store.findBySourceEventId(eventId)).isEmpty();
        }

        tx.commit();
    }

    @Test
    @DisplayName("Eventos com erro não produzem linha na outbox")
    void errorEvents_doNotProduceOutboxRow() {
        String eventId = UUID.randomUUID().toString();

        Event event = new Event();
        event.setId(eventId);
        event.setType(EventType.UPDATE_CREDENTIAL);
        event.setUserId("usr-betufu-error");
        event.setRealmId("betufu-local");
        event.setTime(System.currentTimeMillis());
        event.setError("invalid_credential");
        event.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(event);
        tx.commit();

        Optional<PasswordResetOutboxEntity> found = store.findBySourceEventId(eventId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Eventos nulos, com campos incompletos ou sem details não produzem linha")
    void nullOrIncompleteEvent_doesNotProduceOutboxRow() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        listener.onEvent(null);

        Event missingDetails = new Event();
        missingDetails.setId(UUID.randomUUID().toString());
        missingDetails.setType(EventType.UPDATE_CREDENTIAL);
        missingDetails.setUserId("usr-1");
        missingDetails.setRealmId("betufu-local");
        listener.onEvent(missingDetails);

        Event missingUserId = new Event();
        missingUserId.setId(UUID.randomUUID().toString());
        missingUserId.setType(EventType.UPDATE_CREDENTIAL);
        missingUserId.setRealmId("betufu-local");
        missingUserId.setDetails(Map.of(Details.CREDENTIAL_TYPE, "password"));
        listener.onEvent(missingUserId);

        tx.commit();

        List<PasswordResetOutboxEntity> pending = store.findPending(10);
        assertThat(pending).isEmpty();
    }

    @Test
    @DisplayName("ServiceLoader descobre BetufuPasswordEventListenerFactory com o ID correto")
    void serviceLoader_discoversEventListenerProviderFactory() {
        ServiceLoader<EventListenerProviderFactory> loader =
                ServiceLoader.load(EventListenerProviderFactory.class);

        boolean found = false;
        for (EventListenerProviderFactory factory : loader) {
            if (factory instanceof BetufuPasswordEventListenerFactory) {
                found = true;
                assertThat(factory.getId()).isEqualTo("betufu-password-event-listener");
            }
        }
        assertThat(found)
                .as("BetufuPasswordEventListenerFactory deve ser descoberto via ServiceLoader")
                .isTrue();
    }

    @Test
    @DisplayName("Fábrica instancia BetufuPasswordEventListener com ID betufu-password-event-listener")
    void factoryCreatesListenerInstanceWithCorrectId() {
        BetufuPasswordEventListenerFactory factory = new BetufuPasswordEventListenerFactory();
        assertThat(factory.getId()).isEqualTo("betufu-password-event-listener");
        assertThat(factory.getId()).isEqualTo(BetufuPasswordEventListenerFactory.ID);
    }

    @Test
    @DisplayName("ServiceLoader descobre PasswordResetOutboxJpaEntityProviderFactory com o ID correto")
    void serviceLoader_discoversJpaEntityProviderFactory() {
        ServiceLoader<JpaEntityProviderFactory> loader =
                ServiceLoader.load(JpaEntityProviderFactory.class);

        boolean found = false;
        for (JpaEntityProviderFactory factory : loader) {
            if (factory instanceof PasswordResetOutboxJpaEntityProviderFactory) {
                found = true;
                assertThat(factory.getId()).isEqualTo(PasswordResetOutboxJpaEntityProviderFactory.ID);
            }
        }
        assertThat(found)
                .as("PasswordResetOutboxJpaEntityProviderFactory deve ser descoberto via ServiceLoader")
                .isTrue();
    }

    @Test
    @DisplayName("JpaEntityProvider fornece a entidade PasswordResetOutboxEntity e o changelog location correto")
    void jpaEntityProvider_providesEntitiesAndChangelogLocation() {
        PasswordResetOutboxJpaEntityProviderFactory factory = new PasswordResetOutboxJpaEntityProviderFactory();
        assertThat(factory.getId()).isEqualTo(PasswordResetOutboxJpaEntityProviderFactory.ID);

        JpaEntityProvider provider = factory.create(sessionProxy);
        assertThat(provider).isNotNull();
        assertThat(provider.getEntities()).containsExactly(PasswordResetOutboxEntity.class);
        assertThat(provider.getChangelogLocation()).isEqualTo(PasswordResetOutboxJpaEntityProvider.CHANGELOG_LOCATION);
        assertThat(provider.getFactoryId()).isEqualTo(factory.getId());

        provider.close();
    }

    @Test
    @DisplayName("Changelog Liquibase existe no classpath e define tabela e unicidade por source_event_id")
    void changelogXml_existsAndDefinesTableAndUniqueConstraint() throws Exception {
        String changelogPath = PasswordResetOutboxJpaEntityProvider.CHANGELOG_LOCATION;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(changelogPath)) {
            assertThat(is)
                    .as("Changelog Liquibase deve estar presente no classpath em: " + changelogPath)
                    .isNotNull();

            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains("tableName=\"betufu_password_reset_outbox\"");
            assertThat(content).contains("name=\"source_event_id\"");
            assertThat(content).contains("uniqueConstraintName=\"uq_betufu_password_reset_outbox_source_event\"");
            assertThat(content).contains("name=\"status\"");
            assertThat(content).contains("name=\"attempt_count\"");
        }
    }

    @Test
    @DisplayName("Liquibase aplica changelog da outbox em H2 isolado, executa com idempotência e cria tabela e unicidade em source_event_id")
    void liquibaseAppliesOutboxChangelog_idempotentlyOnSeparateDatabase_andEnforcesSchema() throws Exception {
        String dbName = "liquibase_outbox_it_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try {
                Liquibase liquibase = new Liquibase(
                        PasswordResetOutboxJpaEntityProvider.CHANGELOG_LOCATION,
                        new ClassLoaderResourceAccessor(getClass().getClassLoader()),
                        database);

                // Primeira execução: aplica changelog e cria tabela com restrição única
                liquibase.update(new Contexts(), new LabelExpression());

                // Segunda execução: verifica idempotência da migração
                liquibase.update(new Contexts(), new LabelExpression());
            } finally {
                database.close();
            }
        }

        try (Connection verificationConn = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            try {
                DatabaseMetaData metaData = verificationConn.getMetaData();

                // Prova 1: exatamente 1 tabela BETUFU_PASSWORD_RESET_OUTBOX criada pela migração
                List<String> matchingTables = new ArrayList<>();
                try (ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        String tableName = rs.getString("TABLE_NAME");
                        if ("BETUFU_PASSWORD_RESET_OUTBOX".equalsIgnoreCase(tableName)) {
                            matchingTables.add(tableName);
                        }
                    }
                }
                assertThat(matchingTables)
                        .as("Deve existir exatamente uma tabela BETUFU_PASSWORD_RESET_OUTBOX no schema criado pelo Liquibase")
                        .hasSize(1);

                // Prova 2: restrição/índice de unicidade cobrindo SOURCE_EVENT_ID
                List<String> uniqueColumns = new ArrayList<>();
                try (ResultSet rs = metaData.getIndexInfo(null, null, matchingTables.get(0), true, false)) {
                    while (rs.next()) {
                        boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                        String columnName = rs.getString("COLUMN_NAME");
                        if (!nonUnique && "SOURCE_EVENT_ID".equalsIgnoreCase(columnName)) {
                            uniqueColumns.add(columnName);
                        }
                    }
                }
                assertThat(uniqueColumns)
                        .as("Deve existir índice/restrição de unicidade para SOURCE_EVENT_ID")
                        .isNotEmpty();

                // Prova 3: consulta direta à INFORMATION_SCHEMA confirmando a tabela
                try (Statement stmt = verificationConn.createStatement()) {
                    try (ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'BETUFU_PASSWORD_RESET_OUTBOX'")) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getInt(1)).isEqualTo(1);
                    }
                }
            } finally {
                try (Statement stmt = verificationConn.createStatement()) {
                    stmt.execute("SHUTDOWN");
                }
            }
        }
    }

    @Test
    @DisplayName("Schema do banco de dados aplica restrição de unicidade em source_event_id")
    void databaseSchema_enforcesUniqueConstraintOnSourceEventId() {
        String sharedEventId = "evt-unique-constraint-test-" + UUID.randomUUID();

        PasswordResetOutboxEntity entity1 = new PasswordResetOutboxEntity(
                sharedEventId,
                "usr-1",
                "betufu-local",
                "UPDATE_CREDENTIAL",
                Instant.now());

        PasswordResetOutboxEntity entity2 = new PasswordResetOutboxEntity(
                sharedEventId,
                "usr-2",
                "betufu-local",
                "UPDATE_CREDENTIAL",
                Instant.now());

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        entityManager.persist(entity1);
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(entity2);
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);

        tx.rollback();
    }

    @Test
    @DisplayName("Reexecução e chamadas repetidas de persistência são idempotentes e não duplicam registros")
    void reexecutionAndRepeatedPersistence_isIdempotent_andDoesNotDuplicateRows() {
        String eventId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-08-22T10:00:00Z");

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        PasswordResetOutboxEntity first = store.persistPending(
                eventId, "usr-replay", "betufu-local", "UPDATE_CREDENTIAL", createdAt);
        PasswordResetOutboxEntity second = store.persistPending(
                eventId, "usr-replay", "betufu-local", "UPDATE_CREDENTIAL", createdAt);
        tx.commit();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getId()).isEqualTo(second.getId());

        List<PasswordResetOutboxEntity> pending = store.findPending(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getSourceEventId()).isEqualTo(eventId);
    }

    @Test
    @DisplayName("PasswordResetOutboxEntity preserva campos de estado e transições para o futuro worker")
    void outboxEntity_preservesWorkerStateFieldsAndLifecycleTransitions() {
        String eventId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        PasswordResetOutboxEntity entity = new PasswordResetOutboxEntity(
                eventId,
                "usr-worker-test",
                "betufu-local",
                "UPDATE_CREDENTIAL",
                now);

        assertThat(entity.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_PENDING);
        assertThat(entity.getAttemptCount()).isZero();
        assertThat(entity.getClaimedAt()).isNull();
        assertThat(entity.getClaimInstanceId()).isNull();
        assertThat(entity.getDeliveredAt()).isNull();
        assertThat(entity.getLastError()).isNull();

        // Simula claim por worker
        Instant claimedTime = now.plusSeconds(5);
        entity.setStatus(PasswordResetOutboxEntity.STATUS_CLAIMED);
        entity.setClaimedAt(claimedTime);
        entity.setClaimInstanceId("worker-instance-1");
        entity.incrementAttemptCount();

        assertThat(entity.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_CLAIMED);
        assertThat(entity.getClaimedAt()).isEqualTo(claimedTime);
        assertThat(entity.getClaimInstanceId()).isEqualTo("worker-instance-1");
        assertThat(entity.getAttemptCount()).isEqualTo(1);

        // Simula falha e retry
        entity.setStatus(PasswordResetOutboxEntity.STATUS_FAILED);
        entity.setLastError("Connection timed out to delivery target");
        entity.incrementAttemptCount();

        assertThat(entity.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_FAILED);
        assertThat(entity.getLastError()).isEqualTo("Connection timed out to delivery target");
        assertThat(entity.getAttemptCount()).isEqualTo(2);

        // Simula entrega com sucesso
        Instant deliveredTime = now.plusSeconds(30);
        entity.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        entity.setDeliveredAt(deliveredTime);

        assertThat(entity.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_DELIVERED);
        assertThat(entity.getDeliveredAt()).isEqualTo(deliveredTime);
    }

    @Test
    @DisplayName("findPending ordena registros por createdAt ASC e respeita maxResults")
    void findPending_ordersByCreatedAtAsc_andRespectsMaxResults() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        Instant t1 = Instant.parse("2026-08-22T10:00:00Z");
        Instant t2 = Instant.parse("2026-08-22T10:05:00Z");
        Instant t3 = Instant.parse("2026-08-22T10:10:00Z");

        store.persistPending("evt-order-2", "usr-2", "betufu-local", "UPDATE_CREDENTIAL", t2);
        store.persistPending("evt-order-1", "usr-1", "betufu-local", "UPDATE_CREDENTIAL", t1);
        store.persistPending("evt-order-3", "usr-3", "betufu-local", "UPDATE_CREDENTIAL", t3);

        tx.commit();

        List<PasswordResetOutboxEntity> pendingAll = store.findPending(0);
        assertThat(pendingAll).hasSize(3);
        assertThat(pendingAll.get(0).getSourceEventId()).isEqualTo("evt-order-1");
        assertThat(pendingAll.get(1).getSourceEventId()).isEqualTo("evt-order-2");
        assertThat(pendingAll.get(2).getSourceEventId()).isEqualTo("evt-order-3");

        List<PasswordResetOutboxEntity> pendingLimit2 = store.findPending(2);
        assertThat(pendingLimit2).hasSize(2);
        assertThat(pendingLimit2.get(0).getSourceEventId()).isEqualTo("evt-order-1");
        assertThat(pendingLimit2.get(1).getSourceEventId()).isEqualTo("evt-order-2");
    }

    @Test
    @DisplayName("AdminEvent USER/ACTION com resourcePath users/{id}/reset-password persiste linha PENDING única com payload mínimo")
    void adminResetPassword_canonicalEvent_persistsPendingOutboxRow_withMinimalPayloadAndCorrectTimestamp() {
        String eventId = "admin-evt-" + UUID.randomUUID();
        String targetUserId = "usr-admin-target-42";
        String realmId = "betufu-local";
        long eventTime = 1724348888000L;

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setId(eventId);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setOperationType(OperationType.ACTION);
        adminEvent.setResourcePath("users/" + targetUserId + "/reset-password");
        adminEvent.setRealmId(realmId);
        adminEvent.setTime(eventTime);

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(adminEvent, false);
        tx.commit();

        Optional<PasswordResetOutboxEntity> found = store.findBySourceEventId(eventId);
        assertThat(found).isPresent();

        PasswordResetOutboxEntity entity = found.get();
        assertThat(entity.getSourceEventId()).isEqualTo(eventId);
        assertThat(entity.getSubject()).isEqualTo(targetUserId);
        assertThat(entity.getRealmId()).isEqualTo(realmId);
        assertThat(entity.getEventType()).isEqualTo(BetufuPasswordEventListener.ADMIN_EVENT_TYPE);
        assertThat(entity.getStatus()).isEqualTo(PasswordResetOutboxEntity.STATUS_PENDING);
        assertThat(entity.getCreatedAt()).isEqualTo(Instant.ofEpochMilli(eventTime));
        assertThat(entity.getAttemptCount()).isZero();
        assertThat(entity.getClaimedAt()).isNull();
        assertThat(entity.getDeliveredAt()).isNull();
        assertThat(entity.getLastError()).isNull();
    }

    @Test
    @DisplayName("AdminEvent com resourceType divergente ou nulo não produz linha na outbox")
    void adminEvent_divergentResourceType_doesNotProduceOutboxRow() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        List<ResourceType> nonUserTypes = new ArrayList<>(List.of(
                ResourceType.REALM,
                ResourceType.CLIENT,
                ResourceType.CLIENT_SCOPE,
                ResourceType.GROUP,
                ResourceType.REALM_ROLE,
                ResourceType.USER_SESSION,
                ResourceType.USER_LOGIN_FAILURE,
                ResourceType.AUTHORIZATION_RESOURCE_SERVER
        ));

        for (ResourceType type : nonUserTypes) {
            String eventId = "admin-evt-type-" + UUID.randomUUID();
            AdminEvent event = new AdminEvent();
            event.setId(eventId);
            event.setResourceType(type);
            event.setOperationType(OperationType.ACTION);
            event.setResourcePath("users/usr-type-test/reset-password");
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());

            listener.onEvent(event, false);
            assertThat(store.findBySourceEventId(eventId)).isEmpty();
        }

        // Teste com resourceType nulo
        String nullTypeId = "admin-evt-type-null-" + UUID.randomUUID();
        AdminEvent nullTypeEvent = new AdminEvent();
        nullTypeEvent.setId(nullTypeId);
        nullTypeEvent.setResourceType(null);
        nullTypeEvent.setOperationType(OperationType.ACTION);
        nullTypeEvent.setResourcePath("users/usr-type-test/reset-password");
        nullTypeEvent.setRealmId("betufu-local");
        nullTypeEvent.setTime(System.currentTimeMillis());

        listener.onEvent(nullTypeEvent, false);
        assertThat(store.findBySourceEventId(nullTypeId)).isEmpty();

        tx.commit();
    }

    @Test
    @DisplayName("AdminEvent com operationType divergente ou nulo não produz linha na outbox")
    void adminEvent_divergentOperationType_doesNotProduceOutboxRow() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        List<OperationType> nonActionOperations = List.of(
                OperationType.CREATE,
                OperationType.UPDATE,
                OperationType.DELETE
        );

        for (OperationType op : nonActionOperations) {
            String eventId = "admin-evt-op-" + UUID.randomUUID();
            AdminEvent event = new AdminEvent();
            event.setId(eventId);
            event.setResourceType(ResourceType.USER);
            event.setOperationType(op);
            event.setResourcePath("users/usr-op-test/reset-password");
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());

            listener.onEvent(event, false);
            assertThat(store.findBySourceEventId(eventId)).isEmpty();
        }

        // Teste com operationType nulo
        String nullOpId = "admin-evt-op-null-" + UUID.randomUUID();
        AdminEvent nullOpEvent = new AdminEvent();
        nullOpEvent.setId(nullOpId);
        nullOpEvent.setResourceType(ResourceType.USER);
        nullOpEvent.setOperationType(null);
        nullOpEvent.setResourcePath("users/usr-op-test/reset-password");
        nullOpEvent.setRealmId("betufu-local");
        nullOpEvent.setTime(System.currentTimeMillis());

        listener.onEvent(nullOpEvent, false);
        assertThat(store.findBySourceEventId(nullOpId)).isEmpty();

        tx.commit();
    }

    @Test
    @DisplayName("AdminEvent com variantes de path vazias, com espaços, ambíguas, extras ou com barras adicionais são rejeitadas")
    void adminEvent_malformedAndDivergentResourcePaths_doNotProduceOutboxRow() {
        List<String> invalidPaths = List.of(
                // Segmento ID vazio ou somente espaços
                "users//reset-password",
                "users/ /reset-password",
                "users/\t/reset-password",
                "users/\n/reset-password",
                "users/   /reset-password",
                "users/usr 123/reset-password",
                "users/usr\t123/reset-password",
                // Barras e segmentos extras / trailing / leading
                "users/usr-1/reset-password/",
                "users/usr-1/reset-password/extra",
                "users/usr-1/reset-password/reset-password",
                "/users/usr-1/reset-password",
                "prefix/users/usr-1/reset-password",
                "users/usr-1/other-action/reset-password",
                "users/usr-1/sub/reset-password",
                // Paths de outros recursos ou ações
                "users/usr-1",
                "users/usr-1/credentials",
                "users/usr-1/other-action",
                "users/usr-1/reset-password-other",
                "groups/grp-1/reset-password",
                "realms/master/reset-password",
                "clients/client-1/reset-password",
                // Espaços em volta do path
                " users/usr-1/reset-password",
                "users/usr-1/reset-password ",
                " users/usr-1/reset-password "
        );

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        for (String path : invalidPaths) {
            String eventId = "admin-evt-path-" + UUID.randomUUID();
            AdminEvent event = new AdminEvent();
            event.setId(eventId);
            event.setResourceType(ResourceType.USER);
            event.setOperationType(OperationType.ACTION);
            event.setResourcePath(path);
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());

            listener.onEvent(event, false);
            assertThat(store.findBySourceEventId(eventId))
                    .as("Path inválido não deve persistir: %s", path)
                    .isEmpty();
        }

        // Paths nulos ou em branco
        for (String blankPath : new String[]{null, "", "   ", "\t"}) {
            String eventId = "admin-evt-blank-" + UUID.randomUUID();
            AdminEvent event = new AdminEvent();
            event.setId(eventId);
            event.setResourceType(ResourceType.USER);
            event.setOperationType(OperationType.ACTION);
            event.setResourcePath(blankPath);
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());

            listener.onEvent(event, false);
            assertThat(store.findBySourceEventId(eventId)).isEmpty();
        }

        tx.commit();
    }

    @Test
    @DisplayName("AdminEvent com erro não produz linha na outbox; error nulo ou em branco persiste")
    void adminEvent_errorHandling_persistsOnlyWhenErrorNullOrBlank() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        // Eventos com erro real não persistem
        for (String error : List.of("UNAUTHORIZED", "invalid_request", "conflict", "forbidden")) {
            String eventId = "admin-evt-err-" + UUID.randomUUID();
            AdminEvent event = new AdminEvent();
            event.setId(eventId);
            event.setResourceType(ResourceType.USER);
            event.setOperationType(OperationType.ACTION);
            event.setResourcePath("users/usr-err/reset-password");
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());
            event.setError(error);

            listener.onEvent(event, false);
            assertThat(store.findBySourceEventId(eventId)).isEmpty();
        }

        // Evento com error nulo persiste
        String validNullErrId = "admin-evt-null-err-" + UUID.randomUUID();
        AdminEvent nullErrEvent = new AdminEvent();
        nullErrEvent.setId(validNullErrId);
        nullErrEvent.setResourceType(ResourceType.USER);
        nullErrEvent.setOperationType(OperationType.ACTION);
        nullErrEvent.setResourcePath("users/usr-null-err/reset-password");
        nullErrEvent.setRealmId("betufu-local");
        nullErrEvent.setTime(System.currentTimeMillis());
        nullErrEvent.setError(null);
        listener.onEvent(nullErrEvent, false);
        assertThat(store.findBySourceEventId(validNullErrId)).isPresent();

        // Evento com error em branco persiste
        String validBlankErrId = "admin-evt-blank-err-" + UUID.randomUUID();
        AdminEvent blankErrEvent = new AdminEvent();
        blankErrEvent.setId(validBlankErrId);
        blankErrEvent.setResourceType(ResourceType.USER);
        blankErrEvent.setOperationType(OperationType.ACTION);
        blankErrEvent.setResourcePath("users/usr-blank-err/reset-password");
        blankErrEvent.setRealmId("betufu-local");
        blankErrEvent.setTime(System.currentTimeMillis());
        blankErrEvent.setError("   ");
        listener.onEvent(blankErrEvent, false);
        assertThat(store.findBySourceEventId(validBlankErrId)).isPresent();

        tx.commit();
    }

    @Test
    @DisplayName("AdminEvent nulo ou com ID/realmId nulos ou em branco não produz linha na outbox")
    void adminEvent_nullOrMissingIdOrRealm_doesNotProduceOutboxRow() {
        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();

        // adminEvent nulo
        listener.onEvent((AdminEvent) null, false);

        // ID nulo ou em branco
        for (String id : new String[]{null, "", "   ", "\t"}) {
            AdminEvent event = new AdminEvent();
            event.setId(id);
            event.setResourceType(ResourceType.USER);
            event.setOperationType(OperationType.ACTION);
            event.setResourcePath("users/usr-missing-id/reset-password");
            event.setRealmId("betufu-local");
            event.setTime(System.currentTimeMillis());

            listener.onEvent(event, false);
            if (id != null && !id.isBlank()) {
                assertThat(store.findBySourceEventId(id)).isEmpty();
            }
        }

        // RealmId nulo ou em branco
        for (String realm : new String[]{null, "", "   ", "\t"}) {
            String eventId = "admin-evt-missing-realm-" + UUID.randomUUID();
            AdminEvent event = new AdminEvent();
            event.setId(eventId);
            event.setResourceType(ResourceType.USER);
            event.setOperationType(OperationType.ACTION);
            event.setResourcePath("users/usr-missing-realm/reset-password");
            event.setRealmId(realm);
            event.setTime(System.currentTimeMillis());

            listener.onEvent(event, false);
            assertThat(store.findBySourceEventId(eventId)).isEmpty();
        }

        tx.commit();

        List<PasswordResetOutboxEntity> pending = store.findPending(10);
        assertThat(pending).isEmpty();
    }

    @Test
    @DisplayName("AdminEvent extrai subject estritamente do resourcePath ignorando getResourceId; getResourceId isolado não persiste")
    void adminEvent_poisonResourceId_validPathSubjectMustWin_andPoisonResourceIdAloneCannotPersist() {
        String poisonValue = "poison-target-danger-do-not-use-as-user-id";
        String validSubject = "usr-canonical-admin-target-99";
        String eventId = "admin-evt-poison-" + UUID.randomUUID();

        // Subclasse com getResourceId envenenado
        class PoisonResourceIdAdminEvent extends AdminEvent {
            @Override
            public String getResourceId() {
                return poisonValue;
            }
        }

        PoisonResourceIdAdminEvent poisonEvent = new PoisonResourceIdAdminEvent();
        poisonEvent.setId(eventId);
        poisonEvent.setResourceType(ResourceType.USER);
        poisonEvent.setOperationType(OperationType.ACTION);
        poisonEvent.setResourcePath("users/" + validSubject + "/reset-password");
        poisonEvent.setRealmId("betufu-local");
        poisonEvent.setTime(1724347777000L);

        // Prova: getResourceId de fato devolve o valor envenenado
        assertThat(poisonEvent.getResourceId()).isEqualTo(poisonValue);

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(poisonEvent, false);
        tx.commit();

        Optional<PasswordResetOutboxEntity> found = store.findBySourceEventId(eventId);
        assertThat(found).isPresent();

        PasswordResetOutboxEntity entity = found.get();
        // O subject gravado DEVE ser o extraído do resourcePath, NUNCA o getResourceId()
        assertThat(entity.getSubject()).isEqualTo(validSubject);
        assertThat(entity.getSubject()).isNotEqualTo(poisonValue);
        assertThat(entity.getSubject()).isNotEqualTo(poisonEvent.getResourceId());

        // Prova 2: getResourceId isolado (sem path válido) não pode persistir
        EntityTransaction tx2 = entityManager.getTransaction();
        tx2.begin();

        for (String invalidPath : new String[]{null, "", "users//reset-password", "users/usr-1", "invalid/path"}) {
            String poisonOnlyId = "admin-evt-poison-only-" + UUID.randomUUID();
            PoisonResourceIdAdminEvent poisonOnlyEvent = new PoisonResourceIdAdminEvent();
            poisonOnlyEvent.setId(poisonOnlyId);
            poisonOnlyEvent.setResourceType(ResourceType.USER);
            poisonOnlyEvent.setOperationType(OperationType.ACTION);
            poisonOnlyEvent.setResourcePath(invalidPath);
            poisonOnlyEvent.setRealmId("betufu-local");
            poisonOnlyEvent.setTime(System.currentTimeMillis());

            listener.onEvent(poisonOnlyEvent, false);
            assertThat(store.findBySourceEventId(poisonOnlyId))
                    .as("getResourceId envenenado isolado não deve persistir com path inválido: %s", invalidPath)
                    .isEmpty();
        }

        tx2.commit();
    }

    @Test
    @DisplayName("Rollback da transação do Keycloak para AdminEvent não deixa linha na outbox")
    void adminResetPassword_transactionRollback_leavesNoOutboxRow() {
        String eventId = "admin-evt-rollback-" + UUID.randomUUID();

        AdminEvent adminEvent = new AdminEvent();
        adminEvent.setId(eventId);
        adminEvent.setResourceType(ResourceType.USER);
        adminEvent.setOperationType(OperationType.ACTION);
        adminEvent.setResourcePath("users/usr-admin-rollback/reset-password");
        adminEvent.setRealmId("betufu-local");
        adminEvent.setTime(System.currentTimeMillis());

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(adminEvent, false);
        tx.rollback();

        EntityManager verificationEm = sessionFactory.createEntityManager();
        try {
            PasswordResetOutboxStore verificationStore = new PasswordResetOutboxStore(verificationEm);
            Optional<PasswordResetOutboxEntity> found = verificationStore.findBySourceEventId(eventId);
            assertThat(found).isEmpty();
        } finally {
            verificationEm.close();
        }
    }

    @Test
    @DisplayName("AdminEvents duplicados com mesmo sourceEventId são idempotentes e persistem linha única")
    void adminResetPassword_duplicateEvent_isIdempotent_persistsSingleRow() {
        String eventId = "admin-evt-dup-" + UUID.randomUUID();

        AdminEvent event1 = new AdminEvent();
        event1.setId(eventId);
        event1.setResourceType(ResourceType.USER);
        event1.setOperationType(OperationType.ACTION);
        event1.setResourcePath("users/usr-admin-dup/reset-password");
        event1.setRealmId("betufu-local");
        event1.setTime(1724345678000L);

        AdminEvent event2 = new AdminEvent();
        event2.setId(eventId);
        event2.setResourceType(ResourceType.USER);
        event2.setOperationType(OperationType.ACTION);
        event2.setResourcePath("users/usr-admin-dup/reset-password");
        event2.setRealmId("betufu-local");
        event2.setTime(1724345678000L);

        EntityTransaction tx = entityManager.getTransaction();
        tx.begin();
        listener.onEvent(event1, false);
        listener.onEvent(event2, true);
        tx.commit();

        List<PasswordResetOutboxEntity> pending = store.findPending(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getSourceEventId()).isEqualTo(eventId);
        assertThat(pending.get(0).getEventType()).isEqualTo(BetufuPasswordEventListener.ADMIN_EVENT_TYPE);
    }
}
