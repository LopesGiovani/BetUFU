import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { SettlementHistoryStore } from './settlement-history.store';
@Component({
  selector: 'app-settlement-history-page',
  standalone: true,
  imports: [DatePipe, DecimalPipe, RouterLink],
  providers: [SettlementHistoryStore],
  templateUrl: './settlement-history-page.component.html',
  styleUrl: './settlement-history-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettlementHistoryPageComponent implements OnInit {
  readonly store = inject(SettlementHistoryStore);
  readonly states: Record<string, string> = {
    PENDING: 'Pendente',
    WON: 'Ganho',
    LOST: 'Perdido',
    REFUNDED: 'Reembolsado',
    ANNULLED: 'Anulado',
  };
  readonly reasons: Record<string, string> = {
    SETTLEMENT_CORRECTION: 'Correcao de liquidacao',
    DUPLICATE_OR_MISSING_ENTRY: 'Lancamento duplicado ou ausente',
    PROVEN_TECHNICAL_FAILURE: 'Falha tecnica comprovada',
    ELIGIBILITY_CORRECTION: 'Correcao de elegibilidade',
    CORRECTED_RESULT: 'Resultado corrigido',
    CONFIRMED_RESULT: 'Resultado confirmado',
    EVENT_CANCELLED: 'Partida cancelada',
    NO_VALID_RESULT: 'Sem resultado valido',
    ADMINISTRATIVE_WALKOVER: 'Resultado administrativo',
  };
  ngOnInit() {
    void this.store.load();
  }
}
