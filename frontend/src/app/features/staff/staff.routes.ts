import { inject } from '@angular/core';
import { CanActivateFn, Router, Routes } from '@angular/router';
import { AuthStore } from '../../core/auth/auth-store';
import { homeRouteFor } from '../../core/auth/navigation';
import { roleGuard } from '../../core/auth/role-guard';
import { AvailabilityApi } from './availability/availability-api';
import { BoardApi } from './board/board-api';
import { RegisterApi } from './register/register-api';
import { TicketsApi } from './tickets/tickets-api';

// The kitchen role reaches only the board: every other screen of the area is for the floor.
const floorOnly = roleGuard('SERVER', 'CASHIER', 'ADMIN');

// The staff home is a page of floor shortcuts; the kitchen has nothing on it, so it goes straight to its own home.
const kitchenToItsHome: CanActivateFn = () => {
  const role = inject(AuthStore).role();
  return role === 'KITCHEN' ? inject(Router).createUrlTree([homeRouteFor(role)]) : true;
};

export const STAFF_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('../workspace/workspace-layout').then((m) => m.WorkspaceLayout),
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [kitchenToItsHome],
        loadComponent: () => import('./staff-home').then((m) => m.StaffHome),
      },
      {
        path: '',
        providers: [TicketsApi, RegisterApi],
        children: [
          {
            path: 'quick-sale',
            canMatch: [roleGuard('CASHIER', 'ADMIN')],
            loadComponent: () => import('./quick-sale/quick-sale').then((m) => m.QuickSale),
          },
          {
            path: 'register/shifts',
            canMatch: [roleGuard('ADMIN')],
            loadComponent: () => import('./register/open-shifts').then((m) => m.OpenShifts),
          },
          {
            path: 'register',
            canMatch: [roleGuard('CASHIER', 'ADMIN')],
            loadComponent: () => import('./register/register').then((m) => m.Register),
          },
          {
            path: 'tables',
            canMatch: [floorOnly],
            loadComponent: () => import('./tables/table-grid').then((m) => m.TableGrid),
          },
          {
            path: 'tickets/:id',
            canMatch: [floorOnly],
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
        canMatch: [floorOnly],
        providers: [AvailabilityApi],
        loadComponent: () => import('./availability/availability').then((m) => m.Availability),
      },
    ],
  },
];
