package br.com.betufu.settlement.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.settlement.SettlementProcessPort;
import br.com.betufu.settlement.SettlementReadPort;
import br.com.betufu.settlement.SettlementReadPort.AuthorizationCandidate;
import br.com.betufu.settlement.SettlementReadPort.CaseProgress;
import br.com.betufu.settlement.SettlementReadPort.HistoryItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class SettlementController {
  static final String OWNER_HISTORY_PATH = "/api/v1/me/settlements";
  static final String OPERATIONS_CANDIDATES_PATH = "/api/v1/operations/settlements/candidates";
  static final String OPERATIONS_AUTHORIZE_PATH =
      "/api/v1/operations/settlements/events/{eventId}/authorize";
  static final String OPERATIONS_CASE_PATH = "/api/v1/operations/settlements/cases/{caseId}";

  private final SettlementReadPort reads;
  private final SettlementProcessPort processes;
  private final OperationalIdentityLookup identities;

  public SettlementController(
      SettlementReadPort reads,
      SettlementProcessPort processes,
      OperationalIdentityLookup identities) {
    this.reads = reads;
    this.processes = processes;
    this.identities = identities;
  }

  @GetMapping(path = OWNER_HISTORY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SettlementHistoryPage> history(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    UUID identityId = resolve(user);
    List<SettlementHistoryItem> items =
        reads.findHistoryByIdentity(identityId).stream().map(SettlementHistoryItem::from).toList();
    return ResponseEntity.ok(new SettlementHistoryPage(items));
  }

  @GetMapping(path = OPERATIONS_CANDIDATES_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SettlementAuthorizationCandidatePage> candidates(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    requireAdministrator(resolve(user));
    return ResponseEntity.ok(
        new SettlementAuthorizationCandidatePage(
            reads.findAuthorizationCandidates().stream()
                .map(SettlementAuthorizationCandidateItem::from)
                .toList()));
  }

  @PostMapping(
      path = OPERATIONS_AUTHORIZE_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SettlementAuthorizationResponse> authorize(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID eventId,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody SettlementAuthorizationRequest request) {
    UUID identityId = resolve(user);
    requireAdministrator(identityId);
    try {
      UUID authorizationId =
          processes.authorize(
              eventId,
              request.expectedOutcomeFactId(),
              request.expectedConfirmedVersionId(),
              identityId,
              idempotencyKey);
      UUID caseId = processes.process(authorizationId);
      CaseProgress progress =
          reads
              .findCase(caseId)
              .orElseThrow(
                  () -> new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND));
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(new SettlementAuthorizationResponse(CaseProgressDto.from(progress)));
    } catch (SecurityException ex) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    } catch (IllegalStateException ex) {
      throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    } catch (IllegalArgumentException ex) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }
  }

  @GetMapping(path = OPERATIONS_CASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CaseProgressDto> caseProgress(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID caseId) {
    requireAdministrator(resolve(user));
    return reads
        .findCase(caseId)
        .map(CaseProgressDto::from)
        .map(ResponseEntity::ok)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND));
  }

  private void requireAdministrator(UUID identityId) {
    if (!identities.hasGlobalRole(identityId, "ADMINISTRADOR_GERAL")) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }
  }

  private UUID resolve(OidcUser user) {
    if (user == null || user.getIssuer() == null || user.getSubject() == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return identities
        .findIdentityId(user.getIssuer().toString(), user.getSubject())
        .orElseThrow(
            () -> new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED));
  }

  public record SettlementHistoryPage(List<SettlementHistoryItem> items) {
    public SettlementHistoryPage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record SettlementHistoryItem(
      UUID predictionId,
      UUID eventId,
      UUID marketId,
      Instant acceptedAt,
      String selection,
      BigDecimal stake,
      BigDecimal acceptedOdds,
      BigDecimal potentialReturn,
      UUID settlementId,
      String state,
      String reason,
      String ruleVersion,
      BigDecimal amount,
      String outcomeKind,
      Integer homeScore,
      Integer awayScore,
      Integer winnerSlotNo,
      UUID ledgerEntryId,
      boolean current,
      String processingStatus) {
    static SettlementHistoryItem from(HistoryItem item) {
      return new SettlementHistoryItem(
          item.predictionId(),
          item.eventId(),
          item.marketId(),
          item.acceptedAt(),
          item.selection(),
          item.stake(),
          item.acceptedOdds(),
          item.potentialReturn(),
          item.settlementId(),
          item.state().name(),
          item.reason(),
          item.ruleVersion(),
          item.amount(),
          item.outcomeKind(),
          item.homeScore(),
          item.awayScore(),
          item.winnerSlotNo(),
          item.ledgerEntryId(),
          item.current(),
          item.processingStatus());
    }
  }

  public record SettlementAuthorizationCandidatePage(
      List<SettlementAuthorizationCandidateItem> items) {
    public SettlementAuthorizationCandidatePage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record SettlementAuthorizationCandidateItem(
      UUID eventId,
      String eventTitle,
      Instant scheduledAt,
      UUID outcomeFactId,
      UUID confirmedVersionId,
      String outcomeKind,
      Integer homeScore,
      Integer awayScore,
      Integer winnerSlotNo,
      boolean alreadyAuthorized,
      CaseProgressDto caseProgress) {
    static SettlementAuthorizationCandidateItem from(AuthorizationCandidate item) {
      return new SettlementAuthorizationCandidateItem(
          item.eventId(),
          item.eventTitle(),
          item.scheduledAt(),
          item.outcomeFactId(),
          item.confirmedVersionId(),
          item.outcomeKind(),
          item.homeScore(),
          item.awayScore(),
          item.winnerSlotNo(),
          item.alreadyAuthorized(),
          item.caseProgress() == null ? null : CaseProgressDto.from(item.caseProgress()));
    }
  }

  public record SettlementAuthorizationRequest(
      @NotNull UUID expectedOutcomeFactId, UUID expectedConfirmedVersionId) {}

  public record SettlementAuthorizationResponse(CaseProgressDto caseProgress) {}

  public record CaseProgressDto(UUID caseId, String status, int totalUnits, int completedUnits) {
    static CaseProgressDto from(CaseProgress progress) {
      return new CaseProgressDto(
          progress.caseId(),
          progress.status().name(),
          progress.totalUnits(),
          progress.completedUnits());
    }
  }
}
