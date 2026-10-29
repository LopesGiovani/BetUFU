import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, type Observable, Subject, takeUntil } from 'rxjs';
import { SessionStore } from '../../core/session/session.store';
import type { PrivacyCase } from '../../api/generated/model/privacy-case';
import type { PrivacyRequest } from '../../api/generated/model/privacy-request';
import type { PrivacyExport } from '../../api/generated/model/privacy-export';
import type { PrivacyResponseCommand } from '../../api/generated/model/privacy-response-command';
import { PrivacyRightsApiAdapter, PRIVACY_UUID } from './privacy-rights-api.adapter';

@Injectable()
export class PrivacyRightsStore {
  private readonly api = inject(PrivacyRightsApiAdapter);
  private readonly session = inject(SessionStore);
  private readonly cancel = new Subject<void>();
  private epoch = 0;
  private mode: boolean | null = null;
  private readonly revision = signal(this.session.revision());
  private readonly current = computed(() => this.session.isAuthenticated() && this.revision() === this.session.revision());
  private readonly items = signal<readonly PrivacyCase[]>([]);
  private readonly detail = signal<PrivacyCase | null>(null);
  private readonly exported = signal<PrivacyExport | null>(null);
  private readonly admin = signal(false);
  private readonly working = signal(false);
  private readonly message = signal<string | null>(null);
  readonly offset = signal(0);
  readonly cases = computed(() => this.current() ? this.items() : []);
  readonly selected = computed(() => this.current() ? this.detail() : null);
  readonly artifact = computed(() => this.current() ? this.exported() : null);
  readonly administrator = computed(() => this.current() && this.admin());
  readonly busy = computed(() => this.current() && this.working());
  readonly error = computed(() => this.current() ? this.message() : null);

  constructor() {
    effect(() => {
      this.session.revision(); const authenticated = this.session.isAuthenticated();
      untracked(() => { this.clear(); if (authenticated && this.mode !== null) void this.start(this.mode); });
    });
    inject(DestroyRef).onDestroy(() => { this.mode = null; this.clear(); this.cancel.complete(); });
  }
  private clear() {
    this.epoch++; this.cancel.next(); this.revision.set(this.session.revision());
    this.items.set([]); this.detail.set(null); this.exported.set(null); this.admin.set(false);
    this.working.set(false); this.message.set(null); this.offset.set(0);
  }
  async start(operational: boolean) {
    this.mode = operational;
    if (operational) {
      this.admin.set(false);
      await this.run(this.api.administrator(), value => {
        this.admin.set(value);
        if (!value) this.message.set('Somente o administrador geral pode analisar e responder pedidos.');
      });
    } else await this.load(0);
  }
  async load(offset = this.offset()) {
    if (!Number.isInteger(offset) || offset < 0) return;
    await this.run(this.api.list(offset), items => { this.items.set(items); this.offset.set(offset); });
  }
  async open(id: string) {
    if (this.busy()) return;
    this.detail.set(null); this.exported.set(null);
    if (!PRIVACY_UUID.test(id)) { this.message.set('Informe um identificador de pedido valido.'); return; }
    await this.run(this.api.view(id), value => this.detail.set(value));
  }
  async request(kind: PrivacyRequest.RequestKindEnum, athlete?: { athleteId: string; proof: string; verified: boolean }) {
    const epoch = this.epoch;
    if (!['NOTICE', 'ACCESS', 'CORRECTION', 'DELETION', 'OBJECTION', 'PSEUDONYMIZATION'].includes(kind)) return false;
    if (athlete && (!this.administrator() || !athlete.verified || !PRIVACY_UUID.test(athlete.athleteId) ||
      !athlete.proof.trim() || athlete.proof.length > 280)) {
      this.message.set('Confira a identidade fora do sistema e informe a referencia restrita da comprovacao.'); return false;
    }
    let id: string | null = null;
    const ok = await this.run(this.api.request({ subjectKind: athlete ? 'ATHLETE_WITHOUT_ACCOUNT' : 'ACCOUNT',
      subjectRef: athlete?.athleteId ?? null, requestKind: kind, proofReference: athlete?.proof.trim() ?? null,
      verifiedChannel: athlete ? 'VERIFIED_OPERATOR_CHANNEL' : 'AUTHENTICATED_SESSION' }), value => { id = value.caseId; }, true);
    if (ok && id && epoch === this.epoch && this.current()) {
      if (!this.mode) await this.load(0);
      if (epoch === this.epoch && this.current()) await this.open(id);
    }
    return ok;
  }
  async respond(decision: PrivacyResponseCommand.DecisionEnum, text: string) {
    const epoch = this.epoch;
    const selected = this.selected();
    if (!this.administrator() || !selected || !['APPROVE', 'REJECT', 'ANALYZE'].includes(decision) ||
      !text.trim() || text.length > 2000 || text.includes('\0')) return false;
    const ok = await this.run(this.api.respond(selected.caseId, { decision, responseText: text.trim() }), () => {}, true);
    if (ok && epoch === this.epoch && this.current()) await this.open(selected.caseId);
    return ok;
  }
  async prepareExport() {
    const epoch = this.epoch;
    const selected = this.selected(); if (!this.administrator() || !selected || selected.requestKind !== 'ACCESS') return false;
    const ok = await this.run(this.api.prepareExport(selected.caseId), value => this.exported.set(value), true);
    // Refresh the real timeline; retain the independently returned artifact.
    if (ok && epoch === this.epoch && this.current()) await this.run(this.api.view(selected.caseId), value => this.detail.set(value));
    return ok;
  }
  async download() {
    const selected = this.selected(); if (!selected) return false;
    return this.run(this.api.download(selected.caseId), value => this.exported.set(value));
  }
  private async run<T>(request: Observable<T>, accept: (value: T) => void, mutation = false): Promise<boolean> {
    if (!this.current() || this.busy()) return false;
    const epoch = this.epoch; this.working.set(true); this.message.set(null);
    try {
      const value = await firstValueFrom(request.pipe(takeUntil(this.cancel)));
      if (epoch !== this.epoch || !this.current()) return false;
      accept(value); return true;
    } catch (error) {
      if (epoch === this.epoch && this.current()) this.message.set(error instanceof HttpErrorResponse && error.status === 403
        ? 'Voce nao tem acesso a esta operacao ou pedido.'
        : error instanceof HttpErrorResponse && error.status === 409 ? 'O pedido ainda nao pode ser concluido. Confira as provas de cumprimento e atualize o historico.'
        : mutation ? 'Resultado nao confirmado. Atualize o pedido antes de enviar outra operacao.' : 'Nao foi possivel carregar os dados. Tente novamente.');
      return false;
    } finally { if (epoch === this.epoch) this.working.set(false); }
  }
}
