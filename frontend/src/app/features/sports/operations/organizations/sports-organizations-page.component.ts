import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SportsCatalogTree } from '../../../../api/generated/model/sports-catalog-tree';
import { SportsOrganizationSummary } from '../../../../api/generated/model/sports-organization-summary';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsOrganizationsStore } from './sports-organizations.store';

type ActiveForm = 'organization' | 'team' | null;
type PreviewItem = { kind: 'organization' | 'team'; name: string; status: string; scope: string; version: number };

@Component({
  selector: 'app-sports-organizations-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './sports-organizations-page.component.html',
  styleUrl: './sports-organizations-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsOrganizationsPageComponent implements OnInit {
  readonly scope = inject(SportsScopeStore);
  readonly store = inject(SportsOrganizationsStore);
  private readonly catalogAdapter = inject(SportsCatalogApiAdapter);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });

  readonly tree = signal<SportsCatalogTree | null>(null);
  readonly treeError = signal<string | null>(null);
  readonly treeLoading = signal(false);
  readonly activeForm = signal<ActiveForm>(null);
  readonly editingOrganization = signal<SportsOrganizationSummary | null>(null);
  readonly editingTeam = signal<SportsTeamSummary | null>(null);
  readonly previewItem = signal<PreviewItem | null>(null);

  readonly organizationForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)] }),
  });
  readonly teamForm = new FormGroup({
    organizationId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    editionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    sportId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    categoryId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    slug: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)] }),
  });

  readonly selectedEdition = computed(() => this.tree()?.editions.find(
    (edition) => edition.editionId === this.teamForm.controls.editionId.value,
  ) ?? null);
  readonly categoriesForEdition = computed(() => {
    const editionId = this.teamForm.controls.editionId.value;
    return (this.tree()?.categories ?? []).filter((category) => !editionId || category.editionId === editionId);
  });
  readonly selectedOrganization = computed(() => this.store.organizations().find(
    (organization) => organization.organizationId === this.teamForm.controls.organizationId.value,
  ) ?? null);

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
    this.treeLoading.set(true);
    this.treeError.set(null);
    await Promise.all([
      this.store.load(competitionId),
      firstValueFrom(this.catalogAdapter.fetchTree(competitionId)).then(
        (tree) => this.tree.set(tree),
        () => this.treeError.set('Os registros esportivos desta competicao nao estao disponiveis para montar a equipe.'),
      ),
    ]);
    this.treeLoading.set(false);
    this.setDefaultTeamContext();
  }

  openOrganizationForm(): void {
    this.resetForms();
    this.activeForm.set('organization');
  }

  openTeamForm(): void {
    this.resetForms();
    this.activeForm.set('team');
    this.setDefaultTeamContext();
  }

  editOrganization(organization: SportsOrganizationSummary): void {
    this.resetForms();
    this.activeForm.set('organization');
    this.editingOrganization.set(organization);
    this.organizationForm.setValue({ name: organization.name, slug: organization.slug });
  }

  editTeam(team: SportsTeamSummary): void {
    this.resetForms();
    this.activeForm.set('team');
    this.editingTeam.set(team);
    this.teamForm.setValue({
      organizationId: team.organizationId,
      editionId: team.editionId,
      sportId: team.sportId,
      categoryId: team.categoryId,
      name: team.name,
      slug: team.slug,
    });
  }

  closeForm(): void {
    this.resetForms();
  }

  previewOrganization(organization: SportsOrganizationSummary): void {
    this.previewItem.set({
      kind: 'organization',
      name: organization.name,
      status: this.statusLabel(organization.status),
      scope: 'Organizacao permanente',
      version: organization.lockVersion,
    });
  }

  previewTeam(team: SportsTeamSummary): void {
    this.previewItem.set({
      kind: 'team',
      name: team.name,
      status: this.statusLabel(team.status),
      scope: `${team.organizationName} / ${team.sportName} / ${team.categoryName}`,
      version: team.lockVersion,
    });
  }

  closePreview(): void {
    this.previewItem.set(null);
  }

  async saveOrganization(): Promise<void> {
    if (this.organizationForm.invalid) return;
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;
    const value = this.organizationForm.getRawValue();
    const current = this.editingOrganization();
    const saved = current
      ? await this.store.updateOrganization(current.organizationId, current.lockVersion, value.name, value.slug, competitionId)
      : await this.store.createOrganization(value.name, value.slug, competitionId);
    if (saved) this.closeForm();
  }

  async saveTeam(): Promise<void> {
    if (this.teamForm.invalid) return;
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;
    const value = this.teamForm.getRawValue();
    const current = this.editingTeam();
    const saved = current
      ? await this.store.updateTeam(current.teamId, current.lockVersion, value.name, value.slug, competitionId)
      : await this.store.createTeam(
        value.editionId, value.sportId, value.categoryId, value.organizationId,
        value.name, value.slug, competitionId,
      );
    if (saved) this.closeForm();
  }

  async publishOrganization(organization: SportsOrganizationSummary): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId || organization.status === 'PUBLICADO') return;
    if (!this.confirm(`Publicar a organizacao ${organization.name} na versao ${organization.lockVersion}?`)) return;
    await this.store.publishOrganization(organization.organizationId, organization.lockVersion, competitionId);
  }

  async publishTeam(team: SportsTeamSummary): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId || team.status === 'PUBLICADO') return;
    if (!this.confirm(`Publicar a equipe ${team.name} na versao ${team.lockVersion}?`)) return;
    await this.store.publishTeam(team.teamId, team.lockVersion, competitionId);
  }

  statusLabel(status: string): string {
    return status === 'PUBLICADO' ? 'Publicado' : 'Rascunho';
  }

  editionName(editionId: string): string {
    return this.tree()?.editions.find((edition) => edition.editionId === editionId)?.name ?? 'Edicao indisponivel';
  }

  get competitionId(): string {
    return this.scope.selectedCompetitionId() ?? '';
  }

  private setDefaultTeamContext(): void {
    const current = this.editingTeam();
    if (current) return;
    const tree = this.tree();
    const editionId = tree?.editions[0]?.editionId ?? '';
    const sportId = tree?.sports[0]?.sportId ?? '';
    const categoryId = tree?.categories.find((category) => category.editionId === editionId)?.categoryId ?? '';
    const organizationId = this.store.publishedOrganizations()[0]?.organizationId
      ?? this.store.organizations()[0]?.organizationId
      ?? '';
    this.teamForm.patchValue({ editionId, sportId, categoryId, organizationId }, { emitEvent: false });
  }

  private resetForms(): void {
    this.activeForm.set(null);
    this.editingOrganization.set(null);
    this.editingTeam.set(null);
    this.organizationForm.reset({ name: '', slug: '' });
    this.teamForm.reset({ organizationId: '', editionId: '', sportId: '', categoryId: '', name: '', slug: '' });
    this.store.clearMessages();
  }

  private hasDirtyForm(): boolean {
    return this.organizationForm.dirty || this.teamForm.dirty;
  }

  private confirm(message: string): boolean {
    return typeof window === 'undefined' || typeof window.confirm !== 'function' ? false : window.confirm(message);
  }

  private async syncScopeUrl(competitionId: string): Promise<void> {
    if (this.router) await this.router.navigate([], { relativeTo: this.route ?? undefined, queryParams: { competitionId } });
  }
}
