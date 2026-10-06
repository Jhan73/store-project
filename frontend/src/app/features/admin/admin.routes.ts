import { Routes } from '@angular/router';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('../workspace/workspace-layout').then((m) => m.WorkspaceLayout),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./admin-home').then((m) => m.AdminHome) },
      {
        path: 'catalog',
        loadChildren: () => import('./catalog/catalog.routes').then((m) => m.CATALOG_ROUTES),
      },
      {
        path: 'settings',
        loadChildren: () => import('./settings/settings.routes').then((m) => m.SETTINGS_ROUTES),
      },
      {
        path: 'tables',
        loadChildren: () => import('./tables/tables.routes').then((m) => m.TABLES_ROUTES),
      },
    ],
  },
];
