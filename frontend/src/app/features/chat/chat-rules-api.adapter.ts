import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs';
import { ChatRulesPreferenceService } from '../../api/generated/api/chat-rules-preference.service';

@Injectable({ providedIn: 'root' })
export class ChatRulesApiAdapter {
  private readonly api = inject(ChatRulesPreferenceService);
  read() {
    return this.api.getChatRulesPreference('body', false, { transferCache: false }).pipe(map(value => this.validate(value)));
  }
  dismiss() {
    return this.api.dismissChatRulesPreference('body', false, { transferCache: false }).pipe(map(value => this.validate(value, true)));
  }
  private validate(value: unknown, saving = false): boolean {
    if (!value || typeof value !== 'object' || !('dismissed' in value) ||
      typeof value.dismissed !== 'boolean' || (saving && !value.dismissed)) throw new Error('Invalid chat rules preference');
    return value.dismissed;
  }
}
