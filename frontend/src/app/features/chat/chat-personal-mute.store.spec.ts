import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChatPersonalMuteStore } from './chat-personal-mute.store';
import { ChatCommunityApiAdapter } from './chat-community-api.adapter';
import { SessionStore } from '../../core/session/session.store';
import type { ChatPersonalMuteState } from './chat-community-api.adapter';

const TARGET = '11111111-1111-1111-1111-111111111111';

function setup(api: Partial<ChatCommunityApiAdapter>) {
  const session = {
    isAuthenticated: signal(true),
    revision: signal(1),
  };
  TestBed.configureTestingModule({
    providers: [
      ChatPersonalMuteStore,
      { provide: ChatCommunityApiAdapter, useValue: api },
      { provide: SessionStore, useValue: session },
    ],
  });
  return { store: TestBed.inject(ChatPersonalMuteStore), session };
}

describe('ChatPersonalMuteStore', () => {
  it('applies a mute immediately and commits the server version', async () => {
    const api = {
      readMutes: vi.fn(() => of({ version: 0, participantIds: new Set<string>() })),
      mute: vi.fn(() => of({ version: 1, participantIds: new Set([TARGET]) })),
      unmute: vi.fn(),
    };
    const { store } = setup(api);
    await store.open();
    const pending = store.setMuted(TARGET, true);
    expect(store.isMuted(TARGET)).toBe(true);
    await pending;
    expect(api.mute).toHaveBeenCalledWith(TARGET, 0);
    expect(store.version()).toBe(1);
    expect(store.error()).toBeNull();
  });

  it('rolls back an optimistic mute and exposes a retryable error', async () => {
    const api = {
      readMutes: vi.fn(() => of({ version: 3, participantIds: new Set<string>() })),
      mute: vi.fn(() => throwError(() => new Error('offline'))),
      unmute: vi.fn(),
    };
    const { store } = setup(api);
    await store.open();
    await store.setMuted(TARGET, true);
    expect(store.isMuted(TARGET)).toBe(false);
    expect(store.error()).toContain('Nao foi possivel');
    expect(store.notice()).toContain('Nao foi possivel');
  });

  it('clears private ids when the session is logged out', async () => {
    const api = {
      readMutes: vi.fn(() => of({ version: 2, participantIds: new Set([TARGET]) })),
      mute: vi.fn(),
      unmute: vi.fn(),
    };
    const { store, session } = setup(api);
    await store.open();
    expect(store.isMuted(TARGET)).toBe(true);
    session.isAuthenticated.set(false);
    store.clear();
    expect(store.participantIds().size).toBe(0);
    expect(store.version()).toBe(0);
  });

  it('refetches the version before retrying a conflict', async () => {
    const api = {
      readMutes: vi
        .fn()
        .mockReturnValueOnce(of({ version: 0, participantIds: new Set() }))
        .mockReturnValue(of({ version: 4, participantIds: new Set() })),
      mute: vi
        .fn()
        .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })))
        .mockReturnValue(of({ version: 5, participantIds: new Set([TARGET]) })),
    };
    const { store } = setup(api);
    await store.open();
    await store.setMuted(TARGET, true);
    expect(store.error()).toContain('outro dispositivo');
    expect(store.isMuted(TARGET)).toBe(false);
    await store.retry();
    expect(api.mute).toHaveBeenLastCalledWith(TARGET, 4);
    expect(store.version()).toBe(5);
    expect(store.error()).toBeNull();
  });

  it('replays a failed command before draining push hints received during its retry read', async () => {
    const retryRead = new Subject<ChatPersonalMuteState>();
    const queuedRead = new Subject<ChatPersonalMuteState>();
    const api = {
      readMutes: vi
        .fn()
        .mockReturnValueOnce(of({ version: 0, participantIds: new Set() }))
        .mockReturnValueOnce(retryRead)
        .mockReturnValue(queuedRead),
      mute: vi
        .fn()
        .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 409 })))
        .mockReturnValue(of({ version: 5, participantIds: new Set([TARGET]) })),
    };
    const { store } = setup(api);
    await store.open();
    await store.setMuted(TARGET, true);
    const retry = store.retry();
    await store.refresh();
    retryRead.next({ version: 4, participantIds: new Set() });
    retryRead.complete();
    await retry;
    expect(api.mute).toHaveBeenCalledTimes(2);
    expect(api.mute).toHaveBeenLastCalledWith(TARGET, 4);
    expect(store.isMuted(TARGET)).toBe(true);
    expect(store.error()).toBeNull();
    queuedRead.next({ version: 5, participantIds: new Set([TARGET]) });
    queuedRead.complete();
    await Promise.resolve();
  });

  it('does not revive private ids from a read response after logout', async () => {
    const response = new Subject<ChatPersonalMuteState>();
    const { store, session } = setup({ readMutes: () => response });
    const reading = store.open();
    session.isAuthenticated.set(false);
    session.revision.update((epoch) => epoch + 1);
    store.clear();
    response.next({ version: 3, participantIds: new Set([TARGET]) });
    response.complete();
    await reading;
    expect(store.participantIds().size).toBe(0);
    expect(store.error()).toBeNull();
  });

  it('does not revive a pending command after logout', async () => {
    const response = new Subject<ChatPersonalMuteState>();
    const { store, session } = setup({
      readMutes: () => of({ version: 0, participantIds: new Set<string>() }),
      mute: () => response,
    });
    await store.open();
    const command = store.setMuted(TARGET, true);
    expect(store.isMuted(TARGET)).toBe(true);
    session.isAuthenticated.set(false);
    session.revision.update((epoch) => epoch + 1);
    store.clear();
    response.next({ version: 1, participantIds: new Set([TARGET]) });
    response.complete();
    await command;
    expect(store.participantIds().size).toBe(0);
    expect(store.pending()).toBe(false);
  });

  it('serializes account commands and refreshes a hint received while saving', async () => {
    const response = new Subject<ChatPersonalMuteState>();
    const api = {
      readMutes: vi.fn(() => of({ version: 2, participantIds: new Set([TARGET]) })),
      mute: vi.fn(() => response),
    };
    const { store } = setup(api);
    await store.open();
    const pending = store.setMuted(TARGET, true);
    await store.setMuted('22222222-2222-2222-2222-222222222222', true);
    expect(api.mute).toHaveBeenCalledTimes(1);
    await store.refresh();
    response.next({ version: 3, participantIds: new Set([TARGET]) });
    response.complete();
    await pending;
    await Promise.resolve();
    expect(api.readMutes).toHaveBeenCalledTimes(2);
    expect(store.version()).toBe(3);
  });

  it('retries a failed initial read and applies a same-owner refresh', async () => {
    const api = {
      readMutes: vi
        .fn()
        .mockReturnValueOnce(throwError(() => new Error('offline')))
        .mockReturnValue(of({ version: 2, participantIds: new Set([TARGET]) })),
    };
    const { store } = setup(api);
    await store.open();
    expect(store.canChange()).toBe(false);
    await store.retry();
    expect(store.canChange()).toBe(true);
    expect(store.isMuted(TARGET)).toBe(true);
  });
});

describe('community generated API boundary', () => {
  function adapter() {
    TestBed.configureTestingModule({
      providers: [ChatCommunityApiAdapter, provideHttpClient(), provideHttpClientTesting()],
    });
    return {
      api: TestBed.inject(ChatCommunityApiAdapter),
      http: TestBed.inject(HttpTestingController),
    };
  }

  it('normalizes JSON arrays and sends quoted If-Match on unmute', async () => {
    const { api, http } = adapter();
    const pending = firstValueFrom(api.unmute(TARGET, 7));
    const request = http.expectOne(`/api/v1/chat/me/mutes/${TARGET}`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('If-Match')).toBe('"7"');
    expect(request.request.transferCache).toBe(false);
    request.flush({ version: 8, participantIds: [] });
    expect((await pending).participantIds.size).toBe(0);
    http.verify();
  });

  it('fails closed on invalid preference DTOs', async () => {
    const { api, http } = adapter();
    const pending = firstValueFrom(api.readMutes());
    const assertion = expect(pending).rejects.toThrow('INVALID_CHAT_MUTE_STATE');
    http
      .expectOne('/api/v1/chat/me/mutes')
      .flush({ version: 1, participantIds: ['not-a-participant'] });
    await assertion;
    http.verify();
  });

  it('keeps the caller idempotency key and accepts only closed report reasons', async () => {
    const { api, http } = adapter();
    const pending = firstValueFrom(api.report(TARGET, 'SPAM', TARGET));
    const request = http.expectOne(`/api/v1/chat/messages/${TARGET}/reports`);
    expect(request.request.headers.get('Idempotency-Key')).toBe(TARGET);
    expect(request.request.body).toEqual({ reason: 'SPAM' });
    request.flush({ reportId: TARGET, reportedAt: '2026-10-07T00:00:00Z' });
    expect((await pending).reportId).toBe(TARGET);
    expect(() => api.report(TARGET, 'invented' as never, TARGET)).toThrow('INVALID_CHAT_REPORT');
    http.verify();
  });
});
