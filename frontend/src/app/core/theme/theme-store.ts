import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'system';

export const DARK_CLASS = 'app-dark';
const STORAGE_KEY = 'app-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

@Injectable({ providedIn: 'root' })
export class ThemeStore {
  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly systemDark = signal(false);

  readonly mode = signal<ThemeMode>('system');
  readonly isDark = computed(
    () => this.mode() === 'dark' || (this.mode() === 'system' && this.systemDark()),
  );

  constructor() {
    if (!this.isBrowser) {
      return;
    }
    const query = matchMedia(DARK_QUERY);
    this.systemDark.set(query.matches);
    this.mode.set(this.readStoredMode());
    query.addEventListener('change', () => {
      this.systemDark.set(query.matches);
      this.apply();
    });
    this.apply();
  }

  setMode(mode: ThemeMode): void {
    this.mode.set(mode);
    this.store(mode);
    this.apply();
  }

  private apply(): void {
    this.document.documentElement.classList.toggle(DARK_CLASS, this.isDark());
  }

  private readStoredMode(): ThemeMode {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return stored === 'light' || stored === 'dark' ? stored : 'system';
    } catch {
      return 'system';
    }
  }

  private store(mode: ThemeMode): void {
    try {
      if (mode === 'system') {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, mode);
      }
    } catch {
      // Private mode or blocked storage: the choice just lasts for this page.
    }
  }
}
