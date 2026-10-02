import type { Role } from '../api/api-types';

export function roleLabel(role: Role | null): string {
  switch (role) {
    case 'SERVER':
      return $localize`:@@role.server:Mozo`;
    case 'CASHIER':
      return $localize`:@@role.cashier:Cajero`;
    case 'ADMIN':
      return $localize`:@@role.admin:Administrador`;
    case 'CUSTOMER':
      return $localize`:@@role.customer:Cliente`;
    case null:
      return '';
  }
}
