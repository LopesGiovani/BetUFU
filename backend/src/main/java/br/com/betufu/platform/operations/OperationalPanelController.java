package br.com.betufu.platform.operations;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller operacional para o painel de operações governadas (OPER-06 / D-30 / D-48 / S4).
 *
 * <p>Endpoint: {@code GET /api/v1/operations/panel}.
 *
 * <p>A autorização é governada na cadeia de segurança principal para permitir qualquer um dos três
 * papéis operacionais (ADMINISTRADOR_GERAL, ADMINISTRADOR_COMPETICAO, MODERADOR). A decisão de
 * quais blocos são incluídos na resposta é realizada no {@link OperationalPanelService}, garantindo
 * que o moderador receba uma resposta válida com conjunto vazio de escopo (e não recusa de
 * autorização).
 *
 * <p>A consulta é leitura pura e não renova o prazo de inatividade da sessão.
 */
@RestController
@Profile("runtime | integrated")
public class OperationalPanelController {

  public static final String PANEL_PATH = "/api/v1/operations/panel";

  private final OperationalPanelService panelService;

  public OperationalPanelController(OperationalPanelService panelService) {
    this.panelService = Objects.requireNonNull(panelService, "panelService must not be null");
  }

  @GetMapping(path = PANEL_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<OperationalPanelResponse> getPanel(
      @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
    if (oidcUser == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }

    String issuer = extractIssuer(oidcUser);
    String subject = extractSubject(oidcUser);

    if (issuer == null || subject == null) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }

    OperationalPanelResponse response = panelService.getPanel(issuer, subject);
    return ResponseEntity.ok(response);
  }

  private static String extractIssuer(OidcUser oidcUser) {
    Object value = oidcUser.getClaims().get(IdTokenClaimNames.ISS);
    String text =
        switch (value) {
          case String s -> s;
          case URL u -> u.toString();
          case URI u -> u.toString();
          case null, default -> null;
        };
    return (text != null && !text.isBlank()) ? text : null;
  }

  private static String extractSubject(OidcUser oidcUser) {
    Object value = oidcUser.getClaims().get(IdTokenClaimNames.SUB);
    return (value instanceof String s && !s.isBlank()) ? s : null;
  }
}
