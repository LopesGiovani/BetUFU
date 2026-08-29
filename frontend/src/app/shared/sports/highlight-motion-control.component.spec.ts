import { ComponentFixture, TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { HighlightMotionControlComponent } from './highlight-motion-control.component';

describe('HighlightMotionControlComponent', () => {
  it('expõe a preferência persistente da página com aria-pressed e rótulo explícito', async () => {
    await TestBed.configureTestingModule({
      imports: [HighlightMotionControlComponent],
    }).compileComponents();

    const fixture: ComponentFixture<HighlightMotionControlComponent> =
      TestBed.createComponent(HighlightMotionControlComponent);
    const emitted = vi.fn();
    fixture.componentInstance.pausedChange.subscribe(emitted);
    fixture.componentRef.setInput('paused', false);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(button.textContent).toContain('Pausar destaques');

    button.click();
    expect(emitted).toHaveBeenCalledWith(true);

    fixture.componentRef.setInput('paused', true);
    fixture.detectChanges();
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.textContent).toContain('Retomar destaques');
  });
});
