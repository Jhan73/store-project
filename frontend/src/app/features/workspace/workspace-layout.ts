import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';
import { MessageService } from 'primeng/api';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { Toast } from 'primeng/toast';
import { AuthStore } from '../../core/auth/auth-store';
import { roleLabel } from '../../core/auth/role-label';
import { SidebarStore } from '../../core/layout/sidebar-store';
import { ThemeStore } from '../../core/theme/theme-store';
import { AppShell } from '../../shared/ui/app-shell/app-shell';
import { UndoToast } from '../../shared/ui/undo-toast/undo-toast';
import { navFor } from './workspace-nav';

@Component({
  selector: 'app-workspace-layout',
  imports: [AppShell, RouterOutlet, Toast, ConfirmDialog, UndoToast],
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
    <p-confirmdialog />
    <app-undo-toast />
  `,
})
export class WorkspaceLayout {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly messages = inject(MessageService);
  protected readonly theme = inject(ThemeStore);
  protected readonly sidebar = inject(SidebarStore);

  protected readonly items = computed(() => navFor(this.auth.role()));
  protected readonly role = computed(() => roleLabel(this.auth.role()));

  protected async signOut(): Promise<void> {
    if (!(await this.auth.logout())) {
      this.messages.add({
        severity: 'error',
        summary: $localize`:@@shell.logout.failed:No se pudo cerrar la sesión. Inténtalo de nuevo.`,
      });
      return;
    }
    await this.router.navigateByUrl('/login');
  }
}
