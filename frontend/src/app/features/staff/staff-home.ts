import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import { AuthStore } from '../../core/auth/auth-store';
import { navFor } from '../workspace/workspace-nav';

@Component({
  selector: 'app-staff-home',
  imports: [RouterLink, ButtonDirective, Tooltip, TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: var(--space-4);
    }

    h1 {
      margin: 0;
    }

    ul {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
      gap: var(--space-3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .shortcut {
      width: 100%;
      min-height: 4.5rem;
      justify-content: flex-start;
    }
  `,
  template: `
    <h1 i18n="@@staff.home.title">Operación</h1>
    <ul>
      @for (item of shortcuts(); track item.path) {
        <li>
          <a
            pButton
            severity="secondary"
            class="shortcut"
            [routerLink]="item.path"
            [pTooltip]="item.label"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="item.icon" aria-hidden="true" />
            <span>{{ item.label }}</span>
          </a>
        </li>
      }
    </ul>
  `,
})
export class StaffHome {
  private readonly auth = inject(AuthStore);

  // The same entries as the menu, so a role never sees a shortcut it cannot open.
  protected readonly shortcuts = computed(() =>
    navFor(this.auth.role()).filter((item) => item.path.startsWith('/staff/')),
  );
}
