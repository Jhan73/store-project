import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import {
  IconMail,
  IconPencil,
  IconPlayerPause,
  IconPlayerPlay,
  IconSend,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import { tap, type Observable } from 'rxjs';
import type { Role, StaffMember } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { roleLabel } from '../../../core/auth/role-label';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatInStoreZone } from '../../../core/time/store-time';
import { isStale, reportFailure } from '../admin-errors';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { PendingIds } from '../../../shared/state/pending-ids';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UndoableChanges } from '../../../shared/state/undoable-changes';
import { DataColumn, DataTable } from '../../../shared/ui/data-table/data-table';
import { FormDialog } from '../../../shared/ui/form-dialog/form-dialog';
import { FormField } from '../../../shared/ui/form-field/form-field';
import { StatusTag } from '../../../shared/ui/status-tag/status-tag';
import { StaffApi } from './staff-api';

const STAFF_ROLES: readonly Role[] = ['SERVER', 'CASHIER', 'ADMIN'];
const USERNAME_MAX = 60;

function notBlank(control: AbstractControl<string>): ValidationErrors | null {
  return control.value.trim() === '' ? { blank: true } : null;
}

function usernameControl(): FormControl<string> {
  return new FormControl('', {
    nonNullable: true,
    validators: [notBlank, Validators.maxLength(USERNAME_MAX)],
  });
}

@Component({
  selector: 'app-staff-list',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    ButtonDirective,
    InputText,
    Select,
    TablerIconComponent,
    Tooltip,
    DataTable,
    FormDialog,
    FormField,
    StatusTag,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <div class="toolbar">
      <h1 i18n="@@admin.users.title">Personal</h1>
      <button
        pButton
        type="button"
        data-testid="invite-staff"
        (click)="add()"
        pTooltip="Enviar la invitación por correo"
        i18n-pTooltip="@@admin.users.invite.tooltip"
        tooltipPosition="top"
      >
        <tabler-icon [icon]="icons.invite" aria-hidden="true" />
        <span i18n="@@admin.users.invite">Invitar</span>
      </button>
    </div>

    @if (notice()) {
      <p role="status">{{ notice() }}</p>
    }

    <app-data-table
      label="Listado del personal"
      i18n-label="@@admin.users.tableScroll"
      [value]="rowsView()"
      [columns]="columns"
    >
      <ng-template #row let-member>
        <td>
          @if (member.username) {
            {{ member.username }}
          } @else {
            <app-status-tag [value]="labels.noUsername" severity="warn" />
          }
        </td>
        <td>{{ member.email }}</td>
        <td>
          <p-select
            [appendTo]="'body'"
            optionLabel="label"
            optionValue="value"
            [inputId]="'role-' + member.id"
            [ariaLabel]="roleControlLabel(member)"
            [options]="roleOptions"
            [filter]="true"
            filterBy="label"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            [ngModel]="member.shownRole"
            [disabled]="pending.has(member.id) || changes.has(member.id) || isOwn(member)"
            [attr.data-testid]="'role-' + member.id"
            (onChange)="changeRole(member, $event.value)"
          />
        </td>
        <td>
          <app-status-tag
            [value]="member.stateLabel"
            [severity]="member.active ? 'success' : 'secondary'"
          />
        </td>
        <td>{{ created(member) }}</td>
        <td class="actions">
          <div class="row-actions">
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [outlined]="true"
              [disabled]="pending.has(member.id)"
              [attr.data-testid]="'rename-' + member.id"
              (click)="startRename(member)"
              pTooltip="Cambiar el nombre de usuario con el que se muestra a esta persona"
              i18n-pTooltip="@@admin.users.rename.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.rename" aria-hidden="true" />
              <span i18n="@@admin.users.rename">Nombre</span>
            </button>
            <button
              pButton
              type="button"
              severity="secondary"
              [size]="'small'"
              [outlined]="true"
              [disabled]="pending.has(member.id) || isOwn(member)"
              [attr.data-testid]="'toggle-' + member.id"
              (click)="toggle(member)"
              [pTooltip]="member.active ? tips.deactivate : tips.reactivate"
              tooltipPosition="top"
            >
              @if (member.active) {
                <tabler-icon [icon]="icons.deactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.users.deactivate">Desactivar</ng-container>
              } @else {
                <tabler-icon [icon]="icons.reactivate" aria-hidden="true" />
                <ng-container i18n="@@admin.users.reactivate">Reactivar</ng-container>
              }
            </button>
            @if (member.active) {
              <button
                pButton
                type="button"
                severity="secondary"
                [size]="'small'"
                [outlined]="true"
                [disabled]="pending.has(member.id) || isOwn(member)"
                [attr.data-testid]="'resend-' + member.id"
                (click)="resend(member)"
                pTooltip="Enviar de nuevo el enlace para crear la contraseña"
                i18n-pTooltip="@@admin.users.resend.tooltip"
                tooltipPosition="top"
              >
                <tabler-icon [icon]="icons.resend" aria-hidden="true" />
                <span i18n="@@admin.users.resend">Reenviar enlace</span>
              </button>
            }
          </div>
        </td>
      </ng-template>
    </app-data-table>

    <app-form-dialog
      header="Invitar a una persona"
      i18n-header="@@admin.users.inviteHeader"
      [visible]="open()"
      [busy]="saving()"
      (saved)="submit()"
      (dismissed)="close()"
    >
      <div [formGroup]="form" class="dialog-fields">
        <app-form-field
          label="Nombre de usuario"
          i18n-label="@@admin.users.usernameLabel"
          controlId="staff-username"
          [error]="invalid('username') ? labels.usernameInvalid : null"
          [hint]="labels.usernameHint"
        >
          <input
            pInputText
            id="staff-username"
            formControlName="username"
            autocomplete="off"
            [attr.maxlength]="usernameMax"
            [attr.aria-invalid]="invalid('username') ? 'true' : null"
            fluid
          />
        </app-form-field>
        <app-form-field
          label="Correo de la nueva cuenta"
          i18n-label="@@admin.users.emailLabel"
          controlId="staff-email"
          [error]="invalid('email') ? labels.emailInvalid : null"
          [hint]="labels.inviteHint"
        >
          <input
            pInputText
            id="staff-email"
            type="email"
            formControlName="email"
            autocomplete="off"
            maxlength="254"
            [attr.aria-invalid]="invalid('email') ? 'true' : null"
            fluid
          />
        </app-form-field>
        <app-form-field label="Rol" i18n-label="@@admin.users.roleLabel" controlId="staff-role">
          <p-select
            [appendTo]="'body'"
            inputId="staff-role"
            [ariaLabel]="labels.role"
            optionLabel="label"
            optionValue="value"
            formControlName="role"
            [options]="roleOptions"
            [filter]="true"
            filterBy="label"
            [resetFilterOnHide]="true"
            filterPlaceholder="Buscar"
            i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>

    <app-form-dialog
      header="Nombre de usuario"
      i18n-header="@@admin.users.renameHeader"
      [visible]="renaming() !== null"
      [busy]="saving()"
      (saved)="submitRename()"
      (dismissed)="closeRename()"
    >
      <div [formGroup]="renameForm" class="dialog-fields">
        <app-form-field
          label="Nombre de usuario"
          i18n-label="@@admin.users.usernameLabel"
          controlId="staff-rename"
          [error]="renameInvalid() ? labels.usernameInvalid : null"
          [hint]="labels.usernameHint"
        >
          <input
            pInputText
            id="staff-rename"
            formControlName="username"
            autocomplete="off"
            [attr.maxlength]="usernameMax"
            [attr.aria-invalid]="renameInvalid() ? 'true' : null"
            fluid
          />
        </app-form-field>
      </div>
    </app-form-dialog>
  `,
})
export class StaffList {
  private readonly api = inject(StaffApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly auth = inject(AuthStore);
  private readonly confirm = inject(ConfirmAction);

  protected readonly icons = {
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    resend: IconSend,
    invite: IconMail,
    rename: IconPencil,
  };
  protected readonly usernameMax = USERNAME_MAX;
  protected readonly tips = {
    deactivate: $localize`:@@admin.users.deactivate.tooltip:Impedir que la cuenta inicie sesión`,
    reactivate: $localize`:@@admin.users.reactivate.tooltip:Permitir que la cuenta vuelva a iniciar sesión`,
  };
  protected readonly labels = {
    active: $localize`:@@admin.users.active:Activa`,
    inactive: $localize`:@@admin.users.inactive:Inactiva`,
    role: $localize`:@@admin.users.roleLabel:Rol`,
    noUsername: $localize`:@@admin.users.noUsername:Sin nombre`,
    usernameInvalid: $localize`:@@admin.users.usernameInvalid:Escribe un nombre de usuario de hasta 60 caracteres.`,
    usernameHint: $localize`:@@admin.users.usernameHint:El nombre con el que se muestra a esta persona. No sirve para iniciar sesión.`,
    emailInvalid: $localize`:@@admin.users.emailInvalid:Escribe un correo válido.`,
    inviteHint: $localize`:@@admin.users.inviteHint:La persona recibirá un enlace por correo para crear su propia contraseña.`,
  };
  protected readonly columns: readonly DataColumn[] = [
    {
      field: 'usernameText',
      header: $localize`:@@admin.users.username:Nombre de usuario`,
      sortable: true,
      filter: true,
    },
    { field: 'email', header: $localize`:@@admin.users.email:Correo`, sortable: true, filter: true },
    { field: 'roleText', header: $localize`:@@admin.users.role:Rol`, sortable: true },
    { field: 'stateLabel', header: $localize`:@@admin.users.state:Estado`, sortable: true },
    { field: 'createdAt', header: $localize`:@@admin.users.created:Creada`, sortable: true, searchable: false },
    { field: '', header: $localize`:@@admin.users.actions:Acciones` },
  ];
  protected readonly roleOptions = STAFF_ROLES.map((role) => ({
    value: role,
    label: roleLabel(role),
  }));

  protected readonly changes = new UndoableChanges<Role>(inject(UndoNotice));
  protected readonly members = signal<readonly StaffMember[]>([]);
  protected readonly timeZone = signal('');
  protected readonly notice = signal('');
  protected readonly open = signal(false);
  protected readonly renaming = signal<StaffMember | null>(null);
  protected readonly saving = signal(false);
  protected readonly pending = new PendingIds();
  protected readonly rowsView = computed(() =>
    this.members().map((member) => {
      const shownRole = this.changes.pending(member.id) ?? member.role;
      return {
        ...member,
        shownRole,
        usernameText: member.username ?? '',
        roleText: roleLabel(shownRole),
        stateLabel: member.active ? this.labels.active : this.labels.inactive,
      };
    }),
  );
  private readonly ownId = computed(() => this.auth.session()?.userId ?? null);
  private lastRequest = 0;
  protected readonly renameForm = new FormGroup({ username: usernameControl() });
  protected readonly form = new FormGroup({
    username: usernameControl(),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    role: new FormControl<Role>('SERVER', { nonNullable: true }),
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.changes.flush());
    this.load();
    this.api.timeZone().subscribe({
      next: (zone) => this.timeZone.set(zone),
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  protected roleControlLabel(member: StaffMember): string {
    return $localize`:@@admin.users.roleOf:Rol de ${member.email}:email:`;
  }

  protected created(member: StaffMember): string {
    return this.timeZone() ? formatInStoreZone(member.createdAt, this.timeZone()) : '';
  }

  protected isOwn(member: StaffMember): boolean {
    return member.id === this.ownId();
  }

  protected invalid(name: string): boolean {
    const control = this.form.get(name);
    return !!control && control.invalid && control.touched;
  }

  protected renameInvalid(): boolean {
    const control = this.renameForm.controls.username;
    return control.invalid && control.touched;
  }

  protected add(): void {
    this.form.reset({ username: '', email: '', role: 'SERVER' });
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
    this.form.reset({ username: '', email: '', role: 'SERVER' });
  }

  protected startRename(member: StaffMember): void {
    this.renameForm.reset({ username: member.username ?? '' });
    this.renaming.set(member);
  }

  protected closeRename(): void {
    this.renaming.set(null);
    this.renameForm.reset({ username: '' });
  }

  protected submitRename(): void {
    const member = this.renaming();
    if (!member || this.saving()) {
      return;
    }
    if (this.renameForm.invalid) {
      this.renameForm.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.api.rename(member.id, this.renameForm.getRawValue().username.trim()).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.replace(saved);
        this.closeRename();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error, this.renameForm);
        if (isStale(error)) {
          this.closeRename();
          this.load();
        }
      },
    });
  }

  protected changeRole(member: StaffMember, role: Role): void {
    if (role === member.role || this.pending.has(member.id)) {
      return;
    }
    this.changes.schedule(
      member.id,
      role,
      $localize`:@@admin.users.roleChanged:Rol de ${member.email}:email: cambiado a ${roleLabel(role)}:role:`,
      () => this.api.changeRole(member.id, role).pipe(tap((saved) => this.replace(saved))),
      (error) => this.fail(error),
    );
  }

  protected async toggle(member: StaffMember): Promise<void> {
    if (member.active) {
      const confirmed = await this.confirm.ask({
        header: $localize`:@@admin.users.deactivate.header:Desactivar cuenta`,
        message: $localize`:@@admin.users.deactivate.confirm:La cuenta ${member.email}:email: ya no podrá iniciar sesión y sus sesiones abiertas se cerrarán.`,
        acceptLabel: $localize`:@@admin.users.deactivate:Desactivar`,
      });
      if (!confirmed) {
        return;
      }
    }
    const request = member.active
      ? this.api.deactivate(member.id)
      : this.api.reactivate(member.id);
    this.run(member, request, (saved) => this.replace(saved));
  }

  protected resend(member: StaffMember): void {
    this.run(member, this.api.resendSetPasswordLink(member.id), () =>
      this.notice.set($localize`:@@admin.users.resent:Enlace reenviado a ${member.email}:email:.`),
    );
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, email, role } = this.form.getRawValue();
    const address = email.trim();
    this.saving.set(true);
    this.api.create({ username: username.trim(), email: address, role }).subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set($localize`:@@admin.users.invited:Invitación enviada a ${address}:email:.`);
        this.close();
        this.load();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error, this.form);
      },
    });
  }

  private run<T>(member: StaffMember, request: Observable<T>, onDone: (result: T) => void): void {
    if (this.pending.has(member.id) || this.changes.has(member.id)) {
      return;
    }
    this.pending.add(member.id);
    this.notice.set('');
    request.subscribe({
      next: (result) => {
        this.pending.delete(member.id);
        onDone(result);
      },
      error: (error: unknown) => {
        this.pending.delete(member.id);
        this.fail(error);
      },
    });
  }

  private fail(error: unknown): void {
    reportFailure(this.notifier, error);
    if (isStale(error)) {
      this.load();
    }
  }

  private load(): void {
    const request = ++this.lastRequest;
    this.api.all().subscribe({
      next: (list) => {
        if (request === this.lastRequest) {
          this.members.set(list);
        }
      },
      error: (error: unknown) => {
        if (request === this.lastRequest) {
          this.notifier.show(error);
        }
      },
    });
  }

  private replace(saved: StaffMember): void {
    this.members.update((list) => list.map((item) => (item.id === saved.id ? saved : item)));
  }
}
