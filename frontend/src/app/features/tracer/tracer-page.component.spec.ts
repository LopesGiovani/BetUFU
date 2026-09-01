import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';

import { CurrentIdentityResponse, IdentityService } from '../../api/generated';
import { TracerPageComponent } from './tracer-page.component';

describe('TracerPageComponent', () => {
  const response: CurrentIdentityResponse = {
    identityId: '11a70246-3db4-4c53-b2ab-3fdd91a3ea84',
    status: CurrentIdentityResponse.StatusEnum.Persisted,
    createdAt: '2026-08-14T12:00:00Z',
    updatedAt: '2026-08-14T12:00:01Z',
  };

  let fixture: ComponentFixture<TracerPageComponent>;
  let putCurrentIdentity: ReturnType<typeof vi.fn>;

  function button(): HTMLButtonElement {
    const element = fixture.nativeElement as HTMLElement;
    const action = element.querySelector('button');

    if (!(action instanceof HTMLButtonElement)) {
      throw new Error('Botão de verificação não renderizado.');
    }

    return action;
  }

  beforeEach(async () => {
    putCurrentIdentity = vi.fn();

    await TestBed.configureTestingModule({
      imports: [TracerPageComponent],
      providers: [
        {
          provide: IdentityService,
          useValue: { putCurrentIdentity },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TracerPageComponent);
    fixture.detectChanges();
  });

  it('bloqueia somente a ação enquanto o PUT está pendente', () => {
    const pendingResponse = new Subject<CurrentIdentityResponse>();
    putCurrentIdentity.mockReturnValue(pendingResponse);

    button().click();
    fixture.detectChanges();

    expect(putCurrentIdentity).toHaveBeenCalledTimes(1);
    expect(button().disabled).toBe(true);
    expect(button().textContent?.trim()).toBe('Verificando…');

    fixture.componentInstance.verifyIntegration();
    expect(putCurrentIdentity).toHaveBeenCalledTimes(1);

    pendingResponse.next(response);
    pendingResponse.complete();
    fixture.detectChanges();

    expect(button().disabled).toBe(false);
    expect(button().textContent?.trim()).toBe('Verificar integração');
  });

  it('exibe somente a identidade opaca persistida em sucesso', () => {
    putCurrentIdentity.mockReturnValue(of(response));

    button().click();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const status = element.querySelector('[role="status"]');

    expect(putCurrentIdentity).toHaveBeenCalledTimes(1);
    expect(status?.textContent).toContain(response.identityId);
    expect(element.textContent).not.toContain(response.status);
    expect(element.textContent).not.toContain(response.createdAt);
    expect(element.textContent).not.toContain(response.updatedAt);
  });

  it('mantém a página disponível para nova tentativa sem retry automático', () => {
    putCurrentIdentity.mockReturnValue(
      throwError(() => new Error('backend-interno: segredo-de-diagnóstico')),
    );

    button().click();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const alert = element.querySelector('[role="alert"]');

    expect(putCurrentIdentity).toHaveBeenCalledTimes(1);
    expect(alert?.textContent?.trim()).toBe(
      'Não foi possível verificar a integração. Tente novamente.',
    );
    expect(element.textContent).not.toContain('segredo-de-diagnóstico');
    expect(button().disabled).toBe(false);
    expect(button().textContent?.trim()).toBe('Verificar integração');

    button().click();
    expect(putCurrentIdentity).toHaveBeenCalledTimes(2);
  });
});
