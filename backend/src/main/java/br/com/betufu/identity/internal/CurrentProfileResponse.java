package br.com.betufu.identity.internal;

import java.time.Instant;
import java.util.UUID;

public record CurrentProfileResponse(
        UUID identityId,
        String nickname,
        Instant nicknameUpdatedAt,
        String matriculaStatus,
        UUID favoriteOrganizationId) {

    public CurrentProfileResponse(
            UUID identityId,
            String nickname,
            Instant nicknameUpdatedAt,
            String matriculaStatus) {
        this(identityId, nickname, nicknameUpdatedAt, matriculaStatus, null);
    }

    public record FavoriteOrganizationRequest(UUID organizationId) { }
}
