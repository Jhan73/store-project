import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { Toast } from 'primeng/toast';
import { AuthStore } from '../../core/auth/auth-store';
import { roleLabel } from '../../core/auth/role-label';
import { SidebarStore } from '../../core/layout/sidebar-store';
import { ThemeStore } from '../../core/theme/theme-store';
import { AppShell } from '../../shared/ui/app-shell/app-shell';
import { navFor } from './workspace-nav';

@Component({
  selector: 'app-workspace-layout',
  imports: [AppShell, RouterOutlet, Toast],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-shell
      [items]="items()"
      [roleLabel]="role()"
      [themeMode]="theme.mode()"
      [sidebarExpanded]="sidebar.expanded()"
      [sidebarPinned]="sidebar.pinned()"
      (sidebarToggle)="sidebar.toggle()"
      (sidebarPinToggle)="sidebar.togglePin()"
      (sidebarDismiss)="sidebar.dismiss()"
      (themeModeChange)="theme.setMode($event)"
      (logout)="signOut()"
    >
      <router-outlet />
    </app-shell>
    <p-toast />
  `,
})
export class WorkspaceLayout {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  protected readonly theme = inject(ThemeStore);
  protected readonly sidebar = inject(SidebarStore);

  protected readonly items = computed(() => navFor(this.auth.role()));
  protected readonly role = computed(() => roleLabel(this.auth.role()));

  protected async signOut(): Promise<void> {
    await this.auth.logout();
    await this.router.navigateByUrl('/login');
  }
}
