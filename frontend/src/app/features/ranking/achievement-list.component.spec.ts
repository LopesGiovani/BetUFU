import { TestBed } from '@angular/core/testing';
import { describe, it, expect, vi } from 'vitest';
import { AchievementListComponent } from './achievement-list.component';
import { AchievementApiAdapter } from './achievement-api.adapter';
describe('Conquistas derivadas', () => {
  async function render(read: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      imports: [AchievementListComponent],
      providers: [{ provide: AchievementApiAdapter, useValue: { read } }],
    });
    const f = TestBed.createComponent(AchievementListComponent);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    return f;
  }
  it('explica vazio e regras sem promessa financeira', async () => {
    const f = await render(vi.fn().mockResolvedValue([]));
    expect(f.nativeElement.textContent).toContain('Voce ainda nao recebeu conquistas.');
    expect(f.nativeElement.querySelector('a').getAttribute('href')).toBe('#achievement-rules');
    expect(f.nativeElement.textContent).toContain('Nenhuma conquista gera creditos ou premios.');
  });
  it('mantem conquista revogada com regra e edicao', async () => {
    const f = await render(
      vi.fn().mockResolvedValue([
        {
          awardId: 'a',
          name: 'Azarao',
          description: 'Acerto',
          rule: 'Odd pelo menos 3',
          catalogEdition: 'v1',
          state: 'REVOKED',
          awardedAt: '2026-10-01T00:00:00Z',
          revokedAt: '2026-10-02T00:00:00Z',
        },
      ]),
    );
    expect(f.nativeElement.textContent).toContain('Revogada');
    expect(f.nativeElement.textContent).toContain('Odd pelo menos 3');
    expect(f.nativeElement.textContent).toContain('v1');
    expect(f.nativeElement.querySelector('main')).toBeNull();
  });
  it('erro usa mensagem sanitizada e retry', async () => {
    const f = await render(vi.fn().mockRejectedValue(new Error('email and identity')));
    expect(f.nativeElement.textContent).toContain('Nao foi possivel carregar suas conquistas.');
    expect(f.nativeElement.textContent).not.toContain('email and identity');
    expect(f.nativeElement.querySelector('button').textContent).toContain('Recarregar conquistas');
  });
});
