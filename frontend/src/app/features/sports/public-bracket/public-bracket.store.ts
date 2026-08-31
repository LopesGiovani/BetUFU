import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { SportsBracketView } from '../../../api/generated/model/sports-bracket-view';
import { PublicBracketApiAdapter } from './public-bracket-api.adapter';

type PublicBracketStatus = 'initial' | 'loading' | 'populated' | 'not-found' | 'error';
interface PublicBracketState { status: PublicBracketStatus; bracketId: string | null; data: SportsBracketView | null; errorMessage: string | null; }
const INITIAL: PublicBracketState = { status: 'initial', bracketId: null, data: null, errorMessage: null };

@Injectable({ providedIn: 'root' })
export class PublicBracketStore {
  private readonly adapter = inject(PublicBracketApiAdapter);
  private readonly state = signal<PublicBracketState>(INITIAL);
  private requestSequence = 0;
  readonly status = computed(() => this.state().status);
  readonly data = computed(() => this.state().data);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isNotFound = computed(() => this.status() === 'not-found');
  readonly isError = computed(() => this.status() === 'error');

  async load(bracketId: string): Promise<void> {
    const id = bracketId.trim();
    const requestId = ++this.requestSequence;
    this.state.set({ status: 'loading', bracketId: id || null, data: null, errorMessage: null });
    try {
      const data = await firstValueFrom(this.adapter.fetch(id).pipe(retry({ count: 2, delay: (error: unknown, attempt) => { if (error instanceof HttpErrorResponse && error.status === 404) throw error; return timer(attempt === 1 ? 250 : 500); } })));
      if (requestId !== this.requestSequence) return;
      if (!data || data.bracketId !== id || !Array.isArray(data.rounds)) throw new Error('CONTRATO_BRACKET_INVALIDO');
      this.state.set({ status: 'populated', bracketId: id, data, errorMessage: null });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      this.state.set({ status: this.adapter.isNotFound(error) ? 'not-found' : 'error', bracketId: id || null, data: null, errorMessage: this.adapter.isNotFound(error) ? 'Este chaveamento ainda não possui publicação pública.' : 'Não foi possível carregar o chaveamento público. Tente novamente.' });
    }
  }

  async retry(): Promise<void> { const id = this.state().bracketId; if (id) await this.load(id); }
}
