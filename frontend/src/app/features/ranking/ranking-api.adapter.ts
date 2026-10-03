import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { RankingService } from '../../api/generated';
@Injectable({ providedIn: 'root' })
export class RankingApiAdapter {
  private readonly api = inject(RankingService);
  async read() {
    return (await firstValueFrom(this.api.getLifetimeRanking())).entries.map((e) => ({
      position: e.position,
      nickname: e.nickname,
      wins: e.wins,
      losses: e.losses,
      hitRate: e.hitRate,
      verificationActive: e.verificationActive,
    }));
  }
}
