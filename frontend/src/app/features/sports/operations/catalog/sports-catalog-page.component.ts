import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SportsCatalogTree } from '../../../../api/generated/model/sports-catalog-tree';
import { SportsCategorySummary } from '../../../../api/generated/model/sports-category-summary';
import { SportsEditionSummary } from '../../../../api/generated/model/sports-edition-summary';
import { SportsPhaseSummary } from '../../../../api/generated/model/sports-phase-summary';
import { SportsSportSummary } from '../../../../api/generated/model/sports-sport-summary';
import { SportsVenueSpaceSummary } from '../../../../api/generated/model/sports-venue-space-summary';
import { SportsVenueSummary } from '../../../../api/generated/model/sports-venue-summary';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsCatalogApiAdapter } from './sports-catalog-api.adapter';

type CatalogForm =
  'competition' | 'sport' | 'edition' | 'category' | 'phase' | 'venue' | 'space' | null;

@Component({
  selector: 'app-sports-catalog-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './sports-catalog-page.component.html',
  styleUrl: './sports-catalog-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsCatalogPageComponent implements OnInit {
  readonly scope = inject(SportsScopeStore);
  private readonly adapter = inject(SportsCatalogApiAdapter);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });

  readonly tree = signal<SportsCatalogTree | null>(null);
  readonly treeLoading = signal(false);
  readonly treeError = signal<string | null>(null);
  readonly conflictMessage = signal<string | null>(null);
  readonly feedbackMessage = signal<string | null>(null);
  readonly activeForm = signal<CatalogForm>(null);
  readonly searchTerm = signal('');
  readonly selectedEditionId = signal('');
  readonly selectedCategoryId = signal('');
  readonly mutating = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly editingVersion = signal<number | null>(null);

  readonly scopeForm = new FormGroup({
    competitionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });
  readonly nameSlugForm = new FormGroup({
    name: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)],
    }),
  });
  readonly editionForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)],
    }),
    startDate: new FormControl('', { nonNullable: true }),
    endDate: new FormControl('', { nonNullable: true }),
  });
  readonly categoryForm = new FormGroup({
    editionId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)],
    }),
  });
  readonly phaseForm = new FormGroup({
    categoryId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    slug: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[a-z0-9]+(?:-[a-z0-9]+)*$/u)],
    }),
  });
  readonly venueForm = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    address: new FormControl('', { nonNullable: true }),
    mapUrl: new FormControl('', { nonNullable: true }),
    notes: new FormControl('', { nonNullable: true }),
  });
  readonly spaceForm = new FormGroup({
    venueId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    notes: new FormControl('', { nonNullable: true }),
  });

  readonly filteredSports = computed(() => this.filter(this.tree()?.sports ?? []));
  readonly filteredEditions = computed(() => this.filter(this.tree()?.editions ?? []));
  readonly filteredCategories = computed(() => this.filter(this.tree()?.categories ?? []));
  readonly filteredPhases = computed(() => this.filter(this.tree()?.phases ?? []));
  readonly filteredVenues = computed(() => this.filter(this.tree()?.venues ?? []));
  readonly categoriesForEdition = computed(() => {
    const editionId = this.selectedEditionId();
    return (this.tree()?.categories ?? []).filter(
      (category) => !editionId || category.editionId === editionId,
    );
  });
  readonly phasesForCategory = computed(() => {
    const categoryId = this.selectedCategoryId();
    return (this.tree()?.phases ?? []).filter(
      (phase) => !categoryId || phase.categoryId === categoryId,
    );
  });

  async ngOnInit(): Promise<void> {
    const requested = this.route?.snapshot.queryParamMap.get('competitionId');
    await this.scope.load(requested);
    this.scopeForm.controls.competitionId.setValue(this.scope.selectedCompetitionId() ?? '');
    if (this.scope.hasSelection()) await this.loadTree();
  }

  async openSelectedCompetition(): Promise<void> {
    await this.changeScope(this.scopeForm.controls.competitionId.value);
  }

  async changeScope(competitionId: string): Promise<void> {
    if (!competitionId || competitionId === this.scope.selectedCompetitionId()) return;
    if (
      this.hasDirtyForm() &&
      !window.confirm('Trocar de competicao e descartar alteracoes nao salvas?')
    ) {
      this.scopeForm.controls.competitionId.setValue(this.scope.selectedCompetitionId() ?? '');
      return;
    }
    if (!this.scope.selectCompetition(competitionId)) return;
    this.resetForms();
    await this.syncScopeUrl(competitionId);
    await this.loadTree();
  }

  async retryScope(): Promise<void> {
    await this.scope.load(this.route?.snapshot.queryParamMap.get('competitionId'));
    this.scopeForm.controls.competitionId.setValue(this.scope.selectedCompetitionId() ?? '');
    if (this.scope.hasSelection()) await this.loadTree();
  }

  async loadTree(): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;
    this.treeLoading.set(true);
    this.treeError.set(null);
    this.conflictMessage.set(null);
    try {
      this.tree.set(await firstValueFrom(this.adapter.fetchTree(competitionId)));
      const firstEdition = this.tree()?.editions[0]?.editionId ?? '';
      this.selectedEditionId.set(firstEdition);
      this.selectedCategoryId.set(
        this.tree()?.categories.find((item) => item.editionId === firstEdition)?.categoryId ?? '',
      );
    } catch (error: unknown) {
      this.tree.set(null);
      this.treeError.set(this.errorMessage(error, 'Nao foi possivel carregar o catalogo'));
    } finally {
      this.treeLoading.set(false);
    }
  }

  openForm(form: Exclude<CatalogForm, null>): void {
    this.resetForms();
    this.activeForm.set(form);
    if (form === 'category')
      this.categoryForm.controls.editionId.setValue(this.selectedEditionId());
    if (form === 'phase') this.phaseForm.controls.categoryId.setValue(this.selectedCategoryId());
  }

  closeForm(): void {
    this.resetForms();
  }

  editSport(item: SportsSportSummary): void {
    this.openNamedEdit('sport', item.sportId, item.lockVersion, item.name, item.slug);
  }

  editEdition(item: SportsEditionSummary): void {
    this.resetForms();
    this.activeForm.set('edition');
    this.editingId.set(item.editionId);
    this.editingVersion.set(item.lockVersion);
    this.editionForm.setValue({
      name: item.name,
      slug: item.slug,
      startDate: item.startDate ?? '',
      endDate: item.endDate ?? '',
    });
  }

  editCategory(item: SportsCategorySummary): void {
    this.resetForms();
    this.activeForm.set('category');
    this.editingId.set(item.categoryId);
    this.editingVersion.set(item.lockVersion);
    this.categoryForm.setValue({ editionId: item.editionId, name: item.name, slug: item.slug });
  }

  editPhase(item: SportsPhaseSummary): void {
    this.resetForms();
    this.activeForm.set('phase');
    this.editingId.set(item.phaseId);
    this.editingVersion.set(item.lockVersion);
    this.phaseForm.setValue({ categoryId: item.categoryId, name: item.name, slug: item.slug });
  }

  editVenue(item: SportsVenueSummary): void {
    this.resetForms();
    this.activeForm.set('venue');
    this.editingId.set(item.venueId);
    this.editingVersion.set(item.lockVersion);
    this.venueForm.setValue({
      name: item.name,
      address: item.address ?? '',
      mapUrl: item.mapUrl ?? '',
      notes: item.notes ?? '',
    });
  }

  editSpace(item: SportsVenueSpaceSummary): void {
    this.resetForms();
    this.activeForm.set('space');
    this.editingId.set(item.venueSpaceId);
    this.editingVersion.set(item.lockVersion);
    this.spaceForm.setValue({ venueId: item.venueId, name: item.name, notes: item.notes ?? '' });
  }

  async saveCompetition(): Promise<void> {
    if (this.nameSlugForm.invalid) return;
    await this.mutate(async () => {
      const created = await firstValueFrom(
        this.adapter.createCompetition(
          this.nameSlugForm.controls.name.value,
          this.nameSlugForm.controls.slug.value,
        ),
      );
      await this.scope.load(created.competitionId);
      this.scopeForm.controls.competitionId.setValue(created.competitionId);
      await this.syncScopeUrl(created.competitionId);
      await this.loadTree();
    }, 'Competicao cadastrada.');
  }

  async saveNamed(): Promise<void> {
    if (this.nameSlugForm.invalid || !this.scope.selectedCompetitionId()) return;
    const name = this.nameSlugForm.controls.name.value;
    const slug = this.nameSlugForm.controls.slug.value;
    const form = this.activeForm();
    await this.mutate(async () => {
      if (form === 'sport') {
        if (this.editingId() && this.editingVersion() !== null) {
          await firstValueFrom(
            this.adapter.updateSport({
              sportId: this.editingId()!,
              expectedLockVersion: this.editingVersion()!,
              sportsNameSlugRequest: { name, slug },
            }),
          );
        } else {
          await firstValueFrom(
            this.adapter.createSport(this.scope.selectedCompetitionId()!, name, slug),
          );
        }
      }
      await this.loadTree();
    }, 'Catalogo atualizado.');
  }

  async saveEdition(): Promise<void> {
    if (this.editionForm.invalid || !this.scope.selectedCompetitionId()) return;
    const value = this.editionForm.getRawValue();
    await this.mutate(async () => {
      if (this.editingId() && this.editingVersion() !== null) {
        await firstValueFrom(
          this.adapter.updateEdition({
            editionId: this.editingId()!,
            sportsCatalogEditionRequest: {
              name: value.name,
              slug: value.slug,
              startDate: value.startDate || null,
              endDate: value.endDate || null,
              expectedLockVersion: this.editingVersion(),
            },
          }),
        );
      } else {
        await firstValueFrom(
          this.adapter.createEdition(this.scope.selectedCompetitionId()!, {
            name: value.name,
            slug: value.slug,
            startDate: value.startDate || null,
            endDate: value.endDate || null,
          }),
        );
      }
      await this.loadTree();
    }, 'Edicao salva.');
  }

  async saveCategory(): Promise<void> {
    if (this.categoryForm.invalid) return;
    const value = this.categoryForm.getRawValue();
    await this.mutate(async () => {
      if (this.editingId() && this.editingVersion() !== null) {
        await firstValueFrom(
          this.adapter.updateCategory({
            categoryId: this.editingId()!,
            expectedLockVersion: this.editingVersion()!,
            sportsNameSlugRequest: { name: value.name, slug: value.slug },
          }),
        );
      } else {
        await firstValueFrom(this.adapter.createCategory(value.editionId, value.name, value.slug));
      }
      await this.loadTree();
    }, 'Categoria salva.');
  }

  async savePhase(): Promise<void> {
    if (this.phaseForm.invalid) return;
    const value = this.phaseForm.getRawValue();
    await this.mutate(async () => {
      if (this.editingId() && this.editingVersion() !== null) {
        await firstValueFrom(
          this.adapter.updatePhase({
            phaseId: this.editingId()!,
            expectedLockVersion: this.editingVersion()!,
            sportsNameSlugRequest: { name: value.name, slug: value.slug },
          }),
        );
      } else {
        await firstValueFrom(this.adapter.createPhase(value.categoryId, value.name, value.slug));
      }
      await this.loadTree();
    }, 'Fase salva.');
  }

  async saveVenue(): Promise<void> {
    if (this.venueForm.invalid || !this.scope.selectedCompetitionId()) return;
    const value = this.venueForm.getRawValue();
    await this.mutate(async () => {
      if (this.editingId() && this.editingVersion() !== null) {
        await firstValueFrom(
          this.adapter.updateVenue({
            venueId: this.editingId()!,
            sportsCatalogVenueRequest: {
              name: value.name,
              address: value.address || null,
              mapUrl: value.mapUrl || null,
              notes: value.notes || null,
              expectedLockVersion: this.editingVersion(),
            },
          }),
        );
      } else {
        await firstValueFrom(
          this.adapter.createVenue(
            this.scope.selectedCompetitionId()!,
            value.name,
            value.address || undefined,
            value.mapUrl || undefined,
            value.notes || undefined,
          ),
        );
      }
      await this.loadTree();
    }, 'Local salvo.');
  }

  async saveSpace(): Promise<void> {
    if (this.spaceForm.invalid) return;
    const value = this.spaceForm.getRawValue();
    await this.mutate(async () => {
      if (this.editingId() && this.editingVersion() !== null) {
        await firstValueFrom(
          this.adapter.updateVenueSpace({
            spaceId: this.editingId()!,
            sportsCatalogVenueSpaceRequest: {
              name: value.name,
              notes: value.notes || null,
              expectedLockVersion: this.editingVersion(),
            },
          }),
        );
      } else {
        await firstValueFrom(
          this.adapter.createVenueSpace(value.venueId, value.name, value.notes || undefined),
        );
      }
      await this.loadTree();
    }, 'Espaco interno salvo.');
  }

  editionName(editionId: string): string {
    return (
      this.tree()?.editions.find((edition) => edition.editionId === editionId)?.name ??
      'Edicao indisponivel'
    );
  }

  categoryName(categoryId: string): string {
    return (
      this.tree()?.categories.find((category) => category.categoryId === categoryId)?.name ??
      'Categoria indisponivel'
    );
  }

  private openNamedEdit(
    form: 'sport',
    id: string,
    version: number,
    name: string,
    slug: string,
  ): void {
    this.resetForms();
    this.activeForm.set(form);
    this.editingId.set(id);
    this.editingVersion.set(version);
    this.nameSlugForm.setValue({ name, slug });
  }

  private async mutate(action: () => Promise<void>, successMessage: string): Promise<void> {
    this.mutating.set(true);
    this.conflictMessage.set(null);
    this.feedbackMessage.set(null);
    try {
      await action();
      this.feedbackMessage.set(successMessage);
      this.resetForms();
    } catch (error: unknown) {
      if (this.adapter.isConflict(error)) {
        this.conflictMessage.set(
          'Este registro foi alterado por outra pessoa. Recarregue para comparar a versao atual antes de tentar novamente.',
        );
      } else {
        this.treeError.set(this.errorMessage(error, 'Nao foi possivel salvar o catalogo'));
      }
    } finally {
      this.mutating.set(false);
    }
  }

  private resetForms(): void {
    this.activeForm.set(null);
    this.editingId.set(null);
    this.editingVersion.set(null);
    this.nameSlugForm.reset({ name: '', slug: '' });
    this.editionForm.reset({ name: '', slug: '', startDate: '', endDate: '' });
    this.categoryForm.reset({ editionId: this.selectedEditionId(), name: '', slug: '' });
    this.phaseForm.reset({ categoryId: this.selectedCategoryId(), name: '', slug: '' });
    this.venueForm.reset({ name: '', address: '', mapUrl: '', notes: '' });
    this.spaceForm.reset({ venueId: '', name: '', notes: '' });
  }

  private forms(): readonly FormGroup[] {
    return [
      this.nameSlugForm,
      this.editionForm,
      this.categoryForm,
      this.phaseForm,
      this.venueForm,
      this.spaceForm,
    ];
  }

  private hasDirtyForm(): boolean {
    return this.forms().some((form) => form.dirty);
  }

  private filter<T extends { name: string }>(items: readonly T[]): readonly T[] {
    const term = this.searchTerm().trim().toLocaleLowerCase('pt-BR');
    return term
      ? items.filter((item) => item.name.toLocaleLowerCase('pt-BR').includes(term))
      : items;
  }

  private async syncScopeUrl(competitionId: string): Promise<void> {
    if (!this.router || !this.route) return;
    await this.router.navigate([], { relativeTo: this.route, queryParams: { competitionId } });
  }

  private errorMessage(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse && error.status === 403)
      return 'Voce nao tem acesso a esta area.';
    const correlationId =
      error instanceof HttpErrorResponse && error.error && typeof error.error === 'object'
        ? (error.error as Record<string, unknown>)['correlationId']
        : null;
    return typeof correlationId === 'string' && correlationId.trim()
      ? `${fallback}. Codigo de referencia: ${correlationId.trim()}.`
      : `${fallback}. Tente novamente.`;
  }
}
