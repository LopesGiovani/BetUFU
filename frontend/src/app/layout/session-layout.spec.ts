import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SessionStore } from '../core/session/session.store';
import { SessionService } from '../api/generated/api/session.service';
import { AuthenticatedLayoutComponent } from './authenticated-layout.component';
import type { SessionStateResponse } from '../api/generated/model/session-state-response';
import type { CurrentDeviceLogoutResponse } from '../api/generated/model/current-device-logout-response';

declare const require: any;
declare const process: any;
const fs = require('node:fs');
const path = require('node:path');

describe('SessionStore & AuthenticatedLayoutComponent (D-01, D-08, D-09, D-16, IDEN-03, IDEN-04)', () => {
  let store: SessionStore;
  let mockSessionService: Partial<SessionService>;
  let fixture: ComponentFixture<AuthenticatedLayoutComponent>;
  let component: AuthenticatedLayoutComponent;

  beforeEach(async () => {
    // Spies para APIs de armazenamento do navegador
    vi.spyOn(Storage.prototype, 'setItem');
    vi.spyOn(Storage.prototype, 'getItem');
    if (typeof indexedDB !== 'undefined') {
      vi.spyOn(indexedDB, 'open');
    }

    mockSessionService = {
      getSession: vi.fn(),
      logoutCurrentSession: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayoutComponent],
      providers: [
        SessionStore,
        { provide: SessionService, useValue: mockSessionService },
      ],
    }).compileComponents();

    store = TestBed.inject(SessionStore);
    store.reset();
  });

  it('Estado Inicial: Store inicia em status "checking" com dados nulos', () => {
    expect(store.status()).toBe('checking');
    expect(store.isChecking()).toBe(true);
    expect(store.isAuthenticated()).toBe(false);
    expect(store.displayName()).toBeNull();
    expect(store.idleTimeout()).toBeNull();
    expect(store.endSessionUrl()).toBeNull();
  });

  it('Transição: loadSession com authenticated=true move para status "authenticated"', async () => {
    const mockResponse: SessionStateResponse = {
      authenticated: true,
      displayName: 'LopesGiovani',
      idleTimeout: 'PT8H',
      absoluteTimeout: null,
      lastValidActivityAt: '2026-08-18T19:00:00Z',
    };

    (mockSessionService.getSession as any).mockReturnValue(of(mockResponse));

    await store.loadSession();

    expect(store.status()).toBe('authenticated');
    expect(store.isAuthenticated()).toBe(true);
    expect(store.isChecking()).toBe(false);
    expect(store.displayName()).toBe('LopesGiovani');
    expect(store.idleTimeout()).toBe('PT8H');
    expect(store.error()).toBeNull();
    expect(mockSessionService.getSession).toHaveBeenCalledTimes(1);
  });

  it('Transição: loadSession com authenticated=false move para status "expired" e limpa estado', async () => {
    const mockResponse: SessionStateResponse = {
      authenticated: false,
      displayName: null,
      idleTimeout: 'PT8H',
      absoluteTimeout: null,
      lastValidActivityAt: null,
    };

    (mockSessionService.getSession as any).mockReturnValue(of(mockResponse));

    await store.loadSession();

    expect(store.status()).toBe('expired');
    expect(store.isExpired()).toBe(true);
    expect(store.isAuthenticated()).toBe(false);
    expect(store.displayName()).toBeNull();
    expect(store.idleTimeout()).toBeNull();
  });

  it('Transição: loadSession com erro HTTP/rede trata como "expired" (fail-closed)', async () => {
    (mockSessionService.getSession as any).mockReturnValue(
      throwError(() => new Error('Http failure response for /api/v1/session: 401 Unauthorized')),
    );

    await store.loadSession();

    expect(store.status()).toBe('expired');
    expect(store.isExpired()).toBe(true);
    expect(store.displayName()).toBeNull();
    expect(store.error()).toContain('401 Unauthorized');
  });

  it('Transição: logoutCurrentDevice envia um único POST e move para "logged_out" com endSessionUrl', async () => {
    const mockLogoutResponse: CurrentDeviceLogoutResponse = {
      status: 'LOGGED_OUT',
      endSessionUrl: 'http://localhost:8080/realms/betufu/protocol/openid-connect/logout?client_id=betufu-web',
    };

    (mockSessionService.logoutCurrentSession as any).mockReturnValue(of(mockLogoutResponse));

    const result = await store.logoutCurrentDevice();

    expect(result).toEqual(mockLogoutResponse);
    expect(store.status()).toBe('logged_out');
    expect(store.isLoggedOut()).toBe(true);
    expect(store.displayName()).toBeNull();
    expect(store.endSessionUrl()).toBe(mockLogoutResponse.endSessionUrl);
    expect(mockSessionService.logoutCurrentSession).toHaveBeenCalledTimes(1);
  });

  it('Segurança D-08 / D-09 / D-16: Zero chamadas a localStorage, sessionStorage ou IndexedDB', async () => {
    const mockResponse: SessionStateResponse = {
      authenticated: true,
      displayName: 'DevUFU',
      idleTimeout: 'PT8H',
      absoluteTimeout: null,
      lastValidActivityAt: '2026-08-18T19:30:00Z',
    };

    (mockSessionService.getSession as any).mockReturnValue(of(mockResponse));
    await store.loadSession();

    const mockLogout: CurrentDeviceLogoutResponse = {
      status: 'LOGGED_OUT',
      endSessionUrl: null,
    };
    (mockSessionService.logoutCurrentSession as any).mockReturnValue(of(mockLogout));
    await store.logoutCurrentDevice();

    expect(Storage.prototype.setItem).not.toHaveBeenCalled();
    expect(Storage.prototype.getItem).not.toHaveBeenCalled();
    if (typeof indexedDB !== 'undefined') {
      expect(indexedDB.open).not.toHaveBeenCalled();
    }
  });

  it('Contrato e Isolamento de Código: SessionStore não contém polling, retry nem tokens manuais', () => {
    const basePath = typeof process !== 'undefined' && process.cwd ? process.cwd() : '';
    const storePath = path.resolve(basePath, 'src/app/core/session/session.store.ts');
    const storeContent: string = fs.readFileSync(storePath, 'utf8');

    // Ausência de polling ou loops assíncronos
    expect(storeContent).not.toContain('setInterval');
    expect(storeContent).not.toContain('timer(');
    expect(storeContent).not.toContain('interval(');

    // Ausência de retry automático em chamadas
    expect(storeContent).not.toContain('retry(');
    expect(storeContent).not.toContain('retryWhen(');

    // Ausência de chamadas a storage do navegador
    expect(storeContent).not.toContain('localStorage');
    expect(storeContent).not.toContain('sessionStorage');
    expect(storeContent).not.toContain('indexedDB');
    expect(storeContent).not.toContain('document.cookie');

    // Ausência de manipulação manual de Bearer token
    expect(storeContent).not.toContain('Bearer ');
    expect(storeContent).not.toContain('Authorization');
  });

  it('Layout UI: checking renderiza mensagem de carregamento sem router-outlet ou dados sensíveis', () => {
    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: false }));
    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    component = fixture.componentInstance;
    // Sem disparar loadSession() ainda, o estado é checking
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.session-status-container.checking')).not.toBeNull();
    expect(compiled.textContent).toContain('Preparando sua sessão…');
    expect(compiled.querySelector('.layout-header')).toBeNull();
    expect(compiled.querySelector('main#main-content')).toBeNull();
  });

  it('Layout UI: authenticated renderiza header com marca, nome do usuário e botão Sair', async () => {
    (mockSessionService.getSession as any).mockReturnValue(
      of({
        authenticated: true,
        displayName: 'GiovaniLopes',
        idleTimeout: 'PT8H',
      }),
    );

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.layout-header')).not.toBeNull();
    expect(compiled.querySelector('.brand-name')?.textContent).toContain('BETUFU');
    expect(compiled.querySelector('#user-display-name')?.textContent).toContain('GiovaniLopes');
    expect(compiled.querySelector('.btn-logout')).not.toBeNull();
    expect(compiled.querySelector('main#main-content')).not.toBeNull();
  });

  it('Layout UI: expired oculta router-outlet e exibe aviso com CTA para entrar', async () => {
    (mockSessionService.getSession as any).mockReturnValue(
      of({
        authenticated: false,
        displayName: null,
        idleTimeout: 'PT8H',
      }),
    );

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.session-status-container.expired')).not.toBeNull();
    expect(compiled.textContent).toContain('Sessão expirada ou encerrada');
    expect(compiled.querySelector('.layout-header')).toBeNull();
    expect(compiled.querySelector('main#main-content')).toBeNull();
  });

  it('Acessibilidade UI: Skip link está presente apontando para main#main-content com tabindex="-1"', async () => {
    (mockSessionService.getSession as any).mockReturnValue(
      of({
        authenticated: true,
        displayName: 'UserAcessivel',
        idleTimeout: 'PT8H',
      }),
    );

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const skipLink = compiled.querySelector('a.skip-link') as HTMLAnchorElement;
    expect(skipLink).not.toBeNull();
    expect(skipLink.getAttribute('href')).toBe('#main-content');
    expect(skipLink.textContent).toContain('Pular para o conteúdo principal');

    const main = compiled.querySelector('main#main-content') as HTMLElement;
    expect(main).not.toBeNull();
    expect(main.getAttribute('tabindex')).toBe('-1');
  });
});
