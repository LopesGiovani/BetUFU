import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ChatModerationApiAdapter } from './chat-moderation-api.adapter';
import type { ChatModerationCase } from '../../api/generated/model/chat-moderation-case';
import type { ChatModerationEvidence } from '../../api/generated/model/chat-moderation-evidence';
import type { ChatSanctionSummary } from '../../api/generated/model/chat-sanction-summary';
import type { ChatSanctionSuggestion } from '../../api/generated/model/chat-sanction-suggestion';
import type { ChatReviewReceipt } from '../../api/generated/model/chat-review-receipt';

export type ModerationAction =
  'forward' | 'remove' | 'evidence' | 'sanction' | 'request-review' | 'maintain' | 'annul';

@Injectable()
export class ChatModerationStore {
  private readonly api = inject(ChatModerationApiAdapter);
  private epoch = 0;
  private own = false;
  private command: {
    action: ModerationAction;
    id: string;
    key: string;
    reason?: string;
    kind?: ChatSanctionSuggestion.SuggestedKindEnum;
  } | null = null;
  readonly cases = signal<readonly ChatModerationCase[]>([]);
  readonly reviews = signal<readonly ChatReviewReceipt[]>([]);
  readonly sanctions = signal<readonly ChatSanctionSummary[]>([]);
  readonly admin = signal(false);
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly reason = signal('');
  readonly action = signal<ModerationAction | null>(null);
  readonly suggestedKind = signal<ChatSanctionSuggestion.SuggestedKindEnum | null>(null);
  readonly evidence = signal<ChatModerationEvidence | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clear());
  }

  clear() {
    this.epoch++;
    this.cases.set([]);
    this.reviews.set([]);
    this.sanctions.set([]);
    this.admin.set(false);
    this.loading.set(false);
    this.sending.set(false);
    this.error.set(null);
    this.notice.set(null);
    this.cancel();
    this.evidence.set(null);
  }

  async loadModeration() {
    this.own = false;
    const epoch = ++this.epoch;
    this.loading.set(true);
    this.error.set(null);
    this.admin.set(false);
    this.evidence.set(null);
    try {
      const cases = await firstValueFrom(this.api.cases());
      let reviews: ChatReviewReceipt[] = [];
      let admin = false;
      try {
        reviews = await firstValueFrom(this.api.reviews());
        admin = true;
      } catch (e) {
        if (!(e instanceof HttpErrorResponse && e.status === 403)) throw e;
      }
      if (epoch !== this.epoch) return;
      this.cases.set(cases);
      this.reviews.set(reviews);
      this.admin.set(admin);
    } catch (e) {
      if (epoch === this.epoch) this.fail(e);
    } finally {
      if (epoch === this.epoch) this.loading.set(false);
    }
  }

  async loadSanctions() {
    this.own = true;
    const epoch = ++this.epoch;
    this.loading.set(true);
    this.error.set(null);
    try {
      const items = await firstValueFrom(this.api.sanctions());
      if (epoch === this.epoch) this.sanctions.set(items);
    } catch (e) {
      if (epoch === this.epoch) this.fail(e);
    } finally {
      if (epoch === this.epoch) this.loading.set(false);
    }
  }

  prepare(action: ModerationAction, id: string) {
    if (this.sending()) return;
    this.command = { action, id, key: crypto.randomUUID() };
    this.action.set(action);
    this.reason.set('');
    this.error.set(null);
    this.notice.set(null);
    this.evidence.set(null);
  }

  cancel() {
    if (this.sending()) return;
    this.command = null;
    this.action.set(null);
    this.reason.set('');
    this.suggestedKind.set(null);
  }

  async propose(id: string) {
    if (!this.admin() || this.sending()) return;
    const epoch = this.epoch;
    this.sending.set(true);
    try {
      const suggestion = await firstValueFrom(this.api.suggestion(id));
      if (epoch !== this.epoch) return;
      this.sending.set(false);
      this.prepare('sanction', id);
      this.command!.kind = suggestion.suggestedKind;
      this.suggestedKind.set(suggestion.suggestedKind);
    } catch (e) {
      if (epoch === this.epoch) this.fail(e);
    } finally {
      if (epoch === this.epoch) this.sending.set(false);
    }
  }

  async submit() {
    const c = this.command;
    if (!c || this.sending()) return;
    const reason = this.reason().trim();
    if (!reason || reason.length > 280) {
      this.error.set('Informe um motivo de 1 a 280 caracteres.');
      return;
    }
    // Once sent, retries preserve the exact body and command identifier.
    if (c.reason && c.reason !== reason) {
      this.error.set('Cancele para iniciar uma nova decisao com outro motivo.');
      return;
    }
    c.reason = reason;
    const epoch = this.epoch;
    this.sending.set(true);
    this.error.set(null);
    try {
      switch (c.action) {
        case 'evidence': {
          const value = await firstValueFrom(this.api.evidence(c.id, reason));
          if (epoch === this.epoch) this.evidence.set(value);
          break;
        }
        case 'forward':
          await firstValueFrom(this.api.forward(c.id, reason, c.key));
          break;
        case 'remove':
          await firstValueFrom(this.api.remove(c.id, reason, c.key));
          break;
        case 'sanction':
          if (!c.kind || !this.admin()) throw new Error('INVALID_SANCTION');
          await firstValueFrom(this.api.sanction(c.id, reason, c.key, c.kind));
          break;
        case 'request-review':
          await firstValueFrom(this.api.requestReview(c.id, reason, c.key));
          break;
        case 'maintain':
        case 'annul':
          if (!this.admin()) throw new Error('INVALID_REVIEW');
          await firstValueFrom(
            this.api.decide(c.id, reason, c.key, c.action === 'maintain' ? 'MAINTAIN' : 'ANNUL'),
          );
          break;
      }
      if (epoch !== this.epoch) return;
      this.sending.set(false);
      this.cancel();
      this.notice.set('Operacao registrada pelo servidor.');
      if (c.action !== 'evidence') {
        if (this.own) await this.loadSanctions();
        else await this.loadModeration();
      }
    } catch (e) {
      if (epoch === this.epoch) this.fail(e);
    } finally {
      if (epoch === this.epoch) this.sending.set(false);
    }
  }

  private fail(e: unknown) {
    if (e instanceof HttpErrorResponse && (e.status === 401 || e.status === 403)) {
      this.clear();
      this.error.set('Sua sessao ou permissao mudou. Entre novamente.');
    } else
      this.error.set(
        'Nao foi possivel concluir. Tente novamente; nenhuma confirmacao local substitui o servidor.',
      );
  }
}
