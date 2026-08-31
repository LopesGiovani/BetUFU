import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { SportsRosterMemberRequest } from '../../../../api/generated/model/sports-roster-member-request';
import { SportsRosterMemberItem } from '../../../../api/generated/model/sports-roster-member-item';
import { SportsRosterSnapshot, SportsRostersStore } from './sports-rosters.store';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsOrganizationsApiAdapter } from '../organizations/sports-organizations-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { RosterViewComponent } from '../../../../shared/sports/roster-view.component';

@Component({
  selector: 'app-sports-rosters-page',
  standalone: true,
  imports: [ReactiveFormsModule, RosterViewComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="rosters-page" aria-labelledby="rosters-title">
      <header class="page-header">
        <div><span class="eyebrow">Console operacional · O4</span><h1 id="rosters-title">Elencos esportivos</h1><p>Rascunhos por equipe, snapshots publicados e correções versionadas.</p></div>
        <a class="arena-link" href="/">Voltar para a Arena</a>
      </header>

      @if (scope.isLoading() || teamsLoading()) {
        <article class="state-card" role="status"><h2>Carregando equipes autorizadas</h2><p>O servidor está confirmando o escopo antes de abrir o elenco.</p></article>
      } @else if (scope.isDenied() || scope.isEmpty()) {
        <article class="state-card state-card--error" role="alert"><h2>Nenhuma competição autorizada</h2><p>Sem uma competição retornada pelo servidor, nenhum elenco privado foi aberto.</p></article>
      } @else if (scope.isError() || teamsError()) {
        <article class="state-card state-card--error" role="alert"><h2>Não foi possível carregar as equipes</h2><p>{{ scope.errorMessage() || teamsError() }}</p><button type="button" class="button button--primary" (click)="retry()">Tentar novamente</button></article>
      } @else {
        <section class="scope-panel" aria-labelledby="team-title">
          <div><span class="eyebrow">Escopo validado</span><h2 id="team-title">Equipe</h2><p>Selecione somente uma equipe devolvida pelo catálogo autorizado.</p></div>
          <label>Equipe
            <select [value]="selectedTeamId()" (change)="changeTeam($any($event.target).value)">
              <option value="">Selecione uma equipe</option>
              @for (team of teams(); track team.teamId) { <option [value]="team.teamId">{{ team.name }}</option> }
            </select>
          </label>
        </section>

        @if (store.isLoading()) { <article class="state-card" role="status"><h2>Carregando elenco</h2><p>Consultando rascunho e snapshot publicado.</p></article> }
        @else if (store.isDenied()) { <article class="state-card state-card--error" role="alert"><h2>Acesso encerrado</h2><p>{{ store.errorMessage() }}</p></article> }
        @else if (store.isError()) { <article class="state-card state-card--error" role="alert"><h2>Elenco indisponível</h2><p>{{ store.errorMessage() }}</p><button type="button" class="button button--primary" (click)="reloadRoster()">Tentar carregar elenco</button></article> }
        @else if (store.draft() || store.publication()) {
          @if (store.conflictMessage()) { <div class="notice notice--conflict" role="alert">{{ store.conflictMessage() }}</div> }
          @if (store.feedbackMessage()) { <div class="notice notice--success" role="status" aria-live="polite">{{ store.feedbackMessage() }}</div> }
          <div class="workspace-grid">
            <article class="panel-card">
              <header class="card-header"><div><span class="card-code">RASCUNHO</span><h2>Editar elenco</h2></div><span class="status-chip">{{ store.draft()?.status || 'Sem rascunho' }} · v{{ store.draft()?.lockVersion ?? '-' }}</span></header>
              @if (members().length === 0) { <div class="empty-copy"><h3>Nenhum atleta no elenco</h3><p>O elenco pode ser salvo vazio enquanto a equipe ainda está sendo organizada.</p></div> }
              @else { <ul class="member-editor">@for (member of members(); track member.athleteId; let index = $index) { <li><div><strong>{{ member.athleteName }}</strong><span>Atleta selecionado pelo catálogo</span></div><label>Camisa<input type="number" min="1" max="99" [disabled]="store.publication() !== null" [value]="member.jerseyNumber ?? ''" (input)="changeJersey(index, $any($event.target).value)"></label><label>Posição<input [disabled]="store.publication() !== null" [value]="member.positionName ?? ''" (input)="changePosition(index, $any($event.target).value)"></label></li>}</ul> }
              <div class="form-actions"><button type="button" class="button button--secondary" (click)="previewDraft()">Pré-visualizar</button>@if (store.publication()) { <span class="form-note">Elenco publicado em leitura. Use a correção versionada ao lado.</span> } @else { <button type="button" class="button button--primary" [disabled]="store.isMutating()" (click)="saveDraft()">{{ store.isMutating() ? 'Salvando…' : 'Salvar rascunho' }}</button> }</div>
            </article>
            <article class="panel-card"><header class="card-header"><div><span class="card-code">PUBLICAÇÃO</span><h2>Snapshot imutável</h2></div></header><p class="form-note">Publicar cria uma versão histórica. Depois disso, qualquer ajuste exige motivo e uma nova versão.</p>@if (store.publication(); as publication) { <dl><div><dt>Versão publicada</dt><dd>v{{ publication.versionNo }}</dd></div><div><dt>Publicado em</dt><dd>{{ publication.publishedAt }}</dd></div></dl><button type="button" class="button button--secondary" (click)="previewPublication()">Ver snapshot</button><form [formGroup]="correctionForm" (submit)="correct(); $event.preventDefault()"><label>Motivo da correção<textarea formControlName="reason" rows="3"></textarea></label><button type="submit" class="button button--primary" [disabled]="correctionForm.invalid || store.isMutating()">Corrigir como nova versão</button></form> } @else { <div class="empty-copy"><h3>Ainda não publicado</h3><p>Confirme o rascunho quando a lista estiver pronta.</p><button type="button" class="button button--primary" [disabled]="store.isMutating()" (click)="publish()">Publicar elenco</button></div> }</article>
          </div>
          @if (store.preview(); as preview) { <app-roster-view [snapshot]="preview" /><button type="button" class="text-button" (click)="store.closePreview()">Fechar pré-visualização</button> }
        } @else { <article class="state-card"><h2>Selecione uma equipe</h2><p>O rascunho e o snapshot aparecem depois que o servidor confirmar a equipe.</p></article> }
      }
    </section>
  `,
  styles: [`
    :host { display: block; color: #fff4f4; }
    .rosters-page { display: grid; gap: 24px; max-width: 1240px; margin: 0 auto; padding: 32px 16px; }
    .page-header, .card-header, .scope-panel, .form-actions { display: flex; justify-content: space-between; align-items: start; gap: 16px; }
    .eyebrow, .card-code { color: #ffb4b8; font-size: .75rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
    h1, h2, h3, p { margin: 0; } h1, h2, h3 { font-family: 'Barlow Condensed', sans-serif; } h1 { margin-top: 4px; font-size: 2.6rem; } h2 { margin-top: 4px; font-size: 1.7rem; } p, .form-note, .empty-copy p { color: #cdbfc5; line-height: 1.5; }
    .arena-link, .text-button { color: #ffb4b8; } .arena-link { align-self: center; }
    .scope-panel, .panel-card, .state-card { padding: 24px; background: #171018; border: 1px solid rgba(255,244,244,.18); border-radius: 16px; box-shadow: 0 18px 52px rgba(9,8,13,.36); }
    .scope-panel { align-items: end; } .scope-panel label, label { display: grid; gap: 8px; color: #cdbfc5; font-size: .875rem; font-weight: 600; } select, input, textarea { min-height: 44px; padding: 8px 16px; border: 1px solid rgba(255,244,244,.18); border-radius: 16px; background: #120b12; color: #fff4f4; font: inherit; } textarea { min-height: 88px; }
    select:focus, input:focus, textarea:focus, button:focus-visible { outline: 3px solid #ffd2d4; outline-offset: 3px; }
    .state-card { display: grid; gap: 8px; } .state-card--error, .notice--conflict { border-color: #ff7a82; } .workspace-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(280px, .75fr); gap: 24px; } .panel-card { display: grid; gap: 16px; min-width: 0; }
    .status-chip { padding: 4px 8px; border: 1px solid #72d6a0; border-radius: 999px; color: #72d6a0; font-size: .75rem; font-weight: 700; white-space: nowrap; } .member-editor { display: grid; gap: 12px; padding: 0; margin: 0; list-style: none; } .member-editor li { display: grid; grid-template-columns: minmax(0, 1fr) 100px minmax(120px, .7fr); gap: 12px; align-items: end; padding-top: 12px; border-top: 1px solid rgba(255,244,244,.18); } .member-editor span { display: block; margin-top: 4px; color: #cdbfc5; font-size: .875rem; } .form-actions { justify-content: end; align-items: center; } .button, .text-button { min-height: 44px; padding: 8px 16px; border-radius: 16px; font: inherit; font-weight: 700; cursor: pointer; } .button--primary { border: 0; background: #ff626b; color: #09080d; } .button--secondary, .text-button { border: 1px solid rgba(255,244,244,.18); background: transparent; color: #fff4f4; } .text-button { border: 0; color: #ffb4b8; } button:disabled { opacity: .55; cursor: not-allowed; } .notice { padding: 16px; border: 1px solid; border-radius: 16px; } .notice--success { border-color: #72d6a0; color: #72d6a0; } dl { display: grid; gap: 12px; margin: 0; } dt { color: #cdbfc5; font-size: .75rem; text-transform: uppercase; } dd { margin: 0; } .empty-copy { display: grid; gap: 8px; }
    @media (max-width: 899px) { .workspace-grid { grid-template-columns: 1fr; } } @media (max-width: 599px) { .page-header, .scope-panel, .card-header, .form-actions { flex-direction: column; align-items: stretch; } .member-editor li { grid-template-columns: 1fr 1fr; } .member-editor li > div { grid-column: 1 / -1; } .button { width: 100%; } }
  `],
})
export class SportsRostersPageComponent implements OnInit {
  readonly scope = inject(SportsScopeStore);
  readonly store = inject(SportsRostersStore);
  private readonly organizationsAdapter = inject(SportsOrganizationsApiAdapter);
  private readonly route = inject(ActivatedRoute, { optional: true });
  private readonly router = inject(Router, { optional: true });

  readonly teams = signal<readonly SportsTeamSummary[]>(Object.freeze([]));
  readonly teamsLoading = signal(false);
  readonly teamsError = signal<string | null>(null);
  readonly selectedTeamId = signal('');
  readonly members = signal<readonly SportsRosterMemberItem[]>(Object.freeze([]));
  readonly correctionForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(1024)] }),
  });

  async ngOnInit(): Promise<void> {
    await this.scope.load(this.route?.snapshot.queryParamMap.get('competitionId'));
    await this.loadTeams();
  }

  async retry(): Promise<void> {
    await this.scope.load(this.route?.snapshot.queryParamMap.get('competitionId'));
    await this.loadTeams();
  }

  async loadTeams(): Promise<void> {
    const competitionId = this.scope.selectedCompetitionId();
    if (!competitionId) return;
    this.teamsLoading.set(true);
    this.teamsError.set(null);
    try {
      const teams = await firstValueFrom(this.organizationsAdapter.fetchTeams(competitionId));
      this.teams.set(teams);
      const requestedTeamId = this.route?.snapshot.queryParamMap.get('teamId');
      const firstTeam = teams.find((team) => team.teamId === requestedTeamId) ?? teams[0];
      if (firstTeam) await this.changeTeam(firstTeam.teamId);
    } catch {
      this.teamsError.set('As equipes desta competicao nao estao disponiveis para abrir elencos.');
    } finally {
      this.teamsLoading.set(false);
    }
  }

  async changeTeam(teamId: string): Promise<void> {
    const team = this.teams().find((candidate) => candidate.teamId === teamId);
    if (!team) return;
    this.selectedTeamId.set(team.teamId);
    this.store.selectTeam(team.teamId);
    await this.syncTeamUrl(team.teamId);
    await this.store.load(team.teamId);
    this.syncMembers();
  }

  async reloadRoster(): Promise<void> {
    await this.store.load(this.selectedTeamId());
    this.syncMembers();
  }

  changeJersey(index: number, value: string): void {
    this.updateMember(index, { jerseyNumber: value.trim() ? Number(value) : null });
  }

  changePosition(index: number, value: string): void {
    this.updateMember(index, { positionName: value });
  }

  async saveDraft(): Promise<void> {
    const saved = await this.store.updateDraft(this.requestMembers());
    if (saved) this.syncMembers();
  }

  async publish(): Promise<void> {
    if (!this.confirm('Publicar este elenco e criar um snapshot imutavel?')) return;
    await this.store.publish();
    this.syncMembers();
  }

  async correct(): Promise<void> {
    if (this.correctionForm.invalid) return;
    const saved = await this.store.correct(this.correctionForm.controls.reason.value, this.requestMembers());
    if (saved) this.correctionForm.reset({ reason: '' });
    this.syncMembers();
  }

  previewDraft(): void { this.store.previewSnapshot(this.store.draft()); }
  previewPublication(): void { this.store.previewSnapshot(this.store.publication()); }

  private syncMembers(): void {
    this.members.set((this.store.draft()?.members ?? []).map((member) => ({ ...member })));
  }

  private updateMember(index: number, patch: Partial<SportsRosterMemberItem>): void {
    this.members.update((members) => members.map((member, currentIndex) => currentIndex === index ? { ...member, ...patch } : member));
  }

  private requestMembers(): readonly SportsRosterMemberRequest[] {
    return this.members().map(({ athleteId, jerseyNumber, positionName }) => ({ athleteId, jerseyNumber, positionName }));
  }

  private confirm(message: string): boolean {
    return typeof window === 'undefined' || typeof window.confirm !== 'function' ? false : window.confirm(message);
  }

  private async syncTeamUrl(teamId: string): Promise<void> {
    if (this.router) await this.router.navigate([], { relativeTo: this.route ?? undefined, queryParams: { teamId } });
  }
}
