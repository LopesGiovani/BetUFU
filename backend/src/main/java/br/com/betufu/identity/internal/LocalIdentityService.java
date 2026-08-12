package br.com.betufu.identity.internal;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class LocalIdentityService {

    private final LocalIdentityRepository repository;

    public LocalIdentityService(LocalIdentityRepository repository) {
        this.repository = repository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LocalIdentity persistCurrentIdentity(String issuer, String subject) {
        repository.insertIfAbsent(issuer, subject);

        return repository.findByNaturalKey(issuer, subject)
                .orElseThrow(() -> new IllegalStateException(
                        "Identity insert completed without a readable row"));
    }
}
