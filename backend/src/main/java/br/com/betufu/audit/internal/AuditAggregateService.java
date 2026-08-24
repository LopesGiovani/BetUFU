package br.com.betufu.audit.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.audit.AuditAggregateQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação interna do serviço de agregação de auditoria.
 *
 * <p>Executa consultas de leitura e projeção segura diretamente sobre a base de dados pública,
 * sem importar nenhum tipo de domínio dos módulos {@code platform}, {@code identity} ou {@code verification}.
 */
@Service
@Profile("runtime | integrated")
public class AuditAggregateService implements AuditAggregateQuery {

    private final JdbcTemplate jdbcTemplate;

    public AuditAggregateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentRoleChangeEvent> findRecentRoleChangeEvents(Set<String> actions, int limit) {
        return findRecentEventRows(actions, limit).stream()
                .map(event -> new RecentRoleChangeEvent(
                        event.eventId(),
                        event.occurredAt(),
                        event.actorIdentityId(),
                        event.actorRole(),
                        event.targetIdentityId(),
                        event.targetNickname(),
                        event.action(),
                        event.outcome(),
                        event.hasReason()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentAdministrativeCorrectionEvent> findRecentAdministrativeCorrectionEvents(
            Set<String> actions,
            int limit) {
        return findRecentEventRows(actions, limit).stream()
                .map(event -> new RecentAdministrativeCorrectionEvent(
                        event.eventId(),
                        event.occurredAt(),
                        event.actorIdentityId(),
                        event.actorRole(),
                        event.targetIdentityId(),
                        event.targetNickname(),
                        event.action(),
                        event.outcome(),
                        event.hasReason(),
                        event.reason()))
                .toList();
    }

    private List<RecentAuditEventRow> findRecentEventRows(Set<String> actions, int limit) {
        if (actions == null || actions.isEmpty() || limit <= 0) {
            return List.of();
        }

        String inSql = String.join(",", Collections.nCopies(actions.size(), "?"));
        String sql = """
                SELECT
                    e.event_id,
                    e.occurred_at,
                    e.actor_identity_id,
                    e.actor_role,
                    e.target_identity_id,
                    p.nickname AS target_nickname,
                    e.action,
                    e.outcome,
                    e.reason
                FROM public.audit_event e
                LEFT JOIN public.betufu_profile p ON e.target_identity_id = p.identity_id
                WHERE e.action IN (%s)
                ORDER BY e.occurred_at DESC, e.event_id DESC
                LIMIT ?
                """.formatted(inSql);

        List<Object> params = new ArrayList<>(actions);
        params.add(limit);

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UUID eventId = rs.getObject("event_id", UUID.class);
            Timestamp ts = rs.getTimestamp("occurred_at");
            Instant occurredAt = ts != null ? ts.toInstant() : Instant.EPOCH;
            UUID actorIdentityId = rs.getObject("actor_identity_id", UUID.class);
            String actorRole = rs.getString("actor_role");
            UUID targetIdentityId = rs.getObject("target_identity_id", UUID.class);
            String targetNickname = rs.getString("target_nickname");
            String action = rs.getString("action");
            String outcome = rs.getString("outcome");
            String reason = rs.getString("reason");
            boolean hasReason = reason != null && !reason.isBlank();

            return new RecentAuditEventRow(
                    eventId,
                    occurredAt,
                    actorIdentityId,
                    actorRole,
                    targetIdentityId,
                    targetNickname,
                    action,
                    outcome,
                    hasReason,
                    reason);
        }, params.toArray());
    }

    private record RecentAuditEventRow(
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
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActionOutcomeCount> countByActionsAndOutcomes(Set<String> actions, Instant since, Instant until) {
        if (actions == null || actions.isEmpty()) {
            return List.of();
        }

        Timestamp sinceTs = since != null ? Timestamp.from(since) : Timestamp.from(Instant.EPOCH);
        Timestamp untilTs = until != null ? Timestamp.from(until) : Timestamp.from(Instant.now());

        String inSql = String.join(",", Collections.nCopies(actions.size(), "?"));
        String sql = """
                SELECT e.action, e.outcome, count(*) AS total_count
                FROM public.audit_event e
                WHERE e.action IN (%s)
                  AND e.occurred_at >= ?
                  AND e.occurred_at <= ?
                GROUP BY e.action, e.outcome
                """.formatted(inSql);

        List<Object> params = new ArrayList<>(actions);
        params.add(sinceTs);
        params.add(untilTs);

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ActionOutcomeCount(
                rs.getString("action"),
                rs.getString("outcome"),
                rs.getLong("total_count")),
                params.toArray());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RejectionReasonCount> countRejectionsByReason(String action, Instant since, Instant until) {
        if (action == null || action.isBlank()) {
            return List.of();
        }

        Timestamp sinceTs = since != null ? Timestamp.from(since) : Timestamp.from(Instant.EPOCH);
        Timestamp untilTs = until != null ? Timestamp.from(until) : Timestamp.from(Instant.now());

        String sql = """
                SELECT e.reason, count(*) AS total_count
                FROM public.audit_event e
                WHERE e.action = ?
                  AND e.outcome = 'BLOQUEADO'
                  AND e.occurred_at >= ?
                  AND e.occurred_at <= ?
                GROUP BY e.reason
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RejectionReasonCount(
                rs.getString("reason") != null ? rs.getString("reason") : "",
                rs.getLong("total_count")),
                action, sinceTs, untilTs);
    }
}
