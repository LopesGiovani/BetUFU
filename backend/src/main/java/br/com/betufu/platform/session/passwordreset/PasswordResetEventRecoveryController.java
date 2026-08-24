package br.com.betufu.platform.session.passwordreset;

import java.net.URI;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller operacional para recuperação e reprocessamento administrativo de eventos FAILED
 * do inbox de redefinição de senha (D-40 / ASVS L1 / OPER-06).
 *
 * <p><strong>Duas guardas de segurança independentes:</strong>
 * <ol>
 *   <li>Guarda 1 (Filtro): O prefixo {@code /api/v1/operations/password-reset-events/**} é protegido
 *       na cadeia de segurança principal com {@code .hasRole("ADMINISTRADOR_GERAL")};
 *   <li>Guarda 2 (Controller): Este controller consulta diretamente o contrato {@link OperationalIdentityLookup}
 *       do platform em tempo de execução, exigindo concessão global ativa de {@code ADMINISTRADOR_GERAL}
 *       ({@code scopeRef == null}). Concessões escopadas são estritamente rejeitadas.
 * </ol>
 *
 * <p><strong>Isolamento Arquitetural:</strong>
 * Consome exclusivamente o contrato consumidor {@link OperationalIdentityLookup} pertencente ao módulo
 * {@code platform}. Nenhuma classe do módulo {@code identity} é importada.
 *
 * <p><strong>Auditoria e Não Vazamento:</strong>
 * Toda ação de recuperação registra o {@code identityId} real do ator resolvido pelo contrato, o instante UTC,
 * o {@code sourceEventId} e o resultado, sem jamais expor ou registrar dados sensíveis, tokens ou payloads brutos.
 */
@RestController
@Profile("runtime | integrated")
public class PasswordResetEventRecoveryController {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 100;

    public static final String RECOVERY_BASE_PATH = "/api/v1/operations/password-reset-events";
    public static final String FAILED_EVENTS_PATH = "/api/v1/operations/password-reset-events/failed";
    public static final String REPROCESS_EVENT_PATH =
            "/api/v1/operations/password-reset-events/{sourceEventId}/reprocess";

    private final PasswordResetEventInboxRepository repository;
    private final PasswordResetEventInboxService inboxService;
    private final OperationalIdentityLookup identityLookup;
    private final Clock clock;

    public PasswordResetEventRecoveryController(
            PasswordResetEventInboxRepository repository,
            PasswordResetEventInboxService inboxService,
            OperationalIdentityLookup identityLookup,
            ObjectProvider<Clock> clockProvider) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.inboxService = Objects.requireNonNull(inboxService, "inboxService must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        Clock resolvedClock = clockProvider != null ? clockProvider.getIfAvailable() : null;
        this.clock = resolvedClock != null ? resolvedClock : Clock.systemUTC();
    }

    /**
     * Lista todos os eventos no estado {@code FAILED} disponíveis para intervenção e reprocessamento.
     */
    @GetMapping(path = FAILED_EVENTS_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PasswordResetEventRecoveryPageResponse> listFailedEvents(
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requireGlobalAdmin(oidcUser);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        Page<PasswordResetEventInboxEntity> failed = repository.findPageByStatus(
                PasswordResetEventInboxEntity.STATUS_FAILED,
                PageRequest.of(page, size));
        return ResponseEntity.ok(PasswordResetEventRecoveryPageResponse.fromPage(failed));
    }

    /**
     * Consulta o estado atual de um evento de redefinição de senha pelo {@code sourceEventId}.
     */
    @GetMapping(path = RECOVERY_BASE_PATH + "/{sourceEventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PasswordResetEventRecoveryResponse> getEvent(
            @PathVariable String sourceEventId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        requireGlobalAdmin(oidcUser);

        return repository.findBySourceEventId(sourceEventId)
                .map(entity -> ResponseEntity.ok(PasswordResetEventRecoveryResponse.fromEntity(entity)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Reprocessa e executa o replay autorizado de um evento {@code FAILED} no inbox.
     */
    @PostMapping(
            path = REPROCESS_EVENT_PATH,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PasswordResetEventRecoveryResponse> reprocessFailedEvent(
            @PathVariable String sourceEventId,
            @AuthenticationPrincipal(errorOnInvalidType = false) OidcUser oidcUser) {
        UUID actorIdentityId = requireGlobalAdmin(oidcUser);
        Instant now = clock.instant();

        Optional<PasswordResetEventInboxService.InboxProcessingResult> resultOpt =
                inboxService.reprocessFailedEvent(sourceEventId, actorIdentityId, now);

        if (resultOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        PasswordResetEventInboxService.InboxProcessingResult result = resultOpt.get();
        PasswordResetEventRecoveryResponse response =
                PasswordResetEventRecoveryResponse.fromResult(result, actorIdentityId, now);

        return ResponseEntity.ok(response);
    }

    /**
     * Segunda guarda de segurança: obtém o {@code identityId} do ator exclusivamente por
     * {@link OperationalIdentityLookup}, confirma concessão global de {@code ADMINISTRADOR_GERAL}
     * (com {@code scopeRef == null}) no instante da requisição.
     */
    private UUID requireGlobalAdmin(OidcUser oidcUser) {
        if (oidcUser == null) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }

        String issuer = extractIssuer(oidcUser);
        String subject = extractSubject(oidcUser);

        if (issuer == null || subject == null) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }

        Optional<OperationalIdentityLookup.OperationalIdentity> identityOpt =
                identityLookup.lookupIdentity(issuer, subject);

        if (identityOpt.isEmpty()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }

        OperationalIdentityLookup.OperationalIdentity identity = identityOpt.get();
        boolean hasGlobalAdmin = identity.roles().stream()
                .anyMatch(grant -> "ADMINISTRADOR_GERAL".equals(grant.roleName()) && grant.scopeRef() == null);

        if (!hasGlobalAdmin) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }

        return identity.identityId();
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
