import { createEnvironmentInjector, EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AuthStore } from '../../core/auth/auth-store';
import { UndoNotice } from './undo-notice';
import { UNDO_WINDOW_MS } from './undo-queue';
import { UndoableChanges } from './undoable-changes';

describe('UndoableChanges', () => {
  let signOutHooks: Array<() => Promise<void> | void>;
  let leaveSignOut: ReturnType<typeof vi.fn>;
  let ensureFresh: ReturnType<typeof vi.fn>;
  let owner: EnvironmentInjector;

  beforeEach(() => {
    vi.useFakeTimers();
    signOutHooks = [];
    leaveSignOut = vi.fn();
    ensureFresh = vi.fn().mockResolvedValue(undefined);
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthStore,
          useValue: {
            ensureFresh,
            onSignOut: (hook: () => Promise<void> | void) => {
              signOutHooks.push(hook);
              return leaveSignOut;
            },
          },
        },
      ],
    });
  });

  afterEach(() => {
    if (!(owner as { destroyed?: boolean }).destroyed) {
      owner.destroy();
    }
    vi.useRealTimers();
  });

  function setup() {
    const notice = TestBed.inject(UndoNotice);
    owner = createEnvironmentInjector([], TestBed.inject(EnvironmentInjector));
    const changes = runInInjectionContext(owner, () => new UndoableChanges<boolean>(notice));
    return { notice, changes };
  }

  it('shows the new value while the command waits and drops it when undone', () => {
    const { notice, changes } = setup();
    const run = vi.fn(() => new Subject<void>());

    changes.schedule('a', false, 'Desactivada', run, vi.fn());

    expect(changes.pending('a')).toBe(false);
    notice.undo(notice.active()[0]);
    vi.advanceTimersByTime(10_000);
    expect(changes.pending('a')).toBeUndefined();
    expect(run).not.toHaveBeenCalled();
  });

  it('keeps the value while the request is in flight and drops it once it answers', () => {
    const { changes } = setup();
    const response = new Subject<void>();

    changes.schedule('a', false, 'Desactivada', () => response, vi.fn());
    vi.advanceTimersByTime(5_000);

    expect(changes.pending('a')).toBe(false);
    response.next();
    response.complete();
    expect(changes.pending('a')).toBeUndefined();
  });

  it('reports a failed request and drops the value', () => {
    const { changes } = setup();
    const response = new Subject<void>();
    const onError = vi.fn();

    changes.schedule('a', false, 'Desactivada', () => response, onError);
    vi.advanceTimersByTime(5_000);
    response.error(new Error('boom'));

    expect(onError).toHaveBeenCalledTimes(1);
    expect(changes.pending('a')).toBeUndefined();
  });

  it('flush sends what is waiting at once', () => {
    const { changes } = setup();
    const run = vi.fn(() => new Subject<void>());

    changes.schedule('a', false, 'Desactivada', run, vi.fn());
    changes.flush();

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('sends what is waiting when the page is hidden, so closing the tab does not lose it', () => {
    const { changes } = setup();
    const run = vi.fn(() => new Subject<void>());

    changes.schedule('a', false, 'Desactivada', run, vi.fn());
    window.dispatchEvent(new Event('pagehide'));

    expect(run).toHaveBeenCalledTimes(1);
  });

  it('stops listening to the page once its owner is destroyed', () => {
    const { changes } = setup();
    const run = vi.fn(() => new Subject<void>());

    changes.schedule('a', false, 'Desactivada', run, vi.fn());
    owner.destroy();
    window.dispatchEvent(new Event('pagehide'));

    expect(run).not.toHaveBeenCalled();
    expect(leaveSignOut).toHaveBeenCalledTimes(1);
  });

  it('sends what is waiting at sign-out and resolves only when the request was answered', async () => {
    const { changes } = setup();
    const response = new Subject<void>();
    const run = vi.fn(() => response);
    changes.schedule('a', false, 'Desactivada', run, vi.fn());

    let drained = false;
    const draining = Promise.resolve(signOutHooks[0]()).then(() => (drained = true));
    await Promise.resolve();

    expect(run).toHaveBeenCalledTimes(1);
    expect(drained).toBe(false);
    response.complete();
    await draining;
    expect(drained).toBe(true);
  });

  it('renews a token that would expire inside the undo window when a change is queued', () => {
    const { changes } = setup();

    changes.schedule('a', false, 'Desactivada', () => new Subject<void>(), vi.fn());

    expect(ensureFresh).toHaveBeenCalledWith(UNDO_WINDOW_MS + 30_000);
  });
});
