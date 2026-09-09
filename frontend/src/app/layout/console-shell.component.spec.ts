import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionService } from '../api/generated/api/session.service';
import { SessionStore } from '../core/session/session.store';
import { OperationsApiAdapter } from '../features/operations/operations-api.adapter';
import { ConsoleShellComponent } from './console-shell.component';

@Component({ standalone: true, template: '' })
class EmptyRouteComponent {}

describe('ConsoleShellComponent', () => {
  let fixture: ComponentFixture<ConsoleShellComponent>;
  let sessionService: {
    getSession: ReturnType<typeof vi.fn>;
    logoutCurrentSession: ReturnType<typeof vi.fn>;
  };
  let operationsApiAdapter: { fetchOperationalCapabilities: ReturnType<typeof vi.fn> };
  const routeData: Record<string, unknown> = {};

  beforeEach(async () => {
    sessionService = {
      getSession: vi.fn().mockReturnValue(of({ authenticated: true, displayName: 'Jogador UFU' })),
      logoutCurrentSession: vi.fn().mockReturnValue(of({ endSessionUrl: null })),
    };
    operationsApiAdapter = {
      fetchOperationalCapabilities: vi
        .fn()
        .mockReturnValue(of(['MANAGE_SPORTS_CATALOG', 'VIEW_OPERATIONAL_PANEL'])),
    };
    routeData['consoleVariant'] = 'personal';

    await TestBed.configureTestingModule({
      imports: [ConsoleShellComponent],
      providers: [
        SessionStore,
        { provide: SessionService, useValue: sessionService },
        { provide: OperationsApiAdapter, useValue: operationsApiAdapter },
        provideRouter([
          {
            path: 'conta',
            component: ConsoleShellComponent,
            data: { consoleVariant: 'personal' },
            children: [
              { path: 'perfil', component: EmptyRouteComponent },
              { path: 'matricula', component: EmptyRouteComponent },
            ],
          },
          { path: 'operacao/esportes', component: EmptyRouteComponent },
          { path: '', component: EmptyRouteComponent },
        ]),
        { provide: ActivatedRoute, useValue: { snapshot: { data: routeData } } },
      ],
    }).compileComponents();
  });

  it('monta a variante pessoal sem carregar capacidades operacionais', async () => {
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.variant).toBe('personal');
    expect(fixture.componentInstance.isPersonal).toBe(true);
    expect(operationsApiAdapter.fetchOperationalCapabilities).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('.console-header--personal')).not.toBeNull();
    expect(fixture.componentInstance.personalDestinations.map((item) => item.path)).toContain(
      '/conta/vinculo-atleta',
    );
    expect(fixture.nativeElement.textContent).toContain('Área pessoal');
    expect(fixture.nativeElement.textContent).toContain('Meu perfil');
    expect(fixture.nativeElement.textContent).toContain('Matrícula UFU');
  });

  it('mantém a variante operacional e suas capacidades', async () => {
    routeData['consoleVariant'] = undefined;
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.variant).toBe('operational');
    expect(fixture.componentInstance.isPersonal).toBe(false);
    expect(operationsApiAdapter.fetchOperationalCapabilities).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.operationDestinations().map((item) => item.path)).toContain(
      '/operacao/painel',
    );
  });

  it('mostra Mercados e odds somente com VIEW_ODDS_MARKET', async () => {
    routeData['consoleVariant'] = undefined;
    operationsApiAdapter.fetchOperationalCapabilities.mockReturnValue(of(['VIEW_ODDS_MARKET']));
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.operationDestinations().map((item) => item.path)).toEqual([
      '/operacao/odds/mercados',
    ]);
  });

  it('ignora capability desconhecida e nao concede destino operacional', async () => {
    routeData['consoleVariant'] = undefined;
    operationsApiAdapter.fetchOperationalCapabilities.mockReturnValue(of(['UNKNOWN_CAPABILITY']));
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.componentInstance.operationDestinations()).toEqual([]);
  });

  it('preserva o estado de checking sem montar conteúdo privado antes da sessão', () => {
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.session-state')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.console-header')).toBeNull();
    expect(fixture.nativeElement.querySelector('main#console-main')).toBeNull();
  });

  it('expõe o menu operacional com teclado e devolve foco ao gatilho no Escape', async () => {
    routeData['consoleVariant'] = undefined;
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const trigger = fixture.nativeElement.querySelector(
      '.console-nav__account',
    ) as HTMLButtonElement;
    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('#console-account-menu')).not.toBeNull();

    const menu = fixture.nativeElement.querySelector('#console-account-menu') as HTMLElement;
    menu.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    await Promise.resolve();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(trigger);
  });

  it('limpa a superfície privada quando a sessão expira', async () => {
    sessionService.getSession.mockReturnValue(of({ authenticated: false }));
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.session-state[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.console-header')).toBeNull();
    expect(fixture.nativeElement.querySelector('main#console-main')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('MANAGE_ROLES');
  });

  it('mantém o contrato responsivo do Console em 320px e alvos de toque', () => {
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    return fixture.whenStable().then(() => {
      fixture.detectChanges();

      const skipLink = fixture.nativeElement.querySelector('a.skip-link') as HTMLAnchorElement;
      const main = fixture.nativeElement.querySelector('main#console-main') as HTMLElement;
      const account = fixture.nativeElement.querySelector(
        '.console-nav__account',
      ) as HTMLButtonElement;

      expect(skipLink.getAttribute('href')).toBe('#console-main');
      expect(main.getAttribute('tabindex')).toBe('-1');
      expect(account.getAttribute('aria-controls')).toBe('console-account-menu');
      expect(account.getAttribute('aria-expanded')).toBe('false');
    });
  });

  it('encerra a sessão por um único comando do store', async () => {
    fixture = TestBed.createComponent(ConsoleShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    await fixture.componentInstance.logout();
    expect(sessionService.logoutCurrentSession).toHaveBeenCalledTimes(1);
  });
});
