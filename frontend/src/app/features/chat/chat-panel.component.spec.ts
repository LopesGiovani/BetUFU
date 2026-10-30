import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { afterAll, beforeAll, describe, it, expect, vi } from 'vitest';
import { ChatPanelComponent } from './chat-panel.component';
import { ChatReadStore } from './chat-read.store';
import { ChatOutboxStore } from './chat-outbox.store';
import { SessionStore } from '../../core/session/session.store';
import { ChatPersonalMuteStore } from './chat-personal-mute.store';
import { ChatCommunityApiAdapter } from './chat-community-api.adapter';
import { EmergencyPauseStore } from '../operations/emergency-pause.store';
import { of, Subject, throwError } from 'rxjs';
import type { ChatMessage } from '../../api/generated/model/chat-message';

const MESSAGE: ChatMessage = {
  messageId: '11111111-1111-1111-1111-111111111111',
  participantId: '22222222-2222-2222-2222-222222222222',
  nickname: 'Colega',
  text: '<img src=x>',
  postedAt: '2026-10-07T00:00:00Z',
  removed: false,
  changeCursor: 1,
};

async function community(authenticated = true) {
  const messages = signal<readonly ChatMessage[]>([MESSAGE]);
  const muted = signal<ReadonlySet<string>>(new Set());
  const session = {
    isAuthenticated: signal(authenticated),
    isChecking: signal(false),
    revision: signal(0),
  };
  const api = {
    report: vi.fn<ChatCommunityApiAdapter['report']>(() =>
      of({ reportId: MESSAGE.messageId, reportedAt: MESSAGE.postedAt }),
    ),
  };
  TestBed.configureTestingModule({
    imports: [ChatPanelComponent],
    providers: [{ provide: SessionStore, useValue: session }],
  });
  TestBed.overrideComponent(ChatPanelComponent, {
    set: {
      providers: [
        { provide: EmergencyPauseStore, useValue: { availability: signal('available'), notice: signal(null), open: vi.fn(), clear: vi.fn(), refresh: vi.fn() } },
        {
          provide: ChatReadStore,
          useValue: {
            messages,
            status: signal('ready'),
            isResynchronizing: signal(false),
            historyLoading: signal(false),
            error: signal(null),
            historyCursor: signal(null),
            ownId: signal(null),
            revision: signal(0),
            mode: signal('initial'),
            pushRevision: signal(0),
            connectionLabel: signal('Ao vivo'),
            open: vi.fn(),
            close: vi.fn(),
            refresh: vi.fn(),
          },
        },
        {
          provide: ChatOutboxStore,
          useValue: {
            canCompose: signal(false),
            notice: signal(null),
            items: signal([]),
            acknowledgements: signal(0),
            open: vi.fn(),
            close: vi.fn(),
          },
        },
        {
          provide: ChatPersonalMuteStore,
          useValue: {
            participantIds: muted,
            notice: signal(null),
            error: signal(null),
            canChange: signal(true),
            isMuted: vi.fn(),
            setMuted: vi.fn(),
            refresh: vi.fn(),
          },
        },
        { provide: ChatCommunityApiAdapter, useValue: api },
      ],
    },
  });
  const fixture = TestBed.createComponent(ChatPanelComponent);
  fixture.componentRef.setInput('eventId', '33333333-3333-3333-3333-333333333333');
  await fixture.whenStable();
  return { fixture, session, api, muted, messages };
}

describe('chat composer', () => {
  it('counts emoji correctly, renders plain text and keeps the textarea focused after queueing', async () => {
    const read = {
      status: signal('ready'),
      isResynchronizing: signal(false),
      historyLoading: signal(false),
      error: signal(null),
      historyCursor: signal(null),
      messages: signal([]),
      ownId: signal(null),
      connectionLabel: signal('Ao vivo'),
      revision: signal(0),
      mode: signal('initial'),
      pushRevision: signal(0),
      open: vi.fn(),
      close: vi.fn(),
      refresh: vi.fn(),
    };
    const outbox = {
      canCompose: signal(true),
      notice: signal(null),
      items: signal([]),
      acknowledgements: signal(0),
      enqueue: vi.fn(() => true),
      open: vi.fn(),
      close: vi.fn(),
    };
    const session = {
      isAuthenticated: signal(true),
      isChecking: signal(false),
      revision: signal(0),
    };
    TestBed.configureTestingModule({
      imports: [ChatPanelComponent],
      providers: [{ provide: SessionStore, useValue: session }],
    });
    TestBed.overrideComponent(ChatPanelComponent, {
      set: {
        providers: [
          { provide: ChatReadStore, useValue: read },
          { provide: EmergencyPauseStore, useValue: { availability: signal('available'), notice: signal(null), open: vi.fn(), clear: vi.fn(), refresh: vi.fn() } },
          { provide: ChatOutboxStore, useValue: outbox },
          {
            provide: ChatPersonalMuteStore,
            useValue: {
              participantIds: signal(new Set<string>()),
              notice: signal(null),
              error: signal(null),
              canChange: signal(true),
              isMuted: vi.fn(() => false),
              setMuted: vi.fn(),
              open: vi.fn(),
              clear: vi.fn(),
              refresh: vi.fn(),
              retry: vi.fn(),
            },
          },
          { provide: ChatCommunityApiAdapter, useValue: { report: vi.fn() } },
        ],
      },
    });
    const fixture = TestBed.createComponent(ChatPanelComponent);
    await fixture.whenStable();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    textarea.focus();
    textarea.value = '😀'.repeat(280);
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('280/280');
    expect(textarea.hasAttribute('maxlength')).toBe(false);
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { cancelable: true }));
    await fixture.whenStable();
    expect(outbox.enqueue).toHaveBeenCalledWith('😀'.repeat(280));
    expect(textarea.value).toBe('');
    expect(document.activeElement).toBe(textarea);
    fixture.componentInstance.draft.set('<img src=x>');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    session.isAuthenticated.set(false);
    session.revision.update((n) => n + 1);
    await fixture.whenStable();
    expect(fixture.componentInstance.draft()).toBe('');
    expect(outbox.close).toHaveBeenCalled();
  });
});

describe('personal chat controls', () => {
  const originalShowModal = Object.getOwnPropertyDescriptor(
    HTMLDialogElement.prototype,
    'showModal',
  );
  const originalClose = Object.getOwnPropertyDescriptor(HTMLDialogElement.prototype, 'close');
  beforeAll(() => {
    // JSDOM models dialog attributes; real modal focus containment is verified in Playwright.
    Object.defineProperty(HTMLDialogElement.prototype, 'showModal', {
      configurable: true,
      value(this: HTMLDialogElement) {
        this.setAttribute('open', '');
      },
    });
    Object.defineProperty(HTMLDialogElement.prototype, 'close', {
      configurable: true,
      value(this: HTMLDialogElement) {
        this.removeAttribute('open');
      },
    });
  });
  afterAll(() => {
    if (originalShowModal)
      Object.defineProperty(HTMLDialogElement.prototype, 'showModal', originalShowModal);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal');
    if (originalClose) Object.defineProperty(HTMLDialogElement.prototype, 'close', originalClose);
    else Reflect.deleteProperty(HTMLDialogElement.prototype, 'close');
  });

  it('filters only the muted owner view and preserves removed markers and unmute controls', async () => {
    const { fixture, muted, messages } = await community();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    muted.set(new Set([MESSAGE.participantId!]));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('.chat-message')).toHaveLength(0);
    expect(fixture.nativeElement.textContent).toContain('Desfazer silenciamento');
    messages.set([{ ...MESSAGE, removed: true, participantId: null, nickname: null, text: null }]);
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('Mensagem removida');
    expect(fixture.nativeElement.textContent).toContain('Denunciar mensagem');
  });

  it('offers contextual login to visitors without exposing private actions', async () => {
    const { fixture, api } = await community(false);
    const link = fixture.nativeElement.querySelector('.chat-menu a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe(
      '/login/start?returnTo=%2Feventos%2F33333333-3333-3333-3333-333333333333',
    );
    fixture.componentInstance.openReport(MESSAGE);
    expect(fixture.componentInstance.reporting()).toBeNull();
    expect(api.report).not.toHaveBeenCalled();
  });

  it('cancels the report without a POST and restores focus to the triggering action', async () => {
    const { fixture, api } = await community();
    const trigger = fixture.nativeElement.querySelector(
      '.chat-menu button:last-child',
    ) as HTMLButtonElement;
    trigger.focus();
    fixture.componentInstance.openReport(MESSAGE);
    fixture.detectChanges();
    const dialog = fixture.nativeElement.querySelector('dialog.chat-dialog') as HTMLDialogElement;
    dialog.dispatchEvent(new Event('cancel'));
    await fixture.whenStable();
    expect(api.report).not.toHaveBeenCalled();
    expect(fixture.componentInstance.reporting()).toBeNull();
    expect(document.activeElement).toBe(trigger);
  });

  it('cycles Tab and Shift+Tab between enabled dialog controls', async () => {
    const { fixture } = await community();
    fixture.componentInstance.openReport(MESSAGE);
    await fixture.whenStable();
    const dialog = fixture.nativeElement.querySelector('dialog.chat-dialog') as HTMLDialogElement;
    const first = dialog.querySelector('select')!;
    const last = dialog.querySelector('button:last-child') as HTMLButtonElement;
    last.focus();
    const forward = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    last.dispatchEvent(forward);
    expect(forward.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(first);
    const backward = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true,
    });
    first.dispatchEvent(backward);
    expect(backward.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(last);
    fixture.componentInstance.reportDone.set(true);
    await fixture.whenStable();
    const cancel = dialog.querySelector('button')!;
    cancel.focus();
    cancel.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true }),
    );
    expect(document.activeElement).toBe(cancel);
  });

  it('keeps the same report idempotency key on retry and purges a delayed callback on logout', async () => {
    const { fixture, api, session } = await community();
    api.report.mockReturnValueOnce(throwError(() => new Error('lost acknowledgement')));
    fixture.componentInstance.openReport(MESSAGE);
    await fixture.componentInstance.submitReport();
    const key = api.report.mock.calls[0][2];
    await fixture.componentInstance.submitReport();
    expect(api.report.mock.calls[1][2]).toBe(key);
    expect(fixture.componentInstance.reportDone()).toBe(true);
    fixture.componentInstance.cancelReport();
    const delayed = new Subject<{ reportId: string; reportedAt: string }>();
    api.report.mockReturnValueOnce(delayed);
    fixture.componentInstance.openReport(MESSAGE);
    const posting = fixture.componentInstance.submitReport();
    session.isAuthenticated.set(false);
    session.revision.update((epoch) => epoch + 1);
    await fixture.whenStable();
    delayed.next({ reportId: MESSAGE.messageId, reportedAt: MESSAGE.postedAt });
    delayed.complete();
    await posting;
    expect(fixture.componentInstance.reporting()).toBeNull();
    expect(fixture.componentInstance.reportNotice()).toBeNull();
  });
});
