import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { SessionStore } from '../../core/session/session.store';
import { EmergencyPauseApiAdapter, type PauseAvailability } from './emergency-pause-api.adapter';
import { EmergencyPauseStore } from './emergency-pause.store';
import { EmergencyPausePageComponent } from './emergency-pause-page.component';
import { EmergencyPausesService } from '../../api/generated/api/emergency-pauses.service';

const ID = '11111111-1111-4111-8111-111111111111';
const FACT = { factId: ID, kind: 'CHANNEL' as const, targetId: ID, action: 'PAUSE' as const, reason: 'Containment', resumedPauseId: null, recordedAt: '2026-10-29T00:00:00Z' };

function setup() {
  const session = { revision: signal(0), isAuthenticated: signal(true) };
  const api = {
    list: vi.fn(() => of([FACT])), command: vi.fn(() => of(FACT)),
    chat: vi.fn<() => ReturnType<EmergencyPauseApiAdapter['chat']>>(() => of<PauseAvailability>('available')),
    market: vi.fn<() => ReturnType<EmergencyPauseApiAdapter['market']>>(() => of<PauseAvailability>('available')),
  };
  TestBed.configureTestingModule({ providers: [EmergencyPauseStore, { provide: EmergencyPauseApiAdapter, useValue: api }, { provide: SessionStore, useValue: session }, provideRouter([])] });
  const store = TestBed.inject(EmergencyPauseStore);
  TestBed.tick();
  return { store, api, session };
}

describe('EmergencyPauseStore', () => {
  it('deduplicates invalidations and uses participant reads without an admin list', async () => {
    const { store, api } = setup();
    const pending = new Subject<PauseAvailability>();
    api.market.mockReturnValueOnce(pending);
    store.open('market', ID);
    expect(store.availability()).toBe('unknown');
    void store.refresh(); void store.refresh();
    expect(api.market).toHaveBeenCalledTimes(1);
    pending.next('paused'); pending.complete();
    await vi.waitFor(() => expect(api.market).toHaveBeenCalledTimes(2));
    await vi.waitFor(() => expect(store.availability()).toBe('available'));
    expect(api.list).not.toHaveBeenCalled();
  });

  it('fails closed on transport errors and recovers only after a real retry', async () => {
    const { store, api } = setup();
    api.chat.mockReturnValueOnce(throwError(() => new Error('offline')));
    store.open('chat', null);
    await vi.waitFor(() => expect(store.availability()).toBe('error'));
    expect(store.notice()).not.toContain('pausados');
    await store.refresh();
    expect(store.availability()).toBe('available');
  });

  it('cancels an old account request and clears admin state on session replacement', async () => {
    const { store, api, session } = setup();
    await store.load();
    const pending = new Subject<PauseAvailability>();
    api.chat.mockReturnValueOnce(pending);
    store.open('chat', null);
    session.revision.update(v => v + 1); TestBed.tick();
    expect(pending.observed).toBe(false);
    expect(store.administrator()).toBe(false);
    expect(store.pauses()).toEqual([]);
    expect(store.availability()).toBe('unknown');
    pending.next('available');
    expect(store.availability()).toBe('unknown');
    store.open('chat', null);
    await vi.waitFor(() => expect(store.availability()).toBe('available'));
  });

  it('denies a moderator and validates UUID/reason before mutating', async () => {
    const { store, api } = setup();
    api.list.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 403 })));
    await store.load();
    expect(store.administrator()).toBe(false);
    expect(store.error()).toContain('administrador geral');
    await store.command({ clientCommandId: ID, kind: 'CHANNEL', targetId: ID, action: 'PAUSE', reason: 'test' });
    expect(api.command).not.toHaveBeenCalled();
    await store.load();
    await store.command({ clientCommandId: ID, kind: 'CHANNEL', targetId: 'forged', action: 'PAUSE', reason: '' });
    expect(api.command).not.toHaveBeenCalled();
  });
});

describe('EmergencyPausePageComponent', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
    HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  });

  it('requires confirmation, cancellation sends nothing and resume references the original fact', async () => {
    const { api } = setup();
    const fixture = TestBed.createComponent(EmergencyPausePageComponent);
    await fixture.whenStable();
    const page = fixture.componentInstance;
    page.prepare(FACT);
    expect(page.command()?.resumedPauseId).toBe(FACT.factId);
    expect(page.impact()).toContain('historico');
    page.cancel();
    expect(api.command).not.toHaveBeenCalled();
    page.prepare(FACT); page.reason.set('Verified recovery');
    await page.confirm();
    expect(api.command).toHaveBeenCalledOnce();
    expect(api.command.mock.calls[0]).toBeDefined();
    expect(page.command()).toBeNull();
  });
});

it('rejects malformed availability instead of guessing pause state', async () => {
  const api = { getGlobalChatAvailability: vi.fn(() => of({ state: 'AVAILABLE', messagesPaused: true, revision: null })) };
  TestBed.configureTestingModule({ providers: [EmergencyPauseApiAdapter, { provide: EmergencyPausesService, useValue: api }] });
  await expect(new Promise((resolve, reject) => TestBed.inject(EmergencyPauseApiAdapter).chat(null).subscribe({ next: resolve, error: reject }))).rejects.toThrow('Invalid availability');
});
