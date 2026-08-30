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
  CurrentIdentityResponse,
  IdentityService,
  PublicSportsEventPage,
  SportsCatalogService,
  SportsCatalogScope,
  SportsPublicService,
  provideApi,
} from './generated';

describe('Generated BETUFU API client contract', () => {
  const xsrfToken = 'task-01-20-xsrf-token';
  let httpTesting: HttpTestingController;
  let identityService: IdentityService;
  let sportsPublicService: SportsPublicService;
  let sportsCatalogService: SportsCatalogService;

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
    sportsPublicService = TestBed.inject(SportsPublicService);
    sportsCatalogService = TestBed.inject(SportsCatalogService);
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
});
