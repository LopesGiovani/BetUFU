import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { SportsRosterDraft } from '../../api/generated/model/sports-roster-draft';
import { SportsRosterPublication } from '../../api/generated/model/sports-roster-publication';

export type RosterViewSnapshot = SportsRosterDraft | SportsRosterPublication;

@Component({
  selector: 'app-roster-view',
  standalone: true,
  template: `
    @if (snapshot; as roster) {
      <section class="roster-view" aria-labelledby="roster-view-title">
        <header class="roster-header">
          <div>
            <span class="eyebrow">{{ isPublication(roster) ? 'Snapshot publicado' : 'Pré-visualização' }}</span>
            <h2 id="roster-view-title">{{ roster.teamName }}</h2>
            <p>{{ roster.competitionName }} · {{ roster.editionName }} · {{ roster.sportName }} · {{ roster.categoryName }}</p>
          </div>
          <span class="status-chip">{{ isPublication(roster) ? 'Publicado' : roster.status === 'PUBLICADO' ? 'Publicado' : 'Rascunho' }}</span>
        </header>
        <ul class="member-list" aria-label="Membros do elenco">
          @for (member of roster.members; track member.athleteId) {
            <li>
              <strong>{{ member.athleteName }}</strong>
              <span>{{ member.jerseyNumber ?? 'Sem camisa' }} · {{ member.positionName || 'Posição não informada' }}</span>
            </li>
          } @empty {
            <li class="empty-member">Nenhum atleta neste elenco.</li>
          }
        </ul>
        @if (isPublication(roster)) {
          <footer>Versão {{ roster.versionNo }} · Publicado em {{ roster.publishedAt }}</footer>
        } @else {
          <footer>Versão de edição {{ roster.lockVersion }}</footer>
        }
      </section>
    } @else {
      <section class="roster-view roster-view--empty" aria-label="Pré-visualização de elenco">
        <h2>Nenhum elenco selecionado</h2>
        <p>Selecione uma equipe para consultar o rascunho ou o snapshot publicado.</p>
      </section>
    }
  `,
  styles: [`
    :host { display: block; }
    .roster-view { display: grid; gap: 16px; padding: 24px; color: #fff4f4; background: #171018; border: 1px solid rgba(255,244,244,.18); border-radius: 16px; }
    .roster-view--empty { color: #cdbfc5; }
    .roster-header { display: flex; justify-content: space-between; gap: 16px; align-items: start; }
    .eyebrow { color: #ffb4b8; font-size: .75rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
    h2, p { margin: 0; }
    h2 { margin-top: 4px; font-family: 'Barlow Condensed', sans-serif; font-size: 1.8rem; }
    p, footer { color: #cdbfc5; line-height: 1.5; }
    .status-chip { padding: 4px 8px; border: 1px solid #72d6a0; border-radius: 999px; color: #72d6a0; font-size: .75rem; font-weight: 700; white-space: nowrap; }
    .member-list { display: grid; gap: 8px; padding: 0; margin: 0; list-style: none; }
    .member-list li { display: flex; justify-content: space-between; gap: 16px; padding: 12px 0; border-top: 1px solid rgba(255,244,244,.18); }
    .member-list span { color: #cdbfc5; }
    .empty-member { color: #f5c95c; }
    @media (max-width: 599px) { .roster-header, .member-list li { flex-direction: column; } }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RosterViewComponent {
  @Input() snapshot: RosterViewSnapshot | null = null;

  isPublication(snapshot: RosterViewSnapshot): snapshot is SportsRosterPublication {
    return 'publicationId' in snapshot;
  }
}
