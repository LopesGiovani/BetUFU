package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEmergencyPausePort;
import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adapts the neutral operations state to the chat-owned contract. */
@Component
@Profile("runtime | integrated")
public class EmergencyChatAdapter implements ChatEmergencyPausePort {
  private final EmergencyPausePort pauses;

  public EmergencyChatAdapter(EmergencyPausePort pauses) {
    this.pauses = pauses;
  }

  @Override
  public State lock(TargetKind kind, UUID targetId) {
    var state = pauses.lockState(toOperationKind(kind), targetId);
    return new State(state.paused(), state.currentPauseId());
  }

  @Override
  public State current(TargetKind kind, UUID targetId) {
    var state = pauses.state(toOperationKind(kind), targetId);
    return new State(state.paused(), state.currentPauseId());
  }

  private EmergencyPausePort.Kind toOperationKind(TargetKind kind) {
    return switch (kind) {
      case ACCOUNT -> EmergencyPausePort.Kind.ACCOUNT;
      case EVENT -> EmergencyPausePort.Kind.EVENT;
      case CHANNEL -> EmergencyPausePort.Kind.CHANNEL;
    };
  }
}
