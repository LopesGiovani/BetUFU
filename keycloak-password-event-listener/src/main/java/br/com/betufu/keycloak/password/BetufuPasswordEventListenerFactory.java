package br.com.betufu.keycloak.password;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class BetufuPasswordEventListenerFactory implements EventListenerProviderFactory {

    public static final String ID = "betufu-password-event-listener";

    /**
     * Tamanho mínimo, em bytes já decodificados, exigido da chave HMAC. Uma chave menor que o
     * bloco do SHA-256 enfraquece a assinatura sem que nada no runtime acuse o problema.
     */
    public static final int MINIMUM_HMAC_KEY_BYTES = 32;

    private PasswordResetOutboxDispatcher dispatcher;

    /**
     * A integração nasce desabilitada. Ativar exige declaração explícita na configuração do
     * realm somada às demais condições de {@link #activationAllowed()}.
     */
    private boolean enabled = false;

    private String keyId;
    private byte[] decodedHmacKey = new byte[0];

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new BetufuPasswordEventListener(session);
    }

    @Override
    public void init(Config.Scope config) {
        String endpoint = null;
        String key = null;
        String keyId = "v1";

        if (config != null) {
            endpoint = config.get("endpoint");
            key = config.get("hmacKey");
            keyId = config.get("keyId", "v1");
            enabled = config.getBoolean("enabled", false);
        }

        if (endpoint == null || endpoint.isBlank()) {
            endpoint = System.getenv("BETUFU_PASSWORD_RESET_ENDPOINT");
        }
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = System.getProperty("betufu.password.reset.endpoint");
        }

        if (key == null || key.isBlank()) {
            key = System.getenv("BETUFU_PASSWORD_RESET_HMAC_KEY");
        }
        if (key == null || key.isBlank()) {
            key = System.getProperty("betufu.password.reset.hmacKey");
        }

        if (keyId == null || keyId.isBlank()) {
            keyId = System.getenv("BETUFU_PASSWORD_RESET_KEY_ID");
        }
        if (keyId == null || keyId.isBlank()) {
            keyId = System.getProperty("betufu.password.reset.keyId", "v1");
        }

        this.keyId = keyId;

        // A chave é sempre Base64. Base64 inválido não pode explodir em init(), senão o Keycloak
        // falha o boot inteiro em vez de simplesmente manter a integração desligada; guardamos a
        // chave decodificada e deixamos activationAllowed() recusar a ativação.
        byte[] hmacKeyBytes = new byte[0];
        this.decodedHmacKey = new byte[0];
        if (key != null && !key.isBlank()) {
            try {
                this.decodedHmacKey = Base64.getDecoder().decode(key.trim());
                hmacKeyBytes = key.trim().getBytes(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalidBase64) {
                this.decodedHmacKey = new byte[0];
                hmacKeyBytes = new byte[0];
            }
        }

        this.dispatcher = PasswordResetOutboxDispatcher.builder()
                .endpointUri(endpoint)
                .hmacKey(hmacKeyBytes)
                .keyId(keyId)
                .build();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        if (activationAllowed()) {
            dispatcher.start(factory, 5000L);
        }
    }

    /**
     * Conjunção fail-closed que governa a ativação: habilitação explícita, dispatcher construído,
     * endpoint HTTPS no path exato do contrato, key-id não vazio e chave HMAC Base64 válida com
     * tamanho mínimo. Qualquer condição ausente mantém a integração parada, sem entrega.
     */
    public boolean activationAllowed() {
        if (!enabled || dispatcher == null) {
            return false;
        }
        if (!isApprovedEndpoint(dispatcher.getEndpointUri())) {
            return false;
        }
        if (keyId == null || keyId.isBlank()) {
            return false;
        }
        return decodedHmacKey.length >= MINIMUM_HMAC_KEY_BYTES;
    }

    /**
     * Exige HTTPS, host presente e o path exato do contrato, sem query, fragment nem userInfo.
     *
     * <p>Validar só o esquema deixava a integração subir apontando para qualquer URL HTTPS, e todas
     * as entregas terminariam em 404 sem que nada acusasse. Validar esquema e path ainda deixava
     * passar {@code https:/api/v1/internal/events/password-reset} — uma barra só, sem autoridade —,
     * que satisfaz esquema e path e não identifica servidor algum. Medido: {@code URI.create}
     * devolve {@code host == null} nesse caso e a ativação era autorizada.</p>
     *
     * <p>{@code userInfo} é recusado por higiene de configuração: credencial embutida em URL vaza
     * por log, histórico e mensagem de erro, e não faz parte do contrato de transporte, que
     * autentica por HMAC no envelope.</p>
     */
    private static boolean isApprovedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(endpoint.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null
                    && PasswordResetOutboxDispatcher.DEFAULT_PATH.equals(uri.getPath())
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
        } catch (IllegalArgumentException malformedEndpoint) {
            return false;
        }
    }

    @Override
    public void close() {
        if (dispatcher != null) {
            dispatcher.close();
        }
    }

    @Override
    public String getId() {
        return ID;
    }

    public PasswordResetOutboxDispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(PasswordResetOutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
