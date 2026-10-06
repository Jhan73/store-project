import { IconAdjustments, IconBook2, IconHome, IconSettings, IconTable } from '@tabler/icons-angular';
import type { Role } from '../../core/api/api-types';
import type { NavItem } from '../../shared/ui/app-shell/nav-item';

interface RoleNavItem extends NavItem {
  readonly roles: readonly Role[];
}

// Each work package that adds a screen adds its entry here, so the menu never links to a page that does not exist.
const NAV_ITEMS: readonly RoleNavItem[] = [
  {
    path: '/staff',
    label: $localize`:@@workspace.nav.staff:Operación`,
    icon: IconHome,
    roles: ['SERVER', 'CASHIER', 'ADMIN'],
  },
  {
    path: '/admin',
    label: $localize`:@@workspace.nav.admin:Administración`,
    icon: IconSettings,
    exact: true,
    roles: ['ADMIN'],
  },
  {
    path: '/admin/catalog',
    label: $localize`:@@workspace.nav.catalog:Catálogo`,
    icon: IconBook2,
    roles: ['ADMIN'],
  },
  {
    path: '/admin/settings',
    label: $localize`:@@workspace.nav.settings:Configuración`,
    icon: IconAdjustments,
    roles: ['ADMIN'],
  },
  {
    path: '/admin/tables',
    label: $localize`:@@workspace.nav.tables:Mesas`,
    icon: IconTable,
    roles: ['ADMIN'],
  },
];

export function navFor(role: Role | null): NavItem[] {
  return NAV_ITEMS.filter((item) => role !== null && item.roles.includes(role)).map(
    ({ path, label, icon, exact }) => ({ path, label, icon, ...(exact && { exact }) }),
  );
}
