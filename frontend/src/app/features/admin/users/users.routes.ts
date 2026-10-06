import { Routes } from '@angular/router';
import { StaffApi } from './staff-api';

export const USERS_ROUTES: Routes = [
  {
    path: '',
    providers: [StaffApi],
    loadComponent: () => import('./staff-list').then((m) => m.StaffList),
  },
];
