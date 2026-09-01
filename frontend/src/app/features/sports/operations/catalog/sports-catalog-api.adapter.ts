import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  CreateSportsCatalogCategoryRequestParams,
  CreateSportsCatalogCompetitionRequestParams,
  CreateSportsCatalogEditionRequestParams,
  CreateSportsCatalogPhaseRequestParams,
  CreateSportsCatalogSportRequestParams,
  CreateSportsCatalogVenueRequestParams,
  CreateSportsCatalogVenueSpaceRequestParams,
  SportsCatalogService,
  UpdateSportsCatalogCategoryRequestParams,
  UpdateSportsCatalogEditionRequestParams,
  UpdateSportsCatalogPhaseRequestParams,
  UpdateSportsCatalogSportRequestParams,
  UpdateSportsCatalogVenueRequestParams,
  UpdateSportsCatalogVenueSpaceRequestParams,
} from '../../../../api/generated/api/sports-catalog.service';
import { OperationalCapabilitiesService } from '../../../../api/generated/api/operational-capabilities.service';
import { OperationalCapability } from '../../../../api/generated/model/operational-capability';
import { SportsCatalogScope } from '../../../../api/generated/model/sports-catalog-scope';
import { SportsCatalogTree } from '../../../../api/generated/model/sports-catalog-tree';

export type SportsCatalogCapability =
  | typeof OperationalCapability.ManageSportsCatalog
  | typeof OperationalCapability.BootstrapSportsCompetition;

const CAPABILITY_ORDER: readonly SportsCatalogCapability[] = Object.freeze([
  OperationalCapability.ManageSportsCatalog,
  OperationalCapability.BootstrapSportsCompetition,
]);

@Injectable({ providedIn: 'root' })
export class SportsCatalogApiAdapter {
  private readonly catalogService = inject(SportsCatalogService);
  private readonly capabilitiesService = inject(OperationalCapabilitiesService);

  fetchScope(): Observable<SportsCatalogScope> {
    return defer(() => this.catalogService.getSportsCatalogScope()).pipe(
      map((response) => this.adaptScope(response)),
    );
  }

  fetchCapabilities(): Observable<readonly SportsCatalogCapability[]> {
    return defer(() => this.capabilitiesService.getOperationalCapabilities()).pipe(
      map((response) => this.adaptCapabilities(response)),
    );
  }

  fetchTree(competitionId: string): Observable<SportsCatalogTree> {
    return defer(() => this.catalogService.getSportsCatalogTree({ competitionId })).pipe(
      map((response) => this.adaptTree(response)),
    );
  }

  createCompetition(name: string, slug: string) {
    const request: CreateSportsCatalogCompetitionRequestParams = {
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.catalogService.createSportsCatalogCompetition(request));
  }

  createSport(competitionId: string, name: string, slug: string) {
    const request: CreateSportsCatalogSportRequestParams = {
      competitionId,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.catalogService.createSportsCatalogSport(request));
  }

  createEdition(
    competitionId: string,
    requestBody: CreateSportsCatalogEditionRequestParams['sportsCatalogEditionRequest'],
  ) {
    const request: CreateSportsCatalogEditionRequestParams = {
      competitionId,
      sportsCatalogEditionRequest: requestBody,
    };
    return defer(() => this.catalogService.createSportsCatalogEdition(request));
  }

  createCategory(editionId: string, name: string, slug: string) {
    const request: CreateSportsCatalogCategoryRequestParams = {
      editionId,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.catalogService.createSportsCatalogCategory(request));
  }

  createPhase(categoryId: string, name: string, slug: string) {
    const request: CreateSportsCatalogPhaseRequestParams = {
      categoryId,
      sportsNameSlugRequest: { name, slug },
    };
    return defer(() => this.catalogService.createSportsCatalogPhase(request));
  }

  createVenue(
    competitionId: string,
    name: string,
    address?: string,
    mapUrl?: string,
    notes?: string,
  ) {
    const request: CreateSportsCatalogVenueRequestParams = {
      competitionId,
      sportsCatalogVenueRequest: { name, address, mapUrl, notes },
    };
    return defer(() => this.catalogService.createSportsCatalogVenue(request));
  }

  createVenueSpace(venueId: string, name: string, notes?: string) {
    const request: CreateSportsCatalogVenueSpaceRequestParams = {
      venueId,
      sportsCatalogVenueSpaceRequest: { name, notes },
    };
    return defer(() => this.catalogService.createSportsCatalogVenueSpace(request));
  }

  updateSport(request: UpdateSportsCatalogSportRequestParams) {
    return defer(() => this.catalogService.updateSportsCatalogSport(request));
  }

  updateEdition(request: UpdateSportsCatalogEditionRequestParams) {
    return defer(() => this.catalogService.updateSportsCatalogEdition(request));
  }

  updateCategory(request: UpdateSportsCatalogCategoryRequestParams) {
    return defer(() => this.catalogService.updateSportsCatalogCategory(request));
  }

  updatePhase(request: UpdateSportsCatalogPhaseRequestParams) {
    return defer(() => this.catalogService.updateSportsCatalogPhase(request));
  }

  updateVenue(request: UpdateSportsCatalogVenueRequestParams) {
    return defer(() => this.catalogService.updateSportsCatalogVenue(request));
  }

  updateVenueSpace(request: UpdateSportsCatalogVenueSpaceRequestParams) {
    return defer(() => this.catalogService.updateSportsCatalogVenueSpace(request));
  }

  isConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  adaptScope(response: SportsCatalogScope | null | undefined): SportsCatalogScope {
    if (
      !response ||
      !Array.isArray(response.competitions) ||
      typeof response.bootstrapAllowed !== 'boolean'
    ) {
      throw new Error('CONTRATO_CATALOGO_INVALIDO: escopo ausente ou malformado.');
    }

    const seen = new Set<string>();
    const competitions = response.competitions
      .filter((competition) => {
        if (
          !competition ||
          typeof competition.competitionId !== 'string' ||
          seen.has(competition.competitionId)
        ) {
          return false;
        }
        seen.add(competition.competitionId);
        return typeof competition.name === 'string' && competition.name.trim() !== '';
      })
      .map((competition) => ({ ...competition, name: competition.name.trim() }))
      .sort(
        (left, right) =>
          left.name.localeCompare(right.name, 'pt-BR') ||
          left.competitionId.localeCompare(right.competitionId),
      );

    return {
      competitions,
      bootstrapAllowed: response.bootstrapAllowed,
    };
  }

  adaptTree(response: SportsCatalogTree | null | undefined): SportsCatalogTree {
    if (
      !response ||
      !response.competition ||
      !Array.isArray(response.sports) ||
      !Array.isArray(response.editions) ||
      !Array.isArray(response.categories) ||
      !Array.isArray(response.phases) ||
      !Array.isArray(response.venues)
    ) {
      throw new Error('CONTRATO_CATALOGO_INVALIDO: arvore ausente ou malformada.');
    }

    return {
      competition: response.competition,
      sports: this.sortByName(response.sports),
      editions: this.sortByName(response.editions),
      categories: this.sortByName(response.categories),
      phases: this.sortByName(response.phases),
      venues: this.sortByName(response.venues).map((venue) => ({
        ...venue,
        spaces: Array.isArray(venue.spaces) ? this.sortByName(venue.spaces) : [],
      })),
    };
  }

  adaptCapabilities(response: unknown): readonly SportsCatalogCapability[] {
    if (typeof response !== 'object' || response === null) {
      return Object.freeze([]);
    }
    const raw = (response as { capabilities?: unknown }).capabilities;
    if (!Array.isArray(raw)) {
      return Object.freeze([]);
    }
    const occurrences = new Map<unknown, number>();
    for (const value of raw) occurrences.set(value, (occurrences.get(value) ?? 0) + 1);
    return Object.freeze(
      CAPABILITY_ORDER.filter((capability) => occurrences.get(capability) === 1),
    );
  }

  private sortByName<T extends { name: string }>(items: readonly T[]): T[] {
    return [...items].sort((left, right) => left.name.localeCompare(right.name, 'pt-BR'));
  }
}
