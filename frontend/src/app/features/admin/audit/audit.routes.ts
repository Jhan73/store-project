import { Routes } from '@angular/router';
import { AuditApi } from './audit-api';

export const AUDIT_ROUTES: Routes = [
  {
    path: '',
    providers: [AuditApi],
    loadComponent: () => import('./audit-log').then((m) => m.AuditLog),
  },
];
