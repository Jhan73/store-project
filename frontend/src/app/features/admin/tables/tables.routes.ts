import { Routes } from '@angular/router';
import { TablesApi } from './tables-api';

export const TABLES_ROUTES: Routes = [
  {
    path: '',
    providers: [TablesApi],
    loadComponent: () => import('./table-list').then((m) => m.TableList),
  },
];
