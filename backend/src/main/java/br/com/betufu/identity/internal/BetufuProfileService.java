package br.com.betufu.identity.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class BetufuProfileService {

    private static final Duration MINIMUM_CHANGE_INTERVAL = Duration.ofDays(30);

    private final BetufuProfileRepository repository;

    public BetufuProfileService(ObjectProvider<BetufuProfileRepository> repositoryProvider) {
        this.repository = (repositoryProvider != null) ? repositoryProvider.getIfAvailable() : null;
    }

    @Transactional(readOnly = true)
    public Optional<BetufuProfile> findProfile(UUID identityId) {
        Objects.requireNonNull(identityId, "identityId must not be null");
        return requireRepository().findByIdentityId(identityId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BetufuProfile saveProfile(UUID identityId, String rawNickname) {
        Objects.requireNonNull(identityId, "identityId must not be null");
        BetufuProfileRepository repo = requireRepository();
        String trimmedNickname = (rawNickname == null) ? "" : rawNickname.trim();

        Optional<BetufuProfile> existing = repo.findByIdentityId(identityId);
        if (existing.isEmpty()) {
            repo.insertIfAbsent(identityId, trimmedNickname);
            return repo.findByIdentityId(identityId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Profile insert completed without a readable row"));
        }

        BetufuProfile current = existing.get();
        if (current.getNickname().equals(trimmedNickname)) {
            return current;
        }

        Instant now = Instant.now();
        Instant lastChange = current.getNicknameUpdatedAt();
        if (lastChange != null && Duration.between(lastChange, now).compareTo(MINIMUM_CHANGE_INTERVAL) < 0) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.NICKNAME_COOLDOWN_ACTIVE);
        }

        Instant effectiveChange = now.isBefore(current.getCreatedAt())
                ? current.getCreatedAt()
                : now;
        current.changeNickname(trimmedNickname, effectiveChange);
        return current;
    }

    private BetufuProfileRepository requireRepository() {
        if (repository == null) {
            throw new IllegalStateException("BetufuProfileRepository is not available in this context");
        }
        return repository;
    }
}
