---
name: angular-signals-patterns
description: Best practices, architecture guidelines and reactive patterns for Angular 22 zoneless applications using Signals, Standalone Components, and RxJS adapters.
---

# Angular 22 Signals & Zoneless Architecture Patterns

This skill guides the creation of high-performance, maintainable, and type-safe Angular 22 applications.

## 1. Zoneless Architecture & Reactivity

Angular 22 applications in BETUFU run without Zone.js (`provideExperimentalZonelessChangeDetection()`). Change detection is triggered automatically by Signals.

### Rules of Reactivity:
1. **Use `signal()`, `computed()` and `effect()`:**
   - Primitive state should be stored in `signal()`.
   - Derived values must use `computed()` to avoid redundant calculations.
   - Side effects (e.g. logging or document title changes) should use `effect()`.

```typescript
@Injectable({ providedIn: 'root' })
export class MyFeatureStore {
  readonly #state = signal<FeatureState>({ data: null, loading: false, error: null });

  // Expose read-only signals
  readonly data = computed(() => this.#state().data);
  readonly isLoading = computed(() => this.#state().loading);
  readonly error = computed(() => this.#state().error);

  updateData(newData: Data) {
    this.#state.update((curr) => ({ ...curr, data: newData, loading: false }));
  }
}
```

---

## 2. Standalone Components & OnPush

- All components must be `standalone: true`.
- Prefer `changeDetection: ChangeDetectionStrategy.OnPush` for optimal rendering performance.
- Use native control flow syntax: `@if`, `@for`, `@switch` instead of structural directives (`*ngIf`, `*ngFor`).

```html
@if (store.isLoading()) {
  <div class="spinner" role="status">Carregando...</div>
} @else if (store.error()) {
  <div class="error-banner">{{ store.error() }}</div>
} @else {
  <div class="content-grid">
    @for (item of store.data(); track item.id) {
      <app-item-card [item]="item" />
    }
  </div>
}
```

---

## 3. RxJS & OpenAPI Adapter Integration

When integrating with generated OpenAPI clients or HTTP services:
1. Use `takeUntilDestroyed()` or convert Observables to Signals using `toSignal()`.
2. Do not leak raw HTTP Observables directly into templates.
3. Validate and map backend DTOs to view models in an Adapter layer before updating Store signals.
4. Retry policies: apply retries strictly to idempotent `GET` operations with exponential backoff (e.g. 2 retries, 250ms/500ms); **never** retry mutating calls (`POST`, `PUT`, `DELETE`).

---

## 4. Security & Build-Specific Routing

- **Never** store sensitive authentication tokens in browser storage (`localStorage`, `sessionStorage`, `document.cookie`).
- Maintain build-specific seams (`app.routes.development.ts` vs `app.routes.production.ts`) with `fileReplacements` in `angular.json` to guarantee that development/diagnostic tools are completely excluded from production bundles.
