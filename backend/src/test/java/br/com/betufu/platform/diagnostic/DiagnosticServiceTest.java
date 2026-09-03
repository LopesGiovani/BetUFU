package br.com.betufu.platform.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.env.MockEnvironment;

class DiagnosticServiceTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-08-17T12:00:00Z");
  private Clock fixedClock;
  private MockEnvironment mockEnvironment;
  private DiagnosticService diagnosticService;

  @BeforeEach
  void setUp() {
    MDC.clear();
    fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    mockEnvironment = new MockEnvironment();
    mockEnvironment.setActiveProfiles("runtime", "dev");
    mockEnvironment.setProperty("betufu.version", "0.1.0-dev");
    diagnosticService = new DiagnosticService(fixedClock, mockEnvironment);
  }

  private List<DiagnosticComponent> createAllUpComponents() {
    return List.of(
        new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API respondendo"),
        new DiagnosticComponent(
            DiagnosticComponent.BFF_SESSION, DiagnosticStatus.UP, "Sessão ativa"),
        new DiagnosticComponent(DiagnosticComponent.KEYCLOAK, DiagnosticStatus.UP, "Discovery OK"),
        new DiagnosticComponent(
            DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PostgreSQL 18 OK"),
        new DiagnosticComponent(DiagnosticComponent.REDIS, DiagnosticStatus.UP, "Redis PONG"),
        new DiagnosticComponent(
            DiagnosticComponent.OBJECT_STORAGE, DiagnosticStatus.UP, "Bucket OK"));
  }

  @Test
  @DisplayName("C1: Resposta saudável possui exatamente seis componentes ordenados e status UP")
  void allUpReturnsHealthyResponse() {
    DiagnosticResponse response = diagnosticService.aggregate(createAllUpComponents());

    assertThat(response.status()).isEqualTo(DiagnosticStatus.UP);
    assertThat(response.summary()).isEqualTo("Todos os sistemas operacionais");
    assertThat(response.timestamp()).isEqualTo(FIXED_INSTANT);
    assertThat(response.environment()).isEqualTo("dev,runtime");
    assertThat(response.version()).isEqualTo("0.1.0-dev");
    assertThat(response.correlationId()).startsWith("untracked-");

    assertThat(response.components())
        .hasSize(6)
        .extracting(DiagnosticComponent::name)
        .containsExactly(
            "API", "Sessão BFF", "Keycloak", "PostgreSQL", "Redis", "Armazenamento de objetos");
  }

  @Test
  @DisplayName(
      "C3: Falha parcial auxiliar (apenas Redis degradado) resulta em status geral DEGRADED")
  void partialAuxiliaryFailureReturnsDegradedOverallStatus() {
    List<DiagnosticComponent> components =
        List.of(
            new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API OK"),
            new DiagnosticComponent(
                DiagnosticComponent.BFF_SESSION, DiagnosticStatus.DEGRADED, "Sessão degradada"),
            new DiagnosticComponent(
                DiagnosticComponent.KEYCLOAK, DiagnosticStatus.UP, "Keycloak OK"),
            new DiagnosticComponent(
                DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PostgreSQL OK"),
            new DiagnosticComponent(
                DiagnosticComponent.REDIS, DiagnosticStatus.DEGRADED, "Redis degradado"),
            new DiagnosticComponent(
                DiagnosticComponent.OBJECT_STORAGE, DiagnosticStatus.UP, "S3 OK"));

    DiagnosticResponse response = diagnosticService.aggregate(components);

    assertThat(response.status()).isEqualTo(DiagnosticStatus.DEGRADED);
    assertThat(response.summary()).contains("2 serviço(s) operando em modo degradado");
    assertThat(response.components()).hasSize(6);
  }

  @Test
  @DisplayName("C3 & P2: Falha em PostgreSQL (autoridade primária) torna a plataforma DOWN")
  void postgresDownMakesPlatformDown() {
    List<DiagnosticComponent> components =
        List.of(
            new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API OK"),
            new DiagnosticComponent(
                DiagnosticComponent.BFF_SESSION, DiagnosticStatus.UP, "Sessão OK"),
            new DiagnosticComponent(
                DiagnosticComponent.KEYCLOAK, DiagnosticStatus.UP, "Keycloak OK"),
            new DiagnosticComponent(
                DiagnosticComponent.POSTGRESQL, DiagnosticStatus.DOWN, "PostgreSQL offline"),
            new DiagnosticComponent(DiagnosticComponent.REDIS, DiagnosticStatus.UP, "Redis OK"),
            new DiagnosticComponent(
                DiagnosticComponent.OBJECT_STORAGE, DiagnosticStatus.UP, "S3 OK"));

    DiagnosticResponse response = diagnosticService.aggregate(components);

    assertThat(response.status()).isEqualTo(DiagnosticStatus.DOWN);
    assertThat(response.summary()).contains("Plataforma indisponível");
  }

  @Test
  @DisplayName("C3 & P2: Falha em Keycloak (autoridade de identidade) torna a plataforma DOWN")
  void keycloakDownMakesPlatformDown() {
    List<DiagnosticComponent> components =
        List.of(
            new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API OK"),
            new DiagnosticComponent(
                DiagnosticComponent.BFF_SESSION, DiagnosticStatus.UP, "Sessão OK"),
            new DiagnosticComponent(
                DiagnosticComponent.KEYCLOAK, DiagnosticStatus.DOWN, "Keycloak offline"),
            new DiagnosticComponent(
                DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PostgreSQL OK"),
            new DiagnosticComponent(DiagnosticComponent.REDIS, DiagnosticStatus.UP, "Redis OK"),
            new DiagnosticComponent(
                DiagnosticComponent.OBJECT_STORAGE, DiagnosticStatus.UP, "S3 OK"));

    DiagnosticResponse response = diagnosticService.aggregate(components);

    assertThat(response.status()).isEqualTo(DiagnosticStatus.DOWN);
    assertThat(response.summary()).contains("Plataforma indisponível");
  }

  @Test
  @DisplayName(
      "C3: Falha em múltiplas dependências externas (>= 2 indisponíveis) resulta em status geral DOWN")
  void multipleFailuresReturnDownOverallStatus() {
    List<DiagnosticComponent> components =
        List.of(
            new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API OK"),
            new DiagnosticComponent(
                DiagnosticComponent.BFF_SESSION, DiagnosticStatus.DEGRADED, "Sessão degradada"),
            new DiagnosticComponent(
                DiagnosticComponent.KEYCLOAK, DiagnosticStatus.UP, "Keycloak OK"),
            new DiagnosticComponent(
                DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PostgreSQL OK"),
            new DiagnosticComponent(
                DiagnosticComponent.REDIS, DiagnosticStatus.DOWN, "Redis offline"),
            new DiagnosticComponent(
                DiagnosticComponent.OBJECT_STORAGE, DiagnosticStatus.DOWN, "S3 offline"));

    DiagnosticResponse response = diagnosticService.aggregate(components);

    assertThat(response.status()).isEqualTo(DiagnosticStatus.DOWN);
    assertThat(response.summary())
        .contains("Plataforma indisponível (2 dependência(s) crítica(s) fora do ar)");
    assertThat(response.components()).hasSize(6);
  }

  @Test
  @DisplayName("C1 & Ordem: Agregador reordena lista fora de ordem para a ordem canônica do D-03")
  void unorderedInputIsSortedToCanonicalOrder() {
    List<DiagnosticComponent> reverseComponents =
        List.of(
            new DiagnosticComponent(
                DiagnosticComponent.OBJECT_STORAGE, DiagnosticStatus.UP, "S3 OK"),
            new DiagnosticComponent(DiagnosticComponent.REDIS, DiagnosticStatus.UP, "Redis OK"),
            new DiagnosticComponent(DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PG OK"),
            new DiagnosticComponent(DiagnosticComponent.KEYCLOAK, DiagnosticStatus.UP, "KC OK"),
            new DiagnosticComponent(
                DiagnosticComponent.BFF_SESSION, DiagnosticStatus.UP, "Session OK"),
            new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API OK"));

    DiagnosticResponse response = diagnosticService.aggregate(reverseComponents);

    assertThat(response.components())
        .extracting(DiagnosticComponent::name)
        .containsExactly(
            "API", "Sessão BFF", "Keycloak", "PostgreSQL", "Redis", "Armazenamento de objetos");
  }

  @Test
  @DisplayName("C1: Lista incompleta ou com componentes desconhecidos/duplicados é rejeitada")
  void incompleteOrInvalidComponentsListThrowsException() {
    List<DiagnosticComponent> incomplete =
        List.of(
            new DiagnosticComponent(DiagnosticComponent.API, DiagnosticStatus.UP, "API OK"),
            new DiagnosticComponent(DiagnosticComponent.POSTGRESQL, DiagnosticStatus.UP, "PG OK"));

    assertThatThrownBy(() -> diagnosticService.aggregate(incomplete))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () -> new DiagnosticComponent("ComponenteInvalido", DiagnosticStatus.UP, "Desc"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Correlation ID: Reusa valor presente no MDC do CorrelationIdFilter")
  void reusesCorrelationIdFromMdc() {
    String testCorrId = "corr-test-123456";
    MDC.put("correlationId", testCorrId);

    DiagnosticResponse response = diagnosticService.aggregate(createAllUpComponents());
    assertThat(response.correlationId()).isEqualTo(testCorrId);
  }

  @Test
  @DisplayName("C4: Prova de ausência de host, porta, credencial e stack trace na resposta")
  void verifiesAbsenceOfTopologyAndSecrets() {
    DiagnosticComponent componentWithInternalInfoAttempt =
        new DiagnosticComponent(
            DiagnosticComponent.POSTGRESQL, DiagnosticStatus.DEGRADED, "Serviço degradado");

    assertThat(componentWithInternalInfoAttempt.description())
        .doesNotContain("localhost")
        .doesNotContain("5432")
        .doesNotContain("6379")
        .doesNotContain("jdbc:")
        .doesNotContain("password")
        .doesNotContain("Exception");
  }
}
