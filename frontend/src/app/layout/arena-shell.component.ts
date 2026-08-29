import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-arena-shell',
  standalone: true,
  imports: [RouterLink, RouterOutlet],
  templateUrl: './arena-shell.component.html',
  styleUrl: './arena-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ArenaShellComponent {
}
