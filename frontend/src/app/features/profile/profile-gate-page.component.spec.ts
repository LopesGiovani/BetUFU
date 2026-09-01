import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ProfileService } from '../../api/generated/api/profile.service';
import { CurrentProfileResponse } from '../../api/generated/model/current-profile-response';
import { ProfileGatePageComponent } from './profile-gate-page.component';
import { ProfileStore } from './profile.store';
import { ProfileApiAdapter } from './profile-api.adapter';

const MOCK_SAVED_PROFILE_RESPONSE: CurrentProfileResponse = {
  identityId: 'id-uuid-gate-1234',
  nickname: 'meu_apelido',
  nicknameUpdatedAt: '2026-08-21T12:00:00.000Z',
  matriculaStatus: 'NAO_REGISTRADA',
  favoriteOrganizationId: null,
};

describe('ProfileGatePageComponent (Superfície S1)', () => {
  let fixture: ComponentFixture<ProfileGatePageComponent>;
  let component: ProfileGatePageComponent;
  let router: Router;

  let mockProfileService: {
    getMyProfile: ReturnType<typeof vi.fn>;
    putMyProfile: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockProfileService = {
      getMyProfile: vi
        .fn()
        .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 404 }))),
      putMyProfile: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ProfileGatePageComponent],
      providers: [
        ProfileApiAdapter,
        ProfileStore,
        { provide: ProfileService, useValue: mockProfileService },
        {
          provide: Router,
          useValue: {
            navigate: vi.fn().mockResolvedValue(true),
            navigateByUrl: vi.fn().mockResolvedValue(true),
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(ProfileGatePageComponent);
    component = fixture.componentInstance;
  }

  it('Garantia [empty] (S1): exibe formulário limpo sem dados, foco no campo, sem tratar vazio como falha', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.gate-title')?.textContent?.trim()).toBe('Escolha seu apelido');
    expect(compiled.querySelector('.gate-intro')?.textContent).toContain(
      'Seu apelido é o nome que aparece para outras pessoas na BETUFU',
    );

    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    expect(input).not.toBeNull();
    expect(input.value).toBe('');
    expect(input.getAttribute('maxlength')).toBe('20');
    expect(input.getAttribute('autocomplete')).toBe('off');
    expect(input.getAttribute('spellcheck')).toBe('false');
    expect(input.ownerDocument.activeElement).toBe(input);

    const counter = compiled.querySelector('.char-counter');
    expect(counter?.textContent?.trim()).toBe('0/20');
    expect(counter?.getAttribute('aria-hidden')).toBe('true');

    const help = compiled.querySelector('#nickname-help');
    expect(help?.textContent?.trim()).toBe(
      'De 3 a 20 caracteres. Use letras sem acento, números, hífen (-) ou sublinhado (_).',
    );

    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submitBtn.disabled).toBe(false);
    expect(submitBtn.textContent?.trim()).toBe('Salvar apelido e continuar');

    // Nenhuma mensagem de erro visível
    expect(compiled.querySelector('.field-error')).toBeNull();
    expect(compiled.querySelector('.alert-box')).toBeNull();
  });

  it('Garantia [loading] (S1): troca rótulo do CTA para Salvando… e desabilita apenas o botão durante envio', async () => {
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;

    input.value = 'novo_apelido';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    mockProfileService.putMyProfile.mockReturnValue(of(MOCK_SAVED_PROFILE_RESPONSE));

    const submitPromise = component.onSubmit();
    fixture.detectChanges();

    expect(submitBtn.disabled).toBe(true);
    expect(submitBtn.textContent?.trim()).toBe('Salvando…');

    await submitPromise;
    fixture.detectChanges();

    expect(submitBtn.disabled).toBe(false);
    expect(submitBtn.textContent?.trim()).toBe('Salvar apelido e continuar');
  });

  it('Garantia [error] (S1): campo vazio exibe mensagem ancorada e define foco no campo', async () => {
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    const focusSpy = vi.spyOn(input, 'focus');

    await component.onSubmit();
    fixture.detectChanges();

    const errorEl = compiled.querySelector('#nickname-error');
    expect(errorEl).not.toBeNull();
    expect(errorEl?.textContent?.trim()).toBe('Informe um apelido para continuar.');
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toContain('nickname-error');
    expect(focusSpy).toHaveBeenCalled();
  });

  it('Garantia [error] (S1): apelido fora do padrão exibe explicação literal do contrato', async () => {
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;

    // Menos de 3 caracteres
    input.value = 'ab';
    input.dispatchEvent(new Event('input'));
    await component.onSubmit();
    fixture.detectChanges();

    let errorEl = compiled.querySelector('#nickname-error');
    expect(errorEl?.textContent?.trim()).toBe(
      'Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).',
    );

    // Caracteres com acento ou especiais
    input.value = 'usuário@123';
    input.dispatchEvent(new Event('input'));
    await component.onSubmit();
    fixture.detectChanges();

    errorEl = compiled.querySelector('#nickname-error');
    expect(errorEl?.textContent?.trim()).toBe(
      'Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).',
    );
  });

  it('Garantia [error & partial] (S1): recusa do servidor exibe alerta no topo com correlationId e PRESERVA valor digitado (D-16)', async () => {
    createComponent();
    fixture.detectChanges();

    mockProfileService.putMyProfile.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-gate-err-789' },
          }),
      ),
    );

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;

    input.value = 'apelido_digitado';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    // Alerta no topo
    const alertBox = compiled.querySelector('.alert-box');
    expect(alertBox).not.toBeNull();
    expect(alertBox?.getAttribute('role')).toBe('alert');
    expect(alertBox?.textContent).toContain('Não foi possível salvar seu perfil.');
    expect(alertBox?.textContent).toContain('corr-gate-err-789');

    // D-16 / UI Consideration partial: o valor digitado NÃO foi perdido
    expect(input.value).toBe('apelido_digitado');
    expect(component.form.controls.nickname.value).toBe('apelido_digitado');
  });

  it('Garantia [error] (S1): detail arbitrário do servidor não chega ao DOM', async () => {
    createComponent();
    fixture.detectChanges();

    mockProfileService.putMyProfile.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              detail: 'CANARIO_PRIVADO_NAO_EXIBIR',
              correlationId: 'corr-gate-400-123',
            },
          }),
      ),
    );

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;
    input.value = 'apelido_valido';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    const alertBox = compiled.querySelector('.alert-box');
    expect(alertBox?.textContent).toContain('Não foi possível salvar seu perfil.');
    expect(alertBox?.textContent).toContain('corr-gate-400-123');
    expect(alertBox?.textContent).not.toContain('CANARIO_PRIVADO_NAO_EXIBIR');
  });

  it('Garantia [long-text] (S1): limita tamanho a 20 caracteres e atualiza contador', async () => {
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#nickname-input') as HTMLInputElement;

    input.value = '12345678901234567890';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    const counter = compiled.querySelector('.char-counter');
    expect(counter?.textContent?.trim()).toBe('20/20');
  });

  it('Garantia de segurança (S1): delega o shell ao layout autenticado e não duplica marcos ou navegação', async () => {
    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // O AuthenticatedLayoutComponent é o único dono destes marcos semânticos.
    expect(compiled.querySelector('header')).toBeNull();
    expect(compiled.querySelector('main')).toBeNull();
    expect(compiled.querySelector('.skip-link')).toBeNull();
    expect(compiled.querySelector('.brand-name')).toBeNull();
    expect(compiled.querySelector('.btn-logout')).toBeNull();

    // Itens navegacionais não existem no DOM (não apenas desabilitados)
    expect(compiled.querySelector('.user-navigation')).toBeNull();
    expect(compiled.querySelector('.nav-links')).toBeNull();
    expect(compiled.querySelector('mat-menu')).toBeNull();
  });

  it('Garantia [success]: salva apelido, anuncia em aria-live e redireciona', async () => {
    createComponent();
    fixture.detectChanges();

    mockProfileService.putMyProfile.mockReturnValue(of(MOCK_SAVED_PROFILE_RESPONSE));

    const input = fixture.nativeElement.querySelector('#nickname-input') as HTMLInputElement;
    input.value = 'meu_apelido';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    expect(component.successMessage()).toBe('Apelido salvo. Bem-vindo à BETUFU.');
    const liveRegion = fixture.nativeElement.querySelector('#gate-live-region');
    expect(liveRegion?.textContent).toContain('Apelido salvo. Bem-vindo à BETUFU.');
    expect(router.navigate).toHaveBeenCalledWith(['/conta/perfil']);
  });

  it('Garantia [clipboard]: copia correlationId de erro com feedback acessível', async () => {
    createComponent();
    fixture.detectChanges();

    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, {
      clipboard: {
        writeText: writeTextSpy,
      },
    });

    await component.copyCorrelationId('corr-gate-err-789');
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith('corr-gate-err-789');
    expect(component.copyStatus()).toBe('copied');

    const liveRegion = fixture.nativeElement.querySelector('#gate-live-region');
    expect(liveRegion?.textContent).toContain('Código de referência copiado com sucesso.');
  });
});
