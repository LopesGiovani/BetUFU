package br.com.betufu.keycloak.password;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.keycloak.events.Details;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.KeycloakSession;

public class BetufuPasswordEventListener implements EventListenerProvider {

    public static final String CREDENTIAL_TYPE_PASSWORD = "password";
    public static final String CANONICAL_EVENT_TYPE = "UPDATE_CREDENTIAL";
    public static final String ADMIN_EVENT_TYPE = "ADMIN_RESET_PASSWORD";
    public static final String ADMIN_RESET_PASSWORD = ADMIN_EVENT_TYPE;

    private static final Pattern ADMIN_RESOURCE_PATH_PATTERN =
            Pattern.compile("^users/([^\\s/]+)/reset-password$");

    private final KeycloakSession session;
    private final PasswordResetOutboxStore store;

    public BetufuPasswordEventListener(KeycloakSession session, PasswordResetOutboxStore store) {
        this.session = session;
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public BetufuPasswordEventListener(KeycloakSession session) {
        this(session, new PasswordResetOutboxStore(session));
    }

    @Override
    public void onEvent(Event event) {
        if (event == null) {
            return;
        }

        // Ignorar eventos com erro
        if (event.getError() != null && !event.getError().isBlank()) {
            return;
        }

        // D-40: Apenas UPDATE_CREDENTIAL canônico; UPDATE_PASSWORD legado e outros tipos são ignorados
        if (event.getType() != EventType.UPDATE_CREDENTIAL) {
            return;
        }

        Map<String, String> details = event.getDetails();
        if (details == null) {
            return;
        }

        String credentialType = details.get(Details.CREDENTIAL_TYPE);
        if (!CREDENTIAL_TYPE_PASSWORD.equals(credentialType)) {
            return;
        }

        persistResetEvent(event.getId(), event.getUserId(), event.getRealmId(), CANONICAL_EVENT_TYPE, event.getTime());
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        if (adminEvent == null) {
            return;
        }

        // Ignorar eventos administrativos com erro
        if (adminEvent.getError() != null && !adminEvent.getError().isBlank()) {
            return;
        }

        // D-40: Reset administrativo canônico: ResourceType.USER e OperationType.ACTION
        if (adminEvent.getResourceType() != ResourceType.USER) {
            return;
        }

        if (adminEvent.getOperationType() != OperationType.ACTION) {
            return;
        }

        String resourcePath = adminEvent.getResourcePath();
        if (resourcePath == null || resourcePath.isBlank()) {
            return;
        }

        // D-40: Extração estrita de users/{id}/reset-password sem usar getResourceId()
        Matcher matcher = ADMIN_RESOURCE_PATH_PATTERN.matcher(resourcePath);
        if (!matcher.matches()) {
            return;
        }

        String subject = matcher.group(1);
        if (subject == null || subject.isBlank()) {
            return;
        }

        persistResetEvent(adminEvent.getId(), subject, adminEvent.getRealmId(), ADMIN_EVENT_TYPE, adminEvent.getTime());
    }

    private void persistResetEvent(
            String sourceEventId,
            String subject,
            String realmId,
            String eventType,
            long eventTime) {
        if (sourceEventId == null || sourceEventId.isBlank()
                || subject == null || subject.isBlank()
                || realmId == null || realmId.isBlank()
                || eventType == null || eventType.isBlank()) {
            return;
        }

        Instant createdAt = eventTime > 0
                ? Instant.ofEpochMilli(eventTime)
                : Instant.now();

        // Persiste somente sourceEventId, subject, realm/issuer, timestamp e estado de entrega
        store.persistPending(sourceEventId, subject, realmId, eventType, createdAt);
    }

    @Override
    public void close() {
        // Sem recursos que exijam fechamento explícito nesta etapa
    }

    public KeycloakSession getSession() {
        return session;
    }

    public PasswordResetOutboxStore getStore() {
        return store;
    }
}
