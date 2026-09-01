import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  CreateSportsAthleteRequestParams,
  PublishSportsAthleteRequestParams,
  SportsAthleteService,
  UpdateSportsAthleteRequestParams,
} from '../../../../api/generated/api/sports-athlete.service';
import { SportsAthleteSummary } from '../../../../api/generated/model/sports-athlete-summary';

@Injectable({ providedIn: 'root' })
export class SportsAthletesApiAdapter {
  private readonly service = inject(SportsAthleteService);

  fetchAthletes(): Observable<readonly SportsAthleteSummary[]> {
    return defer(() => this.service.listSportsAthletes()).pipe(
      map((response) => this.adaptAthletes(response)),
    );
  }

  createAthlete(
    teamId: string,
    name: string,
    slug: string,
    matricula?: string | null,
  ): Observable<SportsAthleteSummary> {
    const request: CreateSportsAthleteRequestParams = {
      teamId,
      sportsAthleteCreateRequest: {
        name,
        slug,
        matricula: matricula?.trim() ? matricula.trim() : undefined,
      },
    };
    return defer(() => this.service.createSportsAthlete(request));
  }

  updateAthlete(
    athleteId: string,
    expectedLockVersion: number,
    name: string,
    slug: string,
  ): Observable<SportsAthleteSummary> {
    const request: UpdateSportsAthleteRequestParams = {
      athleteId,
      expectedLockVersion,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.service.updateSportsAthlete(request));
  }

  publishAthlete(athleteId: string, expectedLockVersion: number): Observable<SportsAthleteSummary> {
    const request: PublishSportsAthleteRequestParams = {
      athleteId,
      expectedLockVersion,
    };
    return defer(() => this.service.publishSportsAthlete(request));
  }

  isConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  adaptAthletes(response: unknown): readonly SportsAthleteSummary[] {
    if (!Array.isArray(response)) {
      throw new Error('CONTRATO_ATLETAS_INVALIDO: lista ausente ou malformada.');
    }
    return this.sortByName(response.map((item) => this.requireAthlete(item)));
  }

  private requireAthlete(value: unknown): SportsAthleteSummary {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_ATLETAS_INVALIDO: registro ausente.');
    }
    const item = value as Partial<SportsAthleteSummary>;
    if (
      typeof item.athleteId !== 'string' ||
      typeof item.name !== 'string' ||
      typeof item.slug !== 'string' ||
      (item.status !== 'RASCUNHO' && item.status !== 'PUBLICADO') ||
      typeof item.lockVersion !== 'number'
    ) {
      throw new Error('CONTRATO_ATLETAS_INVALIDO: registro incompleto.');
    }
    return item as SportsAthleteSummary;
  }

  private sortByName<T extends { name: string }>(items: readonly T[]): T[] {
    return [...items].sort(
      (left, right) =>
        left.name.localeCompare(right.name, 'pt-BR') ||
        JSON.stringify(left).localeCompare(JSON.stringify(right), 'pt-BR'),
    );
  }
}
