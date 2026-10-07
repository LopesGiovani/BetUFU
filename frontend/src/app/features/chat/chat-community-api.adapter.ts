import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { ChatService } from '../../api/generated/api/chat.service';
import type { ChatMuteState } from '../../api/generated/model/chat-mute-state';
import type { ChatReportCommand } from '../../api/generated/model/chat-report-command';
import type { ChatReportReceipt } from '../../api/generated/model/chat-report-receipt';

const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/u;
const REASONS = new Set<ChatReportCommand.ReasonEnum>([
  'DISCURSO_DE_ODIO',
  'SPAM',
  'OFENSA',
  'ASSEDIO',
  'FRAUDE',
]);

export type ChatReportReason = ChatReportCommand.ReasonEnum;

export interface ChatPersonalMuteState {
  readonly version: number;
  readonly participantIds: ReadonlySet<string>;
}

function validVersion(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}

function normalizeState(value: ChatMuteState | null | undefined): ChatPersonalMuteState {
  const ids = value?.participantIds;
  const values = ids instanceof Set ? [...ids] : Array.isArray(ids) ? ids : null;
  if (
    !validVersion(value?.version) ||
    !values ||
    values.some((id) => typeof id !== 'string' || !UUID.test(id))
  )
    throw new Error('INVALID_CHAT_MUTE_STATE');
  return { version: value.version, participantIds: new Set(values) };
}

export function chatErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 409) return 'A preferencia mudou em outro dispositivo. Tente novamente.';
    if (error.status === 401 || error.status === 403)
      return 'Entre novamente para alterar seus silenciamentos.';
  }
  return 'Nao foi possivel sincronizar seu silenciamento. Tente novamente.';
}

@Injectable()
export class ChatCommunityApiAdapter {
  private readonly chat = inject(ChatService);

  readMutes(): Observable<ChatPersonalMuteState> {
    return this.chat
      .getAccountChatMutes('body', false, { transferCache: false })
      .pipe(map((state) => normalizeState(state)));
  }

  mute(participantId: string, expectedVersion: number): Observable<ChatPersonalMuteState> {
    this.assertParticipant(participantId);
    if (!validVersion(expectedVersion)) throw new Error('INVALID_CHAT_MUTE_VERSION');
    return this.chat
      .setAccountChatMute({ participantId, chatMuteCommand: { expectedVersion } }, 'body', false, {
        transferCache: false,
      })
      .pipe(map((state) => normalizeState(state)));
  }

  unmute(participantId: string, version: number): Observable<ChatPersonalMuteState> {
    this.assertParticipant(participantId);
    if (!validVersion(version)) throw new Error('INVALID_CHAT_MUTE_VERSION');
    return this.chat
      .removeAccountChatMute({ participantId, ifMatch: `"${version}"` }, 'body', false, {
        transferCache: false,
      })
      .pipe(map((state) => normalizeState(state)));
  }

  report(
    messageId: string,
    reason: ChatReportReason,
    idempotencyKey: string,
  ): Observable<ChatReportReceipt> {
    if (!UUID.test(messageId) || !REASONS.has(reason)) throw new Error('INVALID_CHAT_REPORT');
    if (!UUID.test(idempotencyKey)) throw new Error('CHAT_REPORT_UNAVAILABLE');
    return this.chat
      .reportChatMessage(
        {
          messageId,
          idempotencyKey,
          chatReportCommand: { reason },
        },
        'body',
        false,
        { transferCache: false },
      )
      .pipe(
        map((receipt) => {
          if (!UUID.test(receipt.reportId) || !Number.isFinite(Date.parse(receipt.reportedAt)))
            throw new Error('INVALID_CHAT_REPORT_RECEIPT');
          return receipt;
        }),
      );
  }

  private assertParticipant(participantId: string): void {
    if (!UUID.test(participantId)) throw new Error('INVALID_CHAT_PARTICIPANT');
  }
}
