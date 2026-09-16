import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CreditLedgerService } from '../api/generated/api/credit-ledger.service';
import { SessionService } from '../api/generated/api/session.service';
import { SessionStore } from '../core/session/session.store';
import { ArenaSessionSummaryStore } from './arena-session-summary.store';
import { ArenaShellComponent } from './arena-shell.component';

@Component({ standalone: true, template: '' })
class EmptyRouteComponent {}

describe('ArenaShellComponent', () => {
  let fixture: ComponentFixture<ArenaShellComponent>;
  let sessionService: {
    getSession: ReturnType<typeof vi.fn>;
    logoutCurrentSession: ReturnType<typeof vi.fn>;
  };
  let creditLedgerService: { getMyCreditBalance: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    sessionService = {
      getSession: vi.fn().mockReturnValue(of({ authenticated: false })),
      logoutCurrentSession: vi.fn().mockReturnValue(of({ endSessionUrl: null })),
    };
    creditLedgerService = {
      getMyCreditBalance: vi
        .fn()
        .mockReturnValue(
          of({ balance: 1000, scale: 2, minimumStake: 10, rulesVersion: 'credits-v1' }),
        ),
    };

    await TestBed.configureTestingModule({
      imports: [ArenaShellComponent],
      providers: [
        ArenaSessionSummaryStore,
        SessionStore,
        { provide: SessionService, useValue: sessionService },
        { provide: CreditLedgerService, useValue: creditLedgerService },
        provideRouter([{ path: '', component: EmptyRouteComponent }]),
      ],
    }).compileComponents();
  });

  async function render(): Promise<void> {
    fixture = TestBed.createComponent(ArenaShellComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  it('mantém estado neutro durante a checagem sem piscar Entrar', () => {
    sessionService.getSession.mockReturnValue(new Subject());
    fixture = TestBed.createComponent(ArenaShellComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.arena-session-status')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Entrar');
    expect(fixture.nativeElement.textContent).not.toContain('Minha conta');
  });

  it('mostra login contextual somente para visitante', async () => {
    await render();

    const loginLinks = fixture.nativeElement.querySelectorAll('a.arena-nav__login');
    expect(loginLinks.length).toBe(2);
    loginLinks.forEach((link: HTMLAnchorElement) => {
      expect(link.getAttribute('href')).toBe('/login/start?returnTo=%2F');
    });
    expect(fixture.nativeElement.textContent).not.toContain('Minha conta');
    expect(creditLedgerService.getMyCreditBalance).not.toHaveBeenCalled();
  });

  it('mostra nome, saldo, conta e logout para sessão autenticada em desktop e mobile', async () => {
    sessionService.getSession.mockReturnValue(
      of({ authenticated: true, displayName: '<Aluno UFU>' }),
    );
    await render();
    await vi.waitFor(() =>
      expect(fixture.componentInstance.sessionSummary.balanceStatus()).toBe('available'),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.arena-session-actions').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('<Aluno UFU>');
    expect(fixture.nativeElement.innerHTML).not.toContain('<aluno ufu>');
    expect(fixture.nativeElement.textContent).toContain('Saldo: 1.000,00 créditos');
    expect(fixture.nativeElement.querySelectorAll('a.arena-nav__account').length).toBe(2);
    expect(fixture.nativeElement.querySelectorAll('button.arena-nav__logout').length).toBe(2);
  });

  it('exibe fallback seguro quando o saldo está indisponível', async () => {
    sessionService.getSession.mockReturnValue(of({ authenticated: true, displayName: 'Aluno' }));
    creditLedgerService.getMyCreditBalance.mockReturnValue(
      throwError(() => new Error('falha privada do saldo')),
    );
    await render();
    await vi.waitFor(() =>
      expect(fixture.componentInstance.sessionSummary.balanceStatus()).toBe('unavailable'),
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Saldo indisponível');
    expect(fixture.nativeElement.textContent).not.toContain('falha privada do saldo');
  });

  it('logout limpa nome e saldo e retorna ao estado visitante', async () => {
    sessionService.getSession.mockReturnValue(
      of({ authenticated: true, displayName: 'Aluno UFU' }),
    );
    await render();

    const logout = fixture.nativeElement.querySelector(
      'button.arena-nav__logout',
    ) as HTMLButtonElement;
    logout.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(sessionService.logoutCurrentSession).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).not.toContain('Aluno UFU');
    expect(fixture.nativeElement.textContent).not.toContain('1.000,00 créditos');
    expect(fixture.nativeElement.querySelector('a.arena-nav__login')).not.toBeNull();
  });

  it('preserva semântica de teclado, foco e alvos responsivos', async () => {
    sessionService.getSession.mockReturnValue(of({ authenticated: true, displayName: 'Aluno' }));
    await render();

    const main = fixture.nativeElement.querySelector('main#arena-main') as HTMLElement;
    const account = fixture.nativeElement.querySelector(
      '.arena-session-actions--mobile .arena-nav__account',
    ) as HTMLAnchorElement;
    const logout = fixture.nativeElement.querySelector(
      '.arena-session-actions--mobile .arena-nav__logout',
    ) as HTMLButtonElement;

    expect(fixture.nativeElement.querySelector('a.skip-link')?.getAttribute('href')).toBe(
      '#arena-main',
    );
    expect(main.getAttribute('tabindex')).toBe('-1');
    expect(account.tagName.toLowerCase()).toBe('a');
    expect(account.getAttribute('href')).toContain('/conta/perfil');
    expect(logout.type).toBe('button');
  });

  it('atualiza o retorno contextual quando a rota muda', async () => {
    const router = TestBed.inject(Router);
    await render();
    await router.navigateByUrl('/?origem=arena');
    fixture.detectChanges();

    const login = fixture.nativeElement.querySelector('a.arena-nav__login') as HTMLAnchorElement;
    expect(login.getAttribute('href')).toBe(
      '/login/start?returnTo=' + encodeURIComponent('/?origem=arena'),
    );
  });
});
