package br.com.betufu.platform.session.passwordreset;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Disabled-by-default operational driver for inbox recovery, retention and FAILED monitoring.
 * Production activation remains owned by plan 02-29.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Profile("runtime | integrated")
@ConditionalOnProperty(
        prefix = "betufu.session.password-reset",
        name = "maintenance-enabled",
        havingValue = "true")
public class PasswordResetEventMaintenanceConfiguration {

    @Bean
    PasswordResetEventMaintenanceJob passwordResetEventMaintenanceJob(
            PasswordResetEventInboxService inboxService,
            PasswordResetEventInboxRepository repository,
            MeterRegistry meterRegistry,
            ObjectProvider<Clock> clockProvider,
            @Value("${betufu.session.password-reset.lease-duration:PT5M}") Duration leaseDuration,
            @Value("${betufu.session.password-reset.retention:PT720H}") Duration retention,
            @Value("${betufu.session.password-reset.recovery-batch-size:100}") int batchSize) {
        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        return new PasswordResetEventMaintenanceJob(
                inboxService,
                repository,
                meterRegistry,
                clock,
                leaseDuration,
                retention,
                batchSize,
                "password-reset-maintenance-" + UUID.randomUUID());
    }

    static final class PasswordResetEventMaintenanceJob {

        private static final Logger log = LoggerFactory.getLogger(PasswordResetEventMaintenanceJob.class);

        private final PasswordResetEventInboxService inboxService;
        private final PasswordResetEventInboxRepository repository;
        private final Clock clock;
        private final Duration leaseDuration;
        private final Duration retention;
        private final int batchSize;
        private final String instanceId;
        private final AtomicLong failedEvents = new AtomicLong();

        PasswordResetEventMaintenanceJob(
                PasswordResetEventInboxService inboxService,
                PasswordResetEventInboxRepository repository,
                MeterRegistry meterRegistry,
                Clock clock,
                Duration leaseDuration,
                Duration retention,
                int batchSize,
                String instanceId) {
            this.inboxService = Objects.requireNonNull(inboxService, "inboxService must not be null");
            this.repository = Objects.requireNonNull(repository, "repository must not be null");
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
            this.leaseDuration = requirePositive(leaseDuration, "leaseDuration");
            this.retention = requirePositive(retention, "retention");
            if (batchSize < 1 || batchSize > PasswordResetEventInboxService.MAX_RECOVERY_BATCH_SIZE) {
                throw new IllegalArgumentException("batchSize must be between 1 and "
                        + PasswordResetEventInboxService.MAX_RECOVERY_BATCH_SIZE);
            }
            this.batchSize = batchSize;
            this.instanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
            Gauge.builder("betufu.password_reset.inbox.failed", failedEvents, AtomicLong::get)
                    .description("Number of password-reset inbox events awaiting operator resolution")
                    .register(Objects.requireNonNull(meterRegistry, "meterRegistry must not be null"));
        }

        @Scheduled(
                fixedDelayString = "${betufu.session.password-reset.maintenance-interval:PT1M}",
                initialDelayString = "${betufu.session.password-reset.maintenance-initial-delay:PT10S}")
        void runCycle() {
            Instant now = clock.instant();
            int recovered = inboxService.recoverPendingEvents(
                    instanceId, leaseDuration, now, batchSize).size();
            int deleted = inboxService.cleanupDeliveredOlderThan(retention, now);
            long failed = repository.countByStatus(PasswordResetEventInboxEntity.STATUS_FAILED);
            failedEvents.set(failed);

            if (failed > 0) {
                log.warn("Password reset inbox requires operator action: failedCount={}", failed);
            }
            log.debug(
                    "Password reset maintenance completed: recoveredCount={}, deletedDeliveredCount={}, failedCount={}",
                    recovered,
                    deleted,
                    failed);
        }

        long failedEventCount() {
            return failedEvents.get();
        }

        private static Duration requirePositive(Duration value, String field) {
            Objects.requireNonNull(value, field + " must not be null");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(field + " must be positive");
            }
            return value;
        }
    }
}
