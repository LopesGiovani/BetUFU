import { Injectable, inject } from '@angular/core';
import { PrivateApiRecovery } from '../../core/session/private-api-recovery';
import { AchievementService } from '../../api/generated';
@Injectable({ providedIn: 'root' })
export class AchievementApiAdapter {
  private readonly recovery = inject(PrivateApiRecovery);
  private readonly api = inject(AchievementService);
  async read() {
    return (await this.recovery.read(this.api.getMyAchievements())).items.map((a) => ({
      awardId: a.awardId,
      name: a.name,
      description: a.description,
      rule: a.rule,
      catalogEdition: a.catalogEdition,
      sportsEditionId: a.sportsEditionId,
      state: a.state,
      awardedAt: a.awardedAt,
      revokedAt: a.revokedAt,
    }));
  }
}
