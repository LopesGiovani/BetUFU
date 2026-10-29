import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { SessionStore } from '../../core/session/session.store';
import { PrivacyRightsApiAdapter } from './privacy-rights-api.adapter';
import { PrivacyRightsStore } from './privacy-rights.store';
import { PrivacyRightsPageComponent } from './privacy-rights-page.component';
import type { PrivacyCase } from '../../api/generated/model/privacy-case';
import { PrivacyRightsService } from '../../api/generated/api/privacy-rights.service';
import { OperationalCapabilitiesService } from '../../api/generated/api/operational-capabilities.service';
import { firstValueFrom } from 'rxjs';

const ID = '11111111-1111-4111-8111-111111111111';
const CASE: PrivacyCase = { caseId: ID, subjectKind: 'ACCOUNT', requestKind: 'ACCESS', state: 'PENDING', requestedAt: '2026-10-29T00:00:00Z', timeline: [] };
function setup() {
  const session = { revision: signal(0), isAuthenticated: signal(true) };
  const api = { list: vi.fn(() => of([CASE])), view: vi.fn(() => of(CASE)),
    request: vi.fn(() => of({ caseId: ID, state: 'PENDING', requestedAt: CASE.requestedAt })),
    respond: vi.fn(() => of({})), prepareExport: vi.fn(() => of({ exportId: ID, data: {}, blockedClasses: ['LOGS'], complete: false })),
    download: vi.fn(() => of({ exportId: ID, data: {}, blockedClasses: ['LOGS'], complete: false })),
    administrator: vi.fn(() => of(true)) };
  TestBed.configureTestingModule({ providers: [PrivacyRightsStore, { provide: SessionStore, useValue: session }, { provide: PrivacyRightsApiAdapter, useValue: api }] });
  const store = TestBed.inject(PrivacyRightsStore); TestBed.tick();
  return { store, session, api };
}

describe('PrivacyRightsStore', () => {
  it('uses own-case pagination and sends no supplied account identity or proof', async () => {
    const { store, api } = setup();
    await store.start(false);
    expect(api.list).toHaveBeenCalledWith(0);
    await store.request('DELETION');
    expect(api.request).toHaveBeenCalledWith({ subjectKind: 'ACCOUNT', subjectRef: null, requestKind: 'DELETION', proofReference: null, verifiedChannel: 'AUTHENTICATED_SESSION' });
    expect(store.selected()?.state).toBe('PENDING');
    expect(api.respond).not.toHaveBeenCalled();
  });

  it('hides private data immediately on session replacement and ignores a late response', async () => {
    const { store, api, session } = setup();
    await store.start(false); await store.open(ID);
    const late = new Subject<PrivacyCase>(); api.view.mockReturnValueOnce(late);
    const pending = store.open(ID);
    session.revision.update(v => v + 1);
    expect(store.cases()).toEqual([]); expect(store.selected()).toBeNull();
    TestBed.tick();
    late.next({ ...CASE, state: 'RESPONDED' }); late.complete(); await pending;
    expect(store.selected()).toBeNull(); expect(late.observed).toBe(false);
  });

  it('requires current administrator capability and never auto-completes a partial export', async () => {
    const { store, api } = setup();
    api.administrator.mockReturnValueOnce(of(false)); await store.start(true);
    await store.respond('APPROVE', 'Done');
    expect(api.respond).not.toHaveBeenCalled(); expect(store.administrator()).toBe(false);
    await store.start(true); await store.open(ID); await store.prepareExport();
    expect(store.artifact()?.complete).toBe(false);
    expect(store.artifact()?.blockedClasses).toEqual(['LOGS']); expect(api.respond).not.toHaveBeenCalled();
  });

  it('never follows a late POST with private lookups in a replacement session', async () => {
    const { store, api, session } = setup(); await store.start(false);
    const pending = new Subject<{ caseId: string; state: string; requestedAt: string }>();
    api.request.mockReturnValueOnce(pending);
    const submitted = store.request('ACCESS');
    session.revision.update(value => value + 1); TestBed.tick();
    pending.next({ caseId: ID, state: 'PENDING', requestedAt: CASE.requestedAt }); pending.complete();
    expect(await submitted).toBe(false); expect(api.view).not.toHaveBeenCalled();
  });

  it('removes an earlier case on forbidden detail and reports uncertainty after a failed mutation', async () => {
    const { store, api } = setup(); await store.start(false); await store.open(ID);
    api.view.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 403 })));
    await store.open(ID); expect(store.selected()).toBeNull(); expect(store.error()).toContain('acesso');
    api.request.mockReturnValueOnce(throwError(() => new Error('offline')));
    await store.request('ACCESS'); expect(store.error()).toContain('nao confirmado');
    expect(api.request).toHaveBeenCalledTimes(1);
  });

  it('rejects an unverified athlete procedure before POST and clears artifact on logout', async () => {
    const { store, api, session } = setup(); await store.start(true);
    await store.request('ACCESS', { athleteId: ID, proof: 'restricted-reference', verified: false });
    expect(api.request).not.toHaveBeenCalled();
    await store.open(ID); await store.download(); expect(store.artifact()).not.toBeNull();
    session.isAuthenticated.set(false); expect(store.artifact()).toBeNull(); expect(store.administrator()).toBe(false);
  });
});

it('uses the generated private client without transfer cache and rejects inconsistent exports', async () => {
  const api = { getPrivacyCase: vi.fn(() => of(CASE)), downloadPrivacyExport: vi.fn(() => of({ exportId: ID, data: {}, complete: true, blockedClasses: ['LOGS'] })) };
  TestBed.configureTestingModule({ providers: [PrivacyRightsApiAdapter,
    { provide: PrivacyRightsService, useValue: api }, { provide: OperationalCapabilitiesService, useValue: {} }] });
  const adapter = TestBed.inject(PrivacyRightsApiAdapter);
  expect(await firstValueFrom(adapter.view(ID))).toEqual(CASE);
  expect(api.getPrivacyCase).toHaveBeenCalledWith({ caseId: ID }, 'body', false, { transferCache: false });
  await expect(firstValueFrom(adapter.download(ID))).rejects.toThrow('Invalid private export');
});

describe('privacy confirmation', () => {
  beforeAll(() => {
    HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', ''); };
    HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); };
  });
  it('keeps deletion as a request and cancellation sends no command', async () => {
    const { api } = setup(); const fixture = TestBed.createComponent(PrivacyRightsPageComponent);
    await fixture.whenStable(); const page = fixture.componentInstance;
    page.requestKind.set('DELETION'); page.prepare('request');
    expect(fixture.nativeElement.textContent).toContain('nao apaga');
    page.cancel(); expect(api.request).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('dialog').hasAttribute('open')).toBe(false);
  });
});
