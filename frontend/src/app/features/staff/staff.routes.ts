import { Routes } from '@angular/router';
import { roleGuard } from '../../core/auth/role-guard';
import { AvailabilityApi } from './availability/availability-api';
import { BoardApi } from './board/board-api';
import { RegisterApi } from './register/register-api';
import { TicketsApi } from './tickets/tickets-api';

export const STAFF_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('../workspace/workspace-layout').then((m) => m.WorkspaceLayout),
    children: [
      { path: '', pathMatch: 'full', loadComponent: () => import('./staff-home').then((m) => m.StaffHome) },
      {
        path: '',
        providers: [TicketsApi, RegisterApi],
        children: [
          {
            path: 'register',
            canMatch: [roleGuard('CASHIER', 'ADMIN')],
            loadComponent: () => import('./register/register').then((m) => m.Register),
          },
          {
            path: 'tables',
            loadComponent: () => import('./tables/table-grid').then((m) => m.TableGrid),
          },
          {
            path: 'tickets/:id',
            loadComponent: () => import('./tickets/ticket-editor').then((m) => m.TicketEditor),
          },
        ],
      },
      {
        path: 'board',
        providers: [BoardApi],
        loadComponent: () => import('./board/board').then((m) => m.Board),
      },
      {
        path: 'availability',
        providers: [AvailabilityApi],
        loadComponent: () => import('./availability/availability').then((m) => m.Availability),
      },
    ],
  },
];
