import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicBracketPageComponent } from './public-bracket-page.component';
import { PublicBracketStore } from './public-bracket.store';

describe('PublicBracketPageComponent', () => {
  it('carrega por id opaco e renderiza somente a publicação', async () => {
    const store = {
      isLoading: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      data: signal({
        bracketId: 'b1',
        competitionId: 'c1',
        editionId: 'e1',
        name: 'Futsal',
        slug: 'futsal',
        status: 'PUBLICADO',
        lockVersion: 1,
        sourceReference: 'boletim',
        rounds: [],
      }),
      retry: vi.fn(),
      load: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [PublicBracketPageComponent],
      providers: [
        { provide: PublicBracketStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'b1' } } } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublicBracketPageComponent);
    fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('b1');
    expect(fixture.nativeElement.textContent).toContain('Futsal');
    expect(fixture.nativeElement.textContent).not.toContain('Rascunho');
  });
});
