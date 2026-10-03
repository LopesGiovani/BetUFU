import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { PercentPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { RankingStore } from './ranking.store';
@Component({
  selector: 'app-ranking-page',
  standalone: true,
  imports: [PercentPipe, RouterLink],
  providers: [RankingStore],
  templateUrl: './ranking-page.component.html',
  styleUrl: './ranking-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RankingPageComponent implements OnInit {
  readonly store = inject(RankingStore);
  ngOnInit() {
    void this.store.load();
  }
}
