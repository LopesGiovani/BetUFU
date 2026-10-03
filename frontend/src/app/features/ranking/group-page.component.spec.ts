import { TestBed } from '@angular/core/testing';
import { describe, it, expect, vi } from 'vitest';
import { GroupPageComponent } from './group-page.component';
import { GroupApiAdapter } from './group-api.adapter';
describe('Grupos privados', () => {
  const api = {
    list: vi.fn(),
    read: vi.fn(),
    create: vi.fn(),
    join: vi.fn(),
    leave: vi.fn(),
    close: vi.fn(),
    remove: vi.fn(),
    invite: vi.fn(),
  };
  async function render() {
    vi.resetAllMocks();
    api.list.mockResolvedValue([{ groupId: 'g', name: 'Amigos', closed: false, myRole: 'OWNER' }]);
    api.read.mockResolvedValue({ groupId: 'g', name: 'Amigos', closed: false, standings: [] });
    TestBed.configureTestingModule({
      imports: [GroupPageComponent],
      providers: [{ provide: GroupApiAdapter, useValue: api }],
    });
    const f = TestBed.createComponent(GroupPageComponent);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    return f;
  }
  it('cancelar confirmacao nao chama API e restaura foco', async () => {
    const f = await render();
    await f.componentInstance.store.open('g');
    f.detectChanges();
    const dialog = f.nativeElement.querySelector('dialog') as HTMLDialogElement;
    dialog.showModal = vi.fn();
    dialog.close = vi.fn();
    const button = [...f.nativeElement.querySelectorAll('button')].find(
      (b) => (b as HTMLElement).textContent?.trim() === 'Encerrar grupo',
    ) as HTMLButtonElement;
    button.focus();
    button.click();
    f.detectChanges();
    expect(dialog.showModal).toHaveBeenCalledOnce();
    f.componentInstance.cancel(new Event('cancel', { cancelable: true }));
    f.detectChanges();
    expect(api.close).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(button);
  });
  it('confirmar encerra uma vez e nao cria efeito financeiro', async () => {
    const f = await render();
    await f.componentInstance.store.open('g');
    f.detectChanges();
    const dialog = f.nativeElement.querySelector('dialog');
    dialog.showModal = vi.fn();
    dialog.close = vi.fn();
    const trigger = f.nativeElement.querySelector('button');
    f.componentInstance.ask('close', { currentTarget: trigger } as unknown as Event);
    await f.componentInstance.confirm();
    expect(api.close).toHaveBeenCalledExactlyOnceWith('g');
    expect(f.componentInstance.pending()).toBeNull();
  });
  it('Tab e Shift+Tab mantem o foco na confirmacao', async () => {
    const f = await render();
    const buttons = f.nativeElement.querySelectorAll(
      'dialog button',
    ) as NodeListOf<HTMLButtonElement>;
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
  it('convite invalido nao revela detalhes e pode ser substituido', async () => {
    const f = await render();
    api.join.mockRejectedValue(new Error('group exists owner private'));
    f.componentInstance.code.setValue('bad');
    f.componentInstance.join();
    await f.whenStable();
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain(
      'Nao foi possivel entrar neste grupo com esse convite.',
    );
    expect(f.nativeElement.textContent).not.toContain('owner private');
    expect(f.nativeElement.textContent).toContain('Tentar outro convite');
  });
  it('membro nao recebe gestao do owner', async () => {
    const f = await render();
    f.componentInstance.store.groups.set([
      { groupId: 'g', name: 'Amigos', closed: false, myRole: 'MEMBER' },
    ]);
    await f.componentInstance.store.open('g');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Sair do grupo');
    expect(f.nativeElement.textContent).not.toContain('Encerrar grupo');
    expect(f.nativeElement.textContent).not.toContain('Emitir convite');
  });
  it('administrador emite convite mas nao renomeia grupo', async () => {
    const f = await render();
    f.componentInstance.store.groups.set([
      { groupId: 'g', name: 'Amigos', closed: false, myRole: 'ADMIN' },
    ]);
    await f.componentInstance.store.open('g');
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Emitir convite');
    expect(f.nativeElement.textContent).not.toContain('Renomear grupo');
    expect(f.nativeElement.textContent).not.toContain('Encerrar grupo');
  });
});
