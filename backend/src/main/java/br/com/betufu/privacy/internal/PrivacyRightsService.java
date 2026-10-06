package br.com.betufu.privacy.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.privacy.PrivacySubjectPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
class PrivacyRightsService {
  private final PrivacyRightsRepository repository;
  private final PrivacySubjectPort.Identity identities;
  private final PrivacySubjectPort.Sports sports;
  private final ObjectProvider<SessionRepository<?>> sessions;
  private final Clock clock;

  PrivacyRightsService(
      PrivacyRightsRepository repository,
      PrivacySubjectPort.Identity identities,
      PrivacySubjectPort.Sports sports,
      ObjectProvider<SessionRepository<?>> sessions,
      Clock clock) {
    this.repository = repository;
    this.identities = identities;
    this.sports = sports;
    this.sessions = sessions;
    this.clock = clock;
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
    // Execution/export evidence is supplied by 07-27; a decision alone cannot fulfill these rights.
    if ("APPROVE".equals(decision) && fact.requestKind() != PrivacyRightsModels.RequestKind.NOTICE)
      throw conflict();
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
