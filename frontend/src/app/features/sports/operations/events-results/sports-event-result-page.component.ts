import { SlicePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SportsCatalogTree } from '../../../../api/generated/model/sports-catalog-tree';
import { SportsEditionSummary } from '../../../../api/generated/model/sports-edition-summary';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';
import { SportsEventFormat } from '../../../../api/generated/model/sports-event-format';
import { SportsResultVersion } from '../../../../api/generated/model/sports-result-version';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsVenueState } from '../../../../api/generated/model/sports-venue-state';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';
import { SportsOrganizationsApiAdapter } from '../organizations/sports-organizations-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import {
  SportsEventCreatePayload,
  SportsEventSlotRequest,
  SportsEventUpdatePayload,
  SportsResultScoreItemRequest,
} from './sports-event-result-api.adapter';
import { SportsEventResultStore } from './sports-event-result.store';

export type ActiveModal =
  | 'createEvent'
  | 'editDraft'
  | 'suspend'
  | 'postpone'
  | 'cancel'
  | 'informResult'
  | 'reviewResult'
  | 'confirmResult'
  | 'correctResult'
  | null;

export interface ResultDiffModel {
  current: SportsResultVersion | null;
  proposed: {
    sourceReference: string;
    reason?: string;
    scores: readonly { slotNo: number; displayLabel: string; score: number }[];
  };
}

@Component({
  selector: 'app-sports-event-result-page',
  standalone: true,
  imports: [ReactiveFormsModule, SlicePipe],
  templateUrl: './sports-event-result-page.component.html',
  styleUrl: './sports-event-result-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsEventResultPageComponent implements OnInit {
  readonly scope = inject(SportsScopeStore);
  readonly store = inject(SportsEventResultStore);
  private readonly catalogAdapter = inject(SportsCatalogApiAdapter);
  private readonly organizationsAdapter = inject(SportsOrganizationsApiAdapter);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });

  readonly tree = signal<SportsCatalogTree | null>(null);
  readonly editions = signal<readonly SportsEditionSummary[]>(Object.freeze([]));
  readonly teams = signal<readonly SportsTeamSummary[]>(Object.freeze([]));
  readonly selectedEditionId = signal<string>('');
  readonly catalogLoading = signal(false);
  readonly catalogError = signal<string | null>(null);
  readonly activeModal = signal<ActiveModal>(null);
  readonly activeDiff = signal<ResultDiffModel | null>(null);

  readonly eventForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    format: new FormControl<SportsEventFormat>('HEAD_TO_HEAD', { nonNullable: true, validators: [Validators.required] }),
    sportId: new FormControl('', { nonNullable: true }),
    categoryId: new FormControl('', { nonNullable: true }),
    phaseId: new FormControl('', { nonNullable: true }),
    scheduledAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    zoneId: new FormControl('America/Sao_Paulo', { nonNullable: true, validators: [Validators.required, Validators.maxLength(64)] }),
    venueState: new FormControl<SportsVenueState>('LOCAL_DEFINIDO', { nonNullable: true, validators: [Validators.required] }),
    venueId: new FormControl('', { nonNullable: true }),
    venueSpaceId: new FormControl('', { nonNullable: true }),
    sourceReference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2048)] }),
    slot1Label: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    slot1TeamId: new FormControl('', { nonNullable: true }),
    slot2Label: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    slot2TeamId: new FormControl('', { nonNullable: true }),
  });

  readonly resultForm = new FormGroup({
    sourceReference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2048)] }),
    slot1Score: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    slot2Score: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
  });

  readonly correctionForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(1024)] }),
    sourceReference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2048)] }),
    slot1Score: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
    slot2Score: new FormControl<number | null>(null, { validators: [Validators.required, Validators.min(0)] }),
  });

  readonly suspendForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1024)] }),
  });

  readonly postponeForm = new FormGroup({
    scheduledAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    zoneId: new FormControl('America/Sao_Paulo', { nonNullable: true, validators: [Validators.required, Validators.maxLength(64)] }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(1024)] }),
  });

  readonly cancelForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(1024)] }),
  });

  async ngOnInit(): Promise<void> {
    const requestedComp = this.route?.snapshot.queryParamMap.get('competitionId');
    await this.scope.load(requestedComp);
    if (this.scope.hasSelection()) {
      await this.loadWorkspace();
    }
  }

  async retry(): Promise<void> {
    const requestedComp = this.route?.snapshot.queryParamMap.get('competitionId');
    await this.scope.load(requestedComp);
    if (this.scope.hasSelection()) {
      await this.loadWorkspace();
    }
  }

  async changeScope(competitionId: string): Promise<void> {
    if (!competitionId || competitionId === this.scope.selectedCompetitionId()) return;
    if (this.hasDirtyForm() && !this.confirm('Trocar de competição e descartar alterações não salvas?')) {
      return;
    }
    if (!this.scope.selectCompetition(competitionId)) return;
    this.resetForms();
    await this.syncQueryParams({ competitionId, editionId: null, eventId: null });
    await this.loadWorkspace();
  }

  async loadWorkspace(): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;

    this.catalogLoading.set(true);
    this.catalogError.set(null);

    try {
      const [tree, teams] = await Promise.all([
        firstValueFrom(this.catalogAdapter.fetchTree(competitionId)),
        firstValueFrom(this.organizationsAdapter.fetchTeams(competitionId)).catch(() => Object.freeze([]) as readonly SportsTeamSummary[]),
      ]);

      this.tree.set(tree);
      this.editions.set(tree.editions);
      this.teams.set(teams);

      const requestedEdition = this.route?.snapshot.queryParamMap.get('editionId');
      const activeEdition = tree.editions.find((e) => e.editionId === requestedEdition) ?? tree.editions[0];

      if (activeEdition) {
        this.selectedEditionId.set(activeEdition.editionId);
        const requestedEvent = this.route?.snapshot.queryParamMap.get('eventId');
        await this.store.loadEdition(activeEdition.editionId, requestedEvent);
      } else {
        this.selectedEditionId.set('');
        await this.store.loadEdition(null);
      }
    } catch {
      this.catalogError.set('As estruturas esportivas desta edição não estão disponíveis agora. Tente novamente antes de transcrever ou publicar.');
    } finally {
      this.catalogLoading.set(false);
    }
  }

  async changeEdition(editionId: string): Promise<void> {
    if (!editionId || editionId === this.selectedEditionId()) return;
    this.selectedEditionId.set(editionId);
    this.resetForms();
    await this.syncQueryParams({ editionId, eventId: null });
    await this.store.loadEdition(editionId);
  }

  async selectEvent(eventId: string): Promise<void> {
    if (!eventId || eventId === this.store.selectedEventId()) return;
    this.resetForms();
    await this.syncQueryParams({ eventId });
    await this.store.selectEvent(eventId);
  }

  openCreateEventModal(): void {
    this.resetForms();
    this.eventForm.setValue({
      title: '',
      format: 'HEAD_TO_HEAD',
      sportId: this.tree()?.sports[0]?.sportId ?? '',
      categoryId: this.tree()?.categories[0]?.categoryId ?? '',
      phaseId: this.tree()?.phases[0]?.phaseId ?? '',
      scheduledAt: new Date(Date.now() + 3600000 * 24).toISOString().slice(0, 16),
      zoneId: 'America/Sao_Paulo',
      venueState: 'LOCAL_DEFINIDO',
      venueId: this.tree()?.venues[0]?.venueId ?? '',
      venueSpaceId: this.tree()?.venues[0]?.spaces?.[0]?.venueSpaceId ?? '',
      sourceReference: '',
      slot1Label: '',
      slot1TeamId: this.teams()[0]?.teamId ?? '',
      slot2Label: '',
      slot2TeamId: this.teams()[1]?.teamId ?? '',
    });
    this.activeModal.set('createEvent');
  }

  openEditDraftModal(): void {
    const event = this.store.selectedEvent();
    if (!event) return;
    this.resetForms();
    const s1 = event.slots.find((s) => s.slotNo === 1) ?? event.slots[0];
    const s2 = event.slots.find((s) => s.slotNo === 2) ?? event.slots[1];
    this.eventForm.setValue({
      title: event.title,
      format: event.format,
      sportId: event.sportId ?? '',
      categoryId: event.categoryId ?? '',
      phaseId: event.phaseId ?? '',
      scheduledAt: event.scheduledAt ? event.scheduledAt.slice(0, 16) : '',
      zoneId: event.zoneId || 'America/Sao_Paulo',
      venueState: event.venueState,
      venueId: event.venueId ?? '',
      venueSpaceId: event.venueSpaceId ?? '',
      sourceReference: event.sourceReference,
      slot1Label: s1?.displayLabel ?? '',
      slot1TeamId: s1?.teamId ?? '',
      slot2Label: s2?.displayLabel ?? '',
      slot2TeamId: s2?.teamId ?? '',
    });
    this.activeModal.set('editDraft');
  }

  openSuspendModal(): void {
    this.resetForms();
    this.suspendForm.reset({ reason: '' });
    this.activeModal.set('suspend');
  }

  openPostponeModal(): void {
    const event = this.store.selectedEvent();
    if (!event) return;
    this.resetForms();
    this.postponeForm.setValue({
      scheduledAt: event.scheduledAt ? event.scheduledAt.slice(0, 16) : '',
      zoneId: event.zoneId || 'America/Sao_Paulo',
      reason: '',
    });
    this.activeModal.set('postpone');
  }

  openCancelModal(): void {
    this.resetForms();
    this.cancelForm.reset({ reason: '' });
    this.activeModal.set('cancel');
  }

  openInformResultModal(): void {
    this.resetForms();
    this.resultForm.reset({ sourceReference: '', slot1Score: null, slot2Score: null });
    this.activeModal.set('informResult');
  }

  openReviewResultModal(): void {
    this.resetForms();
    this.resultForm.reset({ sourceReference: '', slot1Score: null, slot2Score: null });
    this.activeModal.set('reviewResult');
  }

  openConfirmResultModal(): void {
    this.resetForms();
    const confirmed = this.store.confirmedVersion();
    const s1 = confirmed?.scores.find((s) => s.slotNo === 1)?.score ?? null;
    const s2 = confirmed?.scores.find((s) => s.slotNo === 2)?.score ?? null;
    this.resultForm.reset({
      sourceReference: confirmed?.sourceReference ?? '',
      slot1Score: s1,
      slot2Score: s2,
    });
    this.activeModal.set('confirmResult');
  }

  openCorrectResultModal(): void {
    this.resetForms();
    const confirmed = this.store.confirmedVersion();
    const s1 = confirmed?.scores.find((s) => s.slotNo === 1)?.score ?? null;
    const s2 = confirmed?.scores.find((s) => s.slotNo === 2)?.score ?? null;
    this.correctionForm.reset({
      reason: '',
      sourceReference: '',
      slot1Score: s1,
      slot2Score: s2,
    });
    this.activeModal.set('correctResult');
  }

  closeModal(): void {
    this.activeModal.set(null);
    this.activeDiff.set(null);
    this.store.clearMessages();
  }

  async saveCreateEvent(): Promise<void> {
    if (this.eventForm.invalid || !this.selectedEditionId()) return;
    const val = this.eventForm.getRawValue();
    const slots: SportsEventSlotRequest[] = [
      { slotNo: 1, displayLabel: val.slot1Label.trim(), teamId: val.slot1TeamId || undefined },
      { slotNo: 2, displayLabel: val.slot2Label.trim(), teamId: val.slot2TeamId || undefined },
    ];
    const payload: SportsEventCreatePayload = {
      title: val.title.trim(),
      format: val.format,
      scheduledAt: new Date(val.scheduledAt).toISOString(),
      zoneId: val.zoneId.trim(),
      venueState: val.venueState,
      sourceReference: val.sourceReference.trim(),
      slots,
      sportId: val.sportId || undefined,
      categoryId: val.categoryId || undefined,
      phaseId: val.phaseId || undefined,
      venueId: val.venueState === 'LOCAL_DEFINIDO' ? (val.venueId || undefined) : undefined,
      venueSpaceId: val.venueState === 'LOCAL_DEFINIDO' ? (val.venueSpaceId || undefined) : undefined,
    };
    const success = await this.store.createEvent(this.selectedEditionId(), payload);
    if (success) this.closeModal();
  }

  async saveUpdateDraft(): Promise<void> {
    if (this.eventForm.invalid) return;
    const val = this.eventForm.getRawValue();
    const slots: SportsEventSlotRequest[] = [
      { slotNo: 1, displayLabel: val.slot1Label.trim(), teamId: val.slot1TeamId || undefined },
      { slotNo: 2, displayLabel: val.slot2Label.trim(), teamId: val.slot2TeamId || undefined },
    ];
    const payload: SportsEventUpdatePayload = {
      title: val.title.trim(),
      format: val.format,
      scheduledAt: new Date(val.scheduledAt).toISOString(),
      zoneId: val.zoneId.trim(),
      venueState: val.venueState,
      sourceReference: val.sourceReference.trim(),
      slots,
      sportId: val.sportId || undefined,
      categoryId: val.categoryId || undefined,
      phaseId: val.phaseId || undefined,
      venueId: val.venueState === 'LOCAL_DEFINIDO' ? (val.venueId || undefined) : undefined,
      venueSpaceId: val.venueState === 'LOCAL_DEFINIDO' ? (val.venueSpaceId || undefined) : undefined,
    };
    const success = await this.store.updateEventDraft(payload);
    if (success) this.closeModal();
  }

  async publishEvent(): Promise<void> {
    const event = this.store.selectedEvent();
    if (!event) return;
    if (!this.confirm(`Publicar o evento "${event.title}" na agenda esportiva?`)) return;
    await this.store.publishEvent();
  }

  async saveSuspend(): Promise<void> {
    const reason = this.suspendForm.controls.reason.value;
    const success = await this.store.suspendEvent(reason);
    if (success) this.closeModal();
  }

  async savePostpone(): Promise<void> {
    if (this.postponeForm.invalid) return;
    const val = this.postponeForm.getRawValue();
    const scheduledAtIso = new Date(val.scheduledAt).toISOString();
    const success = await this.store.postponeEvent(scheduledAtIso, val.zoneId, val.reason);
    if (success) this.closeModal();
  }

  async saveCancel(): Promise<void> {
    if (this.cancelForm.invalid) return;
    const reason = this.cancelForm.controls.reason.value;
    const success = await this.store.cancelEvent(reason);
    if (success) this.closeModal();
  }

  async saveInformResult(): Promise<void> {
    if (this.resultForm.invalid) return;
    const val = this.resultForm.getRawValue();
    const scores: SportsResultScoreItemRequest[] = [
      { slotNo: 1, score: Number(val.slot1Score) },
      { slotNo: 2, score: Number(val.slot2Score) },
    ];
    const success = await this.store.informResult(val.sourceReference, scores);
    if (success) this.closeModal();
  }

  async saveReviewResult(): Promise<void> {
    if (this.resultForm.invalid) return;
    const val = this.resultForm.getRawValue();
    const scores: SportsResultScoreItemRequest[] = [
      { slotNo: 1, score: Number(val.slot1Score) },
      { slotNo: 2, score: Number(val.slot2Score) },
    ];
    const success = await this.store.reviewResult(val.sourceReference, scores);
    if (success) this.closeModal();
  }

  async saveConfirmResult(): Promise<void> {
    if (this.resultForm.invalid) return;
    const val = this.resultForm.getRawValue();
    const scores: SportsResultScoreItemRequest[] = [
      { slotNo: 1, score: Number(val.slot1Score) },
      { slotNo: 2, score: Number(val.slot2Score) },
    ];
    const success = await this.store.confirmResult(val.sourceReference, scores);
    if (success) this.closeModal();
  }

  async saveCorrectResult(): Promise<void> {
    if (this.correctionForm.invalid) return;
    const val = this.correctionForm.getRawValue();
    const scores: SportsResultScoreItemRequest[] = [
      { slotNo: 1, score: Number(val.slot1Score) },
      { slotNo: 2, score: Number(val.slot2Score) },
    ];
    const success = await this.store.correctResult(val.reason, val.sourceReference, scores);
    if (success) this.closeModal();
  }

  previewEvent(event: SportsEventDetail | null = this.store.selectedEvent()): void {
    if (!event) return;
    this.store.previewSnapshot(event);
  }

  previewResultDiff(): void {
    const event = this.store.selectedEvent();
    if (!event) return;
    const confirmed = this.store.confirmedVersion();
    let proposedScores: Array<{ slotNo: number; displayLabel: string; score: number }> = [];
    let sourceRef = '';
    let reasonText = '';

    if (this.activeModal() === 'correctResult') {
      const val = this.correctionForm.getRawValue();
      sourceRef = val.sourceReference;
      reasonText = val.reason;
      proposedScores = [
        { slotNo: 1, displayLabel: this.getSlotLabel(event, 1), score: Number(val.slot1Score ?? 0) },
        { slotNo: 2, displayLabel: this.getSlotLabel(event, 2), score: Number(val.slot2Score ?? 0) },
      ];
    } else {
      const val = this.resultForm.getRawValue();
      sourceRef = val.sourceReference;
      proposedScores = [
        { slotNo: 1, displayLabel: this.getSlotLabel(event, 1), score: Number(val.slot1Score ?? 0) },
        { slotNo: 2, displayLabel: this.getSlotLabel(event, 2), score: Number(val.slot2Score ?? 0) },
      ];
    }

    this.activeDiff.set({
      current: confirmed,
      proposed: {
        sourceReference: sourceRef || '(Fonte não preenchida)',
        reason: reasonText || undefined,
        scores: proposedScores,
      },
    });
  }

  closePreview(): void {
    this.activeDiff.set(null);
    this.store.closePreview();
  }

  onSlotTeamChange(slotNo: 1 | 2, teamId: string): void {
    const team = this.teams().find((t) => t.teamId === teamId);
    if (!team) return;
    if (slotNo === 1) {
      this.eventForm.patchValue({ slot1Label: team.name });
    } else {
      this.eventForm.patchValue({ slot2Label: team.name });
    }
  }

  statusDisplay(status?: string | null): { label: string; icon: string; cssClass: string } {
    switch (status) {
      case 'RASCUNHO':
        return { label: 'Rascunho', icon: '📝', cssClass: 'status-chip--draft' };
      case 'PUBLICADO':
        return { label: 'Publicado', icon: '✅', cssClass: 'status-chip--published' };
      case 'SUSPENSO':
        return { label: 'Suspenso', icon: '⏸️', cssClass: 'status-chip--suspended' };
      case 'ADIADO':
        return { label: 'Adiado', icon: '⏳', cssClass: 'status-chip--postponed' };
      case 'CANCELADO':
        return { label: 'Cancelado', icon: '🛑', cssClass: 'status-chip--cancelled' };
      case 'INFORMADO':
        return { label: 'Informado', icon: '📋', cssClass: 'status-chip--draft' };
      case 'AGUARDANDO_REVISAO':
        return { label: 'Aguardando revisão', icon: '🔍', cssClass: 'status-chip--review' };
      case 'CONFIRMADO':
        return { label: 'Confirmado', icon: '🏆', cssClass: 'status-chip--confirmed' };
      case 'CORRIGIDO':
        return { label: 'Corrigido', icon: '🔄', cssClass: 'status-chip--corrected' };
      default:
        return { label: status || 'Indefinido', icon: 'ℹ️', cssClass: '' };
    }
  }

  getSlotLabel(event: SportsEventDetail | null, slotNo: number): string {
    if (!event) return `Participante ${slotNo}`;
    const slot = event.slots.find((s) => s.slotNo === slotNo);
    return slot?.displayLabel || `Participante ${slotNo}`;
  }

  get competitionId(): string {
    return this.scope.selectedCompetitionId() ?? '';
  }

  private resetForms(): void {
    this.activeModal.set(null);
    this.activeDiff.set(null);
    this.eventForm.reset({
      title: '',
      format: 'HEAD_TO_HEAD',
      sportId: '',
      categoryId: '',
      phaseId: '',
      scheduledAt: '',
      zoneId: 'America/Sao_Paulo',
      venueState: 'LOCAL_DEFINIDO',
      venueId: '',
      venueSpaceId: '',
      sourceReference: '',
      slot1Label: '',
      slot1TeamId: '',
      slot2Label: '',
      slot2TeamId: '',
    });
    this.resultForm.reset({ sourceReference: '', slot1Score: null, slot2Score: null });
    this.correctionForm.reset({ reason: '', sourceReference: '', slot1Score: null, slot2Score: null });
    this.suspendForm.reset({ reason: '' });
    this.postponeForm.reset({ scheduledAt: '', zoneId: 'America/Sao_Paulo', reason: '' });
    this.cancelForm.reset({ reason: '' });
    this.store.clearMessages();
  }

  private hasDirtyForm(): boolean {
    return (
      this.eventForm.dirty ||
      this.resultForm.dirty ||
      this.correctionForm.dirty ||
      this.postponeForm.dirty ||
      this.cancelForm.dirty
    );
  }

  private confirm(message: string): boolean {
    return typeof window === 'undefined' || typeof window.confirm !== 'function' ? false : window.confirm(message);
  }

  private async syncQueryParams(params: Record<string, string | null>): Promise<void> {
    if (this.router) {
      await this.router.navigate([], {
        relativeTo: this.route ?? undefined,
        queryParams: params,
        queryParamsHandling: 'merge',
      });
    }
  }
}
