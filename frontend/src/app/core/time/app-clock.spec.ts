import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppClock } from './app-clock';

describe('AppClock', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-15T11:00:00Z'));
  });

  afterEach(() => vi.useRealTimers());

  it('expoe o instante atual e executa a transicao no prazo exato', () => {
    const clock = new AppClock();
    const callback = vi.fn();

    clock.scheduleAt(Date.parse('2026-09-15T12:00:00Z'), callback);
    vi.advanceTimersByTime(3_599_999);
    expect(callback).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(callback).toHaveBeenCalledTimes(1);
    expect(clock.now()).toBe(Date.parse('2026-09-15T12:00:00Z'));
  });

  it('permite cancelar uma transicao pendente', () => {
    const clock = new AppClock();
    const callback = vi.fn();

    const cancel = clock.scheduleAt(Date.parse('2026-09-15T12:00:00Z'), callback);
    cancel();
    vi.advanceTimersByTime(3_600_000);

    expect(callback).not.toHaveBeenCalled();
  });
});
