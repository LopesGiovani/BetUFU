import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicOrganizationPageComponent } from './public-organization-page.component';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

describe('PublicOrganizationPageComponent', () => {
  it('carrega organização por URL direta e mantém favorito como ação autenticada', async () => {
    const store = {
      isLoading: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      data: signal({
        organizationId: 'o1',
        name: 'Atléticas',
        slug: 'atleticas',
        teams: [],
        rosterMembers: [],
        events: [],
      }),
      retry: vi.fn(),
      load: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [PublicOrganizationPageComponent],
      providers: [
        { provide: SportsPublicViewStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'o1' } } } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublicOrganizationPageComponent);
    fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('organization', 'o1');
    expect(fixture.nativeElement.textContent).toContain(
      'Entre para escolher sua organização favorita',
    );
  });
});
