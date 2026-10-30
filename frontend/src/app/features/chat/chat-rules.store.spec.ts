import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, Subject, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { SessionStore } from '../../core/session/session.store';
import { ChatRulesApiAdapter } from './chat-rules-api.adapter';
import { ChatRulesStore } from './chat-rules.store';

function setup(authenticated = true) {
  const session = { revision: signal(1), isAuthenticated: signal(authenticated) };
  const api = { read: vi.fn(() => of(false)), dismiss: vi.fn(() => of(true)) };
  TestBed.configureTestingModule({ providers: [ChatRulesStore,
    { provide: SessionStore, useValue: session }, { provide: ChatRulesApiAdapter, useValue: api }] });
  return { store: TestBed.inject(ChatRulesStore), api, session };
}

describe('ChatRulesStore', () => {
  it('opens only the first authenticated community visit, and reads again on a new visit', async () => {
    const { store, api } = setup();
    await store.start(true); expect(store.opened()).toBe(true);
    await store.close(); expect(store.opened()).toBe(false); expect(store.dismissed()).toBe(true);
    api.read.mockReturnValueOnce(of(true)); await store.start(true);
    expect(store.opened()).toBe(false); expect(api.dismiss).toHaveBeenCalledTimes(1);
    store.openManual(); expect(store.opened()).toBe(true);
    await store.close(); expect(api.dismiss).toHaveBeenCalledTimes(1);
  });

  it('keeps guests and match visits manual without calling the private API', async () => {
    const guest = setup(false); await guest.store.start(true);
    expect(guest.store.opened()).toBe(false);
    guest.store.openManual(); await guest.store.close();
    expect(guest.api.read).not.toHaveBeenCalled(); expect(guest.api.dismiss).not.toHaveBeenCalled();
    guest.session.isAuthenticated.set(true); await guest.store.start(false);
    expect(guest.store.opened()).toBe(false); expect(guest.api.read).not.toHaveBeenCalled();
  });

  it('reports failed reads without claiming dismissal and retries the real read explicitly', async () => {
    const { store, api } = setup(); api.read.mockReturnValueOnce(throwError(() => new Error('offline')));
    await store.start(true); expect(store.status()).toBe('error'); expect(store.dismissed()).toBe(false);
    expect(store.opened()).toBe(false); expect(store.error()).toContain('consultar');
    await store.retry(); expect(store.opened()).toBe(true); expect(api.read).toHaveBeenCalledTimes(2);
  });

  it('closes despite failed saving and never claims synchronization or retries a POST automatically', async () => {
    const { store, api } = setup(); await store.start(true);
    api.dismiss.mockReturnValueOnce(throwError(() => new Error('offline')));
    await store.close(); expect(store.opened()).toBe(false); expect(store.status()).toBe('sync-error');
    expect(store.error()).toContain('não foi salva'); expect(store.dismissed()).toBe(false);
    expect(api.dismiss).toHaveBeenCalledTimes(1);
    await store.retry(); expect(store.dismissed()).toBe(true); expect(store.status()).toBe('ready');
  });

  it('hides the old preference immediately and ignores late reads when the account changes', async () => {
    const { store, api, session } = setup(); const late = new Subject<boolean>();
    api.read.mockReturnValueOnce(late); const pending = store.start(true);
    session.revision.update(v => v + 1); expect(store.dismissed()).toBe(false); expect(store.opened()).toBe(false);
    const next = store.start(true); late.next(true); late.complete(); await pending; await next;
    expect(store.opened()).toBe(true); expect(store.dismissed()).toBe(false); expect(late.observed).toBe(false);
  });

  it('cancels an outstanding dismissal on logout without carrying success into the next session', async () => {
    const { store, api, session } = setup(); await store.start(true);
    const late = new Subject<boolean>(); api.dismiss.mockReturnValueOnce(late); const pending = store.close();
    expect(store.status()).toBe('dismiss-saving'); session.isAuthenticated.set(false);
    expect(store.dismissed()).toBe(false); await store.start(true); late.next(true); late.complete(); await pending;
    expect(store.status()).toBe('ready'); expect(store.dismissed()).toBe(false); expect(late.observed).toBe(false);
  });
});
