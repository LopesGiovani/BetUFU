package br.com.betufu.identity.internal;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Allowlist projection over the validated ID token.
 *
 * <p>BETUFU opts out of the OIDC {@code userinfo_endpoint}, so every claim consumed by the
 * platform must originate from the ID token that Spring Security already validated. This record
 * declares that allowlist structurally: exactly five components, no more.
 *
 * <p>{@code issuer} and {@code subject} form the natural identity key established by Plan 01-05
 * and are mandatory. Neither is trimmed, case-folded or Unicode-normalized, so {@code (iss, sub)}
 * keeps the exact byte-for-byte comparison the database unique constraint relies on. {@code sub}
 * is read as a raw {@code String}; {@code iss} arrives type-converted and is handled by
 * {@link #issuerClaim}.
 *
 * <p>{@code preferredUsername}, {@code acr} and {@code amr} are optional and never participate in
 * identity. A missing or wrongly shaped optional claim yields {@code null} / an empty list instead
 * of failing the request: an unusable display claim must not turn a valid login into an error.
 */
public record OidcClaimsProjection(
        String issuer,
        String subject,
        String preferredUsername,
        String acr,
        List<String> amr) {

    private static final String ACR_CLAIM = "acr";
    private static final String AMR_CLAIM = "amr";

    public OidcClaimsProjection {
        amr = (amr == null) ? List.of() : List.copyOf(amr);
    }

    /**
     * Projects the authenticated principal, reading exclusively from its ID token.
     *
     * @return the projection, or {@link Optional#empty()} when the principal is absent or the
     *     mandatory identity claims are missing, blank or not textual
     */
    public static Optional<OidcClaimsProjection> from(OidcUser oidcUser) {
        return (oidcUser == null) ? Optional.empty() : from(oidcUser.getIdToken());
    }

    /**
     * Projects a validated ID token.
     *
     * <p>Claims are read from {@link OidcIdToken} rather than from the merged
     * {@code OidcUser} claim set, so a UserInfo response could never supply an identity claim
     * that the ID token itself does not carry.
     */
    public static Optional<OidcClaimsProjection> from(OidcIdToken idToken) {
        if (idToken == null) {
            return Optional.empty();
        }

        String issuer = issuerClaim(idToken);
        String subject = textClaim(idToken, IdTokenClaimNames.SUB);

        if (issuer == null || subject == null) {
            return Optional.empty();
        }

        return Optional.of(new OidcClaimsProjection(
                issuer,
                subject,
                textClaim(idToken, StandardClaimNames.PREFERRED_USERNAME),
                textClaim(idToken, ACR_CLAIM),
                textListClaim(idToken, AMR_CLAIM)));
    }

    /**
     * Reads {@code iss}, which is NOT a {@code String} after a real decode.
     *
     * <p>{@code OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()} maps {@code iss} to
     * {@link URL}, so a token produced by the actual login pipeline carries a {@code URL} here
     * while hand-built test tokens carry a {@code String}. Rejecting the non-{@code String} form
     * would fail every production login with 401 while every test still passed.
     *
     * <p>The textual form is taken with {@code toString()}, which is byte-for-byte the value the
     * previous {@code getClaimAsString} produced — the natural key {@code (issuer, subject)} in
     * {@code local_identity} therefore keeps matching rows written by Plans 01-05 and 01-29.
     *
     * <p>Only {@code iss} needs this: the default converters cover {@code iss}, {@code aud},
     * {@code nonce}, {@code exp}, {@code iat}, {@code auth_time}, {@code amr} and
     * {@code email_verified}, so {@code sub}, {@code preferred_username} and {@code acr} stay
     * textual and keep the stricter check below.
     */
    private static String issuerClaim(OidcIdToken idToken) {
        Object value = idToken.getClaims().get(IdTokenClaimNames.ISS);
        String text = switch (value) {
            case String string -> string;
            case URL url -> url.toString();
            case URI uri -> uri.toString();
            case null, default -> null;
        };

        return (text != null && !text.isBlank()) ? text : null;
    }

    /**
     * Reads a claim only when it is genuinely a non-blank {@code String}.
     *
     * <p>Stricter than {@code getClaimAsString} on purpose for the claims that are never
     * type-converted: coercing a non-textual value through {@code toString()} there could turn a
     * malformed token into a plausible-looking identity. See {@link #issuerClaim} for why
     * {@code iss} is the deliberate exception.
     */
    private static String textClaim(OidcIdToken idToken, String claimName) {
        Object value = idToken.getClaims().get(claimName);

        return (value instanceof String text && !text.isBlank()) ? text : null;
    }

    private static List<String> textListClaim(OidcIdToken idToken, String claimName) {
        Object value = idToken.getClaims().get(claimName);

        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }

        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(entry -> !entry.isBlank())
                .toList();
    }
}
