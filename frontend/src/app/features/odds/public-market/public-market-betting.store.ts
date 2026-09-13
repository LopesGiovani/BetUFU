import { Injectable, computed, inject, signal } from '@angular/core';
import { BettingBalance, BettingTicket, PublicMarketBettingApiAdapter } from './public-market-betting-api.adapter';

export type BettingState = 'visitor' | 'no-enrollment' | 'loading' | 'eligible' | 'insufficient' | 'blocked' | 'stale' | 'success' | 'error';

@Injectable({ providedIn: 'root' })
export class PublicMarketBettingStore {
  private readonly api = inject(PublicMarketBettingApiAdapter);
  private readonly state = signal<BettingState>('visitor');
  private readonly balance = signal<BettingBalance | null>(null);
  private readonly ticket = signal<BettingTicket | null>(null);
  private readonly message = signal<string | null>(null);
  private marketId = '';
  private snapshot: { marketVersionId: string; publicationId: string; ruleVersion: string; odds: Record<string, number> } | null = null;
  readonly status = computed(() => this.state());
  readonly balanceValue = computed(() => this.balance());
  readonly ownTicket = computed(() => this.ticket());
  readonly errorMessage = computed(() => this.message());
  readonly canSubmit = computed(() => this.state() === 'eligible' && this.balance() !== null);

  configure(marketId: string, snapshot: { marketVersionId: string; publicationId: string; ruleVersion: string; odds: Record<string, number> }): void { this.marketId = marketId; this.snapshot = snapshot; }
  setVisitor(): void { this.state.set('visitor'); }
  setNoEnrollment(): void { this.state.set('no-enrollment'); }
  setBlocked(): void { this.state.set('blocked'); }
  async load(): Promise<void> {
    this.state.set('loading'); this.message.set(null);
    try { const [balance, ticket] = await Promise.all([this.api.getBalance(), this.api.getOwnTicket(this.marketId)]); this.balance.set(balance); this.ticket.set(ticket); this.state.set(ticket ? 'success' : balance.balance < balance.minimumStake ? 'insufficient' : 'eligible'); }
    catch { this.state.set('error'); this.message.set('Nao foi possivel carregar os creditos agora.'); }
  }
  async submit(selection: string, stake: number, confirmed: boolean): Promise<void> {
    if (!this.snapshot || !this.canSubmit() || !confirmed) return;
    this.state.set('loading');
    const key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
    try { const ticket = await this.api.submit({ marketId: this.marketId, marketVersionId: this.snapshot.marketVersionId, publicationId: this.snapshot.publicationId, ruleVersion: this.snapshot.ruleVersion, selection, stake }, key); this.ticket.set(ticket); this.state.set('success'); }
    catch (error: unknown) { this.state.set((error as { status?: number })?.status === 409 ? 'stale' : 'error'); this.message.set((error as { status?: number })?.status === 409 ? 'A odd mudou antes do envio. Confira e confirme novamente.' : 'Nao foi possivel enviar o palpite agora. Nenhuma alteracao foi confirmada.'); }
  }
}
