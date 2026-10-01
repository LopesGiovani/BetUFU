package br.com.betufu.settlement.internal;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Retries pending, failed and expired cases using the same idempotent process command. */
@Component
@Profile("runtime | integrated")
public class SettlementRecoveryJob {
  private static final Logger log = LoggerFactory.getLogger(SettlementRecoveryJob.class);
  private final SettlementRepository repository;
  private final SettlementService service;
  private final Clock clock;

  @Autowired
  public SettlementRecoveryJob(SettlementRepository repository, SettlementService service) {
    this(repository, service, Clock.systemUTC());
  }

  public SettlementRecoveryJob(
      SettlementRepository repository, SettlementService service, Clock clock) {
    this.repository = repository;
    this.service = service;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${betufu.settlement.recovery-delay-ms:60000}")
  public void recover() {
    for (var caseId : repository.recoverable(clock.instant())) {
      repository
          .findCaseById(caseId)
          .ifPresent(
              settlementCase -> {
                try {
                  service.process(settlementCase.authorizationId());
                } catch (RuntimeException failure) {
                  log.warn(
                      "Settlement recovery deferred for case {} ({})",
                      caseId,
                      failure.getClass().getSimpleName());
                }
              });
    }
  }
}
