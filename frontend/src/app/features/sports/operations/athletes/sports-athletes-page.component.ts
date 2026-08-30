import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SportsAthleteSummary } from '../../../../api/generated/model/sports-athlete-summary';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsOrganizationsApiAdapter } from '../organizations/sports-organizations-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsAthletesStore } from './sports-athletes.store';

type ActiveForm = 'athlete' | null;
type PreviewItem = { kind: 'athlete'; name: string; status: string; scope: string; version: number };

@Component({
  selector: 'app-sports-athletes-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './sports-athletes-page.component.html',
  styleUrl: './sports-athletes-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsAthletesPageComponent implements OnInit {
  readonly scope = inject(SportsScopeStore);
  readonly store = inject(SportsAthletesStore);
  private readonly organizationsAdapter = inject(SportsOrganizationsApiAdapter);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });

  readonly teams = signal<readonly SportsTeamSummary[]>(Object.freeze([]));
  readonly teamsLoading = signal(false);
  readonly teamsError = signal<string | null>(null);
  readonly activeForm = signal<ActiveForm>(null);
  readonly editingAthlete = signal<SportsAthleteSummary | null>(null);
  readonly previewItem = signal<PreviewItem | null>(null);

  readonly athleteForm = new FormGroup({
    teamId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)] }),
    matricula: new FormControl('', { nonNullable: true }),
  });

  async ngOnInit(): Promise<void> {
    const requested = this.route?.snapshot.queryParamMap.get('competitionId');
    await this.scope.load(requested);
    if (this.scope.hasSelection()) await this.loadWorkspace();
  }

  async changeScope(competitionId: string): Promise<void> {
    if (!competitionId || competitionId === this.scope.selectedCompetitionId()) return;
    if (this.hasDirtyForm() && !this.confirm('Trocar de competicao e descartar alteracoes nao salvas?')) {
      return;
    }
    if (!this.scope.selectCompetition(competitionId)) return;
    this.resetForms();
    await this.syncScopeUrl(competitionId);
    await this.loadWorkspace();
  }

  async retry(): Promise<void> {
    await this.scope.load(this.route?.snapshot.queryParamMap.get('competitionId'));
    if (this.scope.hasSelection()) await this.loadWorkspace();
  }

  async loadWorkspace(): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;
    this.teamsLoading.set(true);
    this.teamsError.set(null);
    await Promise.all([
      this.store.load(),
      firstValueFrom(this.organizationsAdapter.fetchTeams(competitionId)).then(
        (teams) => this.teams.set(teams),
        () => this.teamsError.set('As equipes desta competicao nao estao disponiveis para associar ao cadastro do atleta.'),
      ),
    ]);
    this.teamsLoading.set(false);
    this.setDefaultTeamContext();
  }

  openAthleteForm(): void {
    this.resetForms();
    this.activeForm.set('athlete');
    this.setDefaultTeamContext();
  }

  editAthlete(athlete: SportsAthleteSummary): void {
    this.resetForms();
    this.activeForm.set('athlete');
    this.editingAthlete.set(athlete);
    this.athleteForm.setValue({
      teamId: '',
      name: athlete.name,
      slug: athlete.slug,
      matricula: '',
    });
  }

  closeForm(): void {
    this.resetForms();
  }

  previewAthlete(athlete: SportsAthleteSummary): void {
    this.previewItem.set({
      kind: 'athlete',
      name: athlete.name,
      status: this.statusLabel(athlete.status),
      scope: 'Atleta global',
      version: athlete.lockVersion,
    });
  }

  closePreview(): void {
    this.previewItem.set(null);
  }

  async saveAthlete(): Promise<void> {
    const current = this.editingAthlete();
    if (current) {
      if (this.athleteForm.controls.name.invalid || this.athleteForm.controls.slug.invalid) return;
      const name = this.athleteForm.controls.name.value;
      const slug = this.athleteForm.controls.slug.value;
      const saved = await this.store.updateAthlete(current.athleteId, current.lockVersion, name, slug);
      if (saved) this.closeForm();
      return;
    }

    if (this.athleteForm.invalid) return;
    const value = this.athleteForm.getRawValue();

    // Transient matricula extraction and immediate cleanup
    const rawMatricula = value.matricula?.trim() ? value.matricula.trim() : null;
    this.athleteForm.controls.matricula.setValue('', { emitEvent: false });
    this.athleteForm.controls.matricula.reset('', { emitEvent: false });

    const saved = await this.store.createAthlete(value.teamId, value.name, value.slug, rawMatricula);
    if (saved) {
      this.closeForm();
    }
  }

  async publishAthlete(athlete: SportsAthleteSummary): Promise<void> {
    if (athlete.status === 'PUBLICADO') return;
    if (!this.confirm(`Publicar o atleta ${athlete.name} na versao ${athlete.lockVersion}?`)) return;
    await this.store.publishAthlete(athlete.athleteId, athlete.lockVersion);
  }

  statusLabel(status: string): string {
    return status === 'PUBLICADO' ? 'Publicado' : 'Rascunho';
  }

  get competitionId(): string {
    return this.scope.selectedCompetitionId() ?? '';
  }

  private setDefaultTeamContext(): void {
    const current = this.editingAthlete();
    if (current) return;
    const teamId = this.teams()[0]?.teamId ?? '';
    this.athleteForm.patchValue({ teamId }, { emitEvent: false });
  }

  private resetForms(): void {
    this.activeForm.set(null);
    this.editingAthlete.set(null);
    this.athleteForm.reset({ teamId: '', name: '', slug: '', matricula: '' });
    this.store.clearMessages();
  }

  private hasDirtyForm(): boolean {
    return this.athleteForm.dirty;
  }

  private confirm(message: string): boolean {
    return typeof window === 'undefined' || typeof window.confirm !== 'function' ? false : window.confirm(message);
  }

  private async syncScopeUrl(competitionId: string): Promise<void> {
    if (this.router) await this.router.navigate([], { relativeTo: this.route ?? undefined, queryParams: { competitionId } });
  }
}
