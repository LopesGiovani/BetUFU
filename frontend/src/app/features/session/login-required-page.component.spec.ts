import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { LoginRequiredPageComponent } from './login-required-page.component';

describe('LoginRequiredPageComponent', () => {
  it('oferece login contextual seguro e retorno para a Arena', async () => {
    await TestBed.configureTestingModule({
      imports: [LoginRequiredPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: { get: () => '/conta/perfil?aba=dados#titulo' } },
          },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginRequiredPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Entre para continuar');
    expect(compiled.querySelector<HTMLAnchorElement>('.primary-action')?.getAttribute('href')).toBe(
      '/login/start?returnTo=%2Fconta%2Fperfil%3Faba%3Ddados%23titulo',
    );
    expect(
      compiled.querySelector<HTMLAnchorElement>('.secondary-action')?.getAttribute('href'),
    ).toBe('/');
    expect(compiled.textContent).not.toContain('/api/');
    expect(document.activeElement).toBe(compiled.querySelector('h1'));
  });

  it('falha para a Arena quando returnTo e externo ou tecnico', async () => {
    await TestBed.configureTestingModule({
      imports: [LoginRequiredPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => 'https://evil.invalid' } } },
        },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(LoginRequiredPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector<HTMLAnchorElement>('.primary-action')?.getAttribute('href')).toBe(
      '/login/start?returnTo=%2F',
    );
  });
});
