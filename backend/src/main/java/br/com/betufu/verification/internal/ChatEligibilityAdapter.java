package br.com.betufu.verification.internal;

import br.com.betufu.chat.ChatEligibilityPort;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("runtime | integrated")
public class ChatEligibilityAdapter implements ChatEligibilityPort {
  private final OperationalIdentityLookup identities;
  private final UfuEnrollmentRepository enrollments;

  public ChatEligibilityAdapter(
      OperationalIdentityLookup identities, UfuEnrollmentRepository enrollments) {
    this.identities = identities;
    this.enrollments = enrollments;
  }

  @Override
  public Optional<UUID> findActor(String issuer, String subject) {
    return identities.findIdentityId(issuer, subject);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean lockActiveEnrollment(UUID actorId) {
    return enrollments.findActiveByIdentityIdForUpdate(actorId).isPresent();
  }
}
