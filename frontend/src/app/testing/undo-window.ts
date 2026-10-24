import { UNDO_WINDOW_MS } from '../shared/state/undo-queue';

// Clicks an action that waits out the undo window and lets the window pass, so its request goes out.
export function clickPastUndo(element: HTMLElement): void {
  vi.useFakeTimers();
  try {
    element.click();
    vi.advanceTimersByTime(UNDO_WINDOW_MS);
  } finally {
    vi.useRealTimers();
  }
}
