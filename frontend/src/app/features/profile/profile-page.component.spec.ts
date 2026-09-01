import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfileService } from '../../api/generated/api/profile.service';
import { SessionService } from '../../api/generated/api/session.service';
import { CurrentProfileResponse } from '../../api/generated/model/current-profile-response';
import { ProfilePageComponent } from './profile-page.component';
import { ProfileStore } from './profile.store';
import { ProfileApiAdapter } from './profile-api.adapter';
import { SessionStore } from '../../core/session/session.store';
import { BUILD_SPECIFIC_ROUTES as PROD_ROUTES } from '../../app.routes.production';
import { BUILD_SPECIFIC_ROUTES as DEV_ROUTES } from '../../app.routes.development';

const MOCK_CURRENT_PROFILE: CurrentProfileResponse = {
  identityId: 'uuid-public-1234-abcd',
  nickname: 'jogador_ufu',
  nicknameUpdatedAt: '2026-08-21T10:00:00.000Z',
  matriculaStatus: 'NAO_REGISTRADA',
  favoriteOrganizationId: null,
};

const MOCK_UPDATED_PROFILE: CurrentProfileResponse = {
  identityId: 'uuid-public-1234-abcd',
  nickname: 'novo_apelido',
  nicknameUpdatedAt: '2026-08-21T12:00:00.000Z',
  matriculaStatus: 'NAO_REGISTRADA',
  favoriteOrganizationId: null,
};

@Component({
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class TestHostComponent {}

describe('ProfilePageComponent (Superfície S2)', () => {
  let fixture: ComponentFixture<ProfilePageComponent>;
  let component: ProfilePageComponent;
  let profileStore: ProfileStore;

  let mockProfileService: {
    getMyProfile: ReturnType<typeof vi.fn>;
    putMyProfile: ReturnType<typeof vi.fn>;
    putMyFavoriteOrganization: ReturnType<typeof vi.fn>;
  };

  let mockSessionService: {
    getSession: ReturnType<typeof vi.fn>;
    logoutCurrentSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockProfileService = {
      getMyProfile: vi.fn().mockReturnValue(of(MOCK_CURRENT_PROFILE)),
      putMyProfile: vi.fn().mockReturnValue(of(MOCK_UPDATED_PROFILE)),
      putMyFavoriteOrganization: vi.fn().mockReturnValue(of(MOCK_UPDATED_PROFILE)),
    };

    mockSessionService = {
      getSession: vi.fn().mockReturnValue(
        of({
          authenticated: true,
          displayName: 'Jogador UFU',
          idleTimeout: 'PT15M',
          lastValidActivityAt: '2026-08-21T12:00:00Z',
        }),
      ),
      logoutCurrentSession: vi.fn().mockReturnValue(of({ endSessionUrl: '/logged-out' })),
    };

    await TestBed.configureTestingModule({
      imports: [ProfilePageComponent, TestHostComponent],
      providers: [
        ProfileApiAdapter,
        ProfileStore,
        SessionStore,
        { provide: ProfileService, useValue: mockProfileService },
        { provide: SessionService, useValue: mockSessionService },
        provideRouter(PROD_ROUTES),
      ],
    }).compileComponents();

    profileStore = TestBed.inject(ProfileStore);
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(ProfilePageComponent);
    component = fixture.componentInstance;
  }

  it('Garantia [loading] (S2): exibe estado de loading sem cartões enganosos quando não há dados anteriores', async () => {
    // Configura resposta pendente via NEVER (request em andamento)
    mockProfileService.getMyProfile.mockReturnValue(NEVER);

    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Loading feedback visível
    const loadingState = compiled.querySelector('.status-feedback.loading-state');
    expect(loadingState).not.toBeNull();
    expect(loadingState?.getAttribute('role')).toBe('status');
    expect(loadingState?.textContent).toContain('Carregando perfil…');
    expect(loadingState?.querySelector('.spinner-large')).not.toBeNull();

    // Nenhum cartão enganoso deve estar no DOM
    expect(compiled.querySelector('.profile-card')).toBeNull();
  });

  it('Garantia [empty] (S2): exibe estado empty sem cartões enganosos quando não há perfil criado', async () => {
    mockProfileService.getMyProfile.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    createComponent();
    fixture.detectChanges();
    await vi.waitFor(() => {
      expect(profileStore.status()).toBe('empty');
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Empty feedback visível
    const emptyState = compiled.querySelector('.status-feedback.empty-state');
    expect(emptyState).not.toBeNull();
    expect(emptyState?.textContent).toContain('Nenhum perfil encontrado');
    expect(emptyState?.textContent).toContain('Você precisa escolher um apelido para continuar.');

    const link = emptyState?.querySelector('a.btn-primary');
    expect(link).not.toBeNull();
    expect(link?.textContent?.trim()).toBe('Escolher apelido');

    // Nenhum cartão enganoso no DOM
    expect(compiled.querySelector('.profile-card')).toBeNull();
  });

  it('Garantia [error inicial] (S2): exibe erro bloqueante com retry e correlationId quando falha sem dados prévios', async () => {
    mockProfileService.getMyProfile.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-init-err-111' },
          }),
      ),
    );

    createComponent();
    fixture.detectChanges();
    await vi.waitFor(() => {
      expect(profileStore.status()).toBe('error');
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Error feedback visível
    const errorState = compiled.querySelector('.status-feedback.error-state');
    expect(errorState).not.toBeNull();
    expect(errorState?.getAttribute('role')).toBe('alert');
    expect(errorState?.textContent).toContain('Não foi possível carregar seu perfil');
    expect(errorState?.textContent).toContain('corr-init-err-111');

    const retryBtn = errorState?.querySelector('button.btn-primary') as HTMLButtonElement;
    expect(retryBtn).not.toBeNull();
    expect(retryBtn.textContent?.trim()).toBe('Tentar novamente');

    // Nenhum cartão enganoso no DOM
    expect(compiled.querySelector('.profile-card')).toBeNull();

    // Teste de retry
    mockProfileService.getMyProfile.mockReturnValue(of(MOCK_CURRENT_PROFILE));
    retryBtn.click();
    fixture.detectChanges();
    await vi.waitFor(() => {
      expect(profileStore.status()).toBe('populated');
    });
    fixture.detectChanges();

    expect(compiled.querySelector('.profile-card')).not.toBeNull();
    expect(compiled.querySelector('.status-feedback.error-state')).toBeNull();
  });

  it('Garantia [populated] (S2): renderiza perfil com apelido, identificador público copiável e matrícula não registrada', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Título da página
    expect(compiled.querySelector('.profile-title')?.textContent?.trim()).toBe('Seu perfil');

    // Cartão Apelido
    const nicknameInput = compiled.querySelector('#nickname-input') as HTMLInputElement;
    expect(nicknameInput).not.toBeNull();
    expect(nicknameInput.value).toBe('jogador_ufu');
    expect(nicknameInput.getAttribute('maxlength')).toBe('20');
    expect(nicknameInput.getAttribute('autocomplete')).toBe('off');
    expect(nicknameInput.getAttribute('spellcheck')).toBe('false');

    const counter = compiled.querySelector('.char-counter');
    expect(counter?.textContent?.trim()).toBe('11/20');
    expect(counter?.getAttribute('aria-hidden')).toBe('true');

    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submitBtn.disabled).toBe(true);
    expect(submitBtn.textContent?.trim()).toBe('Salvar alterações');

    // Cartão Identificador público (D-43)
    const identityValue = compiled.querySelector('#identity-value') as HTMLElement;
    expect(identityValue).not.toBeNull();
    expect(identityValue.textContent?.trim()).toBe('uuid-public-1234-abcd');
    expect(identityValue.tagName.toLowerCase()).toBe('code');
    expect(identityValue.getAttribute('tabindex')).toBe('0');

    const identityExplanation = compiled.querySelector('.identity-explanation');
    expect(identityExplanation?.textContent?.trim()).toContain(
      'Apelidos podem se repetir na BETUFU. Este identificador é único e distingue seu perfil de qualquer outro com o mesmo apelido.',
    );

    const copyBtn = compiled.querySelector('.btn-secondary') as HTMLButtonElement;
    expect(copyBtn).not.toBeNull();
    expect(copyBtn.disabled).toBe(false);
    expect(copyBtn.textContent).toContain('Copiar identificador');
    const copyIcon = copyBtn.querySelector('svg.btn-icon');
    expect(copyIcon).not.toBeNull();
    expect(copyIcon?.getAttribute('aria-hidden')).toBe('true');
    expect(copyIcon?.getAttribute('width')).toBe('24');
    expect(copyIcon?.getAttribute('height')).toBe('24');

    // Cartão Matrícula UFU (não registrada)
    const matriculaEmptyText = compiled.querySelector('.matricula-empty-text');
    expect(matriculaEmptyText?.textContent?.trim()).toBe('Nenhuma matrícula registrada.');

    const matriculaLink = compiled.querySelector('a.link-primary') as HTMLAnchorElement;
    expect(matriculaLink).not.toBeNull();
    expect(matriculaLink.textContent?.trim()).toBe('Registrar matrícula');
    expect(
      matriculaLink.getAttribute('href') || matriculaLink.getAttribute('ng-reflect-router-link'),
    ).toContain('matricula');

    const athleteProfileLink = compiled.querySelector(
      'a.athlete-profile-link',
    ) as HTMLAnchorElement;
    expect(athleteProfileLink).not.toBeNull();
    expect(athleteProfileLink.textContent?.trim()).toBe('Gerenciar perfil esportivo');
    expect(
      athleteProfileLink.getAttribute('href') ||
        athleteProfileLink.getAttribute('ng-reflect-router-link'),
    ).toContain('conta/perfil/esportivo');
  });

  it('Garantia [populated] (S2): exibe chip de matrícula registrada quando cadastrada', async () => {
    mockProfileService.getMyProfile.mockReturnValue(
      of({
        identityId: 'uuid-public-1234-abcd',
        nickname: 'jogador_ufu',
        nicknameUpdatedAt: null,
        matriculaStatus: 'REGISTRADA',
        favoriteOrganizationId: null,
      }),
    );

    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const statusChip = compiled.querySelector('.status-chip.chip-available');
    expect(statusChip).not.toBeNull();
    expect(statusChip?.textContent?.trim()).toBe('Matrícula registrada');

    // Link para registrar não deve existir
    expect(compiled.querySelector('a.link-primary')).toBeNull();
  });

  it('Garantia [clipboard] (S2): copia identificador público para área de transferência e anuncia acessivelmente', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextSpy,
      },
    });

    await component.copyIdentityId();
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith('uuid-public-1234-abcd');
    expect(component.copyStatus()).toBe('copied');

    const liveRegion = fixture.nativeElement.querySelector('#profile-live-region');
    expect(liveRegion?.textContent).toContain('Identificador copiado.');
  });

  it('Garantia [clipboard] (S2): falha de cópia do identificador exibe instrução alternativa acessível e seleciona todo o identificador via Range', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const identityEl = fixture.nativeElement.querySelector('#identity-value') as HTMLElement;
    const focusSpy = vi.spyOn(identityEl, 'focus');

    Object.assign(navigator, {
      clipboard: {
        writeText: vi.fn().mockRejectedValue(new Error('Permission denied')),
      },
    });

    await component.copyIdentityId();
    fixture.detectChanges();

    expect(component.copyStatus()).toBe('error');

    const liveRegion = fixture.nativeElement.querySelector('#profile-live-region');
    expect(liveRegion?.textContent).toContain(
      'Não foi possível copiar. Selecione o identificador e copie novamente.',
    );
    expect(focusSpy).toHaveBeenCalled();

    // Prova a seleção de todo o UUID via Range e Selection
    const selection = window.getSelection();
    expect(selection).not.toBeNull();
    expect(selection?.rangeCount).toBeGreaterThan(0);
    const range = selection?.getRangeAt(0);
    expect(range).toBeDefined();
    expect(range?.toString().trim()).toBe('uuid-public-1234-abcd');
  });

  it('Garantia [loading & update] (S2): habilita botão com alteração válida, exibe Salvando… e atualiza apelido', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;

    expect(submitBtn.disabled).toBe(true);

    input.value = 'novo_apelido';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(submitBtn.disabled).toBe(false);

    const submitPromise = component.onSubmit();
    fixture.detectChanges();

    expect(submitBtn.disabled).toBe(true);
    expect(submitBtn.textContent?.trim()).toBe('Salvando…');

    await submitPromise;
    fixture.detectChanges();

    expect(component.successMessage()).toBe('Perfil atualizado.');
    const liveRegion = compiled.querySelector('#profile-live-region');
    expect(liveRegion?.textContent).toContain('Perfil atualizado.');
    expect(mockProfileService.putMyProfile).toHaveBeenCalledWith({
      profileUpdateRequest: { nickname: 'novo_apelido' },
    });
    expect(submitBtn.disabled).toBe(true);
  });

  it('Garantia [error] (S2): validação de campo vazio e padrão com foco no campo inválido antes de bloquear', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    const focusSpy = vi.spyOn(input, 'focus');

    // Valor inválido: menos de 3 caracteres
    input.value = 'ab';
    input.dispatchEvent(new Event('input'));
    component.form.controls.nickname.markAsDirty();
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    let errorEl = compiled.querySelector('#nickname-error');
    expect(errorEl?.textContent?.trim()).toBe(
      'Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).',
    );
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(focusSpy).toHaveBeenCalled();

    // Valor inválido: caracteres especiais
    input.value = 'nome com espaco';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    errorEl = compiled.querySelector('#nickname-error');
    expect(errorEl?.textContent?.trim()).toBe(
      'Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).',
    );
  });

  it('Garantia [error & partial] (S2): erro do servidor exibe alerta no topo com correlationId, PRESERVA valor digitado (D-16) e usa displayProfile', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    mockProfileService.putMyProfile.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-prof-err-555' },
          }),
      ),
    );

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;

    input.value = 'apelido_alterado';
    input.dispatchEvent(new Event('input'));
    component.form.controls.nickname.markAsDirty();
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    // Alerta no topo
    const alertBox = compiled.querySelector('.alert-box');
    expect(alertBox).not.toBeNull();
    expect(alertBox?.getAttribute('role')).toBe('alert');
    expect(alertBox?.textContent).toContain('Não foi possível salvar seu perfil.');
    expect(alertBox?.textContent).toContain('corr-prof-err-555');

    // D-16 / UI Consideration partial: o valor digitado permanece no formulário
    expect(input.value).toBe('apelido_alterado');
    expect(component.form.controls.nickname.value).toBe('apelido_alterado');

    // Cartões continuam visíveis graças ao displayProfile
    const identityValue = compiled.querySelector('#identity-value') as HTMLElement;
    expect(identityValue?.textContent?.trim()).toBe('uuid-public-1234-abcd');
  });

  it('Garantia [error] (S2): detail do servidor não é exposto no DOM', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    mockProfileService.putMyProfile.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              detail: 'CANARIO_PRIVADO_DETAIL_S2',
              correlationId: 'corr-s2-400-999',
            },
          }),
      ),
    );

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    input.value = 'apelido_tentativa';
    input.dispatchEvent(new Event('input'));
    component.form.controls.nickname.markAsDirty();
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    const alertBox = compiled.querySelector('.alert-box');
    expect(alertBox?.textContent).toContain('Não foi possível salvar seu perfil.');
    expect(alertBox?.textContent).toContain('corr-s2-400-999');
    expect(alertBox?.textContent).not.toContain('CANARIO_PRIVADO_DETAIL_S2');
  });

  it('Garantia de segurança (S2): ausência de dados proibidos no DOM (D-45, D-52, PRIV-01, PROF-02)', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const markup = compiled.outerHTML.toLowerCase();

    // D-45: Equipe favorita ausente (sem campo, rótulo, placeholder ou menção)
    expect(markup).not.toContain('equipe favorita');
    expect(markup).not.toContain('time');
    expect(markup).not.toContain('em breve');

    // D-52 / PROF-02: e-mail, HMAC, papéis e MFA ausentes
    expect(markup).not.toContain('e-mail');
    expect(markup).not.toContain('email');
    expect(markup).not.toContain('hmac');
    expect(markup).not.toContain('papel');
    expect(markup).not.toContain('papeis');
    expect(markup).not.toContain('mfa');

    // Não duplica casca de layout
    expect(compiled.querySelector('header')).toBeNull();
    expect(compiled.querySelector('main')).toBeNull();
  });

  it('Garantia [clipboard]: copia correlationId de erro com feedback acessível', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextSpy,
      },
    });

    await component.copyCorrelationId('corr-prof-err-555');
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith('corr-prof-err-555');
    expect(component.copyStatus()).toBe('copied');

    const liveRegion = fixture.nativeElement.querySelector('#profile-live-region');
    expect(liveRegion?.textContent).toContain('Código de referência copiado.');
  });

  it('Garantia de rotas: prova que PROD_ROUTES e DEV_ROUTES definem conta/perfil por carregamento sob demanda (lazy) sem duplicação', () => {
    // Prova as rotas de produção
    const prodRoot = PROD_ROUTES.find((r) => r.path === '');
    expect(prodRoot).toBeDefined();
    expect(prodRoot?.children).toBeDefined();

    const prodProfileRoute = prodRoot?.children?.find((r) => r.path === 'conta/perfil');
    expect(prodProfileRoute).toBeDefined();
    expect(prodProfileRoute?.loadComponent).toBeTypeOf('function');
    expect((prodProfileRoute as unknown as { component?: unknown }).component).toBeUndefined();

    const prodGateRoute = prodRoot?.children?.find((r) => r.path === 'conta/perfil/completar');
    expect(prodGateRoute).toBeDefined();
    expect(prodGateRoute?.loadComponent).toBeTypeOf('function');

    const prodAthleteProfileRoute = prodRoot?.children?.find(
      (r) => r.path === 'conta/perfil/esportivo',
    );
    expect(prodAthleteProfileRoute).toBeDefined();
    expect(prodAthleteProfileRoute?.loadComponent).toBeTypeOf('function');

    // Prova que rotas de produção não contêm rotas técnicas de desenvolvimento
    expect(prodRoot?.children?.find((r) => r.path === 'tracer')).toBeUndefined();
    expect(prodRoot?.children?.find((r) => r.path === 'diagnostic')).toBeUndefined();

    // Prova as rotas de desenvolvimento
    const devRoot = DEV_ROUTES.find((r) => r.path === 'children' || r.path === '');
    expect(devRoot).toBeDefined();
    expect(devRoot?.children).toBeDefined();

    const devProfileRoute = devRoot?.children?.find((r) => r.path === 'conta/perfil');
    expect(devProfileRoute).toBeDefined();
    expect(devProfileRoute?.loadComponent).toBeTypeOf('function');

    const devAthleteProfileRoute = devRoot?.children?.find(
      (r) => r.path === 'conta/perfil/esportivo',
    );
    expect(devAthleteProfileRoute).toBeDefined();
    expect(devAthleteProfileRoute?.loadComponent).toBeTypeOf('function');

    // Prova que rotas de desenvolvimento contêm tracer e diagnostic
    expect(devRoot?.children?.find((r) => r.path === 'tracer')).toBeDefined();
    expect(devRoot?.children?.find((r) => r.path === 'diagnostic')).toBeDefined();
  });

  it('Garantia de integração: fluxo real salvar S1 → redirecionamento para /conta/perfil → renderização de S2 via lazy routing real', async () => {
    const hostFixture = TestBed.createComponent(TestHostComponent);
    const hostRouter = TestBed.inject(Router);

    // 1. Inicia na rota S1 (/conta/perfil/completar) com lazy load real
    mockProfileService.getMyProfile.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );
    await hostRouter.navigate(['/conta/perfil/completar']);
    hostFixture.detectChanges();
    await vi.waitFor(() => {
      expect(profileStore.status()).toBe('empty');
    });
    hostFixture.detectChanges();

    // 2. Confirma que S1 está renderizada
    expect(hostFixture.nativeElement.querySelector('.gate-title')?.textContent?.trim()).toBe(
      'Escolha seu apelido',
    );

    // 3. Preenche apelido em S1 e configura resposta de sucesso
    const gateInput = hostFixture.nativeElement.querySelector(
      '#nickname-input',
    ) as HTMLInputElement;
    gateInput.value = 'jogador_integrado';
    gateInput.dispatchEvent(new Event('input'));
    hostFixture.detectChanges();

    const savedProfile: CurrentProfileResponse = {
      identityId: 'uuid-integ-s1-s2-777',
      nickname: 'jogador_integrado',
      nicknameUpdatedAt: '2026-08-21T14:00:00.000Z',
      matriculaStatus: 'NAO_REGISTRADA',
      favoriteOrganizationId: null,
    };
    mockProfileService.putMyProfile.mockReturnValue(of(savedProfile));
    mockProfileService.getMyProfile.mockReturnValue(of(savedProfile));

    const gateSubmit = hostFixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    gateSubmit.click();
    hostFixture.detectChanges();
    await vi.waitFor(() => {
      expect(hostRouter.url).toBe('/conta/perfil');
    });
    hostFixture.detectChanges();

    // 4. Confirma que navegou para /conta/perfil
    expect(hostRouter.url).toBe('/conta/perfil');

    // 5. Confirma que S2 foi carregada sob demanda e está renderizada exibindo o perfil salvo
    await vi.waitFor(() => {
      const profileTitle = hostFixture.nativeElement.querySelector('.profile-title');
      expect(profileTitle?.textContent?.trim()).toBe('Seu perfil');
    });
    hostFixture.detectChanges();

    const identityValue = hostFixture.nativeElement.querySelector('#identity-value') as HTMLElement;
    expect(identityValue?.textContent?.trim()).toBe('uuid-integ-s1-s2-777');

    const profileInput = hostFixture.nativeElement.querySelector(
      '#nickname-input',
    ) as HTMLInputElement;
    expect(profileInput.value).toBe('jogador_integrado');
  });

  it('Garantia estrutural de ícones: todos os SVGs dentro de botões possuem dimensão 24px conforme UI-SPEC', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const buttonSvgs = compiled.querySelectorAll('button svg');
    expect(buttonSvgs.length).toBeGreaterThan(0);

    buttonSvgs.forEach((svg) => {
      expect(svg.getAttribute('width')).toBe('24');
      expect(svg.getAttribute('height')).toBe('24');
      expect(svg.getAttribute('viewBox')).toBe('0 0 24 24');
      expect(svg.getAttribute('aria-hidden')).toBe('true');
      expect(svg.getAttribute('focusable')).toBe('false');
    });
  });
});
