package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.betufu.identity.internal.CurrentProfileResponse;

class CurrentProfileResponseTest {

    private static final List<String> APPROVED_COMPONENT_NAMES = List.of(
            "identityId",
            "nickname",
            "nicknameUpdatedAt",
            "matriculaStatus",
            "favoriteOrganizationId"
    );

    @Test
    @DisplayName("Garante allowlist estrita de 5 componentes em CurrentProfileResponse contra vazamento de PRIV-01 e PROF-02")
    void enforcesStrictAllowlistOnRecordComponents() {
        assertThat(CurrentProfileResponse.class.isRecord()).isTrue();

        RecordComponent[] components = CurrentProfileResponse.class.getRecordComponents();
        assertThat(components).isNotNull();

        List<String> actualNames = Arrays.stream(components)
                .map(RecordComponent::getName)
                .toList();

        assertThat(actualNames)
                .containsExactlyElementsOf(APPROVED_COMPONENT_NAMES);
    }

    @Test
    @DisplayName("Garante tipos escalares esperados para cada componente aprovado")
    void enforcesExpectedComponentTypes() {
        RecordComponent[] components = CurrentProfileResponse.class.getRecordComponents();
        assertThat(components).isNotNull();

        for (RecordComponent component : components) {
            switch (component.getName()) {
                case "identityId" -> assertThat(component.getType()).isEqualTo(UUID.class);
                case "nickname" -> assertThat(component.getType()).isEqualTo(String.class);
                case "nicknameUpdatedAt" -> assertThat(component.getType()).isEqualTo(Instant.class);
                case "matriculaStatus" -> assertThat(component.getType()).isEqualTo(String.class);
                case "favoriteOrganizationId" -> assertThat(component.getType()).isEqualTo(UUID.class);
                default -> throw new AssertionError("Componente não autorizado na allowlist: " + component.getName());
            }
        }
    }

    @Test
    @DisplayName("Proíbe explicitamente campos sensíveis como email, matricula, hmac, roles e mfa no DTO")
    void explicitlyForbidsSensitiveFieldNames() {
        List<String> componentNames = Arrays.stream(CurrentProfileResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(String::toLowerCase)
                .toList();

        assertThat(componentNames)
                .doesNotContain(
                        "email",
                        "matricula",
                        "hmac",
                        "role",
                        "roles",
                        "favoriteteam",
                        "favoriteteamid",
                        "team",
                        "mfa",
                        "mfaauthenticated",
                        "token",
                        "claims",
                        "password",
                        "issuer",
                        "subject");
    }
}
