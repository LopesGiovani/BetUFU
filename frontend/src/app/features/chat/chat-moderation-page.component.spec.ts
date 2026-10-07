import { TestBed } from '@angular/core/testing';
import { of, throwError, Subject } from 'rxjs';
import type { ChatModerationCase } from '../../api/generated/model/chat-moderation-case';
import { HttpErrorResponse } from '@angular/common/http';
import { ChatModerationApiAdapter } from './chat-moderation-api.adapter';
import { ChatModerationStore } from './chat-moderation.store';
import {
  ChatModerationPageComponent,
  keepConfirmationFocus,
} from './chat-moderation-page.component';
import { SessionStore } from '../../core/session/session.store';

const caseId = '10000000-0000-0000-0000-000000000001';
const item: ChatModerationCase = {
  caseId,
  messageId: caseId,
  eventId: null,
  competitionId: null,
  status: 'OPEN' as const,
  openedAt: '2026-10-07T12:00:00Z',
};
describe('Scoped chat moderation', () => {
  it('cycles confirmation focus in both directions and skips disabled controls', () => {
    const dialog = document.createElement('dialog');
    dialog.innerHTML = '<textarea></textarea><button>Cancel</button><button disabled>Send</button>';
    document.body.append(dialog);
    dialog.addEventListener('keydown', keepConfirmationFocus);
    const textarea = dialog.querySelector('textarea')!;
    const cancel = dialog.querySelector('button')!;
    try {
      cancel.focus();
      cancel.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }),
      );
      expect(document.activeElement).toBe(textarea);
      textarea.dispatchEvent(
        new KeyboardEvent('keydown', {
          key: 'Tab',
          shiftKey: true,
          bubbles: true,
          cancelable: true,
        }),
      );
      expect(document.activeElement).toBe(cancel);
    } finally {
      dialog.remove();
    }
  });
  const adapter = {
    cases: vi.fn(() => of([item])),
    reviews: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 403 }))),
    forward: vi.fn(() => of({ caseId, forwardedAt: item.openedAt })),
    evidence: vi.fn(() =>
      of({ caseId, messageId: caseId, originalBody: 'restricted original', reports: [] }),
    ),
  };
  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      imports: [ChatModerationPageComponent],
      providers: [
        ChatModerationStore,
        { provide: ChatModerationApiAdapter, useValue: adapter },
        { provide: SessionStore, useValue: { isLoggedOut: () => false, isExpired: () => false } },
      ],
    });
    TestBed.overrideComponent(ChatModerationPageComponent, {
      set: {
        providers: [ChatModerationStore, { provide: ChatModerationApiAdapter, useValue: adapter }],
      },
    });
  });

  it('keeps admin controls and restricted evidence absent for scoped moderators', async () => {
    const f = TestBed.createComponent(ChatModerationPageComponent);
    f.detectChanges();
    await vi.waitFor(() => expect(f.componentInstance.store.loading()).toBe(false));
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Encaminhar caso');
    expect(f.nativeElement.textContent).not.toContain('Propor sancao');
    expect(f.nativeElement.textContent).not.toContain('restricted original');
    expect(adapter.evidence).not.toHaveBeenCalled();
  });

  it('keeps the exact command on recoverable failure and clears data on authorization loss', async () => {
    const store = TestBed.inject(ChatModerationStore);
    await store.loadModeration();
    adapter.forward.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })));
    store.prepare('forward', caseId);
    store.reason.set('Encaminhar para analise administrativa');
    await store.submit();
    expect(store.error()).toBeTruthy();
    const first = adapter.forward.mock.calls[0];
    await store.submit();
    expect(adapter.forward.mock.calls[1]).toEqual(first);
    adapter.cases.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 403 })));
    await store.loadModeration();
    expect(store.cases()).toEqual([]);
    expect(store.admin()).toBe(false);
  });

  it('does not send when a confirmation is cancelled', async () => {
    const store = TestBed.inject(ChatModerationStore);
    store.prepare('forward', caseId);
    store.cancel();
    await store.submit();
    expect(adapter.forward).not.toHaveBeenCalled();
  });

  it('discards late private responses after logout or destruction', async () => {
    const pending = new Subject<ChatModerationCase[]>();
    adapter.cases.mockReturnValueOnce(pending);
    const store = TestBed.inject(ChatModerationStore);
    const request = store.loadModeration();
    store.clear();
    pending.next([item]);
    pending.complete();
    await request;
    expect(store.cases()).toEqual([]);
    expect(store.evidence()).toBeNull();
    expect(store.admin()).toBe(false);
  });
});
