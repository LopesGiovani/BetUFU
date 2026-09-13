import { ChangeDetectionStrategy, Component, Input, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { PublicMarketBettingStore } from './public-market-betting.store';

@Component({ selector: 'app-public-market-bet-ticket', standalone: true, imports: [FormsModule, DecimalPipe], templateUrl: './public-market-bet-ticket.component.html', styleUrl: './public-market-bet-ticket.component.scss', changeDetection: ChangeDetectionStrategy.OnPush })
export class PublicMarketBetTicketComponent implements OnInit {
  readonly store = inject(PublicMarketBettingStore);
  @Input({ required: true }) marketId = '';
  @Input({ required: true }) marketVersionId = '';
  @Input({ required: true }) publicationId = '';
  @Input({ required: true }) ruleVersion = 'odds-v1';
  @Input({ required: true }) selections: readonly { label: string; odd: number }[] = [];
  readonly selection = signal('');
  readonly stake = signal<number | null>(null);
  readonly confirmed = signal(false);
  configure(): void { this.store.configure(this.marketId, { marketVersionId: this.marketVersionId, publicationId: this.publicationId, ruleVersion: this.ruleVersion, odds: Object.fromEntries(this.selections.map((item) => [item.label, item.odd])) }); }
  ngOnInit(): void { this.configure(); void this.store.load(); }
  useAllIn(): void { const balance = this.store.balanceValue(); if (balance) this.stake.set(balance.balance); }
  potentialReturn(): number { const odd = this.selections.find((item) => item.label === this.selection())?.odd ?? 0; return Number(((this.stake() ?? 0) * odd).toFixed(2)); }
  netProfit(): number { return Number((this.potentialReturn() - (this.stake() ?? 0)).toFixed(2)); }
  async submit(): Promise<void> { const value = this.stake(); if (value !== null) await this.store.submit(this.selection(), value, this.confirmed()); }
}
