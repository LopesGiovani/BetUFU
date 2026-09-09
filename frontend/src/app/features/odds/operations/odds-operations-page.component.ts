import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  WritableSignal,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OddsMarketLifecycleRequest } from '../../../api/generated/model/odds-market-lifecycle-request';
import { OddsOverrideProposalRequest } from '../../../api/generated/model/odds-override-proposal-request';
import { OddsOverrideApprovalRequest } from '../../../api/generated/model/odds-override-approval-request';
import { OddsOperationsStore } from './odds-operations.store';
import { OperationsApiAdapter } from '../../operations/operations-api.adapter';
import { OperationalCapabilityName } from '../../operations/operations.models';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-odds-operations-page',
  standalone: true,
  imports: [RouterLink, DecimalPipe],
  templateUrl: './odds-operations-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      :host {
        display: block;
        min-height: 100%;
        background: #f7f5f1;
        color: #171717;
      }
      main {
        max-width: 1100px;
        margin: 0 auto;
        padding: 32px 16px 64px;
      }
      .toolbar {
        display: flex;
        justify-content: space-between;
        gap: 16px;
        align-items: flex-start;
        flex-wrap: wrap;
      }
      h1 {
        margin: 0;
        font-size: clamp(1.7rem, 4vw, 2.6rem);
        letter-spacing: -0.03em;
      }
      h2 {
        margin: 0 0 12px;
        font-size: 1.2rem;
      }
      .eyebrow {
        text-transform: uppercase;
        letter-spacing: 0.12em;
        font-size: 0.72rem;
        color: #635f59;
      }
      .panel {
        background: #fff;
        border: 1px solid #ded9d1;
        border-radius: 18px;
        padding: 20px;
        margin-top: 20px;
        box-shadow: 0 8px 28px #1717170c;
      }
      .state {
        border-left: 4px solid #bd3f3f;
        background: #fff7f5;
      }
      .state--ok {
        border-left-color: #287a56;
        background: #f1fbf5;
      }
      .state--warn {
        border-left-color: #b87918;
        background: #fff9ed;
      }
      .facts {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 14px;
        margin-top: 16px;
      }
      .facts div {
        padding: 12px;
        background: #faf9f7;
        border-radius: 10px;
      }
      dt {
        font-size: 0.76rem;
        color: #635f59;
      }
      dd {
        margin: 4px 0 0;
        font-weight: 650;
        overflow-wrap: anywhere;
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 10px;
        margin-top: 18px;
      }
      button,
      a.action {
        border: 1px solid #252525;
        border-radius: 10px;
        padding: 10px 14px;
        font: inherit;
        cursor: pointer;
        text-decoration: none;
      }
      button.primary,
      a.action.primary {
        background: #171717;
        color: #fff;
      }
      button:disabled {
        opacity: 0.45;
        cursor: not-allowed;
      }
      label {
        display: grid;
        gap: 6px;
        margin-top: 12px;
        font-size: 0.9rem;
      }
      input {
        min-height: 42px;
        border: 1px solid #bdb7ae;
        border-radius: 8px;
        padding: 8px 10px;
        font: inherit;
      }
      .compare {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 12px;
        margin-top: 14px;
      }
      .compare strong {
        display: block;
        font-size: 1.4rem;
      }
      .muted {
        color: #635f59;
      }
      @media (max-width: 600px) {
        main {
          padding: 24px 16px 48px;
        }
        .compare {
          grid-template-columns: 1fr;
        }
        .actions > * {
          width: 100%;
        }
      }
    `,
  ],
})
export class OddsOperationsPageComponent implements OnInit {
  readonly store = inject(OddsOperationsStore);
  private readonly operationsApi = inject(OperationsApiAdapter);
  private readonly route = inject(ActivatedRoute);
  readonly correctionMode = signal(false);
  readonly correctionCalculationId = signal('');
  readonly evidenceReference = signal('');
  readonly reason = signal('');
  readonly proposedOdds = signal('');
  readonly modelOdds = signal('');
  readonly approvalReason = signal('');
  readonly capabilities = signal<readonly OperationalCapabilityName[]>([]);
  readonly localProposal = signal(false);

  ngOnInit(): void {
    const marketId = this.route.snapshot.paramMap.get('marketId') ?? '';
    void this.store.loadMarket(marketId);
    void this.loadCapabilities();
  }

  startCorrection(): void {
    this.correctionMode.set(true);
  }

  suspendMarket(): void {
    if (!this.capabilities().includes('MANAGE_ODDS_MARKET')) return;
    void this.store.transition(
      OddsMarketLifecycleRequest.ActionEnum.SuspendMarket,
      this.reason().trim() || undefined,
    );
  }

  submitProposal(): void {
    const snapshot = this.store.snapshot();
    const previous = Number(this.modelOdds());
    const proposed = Number(this.proposedOdds());
    if (
      !this.capabilities().includes('PROPOSE_ODDS_OVERRIDE') ||
      !snapshot ||
      !this.correctionCalculationId().trim() ||
      !this.evidenceReference().trim() ||
      !this.reason().trim() ||
      !Number.isFinite(previous) ||
      previous <= 1 ||
      !Number.isFinite(proposed) ||
      proposed <= 1
    )
      return;
    const request: OddsOverrideProposalRequest = {
      correctionCalculationId: this.correctionCalculationId().trim(),
      previousOdds: previous,
      proposedOdds: proposed,
      evidenceReference: this.evidenceReference().trim(),
      reason: this.reason().trim(),
      expectedLockVersion: snapshot.lockVersion,
    };
    this.localProposal.set(true);
    void this.store.propose(request, this.idempotencyKey());
  }

  approveProposal(): void {
    const snapshot = this.store.snapshot();
    if (
      !this.capabilities().includes('APPROVE_ODDS_OVERRIDE') ||
      this.localProposal() ||
      !snapshot ||
      !this.store.proposal()
    )
      return;
    const request: OddsOverrideApprovalRequest = {
      expectedLockVersion: snapshot.lockVersion,
      reason: this.approvalReason().trim() || 'Aprovacao por segundo administrador do mesmo escopo',
    };
    void this.store.approve(request, this.idempotencyKey());
  }

  async refresh(): Promise<void> {
    const id = this.store.marketId();
    if (id) await this.store.loadMarket(id);
  }

  set(signalValue: WritableSignal<string>, event: Event): void {
    signalValue.set((event.target as HTMLInputElement).value);
  }

  private idempotencyKey(): string {
    const cryptoApi = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto : null;
    return cryptoApi
      ? cryptoApi.randomUUID()
      : `odds-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  private async loadCapabilities(): Promise<void> {
    try {
      this.capabilities.set(
        await firstValueFrom(this.operationsApi.fetchOperationalCapabilities()),
      );
    } catch {
      this.capabilities.set([]);
    }
  }
}
