import { TestBed } from '@angular/core/testing';
import { DARK_CLASS, ThemeStore } from './theme-store';

type MediaListener = () => void;

function stubSystemPreference(initiallyDark: boolean) {
  let dark = initiallyDark;
  const listeners = new Set<MediaListener>();
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({
      get matches() {
        return dark;
      },
      addEventListener: (_: string, listener: MediaListener) => listeners.add(listener),
      removeEventListener: (_: string, listener: MediaListener) => listeners.delete(listener),
    })),
  );
  return {
    change(next: boolean) {
      dark = next;
      listeners.forEach((listener) => listener());
    },
  };
}

describe('ThemeStore', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove(DARK_CLASS);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('follows the system preference when nothing is stored', () => {
    stubSystemPreference(true);

    const store = TestBed.inject(ThemeStore);

    expect(store.mode()).toBe('system');
    expect(store.isDark()).toBe(true);
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(true);
  });

  it('applies and persists an explicit choice, overriding the system', () => {
    stubSystemPreference(true);
    const store = TestBed.inject(ThemeStore);

    store.setMode('light');

    expect(store.isDark()).toBe(false);
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(false);
    expect(localStorage.getItem('app-theme')).toBe('light');
  });

  it('restores a stored choice', () => {
    stubSystemPreference(false);
    localStorage.setItem('app-theme', 'dark');

    const store = TestBed.inject(ThemeStore);

    expect(store.mode()).toBe('dark');
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(true);
  });

  it('ignores an invalid stored value', () => {
    stubSystemPreference(false);
    localStorage.setItem('app-theme', 'purple');

    expect(TestBed.inject(ThemeStore).mode()).toBe('system');
  });

  it('tracks system changes only while in system mode', () => {
    const system = stubSystemPreference(false);
    const store = TestBed.inject(ThemeStore);

    system.change(true);
    expect(store.isDark()).toBe(true);
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(true);

    store.setMode('light');
    system.change(true);
    expect(store.isDark()).toBe(false);
  });

  it('falls back to light when the browser cannot report a preference', () => {
    vi.stubGlobal('matchMedia', undefined);

    const store = TestBed.inject(ThemeStore);

    expect(store.isDark()).toBe(false);
    store.setMode('dark');
    expect(document.documentElement.classList.contains(DARK_CLASS)).toBe(true);
  });

  it('removes the stored choice when going back to system mode', () => {
    stubSystemPreference(false);
    const store = TestBed.inject(ThemeStore);
    store.setMode('dark');

    store.setMode('system');

    expect(localStorage.getItem('app-theme')).toBeNull();
  });
});
