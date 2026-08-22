package br.com.betufu.keycloak.password;

import org.keycloak.Config;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class PasswordResetOutboxJpaEntityProviderFactory implements JpaEntityProviderFactory {

    public static final String ID = "betufu-password-reset-outbox-jpa-entity-provider";

    @Override
    public JpaEntityProvider create(KeycloakSession session) {
        return new PasswordResetOutboxJpaEntityProvider();
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
