import type { TablerIcon } from '@tabler/icons-angular';

export interface NavItem {
  readonly path: string;
  readonly label: string;
  readonly icon: TablerIcon;
  // Active only on this exact URL, for an entry whose path is also the prefix of another entry.
  readonly exact?: boolean;
}
