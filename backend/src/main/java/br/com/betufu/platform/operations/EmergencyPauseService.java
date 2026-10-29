package br.com.betufu.platform.operations;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class EmergencyPauseService implements EmergencyPausePort {
  private final Persistence repository;
  private final OperationalIdentityLookup identities;
  private final ObjectProvider<SessionRepository<?>> sessions;
  private final Map<Kind, TargetLookup> targets;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public EmergencyPauseService(
      Persistence repository,
      OperationalIdentityLookup identities,
      ObjectProvider<SessionRepository<?>> sessions,
      List<TargetLookup> targets,
      Clock clock,
      ApplicationEventPublisher events) {
    this.repository = repository;
    this.identities = identities;
    this.sessions = sessions;
    this.clock = clock;
    this.events = events;
    var mappings = new EnumMap<Kind, TargetLookup>(Kind.class);
    for (var lookup : targets)
      if (mappings.put(lookup.kind(), lookup) != null)
        throw new IllegalArgumentException("duplicate emergency target provider");
    this.targets = Map.copyOf(mappings);
  }

  @Transactional
  public Receipt command(String issuer, String subject, String sessionId, Command command) {
    validate(command);
    UUID actor = administrator(issuer, subject, sessionId);
    repository.lockActor(actor);
    requireTarget(command.kind(), command.targetId());
    State state = lockState(command.kind(), command.targetId());
    administrator(issuer, subject, sessionId);
    Target target = requireTarget(command.kind(), command.targetId());
    var prior = repository.command(actor, command.clientCommandId());
    if (prior.isPresent()) {
      Receipt old = prior.get();
      if (old.kind() != command.kind()
          || !old.targetId().equals(command.targetId())
          || old.action() != command.action()
          || !old.reason().equals(command.reason())
          || !Objects.equals(old.resumedPauseId(), command.resumedPauseId())) throw conflict();
      return old;
    }
    if (command.action() == Action.PAUSE
        ? state.paused()
        : !state.paused() || !state.currentPauseId().equals(command.resumedPauseId()))
      throw conflict();
    Receipt fact =
        repository.insert(actor, command, clock.instant().truncatedTo(ChronoUnit.MICROS));
    repository.project(
        command.kind(),
        command.targetId(),
        command.action() == Action.PAUSE ? new State(true, fact.factId()) : new State(false, null));
    events.publishEvent(new Changed(command.kind(), command.targetId(), target.relatedEventId()));
    return fact;
  }

  @Transactional(readOnly = true)
  public List<Receipt> list(String issuer, String subject, String sessionId) {
    administrator(issuer, subject, sessionId);
    return repository.active();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public State lockState(Kind kind, UUID targetId) {
    repository.lockTarget(kind, targetId);
    State state = state(kind, targetId);
    repository.project(kind, targetId, state);
    return state;
  }

  @Override
  @Transactional(readOnly = true)
  public State state(Kind kind, UUID targetId) {
    var last = repository.latest(kind, targetId);
    return last.isPresent() && last.get().action() == Action.PAUSE
        ? new State(true, last.get().factId())
        : new State(false, null);
  }

  private Target requireTarget(Kind kind, UUID targetId) {
    var lookup = targets.get(kind);
    Target target = lookup == null ? null : lookup.find(targetId).orElse(null);
    if (target == null) throw problem(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    if (target.relatedEventId() != null) {
      var events = targets.get(Kind.EVENT);
      if (events == null || events.find(target.relatedEventId()).isEmpty())
        throw problem(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }
    return target;
  }

  private UUID administrator(String issuer, String subject, String sessionId) {
    var sessionRepository = sessions.getIfAvailable();
    var session =
        sessionRepository == null || sessionId == null
            ? null
            : sessionRepository.findById(sessionId);
    SecurityContext context =
        session == null ? null : session.getAttribute("SPRING_SECURITY_CONTEXT");
    var auth = context == null ? null : context.getAuthentication();
    if (session == null
        || session.isExpired()
        || !clock
            .instant()
            .isBefore(session.getLastAccessedTime().plus(session.getMaxInactiveInterval()))
        || auth == null
        || !auth.isAuthenticated()
        || !(auth.getPrincipal() instanceof OidcUser user)
        || !issuer.equals(user.getIssuer().toString())
        || !subject.equals(user.getSubject()))
      throw problem(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    var identity =
        identities
            .lookupIdentity(issuer, subject)
            .orElseThrow(() -> problem(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    if (identity.roles().stream()
        .noneMatch(
            role -> role.roleName().equals("ADMINISTRADOR_GERAL") && role.scopeRef() == null))
      throw problem(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    return identity.identityId();
  }

  private void validate(Command c) {
    if (c == null
        || c.clientCommandId() == null
        || c.kind() == null
        || c.targetId() == null
        || c.action() == null
        || c.reason() == null
        || c.reason().isBlank()
        || c.reason().length() > 280
        || (c.action() == Action.RESUME) != (c.resumedPauseId() != null))
      throw problem(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  static ApiProblemException conflict() {
    return problem(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }

  static ApiProblemException problem(HttpStatus status, ApiErrorCode code) {
    return new ApiProblemException(status, code);
  }

  @Repository
  @Profile("runtime | integrated")
  public static class Persistence {
    private final JdbcTemplate jdbc;

    public Persistence(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    void lockActor(UUID actor) {
      jdbc.query(
          "SELECT pg_advisory_xact_lock(hashtextextended(?,44))", rs -> {}, actor.toString());
    }

    void lockTarget(Kind kind, UUID target) {
      jdbc.update(
          "INSERT INTO emergency_pause_target(target_kind,target_id) VALUES (?,?) ON CONFLICT DO NOTHING",
          kind.name(),
          target);
      jdbc.query(
          "SELECT target_id FROM emergency_pause_target WHERE target_kind=? AND target_id=? FOR UPDATE",
          rs -> {},
          kind.name(),
          target);
    }

    Optional<Receipt> latest(Kind kind, UUID target) {
      return one(
          "SELECT * FROM emergency_pause_fact WHERE target_kind=? AND target_id=? ORDER BY change_no DESC LIMIT 1",
          kind.name(),
          target);
    }

    Optional<Receipt> command(UUID actor, UUID key) {
      return one(
          "SELECT * FROM emergency_pause_fact WHERE actor_identity_id=? AND client_command_id=?",
          actor,
          key);
    }

    List<Receipt> active() {
      return jdbc.query(
          "SELECT * FROM (SELECT DISTINCT ON(target_kind,target_id) * FROM emergency_pause_fact ORDER BY target_kind,target_id,change_no DESC) latest WHERE action='PAUSE' ORDER BY change_no",
          this::receipt);
    }

    private Optional<Receipt> one(String sql, Object... args) {
      return jdbc.query(sql, this::receipt, args).stream().findFirst();
    }

    Receipt insert(UUID actor, Command command, Instant now) {
      UUID id =
          jdbc.queryForObject(
              "INSERT INTO emergency_pause_fact(target_kind,target_id,actor_identity_id,action,reason,client_command_id,resumed_pause_id,recorded_at) VALUES (?,?,?,?,?,?,?,?) RETURNING pause_fact_id",
              UUID.class,
              command.kind().name(),
              command.targetId(),
              actor,
              command.action().name(),
              command.reason(),
              command.clientCommandId(),
              command.resumedPauseId(),
              Timestamp.from(now));
      return new Receipt(
          id,
          command.kind(),
          command.targetId(),
          command.action(),
          command.reason(),
          command.resumedPauseId(),
          now);
    }

    void project(Kind kind, UUID target, State state) {
      jdbc.update(
          "UPDATE emergency_pause_target SET paused=?,current_pause_id=? WHERE target_kind=? AND target_id=?",
          state.paused(),
          state.currentPauseId(),
          kind.name(),
          target);
    }

    private Receipt receipt(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
      return new Receipt(
          rs.getObject("pause_fact_id", UUID.class),
          Kind.valueOf(rs.getString("target_kind")),
          rs.getObject("target_id", UUID.class),
          Action.valueOf(rs.getString("action")),
          rs.getString("reason"),
          rs.getObject("resumed_pause_id", UUID.class),
          rs.getTimestamp("recorded_at").toInstant());
    }
  }
}
