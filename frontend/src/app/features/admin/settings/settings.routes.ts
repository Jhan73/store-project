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
      {
        path: 'hours',
        loadComponent: () => import('./opening-hours-form').then((m) => m.OpeningHoursForm),
      },
      {
        path: 'zones',
        loadComponent: () => import('./zone-list').then((m) => m.ZoneList),
      },
      {
        path: 'reasons',
        loadComponent: () => import('./reason-list').then((m) => m.ReasonList),
      },
    ],
  },
];
