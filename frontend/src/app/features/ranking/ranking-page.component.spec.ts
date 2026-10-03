import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, vi } from 'vitest';
import { RankingPageComponent } from './ranking-page.component';
import { RankingApiAdapter } from './ranking-api.adapter';
describe('Ranking vitalicio', () => {
  async function render(read: ReturnType<typeof vi.fn>) {
    TestBed.configureTestingModule({
      imports: [RankingPageComponent],
      providers: [provideRouter([]), { provide: RankingApiAdapter, useValue: { read } }],
    });
    const f = TestBed.createComponent(RankingPageComponent);
    f.detectChanges();
    await f.whenStable();
    f.detectChanges();
    return f;
  }
  it('exibe vazio acionavel sem main aninhado', async () => {
    const f = await render(vi.fn().mockResolvedValue([]));
    expect(f.nativeElement.textContent).toContain(
      'Ainda nao ha palpites com resultado definitivo para classificar.',
    );
    expect(f.nativeElement.querySelector('a').textContent).toContain('Voltar a Arena');
    expect(f.nativeElement.querySelector('main')).toBeNull();
  });
  it('preserva empate e estado inativo sem PII', async () => {
    const f = await render(
      vi.fn().mockResolvedValue([
        {
          position: 1,
          nickname: 'aluno',
          wins: 3,
          losses: 1,
          hitRate: 0.75,
          verificationActive: false,
        },
        {
          position: 1,
          nickname: 'atleta',
          wins: 3,
          losses: 1,
          hitRate: 0.75,
          verificationActive: true,
        },
      ]),
    );
    expect(f.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
    expect(f.nativeElement.textContent).toContain('Matricula atual inativa');
    expect(f.nativeElement.querySelector('.table-scroll').getAttribute('tabindex')).toBe('0');
  });
  it('permite nova leitura apos erro e nunca mostra dados inventados', async () => {
    const read = vi.fn().mockRejectedValueOnce(new Error('private payload')).mockResolvedValue([]);
    const f = await render(read);
    expect(f.nativeElement.textContent).not.toContain('private payload');
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain(
      'Nao foi possivel carregar o ranking.',
    );
    f.nativeElement.querySelector('button').click();
    await f.whenStable();
    f.detectChanges();
    expect(read).toHaveBeenCalledTimes(2);
    expect(f.componentInstance.store.error()).toBe(false);
  });
});
