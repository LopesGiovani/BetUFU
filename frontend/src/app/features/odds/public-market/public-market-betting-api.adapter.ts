import { Injectable } from '@angular/core';

export interface BettingBalance { balance: number; scale: number; minimumStake: number; rulesVersion: string; }
export interface BettingTicket { predictionId: string; selection: string; stake: number; acceptedOdds: number; potentialReturn: number; netProfit: number; ruleVersion: string; closeDeadline: string; state: string; acceptedAt: string; }
export interface BettingSubmission { marketId: string; marketVersionId: string; publicationId: string; ruleVersion: string; selection: string; stake: number; }

@Injectable({ providedIn: 'root' })
export class PublicMarketBettingApiAdapter {
  async getBalance(): Promise<BettingBalance> { throw new Error('BETTING_API_UNAVAILABLE'); }
  async getOwnTicket(marketId: string): Promise<BettingTicket | null> { void marketId; return null; }
  async submit(request: BettingSubmission, idempotencyKey: string): Promise<BettingTicket> { void request; void idempotencyKey; throw new Error('BETTING_API_UNAVAILABLE'); }
}
