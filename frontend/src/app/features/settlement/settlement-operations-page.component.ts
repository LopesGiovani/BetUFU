import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { PrivateApiRecovery } from '../../core/session/private-api-recovery';
import {
  SettlementService,
  SettlementAuthorizationCandidateItem,
  SettlementCaseProgress,
} from '../../api/generated';
import { OperationsApiAdapter } from '../operations/operations-api.adapter';
@Component({
  selector: 'app-settlement-operations-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './settlement-operations-page.component.html',
  styleUrl: './settlement-operations-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettlementOperationsPageComponent implements OnInit {
  private readonly recovery = inject(PrivateApiRecovery);
  private readonly api = inject(SettlementService);
  private readonly operations = inject(OperationsApiAdapter);
  readonly candidates = signal<SettlementAuthorizationCandidateItem[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal(false);
  readonly permitted = signal(false);
  readonly stale = signal(false);
  readonly message = signal('');
  readonly selected = signal<SettlementAuthorizationCandidateItem | null>(null);
  readonly progress = signal<SettlementCaseProgress | null>(null);
  @ViewChild('confirmation') confirmation?: ElementRef<HTMLDialogElement>;
  private trigger: HTMLElement | null = null;
  private idempotencyKey = '';
  ngOnInit() {
    void this.load();
  }
  async load() {
    if (this.loading() || this.busy()) return;
    this.loading.set(true);
    this.error.set(false);
    try {
      this.permitted.set(
        (await this.recovery.read(this.operations.fetchOperationalCapabilities())).includes(
          'AUTHORIZE_SETTLEMENT',
        ),
      );
      if (!this.permitted()) {
        this.candidates.set([]);
        this.message.set('Nao foi possivel acessar esta operacao. Retorne a Arena.');
        return;
      }
      this.candidates.set(
        (await this.recovery.read(this.api.listSettlementAuthorizationCandidates())).items,
      );
      this.stale.set(false);
      this.selected.set(null);
      this.idempotencyKey = '';
    } catch {
      this.candidates.set([]);
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
  ask(candidate: SettlementAuthorizationCandidateItem, event: Event) {
    if (
      this.busy() ||
      this.loading() ||
      this.stale() ||
      candidate.alreadyAuthorized ||
      !this.permitted()
    )
      return;
    this.trigger = event.currentTarget as HTMLElement;
    this.selected.set(candidate);
    this.idempotencyKey = crypto.randomUUID();
    this.confirmation?.nativeElement.showModal();
  }
  cancel(event?: Event) {
    event?.preventDefault();
    this.confirmation?.nativeElement.close();
    this.trigger?.focus();
  }
  containFocus(event: KeyboardEvent) {
    if (event.key !== 'Tab') return;
    const buttons =
      this.confirmation?.nativeElement.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
    if (!buttons?.length) return;
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    if (event.shiftKey ? event.target === first : event.target === last) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    }
  }
  async authorize() {
    const candidate = this.selected();
    if (!candidate || this.busy() || this.stale() || !this.permitted()) return;
    this.cancel();
    this.busy.set(true);
    this.message.set('');
    try {
      const response = await this.recovery.read(
        this.api.authorizeSettlement({
          eventId: candidate.eventId,
          idempotencyKey: this.idempotencyKey,
          settlementAuthorizationRequest: {
            expectedOutcomeFactId: candidate.outcomeFactId,
            expectedConfirmedVersionId: candidate.confirmedVersionId ?? null,
          },
        }),
      );
      this.progress.set(response.caseProgress);
      this.message.set('Autorizacao registrada');
      this.candidates.update((items) =>
        items.map((i) =>
          i.outcomeFactId === candidate.outcomeFactId ? { ...i, alreadyAuthorized: true } : i,
        ),
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.stale.set(true);
        this.message.set(
          'A versao mudou ou a operacao esta em conflito. Atualize o resultado e confira novamente.',
        );
      } else {
        this.message.set(
          'Nao foi possivel registrar a autorizacao. Atualize os resultados antes de tentar novamente.',
        );
        this.stale.set(true);
      }
    } finally {
      this.busy.set(false);
    }
  }
  async refreshProgress() {
    const progress = this.progress();
    if (!progress || this.busy()) return;
    this.busy.set(true);
    try {
      this.progress.set(
        await this.recovery.read(this.api.getSettlementCaseProgress({ caseId: progress.caseId })),
      );
    } catch {
      this.message.set('Nao foi possivel atualizar o progresso. Tente novamente.');
    } finally {
      this.busy.set(false);
    }
  }
  result(candidate: SettlementAuthorizationCandidateItem) {
    return candidate.outcomeKind === 'SPORTING_RESULT'
      ? candidate.homeScore + ' x ' + candidate.awayScore
      : candidate.outcomeKind === 'EVENT_CANCELLED'
        ? 'Partida cancelada'
        : candidate.outcomeKind === 'ABANDONED_NO_VALID_RESULT'
          ? 'Sem resultado valido'
          : 'Resultado administrativo: vencedor ' +
            (candidate.winnerSlotNo === 1 ? 'mandante' : 'visitante');
  }
}
