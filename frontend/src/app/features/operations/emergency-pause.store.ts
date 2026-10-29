import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, Subject, takeUntil } from 'rxjs';
import { SessionStore } from '../../core/session/session.store';
import type { EmergencyPauseCommand } from '../../api/generated/model/emergency-pause-command';
import type { EmergencyPauseState } from '../../api/generated/model/emergency-pause-state';
import { EmergencyPauseApiAdapter, type PauseAvailability } from './emergency-pause-api.adapter';

@Injectable()
export class EmergencyPauseStore {
  private readonly api = inject(EmergencyPauseApiAdapter);
  private readonly session = inject(SessionStore);
  private readonly cancel = new Subject<void>();
  private epoch = 0;
  private target: { kind: 'chat' | 'market'; id: string | null } | null = null;
  private fetching = false;
  private again = false;
  readonly availability = signal<PauseAvailability>('unknown');
  readonly administrator = signal(false);
  readonly pauses = signal<readonly EmergencyPauseState[]>([]);
  readonly loading = signal(false);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = computed(() => ({
    unknown: 'Verificando disponibilidade', available: null,
    paused: 'Novos envios pausados temporariamente.',
    error: 'Nao foi possivel verificar a disponibilidade.',
  })[this.availability()]);

  constructor() {
    effect(() => {
      this.session.revision();
      this.session.isAuthenticated();
      untracked(() => this.clear());
    });
    inject(DestroyRef).onDestroy(() => { this.clear(); this.cancel.complete(); });
  }

  clear() {
    this.epoch++;
    this.cancel.next();
    this.target = null;
    this.fetching = false;
    this.again = false;
    this.availability.set('unknown');
    this.administrator.set(false);
    this.pauses.set([]);
    this.loading.set(false);
    this.sending.set(false);
    this.error.set(null);
  }

  open(kind: 'chat' | 'market', id: string | null) {
    if (this.target?.kind === kind && this.target.id === id) return;
    this.clear();
    this.target = { kind, id };
    void this.refresh();
  }

  async refresh() {
    const target = this.target;
    if (!target) return;
    if (this.fetching) { this.again = true; return; }
    this.fetching = true;
    this.availability.set('unknown');
    const epoch = this.epoch;
    try {
      const request = target.kind === 'chat' ? this.api.chat(target.id) : this.api.market(target.id!);
      const value = await firstValueFrom(request.pipe(takeUntil(this.cancel)));
      if (epoch === this.epoch) this.availability.set(value);
    } catch {
      if (epoch === this.epoch) this.availability.set('error');
    } finally {
      if (epoch === this.epoch) {
        this.fetching = false;
        if (this.again) { this.again = false; void this.refresh(); }
      }
    }
  }

  async load() {
    const epoch = this.epoch;
    this.loading.set(true);
    this.error.set(null);
    try {
      const items = await firstValueFrom(this.api.list().pipe(takeUntil(this.cancel)));
      if (epoch === this.epoch) { this.pauses.set(items); this.administrator.set(true); }
    } catch (error) {
      if (epoch === this.epoch) {
        this.pauses.set([]);
        this.administrator.set(false);
        this.error.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'Somente o administrador geral pode gerenciar pausas.' : 'Nao foi possivel carregar as pausas.');
      }
    } finally {
      if (epoch === this.epoch) this.loading.set(false);
    }
  }

  async command(command: EmergencyPauseCommand): Promise<boolean> {
    if (this.sending() || !this.administrator()) return false;
    if (!/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/u.test(command.targetId) ||
        !command.reason.trim() || command.reason.length > 280) {
      this.error.set('Informe um identificador valido e um motivo de ate 280 caracteres.');
      return false;
    }
    const epoch = this.epoch;
    this.sending.set(true);
    this.error.set(null);
    try {
      await firstValueFrom(this.api.command(command).pipe(takeUntil(this.cancel)));
      if (epoch !== this.epoch) return false;
      await this.load();
      return epoch === this.epoch;
    } catch {
      if (epoch === this.epoch) this.error.set('Operacao nao confirmada. Confira o estado antes de tentar novamente.');
      return false;
    } finally {
      if (epoch === this.epoch) this.sending.set(false);
    }
  }
}
