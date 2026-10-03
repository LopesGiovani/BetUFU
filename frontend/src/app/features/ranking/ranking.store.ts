import { Injectable, inject, signal } from '@angular/core';
import { RankingApiAdapter } from './ranking-api.adapter';
@Injectable()
export class RankingStore {
  private readonly api = inject(RankingApiAdapter);
  readonly entries = signal<Awaited<ReturnType<RankingApiAdapter['read']>>>([]);
  readonly loading = signal(false);
  readonly error = signal(false);
  async load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(false);
    try {
      this.entries.set(await this.api.read());
    } catch {
      this.entries.set([]);
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
