import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom, Subject, takeUntil } from 'rxjs';
import {
  ChatCommunityApiAdapter,
  chatErrorMessage,
  type ChatPersonalMuteState,
} from './chat-community-api.adapter';
import { SessionStore } from '../../core/session/session.store';

interface PendingAction {
  participantId: string;
  muted: boolean;
}

@Injectable()
export class ChatPersonalMuteStore {
  private readonly api = inject(ChatCommunityApiAdapter);
  private readonly session = inject(SessionStore);
  private readonly state = signal<ChatPersonalMuteState>({ version: 0, participantIds: new Set() });
  private readonly busy = signal(false);
  private readonly ready = signal(false);
  private readonly failure = signal<string | null>(null);
  private readonly cancel = new Subject<void>();
  private epoch = -1;
  private sequence = 0;
  private refreshAgain = false;
  private action: PendingAction | null = null;

  readonly version = computed(() => this.state().version);
  readonly participantIds = computed(() => this.state().participantIds);
  readonly pending = this.busy.asReadonly();
  readonly error = this.failure.asReadonly();
  readonly notice = computed(
    () => this.failure() ?? (this.busy() ? 'Sincronizando silenciamentos...' : null),
  );
  readonly canChange = computed(
    () => this.ready() && !this.busy() && this.session.isAuthenticated(),
  );

  constructor() {
    inject(DestroyRef).onDestroy(() => this.clear());
    effect(() => {
      const epoch = this.session.revision();
      const authenticated = this.session.isAuthenticated();
      untracked(() => {
        if (epoch !== this.epoch || !authenticated) this.clear();
        this.epoch = epoch;
        if (authenticated) void this.open();
      });
    });
  }

  async open(): Promise<void> {
    if (!this.ready() && !this.busy()) await this.refresh();
  }

  clear(): void {
    this.sequence++;
    this.cancel.next();
    this.action = null;
    this.refreshAgain = false;
    this.busy.set(false);
    this.ready.set(false);
    this.state.set({ version: 0, participantIds: new Set() });
    this.failure.set(null);
  }

  isMuted(participantId: string): boolean {
    return this.participantIds().has(participantId);
  }

  async setMuted(participantId: string, muted: boolean): Promise<void> {
    if (!this.canChange()) return;
    const previous = this.state();
    const optimistic = new Set(previous.participantIds);
    if (muted) optimistic.add(participantId);
    else optimistic.delete(participantId);
    this.state.set({ version: previous.version, participantIds: optimistic });
    this.busy.set(true);
    this.failure.set(null);
    this.action = { participantId, muted };
    const token = ++this.sequence;
    const epoch = this.session.revision();
    try {
      const request = muted
        ? this.api.mute(participantId, previous.version)
        : this.api.unmute(participantId, previous.version);
      const result = await firstValueFrom(request.pipe(takeUntil(this.cancel)));
      if (!this.current(token, epoch)) return;
      this.apply(result);
      this.action = null;
    } catch (error) {
      if (!this.current(token, epoch)) return;
      this.state.set(previous);
      this.failure.set(chatErrorMessage(error));
    } finally {
      if (this.current(token, epoch)) {
        this.busy.set(false);
        if (this.refreshAgain) void this.refresh();
      }
    }
  }

  async retry(): Promise<void> {
    const action = this.action;
    const epoch = this.session.revision();
    // Refetch first: a stale version must never overwrite another device's preference.
    if (await this.readPreferences(false)) {
      if (epoch !== this.session.revision()) return;
      if (action) await this.setMuted(action.participantId, action.muted);
    }
    if (epoch === this.session.revision() && this.refreshAgain && !this.busy()) void this.refresh();
  }

  refresh(): Promise<boolean> {
    return this.readPreferences(true);
  }

  private async readPreferences(drainHints: boolean): Promise<boolean> {
    if (!this.session.isAuthenticated()) return false;
    if (this.busy()) {
      this.refreshAgain = true;
      return false;
    }
    this.refreshAgain = false;
    this.busy.set(true);
    const token = ++this.sequence;
    const epoch = this.session.revision();
    try {
      const result = await firstValueFrom(this.api.readMutes().pipe(takeUntil(this.cancel)));
      if (!this.current(token, epoch)) return false;
      this.apply(result);
      // A background refresh does not silently dismiss a failed user's command.
      if (!this.action) this.failure.set(null);
      return true;
    } catch (error) {
      if (this.current(token, epoch)) this.failure.set(chatErrorMessage(error));
      return false;
    } finally {
      if (this.current(token, epoch)) {
        this.busy.set(false);
        if (this.refreshAgain && drainHints) void this.refresh();
      }
    }
  }

  private current(token: number, epoch: number): boolean {
    return (
      token === this.sequence && epoch === this.session.revision() && this.session.isAuthenticated()
    );
  }

  private apply(value: ChatPersonalMuteState): void {
    if (value.version < this.version()) return;
    this.state.set({ version: value.version, participantIds: new Set(value.participantIds) });
    this.ready.set(true);
  }
}
