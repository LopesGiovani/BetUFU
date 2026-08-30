import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AthleteLinkProposal } from '../../../api/generated/model/athlete-link-proposal';
import { AthleteLinkProposalApiAdapter } from './athlete-link-proposal-api.adapter';
import { AthleteLinkProposalStore } from './athlete-link-proposal.store';

const proposal: AthleteLinkProposal = {
  proposalId: 'proposal-1', athleteId: 'athlete-1', athleteName: 'Atleta Um', athleteSlug: 'atleta-um',
  status: AthleteLinkProposal.StatusEnum.Pendente, createdAt: '2026-08-30T10:00:00Z', updatedAt: '2026-08-30T10:00:00Z', expiresAt: '2026-09-30T10:00:00Z', decidedAt: null,
};

type ProposalAdapterMock = {
  fetchProposal: ReturnType<typeof vi.fn>;
  decide: ReturnType<typeof vi.fn>;
  isNotFound: ReturnType<typeof vi.fn>;
  isUnauthorized: ReturnType<typeof vi.fn>;
  isConflict: ReturnType<typeof vi.fn>;
};

describe('AthleteLinkProposalStore', () => {
  let adapter: ProposalAdapterMock;

  beforeEach(() => {
    adapter = {
      fetchProposal: vi.fn(() => of(proposal)),
      decide: vi.fn(() => of({ ...proposal, status: AthleteLinkProposal.StatusEnum.Contestada })),
      isNotFound: vi.fn((error: unknown) => error instanceof HttpErrorResponse && error.status === 404),
      isUnauthorized: vi.fn((error: unknown) => error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)),
      isConflict: vi.fn((error: unknown) => error instanceof HttpErrorResponse && error.status === 409),
    };
    TestBed.configureTestingModule({ providers: [AthleteLinkProposalStore, { provide: AthleteLinkProposalApiAdapter, useValue: adapter }] });
  });

  it('carrega proposta e envia contestação sem retry na mutação', async () => {
    const store = TestBed.inject(AthleteLinkProposalStore);
    await store.load();
    expect(store.proposal()?.athleteName).toBe('Atleta Um');
    await expect(store.contest()).resolves.toBe(true);
    expect(adapter.decide).toHaveBeenCalledWith('proposal-1', 'CONTEST');
    expect(adapter.decide).toHaveBeenCalledTimes(1);
    expect(store.proposal()?.status).toBe('CONTESTADA');
  });

  it('trata 404 como vazio e 401 limpa a proposta privada sem repetir a chamada', async () => {
    adapter.fetchProposal.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 404 })));
    const store = TestBed.inject(AthleteLinkProposalStore);
    await store.load();
    expect(store.isEmpty()).toBe(true);

    adapter.fetchProposal.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    await store.load();
    expect(store.proposal()).toBeNull();
    expect(store.errorMessage()).toContain('sessão expirou');
    expect(adapter.fetchProposal).toHaveBeenCalledTimes(2);
  });

  it('preserva proposta em conflito para comparação e não repete PUT', async () => {
    const store = TestBed.inject(AthleteLinkProposalStore);
    await store.load();
    adapter.decide.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    await expect(store.accept()).resolves.toBe(false);
    expect(store.proposal()?.proposalId).toBe('proposal-1');
    expect(store.errorMessage()).toContain('alterado por outra pessoa');
    expect(adapter.decide).toHaveBeenCalledTimes(1);
  });
});
