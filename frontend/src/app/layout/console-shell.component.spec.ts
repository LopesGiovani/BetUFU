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
      fetchOperationalCapabilities: vi.fn().mockReturnValue(of(['MANAGE_SPORTS_CATALOG'])),
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
  });
});
