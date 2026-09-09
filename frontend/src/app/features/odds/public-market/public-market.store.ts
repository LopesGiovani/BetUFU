import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { PublicMarketApiAdapter, PublicMarketSnapshot } from './public-market-api.adapter';

export type PublicMarketStatus = 'initial' | 'loading' | 'populated' | 'empty' | 'stale' | 'error';

interface PublicMarketState {
  status: PublicMarketStatus;
  marketId: string | null;
  snapshot: PublicMarketSnapshot | null;
  errorMessage: string | null;
  isRetrying: boolean;
}

const INITIAL_STATE: PublicMarketState = Object.freeze({
  status: 'initial',
  marketId: null,
  snapshot: null,
  errorMessage: null,
  isRetrying: false,
});

@Injectable({ providedIn: 'root' })
export class PublicMarketStore {
  private readonly adapter = inject(PublicMarketApiAdapter);
  private readonly state = signal<PublicMarketState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly marketId = computed(() => this.state().marketId);
  readonly snapshot = computed(() => this.state().snapshot);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly isRetrying = computed(() => this.state().isRetrying);
  readonly isInitial = computed(() => this.status() === 'initial');
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isPopulated = computed(() => this.status() === 'populated');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isStale = computed(() => this.status() === 'stale');
  readonly isError = computed(() => this.status() === 'error');

  async loadMarket(marketId: string): Promise<void> {
    const requestId = ++this.requestSequence;
    const current = this.state();
    const normalizedId = marketId.trim();
    this.state.set({
      ...current,
      status: 'loading',
      marketId: normalizedId || null,
      errorMessage: null,
      isRetrying: false,
    });
    if (!normalizedId) {
      this.state.set({
        ...this.state(),
        status: 'empty',
        errorMessage: 'Mercado ainda nao publicado.',
      });
      return;
    }

    try {
      const snapshot = await firstValueFrom(this.adapter.fetchMarket(normalizedId));
      if (requestId !== this.requestSequence) return;
      this.state.set({
        status: 'populated',
        marketId: normalizedId,
        snapshot,
        errorMessage: null,
        isRetrying: false,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      const httpError = error instanceof HttpErrorResponse ? error : null;
      if (httpError?.status === 404) {
        this.state.set({
          status: 'empty',
          marketId: normalizedId,
          snapshot: null,
          errorMessage: 'Mercado ainda nao publicado.',
          isRetrying: false,
        });
        return;
      }
      const previous = this.state().snapshot;
      this.state.set({
        status: previous ? 'stale' : 'error',
        marketId: normalizedId,
        snapshot: previous,
        errorMessage: 'Nao foi possivel carregar as odds agora. Tente novamente ou volte ao evento.',
        isRetrying: false,
      });
    }
  }

  async retry(): Promise<void> {
    const marketId = this.marketId();
    if (marketId) {
      this.state.update((state) => ({ ...state, isRetrying: true }));
      await this.loadMarket(marketId);
    }
  }
}
