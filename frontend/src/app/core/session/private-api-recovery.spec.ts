import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { describe, it, expect, vi } from 'vitest';
import { PrivateApiRecovery } from './private-api-recovery';
import { SessionStore } from './session.store';
describe('Sessao expirada em pagina privada', () => {
  it('limpa estado e redireciona 401 com contexto sem repetir mutacao', async () => {
    const router = { url: '/conta/grupos', navigate: vi.fn() };
    const session = { reset: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        { provide: SessionStore, useValue: session },
      ],
    });
    const service = TestBed.inject(PrivateApiRecovery);
    await expect(
      service.read(throwError(() => new HttpErrorResponse({ status: 401 }))),
    ).rejects.toBeDefined();
    expect(session.reset).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledExactlyOnceWith(['/entrar'], {
      queryParams: { returnTo: '/conta/grupos' },
    });
  });
  it('403 nao inicia login nem altera sessao', async () => {
    const router = { url: '/conta/grupos', navigate: vi.fn() };
    const session = { reset: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: router },
        { provide: SessionStore, useValue: session },
      ],
    });
    await expect(
      TestBed.inject(PrivateApiRecovery).read(
        throwError(() => new HttpErrorResponse({ status: 403 })),
      ),
    ).rejects.toBeDefined();
    expect(session.reset).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
  it('retorna leitura bem sucedida sem redirecionar', async () => {
    TestBed.configureTestingModule({
      providers: [
        { provide: Router, useValue: {} },
        { provide: SessionStore, useValue: {} },
      ],
    });
    expect(await TestBed.inject(PrivateApiRecovery).read(of({ items: [] }))).toEqual({ items: [] });
  });
});
