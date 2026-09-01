package br.com.betufu.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * API pública do módulo de auditoria para consultas agregadas e eventos recentes higienizados
 * (D-04).
 *
 * <p>Conforme a política do procedimento de auditoria, não existe endpoint de listagem crua da
 * trilha. A projeção de privacidade acontece exclusivamente dentro do módulo de auditoria: os tipos
 * públicos não contêm valor anterior, valor novo nem chave de idempotência.
 */
public interface AuditAggregateQuery {

  /**
   * Projeção imutável de evento recente de auditoria para o painel operacional. Não expõe valor
   * anterior, valor novo nem chave de idempotência.
   */
  record RecentRoleChangeEvent(
      UUID eventId,
      Instant occurredAt,
      UUID actorIdentityId,
      String actorRole,
      UUID targetIdentityId,
      String targetNickname,
      String action,
      String outcome,
      boolean hasReason) {

    public RecentRoleChangeEvent {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(action, "action must not be null");
      Objects.requireNonNull(outcome, "outcome must not be null");
    }
  }

  /**
   * Projeção imutável de correção administrativa de matrícula.
   *
   * <p>Este é o único recorte recente que transporta o motivo integral, pois ele é conteúdo
   * operacional exigido para a correção. Alterações de papel expõem apenas {@link
   * RecentRoleChangeEvent#hasReason()}.
   */
  record RecentAdministrativeCorrectionEvent(
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

    public RecentAdministrativeCorrectionEvent {
      Objects.requireNonNull(eventId, "eventId must not be null");
      Objects.requireNonNull(occurredAt, "occurredAt must not be null");
      Objects.requireNonNull(action, "action must not be null");
      Objects.requireNonNull(outcome, "outcome must not be null");
    }
  }

  /** Projeção imutável de contagem agregada de eventos por ação e resultado. */
  record ActionOutcomeCount(String action, String outcome, long count) {

    public ActionOutcomeCount {
      Objects.requireNonNull(action, "action must not be null");
      Objects.requireNonNull(outcome, "outcome must not be null");
    }
  }

  /** Projeção imutável de contagem de recusas agrupadas por motivo. */
  record RejectionReasonCount(String reason, long count) {

    public RejectionReasonCount {
      Objects.requireNonNull(reason, "reason must not be null");
    }
  }

  /**
   * Consulta os eventos mais recentes para um conjunto fechado de ações, ordenados por {@code
   * occurredAt DESC, eventId DESC}.
   *
   * @param actions conjunto de ações permitidas
   * @param limit limite máximo de registros
   * @return lista de eventos recentes higienizados
   */
  List<RecentRoleChangeEvent> findRecentRoleChangeEvents(Set<String> actions, int limit);

  /** Consulta correções administrativas recentes com o motivo integral exigido pelo painel. */
  List<RecentAdministrativeCorrectionEvent> findRecentAdministrativeCorrectionEvents(
      Set<String> actions, int limit);

  /**
   * Consulta contagens de eventos agrupados por ação e resultado num intervalo temporal.
   *
   * @param actions conjunto de ações a contabilizar
   * @param since início do intervalo (inclusive)
   * @param until fim do intervalo (inclusive)
   * @return lista de contagens agregadas
   */
  List<ActionOutcomeCount> countByActionsAndOutcomes(
      Set<String> actions, Instant since, Instant until);

  /**
   * Consulta contagens de recusas agrupadas por motivo para uma ação específica num intervalo.
   *
   * @param action ação auditada (ex: {@code REGISTRO_MATRICULA})
   * @param since início do intervalo
   * @param until fim do intervalo
   * @return contagens por motivo
   */
  List<RejectionReasonCount> countRejectionsByReason(String action, Instant since, Instant until);
}
