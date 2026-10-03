package br.com.betufu.ranking.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.ranking.AchievementQueryPort;
import br.com.betufu.ranking.AchievementQueryPort.Achievement;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("runtime | integrated")
public class AchievementController {
  static final String OWNER_ACHIEVEMENTS_PATH = "/api/v1/me/achievements";

  private final AchievementQueryPort achievements;
  private final OperationalIdentityLookup identities;

  public AchievementController(
      AchievementQueryPort achievements, OperationalIdentityLookup identities) {
    this.achievements = achievements;
    this.identities = identities;
  }

  @GetMapping(path = OWNER_ACHIEVEMENTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AchievementPage> mine(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser user) {
    return ResponseEntity.ok(
        new AchievementPage(
            achievements.findByIdentity(resolve(user)).stream()
                .map(
                    item ->
                        new Achievement(
                            item.awardId(),
                            item.code(),
                            item.name(),
                            item.description(),
                            item.rule(),
                            item.catalogEdition(),
                            item.sportsEditionId(),
                            item.state(),
                            item.awardedAt(),
                            item.revokedAt(),
                            item.state() == AchievementQueryPort.AwardState.REVOKED
                                ? "CORRECTED_SETTLEMENT_FACT"
                                : null))
                .toList()));
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

  public record AchievementPage(List<Achievement> items) {
    public AchievementPage {
      items = items == null ? List.of() : List.copyOf(items);
    }
  }
}
