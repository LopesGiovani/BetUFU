package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.betufu.chat.internal.EmergencyChatAdapter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The consumer adapters preserve the operation lock boundary without leaking its DTOs. */
class EmergencyPauseConcurrencyIT {
  @Test
  void chatAdapterMapsTheLockedOperationState() {
    var operations = mock(EmergencyPausePort.class);
    UUID revision = UUID.randomUUID();
    when(operations.lockState(
            EmergencyPausePort.Kind.CHANNEL,
            UUID.fromString("00000000-0000-0000-0000-000000000001")))
        .thenReturn(new EmergencyPausePort.State(true, revision));

    var state =
        new EmergencyChatAdapter(operations)
            .lock(
                br.com.betufu.chat.ChatEmergencyPausePort.TargetKind.CHANNEL,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
    assertThat(state.paused()).isTrue();
    assertThat(state.revision()).isEqualTo(revision);
  }
}
