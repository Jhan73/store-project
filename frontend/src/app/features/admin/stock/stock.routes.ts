import { Routes } from '@angular/router';
import { StockApi } from './stock-api';

export const STOCK_ROUTES: Routes = [
  {
    path: '',
    providers: [StockApi],
    loadComponent: () => import('./stock-list').then((m) => m.StockList),
  },
];
