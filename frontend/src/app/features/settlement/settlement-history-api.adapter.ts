import { Injectable, inject } from '@angular/core';
import { PrivateApiRecovery } from '../../core/session/private-api-recovery';
import { CreditCorrectionService, SettlementService } from '../../api/generated';
@Injectable({ providedIn: 'root' })
export class SettlementHistoryApiAdapter {
  private readonly recovery = inject(PrivateApiRecovery);
  private readonly settlements = inject(SettlementService);
  private readonly corrections = inject(CreditCorrectionService);
  async read() {
    const [history, corrections] = await Promise.all([
      this.recovery.read(this.settlements.getMySettlementHistory()),
      this.recovery.read(this.corrections.getMyCreditCorrections()),
    ]);
    return {
      items: history.items.map((i) => ({ ...i })),
      corrections: corrections.items.map((c) => ({
        caseId: c.caseId,
        referenceCode: c.referenceCode,
        reason: c.reason,
        amount: c.amount,
        recovered: c.recovered,
        pending: c.pending,
        status: c.status,
        createdAt: c.createdAt,
        originalEntryId: c.originalEntryId,
        evidenceId: c.evidenceId,
      })),
    };
  }
}
