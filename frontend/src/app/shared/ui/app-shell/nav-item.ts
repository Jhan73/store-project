import type { TablerIcon } from '@tabler/icons-angular';

export interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly icon: TablerIcon;
}
