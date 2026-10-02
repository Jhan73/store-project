import { Routes } from '@angular/router';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('../workspace/workspace-layout').then((m) => m.WorkspaceLayout),
    children: [
      { path: '', loadComponent: () => import('./admin-home').then((m) => m.AdminHome) },
    ],
  },
];
