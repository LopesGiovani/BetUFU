package br.com.betufu.keycloak.password;

import java.util.Collections;
import java.util.List;
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider;

public class PasswordResetOutboxJpaEntityProvider implements JpaEntityProvider {

    public static final String CHANGELOG_LOCATION = "META-INF/betufu-password-reset-outbox-changelog.xml";

    @Override
    public List<Class<?>> getEntities() {
        return Collections.singletonList(PasswordResetOutboxEntity.class);
    }

    @Override
    public String getChangelogLocation() {
        return CHANGELOG_LOCATION;
    }

    @Override
    public void close() {
        // Sem recursos que exijam fechamento explícito
    }

    @Override
    public String getFactoryId() {
        return PasswordResetOutboxJpaEntityProviderFactory.ID;
    }
}
