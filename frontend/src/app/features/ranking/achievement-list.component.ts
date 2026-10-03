import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { AchievementApiAdapter } from './achievement-api.adapter';
@Component({
  selector: 'app-achievement-list',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './achievement-list.component.html',
  styleUrl: './achievement-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AchievementListComponent implements OnInit {
  private readonly api = inject(AchievementApiAdapter);
  readonly items = signal<Awaited<ReturnType<AchievementApiAdapter['read']>>>([]);
  readonly loading = signal(false);
  readonly error = signal(false);
  ngOnInit() {
    void this.load();
  }
  async load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.error.set(false);
    try {
      this.items.set(await this.api.read());
    } catch {
      this.items.set([]);
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
