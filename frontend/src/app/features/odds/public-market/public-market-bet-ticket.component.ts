import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  Input,
  OnChanges,
  OnInit,
  SimpleChanges,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { PublicMarketSelection } from './public-market-api.adapter';
import { BettingSelection } from './public-market-betting-api.adapter';
import { PublicMarketBettingStore } from './public-market-betting.store';

@Component({
  selector: 'app-public-market-bet-ticket',
  standalone: true,
  imports: [FormsModule, DecimalPipe, RouterLink],
  templateUrl: './public-market-bet-ticket.component.html',
  styleUrl: './public-market-bet-ticket.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicMarketBetTicketComponent implements OnInit, OnChanges {
  readonly store = inject(PublicMarketBettingStore);

  @Input({ required: true }) marketId = '';
  @Input({ required: true }) marketVersionId = '';
  @Input({ required: true }) publicationId = '';
  @Input({ required: true }) ruleVersion = '';
  @Input({ required: true }) closeDeadline = '';
  @Input({ required: true }) marketState: OddsMarketState = OddsMarketState.Open;
  @Input({ required: true }) selections: readonly PublicMarketSelection[] = [];

  readonly selection = signal<BettingSelection | null>(null);
  readonly stake = signal<number | null>(null);
  readonly confirmed = signal(false);

  readonly stakeError = computed(() => {
    const value = this.stake();
    const balance = this.store.balanceValue();
    if (value === null || !Number.isFinite(value)) return null;
    if (!balance) return 'Saldo indisponivel para validar o valor.';
    if (Number(value.toFixed(2)) !== value) {
      return 'Use no maximo duas casas decimais.';
    }
    if (value < balance.minimumStake) {
      return `O minimo e ${balance.minimumStake.toFixed(2)} creditos.`;
    }
    if (value > balance.balance) return 'O valor nao pode ultrapassar o saldo disponivel.';
    return null;
  });

  readonly canSubmit = computed(
    () =>
      this.store.canSubmit() &&
      this.selection() !== null &&
      this.stake() !== null &&
      this.stakeError() === null &&
      (!this.store.requiresReconfirmation() || this.confirmed()),
  );

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['marketId'] || changes['marketVersionId'] || changes['publicationId']) {
      this.confirmed.set(false);
    }
    this.configure();
  }

  ngOnInit(): void {
    this.configure();
    if (this.store.shouldLoad(this.marketId)) void this.store.load();
  }

  configure(): void {
    this.store.configure(this.marketId, {
      marketVersionId: this.marketVersionId,
      publicationId: this.publicationId,
      ruleVersion: this.ruleVersion,
      closeDeadline: this.closeDeadline,
      state: this.marketState,
      selections: this.selections,
    });
  }

  useAllIn(): void {
    const balance = this.store.balanceValue();
    if (balance) this.stake.set(balance.balance);
  }

  chooseSelection(selection: BettingSelection): void {
    this.selection.set(selection);
    if (this.store.requiresReconfirmation()) this.confirmed.set(false);
  }

  updateStake(value: number): void {
    this.stake.set(Number.isFinite(value) ? value : null);
  }

  potentialReturn(): number {
    const odd = this.store.selections().find((item) => item.code === this.selection())?.odd ?? 0;
    return Number(((this.stake() ?? 0) * odd).toFixed(2));
  }

  netProfit(): number {
    return Number((this.potentialReturn() - (this.stake() ?? 0)).toFixed(2));
  }

  selectionLabel(selection: BettingSelection): string {
    return { HOME: 'Mandante', DRAW: 'Empate', AWAY: 'Visitante' }[selection];
  }

  async submit(): Promise<void> {
    const value = this.stake();
    const selection = this.selection();
    if (value === null || selection === null) return;
    const outcome = await this.store.submit(selection, value, this.confirmed());
    if (outcome === 'stale') this.confirmed.set(false);
  }
}
