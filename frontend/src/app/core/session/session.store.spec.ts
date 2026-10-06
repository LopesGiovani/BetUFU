import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { describe, it, expect } from 'vitest';
import { SessionService } from '../../api/generated/api/session.service';
import { SessionStore } from './session.store';
import type { SessionStateResponse } from '../../api/generated/model/session-state-response';
import type { CurrentDeviceLogoutResponse } from '../../api/generated/model/current-device-logout-response';

describe('SessionStore concurrent logout', () => {
  it('revokes local authentication before a delayed logout acknowledgement', async () => {
    const logout = new Subject<CurrentDeviceLogoutResponse>();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SessionService,
          useValue: {
            getSession: () => of({ authenticated: true }),
            logoutCurrentSession: () => logout,
          },
        },
      ],
    });
    const store = TestBed.inject(SessionStore);
    await store.loadSession();
    expect(store.isAuthenticated()).toBe(true);
    const pending = store.logoutCurrentDevice();
    expect(store.isAuthenticated()).toBe(false);
    await store.loadSession();
    expect(store.isAuthenticated()).toBe(false);
    logout.next({ endSessionUrl: null } as CurrentDeviceLogoutResponse);
    await pending;
    expect(store.isLoggedOut()).toBe(true);
  });

  it('a session read started before logout cannot restore authentication', async () => {
    const read = new Subject<SessionStateResponse>();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: SessionService,
          useValue: {
            getSession: () => read,
            logoutCurrentSession: () => of({ endSessionUrl: null }),
          },
        },
      ],
    });
    const store = TestBed.inject(SessionStore);
    const pending = store.loadSession();
    await store.logoutCurrentDevice();
    read.next({ authenticated: true } as SessionStateResponse);
    await pending;
    expect(store.isLoggedOut()).toBe(true);
  });
});
