import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { SportsBracketView } from '../../../../api/generated/model/sports-bracket-view';
import { SportsBracketUpdateRequest } from '../../../../api/generated/model/sports-bracket-update-request';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';
import { SportsBracketApiAdapter } from './sports-bracket-api.adapter';

type SportsBracketStatus = 'initial' | 'loading' | 'populated' | 'not-found' | 'denied' | 'error';
interface SportsBracketState { status: SportsBracketStatus; bracketId: string | null; data: SportsBracketView | null; events: readonly SportsEventDetail[]; errorMessage: string | null; conflictMessage: string | null; feedbackMessage: string | null; isMutating: boolean; }
const INITIAL: SportsBracketState = { status: 'initial', bracketId: null, data: null, events: Object.freeze([]), errorMessage: null, conflictMessage: null, feedbackMessage: null, isMutating: false };

@Injectable({ providedIn: 'root' })
export class SportsBracketStore {
  private readonly adapter = inject(SportsBracketApiAdapter);
  private readonly state = signal<SportsBracketState>(INITIAL);
  private requestSequence = 0;
  readonly status = computed(() => this.state().status);
  readonly data = computed(() => this.state().data);
  readonly events = computed(() => this.state().events);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly conflictMessage = computed(() => this.state().conflictMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isNotFound = computed(() => this.status() === 'not-found');
  readonly isDenied = computed(() => this.status() === 'denied');
  readonly isError = computed(() => this.status() === 'error');

  async load(bracketId: string): Promise<void> {
    const id = bracketId.trim(); const requestId = ++this.requestSequence;
    this.state.set({ ...INITIAL, status: 'loading', bracketId: id || null });
    try {
      const data = await firstValueFrom(this.adapter.fetch(id).pipe(retry({ count: 2, delay: (error: unknown, attempt) => { if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403 || error.status === 404)) throw error; return timer(attempt === 1 ? 250 : 500); } })));
      if (requestId !== this.requestSequence) return;
      if (!data || data.bracketId !== id || !Array.isArray(data.rounds)) throw new Error('CONTRATO_BRACKET_INVALIDO');
       const events = Object.freeze(await firstValueFrom(this.adapter.fetchEvents(data.editionId)));
      if (requestId !== this.requestSequence) return;
      this.state.set({ ...INITIAL, status: 'populated', bracketId: id, data, events });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      const status = this.adapter.isUnauthorized(error) ? 'denied' : this.adapter.isNotFound(error) ? 'not-found' : 'error';
      this.state.set({ ...INITIAL, status, bracketId: id || null, errorMessage: status === 'denied' ? 'Você não tem acesso a este chaveamento operacional.' : status === 'not-found' ? 'Chaveamento não encontrado neste escopo.' : 'Não foi possível carregar chaveamento e classificação. Tente carregar chaveamento e classificação.' });
    }
  }
  async retry(): Promise<void> { const id = this.state().bracketId; if (id) await this.load(id); }

  async update(payload: SportsBracketUpdateRequest): Promise<boolean> {
    const current = this.state();
    if (!current.data) return false;
    const bracket = current.data;
    return this.mutate(async () => {
      const data = await firstValueFrom(this.adapter.update(bracket.bracketId, bracket.lockVersion, payload));
      this.state.update((state) => ({ ...state, data, conflictMessage: null }));
    }, 'Chaveamento salvo como rascunho.');
  }

  async publish(sourceReference: string): Promise<boolean> {
    const current = this.state();
    if (!current.data || !sourceReference.trim()) return false;
    const bracket = current.data;
    return this.mutate(async () => {
      const data = await firstValueFrom(this.adapter.publish(bracket.bracketId, bracket.lockVersion, sourceReference.trim()));
      this.state.update((state) => ({ ...state, data, conflictMessage: null }));
    }, 'Chaveamento publicado com fonte oficial.');
  }

  async correct(reason: string, sourceReference: string): Promise<boolean> {
    const current = this.state();
    if (!current.data || !reason.trim() || !sourceReference.trim()) return false;
    const bracket = current.data;
    return this.mutate(async () => {
      const data = await firstValueFrom(this.adapter.correct(bracket.bracketId, bracket.lockVersion, reason.trim(), sourceReference.trim()));
      this.state.update((state) => ({ ...state, data, conflictMessage: null }));
    }, 'Correção publicada como nova versão auditada.');
  }

  private async mutate(action: () => Promise<void>, successMessage: string): Promise<boolean> {
    this.state.update((state) => ({ ...state, isMutating: true, errorMessage: null, conflictMessage: null, feedbackMessage: null }));
    try { await action(); this.state.update((state) => ({ ...state, isMutating: false, feedbackMessage: successMessage })); return true; }
    catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) this.state.update((state) => ({ ...INITIAL, status: 'denied', bracketId: state.bracketId, errorMessage: 'Seu acesso a este chaveamento foi encerrado.' }));
      else if (error instanceof HttpErrorResponse && error.status === 409) this.state.update((state) => ({ ...state, isMutating: false, conflictMessage: 'Este chaveamento foi alterado por outra pessoa. Recarregue para comparar a versão atual antes de tentar novamente.' }));
      else this.state.update((state) => ({ ...state, isMutating: false, errorMessage: 'Não foi possível salvar esta alteração agora. Tente novamente.' }));
      return false;
    }
  }
}
