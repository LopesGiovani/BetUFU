import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { SportsBracketPageComponent } from './sports-bracket-page.component';
import { SportsBracketStore } from './sports-bracket.store';

describe('SportsBracketPageComponent', () => {
  it('carrega preview operacional pelo id e marca rascunho sem publicar', async () => {
    const store = { isLoading: signal(false), isMutating: signal(false), isDenied: signal(false), isNotFound: signal(false), isError: signal(false), errorMessage: signal(null), conflictMessage: signal(null), feedbackMessage: signal(null), events: signal([]), data: signal({ bracketId: 'b1', competitionId: 'c1', editionId: 'e1', name: 'Futsal', slug: 'futsal', status: 'RASCUNHO', lockVersion: 0, rounds: [] }), retry: vi.fn(), load: vi.fn(), update: vi.fn(), publish: vi.fn(), correct: vi.fn() };
    await TestBed.configureTestingModule({ imports: [SportsBracketPageComponent], providers: [{ provide: SportsBracketStore, useValue: store }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'b1' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(SportsBracketPageComponent); fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('b1');
    expect(fixture.nativeElement.textContent).toContain('Operação esportiva');
    expect(fixture.nativeElement.textContent).toContain('Pré-visualização operacional');
  });
});
