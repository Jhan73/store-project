import { PLATFORM_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SidebarStore } from './sidebar-store';

describe('SidebarStore', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('starts as a rail, not pinned', () => {
    const store = TestBed.inject(SidebarStore);

    expect(store.pinned()).toBe(false);
    expect(store.expanded()).toBe(false);
  });

  it('expands temporarily without pinning', () => {
    const store = TestBed.inject(SidebarStore);

    store.toggle();

    expect(store.expanded()).toBe(true);
    expect(store.pinned()).toBe(false);
    expect(localStorage.getItem('app-sidebar-pinned')).toBeNull();
  });

  it('collapses a temporary expansion with the toggle', () => {
    const store = TestBed.inject(SidebarStore);
    store.toggle();

    store.toggle();

    expect(store.expanded()).toBe(false);
  });

  it('docks the sidebar when pinned and saves it', () => {
    const store = TestBed.inject(SidebarStore);
    store.toggle();

    store.togglePin();

    expect(store.pinned()).toBe(true);
    expect(store.expanded()).toBe(true);
    expect(localStorage.getItem('app-sidebar-pinned')).toBe('true');
  });

  it('unpins and returns to the rail when a pinned sidebar is collapsed', () => {
    const store = TestBed.inject(SidebarStore);
    store.toggle();
    store.togglePin();

    store.toggle();

    expect(store.pinned()).toBe(false);
    expect(store.expanded()).toBe(false);
    expect(localStorage.getItem('app-sidebar-pinned')).toBeNull();
  });

  it('stays expanded as a temporary overlay when unpinned with the pin button', () => {
    const store = TestBed.inject(SidebarStore);
    store.toggle();
    store.togglePin();

    store.togglePin();

    expect(store.pinned()).toBe(false);
    expect(store.expanded()).toBe(true);
  });

  it('dismisses a temporary expansion but never a pinned one', () => {
    const store = TestBed.inject(SidebarStore);
    store.toggle();
    store.dismiss();
    expect(store.expanded()).toBe(false);

    store.toggle();
    store.togglePin();
    store.dismiss();

    expect(store.pinned()).toBe(true);
    expect(store.expanded()).toBe(true);
  });

  it('restores a pinned sidebar as docked', () => {
    localStorage.setItem('app-sidebar-pinned', 'true');

    const store = TestBed.inject(SidebarStore);

    expect(store.pinned()).toBe(true);
    expect(store.expanded()).toBe(true);
  });

  it('never restores a temporary expansion', () => {
    const first = TestBed.inject(SidebarStore);
    first.toggle();
    TestBed.resetTestingModule();

    const second = TestBed.inject(SidebarStore);

    expect(second.expanded()).toBe(false);
  });

  it('keeps working when the storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked');
    });

    const store = TestBed.inject(SidebarStore);
    store.toggle();
    store.togglePin();

    expect(store.pinned()).toBe(true);
    store.toggle();
    expect(store.pinned()).toBe(false);
  });

  it('does not touch the storage on the server', () => {
    const read = vi.spyOn(Storage.prototype, 'getItem');
    const write = vi.spyOn(Storage.prototype, 'setItem');
    TestBed.configureTestingModule({ providers: [{ provide: PLATFORM_ID, useValue: 'server' }] });

    const store = TestBed.inject(SidebarStore);
    store.toggle();
    store.togglePin();

    expect(read).not.toHaveBeenCalled();
    expect(write).not.toHaveBeenCalled();
  });
});
