package br.com.betufu.sports.internal.outcome;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.AdministrativeOutcomeRequest;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeFact;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeKind;
import br.com.betufu.sports.internal.outcome.SportsOutcomeRepository.EventScope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SportsOutcomeService {
  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
  private final SportsOutcomeRepository repository;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionTemplate transactions;

  SportsOutcomeService(
      SportsOutcomeRepository repository,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository);
    this.identityLookup = Objects.requireNonNull(identityLookup);
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  public OutcomeFact recordAdministrativeOutcome(
      String issuer,
      String subject,
      UUID eventId,
      Long expectedLockVersion,
      AdministrativeOutcomeRequest request) {
    if (expectedLockVersion == null
        || expectedLockVersion < 0
        || request == null
        || request.outcomeKind() == null
        || request.reason() == null
        || request.reason().isBlank()
        || request.sourceReference() == null
        || request.sourceReference().isBlank()
        || (request.outcomeKind() == OutcomeKind.ABANDONED_NO_VALID_RESULT
            && request.winnerSlotNo() != null)) {
      throw validation();
    }
    requireLength(request.reason().trim(), 1024);
    requireLength(request.sourceReference().trim(), 2048);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    return transactions.execute(
        status -> {
          EventScope event =
              repository.findEventForUpdate(eventId).orElseThrow(SportsOutcomeService::notFound);
          requireWritable(actor, event.competitionId());
          if (event.format().name().equals("GENERIC")
              || "RASCUNHO".equals(event.status())
              || "CANCELADO".equals(event.status())) throw validation();
          if (event.lockVersion() != expectedLockVersion) throw staleVersion();
          Optional<OutcomeFact> head = repository.findHead(eventId);
          OutcomeFact fact =
              repository.insertAdministrativeFact(
                  event,
                  repository.nextVersion(eventId),
                  request.outcomeKind(),
                  request.sourceReference(),
                  request.reason(),
                  actor.identityId(),
                  Instant.now(),
                  head.map(OutcomeFact::factId).orElse(null),
                  request.winnerSlotNo());
          if (repository.advanceEvent(eventId, expectedLockVersion) == 0) throw staleVersion();
          return fact;
        });
  }

  private OperationalIdentity resolveIdentity(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    return identityLookup
        .lookupIdentity(issuer, subject)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
  }

  private static void requireWritable(OperationalIdentity actor, UUID competitionId) {
    boolean allowed =
        actor.roles().stream()
            .anyMatch(
                role ->
                    (GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null)
                        || (COMPETITION_ADMIN.equals(role.roleName())
                            && competitionId.equals(role.scopeRef())));
    if (!allowed) throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }

  private static void requireLength(String value, int max) {
    if (value.getBytes(StandardCharsets.UTF_8).length > max) throw validation();
  }

  private static ApiProblemException validation() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException notFound() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }

  private static ApiProblemException staleVersion() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }
}
