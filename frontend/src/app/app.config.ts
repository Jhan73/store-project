import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import localeEsPE from '@angular/common/locales/es-PE';
import {
  ApplicationConfig,
  LOCALE_ID,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideClientHydration, withIncrementalHydration } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth-interceptor';
import { errorInterceptor } from './core/errors/error-interceptor';
import { AppPreset } from './core/theme/app-preset';
import { DARK_CLASS } from './core/theme/theme-store';
import { primeTranslation } from './core/theme/prime-translation';

registerLocaleData(localeEsPE);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withFetch(), withInterceptors([errorInterceptor, authInterceptor])),
    provideClientHydration(withIncrementalHydration()),
    { provide: LOCALE_ID, useValue: 'es-PE' },
    MessageService,
    providePrimeNG({
      theme: { preset: AppPreset, options: { darkModeSelector: `.${DARK_CLASS}` } },
      translation: primeTranslation,
    }),
  ],
};
