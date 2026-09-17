import { Injectable } from '@angular/core';

const MAX_TIMER_DELAY_MS = 2_147_483_647;

@Injectable({ providedIn: 'root' })
export class AppClock {
  now(): number {
    return Date.now();
  }

  scheduleAt(instant: number, callback: () => void): () => void {
    let cancelled = false;
    let timerId: ReturnType<typeof setTimeout> | null = null;

    const arm = (): void => {
      if (cancelled) return;
      const remaining = instant - this.now();
      if (remaining <= 0) {
        callback();
        return;
      }
      timerId = setTimeout(arm, Math.min(remaining, MAX_TIMER_DELAY_MS));
    };

    arm();
    return () => {
      cancelled = true;
      if (timerId !== null) clearTimeout(timerId);
    };
  }
}
