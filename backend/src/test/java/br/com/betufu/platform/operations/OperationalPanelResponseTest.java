package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import br.com.betufu.audit.AuditAggregateQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperationalPanelResponseTest {

    private static final List<String> APPROVED_PANEL_RESPONSE_COMPONENTS = List.of(
            "queriedAt",
            "timeWindow",
            "roleChanges",
            "rejectedEnrollmentsCount",
            "administrativeCorrections",
            "unregisteredEnrollmentsCount",
            "unavailableBlocks");

    private static final List<String> APPROVED_TIME_WINDOW_COMPONENTS = List.of(
            "since",
            "until");

    private static final List<String> APPROVED_ROLE_CHANGES_BLOCK_COMPONENTS = List.of(
            "recentEvents");

    private static final List<String> APPROVED_ROLE_CHANGE_EVENT_COMPONENTS = List.of(
            "eventId",
            "occurredAt",
            "actorIdentityId",
            "actorRole",
            "targetIdentityId",
            "targetNickname",
            "action",
            "outcome",
            "hasReason");

    private static final List<String> APPROVED_REJECTED_ENROLLMENTS_COMPONENTS = List.of(
            "duplicateCount",
            "attemptLimitCount",
            "totalCount");

    private static final List<String> APPROVED_ADMIN_CORRECTIONS_BLOCK_COMPONENTS = List.of(
            "recentEvents");

    private static final List<String> APPROVED_ADMIN_CORRECTION_EVENT_COMPONENTS = List.of(
            "eventId",
            "occurredAt",
            "actorIdentityId",
            "actorRole",
            "targetIdentityId",
            "targetNickname",
            "action",
            "outcome",
            "hasReason",
            "reason");

    private static final List<String> APPROVED_UNREGISTERED_ENROLLMENTS_COMPONENTS = List.of(
            "count");

    private static final List<String> APPROVED_OPERATIONAL_BLOCK_VALUES = List.of(
            "ROLE_CHANGES",
            "REJECTED_ENROLLMENTS_COUNT",
            "ADMINISTRATIVE_CORRECTIONS",
            "UNREGISTERED_ENROLLMENTS_COUNT");

    @Test
    @DisplayName("Garante allowlist estrita de 7 componentes no record OperationalPanelResponse")
    void enforcesStrictAllowlistOnOperationalPanelResponseComponents() {
        assertThat(OperationalPanelResponse.class.isRecord()).isTrue();

        List<String> actualNames = Arrays.stream(OperationalPanelResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(actualNames)
                .containsExactlyElementsOf(APPROVED_PANEL_RESPONSE_COMPONENTS);
    }

    @Test
    @DisplayName("Garante tipos esperados para os componentes de OperationalPanelResponse")
    void enforcesExpectedComponentTypesOnOperationalPanelResponse() {
        for (RecordComponent component : OperationalPanelResponse.class.getRecordComponents()) {
            switch (component.getName()) {
                case "queriedAt" -> assertThat(component.getType()).isEqualTo(Instant.class);
                case "timeWindow" -> assertThat(component.getType()).isEqualTo(OperationalPanelResponse.TimeWindow.class);
                case "roleChanges" -> assertThat(component.getType()).isEqualTo(OperationalPanelResponse.RoleChangesBlock.class);
                case "rejectedEnrollmentsCount" -> assertThat(component.getType()).isEqualTo(OperationalPanelResponse.RejectedEnrollmentsBlock.class);
                case "administrativeCorrections" -> assertThat(component.getType()).isEqualTo(OperationalPanelResponse.AdministrativeCorrectionsBlock.class);
                case "unregisteredEnrollmentsCount" -> assertThat(component.getType()).isEqualTo(OperationalPanelResponse.UnregisteredEnrollmentsBlock.class);
                case "unavailableBlocks" -> assertThat(component.getType()).isEqualTo(List.class);
                default -> throw new AssertionError("Componente não autorizado na allowlist: " + component.getName());
            }
        }
    }

    @Test
    @DisplayName("Garante allowlist estrita e valores do enum OperationalBlock")
    void enforcesStrictAllowlistOnOperationalBlockEnum() {
        assertThat(OperationalPanelResponse.OperationalBlock.class.isEnum()).isTrue();

        List<String> actualNames = Arrays.stream(OperationalPanelResponse.OperationalBlock.values())
                .map(Enum::name)
                .toList();

        assertThat(actualNames).containsExactlyElementsOf(APPROVED_OPERATIONAL_BLOCK_VALUES);
    }

    @Test
    @DisplayName("Garante allowlist estrita e tipos dos componentes de TimeWindow")
    void enforcesStrictAllowlistOnTimeWindow() {
        assertThat(OperationalPanelResponse.TimeWindow.class.isRecord()).isTrue();

        List<String> actualNames = Arrays.stream(OperationalPanelResponse.TimeWindow.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(actualNames)
                .containsExactlyElementsOf(APPROVED_TIME_WINDOW_COMPONENTS);

        for (RecordComponent component : OperationalPanelResponse.TimeWindow.class.getRecordComponents()) {
            assertThat(component.getType()).isEqualTo(Instant.class);
        }
    }

    @Test
    @DisplayName("Garante allowlist estrita e tipos dos componentes de RoleChangesBlock e RoleChangeEventDto")
    void enforcesStrictAllowlistOnRoleChangesBlock() {
        assertThat(OperationalPanelResponse.RoleChangesBlock.class.isRecord()).isTrue();
        assertThat(OperationalPanelResponse.RoleChangeEventDto.class.isRecord()).isTrue();

        List<String> blockNames = Arrays.stream(OperationalPanelResponse.RoleChangesBlock.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(blockNames).containsExactlyElementsOf(APPROVED_ROLE_CHANGES_BLOCK_COMPONENTS);

        List<String> eventNames = Arrays.stream(OperationalPanelResponse.RoleChangeEventDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(eventNames).containsExactlyElementsOf(APPROVED_ROLE_CHANGE_EVENT_COMPONENTS);

        for (RecordComponent component : OperationalPanelResponse.RoleChangeEventDto.class.getRecordComponents()) {
            switch (component.getName()) {
                case "eventId", "actorIdentityId", "targetIdentityId" -> assertThat(component.getType()).isEqualTo(UUID.class);
                case "occurredAt" -> assertThat(component.getType()).isEqualTo(Instant.class);
                case "actorRole", "targetNickname", "action", "outcome" -> assertThat(component.getType()).isEqualTo(String.class);
                case "hasReason" -> assertThat(component.getType()).isEqualTo(boolean.class);
                default -> throw new AssertionError("Componente de evento não autorizado: " + component.getName());
            }
        }
    }

    @Test
    @DisplayName("Garante allowlist estrita e tipos dos componentes dos demais blocos")
    void enforcesStrictAllowlistOnRemainingBlocks() {
        List<String> rejNames = Arrays.stream(OperationalPanelResponse.RejectedEnrollmentsBlock.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(rejNames).containsExactlyElementsOf(APPROVED_REJECTED_ENROLLMENTS_COMPONENTS);

        List<String> corrBlockNames = Arrays.stream(OperationalPanelResponse.AdministrativeCorrectionsBlock.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(corrBlockNames).containsExactlyElementsOf(APPROVED_ADMIN_CORRECTIONS_BLOCK_COMPONENTS);

        List<String> corrEventNames = Arrays.stream(OperationalPanelResponse.AdministrativeCorrectionEventDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(corrEventNames).containsExactlyElementsOf(APPROVED_ADMIN_CORRECTION_EVENT_COMPONENTS);

        List<String> unregNames = Arrays.stream(OperationalPanelResponse.UnregisteredEnrollmentsBlock.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(unregNames).containsExactlyElementsOf(APPROVED_UNREGISTERED_ENROLLMENTS_COMPONENTS);

        RecordComponent unregisteredCount = OperationalPanelResponse.UnregisteredEnrollmentsBlock.class
                .getRecordComponents()[0];
        assertThat(unregisteredCount.getType()).isEqualTo(int.class);
        assertThat(Iterable.class.isAssignableFrom(unregisteredCount.getType())).isFalse();
        assertThat(unregisteredCount.getType().isArray()).isFalse();
    }

    @Test
    @DisplayName("Motivo integral cruza a fronteira da auditoria somente para correção administrativa")
    void exposesFullReasonOnlyForAdministrativeCorrectionProjection() {
        List<String> roleProjectionNames = Arrays.stream(
                        AuditAggregateQuery.RecentRoleChangeEvent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(roleProjectionNames)
                .contains("hasReason")
                .doesNotContain("reason");

        List<String> correctionProjectionNames = Arrays.stream(
                        AuditAggregateQuery.RecentAdministrativeCorrectionEvent.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(correctionProjectionNames)
                .contains("hasReason", "reason");
    }

    @Test
    @DisplayName("Proíbe explicitamente campos sensíveis como email, matricula crua, hmac, tokens e valores anteriores/novos")
    void explicitlyForbidsSensitiveFieldNamesAcrossAllDtoComponents() {
        List<Class<?>> recordClasses = List.of(
                OperationalPanelResponse.class,
                OperationalPanelResponse.TimeWindow.class,
                OperationalPanelResponse.RoleChangesBlock.class,
                OperationalPanelResponse.RoleChangeEventDto.class,
                OperationalPanelResponse.RejectedEnrollmentsBlock.class,
                OperationalPanelResponse.AdministrativeCorrectionsBlock.class,
                OperationalPanelResponse.AdministrativeCorrectionEventDto.class,
                OperationalPanelResponse.UnregisteredEnrollmentsBlock.class);

        for (Class<?> recordClass : recordClasses) {
            List<String> componentNames = Arrays.stream(recordClass.getRecordComponents())
                    .map(RecordComponent::getName)
                    .map(String::toLowerCase)
                    .toList();

            assertThat(componentNames)
                    .as("Classe %s não pode conter campos sensíveis", recordClass.getSimpleName())
                    .doesNotContain(
                            "email",
                            "matricula",
                            "rawmatricula",
                            "hmac",
                            "digest",
                            "password",
                            "secret",
                            "token",
                            "claims",
                            "idempotencykey",
                            "previousvalue",
                            "newvalue",
                            "rawpayload");
        }
    }
}
