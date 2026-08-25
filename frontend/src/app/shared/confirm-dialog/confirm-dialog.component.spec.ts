import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfirmDialogComponent } from './confirm-dialog.component';

describe('ConfirmDialogComponent (Acessibilidade e Foco Modal - D-49 e T-02-19-02)', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let component: ConfirmDialogComponent;
  let triggerButton: HTMLButtonElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;

    // Cria um elemento disparador simulado no DOM
    triggerButton = document.createElement('button');
    triggerButton.textContent = 'Gatilho';
    document.body.appendChild(triggerButton);
    component.triggerElement = triggerButton;
  });

  afterEach(() => {
    triggerButton.remove();
  });

  function openDialog(options: Partial<ConfirmDialogComponent> = {}): void {
    component.isOpen = true;
    component.title = options.title ?? 'Conceder papel?';
    component.body = options.body ?? 'A pessoa passa a ter esse papel imediatamente.';
    component.confirmLabel = options.confirmLabel ?? 'Conceder papel';
    component.cancelLabel = options.cancelLabel ?? 'Cancelar';
    component.isDestructive = options.isDestructive ?? false;
    component.isLoading = options.isLoading ?? false;
    component.ngOnChanges({
      isOpen: {
        currentValue: true,
        previousValue: false,
        firstChange: true,
        isFirstChange: () => true,
      },
    });
    fixture.detectChanges();
  }

  it('Garantia Acessibilidade: renderiza com semântica de diálogo modal acessível', () => {
    openDialog();
    const dialogEl = fixture.nativeElement.querySelector('[role="dialog"]');
    expect(dialogEl).not.toBeNull();
    expect(dialogEl.getAttribute('aria-modal')).toBe('true');
    expect(dialogEl.getAttribute('aria-labelledby')).toBe('confirm-dialog-title');
    expect(dialogEl.getAttribute('aria-describedby')).toBe('confirm-dialog-body');

    const titleEl = fixture.nativeElement.querySelector('#confirm-dialog-title');
    const bodyEl = fixture.nativeElement.querySelector('#confirm-dialog-body');
    expect(titleEl?.textContent?.trim()).toBe('Conceder papel?');
    expect(bodyEl?.textContent?.trim()).toBe('A pessoa passa a ter esse papel imediatamente.');
  });

  // PROVA DE FOCO 1: Foco inicial obrigatório no botão Cancelar
  it('Prova de Foco 1 (D-49): foco inicial vai automaticamente para o botão Cancelar ao abrir o diálogo', async () => {
    openDialog();
    fixture.detectChanges();

    const cancelBtn = fixture.nativeElement.querySelector('.btn-cancel');
    await vi.waitFor(() => expect(document.activeElement).toBe(cancelBtn));
  });

  // PROVA DE FOCO 2: Focus Trap (Tab e Shift+Tab permanecem confinados no modal)
  it('Prova de Foco 2: focus trap prende a navegação por Tab e Shift+Tab dentro do diálogo modal', async () => {
    openDialog();
    fixture.detectChanges();

    const cancelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-cancel');
    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    await vi.waitFor(() => expect(document.activeElement).toBe(cancelBtn));

    // 1. Estando no Confirm (último elemento), pressionar Tab deve mover o foco para Cancel (primeiro)
    confirmBtn.focus();
    expect(document.activeElement).toBe(confirmBtn);

    const tabEvent = new KeyboardEvent('keydown', {
      key: 'Tab',
      bubbles: true,
      cancelable: true,
      shiftKey: false,
    });
    component.onKeyDown(tabEvent);
    expect(document.activeElement).toBe(cancelBtn);

    // 2. Estando no Cancel (primeiro elemento), pressionar Shift+Tab deve mover o foco para Confirm (último)
    cancelBtn.focus();
    expect(document.activeElement).toBe(cancelBtn);

    const shiftTabEvent = new KeyboardEvent('keydown', {
      key: 'Tab',
      bubbles: true,
      cancelable: true,
      shiftKey: true,
    });
    component.onKeyDown(shiftTabEvent);
    expect(document.activeElement).toBe(confirmBtn);
  });

  // PROVA DE FOCO 3: Tecla Escape cancela e devolve o foco ao elemento disparador
  it('Prova de Foco 3: tecla Escape cancela e fecha o diálogo, devolvendo o foco ao elemento disparador', async () => {
    openDialog();
    fixture.detectChanges();
    await vi.waitFor(() => expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('.btn-cancel'),
    ));

    const cancelSpy = vi.spyOn(component.cancelled, 'emit');
    const focusSpy = vi.spyOn(triggerButton, 'focus');

    const escEvent = new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true,
    });
    component.onKeyDown(escEvent);

    expect(cancelSpy).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(focusSpy).toHaveBeenCalledTimes(1));
  });

  it('clique no botão Cancelar emite cancelled e devolve o foco ao elemento disparador', async () => {
    openDialog();
    fixture.detectChanges();
    await vi.waitFor(() => expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('.btn-cancel'),
    ));

    const cancelSpy = vi.spyOn(component.cancelled, 'emit');
    const focusSpy = vi.spyOn(triggerButton, 'focus');

    const cancelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-cancel');
    cancelBtn.click();

    expect(cancelSpy).toHaveBeenCalledTimes(1);
    await vi.waitFor(() => expect(focusSpy).toHaveBeenCalledTimes(1));
  });

  it('clique no botão Confirmar emite confirmed e deixa a restauração ao consumidor após a mutação', async () => {
    openDialog();
    fixture.detectChanges();
    await vi.waitFor(() => expect(document.activeElement).toBe(
      fixture.nativeElement.querySelector('.btn-cancel'),
    ));

    const confirmSpy = vi.spyOn(component.confirmed, 'emit');
    const focusSpy = vi.spyOn(triggerButton, 'focus');

    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    confirmBtn.click();

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(focusSpy).not.toHaveBeenCalled();
  });

  it('aplica estilo destrutivo (btn-destructive) quando isDestructive é verdadeiro', () => {
    openDialog({ isDestructive: true, confirmLabel: 'Revogar papel' });

    const confirmBtn = fixture.nativeElement.querySelector('.btn-confirm');
    expect(confirmBtn.classList.contains('btn-destructive')).toBe(true);
    expect(confirmBtn.classList.contains('btn-accent')).toBe(false);
  });

  it('aplica estilo acento (btn-accent) quando isDestructive é falso', () => {
    openDialog({ isDestructive: false, confirmLabel: 'Conceder papel' });

    const confirmBtn = fixture.nativeElement.querySelector('.btn-confirm');
    expect(confirmBtn.classList.contains('btn-accent')).toBe(true);
    expect(confirmBtn.classList.contains('btn-destructive')).toBe(false);
  });

  it('quando isLoading é verdadeiro, desabilita os botões e exibe spinner', () => {
    openDialog({ isLoading: true });

    const cancelBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-cancel');
    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-confirm');
    const spinner = fixture.nativeElement.querySelector('.btn-spinner');

    expect(cancelBtn.disabled).toBe(true);
    expect(confirmBtn.disabled).toBe(true);
    expect(spinner).not.toBeNull();
  });
});
