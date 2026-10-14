import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LiveSync } from './live-sync';

describe('LiveSync', () => {
  function setup() {
    const connected = signal(false);
    const sync = TestBed.runInInjectionContext(
      () => new LiveSync({ isConnected: connected.asReadonly() }),
    );
    return { connected, sync };
  }

  it('stays disabled until a read that began on the live connection is applied', () => {
    const { connected, sync } = setup();
    expect(sync.enabled()).toBe(false);

    connected.set(true);
    expect(sync.enabled()).toBe(false);

    sync.applied(sync.begin());
    expect(sync.enabled()).toBe(true);
  });

  it('ignores a read that began while disconnected', () => {
    const { connected, sync } = setup();
    const read = sync.begin();
    connected.set(true);

    sync.applied(read);

    expect(sync.enabled()).toBe(false);
  });

  it('ignores a read that was overtaken by a disconnect', async () => {
    const { connected, sync } = setup();
    connected.set(true);
    TestBed.tick();
    const read = sync.begin();

    connected.set(false);
    TestBed.tick();
    connected.set(true);
    TestBed.tick();
    sync.applied(read);

    expect(sync.enabled()).toBe(false);
  });

  it('turns off on a disconnect and again on a failed read', () => {
    const { connected, sync } = setup();
    connected.set(true);
    sync.applied(sync.begin());

    sync.failed();
    expect(sync.enabled()).toBe(false);

    sync.applied(sync.begin());
    connected.set(false);
    expect(sync.enabled()).toBe(false);
  });
});
