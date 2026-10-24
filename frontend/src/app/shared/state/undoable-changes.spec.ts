import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { UndoNotice } from './undo-notice';
import { UndoableChanges } from './undoable-changes';

describe('UndoableChanges', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  function setup() {
    const notice = TestBed.inject(UndoNotice);
    return { notice, changes: new UndoableChanges<boolean>(notice) };
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
});
