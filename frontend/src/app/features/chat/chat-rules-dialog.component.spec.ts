import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { firstValueFrom, of } from 'rxjs';
import { beforeAll, expect, it, vi } from 'vitest';
import { SessionStore } from '../../core/session/session.store';
import { ChatRulesPreferenceService } from '../../api/generated/api/chat-rules-preference.service';
import { ChatRulesApiAdapter } from './chat-rules-api.adapter';
import { ChatRulesDialogComponent } from './chat-rules-dialog.component';

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
});

it('uses only the official private client, disables transfer cache and rejects a false dismissal receipt', async () => {
  const api = { getChatRulesPreference: vi.fn(() => of({ dismissed: false })), dismissChatRulesPreference: vi.fn(() => of({ dismissed: false })) };
  TestBed.configureTestingModule({ providers: [ChatRulesApiAdapter, { provide: ChatRulesPreferenceService, useValue: api }] });
  const adapter = TestBed.inject(ChatRulesApiAdapter);
  expect(await firstValueFrom(adapter.read())).toBe(false);
  expect(api.getChatRulesPreference).toHaveBeenCalledWith('body', false, { transferCache: false });
  await expect(firstValueFrom(adapter.dismiss())).rejects.toThrow('Invalid chat rules preference');
});

it('provides a labelled native dialog, closes on Escape and restores the manual trigger without legal acceptance', async () => {
  const api = { read: vi.fn(() => of(false)), dismiss: vi.fn(() => of(true)) };
  TestBed.configureTestingModule({ providers: [
    { provide: SessionStore, useValue: { revision: signal(1), isAuthenticated: signal(false) } },
    { provide: ChatRulesApiAdapter, useValue: api },
  ] });
  const fixture = TestBed.createComponent(ChatRulesDialogComponent); await fixture.whenStable();
  document.body.append(fixture.nativeElement);
  try {
    const trigger: HTMLButtonElement = fixture.nativeElement.querySelector('button'); trigger.focus(); trigger.click(); await fixture.whenStable();
    const dialog: HTMLDialogElement = fixture.nativeElement.querySelector('dialog');
    expect(dialog.open).toBe(true); expect(dialog.getAttribute('aria-labelledby')).toBeTruthy();
    expect(dialog.textContent).toContain('não significa aceite jurídico');
    const close: HTMLButtonElement = dialog.querySelector('button')!;
    for (const shiftKey of [false, true]) {
      const key = new KeyboardEvent('keydown', { key: 'Tab', shiftKey, bubbles: true, cancelable: true });
      close.dispatchEvent(key); expect(key.defaultPrevented).toBe(true); expect(document.activeElement).toBe(close);
    }
    dialog.dispatchEvent(new Event('cancel', { cancelable: true })); await fixture.whenStable();
    expect(dialog.open).toBe(false); expect(document.activeElement).toBe(trigger);
    expect(api.read).not.toHaveBeenCalled(); expect(api.dismiss).not.toHaveBeenCalled();
  } finally { fixture.nativeElement.remove(); }
});
