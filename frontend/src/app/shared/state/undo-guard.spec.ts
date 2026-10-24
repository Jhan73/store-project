import { createEnvironmentInjector, EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { AuthStore } from '../../core/auth/auth-store';
import { UndoGuard } from './undo-guard';
import { UndoQueue } from './undo-queue';

describe('UndoGuard', () => {
  let owner: EnvironmentInjector;
  let renewal: Promise<void>;

  beforeEach(() => {
    renewal = Promise.reject(new Error('offline'));
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthStore,
          useValue: {
            ensureFresh: () => renewal,
            onSignOut: () => () => undefined,
          },
        },
      ],
    });
    owner = createEnvironmentInjector([], TestBed.inject(EnvironmentInjector));
  });

  afterEach(() => owner.destroy());

  it('handles a rejected token renewal and keeps queueing and flushing', async () => {
    const handled = vi.spyOn(renewal, 'then');
    const queue = new UndoQueue();
    const guard = runInInjectionContext(owner, () => new UndoGuard(queue));
    const send = vi.fn();

    guard.queued();
    queue.schedule('a', send);
    await guard.drain();

    expect(handled).toHaveBeenCalled();
    expect(send).toHaveBeenCalledTimes(1);
  });
});
