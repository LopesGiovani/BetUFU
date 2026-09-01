import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsTabularImportPreview } from '../../../../api/generated/model/sports-tabular-import-preview';
import { SportsTabularImportResult } from '../../../../api/generated/model/sports-tabular-import-result';
import { SportsTabularImportApiAdapter } from './sports-tabular-import-api.adapter';
import { SportsTabularImportStore } from './sports-tabular-import.store';

const PREVIEW: SportsTabularImportPreview = {
  importId: 'import-1',
  format: 'CSV',
  totalRows: 2,
  validRows: 2,
  errorCount: 0,
  errors: [],
  summary: [
    {
      organizationName: 'Organização',
      teamName: 'Equipe',
      sportName: 'Futsal',
      categoryName: 'Livre',
      athleteCount: 2,
    },
  ],
  readyToImport: true,
};

const RESULT: SportsTabularImportResult = {
  importId: 'import-1',
  status: 'CONCLUIDO',
  totalImportedAthletes: 2,
  totalImportedRosters: 1,
  message: 'Importação concluída como rascunho.',
};

describe('SportsTabularImportStore', () => {
  let adapter: {
    detectFormat: ReturnType<typeof vi.fn>;
    validate: ReturnType<typeof vi.fn>;
    commit: ReturnType<typeof vi.fn>;
    checkStatus: ReturnType<typeof vi.fn>;
    isUnauthorized: ReturnType<typeof vi.fn>;
  };
  let store: SportsTabularImportStore;

  beforeEach(() => {
    adapter = {
      detectFormat: vi.fn(() => 'CSV'),
      validate: vi.fn(() => of(PREVIEW)),
      commit: vi.fn(() => of(RESULT)),
      checkStatus: vi.fn(() => of(RESULT)),
      isUnauthorized: vi.fn(
        (error: unknown) =>
          error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403),
      ),
    };
    TestBed.configureTestingModule({
      providers: [
        SportsTabularImportStore,
        { provide: SportsTabularImportApiAdapter, useValue: adapter },
      ],
    });
    store = TestBed.inject(SportsTabularImportStore);
  });

  it('mantém arquivo selecionado sem disparar upload automático', () => {
    const file = new File(['organizacao_slug\n'], 'elenco.csv', { type: 'text/csv' });

    expect(store.selectFile(file)).toBe(true);
    expect(store.fileName()).toBe('elenco.csv');
    expect(store.format()).toBe('CSV');
    expect(adapter.validate).not.toHaveBeenCalled();
    expect(store.status()).toBe('selected');
  });

  it('valida e permite somente uma confirmação', async () => {
    store.selectFile(new File(['dados'], 'elenco.csv', { type: 'text/csv' }));
    await expect(store.validate('edition-1')).resolves.toBe(true);
    expect(store.preview()).toEqual(PREVIEW);
    expect(store.canCommit()).toBe(true);

    await expect(store.commit('edition-1')).resolves.toBe(true);
    await expect(store.commit('edition-1')).resolves.toBe(false);
    expect(adapter.commit).toHaveBeenCalledTimes(1);
    expect(store.status()).toBe('completed');
    expect(store.result()).toEqual(RESULT);
    expect(store.fileName()).toBeNull();
  });

  it('não reenvia commit quando a resposta fica indeterminada', async () => {
    store.selectFile(
      new File(['dados'], 'elenco.xlsx', {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }),
    );
    await store.validate('edition-1');
    adapter.commit.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));

    await expect(store.commit('edition-1')).resolves.toBe(false);
    expect(store.isIndeterminate()).toBe(true);
    expect(store.canCommit()).toBe(false);
    expect(adapter.commit).toHaveBeenCalledTimes(1);

    adapter.checkStatus.mockReturnValue(of(RESULT));
    await expect(store.checkStatus('edition-1')).resolves.toBe(true);
    expect(store.status()).toBe('completed');
    expect(adapter.commit).toHaveBeenCalledTimes(1);
    expect(adapter.checkStatus).toHaveBeenCalledTimes(1);
  });

  it('limpa arquivo, preview e identificador privado em 401/403', async () => {
    store.selectFile(new File(['dados'], 'elenco.csv', { type: 'text/csv' }));
    await store.validate('edition-1');
    adapter.isUnauthorized.mockReturnValue(true);
    adapter.commit.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    await expect(store.commit('edition-1')).resolves.toBe(false);
    expect(store.isDenied()).toBe(true);
    expect(store.fileName()).toBeNull();
    expect(store.preview()).toBeNull();
    expect(store.result()).toBeNull();
    expect(store.errorMessage()).not.toContain('import-1');
  });
});
