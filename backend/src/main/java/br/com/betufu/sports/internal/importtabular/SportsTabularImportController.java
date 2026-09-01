package br.com.betufu.sports.internal.importtabular;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.internal.importtabular.SportsTabularImportService.SportsTabularImportPreview;
import br.com.betufu.sports.internal.importtabular.SportsTabularImportService.SportsTabularImportResult;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/operations/sports/editions/{editionId}/tabular-import")
@Profile("runtime | integrated")
public class SportsTabularImportController {

  private final SportsTabularImportService service;

  @Autowired
  public SportsTabularImportController(SportsTabularImportService service) {
    this.service = Objects.requireNonNull(service, "service must not be null");
  }

  @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public SportsTabularImportPreview validate(
      @PathVariable("editionId") UUID editionId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    if (file == null || file.isEmpty()) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    String issuer = principal.getIssuer() != null ? principal.getIssuer().toString() : null;
    String subject = principal.getSubject();

    return service.validateImport(issuer, subject, editionId, bytes, file.getOriginalFilename());
  }

  @PostMapping("/commit")
  public SportsTabularImportResult commit(
      @PathVariable("editionId") UUID editionId,
      @RequestParam("importId") UUID importId,
      @AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    if (importId == null) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    String issuer = principal.getIssuer() != null ? principal.getIssuer().toString() : null;
    String subject = principal.getSubject();

    return service.commitImport(issuer, subject, editionId, importId);
  }

  @GetMapping("/status/{importId}")
  public SportsTabularImportResult getStatus(
      @PathVariable("editionId") UUID editionId,
      @PathVariable("importId") UUID importId,
      @AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    if (importId == null) {
      throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    String issuer = principal.getIssuer() != null ? principal.getIssuer().toString() : null;
    String subject = principal.getSubject();

    return service.getImportStatus(issuer, subject, editionId, importId);
  }
}
