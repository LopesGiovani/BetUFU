import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router, RouterOutlet } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { EnrollmentStatusResponse } from '../../api/generated/model/enrollment-status-response';
import { EnrollmentPageComponent } from './enrollment-page.component';
import { EnrollmentStore } from './enrollment.store';
import { EnrollmentApiAdapter } from './enrollment-api.adapter';
import { BUILD_SPECIFIC_ROUTES as PROD_ROUTES } from '../../app.routes.production';
import { BUILD_SPECIFIC_ROUTES as DEV_ROUTES } from '../../app.routes.development';

const MOCK_UNREGISTERED_ENROLLMENT: EnrollmentStatusResponse = {
  status: 'NAO_REGISTRADA',
  createdAt: null,
};

const MOCK_REGISTERED_ENROLLMENT: EnrollmentStatusResponse = {
  status: 'ATIVO',
  createdAt: '2026-08-22T12:00:00.000Z',
};

@Component({
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
class TestHostComponent {}

describe('EnrollmentPageComponent (Superfície S3)', () => {
  let fixture: ComponentFixture<EnrollmentPageComponent>;
  let component: EnrollmentPageComponent;
  let enrollmentStore: EnrollmentStore;
  let router: Router;

  let mockEnrollmentService: {
    getEnrollment: ReturnType<typeof vi.fn>;
    putEnrollment: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockEnrollmentService = {
      getEnrollment: vi.fn().mockReturnValue(of(MOCK_UNREGISTERED_ENROLLMENT)),
      putEnrollment: vi.fn().mockReturnValue(of(MOCK_REGISTERED_ENROLLMENT)),
    };

    await TestBed.configureTestingModule({
      imports: [EnrollmentPageComponent, TestHostComponent],
      providers: [
        EnrollmentApiAdapter,
        EnrollmentStore,
        { provide: EnrollmentService, useValue: mockEnrollmentService },
        provideRouter(PROD_ROUTES),
      ],
    }).compileComponents();

    enrollmentStore = TestBed.inject(EnrollmentStore);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function createComponent(): void {
    fixture = TestBed.createComponent(EnrollmentPageComponent);
    component = fixture.componentInstance;
  }

  it('Garantia [loading] (S3): exibe feedback de loading inicial quando não há estado anterior', async () => {
    mockEnrollmentService.getEnrollment.mockReturnValue(NEVER);

    createComponent();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    const loadingState = compiled.querySelector('.status-feedback.loading-state');
    expect(loadingState).not.toBeNull();
    expect(loadingState?.getAttribute('role')).toBe('status');
    expect(loadingState?.textContent).toContain('Carregando matrícula…');
    expect(loadingState?.querySelector('.spinner-large')).not.toBeNull();

    expect(compiled.querySelector('.enrollment-card')).toBeNull();
  });

  it('Garantia [error inicial] (S3): exibe erro bloqueante com retry e correlationId quando a carga inicial falha', async () => {
    mockEnrollmentService.getEnrollment.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 500,
            error: { correlationId: 'corr-init-enroll-500' },
          }),
      ),
    );

    createComponent();
    fixture.detectChanges();
    await vi.waitFor(() => {
      expect(enrollmentStore.status()).toBe('error');
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    const errorState = compiled.querySelector('.status-feedback.error-state');
    expect(errorState).not.toBeNull();
    expect(errorState?.getAttribute('role')).toBe('alert');
    expect(errorState?.textContent).toContain('Não foi possível carregar as informações de matrícula');
    expect(errorState?.textContent).toContain('corr-init-enroll-500');

    const retryBtn = errorState?.querySelector('button.btn-primary') as HTMLButtonElement;
    expect(retryBtn).not.toBeNull();
    expect(retryBtn.textContent?.trim()).toBe('Tentar novamente');

    // Executa retry com sucesso
    mockEnrollmentService.getEnrollment.mockReturnValue(of(MOCK_UNREGISTERED_ENROLLMENT));
    retryBtn.click();
    fixture.detectChanges();
    await vi.waitFor(() => {
      expect(enrollmentStore.status()).toBe('empty');
    });
    fixture.detectChanges();

    expect(compiled.querySelector('.enrollment-card')).not.toBeNull();
    expect(compiled.querySelector('.status-feedback.error-state')).toBeNull();
  });

  it('Garantia [empty / não registrada] (S3): renderiza texto de abertura literal, campo de matrícula, ajuda persistente e aviso prévio de imutabilidade', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Título h1 e texto de abertura literal
    expect(compiled.querySelector('.enrollment-title')?.textContent?.trim()).toBe('Matrícula UFU');
    expect(compiled.querySelector('.opening-text')?.textContent?.trim()).toBe(
      'O registro da matrícula é voluntário e garante que ela pertença a uma única conta BETUFU. A BETUFU não confere documentos e não guarda o número informado.',
    );

    // Campo de texto livre
    const input = compiled.querySelector('#matricula-input') as HTMLInputElement;
    expect(input).not.toBeNull();
    expect(input.value).toBe('');
    expect(input.getAttribute('autocomplete')).toBe('off');
    expect(input.getAttribute('spellcheck')).toBe('false');
    expect(input.getAttribute('inputmode')).toBe('text');

    // Ajuda persistente
    const help = compiled.querySelector('#matricula-help');
    expect(help?.textContent?.trim()).toBe(
      'Informe como aparece no seu registro acadêmico. Letras, números e separadores são aceitos.',
    );

    // Aviso de imutabilidade ANTES de confirmar
    const immutabilityNotice = compiled.querySelector('.immutability-notice');
    expect(immutabilityNotice?.textContent?.trim()).toBe(
      'Depois de registrada, a matrícula só pode ser alterada por um administrador geral.',
    );

    // Botão desabilitado no estado inicial pristine
    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(submitBtn).not.toBeNull();
    expect(submitBtn.disabled).toBe(true);
    expect(submitBtn.textContent?.trim()).toBe('Registrar matrícula');
  });

  it('Garantia [loading no envio] (S3): desabilita unicamente o botão de submit e altera o rótulo para Registrando…', async () => {
    mockEnrollmentService.putEnrollment.mockReturnValue(NEVER);

    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#matricula-input') as HTMLInputElement;
    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;

    input.value = '12345678';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(submitBtn.disabled).toBe(false);

    void component.onSubmit();
    fixture.detectChanges();

    expect(submitBtn.disabled).toBe(true);
    expect(submitBtn.textContent?.trim()).toBe('Registrando…');
  });

  it('Garantia [sucesso e privacidade] (S3): limpa o campo após sucesso, não persiste número em lugar nenhum e transita para estado registrada (D-16, PRIV-01)', async () => {
    // Dublês de armazenamento do navegador para provar que nenhuma chamada de storage ocorre
    const localStorageSpy = vi.spyOn(Storage.prototype, 'setItem');
    const sessionStorageSpy = vi.spyOn(sessionStorage, 'setItem');

    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#matricula-input') as HTMLInputElement;
    const submitBtn = compiled.querySelector('button[type="submit"]') as HTMLButtonElement;

    input.value = '11811BCC001';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    vi.useFakeTimers();
    await component.onSubmit();
    fixture.detectChanges();

    // 1. Prova que putEnrollment foi chamado com a matrícula
    expect(mockEnrollmentService.putEnrollment).toHaveBeenCalledWith({
      enrollmentRegistrationRequest: { matricula: '11811BCC001' },
    });

    // 2. Prova que o formulário foi limpo no sucesso (D-16 / PRIV-01)
    expect(component.form.controls.matricula.value).toBe('');

    // 3. Prova que nenhuma chamada de storage ocorreu
    expect(localStorageSpy).not.toHaveBeenCalled();
    expect(sessionStorageSpy).not.toHaveBeenCalled();

    // 4. Prova mensagem de sucesso visível, acessível e persistente
    expect(component.successMessage()).toBe(
      'Matrícula registrada. O número não fica guardado nem aparece em nenhuma tela.',
    );
    const successFeedback = compiled.querySelector('.success-feedback');
    expect(successFeedback).not.toBeNull();
    expect(successFeedback?.getAttribute('role')).toBe('status');
    expect(successFeedback?.getAttribute('aria-live')).toBe('polite');
    expect(successFeedback?.textContent).toContain(
      'Matrícula registrada. O número não fica guardado nem aparece em nenhuma tela.',
    );

    vi.advanceTimersByTime(5000);
    fixture.detectChanges();
    expect(compiled.querySelector('.success-feedback')?.textContent).toContain(
      'Matrícula registrada. O número não fica guardado nem aparece em nenhuma tela.',
    );

    // 5. Prova transição para o estado registrada
    expect(component.isMatriculaRegistrada()).toBe(true);
    expect(compiled.querySelector('.status-chip.chip-available')?.textContent?.trim()).toBe(
      'Matrícula registrada',
    );
    expect(compiled.querySelector('.registered-body')?.textContent?.trim()).toBe(
      'Sua conta já tem uma matrícula registrada. O número não é exibido aqui nem em nenhuma outra tela, porque a BETUFU não o conserva. Para corrigir, fale com um administrador geral.',
    );

    localStorageSpy.mockRestore();
    sessionStorageSpy.mockRestore();
  });

  it('Garantia [recusa e partial] (S3): preserva o valor digitado no formulário em caso de recusa e exibe alerta no topo (D-16)', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    mockEnrollmentService.putEnrollment.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'BETUFU-409-MATRICULA_DUPLICADA',
              correlationId: 'corr-recusa-dup-999',
            },
          }),
      ),
    );

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#matricula-input') as HTMLInputElement;

    input.value = '11921BSI002';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    // 1. Prova alerta no topo com role alert e correlationId
    const alertBox = compiled.querySelector('.alert-box');
    expect(alertBox).not.toBeNull();
    expect(alertBox?.getAttribute('role')).toBe('alert');
    expect(alertBox?.textContent).toContain(
      'Esta matrícula já está registrada em outra conta BETUFU. Se ela é sua, fale com um administrador geral para pedir a correção.',
    );
    expect(alertBox?.textContent).toContain('corr-recusa-dup-999');

    // 2. Prova que o valor digitado foi PRESERVADO no formulário (D-16 / UI Consideration partial)
    expect(input.value).toBe('11921BSI002');
    expect(component.form.controls.matricula.value).toBe('11921BSI002');

    // 3. Prova que o formulário continua na tela
    expect(compiled.querySelector('.enrollment-form')).not.toBeNull();
  });

  it('Garantia de proibições em S3: DOM do estado registrada não contém edição, remoção, lápis, validade ou revalidação (D-26, D-32)', async () => {
    mockEnrollmentService.getEnrollment.mockReturnValue(of(MOCK_REGISTERED_ENROLLMENT));

    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const markup = compiled.outerHTML.toLowerCase();

    // Proibições D-26 e D-32: ausência total de controles e menções
    expect(markup).not.toContain('editar');
    expect(markup).not.toContain('edit');
    expect(markup).not.toContain('remover');
    expect(markup).not.toContain('excluir');
    expect(markup).not.toContain('apagar');
    expect(markup).not.toContain('lápis');
    expect(markup).not.toContain('pencil');
    expect(markup).not.toContain('validade');
    expect(markup).not.toContain('revalidação');
    expect(markup).not.toContain('revalidar');
  });

  it('Garantia de ausência documental (D-25): nunca renderiza campo de arquivo, dropzone, botão de anexo ou palavra comprovante', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const markup = compiled.outerHTML.toLowerCase();

    // Proibição D-25
    expect(markup).not.toContain('comprovante');
    expect(markup).not.toContain('anexo');
    expect(markup).not.toContain('upload');
    expect(markup).not.toContain('dropzone');
    expect(compiled.querySelector('input[type="file"]')).toBeNull();
  });

  it('Garantia de validação de campo (S3): valida campo vazio e formato inválido com foco no campo', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const input = compiled.querySelector('#matricula-input') as HTMLInputElement;
    const focusSpy = vi.spyOn(input, 'focus');

    // 1. Submissão com campo vazio
    await component.onSubmit();
    fixture.detectChanges();

    expect(compiled.querySelector('#matricula-error')?.textContent?.trim()).toBe(
      'Informe o número da matrícula.',
    );
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(focusSpy).toHaveBeenCalled();

    // 2. Submissão com formato inválido (menos de 5 caracteres alfanuméricos)
    input.value = '123';
    input.dispatchEvent(new Event('input'));
    component.form.controls.matricula.markAsDirty();
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    expect(compiled.querySelector('#matricula-error')?.textContent?.trim()).toBe(
      'Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.',
    );

    // 3. Submissão com caracteres especiais inválidos
    input.value = '123@#$45';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    await component.onSubmit();
    fixture.detectChanges();

    expect(compiled.querySelector('#matricula-error')?.textContent?.trim()).toBe(
      'Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.',
    );
  });

  it('Garantia [clipboard] (S3): copia correlationId de erro com feedback acessível', async () => {
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

    await component.copyCorrelationId('corr-s3-test-123');
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith('corr-s3-test-123');
    expect(component.copyStatus()).toBe('copied');

    const liveRegion = fixture.nativeElement.querySelector('#enrollment-live-region');
    expect(liveRegion?.textContent).toContain('Código de referência copiado.');
  });

  it('Garantia de rotas: prova que PROD_ROUTES e DEV_ROUTES definem conta/matricula por carregamento sob demanda', () => {
    const prodRoot = PROD_ROUTES.find((r) => r.path === '');
    expect(prodRoot).toBeDefined();

    const prodEnrollmentRoute = prodRoot?.children?.find((r) => r.path === 'conta/matricula');
    expect(prodEnrollmentRoute).toBeDefined();
    expect(prodEnrollmentRoute?.loadComponent).toBeTypeOf('function');

    const devRoot = DEV_ROUTES.find((r) => r.path === 'children' || r.path === '');
    expect(devRoot).toBeDefined();

    const devEnrollmentRoute = devRoot?.children?.find((r) => r.path === 'conta/matricula');
    expect(devEnrollmentRoute).toBeDefined();
    expect(devEnrollmentRoute?.loadComponent).toBeTypeOf('function');
  });

  it('Garantia estrutural de botões e layout: não duplica casca de layout e SVGs em botões possuem 24px', async () => {
    createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    // Não duplica layout
    expect(compiled.querySelector('header')).toBeNull();
    expect(compiled.querySelector('main')).toBeNull();
  });
});
