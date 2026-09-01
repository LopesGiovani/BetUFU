import { Component } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { Router, provideRouter } from '@angular/router';
import { SessionStore } from '../core/session/session.store';
import { SessionService } from '../api/generated/api/session.service';
import { OperationalCapabilitiesService } from '../api/generated/api/operational-capabilities.service';
import { OperationalPanelService } from '../api/generated/api/operational-panel.service';
import { RoleService } from '../api/generated/api/role.service';
import { AuthenticatedLayoutComponent } from './authenticated-layout.component';
import { OperationsApiAdapter } from '../features/operations/operations-api.adapter';
import { OperationsPanelPageComponent } from '../features/operations/operations-panel-page.component';
import type { SessionStateResponse } from '../api/generated/model/session-state-response';
import type { CurrentDeviceLogoutResponse } from '../api/generated/model/current-device-logout-response';

declare const require: any;
declare const process: any;
const fs = require('node:fs');
const path = require('node:path');

@Component({
  standalone: true,
  template: '',
})
class EmptyRouteComponent {}

describe('SessionStore & AuthenticatedLayoutComponent (D-01, D-08, D-09, D-16, IDEN-03, IDEN-04)', () => {
  let store: SessionStore;
  let mockSessionService: Partial<SessionService>;
  let fixture: ComponentFixture<AuthenticatedLayoutComponent>;
  let component: AuthenticatedLayoutComponent;
  let mockOperationsApiAdapter: { fetchOperationalCapabilities: ReturnType<typeof vi.fn> };

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
    mockOperationsApiAdapter = {
      fetchOperationalCapabilities: vi.fn().mockReturnValue(of([])),
    };

    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayoutComponent],
      providers: [
        SessionStore,
        { provide: SessionService, useValue: mockSessionService },
        { provide: OperationsApiAdapter, useValue: mockOperationsApiAdapter },
        provideRouter([
          { path: '', component: EmptyRouteComponent },
          { path: 'conta/perfil/completar', component: EmptyRouteComponent },
          { path: 'conta/perfil', component: EmptyRouteComponent },
          { path: 'conta/matricula', component: EmptyRouteComponent },
          { path: 'operacao/painel', component: EmptyRouteComponent },
          { path: 'operacao/papeis', component: EmptyRouteComponent },
          { path: 'operacao/matriculas/corrigir', component: EmptyRouteComponent },
        ]),
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
      endSessionUrl:
        'http://localhost:8080/realms/betufu/protocol/openid-connect/logout?client_id=betufu-web',
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

  it('Layout UI: authenticated renderiza header sem nome do usuario e menu com Sair', async () => {
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
    expect(compiled.querySelectorAll('a.skip-link')).toHaveLength(1);
    expect(compiled.querySelectorAll('header')).toHaveLength(1);
    expect(compiled.querySelectorAll('main#main-content')).toHaveLength(1);
    expect(compiled.querySelector('.layout-header')).not.toBeNull();
    expect(compiled.querySelector('.brand-name')?.textContent).toContain('BETUFU');
    expect(compiled.querySelector('#user-display-name')).toBeNull();
    expect(compiled.textContent).not.toContain('GiovaniLopes');
    const trigger = compiled.querySelector('.account-menu-trigger') as HTMLButtonElement;
    expect(trigger).not.toBeNull();
    trigger.click();
    fixture.detectChanges();
    expect(compiled.querySelector('.btn-logout')).not.toBeNull();
    expect(compiled.querySelector('main#main-content')).not.toBeNull();
  });

  it('Layout responsivo: contem o menu sem reintroduzir nome ou alargar o header em 320px', () => {
    const basePath = typeof process !== 'undefined' && process.cwd ? process.cwd() : '';
    const stylePath = path.resolve(basePath, 'src/app/layout/authenticated-layout.component.scss');
    const styleContent: string = fs.readFileSync(stylePath, 'utf8');

    expect(styleContent).toMatch(/@media\s*\(max-width:/);
    expect(styleContent).toMatch(/\.header-inner\s*\{[^}]*min-width:\s*0/s);
    expect(styleContent).toMatch(/\.user-navigation\s*\{[^}]*min-width:\s*0/s);
    expect(styleContent).toMatch(
      /\.account-menu\s*\{[^}]*max-width:\s*calc\(100vw\s*-\s*tokens\.\$space-8\)/s,
    );
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
    expect(compiled.textContent).toContain('Sessão encerrada');
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

  it('S1 renderiza somente Sair mesmo quando o BFF devolveria todas as capacidades', async () => {
    mockOperationsApiAdapter.fetchOperationalCapabilities.mockReturnValue(
      of(['VIEW_OPERATIONAL_PANEL', 'MANAGE_ROLES', 'CORRECT_ENROLLMENT']),
    );
    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: true }));
    await TestBed.inject(Router).navigateByUrl('/conta/perfil/completar');

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const navigation = fixture.nativeElement.querySelector('.user-navigation') as HTMLElement;
    expect(navigation.textContent?.trim()).toBe('Sair');
    expect(navigation.querySelector('.account-menu-trigger')).toBeNull();
    expect(navigation.querySelectorAll('a')).toHaveLength(0);
    expect(mockOperationsApiAdapter.fetchOperationalCapabilities).not.toHaveBeenCalled();
  });

  it('fora de S1 mostra itens base e somente destinos autorizados em ordem deterministica', async () => {
    mockOperationsApiAdapter.fetchOperationalCapabilities.mockReturnValue(
      of(['CORRECT_ENROLLMENT', 'VIEW_OPERATIONAL_PANEL']),
    );
    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: true }));
    await TestBed.inject(Router).navigateByUrl('/conta/perfil');

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(mockOperationsApiAdapter.fetchOperationalCapabilities).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.operationItems().map((item) => item.path)).toEqual([
      '/operacao/painel',
      '/operacao/matriculas/corrigir',
    ]);
    const trigger = fixture.nativeElement.querySelector(
      '.account-menu-trigger',
    ) as HTMLButtonElement;
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(trigger.getAttribute('aria-controls')).toBe('account-navigation-menu');
    trigger.click();
    fixture.detectChanges();

    const menu = fixture.nativeElement.querySelector('#account-navigation-menu') as HTMLElement;
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect([...menu.querySelectorAll('a')].map((item) => item.textContent?.trim())).toEqual([
      'Meu perfil',
      'Matrícula UFU',
      'Painel operacional',
      'Corrigir matrícula',
    ]);
    expect(menu.textContent).not.toContain('Papéis operacionais');
    expect(menu.querySelector('[disabled]')).toBeNull();
    expect(menu.textContent).not.toMatch(
      /ADMINISTRADOR|VIEW_OPERATIONAL|CORRECT_ENROLLMENT|MFA|identity-/u,
    );
  });

  it('limpa capacidades quando a sessao expira e consulta novamente apos reautenticacao', async () => {
    mockOperationsApiAdapter.fetchOperationalCapabilities
      .mockReturnValueOnce(of(['MANAGE_ROLES']))
      .mockReturnValueOnce(of([]));
    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: true }));

    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.operationItems().map((item) => item.path)).toEqual([
      '/operacao/papeis',
    ]);

    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: false }));
    await store.loadSession();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.operationItems()).toEqual([]);

    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: true }));
    await store.loadSession();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(mockOperationsApiAdapter.fetchOperationalCapabilities).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.operationItems()).toEqual([]);
  });

  it('Enter e Espaco alternam o disclosure; Tab preserva a ordem nativa; Escape devolve foco', async () => {
    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: true }));
    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector(
      '.account-menu-trigger',
    ) as HTMLButtonElement;
    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');

    const menu = fixture.nativeElement.querySelector('#account-navigation-menu') as HTMLElement;
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    await Promise.resolve();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(trigger);

    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');

    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    await Promise.resolve();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(trigger);

    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');

    const reopenedMenu = fixture.nativeElement.querySelector(
      '#account-navigation-menu',
    ) as HTMLElement;
    const tabbableItems = [
      ...reopenedMenu.querySelectorAll<HTMLAnchorElement | HTMLButtonElement>('a, button'),
    ];
    expect(tabbableItems.map((item) => item.textContent?.trim())).toEqual([
      'Meu perfil',
      'Matrícula UFU',
      'Sair',
    ]);
    expect(
      tabbableItems.every((item) => item.tabIndex === 0 && !item.hasAttribute('disabled')),
    ).toBe(true);
    const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    tabbableItems[0].dispatchEvent(tabEvent);
    expect(tabEvent.defaultPrevented).toBe(false);
  });

  it('usa copia generica para sessao encerrada sem inferir timeout ou causa de seguranca', async () => {
    (mockSessionService.getSession as any).mockReturnValue(of({ authenticated: false }));
    fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Sua sessão foi encerrada. Entre novamente para continuar.');
    expect(text).not.toMatch(/tempo limite|inatividade|papel|senha|segurança/iu);
  });

  it('contrato responsivo garante alvos de 44px, rolagem vertical e nenhum overflow horizontal', () => {
    const stylePath = path.resolve(
      process.cwd(),
      'src/app/layout/authenticated-layout.component.scss',
    );
    const styleContent: string = fs.readFileSync(stylePath, 'utf8');

    expect(styleContent).toMatch(
      /\.account-menu-trigger[\s\S]*min-height:\s*tokens\.\$min-touch-target/u,
    );
    expect(styleContent).toMatch(
      /\.account-menu-item[\s\S]*min-height:\s*tokens\.\$min-touch-target/u,
    );
    expect(styleContent).toMatch(
      /\.account-menu[\s\S]*max-height:[^;]+;[\s\S]*overflow-y:\s*auto/u,
    );
    expect(styleContent).toMatch(/overflow-x:\s*hidden/u);
    expect(styleContent).toMatch(/overflow-wrap:\s*anywhere/u);
  });
});

describe('S7 authority path integrado', () => {
  it('usa capability do cliente gerado para navegar ao painel real e obedece o 403 do BFF', async () => {
    const sessionService = {
      getSession: vi.fn().mockReturnValue(of({ authenticated: true })),
      logoutCurrentSession: vi.fn(),
    };
    const capabilitiesService = {
      getOperationalCapabilities: vi.fn().mockReturnValue(
        of({
          capabilities: ['VIEW_OPERATIONAL_PANEL'],
        }),
      ),
    };
    const panelService = {
      getOperationalPanel: vi.fn().mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 403,
              error: { correlationId: 'corr-stale-capability' },
            }),
        ),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [AuthenticatedLayoutComponent],
      providers: [
        SessionStore,
        OperationsApiAdapter,
        { provide: SessionService, useValue: sessionService },
        { provide: OperationalCapabilitiesService, useValue: capabilitiesService },
        { provide: OperationalPanelService, useValue: panelService },
        { provide: RoleService, useValue: {} },
        provideRouter([
          { path: 'conta/perfil', component: EmptyRouteComponent },
          { path: 'operacao/painel', component: OperationsPanelPageComponent },
        ]),
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    await router.navigateByUrl('/conta/perfil');
    const fixture = TestBed.createComponent(AuthenticatedLayoutComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector(
      '.account-menu-trigger',
    ) as HTMLButtonElement;
    trigger.click();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const panelLink = [...root.querySelectorAll<HTMLAnchorElement>('a')].find(
      (link) => link.textContent?.trim() === 'Painel operacional',
    );
    expect(panelLink).toBeDefined();
    panelLink?.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/operacao/painel');
    expect(capabilitiesService.getOperationalCapabilities).toHaveBeenCalledTimes(1);
    expect(panelService.getOperationalPanel).toHaveBeenCalledTimes(1);
    expect(root.querySelectorAll('.panel-card')).toHaveLength(0);
    expect(root.querySelector('.operations-grid')).toBeNull();
    expect(root.querySelector('.error-text')?.textContent?.trim()).toBe(
      'Você não tem acesso a esta área.',
    );
    expect(root.textContent).not.toMatch(
      /ADMINISTRADOR|VIEW_OPERATIONAL_PANEL|MANAGE_ROLES|CORRECT_ENROLLMENT/u,
    );
  });
});
