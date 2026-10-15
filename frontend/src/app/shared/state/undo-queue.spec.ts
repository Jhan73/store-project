import { UndoQueue } from './undo-queue';

describe('UndoQueue', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('sends a command only after the undo window', () => {
    const queue = new UndoQueue(5_000);
    const send = vi.fn();

    queue.schedule('a', send);
    vi.advanceTimersByTime(4_999);
    expect(send).not.toHaveBeenCalled();
    expect(queue.has('a')).toBe(true);

    vi.advanceTimersByTime(1);
    expect(send).toHaveBeenCalledTimes(1);
    expect(queue.has('a')).toBe(false);
  });

  it('defaults to a 5 second window', () => {
    const queue = new UndoQueue();
    const send = vi.fn();

    queue.schedule('a', send);
    vi.advanceTimersByTime(4_999);
    expect(send).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);

    expect(send).toHaveBeenCalledTimes(1);
  });

  it('never sends a command that was undone', () => {
    const queue = new UndoQueue(5_000);
    const send = vi.fn();
    queue.schedule('a', send);

    expect(queue.cancel('a')).toBe(true);
    vi.advanceTimersByTime(10_000);

    expect(send).not.toHaveBeenCalled();
    expect(queue.has('a')).toBe(false);
  });

  it('says nothing was undone when the command already left', () => {
    const queue = new UndoQueue(5_000);
    queue.schedule('a', vi.fn());
    vi.advanceTimersByTime(5_000);

    expect(queue.cancel('a')).toBe(false);
  });

  it('keeps one command per key', () => {
    const queue = new UndoQueue(5_000);
    const first = vi.fn();
    const second = vi.fn();

    expect(queue.schedule('a', first)).toBe(true);
    expect(queue.schedule('a', second)).toBe(false);
    vi.advanceTimersByTime(5_000);

    expect(first).toHaveBeenCalledTimes(1);
    expect(second).not.toHaveBeenCalled();
  });

  it('sends every pending command at once on flush and not again later', () => {
    const queue = new UndoQueue(5_000);
    const a = vi.fn();
    const b = vi.fn();
    queue.schedule('a', a);
    queue.schedule('b', b);

    queue.flush();

    expect(a).toHaveBeenCalledTimes(1);
    expect(b).toHaveBeenCalledTimes(1);
    expect(queue.has('a')).toBe(false);
    vi.advanceTimersByTime(10_000);
    expect(a).toHaveBeenCalledTimes(1);
    expect(b).toHaveBeenCalledTimes(1);
  });

  it('exposes the pending keys to a template', () => {
    const queue = new UndoQueue(5_000);
    queue.schedule('a', vi.fn());
    queue.schedule('b', vi.fn());

    expect(queue.keys()).toEqual(new Set(['a', 'b']));
    queue.cancel('a');
    expect(queue.keys()).toEqual(new Set(['b']));
  });
});
