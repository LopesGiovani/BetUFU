import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { NotFoundPageComponent } from './not-found-page.component';

describe('NotFoundPageComponent', () => {
  it('explica a rota inexistente e oferece retorno acessivel para a Arena', async () => {
    await TestBed.configureTestingModule({
      imports: [NotFoundPageComponent],
      providers: [provideRouter([])],
    }).compileComponents();

    const fixture = TestBed.createComponent(NotFoundPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('main')).not.toBeNull();
    expect(compiled.querySelector('h1')?.textContent).toContain('Página não encontrada');
    expect(compiled.querySelector<HTMLAnchorElement>('.primary-action')?.getAttribute('href')).toBe(
      '/',
    );
    expect(document.activeElement).toBe(compiled.querySelector('h1'));
  });
});
