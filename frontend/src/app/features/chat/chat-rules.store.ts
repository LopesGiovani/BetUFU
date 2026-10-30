import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Subject, firstValueFrom, takeUntil } from 'rxjs';
import { SessionStore } from '../../core/session/session.store';
import { ChatRulesApiAdapter } from './chat-rules-api.adapter';

type RulesStatus = 'loading' | 'ready' | 'error' | 'dismiss-saving' | 'sync-error';

@Injectable()
export class ChatRulesStore {
  private readonly api = inject(ChatRulesApiAdapter);
  private readonly session = inject(SessionStore);
  private readonly cancel = new Subject<void>();
  private sequence = 0;
  private closed = false;
  private readonly revision = signal(this.session.revision());
  private readonly authenticated = signal(this.session.isAuthenticated());
  private readonly current = computed(() => this.revision() === this.session.revision() && this.authenticated() === this.session.isAuthenticated());
  private readonly visible = signal(false);
  private readonly saved = signal(false);
  private readonly state = signal<RulesStatus>('ready');
  readonly opened = computed(() => this.current() && this.visible());
  readonly dismissed = computed(() => this.current() && this.saved());
  readonly status = computed(() => this.current() ? this.state() : 'ready');
  readonly error = computed(() => this.status() === 'error'
    ? 'Não foi possível consultar a preferência. Tente novamente ou abra Regras.'
    : this.status() === 'sync-error' ? 'A preferência não foi salva. O aviso pode reaparecer em outro dispositivo.' : null);

  constructor() {
    inject(DestroyRef).onDestroy(() => { this.sequence++; this.cancel.next(); this.cancel.complete(); });
  }
  async start(automatic: boolean) {
    this.sequence++; this.cancel.next(); this.revision.set(this.session.revision()); this.authenticated.set(this.session.isAuthenticated());
    this.visible.set(false); this.saved.set(false); this.closed = false; this.state.set('ready');
    if (automatic && this.session.isAuthenticated()) await this.read();
  }
  openManual() { this.visible.set(true); }
  async close() {
    this.visible.set(false); this.closed = true;
    if (!this.current() || !this.session.isAuthenticated() || this.dismissed() || this.status() === 'dismiss-saving') return;
    await this.save();
  }
  async retry() {
    if (!this.current() || !this.session.isAuthenticated()) return;
    if (this.status() === 'sync-error') await this.save();
    else if (this.status() === 'error') await this.read();
  }
  private async read() {
    const token = ++this.sequence; this.cancel.next(); this.state.set('loading');
    try {
      const dismissed = await firstValueFrom(this.api.read().pipe(takeUntil(this.cancel)));
      if (!this.accepts(token)) return;
      this.saved.set(dismissed); this.state.set('ready');
      if (!dismissed && !this.closed) this.visible.set(true);
    } catch {
      if (this.accepts(token)) this.state.set('error');
    }
  }
  private async save() {
    const token = ++this.sequence; this.cancel.next(); this.state.set('dismiss-saving');
    try {
      await firstValueFrom(this.api.dismiss().pipe(takeUntil(this.cancel)));
      if (this.accepts(token)) { this.saved.set(true); this.state.set('ready'); }
    } catch {
      if (this.accepts(token)) this.state.set('sync-error');
    }
  }
  private accepts(token: number) { return token === this.sequence && this.current() && this.session.isAuthenticated(); }
}
