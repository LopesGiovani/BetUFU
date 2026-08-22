package br.com.betufu.keycloak.password;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class BetufuPasswordEventListenerFactory implements EventListenerProviderFactory {

    public static final String ID = "betufu-password-event-listener";

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new BetufuPasswordEventListener(session);
    }

    @Override
    public void init(Config.Scope config) {
        // Inicialização de configuração do provider SPI
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Pós-inicialização da fábrica do provider SPI
    }

    @Override
    public void close() {
        // Limpeza de recursos da fábrica
    }

    @Override
    public String getId() {
        return ID;
    }
}
