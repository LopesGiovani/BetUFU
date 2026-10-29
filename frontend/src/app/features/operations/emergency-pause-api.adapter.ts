import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { EmergencyPausesService } from '../../api/generated/api/emergency-pauses.service';
import type { EmergencyPauseCommand } from '../../api/generated/model/emergency-pause-command';
import type { EmergencyPauseAvailability } from '../../api/generated/model/emergency-pause-availability';

const options = { transferCache: false };
export type PauseAvailability = 'unknown' | 'available' | 'paused' | 'error';

@Injectable({ providedIn: 'root' })
export class EmergencyPauseApiAdapter {
  private readonly api = inject(EmergencyPausesService);

  list() {
    return this.api.listEmergencyPauses('body', false, options);
  }

  command(command: EmergencyPauseCommand) {
    return this.api.commandEmergencyPause({ emergencyPauseCommand: command }, 'body', false, options);
  }

  chat(eventId: string | null) {
    return (eventId === null
      ? this.api.getGlobalChatAvailability('body', false, options)
      : this.api.getEventChatAvailability({ eventId }, 'body', false, options)
    ).pipe(map((value) => this.availability(value, 'messagesPaused')));
  }

  market(marketId: string) {
    return this.api.getPredictionMarketAvailability({ marketId }, 'body', false, options)
      .pipe(map((value) => this.availability(value, 'predictionsPaused')));
  }

  private availability(value: EmergencyPauseAvailability, flag: 'messagesPaused' | 'predictionsPaused'): PauseAvailability {
    if (!value || typeof value[flag] !== 'boolean' ||
        (value.state !== 'PAUSED' && value.state !== 'AVAILABLE') ||
        value[flag] !== (value.state === 'PAUSED')) throw new Error('Invalid availability');
    return value[flag] ? 'paused' : 'available';
  }
}
