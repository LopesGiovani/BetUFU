import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { Subject, firstValueFrom, takeUntil } from 'rxjs';
import { SessionStore } from '../../core/session/session.store';
import { ChatWriteApiAdapter, ChatWriteEligibility } from './chat-write-api.adapter';

export function validChatText(text: string): boolean {
  return (
    text.trim().length > 0 &&
    [...text].length <= 280 &&
    !/[\u0000-\u001f\u007f-\u009f]/u.test(text) &&
    !/[\ud800-\udfff]/u.test(text)
  );
}
export interface ChatOutboxItem {
  readonly key: string;
  readonly text: string;
  readonly status: 'pending' | 'sending' | 'sent';
  readonly attempts: number;
}

@Injectable()
export class ChatOutboxStore {
  private readonly api = inject(ChatWriteApiAdapter);
  private readonly session = inject(SessionStore);
  private readonly queue = signal<readonly ChatOutboxItem[]>([]);
  private readonly ready = signal(false);
  private readonly note = signal<string | null>(null);
  private readonly deliveryRevision = signal(0);
  private readonly cancel = new Subject<void>();
  private target: { eventId: string | null; owner: string; epoch: number } | null = null;
  private generation = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private busy = false;
  private nextAt = 0;
  private initialized = false;

  readonly items = this.queue.asReadonly();
  readonly notice = this.note.asReadonly();
  readonly acknowledgements = this.deliveryRevision.asReadonly();
  readonly canCompose = computed(
    () =>
      this.ready() &&
      this.session.isAuthenticated() &&
      this.queue().filter((i) => i.status !== 'sent').length < 20,
  );

  constructor() {
    effect(() => {
      const authenticated = this.session.isAuthenticated();
      const epoch = this.session.revision();
      if (!authenticated || (this.target && this.target.epoch !== epoch)) this.close();
    });
    window.addEventListener('online', this.online);
    window.addEventListener('pagehide', this.pagehide);
    inject(DestroyRef).onDestroy(() => {
      this.close();
      this.cancel.complete();
      window.removeEventListener('online', this.online);
      window.removeEventListener('pagehide', this.pagehide);
    });
  }

  open(eventId: string | null): void {
    if (
      this.initialized &&
      this.target?.eventId === eventId &&
      this.target.epoch === this.session.revision()
    )
      return;
    this.close();
    if (!this.session.isAuthenticated()) return;
    this.initialized = true;
    const generation = this.generation;
    const epoch = this.session.revision();
    void firstValueFrom(this.api.eligibility(eventId).pipe(takeUntil(this.cancel)))
      .then((eligibility) => {
        if (
          generation !== this.generation ||
          epoch !== this.session.revision() ||
          !this.session.isAuthenticated()
        )
          return;
        this.target = { eventId, owner: eligibility.owner, epoch };
        this.ready.set(this.eligible(eligibility));
        if (!this.ready())
          this.note.set('Envio indisponivel. Verifique seu vinculo e as restricoes da conversa.');
      })
      .catch(() => {
        if (generation === this.generation)
          this.note.set('Nao foi possivel verificar o envio. Reabra a conversa quando conectado.');
      });
  }

  enqueue(text: string): boolean {
    if (!this.canCompose() || !this.target || !validChatText(text)) return false;
    this.queue.update((items) => [
      ...items.filter((i) => i.status !== 'sent').slice(-19),
      { key: crypto.randomUUID(), text, status: 'pending', attempts: 0 },
    ]);
    this.note.set(
      navigator.onLine
        ? 'Aguardando confirmacao do servidor.'
        : 'Aguardando conexao. Pendentes se perdem ao fechar ou atualizar esta aba.',
    );
    this.schedule();
    return true;
  }

  close(): void {
    this.generation++;
    this.cancel.next();
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null;
    this.target = null;
    this.busy = false;
    this.initialized = false;
    this.nextAt = 0;
    this.ready.set(false);
    this.queue.set([]);
    this.note.set(null);
  }

  private eligible(value: ChatWriteEligibility): boolean {
    return (
      value.active &&
      value.open &&
      (value.deadline === null || Date.parse(value.deadline) > Date.now())
    );
  }
  private schedule(): void {
    if (this.timer !== null || this.busy || !this.target || !this.ready() || !navigator.onLine)
      return;
    if (!this.queue().some((i) => i.status === 'pending')) return;
    this.timer = setTimeout(
      () => {
        this.timer = null;
        void this.flush();
      },
      Math.min(2147483647, Math.max(0, this.nextAt - Date.now())),
    );
  }
  private live(generation: number): boolean {
    return (
      generation === this.generation &&
      !!this.target &&
      this.session.isAuthenticated() &&
      this.target.epoch === this.session.revision()
    );
  }
  private async flush(): Promise<void> {
    if (Date.now() < this.nextAt) {
      this.schedule();
      return;
    }
    const item = this.queue().find((i) => i.status === 'pending');
    const target = this.target;
    if (!item || !target || !navigator.onLine || !this.live(this.generation)) return;
    const generation = this.generation;
    this.busy = true;
    try {
      const eligibility = await firstValueFrom(
        this.api.eligibility(target.eventId).pipe(takeUntil(this.cancel)),
      );
      if (!this.live(generation)) return;
      if (eligibility.owner !== target.owner) {
        this.close();
        return;
      }
      if (!this.eligible(eligibility)) {
        this.stop(
          'Envio restrito ou conversa encerrada. Consulte o historico antes de tentar outra mensagem.',
        );
        return;
      }
      if (!navigator.onLine) return;
      this.update(item.key, 'sending', item.attempts + 1);
      this.nextAt = Date.now() + 3000;
      await firstValueFrom(
        this.api.send(target.eventId, item.key, item.text).pipe(takeUntil(this.cancel)),
      );
      if (!this.live(generation)) return;
      this.update(item.key, 'sent', item.attempts + 1);
      this.nextAt = Date.now() + 3000;
      this.note.set('Mensagem enviada: confirmada pelo servidor.');
      this.deliveryRevision.update((n) => n + 1);
    } catch (error) {
      if (!this.live(generation)) return;
      const status = error instanceof HttpErrorResponse ? error.status : -1;
      if ([400, 401, 403, 404, 409].includes(status)) {
        this.stop(
          'Envio interrompido pela sessao ou restricao da conversa. Consulte o historico; uma tentativa anterior pode ter sido salva.',
        );
      } else if (item.attempts + 1 >= 5 || (status !== 0 && status !== 429 && status < 500)) {
        this.stop(
          'Confirmacao indisponivel. Consulte o historico antes de reenviar; uma tentativa pode ter sido salva.',
        );
      } else {
        this.update(item.key, 'pending', item.attempts + 1);
        this.nextAt = Math.max(
          this.nextAt,
          Date.now() + 1000 * 2 ** (item.attempts + 1),
          this.retryAt(error),
        );
        this.note.set(
          status === 429
            ? 'Aguardando o prazo informado pelo servidor.'
            : 'Aguardando conexao ou confirmacao. A mesma tentativa sera retomada.',
        );
      }
    } finally {
      if (generation === this.generation) {
        this.busy = false;
        this.schedule();
      }
    }
  }
  private update(key: string, status: ChatOutboxItem['status'], attempts: number): void {
    this.queue.update((items) =>
      items.map((i) => (i.key === key ? { ...i, status, attempts } : i)),
    );
  }
  private stop(message: string): void {
    this.queue.update((items) => items.filter((i) => i.status === 'sent'));
    this.ready.set(false);
    this.note.set(message);
  }
  private retryAt(error: unknown): number {
    if (!(error instanceof HttpErrorResponse) || error.status !== 429) return 0;
    const header = error.headers.get('Retry-After');
    const body = error.error?.retryAt;
    const headerTime =
      header && /^\d+$/u.test(header)
        ? Date.now() + Number(header) * 1000
        : Date.parse(header ?? '');
    const bodyTime = typeof body === 'string' ? Date.parse(body) : NaN;
    return Math.max(
      Number.isFinite(headerTime) ? headerTime : 0,
      Number.isFinite(bodyTime) ? bodyTime : 0,
    );
  }
  private readonly online = () => this.schedule();
  private readonly pagehide = () => this.close();
}
