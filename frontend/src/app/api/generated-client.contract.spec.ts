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
  provideApi,
} from './generated';

describe('Generated BETUFU API client contract', () => {
  const xsrfToken = 'task-01-20-xsrf-token';
  let httpTesting: HttpTestingController;
  let identityService: IdentityService;

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
});
