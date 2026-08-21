package br.com.betufu.identity.internal;

import java.time.Instant;
import java.util.UUID;

public record CurrentProfileResponse(
        UUID identityId,
        String nickname,
        Instant nicknameUpdatedAt,
        String matriculaStatus) {}
