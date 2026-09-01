---
name: frontend-accessibility-wcag
description: Design and implement accessible, responsive and high-contrast web interfaces following WCAG 2.1 AA and modern UI design system tokens.
---

# Frontend Design & WCAG 2.1 AA Accessibility Guide

This skill guides the creation of responsive, beautiful, accessible, and high-performance UI components.

## 1. Core WCAG 2.1 AA Principles (POUR)

### Perceivable
- **Color Contrast:**
  - Normal text (< 18pt / < 14pt bold): minimum **4.5:1** contrast ratio.
  - Large text (≥ 18pt / ≥ 14pt bold) and UI components/borders: minimum **3.0:1** contrast ratio.
  - Do not rely on color alone to convey meaning (always pair status colors with text, icons, or badges).
- **Text Alternatives:** All meaningful images must have `alt` text; decorative icons should use `aria-hidden="true"`.

### Operable
- **Keyboard Navigation:**
  - All interactive elements (`<button>`, `<a>`, `<input>`, `<select>`) must be reachable via `Tab` and activatable via `Enter` or `Space`.
  - Visible focus indicator: never use `outline: none` without providing a distinct `:focus-visible` replacement (e.g. `outline: 2px solid var(--color-primary)`).
- **Target Size:** Interactive touch targets must be at least **44x44px** (WCAG 2.5.5 / 2.5.8).
- **No Keyboard Trap:** Focus must move logically without getting stuck.

### Understandable
- **Clear Form Labels:** Every input must have an associated `<label for="id">` or `aria-label`.
- **Live Regions:** Dynamic status changes and notifications must use `aria-live="polite"` or `role="status"` to announce updates to screen readers.
- **Error Messages:** Present human-readable, safe error messages with actionable steps.

### Robust
- **Semantic HTML:** Use native elements (`<header>`, `<main>`, `<nav>`, `<button>`, `<section>`, `<article>`) instead of nested `<div>`s with custom click handlers.

---

## 2. Design System Tokens Integration

Always use design tokens instead of hardcoded hex/px values:
```scss
.my-card {
  background: var(--color-surface-card);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border-default);
  border-radius: var(--radius-md);
  padding: var(--spacing-4);

  &:focus-visible {
    outline: 2px solid var(--color-brand-primary);
    outline-offset: 2px;
  }
}
```

---

## 3. Testing Accessibility

1. **Automated Audits:** Use axe-core or Playwright accessibility checks.
2. **Keyboard Traversal:** Test entire workflows using only `Tab`, `Shift+Tab`, `Enter`, `Space`, and `Escape`.
3. **Screen Reader Verification:** Verify that `aria-live` announces dynamic state changes (loading, success, errors).
