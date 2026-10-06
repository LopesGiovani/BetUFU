import { Injectable, inject } from '@angular/core';
import { forkJoin, map, Observable } from 'rxjs';
import { ChatService } from '../../api/generated/api/chat.service';
import { ProfileService } from '../../api/generated/api/profile.service';
import { SessionService } from '../../api/generated/api/session.service';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import type { ChatCommandReceipt } from '../../api/generated/model/chat-command-receipt';

const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u;
export interface ChatWriteEligibility {
  owner: string;
  active: boolean;
  open: boolean;
  deadline: string | null;
}

@Injectable()
export class ChatWriteApiAdapter {
  private readonly chat = inject(ChatService);
  private readonly profiles = inject(ProfileService);
  private readonly sessions = inject(SessionService);
  private readonly enrollments = inject(EnrollmentService);

  eligibility(eventId: string | null): Observable<ChatWriteEligibility> {
    const options = { transferCache: false };
    const channel =
      eventId === null
        ? this.chat.getGlobalChat({ limit: 1 }, 'body', false, options)
        : this.chat.getEventChat({ eventId, limit: 1 }, 'body', false, options);
    return forkJoin({
      session: this.sessions.getSession('body', false, options),
      profile: this.profiles.getMyProfile('body', false, options),
      enrollment: this.enrollments.getEnrollment('body', false, options),
      channel,
    }).pipe(
      map(({ session, profile, enrollment, channel }) => {
        if (
          channel.kind !== (eventId === null ? 'GLOBAL' : 'EVENT') ||
          (channel.eventId ?? null) !== eventId
        )
          throw new Error('INVALID_CHAT_CHANNEL');
        if (!session.authenticated || !UUID.test(profile.identityId))
          throw new Error('CHAT_SESSION_UNAVAILABLE');
        const deadline = channel.deadline ?? null;
        if (deadline !== null && !Number.isFinite(Date.parse(deadline)))
          throw new Error('INVALID_CHAT_DEADLINE');
        // Read availability is not write authority; the POST enforces pauses and sanctions atomically.
        return {
          owner: profile.identityId,
          active: enrollment.status === 'ATIVO',
          open: channel.state === 'AVAILABLE',
          deadline,
        };
      }),
    );
  }

  send(eventId: string | null, key: string, text: string): Observable<ChatCommandReceipt> {
    const params = { idempotencyKey: key, chatSendRequest: { text } };
    const options = { transferCache: false };
    const request =
      eventId === null
        ? this.chat.sendGlobalChatMessage(params, 'response', false, options)
        : this.chat.sendEventChatMessage({ ...params, eventId }, 'response', false, options);
    return request.pipe(
      map((response) => {
        const receipt = response.body;
        if (
          response.status !== 201 ||
          !receipt ||
          !UUID.test(receipt.messageId) ||
          !Number.isFinite(Date.parse(receipt.postedAt)) ||
          !Number.isSafeInteger(receipt.changeCursor) ||
          receipt.changeCursor < 0
        )
          throw new Error('INVALID_CHAT_ACK');
        return {
          messageId: receipt.messageId,
          postedAt: receipt.postedAt,
          changeCursor: receipt.changeCursor,
        };
      }),
    );
  }
}
