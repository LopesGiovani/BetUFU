import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter, RouterOutlet } from '@angular/router';

import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  function renderHost() {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('creates the application', () => {
    const fixture = TestBed.createComponent(AppComponent);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders exactly one empty router outlet', () => {
    const fixture = renderHost();
    const element = fixture.nativeElement as HTMLElement;
    const outlets = fixture.debugElement.queryAll(By.directive(RouterOutlet));

    expect(outlets).toHaveLength(1);
    expect(element.querySelectorAll('router-outlet')).toHaveLength(1);
    expect(element.children).toHaveLength(1);
    expect(element.firstElementChild?.tagName.toLowerCase()).toBe('router-outlet');
    expect(element.textContent?.trim()).toBe('');
  });

  it('does not expose technical links, credentials, or future capabilities', () => {
    const fixture = renderHost();
    const element = fixture.nativeElement as HTMLElement;
    const renderedMarkup = element.outerHTML
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .toLowerCase();
    const forbiddenMarkers = [
      'tracer',
      'diagnostico',
      'esportes',
      'odds',
      'creditos',
      'palpites',
      'ranking',
      'grupos',
      'chat',
      'senha',
      'password',
      'oauth',
      'localstorage',
      'sessionstorage',
      'indexeddb',
      'authorization',
      'bearer',
      'http_interceptors',
      'http-interceptors',
      'httpinterceptors',
      'withinterceptors',
      'with-interceptors',
    ];

    expect(
      element.querySelector(
        'a, nav, form, input, button, select, textarea, [href], [src], [action], [formaction], [routerlink]',
      ),
    ).toBeNull();

    for (const marker of forbiddenMarkers) {
      expect(renderedMarkup).not.toContain(marker);
    }
  });
});
