package br.com.betufu.ledger.internal;

import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.internal.CreditBalanceModels.CreditBalanceResponse;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
@RequestMapping("/api/v1/me/credits")
public class CreditBalanceController {
  private final CreditLedgerPort ledger;
  private final OperationalIdentityLookup identities;

  public CreditBalanceController(CreditLedgerPort ledger, OperationalIdentityLookup identities) {
    this.ledger = ledger;
    this.identities = identities;
  }

  @GetMapping
  public ResponseEntity<CreditBalanceResponse> get(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    UUID id = resolve(user);
    if (id == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    var balance = ledger.getBalance(id);
    return ResponseEntity.ok(
        new CreditBalanceResponse(
            balance.balance(), 2, CreditLedgerPort.MINIMUM_STAKE, "credits-v1"));
  }

  private UUID resolve(OidcUser user) {
    if (user == null || user.getIssuer() == null || user.getSubject() == null) return null;
    return identities.findIdentityId(user.getIssuer().toString(), user.getSubject()).orElse(null);
  }
}
