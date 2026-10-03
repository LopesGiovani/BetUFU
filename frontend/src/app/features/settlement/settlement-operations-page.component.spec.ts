import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { SettlementOperationsPageComponent } from './settlement-operations-page.component';
import { SettlementService } from '../../api/generated';
import { OperationsApiAdapter } from '../operations/operations-api.adapter';
import { PrivateApiRecovery } from '../../core/session/private-api-recovery';
import { firstValueFrom } from 'rxjs';
describe('Autorizacao explicita de liquidacao', () => {
  const candidate = {
    eventId: 'e',
    eventTitle: 'Jogo',
    scheduledAt: '2026-10-01T00:00:00Z',
    outcomeFactId: 'f',
    confirmedVersionId: 'v',
    outcomeKind: 'SPORTING_RESULT',
    homeScore: 2,
    awayScore: 1,
    alreadyAuthorized: false,
  };
  async function render(permitted = true) {
    const api = {
      listSettlementAuthorizationCandidates: vi.fn().mockReturnValue(of({ items: [candidate] })),
      authorizeSettlement: vi.fn(),
      getSettlementCaseProgress: vi.fn(),
    };
    TestBed.configureTestingModule({
      imports: [SettlementOperationsPageComponent],
      providers: [
        provideRouter([]),
        { provide: SettlementService, useValue: api },
        { provide: PrivateApiRecovery, useValue: { read: firstValueFrom } },
        {
          provide: OperationsApiAdapter,
          useValue: {
            fetchOperationalCapabilities: () => of(permitted ? ['AUTHORIZE_SETTLEMENT'] : []),
          },
        },
      ],
    });
    const f = TestBed.createComponent(SettlementOperationsPageComponent);
    f.detectChanges();
    await f.whenStable();
    await vi.waitFor(() => expect(f.componentInstance.loading()).toBe(false));
    f.detectChanges();
    const dialog = f.nativeElement.querySelector('dialog');
    dialog.showModal = vi.fn();
    dialog.close = vi.fn();
    return { f, api, dialog };
  }
  it('nao consulta candidatos quando nao autorizado', async () => {
    const { f, api } = await render(false);
    expect(api.listSettlementAuthorizationCandidates).not.toHaveBeenCalled();
    expect(f.nativeElement.textContent).toContain('Voltar a Arena');
  });
  it('conferencia exata e cancelar nao produz autorizacao', async () => {
    const { f, api, dialog } = await render();
    const button = f.nativeElement.querySelector('article button');
    button.focus();
    button.click();
    f.detectChanges();
    expect(dialog.textContent).toContain('Versao exata: v');
    f.componentInstance.cancel();
    expect(api.authorizeSettlement).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(button);
  });
  it('409 bloqueia ate atualizar e nao autoriza nova versao silenciosamente', async () => {
    const { f, api } = await render();
    api.authorizeSettlement.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    f.componentInstance.ask(candidate, {
      currentTarget: f.nativeElement.querySelector('button'),
    } as unknown as Event);
    await f.componentInstance.authorize();
    await f.componentInstance.authorize();
    f.detectChanges();
    expect(api.authorizeSettlement).toHaveBeenCalledTimes(1);
    expect(f.componentInstance.stale()).toBe(true);
    expect(api.authorizeSettlement.mock.calls[0][0].settlementAuthorizationRequest).toEqual({
      expectedOutcomeFactId: 'f',
      expectedConfirmedVersionId: 'v',
    });
    await f.componentInstance.load();
    expect(f.componentInstance.stale()).toBe(false);
    expect(f.componentInstance.selected()).toBeNull();
  });
  it('Tab e Shift+Tab mantem o foco na conferencia', async () => {
    const { dialog } = await render();
    const buttons = dialog.querySelectorAll('button') as NodeListOf<HTMLButtonElement>;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    last.focus();
    const tab = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
    last.dispatchEvent(tab);
    expect(tab.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(first);
    const back = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true,
    });
    first.dispatchEvent(back);
    expect(back.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(last);
  });
  it('progresso parcial nunca aparece concluido', async () => {
    const { f, api } = await render();
    api.authorizeSettlement.mockReturnValue(
      of({
        caseProgress: { caseId: 'c', status: 'RUNNING', totalUnits: 4, completedUnits: 2 },
      }),
    );
    f.componentInstance.ask(candidate, {
      currentTarget: f.nativeElement.querySelector('button'),
    } as unknown as Event);
    await f.componentInstance.authorize();
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Autorizacao registrada');
    expect(f.nativeElement.textContent).toContain('Pendente ou parcial em processamento');
    expect(f.nativeElement.textContent).not.toContain('Concluida');
  });
});
