package br.com.betufu.keycloak.password;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.Config;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class PasswordResetOutboxRecoveryResourceProvider
        implements RealmResourceProvider, RealmResourceProviderFactory {

    public static final String ID = "password-reset-recovery";
    public static final String MANAGE_REALM_ROLE = "manage-realm";
    public static final String REALM_MANAGEMENT_CLIENT = "realm-management";
    public static final Duration DEFAULT_CLEANUP_RETENTION = Duration.ofDays(30);

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) {
            super(message);
        }
    }

    private KeycloakSession session;
    private PasswordResetOutboxStore store;
    private Clock clock;

    public PasswordResetOutboxRecoveryResourceProvider() {
        this.clock = Clock.systemUTC();
    }

    public PasswordResetOutboxRecoveryResourceProvider(KeycloakSession session) {
        this.session = session;
        this.store = session != null ? new PasswordResetOutboxStore(session) : null;
        this.clock = Clock.systemUTC();
    }

    public PasswordResetOutboxRecoveryResourceProvider(
            KeycloakSession session, PasswordResetOutboxStore store, Clock clock) {
        this.session = session;
        this.store = store;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public Object getResource() {
        return this;
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new PasswordResetOutboxRecoveryResourceProvider(session);
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }

    /**
     * Autorização por API suportada do Keycloak, sem leitura manual de header.
     *
     * <p>{@code BearerTokenAuthenticator} é quem verifica assinatura, expiração, issuer e audience
     * contra as chaves do realm. A versão anterior interpretava o Bearer por conta própria e caía
     * num {@code TokenVerifier} sem contexto de verificação; foi removida por inteiro, junto com
     * {@code session.tokens().decode()} e com a sobrecarga que autorizava por {@code AccessToken}.</p>
     *
     * <p>A autoridade exigida é o papel do cliente {@code realm-management}. Um papel de realm
     * homônimo não serve: qualquer realm pode declarar um papel chamado {@code manage-realm}, e
     * aceitá-lo transformaria um nome escolhido livremente em autoridade administrativa.</p>
     */
    public void checkManageRealmPermission() {
        if (session == null) {
            throw new UnauthorizedException("Authentication is required");
        }

        UserModel user;
        try {
            user = authenticatedUser();
        } catch (RuntimeException failedAuthentication) {
            // A mensagem da exceção pode carregar detalhe interno e não atravessa a fronteira.
            throw new UnauthorizedException("Authentication is required");
        }

        if (user == null) {
            throw new UnauthorizedException("Authentication is required");
        }

        RealmModel realm = session.getContext() != null ? session.getContext().getRealm() : null;
        if (!hasManageRealmRole(user, realm)) {
            throw new ForbiddenException("Authenticated user does not have manage-realm role");
        }
    }

    /**
     * Único ponto de autenticação do recurso. É {@code protected} apenas para que o teste possa
     * injetar um usuário já autenticado sem subir um Keycloak inteiro.
     *
     * <p>Em produção não existe outro caminho: a autenticação é sempre o
     * {@code BearerTokenAuthenticator}, que verifica assinatura, expiração, issuer e audience com
     * as chaves do realm. Essa verificação criptográfica não é comprovada nesta onda — ela é gate
     * bloqueante do plano 02-29.</p>
     */
    protected UserModel authenticatedUser() {
        AuthenticationManager.AuthResult auth =
                new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
        return auth != null ? auth.getUser() : null;
    }

    /**
     * Somente o papel {@code manage-realm} do cliente {@code realm-management} concede autoridade.
     */
    public boolean hasManageRealmRole(UserModel user, RealmModel realm) {
        if (user == null || realm == null) {
            return false;
        }
        ClientModel client = realm.getClientByClientId(REALM_MANAGEMENT_CLIENT);
        if (client == null) {
            return false;
        }
        RoleModel clientRole = client.getRole(MANAGE_REALM_ROLE);
        return clientRole != null && user.hasRole(clientRole);
    }

    public RecoveryResult executeListFailed() {
        checkManageRealmPermission();
        ensureStore();
        List<PasswordResetOutboxEntity> failedEntities = store.findFailed(100);
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < failedEntities.size(); i++) {
            if (i > 0) sb.append(",");
            PasswordResetOutboxEntity entity = failedEntities.get(i);
            sb.append(buildEntitySummaryJson(entity));
        }
        sb.append("]");
        return RecoveryResult.ok(sb.toString());
    }

    @GET
    @Path("failed")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFailed() {
        try {
            RecoveryResult result = executeListFailed();
            return Response.status(result.status()).entity(result.entity()).type(MediaType.APPLICATION_JSON).build();
        } catch (UnauthorizedException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    public RecoveryResult executeReplay(String sourceEventId) {
        checkManageRealmPermission();
        if (sourceEventId == null || sourceEventId.isBlank()) {
            return RecoveryResult.badRequest("{\"error\":\"sourceEventId is required\"}");
        }

        ensureStore();
        Instant now = clock.instant();
        Optional<PasswordResetOutboxEntity> optional = store.findBySourceEventId(sourceEventId);
        if (optional.isEmpty()) {
            return RecoveryResult.notFound("{\"error\":\"Outbox record not found for sourceEventId: " + escapeJson(sourceEventId) + "\"}");
        }

        PasswordResetOutboxEntity entity = optional.get();
        if (!PasswordResetOutboxEntity.STATUS_FAILED.equals(entity.getStatus())) {
            return RecoveryResult.conflict("{\"error\":\"Outbox record is not in FAILED status (current: " + escapeJson(entity.getStatus()) + ")\"}");
        }

        boolean replayed = store.replayFailed(sourceEventId, now);
        if (replayed) {
            return RecoveryResult.ok("{\"status\":\"REPLAYED\",\"sourceEventId\":\"" + escapeJson(sourceEventId) + "\",\"newStatus\":\"PENDING\"}");
        } else {
            return RecoveryResult.serverError("{\"error\":\"Failed to replay outbox event\"}");
        }
    }

    @POST
    @Path("replay/{sourceEventId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response replay(@PathParam("sourceEventId") String sourceEventId) {
        try {
            RecoveryResult result = executeReplay(sourceEventId);
            return Response.status(result.status()).entity(result.entity()).type(MediaType.APPLICATION_JSON).build();
        } catch (UnauthorizedException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    public RecoveryResult executeCleanupDelivered() {
        checkManageRealmPermission();
        ensureStore();
        Instant now = clock.instant();
        Instant cutoff = now.minus(DEFAULT_CLEANUP_RETENTION);
        int deletedCount = store.deleteDeliveredOlderThan(cutoff);
        return RecoveryResult.ok("{\"deletedCount\":" + deletedCount + ",\"cutoff\":\"" + cutoff.toString() + "\",\"retentionDays\":30}");
    }

    @POST
    @Path("cleanup")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cleanupDelivered() {
        try {
            RecoveryResult result = executeCleanupDelivered();
            return Response.status(result.status()).entity(result.entity()).type(MediaType.APPLICATION_JSON).build();
        } catch (UnauthorizedException e) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Bearer")
                    .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (ForbiddenException e) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    private void ensureStore() {
        if (this.store == null) {
            if (this.session == null) {
                throw new IllegalStateException("KeycloakSession is not set");
            }
            this.store = new PasswordResetOutboxStore(this.session);
        }
    }

    public static String buildEntitySummaryJson(PasswordResetOutboxEntity entity) {
        long createdAtMillis = entity.getCreatedAt() != null ? entity.getCreatedAt().toEpochMilli() : 0L;
        long updatedAtMillis = entity.getUpdatedAt() != null ? entity.getUpdatedAt().toEpochMilli() : 0L;
        // DTO operacional mínimo. O operador precisa saber o que reprocessar e em que estado está,
        // não quem é o titular. Sujeito, realm e a mensagem crua da exceção ficam fora: o operador
        // correlaciona pelo sourceEventId, e o erro vira um código estável em vez de texto interno.
        return "{\"id\":\"" + escapeJson(entity.getId()) + "\"" +
                ",\"sourceEventId\":\"" + escapeJson(entity.getSourceEventId()) + "\"" +
                ",\"status\":\"" + escapeJson(entity.getStatus()) + "\"" +
                ",\"attemptCount\":" + entity.getAttemptCount() +
                ",\"lastErrorCode\":\"" + lastErrorCode(entity) + "\"" +
                ",\"createdAt\":" + createdAtMillis +
                ",\"updatedAt\":" + updatedAtMillis + "}";
    }

    /**
     * Reduz o último erro a um código seguro. A mensagem crua pode carregar host, caminho, SQL ou
     * dado do titular conforme a exceção que a originou, e nada disso pode atravessar a API.
     */
    static String lastErrorCode(PasswordResetOutboxEntity entity) {
        String lastError = entity.getLastError();
        if (lastError == null || lastError.isBlank()) {
            return "NONE";
        }
        return "DELIVERY_ERROR";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public KeycloakSession getSession() {
        return session;
    }

    public PasswordResetOutboxStore getStore() {
        return store;
    }

    public Clock getClock() {
        return clock;
    }

    public record RecoveryResult(int status, String entity) {
        public static RecoveryResult ok(String entity) {
            return new RecoveryResult(200, entity);
        }

        public static RecoveryResult badRequest(String errorJson) {
            return new RecoveryResult(400, errorJson);
        }

        public static RecoveryResult unauthorized(String errorJson) {
            return new RecoveryResult(401, errorJson);
        }

        public static RecoveryResult forbidden(String errorJson) {
            return new RecoveryResult(403, errorJson);
        }

        public static RecoveryResult notFound(String errorJson) {
            return new RecoveryResult(404, errorJson);
        }

        public static RecoveryResult conflict(String errorJson) {
            return new RecoveryResult(409, errorJson);
        }

        public static RecoveryResult serverError(String errorJson) {
            return new RecoveryResult(500, errorJson);
        }

        public int getStatus() {
            return status;
        }

        public String getEntity() {
            return entity;
        }
    }
}
