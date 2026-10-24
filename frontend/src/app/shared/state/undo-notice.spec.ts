import { TestBed } from '@angular/core/testing';
import { UndoNotice } from './undo-notice';
import { UndoQueue } from './undo-queue';

describe('UndoNotice', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('schedules the command on the queue and lists a notice while it waits', () => {
    const notice = TestBed.inject(UndoNotice);
    const queue = new UndoQueue();
    const send = vi.fn();

    expect(notice.schedule(queue, 'a', 'Quitada', send)).toBe(true);

    expect(notice.active().map((item) => item.message)).toEqual(['Quitada']);
    vi.advanceTimersByTime(5_000);
    expect(send).toHaveBeenCalledTimes(1);
    expect(notice.active()).toEqual([]);
  });

  it('does not duplicate a command already waiting under the same key', () => {
    const notice = TestBed.inject(UndoNotice);
    const queue = new UndoQueue();

    notice.schedule(queue, 'a', 'Quitada', vi.fn());

    expect(notice.schedule(queue, 'a', 'Quitada', vi.fn())).toBe(false);
    expect(notice.active()).toHaveLength(1);
  });

  it('undo cancels the command and drops the notice', () => {
    const notice = TestBed.inject(UndoNotice);
    const queue = new UndoQueue();
    const send = vi.fn();
    notice.schedule(queue, 'a', 'Quitada', send);

    notice.undo(notice.active()[0]);
    vi.advanceTimersByTime(10_000);

    expect(send).not.toHaveBeenCalled();
    expect(notice.active()).toEqual([]);
  });
});
