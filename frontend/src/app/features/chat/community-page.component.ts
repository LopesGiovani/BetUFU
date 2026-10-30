import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ChatPanelComponent } from './chat-panel.component';

@Component({
  selector: 'app-community-page',
  standalone: true,
  imports: [ChatPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="community" aria-labelledby="community-title">
      <p class="community-kicker">Arena / Comunidade</p>
      <h1 id="community-title">Comunidade</h1>
      <p>Uma conversa sobre os jogos universitarios.</p>
      <app-chat-panel [automaticRules]="true" />
    </section>
  `,
  styles: `
    .community {
      width: var(--bt-container);
      margin-inline: auto;
      padding-block: var(--bt-space-8) var(--bt-space-16);
      color: var(--bt-text);
    }
    h1 {
      margin-block: var(--bt-space-2);
      font:
        600 var(--bt-type-heading)/1.2 'Barlow Condensed',
        sans-serif;
    }
    .community-kicker {
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      text-transform: uppercase;
    }
    p {
      overflow-wrap: anywhere;
    }
  `,
})
export class CommunityPageComponent {}
