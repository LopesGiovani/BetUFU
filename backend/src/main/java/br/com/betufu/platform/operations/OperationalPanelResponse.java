package br.com.betufu.platform.operations;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resposta imutável do painel operacional (OPER-06, S4 do 02-UI-SPEC.md).
 *
 * <p>Blocos não autorizados para o papel vigente são {@code null} e deliberadamente omitidos da
 * serialização JSON via {@link JsonInclude.Include#NON_NULL}. A ausência no servidor protege a
 * privacidade e impede a revelação de escopo (OPER-02, OPER-04).
 *
 * <p>Agregados nunca contêm listas de pessoas, e-mails, matrículas ou digests (PRIV-01, PROF-02).
 *
 * <p>Falhas parciais em blocos autorizados são declaradas através de {@code unavailableBlocks},
 * contendo exclusivamente os blocos autorizados que falharam na consulta, sem expor mensagens de
 * erro técnicas nem revelar blocos fora do escopo do papel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OperationalPanelResponse(
    Instant queriedAt,
    TimeWindow timeWindow,
    RoleChangesBlock roleChanges,
    RejectedEnrollmentsBlock rejectedEnrollmentsCount,
    AdministrativeCorrectionsBlock administrativeCorrections,
    UnregisteredEnrollmentsBlock unregisteredEnrollmentsCount,
    List<OperationalBlock> unavailableBlocks) {

  public OperationalPanelResponse {
    Objects.requireNonNull(queriedAt, "queriedAt must not be null");
    Objects.requireNonNull(timeWindow, "timeWindow must not be null");
    unavailableBlocks = unavailableBlocks == null ? List.of() : List.copyOf(unavailableBlocks);
  }

  public OperationalPanelResponse(
      Instant queriedAt,
      TimeWindow timeWindow,
      RoleChangesBlock roleChanges,
      RejectedEnrollmentsBlock rejectedEnrollmentsCount,
      AdministrativeCorrectionsBlock administrativeCorrections,
      UnregisteredEnrollmentsBlock unregisteredEnrollmentsCount) {
    this(
        queriedAt,
        timeWindow,
        roleChanges,
        rejectedEnrollmentsCount,
        administrativeCorrections,
        unregisteredEnrollmentsCount,
        List.of());
  }

  /** Enumeração fechada dos blocos operacionais governados do painel. */
  public enum OperationalBlock {
    ROLE_CHANGES,
    REJECTED_ENROLLMENTS_COUNT,
    ADMINISTRATIVE_CORRECTIONS,
    UNREGISTERED_ENROLLMENTS_COUNT
  }

  /** Recorte temporal aplicado às consultas de agregação do painel. */
  public record TimeWindow(Instant since, Instant until) {
    public TimeWindow {
      Objects.requireNonNull(since, "since must not be null");
      Objects.requireNonNull(until, "until must not be null");
    }
  }

  /**
   * Bloco de alterações de papel (concessões, revogações e tentativas bloqueadas). Autorizado
   * apenas para ADMINISTRADOR_GERAL.
   */
  public record RoleChangesBlock(List<RoleChangeEventDto> recentEvents) {
    public RoleChangesBlock {
      recentEvents = recentEvents == null ? List.of() : List.copyOf(recentEvents);
    }
  }

  /** Projeção imutável de evento de alteração de papel. */
  public record RoleChangeEventDto(
      UUID eventId,
      Instant occurredAt,
      UUID actorIdentityId,
      String actorRole,
      UUID targetIdentityId,
      String targetNickname,
      String action,
      String outcome,
      boolean hasReason) {
    public RoleChangeEventDto {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(action, "action must not be null");
      Objects.requireNonNull(outcome, "outcome must not be null");
    }
  }

  /**
   * Bloco de registros de matrícula recusados (números agregados por categoria). Autorizado apenas
   * para ADMINISTRADOR_GERAL.
   */
  public record RejectedEnrollmentsBlock(
      long duplicateCount, long attemptLimitCount, long totalCount) {}

  /**
   * Bloco de correções administrativas de matrícula. Autorizado apenas para ADMINISTRADOR_GERAL.
   */
  public record AdministrativeCorrectionsBlock(
      List<AdministrativeCorrectionEventDto> recentEvents) {
    public AdministrativeCorrectionsBlock {
      recentEvents = recentEvents == null ? List.of() : List.copyOf(recentEvents);
    }
  }

  /** Projeção imutável de correção administrativa de matrícula. */
  public record AdministrativeCorrectionEventDto(
      UUID eventId,
      Instant occurredAt,
      UUID actorIdentityId,
      String actorRole,
      UUID targetIdentityId,
      String targetNickname,
      String action,
      String outcome,
      boolean hasReason,
      String reason) {
    public AdministrativeCorrectionEventDto {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(action, "action must not be null");
      Objects.requireNonNull(outcome, "outcome must not be null");
    }
  }

  /**
   * Bloco de contas sem matrícula registrada (número agregado puro, sem lista). Autorizado para
   * ADMINISTRADOR_GERAL e ADMINISTRADOR_COMPETICAO.
   */
  public record UnregisteredEnrollmentsBlock(int count) {}
}
