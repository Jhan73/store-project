import { Routes } from '@angular/router';

export const STAFF_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('../workspace/workspace-layout').then((m) => m.WorkspaceLayout),
    children: [
      { path: '', loadComponent: () => import('./staff-home').then((m) => m.StaffHome) },
    ],
  },
];
