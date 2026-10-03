package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OperationalCapabilitiesResponseTest {

  private static final List<String> APPROVED_RESPONSE_COMPONENTS = List.of("capabilities");

  private static final List<String> APPROVED_CAPABILITY_VALUES =
      List.of(
          "VIEW_OPERATIONAL_PANEL",
          "MANAGE_ROLES",
          "CORRECT_ENROLLMENT",
          "MANAGE_SPORTS_CATALOG",
          "BOOTSTRAP_SPORTS_COMPETITION",
          "VIEW_ODDS_MARKET",
          "MANAGE_ODDS_MARKET",
          "PROPOSE_ODDS_OVERRIDE",
          "APPROVE_ODDS_OVERRIDE",
          "AUTHORIZE_SETTLEMENT");

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  @DisplayName(
      "Garante allowlist estrita de 1 componente no record OperationalCapabilitiesResponse")
  void enforcesStrictAllowlistOnOperationalCapabilitiesResponseComponents() {
    assertThat(OperationalCapabilitiesResponse.class.isRecord()).isTrue();

    List<String> actualNames =
        Arrays.stream(OperationalCapabilitiesResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

    assertThat(actualNames).containsExactlyElementsOf(APPROVED_RESPONSE_COMPONENTS);

    RecordComponent component = OperationalCapabilitiesResponse.class.getRecordComponents()[0];
    assertThat(component.getType()).isEqualTo(List.class);
  }

  @Test
  @DisplayName("Garante allowlist estrita e ordem canônica no enum OperationalCapability")
  void enforcesStrictAllowlistAndCanonicalOrderOnOperationalCapabilityEnum() {
    assertThat(OperationalCapability.class.isEnum()).isTrue();

    List<String> actualNames =
        Arrays.stream(OperationalCapability.values()).map(Enum::name).toList();

    assertThat(actualNames).containsExactlyElementsOf(APPROVED_CAPABILITY_VALUES);
    assertThat(OperationalCapability.VIEW_OPERATIONAL_PANEL.ordinal()).isEqualTo(0);
    assertThat(OperationalCapability.MANAGE_ROLES.ordinal()).isEqualTo(1);
    assertThat(OperationalCapability.CORRECT_ENROLLMENT.ordinal()).isEqualTo(2);
    assertThat(OperationalCapability.MANAGE_SPORTS_CATALOG.ordinal()).isEqualTo(3);
    assertThat(OperationalCapability.BOOTSTRAP_SPORTS_COMPETITION.ordinal()).isEqualTo(4);
    assertThat(OperationalCapability.VIEW_ODDS_MARKET.ordinal()).isEqualTo(5);
    assertThat(OperationalCapability.MANAGE_ODDS_MARKET.ordinal()).isEqualTo(6);
    assertThat(OperationalCapability.PROPOSE_ODDS_OVERRIDE.ordinal()).isEqualTo(7);
    assertThat(OperationalCapability.APPROVE_ODDS_OVERRIDE.ordinal()).isEqualTo(8);
    assertThat(OperationalCapability.AUTHORIZE_SETTLEMENT.ordinal()).isEqualTo(9);
  }

  @Test
  @DisplayName("Garante imutabilidade e cópia defensiva na lista de capacidades")
  void enforcesImmutabilityAndDefensiveCopy() {
    // Normalização de null para lista vazia
    OperationalCapabilitiesResponse emptyResponse = new OperationalCapabilitiesResponse(null);
    assertThat(emptyResponse.capabilities()).isEmpty();

    // Cópia defensiva na entrada
    List<OperationalCapability> mutableList = new ArrayList<>();
    mutableList.add(OperationalCapability.VIEW_OPERATIONAL_PANEL);

    OperationalCapabilitiesResponse response = new OperationalCapabilitiesResponse(mutableList);
    mutableList.add(OperationalCapability.MANAGE_ROLES);

    assertThat(response.capabilities())
        .containsExactly(OperationalCapability.VIEW_OPERATIONAL_PANEL);

    // Imutabilidade da lista exposta
    assertThatThrownBy(() -> response.capabilities().add(OperationalCapability.CORRECT_ENROLLMENT))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Serializa estritamente campo único 'capabilities' em JSON sem campos extras")
  void serializesStrictlySingleCapabilitiesFieldInJson() throws Exception {
    OperationalCapabilitiesResponse fullResponse =
        new OperationalCapabilitiesResponse(
            List.of(
                OperationalCapability.VIEW_OPERATIONAL_PANEL,
                OperationalCapability.MANAGE_ROLES,
                OperationalCapability.CORRECT_ENROLLMENT,
                OperationalCapability.MANAGE_SPORTS_CATALOG,
                OperationalCapability.BOOTSTRAP_SPORTS_COMPETITION));

    String json = JSON.writeValueAsString(fullResponse);
    JsonNode root = JSON.readTree(json);

    assertThat(root.isObject()).isTrue();
    assertThat(root.size()).isEqualTo(1);
    assertThat(root.has("capabilities")).isTrue();
    assertThat(root.get("capabilities").isArray()).isTrue();
    assertThat(root.get("capabilities").size()).isEqualTo(5);
    assertThat(root.get("capabilities").get(0).asText()).isEqualTo("VIEW_OPERATIONAL_PANEL");
    assertThat(root.get("capabilities").get(1).asText()).isEqualTo("MANAGE_ROLES");
    assertThat(root.get("capabilities").get(2).asText()).isEqualTo("CORRECT_ENROLLMENT");
    assertThat(root.get("capabilities").get(3).asText()).isEqualTo("MANAGE_SPORTS_CATALOG");
    assertThat(root.get("capabilities").get(4).asText()).isEqualTo("BOOTSTRAP_SPORTS_COMPETITION");

    OperationalCapabilitiesResponse emptyResponse = new OperationalCapabilitiesResponse(List.of());
    String emptyJson = JSON.writeValueAsString(emptyResponse);
    JsonNode emptyRoot = JSON.readTree(emptyJson);

    assertThat(emptyRoot.isObject()).isTrue();
    assertThat(emptyRoot.size()).isEqualTo(1);
    assertThat(emptyRoot.get("capabilities").isArray()).isTrue();
    assertThat(emptyRoot.get("capabilities").isEmpty()).isTrue();
  }

  @Test
  @DisplayName(
      "Proíbe explicitamente campos sensíveis como identityId, papel, escopo, apelido, email, matrícula ou tokens")
  void explicitlyForbidsSensitiveFieldNamesAcrossDto() {
    List<String> componentNames =
        Arrays.stream(OperationalCapabilitiesResponse.class.getRecordComponents())
            .map(RecordComponent::getName)
            .map(String::toLowerCase)
            .toList();

    assertThat(componentNames)
        .doesNotContain(
            "identityid",
            "identity_id",
            "role",
            "rolename",
            "role_name",
            "scope",
            "scoperef",
            "scope_ref",
            "nickname",
            "apelido",
            "email",
            "matricula",
            "rawmatricula",
            "hmac",
            "digest",
            "token",
            "claims",
            "mfa",
            "password",
            "secret");
  }
}
