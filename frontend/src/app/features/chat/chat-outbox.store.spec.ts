import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionStore } from '../../core/session/session.store';
import { ChatWriteApiAdapter } from './chat-write-api.adapter';
import { ChatOutboxStore, validChatText } from './chat-outbox.store';

const owner = '11111111-1111-4111-8111-111111111111';
const receipt = {
  messageId: '22222222-2222-4222-8222-222222222222',
  postedAt: '2026-10-06T12:00:00Z',
  changeCursor: 1,
};

describe('volatile chat outbox', () => {
  const authenticated = signal(true);
  const revision = signal(0);
  let online = true;
  let store: ChatOutboxStore;
  let api: { eligibility: ReturnType<typeof vi.fn>; send: ReturnType<typeof vi.fn> };
  beforeEach(async () => {
    vi.useFakeTimers();
    authenticated.set(true);
    revision.set(0);
    online = true;
    vi.spyOn(navigator, 'onLine', 'get').mockImplementation(() => online);
    api = {
      eligibility: vi.fn(() => of({ owner, active: true, deadline: null, open: true })),
      send: vi.fn(() => of(receipt)),
    };
    TestBed.configureTestingModule({
      providers: [
        ChatOutboxStore,
        { provide: ChatWriteApiAdapter, useValue: api },
        { provide: SessionStore, useValue: { isAuthenticated: authenticated, revision } },
      ],
    });
    store = TestBed.inject(ChatOutboxStore);
    store.open(null);
    await vi.advanceTimersByTimeAsync(0);
  });
  afterEach(() => {
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
    vi.useRealTimers();
  });

  it('counts Unicode code points without truncation and rejects controls/unpaired surrogates', () => {
    expect(validChatText('😀'.repeat(280))).toBe(true);
    expect(validChatText('😀'.repeat(281))).toBe(false);
    for (const text of ['', '   ', 'x\n', '\u007f', '\ud800'])
      expect(validChatText(text)).toBe(false);
  });
  it('offline waits in memory and reconnect sends once', async () => {
    online = false;
    expect(store.enqueue('offline')).toBe(true);
    await vi.advanceTimersByTimeAsync(10000);
    expect(api.send).not.toHaveBeenCalled();
    online = true;
    window.dispatchEvent(new Event('online'));
    await vi.advanceTimersByTimeAsync(0);
    expect(api.send).toHaveBeenCalledOnce();
    expect(store.items()[0].status).toBe('sent');
  });
  it('lost acknowledgement retries the original key, never confirms an uncertain result', async () => {
    api.send.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 0 })));
    store.enqueue('original');
    await vi.advanceTimersByTimeAsync(0);
    expect(store.items()[0].status).toBe('pending');
    const first = api.send.mock.calls[0];
    await vi.advanceTimersByTimeAsync(3000);
    expect(api.send.mock.calls[1]).toEqual(first);
    expect(store.items()[0].status).toBe('sent');
  });
  it('multiple queued messages are serialized with at least three seconds between sends', async () => {
    store.enqueue('one');
    store.enqueue('two');
    await vi.advanceTimersByTimeAsync(0);
    expect(api.send).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(2999);
    expect(api.send).toHaveBeenCalledTimes(1);
    await vi.advanceTimersByTimeAsync(1);
    expect(api.send).toHaveBeenCalledTimes(2);
  });
  it.each([400, 401, 403, 404, 409])(
    'status %i stops and purges pending writes',
    async (status) => {
      api.send.mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
      store.enqueue('one');
      store.enqueue('two');
      await vi.advanceTimersByTimeAsync(60000);
      expect(api.send).toHaveBeenCalledOnce();
      expect(store.items().some((item) => item.status === 'pending')).toBe(false);
      expect(store.canCompose()).toBe(false);
    },
  );
  it('429 respects both Retry-After and retryAt, with a bounded retry budget', async () => {
    api.send.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            headers: new HttpHeaders({ 'Retry-After': '10' }),
            error: { retryAt: new Date(Date.now() + 15000).toISOString() },
          }),
      ),
    );
    store.enqueue('rate limited');
    await vi.advanceTimersByTimeAsync(14999);
    expect(api.send).toHaveBeenCalledOnce();
    await vi.advanceTimersByTimeAsync(1);
    expect(api.send).toHaveBeenCalledTimes(2);
    await vi.advanceTimersByTimeAsync(200000);
    expect(api.send.mock.calls.length).toBeLessThanOrEqual(5);
    expect(store.canCompose()).toBe(false);
  });
  it('a long authoritative Retry-After does not overflow the browser timer into an immediate retry', async () => {
    api.send.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            headers: new HttpHeaders({ 'Retry-After': '3000000' }),
          }),
      ),
    );
    store.enqueue('long delay');
    await vi.advanceTimersByTimeAsync(60000);
    expect(api.send).toHaveBeenCalledOnce();
    expect(store.items()[0].status).toBe('pending');
  });
  it('revalidates eligibility and refuses another owner before POST', async () => {
    api.eligibility.mockReturnValue(
      of({ owner: 'other', active: true, deadline: null, open: true }),
    );
    store.enqueue('private');
    await vi.advanceTimersByTimeAsync(10000);
    expect(api.send).not.toHaveBeenCalled();
    expect(store.items()).toEqual([]);
  });
  it('a current deadline closes the channel before sending', async () => {
    api.eligibility.mockReturnValue(
      of({ owner, active: true, deadline: new Date(Date.now()).toISOString(), open: true }),
    );
    store.enqueue('late');
    await vi.advanceTimersByTimeAsync(10000);
    expect(api.send).not.toHaveBeenCalled();
  });
  it.each([
    { active: false, open: true },
    { active: true, open: false },
  ])('current eligibility %j prevents POST', async (eligibility) => {
    api.eligibility.mockReturnValue(of({ owner, deadline: null, ...eligibility }));
    store.enqueue('restricted');
    await vi.advanceTimersByTimeAsync(10000);
    expect(api.send).not.toHaveBeenCalled();
    expect(store.canCompose()).toBe(false);
  });
  it('session invalidation cancels an in-flight request and all timers', async () => {
    const response = new Subject<typeof receipt>();
    api.send.mockReturnValue(response);
    store.enqueue('private');
    await vi.advanceTimersByTimeAsync(0);
    expect(response.observed).toBe(true);
    authenticated.set(false);
    revision.update((n) => n + 1);
    TestBed.tick();
    expect(response.observed).toBe(false);
    response.next(receipt);
    await vi.advanceTimersByTimeAsync(60000);
    expect(store.items()).toEqual([]);
  });
  it('close and page refresh discard the queue and callbacks', async () => {
    online = false;
    store.enqueue('volatile');
    store.close();
    online = true;
    window.dispatchEvent(new Event('online'));
    await vi.advanceTimersByTimeAsync(60000);
    expect(api.send).not.toHaveBeenCalled();
    store.open(null);
    await vi.advanceTimersByTimeAsync(0);
    online = false;
    store.enqueue('refresh');
    window.dispatchEvent(new Event('pagehide'));
    expect(store.items()).toEqual([]);
  });
});
