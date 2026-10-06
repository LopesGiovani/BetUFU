import { TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { firstValueFrom, of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { ChatService } from '../../api/generated/api/chat.service';
import { ProfileService } from '../../api/generated/api/profile.service';
import { SessionService } from '../../api/generated/api/session.service';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { ChatWriteApiAdapter } from './chat-write-api.adapter';

const receipt = {
  messageId: '22222222-2222-4222-8222-222222222222',
  postedAt: '2026-10-06T12:00:00Z',
  changeCursor: 1,
};
function adapter(status: number, body = receipt) {
  const send = vi.fn(() => of(new HttpResponse({ status, body })));
  TestBed.configureTestingModule({
    providers: [
      ChatWriteApiAdapter,
      {
        provide: ChatService,
        useValue: { sendGlobalChatMessage: send, sendEventChatMessage: send },
      },
      { provide: ProfileService, useValue: {} },
      { provide: SessionService, useValue: {} },
      { provide: EnrollmentService, useValue: {} },
    ],
  });
  return { adapter: TestBed.inject(ChatWriteApiAdapter), send };
}
describe('authoritative chat acknowledgement adapter', () => {
  it.each([true, false])('uses current session and enrollment, active=%s', async (active) => {
    const read = vi.fn(() =>
      of({ kind: 'GLOBAL', eventId: null, state: 'AVAILABLE', deadline: null }),
    );
    TestBed.configureTestingModule({
      providers: [
        ChatWriteApiAdapter,
        { provide: ChatService, useValue: { getGlobalChat: read } },
        {
          provide: ProfileService,
          useValue: {
            getMyProfile: () =>
              of({ identityId: receipt.messageId, matriculaStatus: 'REGISTRADA' }),
          },
        },
        { provide: SessionService, useValue: { getSession: () => of({ authenticated: true }) } },
        {
          provide: EnrollmentService,
          useValue: { getEnrollment: () => of({ status: active ? 'ATIVO' : 'LIBERADO' }) },
        },
      ],
    });
    expect(await firstValueFrom(TestBed.inject(ChatWriteApiAdapter).eligibility(null))).toEqual({
      owner: receipt.messageId,
      active,
      open: true,
      deadline: null,
    });
    expect(read).toHaveBeenCalledWith({ limit: 1 }, 'body', false, { transferCache: false });
  });
  it.each([null, '11111111-1111-4111-8111-111111111111'])(
    'sends exact text and key through the generated %s API',
    async (event) => {
      const { adapter: api, send } = adapter(201);
      expect(await firstValueFrom(api.send(event, 'original-key', ' 😀 '))).toEqual(receipt);
      expect(send).toHaveBeenCalledWith(
        {
          idempotencyKey: 'original-key',
          chatSendRequest: { text: ' 😀 ' },
          ...(event ? { eventId: event } : {}),
        },
        'response',
        false,
        { transferCache: false },
      );
    },
  );
  it.each([200, 202, 204])('does not mark status %i as persisted', async (status) => {
    const { adapter: api } = adapter(status);
    await expect(firstValueFrom(api.send(null, 'key', 'text'))).rejects.toThrow('INVALID_CHAT_ACK');
  });
  it('rejects a malformed acknowledgement even on201', async () => {
    const { adapter: api } = adapter(201, { ...receipt, changeCursor: -1 });
    await expect(firstValueFrom(api.send(null, 'key', 'text'))).rejects.toThrow('INVALID_CHAT_ACK');
  });
});
