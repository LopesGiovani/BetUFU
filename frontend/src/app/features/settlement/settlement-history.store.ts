import { Injectable, inject, signal } from '@angular/core';
import { SettlementHistoryApiAdapter } from './settlement-history-api.adapter';
@Injectable()
export class SettlementHistoryStore {
  private readonly api = inject(SettlementHistoryApiAdapter);
  readonly data = signal<Awaited<ReturnType<SettlementHistoryApiAdapter['read']>>>({
    items: [],
    corrections: [],
  });
  readonly loading = signal(false);
  readonly error = signal(false);
  async load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(false);
    try {
      this.data.set(await this.api.read());
    } catch {
      this.data.set({ items: [], corrections: [] });
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
