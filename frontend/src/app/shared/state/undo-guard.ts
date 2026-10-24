import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';
import { defer, finalize, type Observable } from 'rxjs';
import { AuthStore } from '../../core/auth/auth-store';
import { UNDO_WINDOW_MS, type UndoQueue } from './undo-queue';

// A command flushed as the page closes cannot wait for a token refresh, so the token must outlive the undo window.
const TOKEN_MARGIN_MS = 30_000;

// Keeps what waits in an UndoQueue from being lost: it is sent when the page is hidden and at sign-out
// (while the bearer still works, waiting for the answers). Create it in an injection context.
export class UndoGuard {
  private readonly auth = inject(AuthStore);
  private readonly inFlight = new Set<Promise<void>>();

  constructor(private readonly queue: UndoQueue) {
    const page = inject(DOCUMENT).defaultView;
    const flush = () => this.queue.flush();
    page?.addEventListener('pagehide', flush);
    const leaveSignOut = this.auth.onSignOut(() => this.drain());
    inject(DestroyRef).onDestroy(() => {
      leaveSignOut();
      page?.removeEventListener('pagehide', flush);
    });
  }

  // Call when a command is queued.
  queued(): void {
    void this.auth.ensureFresh(UNDO_WINDOW_MS + TOKEN_MARGIN_MS);
  }

  // Registers the request so a sign-out can wait for its answer.
  track<T>(request: Observable<T>): Observable<T> {
    return defer(() => {
      let settle!: () => void;
      const answered = new Promise<void>((resolve) => (settle = resolve));
      this.inFlight.add(answered);
      void answered.then(() => this.inFlight.delete(answered));
      return request.pipe(finalize(settle));
    });
  }

  drain(): Promise<void> {
    this.queue.flush();
    return Promise.allSettled([...this.inFlight]).then(() => undefined);
  }
}
