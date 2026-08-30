import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  AthleteLinkService,
  DecideAthleteLinkProposalRequestParams,
} from '../../../api/generated/api/athlete-link.service';
import { AthleteLinkDecisionRequest } from '../../../api/generated/model/athlete-link-decision-request';
import { AthleteLinkProposal } from '../../../api/generated/model/athlete-link-proposal';

@Injectable({ providedIn: 'root' })
export class AthleteLinkProposalApiAdapter {
  private readonly service = inject(AthleteLinkService);

  fetchProposal(): Observable<AthleteLinkProposal> {
    return defer(() => this.service.getMyAthleteLinkProposal()).pipe(
      map((response) => this.requireProposal(response)),
    );
  }

  decide(
    proposalId: string,
    decision: AthleteLinkDecisionRequest.DecisionEnum,
  ): Observable<AthleteLinkProposal> {
    const request: DecideAthleteLinkProposalRequestParams = {
      proposalId,
      athleteLinkDecisionRequest: { decision },
    };
    // Mutations intentionally have no retry policy; replay is handled by the API.
    return defer(() => this.service.decideAthleteLinkProposal(request)).pipe(
      map((response) => this.requireProposal(response)),
    );
  }

  isNotFound(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 404;
  }

  isUnauthorized(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  isConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  private requireProposal(response: AthleteLinkProposal | null | undefined): AthleteLinkProposal {
    if (!response || typeof response.proposalId !== 'string' || typeof response.athleteId !== 'string'
      || typeof response.athleteName !== 'string' || typeof response.athleteSlug !== 'string'
      || typeof response.status !== 'string' || typeof response.createdAt !== 'string'
      || typeof response.updatedAt !== 'string') {
      throw new Error('CONTRATO_VINCULO_INVALIDO: proposta ausente ou malformada.');
    }
    return response;
  }
}
