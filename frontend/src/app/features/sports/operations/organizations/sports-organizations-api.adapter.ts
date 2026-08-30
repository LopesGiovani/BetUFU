import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  CreateSportsOrganizationRequestParams,
  CreateSportsTeamRequestParams,
  ListSportsTeamsRequestParams,
  PublishSportsOrganizationRequestParams,
  PublishSportsTeamRequestParams,
  SportsOrganizationService,
  UpdateSportsOrganizationRequestParams,
  UpdateSportsTeamRequestParams,
} from '../../../../api/generated/api/sports-organization.service';
import { SportsOrganizationSummary } from '../../../../api/generated/model/sports-organization-summary';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';

@Injectable({ providedIn: 'root' })
export class SportsOrganizationsApiAdapter {
  private readonly service = inject(SportsOrganizationService);

  fetchOrganizations(): Observable<readonly SportsOrganizationSummary[]> {
    return defer(() => this.service.listSportsOrganizations()).pipe(
      map((response) => this.adaptOrganizations(response)),
    );
  }

  fetchTeams(competitionId: string): Observable<readonly SportsTeamSummary[]> {
    const request: ListSportsTeamsRequestParams = { competitionId };
    return defer(() => this.service.listSportsTeams(request)).pipe(
      map((response) => this.adaptTeams(response)),
    );
  }

  createOrganization(name: string, slug: string) {
    const request: CreateSportsOrganizationRequestParams = {
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.service.createSportsOrganization(request));
  }

  updateOrganization(
    organizationId: string,
    expectedLockVersion: number,
    name: string,
    slug: string,
  ) {
    const request: UpdateSportsOrganizationRequestParams = {
      organizationId,
      expectedLockVersion,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.service.updateSportsOrganization(request));
  }

  publishOrganization(organizationId: string, expectedLockVersion: number) {
    const request: PublishSportsOrganizationRequestParams = { organizationId, expectedLockVersion };
    return defer(() => this.service.publishSportsOrganization(request));
  }

  createTeam(
    editionId: string,
    sportId: string,
    categoryId: string,
    organizationId: string,
    name: string,
    slug: string,
  ) {
    const request: CreateSportsTeamRequestParams = {
      editionId,
      sportId,
      categoryId,
      organizationId,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.service.createSportsTeam(request));
  }

  updateTeam(teamId: string, expectedLockVersion: number, name: string, slug: string) {
    const request: UpdateSportsTeamRequestParams = {
      teamId,
      expectedLockVersion,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.service.updateSportsTeam(request));
  }

  publishTeam(teamId: string, expectedLockVersion: number) {
    const request: PublishSportsTeamRequestParams = { teamId, expectedLockVersion };
    return defer(() => this.service.publishSportsTeam(request));
  }

  isConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  adaptOrganizations(response: unknown): readonly SportsOrganizationSummary[] {
    if (!Array.isArray(response)) {
      throw new Error('CONTRATO_ORGANIZACOES_INVALIDO: lista ausente ou malformada.');
    }
    return this.sortByName(response.map((item) => this.requireOrganization(item)));
  }

  adaptTeams(response: unknown): readonly SportsTeamSummary[] {
    if (!Array.isArray(response)) {
      throw new Error('CONTRATO_EQUIPES_INVALIDO: lista ausente ou malformada.');
    }
    return this.sortByName(response.map((item) => this.requireTeam(item)));
  }

  private requireOrganization(value: unknown): SportsOrganizationSummary {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_ORGANIZACOES_INVALIDO: registro ausente.');
    }
    const item = value as Partial<SportsOrganizationSummary>;
    if (
      typeof item.organizationId !== 'string'
      || typeof item.name !== 'string'
      || typeof item.slug !== 'string'
      || (item.status !== 'RASCUNHO' && item.status !== 'PUBLICADO')
      || typeof item.lockVersion !== 'number'
    ) {
      throw new Error('CONTRATO_ORGANIZACOES_INVALIDO: registro incompleto.');
    }
    return item as SportsOrganizationSummary;
  }

  private requireTeam(value: unknown): SportsTeamSummary {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_EQUIPES_INVALIDO: registro ausente.');
    }
    const item = value as Partial<SportsTeamSummary>;
    const requiredStrings = [
      item.teamId,
      item.organizationId,
      item.organizationName,
      item.competitionId,
      item.editionId,
      item.sportId,
      item.sportName,
      item.categoryId,
      item.categoryName,
      item.name,
      item.slug,
    ];
    if (
      requiredStrings.some((entry) => typeof entry !== 'string')
      || (item.status !== 'RASCUNHO' && item.status !== 'PUBLICADO')
      || typeof item.lockVersion !== 'number'
    ) {
      throw new Error('CONTRATO_EQUIPES_INVALIDO: registro incompleto.');
    }
    return item as SportsTeamSummary;
  }

  private sortByName<T extends { name: string }>(items: readonly T[]): T[] {
    return [...items].sort((left, right) =>
      left.name.localeCompare(right.name, 'pt-BR')
      || JSON.stringify(left).localeCompare(JSON.stringify(right), 'pt-BR'));
  }
}
