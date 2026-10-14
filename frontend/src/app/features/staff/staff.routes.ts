import { Routes } from '@angular/router';
import { AvailabilityApi } from './availability/availability-api';
import { TicketsApi } from './tickets/tickets-api';

export const STAFF_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('../workspace/workspace-layout').then((m) => m.WorkspaceLayout),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./staff-home').then((m) => m.StaffHome) },
      {
        path: '',
        providers: [TicketsApi],
        children: [
          {
            path: 'tables',
            loadComponent: () => import('./tables/table-grid').then((m) => m.TableGrid),
          },
        ],
      },
      {
        path: 'availability',
        providers: [AvailabilityApi],
        loadComponent: () => import('./availability/availability').then((m) => m.Availability),
      },
    ],
  },
];
