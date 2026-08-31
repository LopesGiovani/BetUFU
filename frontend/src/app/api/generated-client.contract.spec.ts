import {
  provideHttpClient,
  withXsrfConfiguration,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import {
  AthleteLinkDecisionRequest,
  AthleteLinkProposal,
  AthleteLinkReviewPage,
  AthleteLinkService,
  CurrentIdentityResponse,
  IdentityService,
  ProfileService,
  PublicSportsEventPage,
  SportsAthleteCreateRequest,
  SportsAthleteService,
  SportsAthleteSummary,
  SportsBracketService,
  SportsBracketView,
  SportsCatalogService,
  SportsCatalogScope,
  SportsEventDetail,
  SportsEventResultService,
  SportsOrganizationService,
  SportsPublicService,
  SportsResultVersion,
  SportsStandingsRow,
  SportsStandingsService,
  SportsStandingsSnapshot,
  provideApi,
} from './generated';

describe('Generated BETUFU API client contract', () => {
  const xsrfToken = 'task-01-20-xsrf-token';
  let httpTesting: HttpTestingController;
  let identityService: IdentityService;
  let sportsAthleteService: SportsAthleteService;
  let sportsBracketService: SportsBracketService;
  let sportsPublicService: SportsPublicService;
  let sportsCatalogService: SportsCatalogService;
  let sportsOrganizationService: SportsOrganizationService;
  let sportsEventResultService: SportsEventResultService;
  let sportsStandingsService: SportsStandingsService;
  let profileService: ProfileService;
  let athleteLinkService: AthleteLinkService;

  function configureClient(includeApiProvider: boolean): void {
    document.cookie = 'XSRF-TOKEN=' + xsrfToken + '; path=/';

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({
            cookieName: 'XSRF-TOKEN',
            headerName: 'X-XSRF-TOKEN',
          }),
        ),
        provideHttpClientTesting(),
        ...(includeApiProvider ? [provideApi('')] : []),
      ],
    });

    httpTesting = TestBed.inject(HttpTestingController);
    identityService = TestBed.inject(IdentityService);
    sportsAthleteService = TestBed.inject(SportsAthleteService);
    sportsBracketService = TestBed.inject(SportsBracketService);
    sportsPublicService = TestBed.inject(SportsPublicService);
    sportsCatalogService = TestBed.inject(SportsCatalogService);
    sportsOrganizationService = TestBed.inject(SportsOrganizationService);
    sportsEventResultService = TestBed.inject(SportsEventResultService);
    sportsStandingsService = TestBed.inject(SportsStandingsService);
    profileService = TestBed.inject(ProfileService);
    athleteLinkService = TestBed.inject(AthleteLinkService);
  }

  afterEach(() => {
    httpTesting.verify();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  async function expectRelativePutContract(): Promise<void> {
    const response: CurrentIdentityResponse = {
      identityId: '11a70246-3db4-4c53-b2ab-3fdd91a3ea84',
      status: CurrentIdentityResponse.StatusEnum.Persisted,
      createdAt: '2026-08-12T12:00:00Z',
      updatedAt: '2026-08-12T12:00:01Z',
    };
    const resultPromise = firstValueFrom(identityService.putCurrentIdentity());

    const request = httpTesting.expectOne('/api/v1/tracer/identity/current');

    expect(request.request.method).toBe('PUT');
    expect(request.request.urlWithParams).toBe('/api/v1/tracer/identity/current');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe(xsrfToken);
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.headers.has('Cookie')).toBe(false);
    expect(request.request.withCredentials).toBe(false);

    request.flush(response);

    await expect(resultPromise).resolves.toEqual(response);
  }

  it('uses the explicit empty BASE_PATH and keeps native XSRF', async () => {
    configureClient(true);
    await expectRelativePutContract();
  });

  it('fails safe to the relative PUT contract without BASE_PATH and keeps native XSRF', async () => {
    configureClient(false);
    await expectRelativePutContract();
  });

  it('uses a relative anonymous GET for the published sports agenda', async () => {
    configureClient(true);
    const response: PublicSportsEventPage = {
      items: [],
      page: 2,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    };
    const resultPromise = firstValueFrom(sportsPublicService.listPublishedSportsEvents({
      competitionId: '5a9bd592-1df4-4620-a33d-23ec59beb5fe',
      editionId: '969088ef-42b9-47bc-8a45-427647b01f0d',
      sportId: '0d2c2f7c-65a7-4c5b-bb7d-70d0bbf4c0c9',
      categoryId: 'ca5c9e40-06d7-4905-bdd4-a85c2dcb9f46',
      phaseId: 'abf0d1c8-3e11-4a99-8d3a-2159d7bcdd3b',
      state: 'LOCAL_DEFINIDO',
      from: '2026-08-29T12:00:00Z',
      to: '2026-08-30T12:00:00Z',
      page: 2,
      size: 20,
    }));

    const request = httpTesting.expectOne(
      '/api/v1/public/sports/events'
      + '?competitionId=5a9bd592-1df4-4620-a33d-23ec59beb5fe'
      + '&editionId=969088ef-42b9-47bc-8a45-427647b01f0d'
      + '&sportId=0d2c2f7c-65a7-4c5b-bb7d-70d0bbf4c0c9'
      + '&categoryId=ca5c9e40-06d7-4905-bdd4-a85c2dcb9f46'
      + '&phaseId=abf0d1c8-3e11-4a99-8d3a-2159d7bcdd3b'
      + '&state=LOCAL_DEFINIDO'
      + '&from=2026-08-29T12%3A00%3A00Z'
      + '&to=2026-08-30T12%3A00%3A00Z'
      + '&page=2&size=20',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.headers.has('Cookie')).toBe(false);
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    expect(request.request.withCredentials).toBe(false);

    request.flush(response);

    await expect(resultPromise).resolves.toEqual(response);
  });

  it('consulta o escopo O1 pelo endpoint gerado sem aceitar autoridade local', async () => {
    configureClient(true);
    const response: SportsCatalogScope = { competitions: [], bootstrapAllowed: false };
    const resultPromise = firstValueFrom(sportsCatalogService.getSportsCatalogScope());
    const request = httpTesting.expectOne('/api/v1/operations/sports/catalog/scope');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.has('Authorization')).toBe(false);
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    request.flush(response);
    await expect(resultPromise).resolves.toEqual(response);
  });

  it('consome O3 e favorito por organizacao com caminhos relativos e IDs explicitos', async () => {
    configureClient(true);
    const organizationResult = firstValueFrom(sportsOrganizationService.listSportsOrganizations());
    const organizationRequest = httpTesting.expectOne('/api/v1/operations/sports/organizations');
    expect(organizationRequest.request.method).toBe('GET');
    expect(organizationRequest.request.headers.has('Authorization')).toBe(false);
    organizationRequest.flush([]);
    await expect(organizationResult).resolves.toEqual([]);

    const profileResult = firstValueFrom(profileService.putMyFavoriteOrganization({
      favoriteOrganizationRequest: { organizationId: '72de12f3-8e26-43a0-b8e0-08c5f5ed9b35' },
    }));
    const favoriteRequest = httpTesting.expectOne('/api/v1/profile/me/favorite-organization');
    expect(favoriteRequest.request.method).toBe('PUT');
    expect(favoriteRequest.request.body).toEqual({
      organizationId: '72de12f3-8e26-43a0-b8e0-08c5f5ed9b35',
    });
    expect(favoriteRequest.request.headers.has('Authorization')).toBe(false);
    favoriteRequest.flush({
      identityId: '11a70246-3db4-4c53-b2ab-3fdd91a3ea84',
      nickname: 'jogador',
      nicknameUpdatedAt: null,
      matriculaStatus: 'NAO_REGISTRADA',
      favoriteOrganizationId: '72de12f3-8e26-43a0-b8e0-08c5f5ed9b35',
    });
    await expect(profileResult).resolves.toMatchObject({
      favoriteOrganizationId: '72de12f3-8e26-43a0-b8e0-08c5f5ed9b35',
    });
  });

  it('consome endpoints operacionais de atleta com caminhos relativos e escopo derivado', async () => {
    configureClient(true);

    const listResult = firstValueFrom(sportsAthleteService.listSportsAthletes());
    const listRequest = httpTesting.expectOne('/api/v1/operations/sports/athletes');
    expect(listRequest.request.method).toBe('GET');
    expect(listRequest.request.headers.has('Authorization')).toBe(false);
    listRequest.flush([
      {
        athleteId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
        name: 'Atleta Um',
        slug: 'atleta-um',
        status: SportsAthleteSummary.StatusEnum.Rascunho,
        lockVersion: 0,
      },
    ]);
    await expect(listResult).resolves.toEqual([
      {
        athleteId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
        name: 'Atleta Um',
        slug: 'atleta-um',
        status: 'RASCUNHO',
        lockVersion: 0,
      },
    ]);

    const createPayload: SportsAthleteCreateRequest = {
      name: 'Atleta Dois',
      slug: 'atleta-dois',
      matricula: '12345678',
    };
    const createResult = firstValueFrom(sportsAthleteService.createSportsAthlete({
      teamId: '72de12f3-8e26-43a0-b8e0-08c5f5ed9b35',
      sportsAthleteCreateRequest: createPayload,
    }));
    const createRequest = httpTesting.expectOne('/api/v1/operations/sports/teams/72de12f3-8e26-43a0-b8e0-08c5f5ed9b35/athletes');
    expect(createRequest.request.method).toBe('POST');
    expect(createRequest.request.body).toEqual({
      name: 'Atleta Dois',
      slug: 'atleta-dois',
      matricula: '12345678',
    });
    expect(createRequest.request.headers.has('Authorization')).toBe(false);
    createRequest.flush({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Dois',
      slug: 'atleta-dois',
      status: SportsAthleteSummary.StatusEnum.Rascunho,
      lockVersion: 0,
    });
    await expect(createResult).resolves.toEqual({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Dois',
      slug: 'atleta-dois',
      status: 'RASCUNHO',
      lockVersion: 0,
    });

    const updateResult = firstValueFrom(sportsAthleteService.updateSportsAthlete({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      expectedLockVersion: 0,
      sportsNameSlugRequest: { name: 'Atleta Dois Atualizado', slug: 'atleta-dois-atualizado' },
    }));
    const updateRequest = httpTesting.expectOne('/api/v1/operations/sports/athletes/b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e?expectedLockVersion=0');
    expect(updateRequest.request.method).toBe('PUT');
    expect(updateRequest.request.body).toEqual({
      name: 'Atleta Dois Atualizado',
      slug: 'atleta-dois-atualizado',
    });
    updateRequest.flush({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Dois Atualizado',
      slug: 'atleta-dois-atualizado',
      status: SportsAthleteSummary.StatusEnum.Rascunho,
      lockVersion: 1,
    });
    await expect(updateResult).resolves.toEqual({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Dois Atualizado',
      slug: 'atleta-dois-atualizado',
      status: 'RASCUNHO',
      lockVersion: 1,
    });

    const publishResult = firstValueFrom(sportsAthleteService.publishSportsAthlete({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      expectedLockVersion: 1,
    }));
    const publishRequest = httpTesting.expectOne('/api/v1/operations/sports/athletes/b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e/publish?expectedLockVersion=1');
    expect(publishRequest.request.method).toBe('POST');
    publishRequest.flush({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Dois Atualizado',
      slug: 'atleta-dois-atualizado',
      status: SportsAthleteSummary.StatusEnum.Publicado,
      lockVersion: 2,
    });
    await expect(publishResult).resolves.toEqual({
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Dois Atualizado',
      slug: 'atleta-dois-atualizado',
      status: 'PUBLICADO',
      lockVersion: 2,
    });
  });

  it('consome proposta owner e fila O8 pelos endpoints gerados sem dados privados', async () => {
    configureClient(true);
    const proposal: AthleteLinkProposal = {
      proposalId: 'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      athleteName: 'Atleta Um',
      athleteSlug: 'atleta-um',
      status: AthleteLinkProposal.StatusEnum.Pendente,
      createdAt: '2026-08-30T10:00:00Z',
      updatedAt: '2026-08-30T10:00:00Z',
      expiresAt: '2026-09-30T10:00:00Z',
      decidedAt: null,
    };

    const proposalResult = firstValueFrom(athleteLinkService.getMyAthleteLinkProposal());
    const proposalRequest = httpTesting.expectOne('/api/v1/me/sports/athlete-links/proposal');
    expect(proposalRequest.request.method).toBe('GET');
    expect(proposalRequest.request.headers.has('Authorization')).toBe(false);
    proposalRequest.flush(proposal);
    await expect(proposalResult).resolves.toEqual(proposal);

    const decisionResult = firstValueFrom(athleteLinkService.decideAthleteLinkProposal({
      proposalId: proposal.proposalId,
      athleteLinkDecisionRequest: { decision: AthleteLinkDecisionRequest.DecisionEnum.Contest },
    }));
    const decisionRequest = httpTesting.expectOne('/api/v1/me/sports/athlete-links/' + proposal.proposalId + '/decision');
    expect(decisionRequest.request.method).toBe('PUT');
    expect(decisionRequest.request.body).toEqual({ decision: 'CONTEST' });
    expect(decisionRequest.request.headers.has('Authorization')).toBe(false);
    decisionRequest.flush({ ...proposal, status: AthleteLinkProposal.StatusEnum.Contestada });
    await expect(decisionResult).resolves.toMatchObject({ status: 'CONTESTADA' });

    const page: AthleteLinkReviewPage = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false };
    const reviewResult = firstValueFrom(athleteLinkService.listAthleteLinkReviews({ page: 0, size: 20 }));
    const reviewRequest = httpTesting.expectOne('/api/v1/operations/sports/athlete-link-reviews?page=0&size=20');
    expect(reviewRequest.request.method).toBe('GET');
    expect(reviewRequest.request.headers.has('Authorization')).toBe(false);
    reviewRequest.flush(page);
    await expect(reviewResult).resolves.toEqual(page);
  });

  it('assegura assimetria de privacidade: matricula restrita ao request e ausencia total de HMAC', () => {
    const athleteRequest: SportsAthleteCreateRequest = {
      name: 'Atleta Teste',
      slug: 'atleta-teste',
      matricula: '12345678',
    };
    expect(athleteRequest.matricula).toBe('12345678');

    const athleteSummary: SportsAthleteSummary = {
      athleteId: 'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
      name: 'Atleta Teste',
      slug: 'atleta-teste',
      status: SportsAthleteSummary.StatusEnum.Rascunho,
      lockVersion: 0,
    };
    expect('matricula' in athleteSummary).toBe(false);
    expect('fingerprint' in athleteSummary).toBe(false);
    expect('hmac' in athleteSummary).toBe(false);
  });

  it('consome lifecycle completo de evento e resultado pelo SportsEventResultService gerado', async () => {
    configureClient(true);

    const eventId = 'e1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c';
    const editionId = 'ed1a2b3c-d5e6-7f8a-9b0c-1d2e3f4a5b6c';
    const sampleEvent: SportsEventDetail = {
      eventId,
      competitionId: 'c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      competitionName: 'InterUFU',
      editionId,
      editionName: '2026',
      title: 'Futsal Masculino - Final',
      format: 'HEAD_TO_HEAD',
      status: 'RASCUNHO',
      scheduledAt: '2026-09-01T18:00:00Z',
      zoneId: 'America/Sao_Paulo',
      venueState: 'LOCAL_DEFINIDO',
      sourceReference: 'https://boletim.ufu.br/evento/1',
      lockVersion: 0,
      slots: [
        { slotNo: 1, displayLabel: 'Engenharia', teamId: 't1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c', teamName: 'Engenharia' },
        { slotNo: 2, displayLabel: 'Medicina', teamId: 't2a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c', teamName: 'Medicina' },
      ],
      createdAt: '2026-08-30T12:00:00Z',
      updatedAt: '2026-08-30T12:00:00Z',
    };

    const listEventsResult = firstValueFrom(sportsEventResultService.listSportsEvents({ editionId }));
    const listReq = httpTesting.expectOne(`/api/v1/operations/sports/editions/${editionId}/events`);
    expect(listReq.request.method).toBe('GET');
    listReq.flush([sampleEvent]);
    await expect(listEventsResult).resolves.toEqual([sampleEvent]);

    const createResult = firstValueFrom(sportsEventResultService.createSportsEvent({
      editionId,
      sportsEventCreateRequest: {
        title: 'Futsal Masculino - Final',
        format: 'HEAD_TO_HEAD',
        scheduledAt: '2026-09-01T18:00:00Z',
        zoneId: 'America/Sao_Paulo',
        venueState: 'LOCAL_DEFINIDO',
        sourceReference: 'https://boletim.ufu.br/evento/1',
        slots: [
          { slotNo: 1, displayLabel: 'Engenharia' },
          { slotNo: 2, displayLabel: 'Medicina' },
        ],
      },
    }));
    const createReq = httpTesting.expectOne(`/api/v1/operations/sports/editions/${editionId}/events`);
    expect(createReq.request.method).toBe('POST');
    createReq.flush(sampleEvent);
    await expect(createResult).resolves.toEqual(sampleEvent);

    const getDetailResult = firstValueFrom(sportsEventResultService.getSportsEventDetail({ eventId }));
    const detailReq = httpTesting.expectOne(`/api/v1/operations/sports/events/${eventId}`);
    expect(detailReq.request.method).toBe('GET');
    detailReq.flush(sampleEvent);
    await expect(getDetailResult).resolves.toEqual(sampleEvent);

    const publishResult = firstValueFrom(sportsEventResultService.publishSportsEvent({ eventId, expectedLockVersion: 0 }));
    const publishReq = httpTesting.expectOne(`/api/v1/operations/sports/events/${eventId}/publish?expectedLockVersion=0`);
    expect(publishReq.request.method).toBe('POST');
    publishReq.flush({ ...sampleEvent, status: 'PUBLICADO', lockVersion: 1 });
    await expect(publishResult).resolves.toMatchObject({ status: 'PUBLICADO', lockVersion: 1 });

    const sampleResultVersion: SportsResultVersion = {
      versionId: 'v1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      eventId,
      competitionId: 'c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      editionId,
      versionNo: 1,
      status: 'CONFIRMADO',
      sourceReference: 'https://boletim.ufu.br/sumula/1.pdf',
      scores: [
        { slotNo: 1, displayLabel: 'Engenharia', score: 3 },
        { slotNo: 2, displayLabel: 'Medicina', score: 2 },
      ],
      recordedByIdentityId: 'id1a2b3c-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      confirmedByIdentityId: 'id1a2b3c-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      recordedAt: '2026-08-30T19:00:00Z',
      confirmedAt: '2026-08-30T19:05:00Z',
      baseLockVersion: 1,
    };

    const confirmResult = firstValueFrom(sportsEventResultService.confirmSportsResult({
      eventId,
      expectedLockVersion: 1,
      sportsResultConfirmRequest: {
        sourceReference: 'https://boletim.ufu.br/sumula/1.pdf',
        scores: [
          { slotNo: 1, score: 3 },
          { slotNo: 2, score: 2 },
        ],
      },
    }));
    const confirmReq = httpTesting.expectOne(`/api/v1/operations/sports/events/${eventId}/results/confirm?expectedLockVersion=1`);
    expect(confirmReq.request.method).toBe('POST');
    confirmReq.flush(sampleResultVersion);
    await expect(confirmResult).resolves.toEqual(sampleResultVersion);

    const listVersionsResult = firstValueFrom(sportsEventResultService.listSportsResultVersions({ eventId }));
    const listVerReq = httpTesting.expectOne(`/api/v1/operations/sports/events/${eventId}/results`);
    expect(listVerReq.request.method).toBe('GET');
    listVerReq.flush([sampleResultVersion]);
    await expect(listVersionsResult).resolves.toEqual([sampleResultVersion]);
  });

  it('separa leitura publica P7 do preview operacional O7 no cliente gerado', async () => {
    configureClient(true);
    const bracketId = 'b1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c';
    const response: SportsBracketView = {
      bracketId,
      competitionId: 'c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      editionId: 'e1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      name: 'Chaveamento Futsal',
      slug: 'chaveamento-futsal',
      status: 'PUBLICADO',
      lockVersion: 2,
      rounds: [],
    };

    const publicResult = firstValueFrom(sportsBracketService.getPublicSportsBracket({ bracketId }));
    const publicRequest = httpTesting.expectOne(`/api/v1/public/sports/brackets/${bracketId}`);
    expect(publicRequest.request.method).toBe('GET');
    expect(publicRequest.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    publicRequest.flush(response);
    await expect(publicResult).resolves.toEqual(response);

    const previewResult = firstValueFrom(sportsBracketService.getSportsBracket({ bracketId }));
    const previewRequest = httpTesting.expectOne(`/api/v1/operations/sports/brackets/${bracketId}`);
    expect(previewRequest.request.method).toBe('GET');
    expect(previewRequest.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    previewRequest.flush({ ...response, status: 'RASCUNHO' });
    await expect(previewResult).resolves.toMatchObject({ status: 'RASCUNHO' });
  });

  it('separa leitura publica P8 do preview operacional O7 no SportsStandingsService gerado', async () => {
    configureClient(true);
    const standingsId = 's1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c';
    const response: SportsStandingsSnapshot = {
      standingsId,
      competitionId: 'c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      editionId: 'e1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c',
      name: 'Classificação Futsal',
      slug: 'classificacao-futsal',
      status: 'PUBLICADO',
      competitionName: 'InterUFU',
      editionName: '2026',
      lockVersion: 1,
      columns: ['Pos', 'Equipe', 'Pontos'],
      rows: [
        { position: 1, participantName: 'Engenharia', values: ['1', 'Engenharia', '9'] },
      ],
    };

    const publicResult = firstValueFrom(sportsStandingsService.getPublicSportsStandings({ standingsId }));
    const publicRequest = httpTesting.expectOne(`/api/v1/public/sports/standings/${standingsId}`);
    expect(publicRequest.request.method).toBe('GET');
    expect(publicRequest.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    publicRequest.flush(response);
    await expect(publicResult).resolves.toEqual(response);

    const previewResult = firstValueFrom(sportsStandingsService.getSportsStandings({ standingsId }));
    const previewRequest = httpTesting.expectOne(`/api/v1/operations/sports/standings/${standingsId}`);
    expect(previewRequest.request.method).toBe('GET');
    expect(previewRequest.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    previewRequest.flush({ ...response, status: 'RASCUNHO' });
    await expect(previewResult).resolves.toMatchObject({ status: 'RASCUNHO' });
  });
});
