package br.com.betufu.ledger.internal;

import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.ledger.CreditCorrectionModels.View;
import br.com.betufu.ledger.CreditCorrectionPort;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class CreditCorrectionController {
  static final String OWNER_PATH = "/api/v1/me/credit-corrections";
  static final String OPERATIONS_PATH = "/api/v1/operations/credit-corrections";
  static final String APPROVE_PATH = "/api/v1/operations/credit-corrections/{caseId}/approve";
  static final String REJECT_PATH = "/api/v1/operations/credit-corrections/{caseId}/reject";

  private final CreditCorrectionPort corrections;
  private final OperationalIdentityLookup identities;

  public CreditCorrectionController(
      CreditCorrectionPort corrections, OperationalIdentityLookup identities) {
    this.corrections = corrections;
    this.identities = identities;
  }

  @GetMapping(path = OWNER_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<CreditCorrectionPage> mine(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    return ResponseEntity.ok(new CreditCorrectionPage(corrections.listForOwner(resolve(user))));
  }

  @PostMapping(
      path = OPERATIONS_PATH,
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<View> propose(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CreditCorrectionRequest request) {
    UUID maker = resolve(user);
    View view =
        corrections.propose(
            maker,
            request.identityId(),
            request.kind(),
            request.reason(),
            request.amount(),
            request.evidenceType(),
            request.evidenceId(),
            request.eligibilityContextId(),
            request.supersedesCaseId(),
            request.description(),
            request.calculation(),
            idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(view);
  }

  @PostMapping(path = APPROVE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<View> approve(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID caseId) {
    return ResponseEntity.ok(corrections.approve(resolve(user), caseId));
  }

  @PostMapping(path = REJECT_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<View> reject(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user,
      @PathVariable UUID caseId) {
    return ResponseEntity.ok(corrections.reject(resolve(user), caseId));
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

  @ExceptionHandler({
    SecurityException.class,
    java.util.NoSuchElementException.class,
    IllegalArgumentException.class,
    IllegalStateException.class
  })
  public ResponseEntity<org.springframework.http.ProblemDetail> rejection(
      RuntimeException failure) {
    HttpStatus status =
        failure instanceof SecurityException
            ? HttpStatus.FORBIDDEN
            : failure instanceof java.util.NoSuchElementException
                ? HttpStatus.NOT_FOUND
                : failure instanceof IllegalArgumentException
                    ? HttpStatus.BAD_REQUEST
                    : HttpStatus.CONFLICT;
    ApiErrorCode code =
        status == HttpStatus.CONFLICT
            ? ApiErrorCode.STALE_VERSION
            : ApiErrorCode.fromStatus(status.value());
    return new br.com.betufu.platform.api.ProblemDetailsHandler()
        .handleApiProblem(new ApiProblemException(status, code));
  }

  public record CreditCorrectionPage(List<View> items) {
    public CreditCorrectionPage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }

  public record CreditCorrectionRequest(
      @NotNull UUID identityId,
      @NotNull Kind kind,
      @NotNull Reason reason,
      @NotNull @DecimalMin("0.01") BigDecimal amount,
      @NotNull Type evidenceType,
      @NotNull UUID evidenceId,
      UUID eligibilityContextId,
      UUID supersedesCaseId,
      @NotBlank @Size(max = 512) String description,
      @NotBlank @Size(max = 1024) String calculation) {}
}
