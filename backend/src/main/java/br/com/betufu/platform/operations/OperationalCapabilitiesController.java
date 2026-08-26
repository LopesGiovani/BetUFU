package br.com.betufu.platform.operations;

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

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;

/**
 * Controller operacional para consulta de capacidades operacionais atuais do ator autenticado
 * (OPER-01, OPER-02, OPER-04, IDEN-05, D-46).
 *
 * <p>Endpoint: {@code GET /api/v1/operations/capabilities}.
 *
 * <p>O endpoint é protegido pelo matcher amplo autenticado {@code /api/v1/**} em
 * {@code SecurityConfiguration}, sem regras de autorização customizadas: qualquer usuário
 * autenticado com sessão válida pode consultar suas próprias capacidades. A resposta é fail-closed
 * e devolve lista vazia para identidades sem papéis operacionais.
 *
 * <p>As capacidades retornadas servem exclusivamente como auxílio de navegação e renderização
 * de menu (S7), permanecendo os endpoints de negócio como autoridade final com seus respectivos 403.
 */
@RestController
@Profile("runtime | integrated")
public class OperationalCapabilitiesController {

    public static final String CAPABILITIES_PATH = "/api/v1/operations/capabilities";

    private final OperationalCapabilitiesService capabilitiesService;

    public OperationalCapabilitiesController(OperationalCapabilitiesService capabilitiesService) {
        this.capabilitiesService = Objects.requireNonNull(capabilitiesService, "capabilitiesService must not be null");
    }

    @GetMapping(path = CAPABILITIES_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OperationalCapabilitiesResponse> getCapabilities(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }

        String issuer = extractIssuer(oidcUser);
        String subject = extractSubject(oidcUser);

        if (issuer == null || subject == null) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }

        OperationalCapabilitiesResponse response = capabilitiesService.getCapabilities(issuer, subject);
        return ResponseEntity.ok(response);
    }

    private static String extractIssuer(OidcUser oidcUser) {
        Object value = oidcUser.getClaims().get(IdTokenClaimNames.ISS);
        String text = switch (value) {
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
