package br.com.betufu.privacy.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.privacy.PrivacyRestrictedRecordsPort;
import br.com.betufu.privacy.PrivacyRestrictedRecordsPort.DataClass;
import br.com.betufu.privacy.PrivacySubjectPort;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Profile("runtime | integrated")
class PrivacyRightsService {
  private final PrivacyRightsRepository repository;
  private final PrivacySubjectPort.Identity identities;
  private final PrivacySubjectPort.Sports sports;
  private final ObjectProvider<SessionRepository<?>> sessions;
  private final Clock clock;
  private final List<PrivacyRestrictedRecordsPort> producers;
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  PrivacyRightsService(
      PrivacyRightsRepository repository,
      PrivacySubjectPort.Identity identities,
      PrivacySubjectPort.Sports sports,
      ObjectProvider<SessionRepository<?>> sessions,
      Clock clock,
      List<PrivacyRestrictedRecordsPort> producers,
      JdbcTemplate jdbc,
      ObjectMapper mapper) {
    this.repository = repository;
    this.identities = identities;
    this.sports = sports;
    this.sessions = sessions;
    this.clock = clock;
    this.producers = List.copyOf(producers);
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Transactional
  PrivacyRightsModels.CaseReceipt request(
      String issuer, String subject, String sessionId, PrivacyRightsModels.RequestCommand command) {
    requireCurrentSession(issuer, subject, sessionId);
    UUID actor = authenticated(issuer, subject);
    validate(command);
    if (command.subjectKind() == PrivacyRightsModels.SubjectKind.ACCOUNT) {
      if (!"AUTHENTICATED_SESSION".equals(command.verifiedChannel())
          || command.subjectRef() != null
          || command.proofReference() != null) throw invalid();
    } else {
      if (!identities.isGeneralAdministrator(actor)
          || !"VERIFIED_OPERATOR_CHANNEL".equals(command.verifiedChannel())
          || command.subjectRef() == null
          || command.proofReference() == null
          || !sports.existsAthlete(command.subjectRef())) throw forbidden();
    }
    UUID owner = command.subjectKind() == PrivacyRightsModels.SubjectKind.ACCOUNT ? actor : null;
    var fact =
        repository.insertCase(
            command.subjectKind(),
            command.subjectRef(),
            owner,
            command.requestKind(),
            command.proofReference(),
            command.verifiedChannel());
    var event = repository.insertEvent(fact.caseId(), "REQUESTED", actor);
    repository.insertState(fact.caseId(), "PENDING", event.eventId());
    return new PrivacyRightsModels.CaseReceipt(fact.caseId(), "PENDING", fact.requestedAt());
  }

  @Transactional(readOnly = true)
  PrivacyRightsModels.CaseView view(String issuer, String subject, String sessionId, UUID caseId) {
    requireCurrentSession(issuer, subject, sessionId);
    UUID actor = authenticated(issuer, subject);
    var fact = repository.findCase(caseId).orElseThrow(PrivacyRightsService::missing);
    boolean owner = actor.equals(fact.ownerIdentityId());
    if (!owner && !identities.isGeneralAdministrator(actor)) throw forbidden();
    return caseView(fact);
  }

  @Transactional(readOnly = true)
  List<PrivacyRightsModels.CaseView> ownCases(
      String issuer, String subject, String sessionId, int limit, int offset) {
    requireCurrentSession(issuer, subject, sessionId);
    UUID actor = authenticated(issuer, subject);
    if (limit < 1 || limit > 100 || offset < 0) throw invalid();
    return repository.ownCases(actor, limit, offset).stream().map(this::caseView).toList();
  }

  private PrivacyRightsModels.CaseView caseView(PrivacyRightsRepository.CaseFact fact) {
    UUID caseId = fact.caseId();
    return new PrivacyRightsModels.CaseView(
        fact.caseId(),
        fact.subjectKind(),
        fact.requestKind(),
        repository.historicalState(caseId),
        fact.requestedAt(),
        repository.timeline(caseId).stream()
            .map(
                item ->
                    new PrivacyRightsModels.TimelineItem(
                        item.eventKind(), item.occurredAt(), item.responseText()))
            .toList());
  }

  @Transactional
  PrivacyRightsModels.ResponseReceipt respond(
      String issuer,
      String subject,
      String sessionId,
      UUID caseId,
      String decision,
      String responseText) {
    requireCurrentSession(issuer, subject, sessionId);
    UUID actor = authenticated(issuer, subject);
    if (!identities.isGeneralAdministrator(actor)) throw forbidden();
    if (!"APPROVE".equals(decision) && !"REJECT".equals(decision) && !"ANALYZE".equals(decision))
      throw invalid();
    if (responseText == null
        || responseText.isBlank()
        || responseText.length() > 2000
        || responseText.indexOf('\0') >= 0) throw invalid();
    var fact = repository.lockCase(caseId).orElseThrow(PrivacyRightsService::missing);
    requireCurrentSession(issuer, subject, sessionId);
    if (!identities.isGeneralAdministrator(actor)) throw forbidden();
    String previousState = repository.historicalState(caseId);
    if ("RESPONDED".equals(previousState) || "REJECTED".equals(previousState)) throw conflict();
    // A partial artifact never fulfills a complete access request; other rights need disposition
    // evidence.
    boolean completeExport =
        fact.requestKind() == PrivacyRightsModels.RequestKind.ACCESS
            && Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "SELECT EXISTS(SELECT FROM privacy_rights_export_fact f JOIN privacy_restricted_export_payload p ON p.export_id=f.export_id WHERE f.case_id=? AND p.complete)",
                    Boolean.class,
                    caseId));
    if ("APPROVE".equals(decision)
        && fact.requestKind() != PrivacyRightsModels.RequestKind.NOTICE
        && !completeExport) throw conflict();
    String state =
        "ANALYZE".equals(decision)
            ? "UNDER_REVIEW"
            : "APPROVE".equals(decision) ? "RESPONDED" : "REJECTED";
    String eventKind =
        "ANALYZE".equals(decision)
            ? "ANALYZED"
            : "APPROVE".equals(decision) ? "RESPONSE_SENT" : "REJECTED";
    var event = repository.insertResponseEvent(fact.caseId(), eventKind, actor, responseText);
    repository.updateState(fact.caseId(), state, event.eventId());
    return new PrivacyRightsModels.ResponseReceipt(
        fact.caseId(), state, eventKind, event.occurredAt());
  }

  record ExportArtifact(
      UUID exportId, Map<String, Object> data, List<String> blockedClasses, boolean complete) {}

  @Transactional
  ExportArtifact prepareExport(String issuer, String subject, String sessionId, UUID caseId) {
    UUID actor = authorizeCase(issuer, subject, sessionId, caseId, true);
    var fact = repository.lockCase(caseId).orElseThrow(PrivacyRightsService::missing);
    requireCurrentSession(issuer, subject, sessionId);
    if (!identities.isGeneralAdministrator(actor)) throw forbidden();
    if (fact.requestKind() != PrivacyRightsModels.RequestKind.ACCESS
        || "RESPONDED".equals(repository.historicalState(caseId))
        || "REJECTED".equals(repository.historicalState(caseId))) throw conflict();
    var verified =
        new PrivacyRestrictedRecordsPort.Subject(fact.ownerIdentityId(), fact.subjectRef());
    Map<String, Object> data = new java.util.TreeMap<>();
    List<String> blocked = new java.util.ArrayList<>();
    for (DataClass type : DataClass.values()) {
      var candidates = producers.stream().filter(p -> p.supportedClasses().contains(type)).toList();
      if (type == DataClass.RIGHTS) {
        data.put(
            type.name(),
            jdbc.query(
                "SELECT case_id,request_kind,requested_at FROM privacy_rights_case_fact WHERE (owner_identity_id=? OR subject_ref=?) ORDER BY requested_at,case_id",
                (rs, row) ->
                    new PrivacyRestrictedRecordsPort.RestrictedRecord(
                        rs.getString(1),
                        Map.of(
                            "kind",
                            rs.getString(2),
                            "requestedAt",
                            rs.getTimestamp(3).toInstant().toString())),
                verified.identityId(),
                verified.athleteId()));
      } else if (candidates.size() == 1)
        data.put(type.name(), candidates.getFirst().export(verified, type));
      else blocked.add(type.name());
    }
    Map<String, Object> artifact =
        Map.of("data", data, "blockedClasses", blocked, "complete", blocked.isEmpty());
    String json = mapper.writeValueAsString(artifact);
    String digest = PrivacyRetentionRegistry.sha256(json);
    String channel =
        fact.subjectKind() == PrivacyRightsModels.SubjectKind.ACCOUNT
            ? "AUTHENTICATED_SESSION"
            : "VERIFIED_OPERATOR_CHANNEL";
    UUID exportId =
        jdbc.queryForObject(
            "INSERT INTO privacy_rights_export_fact(case_id,format,artifact_digest,verified_channel) VALUES (?,'JSON',?,?) RETURNING export_id",
            UUID.class,
            caseId,
            digest,
            channel);
    jdbc.update(
        "INSERT INTO privacy_restricted_export_payload(export_id,artifact,complete) VALUES (?,CAST(? AS jsonb),?)",
        exportId,
        json,
        blocked.isEmpty());
    repository.insertEvent(caseId, "EXPORT_READY", actor);
    return new ExportArtifact(exportId, Map.copyOf(data), List.copyOf(blocked), blocked.isEmpty());
  }

  @Transactional(readOnly = true)
  ExportArtifact downloadExport(String issuer, String subject, String sessionId, UUID caseId) {
    authorizeCase(issuer, subject, sessionId, caseId, false);
    return jdbc
        .query(
            "SELECT f.export_id,f.artifact_digest,p.artifact::text,p.complete FROM privacy_rights_export_fact f JOIN privacy_restricted_export_payload p ON p.export_id=f.export_id WHERE f.case_id=? ORDER BY f.created_at DESC,f.export_id DESC LIMIT 1",
            (rs, row) -> {
              var node = mapper.readTree(rs.getString(3));
              Map<String, Object> data =
                  mapper.convertValue(
                      node.get("data"),
                      new tools.jackson.core.type.TypeReference<Map<String, Object>>() {});
              List<String> blocked =
                  mapper.convertValue(
                      node.get("blockedClasses"),
                      new tools.jackson.core.type.TypeReference<List<String>>() {});
              for (var item : new java.util.ArrayList<>(data.entrySet())) {
                DataClass type = DataClass.valueOf(item.getKey());
                var producer =
                    producers.stream().filter(p -> p.supportedClasses().contains(type)).findFirst();
                if (type != DataClass.RIGHTS
                    && (producer.isEmpty()
                        || producers.stream()
                                .filter(p -> p.supportedClasses().contains(type))
                                .count()
                            != 1)) throw conflict();
                if (producer.isPresent()) {
                  List<PrivacyRestrictedRecordsPort.RestrictedRecord> records =
                      mapper.convertValue(
                          item.getValue(),
                          new tools.jackson.core.type.TypeReference<
                              List<PrivacyRestrictedRecordsPort.RestrictedRecord>>() {});
                  data.put(
                      item.getKey(),
                      records.stream().map(r -> producer.get().redactForServing(type, r)).toList());
                }
              }
              return new ExportArtifact(
                  rs.getObject(1, UUID.class), data, blocked, rs.getBoolean(4));
            },
            caseId)
        .stream()
        .findFirst()
        .orElseThrow(PrivacyRightsService::missing);
  }

  private UUID authorizeCase(
      String issuer, String subject, String sessionId, UUID caseId, boolean adminOnly) {
    requireCurrentSession(issuer, subject, sessionId);
    UUID actor = authenticated(issuer, subject);
    var fact = repository.findCase(caseId).orElseThrow(PrivacyRightsService::missing);
    if ((adminOnly || !actor.equals(fact.ownerIdentityId()))
        && !identities.isGeneralAdministrator(actor)) throw forbidden();
    return actor;
  }

  private void requireCurrentSession(String issuer, String subject, String sessionId) {
    var repository = sessions.getIfAvailable();
    var session = repository == null || sessionId == null ? null : repository.findById(sessionId);
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
        || !user.getIssuer().toString().equals(issuer)
        || !user.getSubject().equals(subject))
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static ApiProblemException conflict() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.VALIDATION_FAILED);
  }

  private UUID authenticated(String issuer, String subject) {
    if (issuer == null || subject == null)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    return identities
        .findAuthenticatedIdentity(issuer, subject)
        .orElseThrow(
            () -> new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED));
  }

  private static void validate(PrivacyRightsModels.RequestCommand command) {
    if (command == null || command.subjectKind() == null || command.requestKind() == null)
      throw invalid();
    if (command.verifiedChannel() == null || command.verifiedChannel().isBlank()) throw invalid();
    if (command.subjectKind() == PrivacyRightsModels.SubjectKind.ATHLETE_WITHOUT_ACCOUNT
        && (command.proofReference() == null || command.proofReference().isBlank()))
      throw invalid();
  }

  private static ApiProblemException invalid() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException forbidden() {
    return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }

  private static ApiProblemException missing() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }
}
