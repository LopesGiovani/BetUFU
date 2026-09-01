import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AthleteLinkProposal } from '../../../api/generated/model/athlete-link-proposal';
import { AthleteLinkProposalStore } from './athlete-link-proposal.store';
import { AthleteLinkProposalPageComponent } from './athlete-link-proposal-page.component';

const proposal: AthleteLinkProposal = {
  proposalId: 'proposal-1',
  athleteId: 'athlete-1',
  athleteName: 'Atleta Um',
  athleteSlug: 'atleta-um',
  status: AthleteLinkProposal.StatusEnum.Pendente,
  createdAt: '2026-08-30T10:00:00Z',
  updatedAt: '2026-08-30T10:00:00Z',
  expiresAt: '2026-09-30T10:00:00Z',
  decidedAt: null,
};

describe('AthleteLinkProposalPageComponent', () => {
  let fixture: ComponentFixture<AthleteLinkProposalPageComponent>;
  let store: Record<string, unknown>;

  beforeEach(async () => {
    store = {
      proposal: signal(proposal),
      status: signal('ready'),
      errorMessage: signal<string | null>(null),
      feedbackMessage: signal<string | null>(null),
      isLoading: signal(false),
      isEmpty: signal(false),
      isError: signal(false),
      isMutating: signal(false),
      load: vi.fn().mockResolvedValue(undefined),
      accept: vi.fn().mockResolvedValue(true),
      reject: vi.fn().mockResolvedValue(true),
      contest: vi.fn().mockResolvedValue(true),
    };
    await TestBed.configureTestingModule({
      imports: [AthleteLinkProposalPageComponent],
      providers: [{ provide: AthleteLinkProposalStore, useValue: store }],
    }).compileComponents();
    fixture = TestBed.createComponent(AthleteLinkProposalPageComponent);
    fixture.detectChanges();
  });

  it('exibe apenas o nome esportivo, aviso de não oficialidade e ações owner-only', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Atleta Um');
    expect(root.textContent).toContain('não comprova vínculo oficial');
    expect(root.textContent).not.toMatch(/matr[ií]cula|hmac|@/iu);
    expect(root.querySelectorAll('button').length).toBe(3);
  });

  it('confirma contestação antes de chamar o store', async () => {
    const root = fixture.nativeElement as HTMLElement;
    const contestButton = Array.from(root.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Contestar'),
    ) as HTMLButtonElement;
    contestButton.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Contestar vínculo');
    expect(store['contest']).not.toHaveBeenCalled();
    const confirm = Array.from(root.querySelectorAll('button')).find((button) =>
      button.textContent?.includes('Enviar contestação'),
    ) as HTMLButtonElement;
    confirm.click();
    await fixture.whenStable();
    expect(store['contest']).toHaveBeenCalledTimes(1);
  });
});
