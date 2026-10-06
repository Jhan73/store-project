import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  FormControl,
  FormGroup,
  FormsModule,
  NgModel,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import {
  IconChevronLeft,
  IconChevronRight,
  IconMail,
  IconPlayerPause,
  IconPlayerPlay,
  IconSend,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { Observable } from 'rxjs';
import type { Role, StaffMember, StaffPage } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { roleLabel } from '../../../core/auth/role-label';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatInStoreZone } from '../../../core/time/store-time';
import { isStale, reportFailure } from '../admin-errors';
import { PendingIds } from '../../../shared/state/pending-ids';
import { StaffApi } from './staff-api';

const PAGE_SIZE = 20;
const STAFF_ROLES: readonly Role[] = ['SERVER', 'CASHIER', 'ADMIN'];

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
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <h1 i18n="@@admin.users.title">Personal</h1>

    @if (notice()) {
      <p role="status">{{ notice() }}</p>
    }

    <table>
      <thead>
        <tr>
          <th i18n="@@admin.users.email">Correo</th>
          <th i18n="@@admin.users.role">Rol</th>
          <th i18n="@@admin.users.state">Estado</th>
          <th i18n="@@admin.users.created">Creada</th>
          <th><span class="sr-only" i18n="@@admin.users.actions">Acciones</span></th>
        </tr>
      </thead>
      <tbody>
        @for (member of current().content; track member.id) {
          <tr>
            <td>{{ member.email }}</td>
            <td>
              <p-select
                #roleSelect="ngModel"
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
                [ngModel]="member.role"
                [disabled]="pending.has(member.id) || isOwn(member)"
                [attr.data-testid]="'role-' + member.id"
                (onChange)="changeRole(member, $event.value, roleSelect)"
              />
            </td>
            <td>
              @if (member.active) {
                <span i18n="@@admin.users.active">Activa</span>
              } @else {
                <span class="muted" i18n="@@admin.users.inactive">Inactiva</span>
              }
            </td>
            <td>{{ created(member) }}</td>
            <td class="actions">
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
            </td>
          </tr>
        }
      </tbody>
    </table>

    @if (current().totalPages > 1) {
      <nav class="toolbar" aria-label="Paginación" i18n-aria-label="@@admin.users.paging">
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="previous-page"
          [disabled]="current().page === 0"
          (click)="go(current().page - 1)"
          pTooltip="Ir a la página anterior"
          i18n-pTooltip="@@admin.users.previous.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.previous" aria-hidden="true" />
          <span i18n="@@admin.users.previous">Anterior</span>
        </button>
        <span i18n="@@admin.users.pageOf">Página {{ current().page + 1 }} de {{ current().totalPages }}</span>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="next-page"
          [disabled]="current().page + 1 >= current().totalPages"
          (click)="go(current().page + 1)"
          pTooltip="Ir a la página siguiente"
          i18n-pTooltip="@@admin.users.next.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.next" aria-hidden="true" />
          <span i18n="@@admin.users.next">Siguiente</span>
        </button>
      </nav>
    }

    <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field wide">
        <label for="staff-email" i18n="@@admin.users.emailLabel">Correo de la nueva cuenta</label>
        <input
          pInputText
          id="staff-email"
          type="email"
          formControlName="email"
          autocomplete="off"
          [attr.aria-invalid]="invalid('email') ? 'true' : null"
          fluid
        />
        @if (invalid('email')) {
          <small class="error" i18n="@@admin.users.emailInvalid">Escribe un correo válido.</small>
        }
      </div>
      <div class="field">
        <label for="staff-role" id="staff-role-label" i18n="@@admin.users.roleLabel">Rol</label>
        <p-select
          inputId="staff-role"
          [ariaLabelledBy]="'staff-role-label'"
          optionLabel="label"
          optionValue="value"
          formControlName="role"
          [options]="roleOptions"
          [filter]="true"
          filterBy="label"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
        />
      </div>
      <p class="muted" i18n="@@admin.users.inviteHint">
        La persona recibirá un enlace por correo para crear su propia contraseña.
      </p>
      <div class="actions">
        <button
          pButton
          type="submit"
          [loading]="saving()"
          pTooltip="Enviar la invitación por correo"
          i18n-pTooltip="@@admin.users.invite.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.invite" aria-hidden="true" />
          <span i18n="@@admin.users.invite">Invitar</span>
        </button>
      </div>
    </form>
  `,
})
export class StaffList {
  private readonly api = inject(StaffApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly auth = inject(AuthStore);

  protected readonly icons = {
    deactivate: IconPlayerPause,
    reactivate: IconPlayerPlay,
    resend: IconSend,
    invite: IconMail,
    previous: IconChevronLeft,
    next: IconChevronRight,
  };
  protected readonly tips = {
    deactivate: $localize`:@@admin.users.deactivate.tooltip:Impedir que la cuenta inicie sesión`,
    reactivate: $localize`:@@admin.users.reactivate.tooltip:Permitir que la cuenta vuelva a iniciar sesión`,
  };

  protected readonly roleOptions = STAFF_ROLES.map((role) => ({
    value: role,
    label: roleLabel(role),
  }));
  protected readonly current = signal<StaffPage>({
    content: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });
  protected readonly timeZone = signal('');
  protected readonly notice = signal('');
  protected readonly saving = signal(false);
  protected readonly pending = new PendingIds();
  private readonly ownId = computed(() => this.auth.session()?.userId ?? null);
  private lastRequest = 0;
  protected readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    role: new FormControl<Role>('SERVER', { nonNullable: true }),
  });

  constructor() {
    this.go(0);
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

  protected go(page: number): void {
    const request = ++this.lastRequest;
    this.api.list(page, PAGE_SIZE).subscribe({
      next: (result) => {
        if (request === this.lastRequest) {
          this.current.set(result);
        }
      },
      error: (error: unknown) => {
        if (request === this.lastRequest) {
          this.notifier.show(error);
        }
      },
    });
  }

  protected changeRole(member: StaffMember, role: Role, control: NgModel): void {
    this.run(member, this.api.changeRole(member.id, role), {
      onDone: (saved) => this.replace(saved),
      onFail: () => {
        // The select already shows the new role; put it back to what the list holds now.
        control.control.setValue(this.held(member.id)?.role ?? member.role);
      },
    });
  }

  protected toggle(member: StaffMember): void {
    const request = member.active
      ? this.api.deactivate(member.id)
      : this.api.reactivate(member.id);
    this.run(member, request, { onDone: (saved) => this.replace(saved) });
  }

  protected resend(member: StaffMember): void {
    this.run(member, this.api.resendSetPasswordLink(member.id), {
      onDone: () => this.notice.set($localize`:@@admin.users.resent:Enlace reenviado a ${member.email}:email:.`),
    });
  }

  protected submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { email, role } = this.form.getRawValue();
    const address = email.trim();
    this.saving.set(true);
    this.api.create({ email: address, role }).subscribe({
      next: () => {
        this.saving.set(false);
        this.notice.set($localize`:@@admin.users.invited:Invitación enviada a ${address}:email:.`);
        this.form.reset({ email: '', role: 'SERVER' });
        this.go(this.current().page);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        reportFailure(this.notifier, error, this.form);
      },
    });
  }

  private run<T>(
    member: StaffMember,
    request: Observable<T>,
    handlers: { onDone: (result: T) => void; onFail?: () => void },
  ): void {
    if (this.pending.has(member.id)) {
      return;
    }
    this.pending.add(member.id);
    this.notice.set('');
    request.subscribe({
      next: (result) => {
        this.pending.delete(member.id);
        handlers.onDone(result);
      },
      error: (error: unknown) => {
        this.pending.delete(member.id);
        handlers.onFail?.();
        reportFailure(this.notifier, error);
        if (isStale(error)) {
          this.go(this.current().page);
        }
      },
    });
  }

  private held(id: string): StaffMember | undefined {
    return this.current().content.find((item) => item.id === id);
  }

  private replace(saved: StaffMember): void {
    this.current.update((page) => ({
      ...page,
      content: page.content.map((item) => (item.id === saved.id ? saved : item)),
    }));
  }
}
