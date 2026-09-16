package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ContextualLoginTargetPolicyTest {

  @ParameterizedTest
  @MethodSource("safeTargets")
  void acceptsOnlyCanonicalInternalTargets(String target) {
    assertThat(ContextualLoginTargetPolicy.requireSafeTarget(target)).isEqualTo(target);
  }

  @ParameterizedTest
  @MethodSource("unsafeTargets")
  void rejectsExternalAmbiguousAndTechnicalTargets(String target) {
    assertThatThrownBy(() -> ContextualLoginTargetPolicy.requireSafeTarget(target))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsafe contextual login target");
  }

  @Test
  void rejectsNullTarget() {
    assertThatThrownBy(() -> ContextualLoginTargetPolicy.requireSafeTarget(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsafe contextual login target");
  }

  private static Stream<String> safeTargets() {
    return Stream.of(
        "/",
        "/odds/mercados/11111111-1111-4111-8111-111111111111",
        "/odds/mercados/11111111-1111-4111-8111-111111111111?selection=AWAY#bilhete",
        "/conta/perfil?aba=matricula");
  }

  private static Stream<String> unsafeTargets() {
    return Stream.of(
        "",
        "odds/mercados/1",
        "https://evil.invalid/steal",
        "//evil.invalid/steal",
        "/\\evil.invalid/steal",
        "/odds//mercados/1",
        "/odds/../login",
        "/odds/%2e%2e/login",
        "/odds/%2f%2fevil.invalid",
        "/odds/%5cevil.invalid",
        "/odds/mercados/1%0d%0aLocation:%20https://evil.invalid",
        "/login",
        "/login/start?returnTo=/odds/mercados/1",
        "/login/oauth2/code/keycloak",
        "/oauth2/authorization/keycloak",
        "/logout",
        "/api/v1/session",
        "/actuator/health/liveness",
        "/error");
  }
}
