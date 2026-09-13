package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionAcceptancePort;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/me/predictions")
public class PredictionController {
  private final PredictionAcceptancePort acceptance;
  private final OperationalIdentityLookup identities;
  private final PredictionRepository repository;
  public PredictionController(PredictionAcceptancePort acceptance, OperationalIdentityLookup identities, PredictionRepository repository) { this.acceptance = acceptance; this.identities = identities; this.repository = repository; }
  @PostMapping
  public ResponseEntity<?> post(@AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody PredictionModels.PredictionSubmitRequest request) {
    UUID id = resolve(user); if (id == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    if (key == null || key.isBlank()) return ResponseEntity.badRequest().build();
    return ResponseEntity.status(HttpStatus.CREATED).body(acceptance.accept(id, request.toCommand(key)));
  }
  @GetMapping("/markets/{marketId}")
  public ResponseEntity<?> get(@AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user, @PathVariable UUID marketId) {
    UUID id = resolve(user); if (id == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    return repository.findByMarket(id, marketId).<ResponseEntity<?>>map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }
  private UUID resolve(OidcUser user) { if (user == null || user.getIssuer() == null || user.getSubject() == null) return null; return identities.findIdentityId(user.getIssuer().toString(), user.getSubject()).orElse(null); }
}
