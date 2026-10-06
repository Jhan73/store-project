import { Routes } from '@angular/router';
import { SettingsApi } from './settings-api';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    providers: [SettingsApi],
    loadComponent: () => import('./settings-layout').then((m) => m.SettingsLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'general' },
      {
        path: 'general',
        loadComponent: () => import('./settings-form').then((m) => m.SettingsForm),
      },
    ],
  },
];
