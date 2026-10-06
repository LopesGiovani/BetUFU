import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { describe, it, expect, vi } from 'vitest';
import { ChatPanelComponent } from './chat-panel.component';
import { ChatReadStore } from './chat-read.store';
import { ChatOutboxStore } from './chat-outbox.store';
import { SessionStore } from '../../core/session/session.store';

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
          { provide: ChatOutboxStore, useValue: outbox },
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
