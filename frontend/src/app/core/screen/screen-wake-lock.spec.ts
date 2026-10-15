import { TestBed } from '@angular/core/testing';
import { ScreenWakeLock } from './screen-wake-lock';

class FakeSentinel extends EventTarget {
  released = false;
  readonly release = vi.fn(() => {
    this.released = true;
    return Promise.resolve();
  });

  // What the browser does when the page is hidden: it drops the lock on its own.
  dropByBrowser() {
    this.released = true;
    this.dispatchEvent(new Event('release'));
  }
}

describe('ScreenWakeLock', () => {
  let sentinels: FakeSentinel[];
  let request: ReturnType<typeof vi.fn>;
  let visibility: DocumentVisibilityState;

  const showPage = (state: DocumentVisibilityState) => {
    visibility = state;
    document.dispatchEvent(new Event('visibilitychange'));
  };

  const flush = () => new Promise((resolve) => setTimeout(resolve));

  beforeEach(() => {
    sentinels = [];
    visibility = 'visible';
    request = vi.fn(() => {
      const sentinel = new FakeSentinel();
      sentinels.push(sentinel);
      return Promise.resolve(sentinel);
    });
    Object.defineProperty(navigator, 'wakeLock', { configurable: true, value: { request } });
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
  });

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'wakeLock');
    Reflect.deleteProperty(document, 'visibilityState');
  });

  const create = () => {
    TestBed.configureTestingModule({ providers: [ScreenWakeLock] });
    return TestBed.inject(ScreenWakeLock);
  };

  it('asks the browser to keep the screen on', async () => {
    const lock = create();

    lock.start();
    await flush();

    expect(request).toHaveBeenCalledWith('screen');
    expect(lock.active()).toBe(true);
  });

  it('asks only once however many times it is started', async () => {
    const lock = create();

    lock.start();
    lock.start();
    await flush();

    expect(request).toHaveBeenCalledTimes(1);
  });

  it('does nothing when the browser has no wake lock', async () => {
    Reflect.deleteProperty(navigator, 'wakeLock');
    const lock = create();

    lock.start();
    await flush();

    expect(lock.active()).toBe(false);
    lock.stop();
  });

  it('takes the lock again when the page becomes visible after the browser dropped it', async () => {
    const lock = create();
    lock.start();
    await flush();

    showPage('hidden');
    sentinels[0].dropByBrowser();
    await flush();
    expect(lock.active()).toBe(false);
    expect(request).toHaveBeenCalledTimes(1);

    showPage('visible');
    await flush();

    expect(request).toHaveBeenCalledTimes(2);
    expect(lock.active()).toBe(true);
  });

  it('does not ask again while it still holds the lock', async () => {
    const lock = create();
    lock.start();
    await flush();

    showPage('visible');
    await flush();

    expect(request).toHaveBeenCalledTimes(1);
  });

  it('does not ask while the page is hidden', async () => {
    const lock = create();
    lock.start();
    await flush();
    sentinels[0].dropByBrowser();

    showPage('hidden');
    await flush();

    expect(request).toHaveBeenCalledTimes(1);
  });

  it('keeps trying on the next visibility change when the browser refuses', async () => {
    request.mockRejectedValueOnce(new DOMException('low battery', 'NotAllowedError'));
    const lock = create();

    lock.start();
    await flush();
    expect(lock.active()).toBe(false);

    showPage('visible');
    await flush();

    expect(lock.active()).toBe(true);
  });

  it('releases the lock and stops reacting to the page when stopped', async () => {
    const lock = create();
    lock.start();
    await flush();

    lock.stop();
    await flush();

    expect(sentinels[0].release).toHaveBeenCalled();
    expect(lock.active()).toBe(false);
    showPage('visible');
    await flush();
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('releases the lock when it is destroyed', async () => {
    const lock = create();
    lock.start();
    await flush();

    TestBed.resetTestingModule();
    await flush();

    expect(sentinels[0].release).toHaveBeenCalled();
    showPage('visible');
    await flush();
    expect(request).toHaveBeenCalledTimes(1);
    expect(lock.active()).toBe(false);
  });

  it('releases a lock that arrives after it was stopped', async () => {
    const lock = create();

    lock.start();
    lock.stop();
    await flush();

    expect(sentinels[0].release).toHaveBeenCalled();
    expect(lock.active()).toBe(false);
  });
});
