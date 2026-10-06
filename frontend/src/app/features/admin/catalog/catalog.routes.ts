import { Routes } from '@angular/router';
import { CatalogApi } from './catalog-api';

export const CATALOG_ROUTES: Routes = [
  {
    path: '',
    providers: [CatalogApi],
    loadComponent: () => import('./catalog-layout').then((m) => m.CatalogLayout),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'categories' },
      {
        path: 'categories',
        loadComponent: () => import('./category-list').then((m) => m.CategoryList),
      },
      {
        path: 'stations',
        loadComponent: () => import('./station-list').then((m) => m.StationList),
      },
    ],
  },
];
