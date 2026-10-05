package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEventPort;
import br.com.betufu.chat.ChatPredictionPort;
import br.com.betufu.chat.ChatSettlementPort;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authoritative write gate for event chat; global chat is deliberately independent. */
@Service
@Profile("runtime | integrated")
public class ChatLifecycleService {
  private static final long CHAT_WINDOW_SECONDS = 2 * 60 * 60;

  private final JdbcTemplate jdbc;
  private final ChatEventPort events;
  private final ChatPredictionPort predictions;
  private final ChatSettlementPort settlements;
  private final Clock clock;

  public ChatLifecycleService(
      JdbcTemplate jdbc,
      ChatEventPort events,
      ChatPredictionPort predictions,
      ChatSettlementPort settlements,
      Clock clock) {
    this.jdbc = Objects.requireNonNull(jdbc);
    this.events = Objects.requireNonNull(events);
    this.predictions = Objects.requireNonNull(predictions);
    this.settlements = Objects.requireNonNull(settlements);
    this.clock = Objects.requireNonNull(clock);
  }

  // The outer write transaction also preserves this expected rejection.
  @Transactional(noRollbackFor = ApiProblemException.class)
  public void requireWritable(UUID eventId, Instant requestedNow) {
    if (eventId == null) return;
    var history = events.findDefinitiveMilestones(eventId);
    ensureState(eventId);
    lockState(eventId);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    UUID closedFact =
        jdbc.query(
            "SELECT lifecycle_fact_id FROM public.chat_event_lifecycle_close WHERE event_id=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            eventId);
    if (closedFact != null) {
      close(eventId, closedFact, now);
      return;
    }

    boolean requiresSettlement = predictions.inspect(eventId).hasAccepted();
    for (var milestone : history) {
      Instant at = milestone.milestoneAt();
      if (requiresSettlement) {
        var completion = settlements.findCompletion(eventId, milestone.sourceFactId()).orElse(null);
        if (completion == null || !completion.complete() || completion.completedAt() == null)
          continue;
        at = completion.completedAt();
      }
      String kind = requiresSettlement ? "RESULT_SETTLED" : "NO_PREDICTIONS_CONFIRMED";
      if ("EVENT_CANCELLED".equals(milestone.kind())) kind = "CANCELLATION_CONFIRMED";
      at = at.truncatedTo(ChronoUnit.MICROS);
      appendMilestone(eventId, kind, milestone, at, at.plusSeconds(CHAT_WINDOW_SECONDS));
    }

    // Replay immutable facts even when the projection was lost or no writes observed a correction.
    var facts =
        jdbc.query(
            "SELECT lifecycle_fact_id,milestone_kind,milestone_at,deadline_at FROM public.chat_event_lifecycle_fact WHERE event_id=? ORDER BY milestone_at,lifecycle_fact_id",
            (rs, row) ->
                new Fact(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getTimestamp(3).toInstant(),
                    rs.getTimestamp(4).toInstant()),
            eventId);
    Fact active = null;
    for (var fact : facts) {
      if (active != null && !fact.at().isBefore(active.deadline())) {
        close(eventId, active.id(), now);
        return;
      }
      active = fact;
    }
    if (active == null) return;
    jdbc.update(
        "UPDATE public.chat_event_lifecycle_state SET state='OPEN',milestone_kind=?,lifecycle_fact_id=?,milestone_at=?,deadline_at=?,rebuilt_at=? WHERE event_id=?",
        active.kind(),
        active.id(),
        Timestamp.from(active.at()),
        Timestamp.from(active.deadline()),
        Timestamp.from(now),
        eventId);
    if (!now.isBefore(active.deadline())) close(eventId, active.id(), now);
  }

  private record Fact(UUID id, String kind, Instant at, Instant deadline) {}

  private UUID appendMilestone(
      UUID eventId,
      String kind,
      ChatEventPort.DefinitiveMilestone milestone,
      Instant milestoneAt,
      Instant deadline) {
    UUID existing =
        jdbc.query(
            "SELECT lifecycle_fact_id FROM public.chat_event_lifecycle_fact WHERE event_id=? AND milestone_kind=? AND source_fact_id=?",
            rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
            eventId,
            kind,
            milestone.sourceFactId());
    if (existing != null) return existing;
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO public.chat_event_lifecycle_fact
          (lifecycle_fact_id,event_id,milestone_kind,source_fact_id,source_version_id,milestone_at,deadline_at)
        VALUES (?,?,?,?,?,?,?)
        ON CONFLICT (event_id,milestone_kind,source_fact_id) DO NOTHING
        """,
        id,
        eventId,
        kind,
        milestone.sourceFactId(),
        milestone.sourceVersionId(),
        Timestamp.from(milestoneAt),
        Timestamp.from(deadline));
    return jdbc.queryForObject(
        "SELECT lifecycle_fact_id FROM public.chat_event_lifecycle_fact WHERE event_id=? AND milestone_kind=? AND source_fact_id=?",
        UUID.class,
        eventId,
        kind,
        milestone.sourceFactId());
  }

  private void ensureState(UUID eventId) {
    jdbc.update(
        "INSERT INTO public.chat_event_lifecycle_state(event_id) VALUES (?) ON CONFLICT DO NOTHING",
        eventId);
  }

  private State lockState(UUID eventId) {
    return jdbc.queryForObject(
        "SELECT state, deadline_at, lifecycle_fact_id FROM public.chat_event_lifecycle_state WHERE event_id=? FOR UPDATE",
        (rs, row) ->
            new State(
                rs.getString("state"),
                rs.getTimestamp("deadline_at") == null
                    ? null
                    : rs.getTimestamp("deadline_at").toInstant(),
                rs.getObject("lifecycle_fact_id", UUID.class)),
        eventId);
  }

  private void close(UUID eventId, UUID lifecycleFactId, Instant now) {
    jdbc.update(
        "INSERT INTO public.chat_event_lifecycle_close(event_id,lifecycle_fact_id,closed_at,recorded_at) VALUES (?,?,?,?) ON CONFLICT (event_id) DO NOTHING",
        eventId,
        lifecycleFactId,
        Timestamp.from(now),
        Timestamp.from(now));
    jdbc.update(
        "UPDATE public.chat_event_lifecycle_state s SET state='CLOSED',closed_at=c.closed_at,lifecycle_fact_id=c.lifecycle_fact_id,milestone_kind=f.milestone_kind,milestone_at=f.milestone_at,deadline_at=f.deadline_at,rebuilt_at=? FROM public.chat_event_lifecycle_close c JOIN public.chat_event_lifecycle_fact f ON f.lifecycle_fact_id=c.lifecycle_fact_id WHERE s.event_id=c.event_id AND s.event_id=?",
        Timestamp.from(now),
        eventId);
    throw closed();
  }

  private ApiProblemException closed() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }

  private record State(String state, Instant deadlineAt, UUID lifecycleFactId) {}
}
