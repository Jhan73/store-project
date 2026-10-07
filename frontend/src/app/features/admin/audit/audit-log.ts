import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import type { Subscription } from 'rxjs';
import { ActivatedRoute, Router, type Params } from '@angular/router';
import {
  IconEye,
  IconFilterOff,
  IconRefresh,
  IconSearch,
  IconX,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { DatePicker } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Paginator, type PaginatorState } from 'primeng/paginator';
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { AuditEntry, AuditPage, StaffMember } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { dateToDay, dayToDate } from '../../../core/time/store-day';
import { formatInStoreZone } from '../../../core/time/store-time';
import { datePickerAriaInvalid } from '../../../shared/forms/aria-invalid';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
import { AuditApi } from './audit-api';
import { AuditDetail } from './audit-detail';
import {
  type AuditFilterErrors,
  type AuditFilters,
  EMPTY_FILTERS,
  filterErrors,
  filtersToParams,
  filtersToQuery,
  PAGE_SIZES,
  paramsToFilters,
} from './audit-filters';
import {
  actorRoleLabel,
  AUDIT_ACTIONS,
  AUDIT_ENTITY_TYPES,
  entityTypeLabel,
  shortId,
} from './audit-labels';

type Status = 'idle' | 'loading' | 'ready' | 'error';

interface Option {
  readonly value: string;
  readonly label: string;
}

const WHEN: Intl.DateTimeFormatOptions = { dateStyle: 'short', timeStyle: 'medium', hourCycle: 'h23' };

@Component({
  selector: 'app-audit-log',
  imports: [
    ReactiveFormsModule,
    ButtonDirective,
    DatePicker,
    Dialog,
    InputText,
    Paginator,
    Select,
    TablerIconComponent,
    TableScroll,
    Tooltip,
    AuditDetail,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../admin-section.scss', './audit-log.scss'],
  template: `
    <h1 i18n="@@admin.audit.title">Auditoría</h1>

    <form class="filters" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <div class="field">
        <label for="audit-range" i18n="@@admin.audit.range">Fechas</label>
        <p-datepicker
          inputId="audit-range"
          formControlName="range"
          [selectionMode]="'range'"
          [dateFormat]="'dd/mm/yy'"
          placeholder="dd/mm/aaaa - dd/mm/aaaa"
          i18n-placeholder="@@admin.audit.range.placeholder"
          [showButtonBar]="true"
          [invalid]="!!errors().range"
          [pt]="rangeAria()"
          [appendTo]="'body'"
          fluid
        />
        @if (errors().range) {
          <small class="error" i18n="@@admin.audit.range.order">
            La fecha inicial no puede ser posterior a la final.
          </small>
        }
      </div>
      <div class="field">
        <label for="audit-actor" id="audit-actor-label" i18n="@@admin.audit.actor">Persona</label>
        <p-select
          inputId="audit-actor"
          formControlName="actorId"
          [ariaLabelledBy]="'audit-actor-label'"
          optionLabel="label"
          optionValue="value"
          [options]="actorOptions()"
          [filter]="true"
          filterBy="label"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          placeholder="Todas"
          i18n-placeholder="@@admin.audit.actor.placeholder"
          [showClear]="true"
          [appendTo]="'body'"
          fluid
        />
      </div>
      <div class="field">
        <label for="audit-entity-type" id="audit-entity-type-label" i18n="@@admin.audit.entityType">
          Tipo de entidad
        </label>
        <p-select
          inputId="audit-entity-type"
          formControlName="entityType"
          [ariaLabelledBy]="'audit-entity-type-label'"
          optionLabel="label"
          optionValue="value"
          [options]="entityTypeOptions()"
          [filter]="true"
          filterBy="label"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          placeholder="Todos"
          i18n-placeholder="@@admin.audit.entityType.placeholder"
          [showClear]="true"
          [appendTo]="'body'"
          fluid
        />
      </div>
      <div class="field">
        <label for="audit-entity-id" i18n="@@admin.audit.entityId">Id de la entidad</label>
        <input
          pInputText
          id="audit-entity-id"
          formControlName="entityId"
          autocomplete="off"
          maxlength="36"
          [attr.aria-invalid]="errors().entityId ? 'true' : null"
          [attr.aria-describedby]="errors().entityId ? 'audit-entity-id-error' : null"
          fluid
        />
        @if (errors().entityId) {
          <small class="error" id="audit-entity-id-error" i18n="@@admin.audit.entityId.invalid">
            Escribe un id válido (formato UUID).
          </small>
        }
      </div>
      <div class="field">
        <label for="audit-action" id="audit-action-label" i18n="@@admin.audit.action">Acción</label>
        <p-select
          inputId="audit-action"
          formControlName="action"
          [ariaLabelledBy]="'audit-action-label'"
          optionLabel="label"
          optionValue="value"
          [options]="actionOptions()"
          [filter]="true"
          filterBy="label"
          [resetFilterOnHide]="true"
          filterPlaceholder="Buscar"
          i18n-filterPlaceholder="@@shared.select.filterPlaceholder"
          placeholder="Todas"
          i18n-placeholder="@@admin.audit.action.placeholder"
          [showClear]="true"
          [appendTo]="'body'"
          fluid
        />
      </div>
      <div class="actions">
        <button
          pButton
          type="submit"
          data-testid="search"
          pTooltip="Buscar con estos filtros"
          i18n-pTooltip="@@admin.audit.search.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.search" aria-hidden="true" />
          <span i18n="@@admin.audit.search">Buscar</span>
        </button>
        <button
          pButton
          type="button"
          severity="secondary"
          [outlined]="true"
          data-testid="clear"
          (click)="clear()"
          pTooltip="Quitar todos los filtros y buscar de nuevo"
          i18n-pTooltip="@@admin.audit.clear.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.clear" aria-hidden="true" />
          <span i18n="@@admin.audit.clear">Limpiar</span>
        </button>
      </div>
    </form>

    <p class="count" role="status" [attr.aria-live]="'polite'">
      @if (status() === 'ready' && result(); as current) {
        <span i18n="@@admin.audit.count">
          {current.totalElements, plural, =0 {Sin registros} =1 {1 registro} other {{{ current.totalElements }} registros}}
        </span>
      }
    </p>

    @switch (status()) {
      @case ('loading') {
        <p class="muted" i18n="@@admin.audit.loading">Cargando…</p>
      }
      @case ('error') {
        <div class="state" role="alert">
          <p i18n="@@admin.audit.error">No se pudo cargar el registro de auditoría.</p>
          <button
            pButton
            type="button"
            severity="secondary"
            data-testid="retry"
            (click)="retry()"
            pTooltip="Volver a intentar la búsqueda"
            i18n-pTooltip="@@admin.audit.retry.tooltip"
            tooltipPosition="top"
          >
            <tabler-icon [icon]="icons.retry" aria-hidden="true" />
            <span i18n="@@admin.audit.retry">Reintentar</span>
          </button>
        </div>
      }
      @case ('ready') {
        @if (result(); as current) {
          @if (current.content.length === 0) {
            <p class="muted" i18n="@@admin.audit.empty">No hay registros con estos filtros.</p>
          } @else {
            <app-table-scroll label="Registro de auditoría" i18n-label="@@admin.audit.tableScroll">
              <table>
                <caption class="sr-only" i18n="@@admin.audit.caption">
                  Cambios registrados, del más reciente al más antiguo
                </caption>
                <thead>
                  <tr>
                    <th i18n="@@admin.audit.col.when">Cuándo</th>
                    <th i18n="@@admin.audit.col.actor">Quién</th>
                    <th i18n="@@admin.audit.col.action">Acción</th>
                    <th i18n="@@admin.audit.col.entity">Entidad</th>
                    <th i18n="@@admin.audit.col.reason">Motivo</th>
                    <th><span class="sr-only" i18n="@@admin.audit.col.detail">Detalle</span></th>
                  </tr>
                </thead>
                <tbody>
                  @for (entry of current.content; track entry.id) {
                    <tr>
                      <td>
                        <time [attr.datetime]="entry.occurredAt">{{ when(entry) }}</time>
                      </td>
                      <td>
                        @if (actorEmail(entry); as email) {
                          <span>{{ email }}</span>
                        } @else if (entry.actorId) {
                          <code class="id" [pTooltip]="entry.actorId" tooltipPosition="top">{{
                            short(entry.actorId)
                          }}</code>
                        } @else {
                          <span>{{ roleName(entry) }}</span>
                        }
                        @if (entry.actorId) {
                          <small class="muted">{{ roleName(entry) }}</small>
                        }
                      </td>
                      <td>
                        <code>{{ entry.action }}</code>
                      </td>
                      <td>
                        <span>{{ typeName(entry) }}</span>
                        <code class="id" [pTooltip]="entry.entityId" tooltipPosition="top">{{
                          short(entry.entityId)
                        }}</code>
                      </td>
                      <td>{{ entry.reason ?? '—' }}</td>
                      <td class="actions">
                        <button
                          pButton
                          type="button"
                          severity="secondary"
                          [size]="'small'"
                          [outlined]="true"
                          [attr.aria-haspopup]="'dialog'"
                          [attr.aria-expanded]="selected()?.id === entry.id"
                          [attr.aria-label]="detailLabel(entry)"
                          [attr.data-testid]="'detail-' + entry.id"
                          (click)="open(entry)"
                          pTooltip="Ver los valores antes y después del cambio"
                          i18n-pTooltip="@@admin.audit.detail.tooltip"
                          tooltipPosition="top"
                        >
                          <tabler-icon [icon]="icons.detail" aria-hidden="true" />
                          <span i18n="@@admin.audit.detail">Detalle</span>
                        </button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </app-table-scroll>
            <p-paginator
              [first]="current.page * current.size"
              [rows]="current.size"
              [totalRecords]="current.totalElements"
              [rowsPerPageOptions]="pageSizes"
              (onPageChange)="goTo($event)"
            />
          }
        }
      }
    }

    <p-dialog
      header="Detalle del cambio"
      i18n-header="@@admin.audit.detail.title"
      [modal]="true"
      [dismissableMask]="true"
      [draggable]="false"
      [style]="{ width: 'min(60rem, calc(100vw - 1rem))' }"
      [visible]="selected() !== null"
      (visibleChange)="closeIfHidden($event)"
    >
      @if (selected(); as entry) {
        <app-audit-detail [entry]="entry" [when]="when(entry)" [actor]="actorName(entry)" />
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="detail-close"
          (click)="close()"
          pTooltip="Cerrar el detalle"
          i18n-pTooltip="@@admin.audit.detail.close.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.close" aria-hidden="true" />
          <span i18n="@@admin.audit.detail.close">Cerrar</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class AuditLog {
  private readonly api = inject(AuditApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly icons = {
    search: IconSearch,
    clear: IconFilterOff,
    retry: IconRefresh,
    detail: IconEye,
    close: IconX,
  };
  protected readonly pageSizes = [...PAGE_SIZES];

  protected readonly form = new FormGroup({
    range: new FormControl<Date[] | null>(null),
    actorId: new FormControl<string | null>(null),
    entityType: new FormControl<string | null>(null),
    entityId: new FormControl('', { nonNullable: true }),
    action: new FormControl<string | null>(null),
  });

  protected readonly status = signal<Status>('loading');
  protected readonly result = signal<AuditPage | null>(null);
  protected readonly errors = signal<AuditFilterErrors>({});
  protected readonly selected = signal<AuditEntry | null>(null);
  private readonly applied = signal<AuditFilters>(EMPTY_FILTERS);
  private readonly timeZone = signal('');
  private readonly staff = signal<readonly StaffMember[]>([]);
  private readonly staffById = computed(() => new Map(this.staff().map((member) => [member.id, member])));
  private lastRequest = 0;
  private inFlight: Subscription | undefined;
  private listening = false;
  private actorsLoaded = false;

  protected readonly actorOptions = computed<Option[]>(() => {
    const options = this.staff()
      .map((member) => ({ value: member.id, label: member.email }))
      .sort((a, b) => a.label.localeCompare(b.label));
    const chosen = this.applied().actorId;
    return chosen && !this.staffById().has(chosen)
      ? [...options, { value: chosen, label: shortId(chosen) }]
      : options;
  });
  protected readonly entityTypeOptions = computed<Option[]>(() =>
    withExtra(AUDIT_ENTITY_TYPES, this.applied().entityType).map((value) => ({
      value,
      label: entityTypeLabel(value),
    })),
  );
  protected readonly actionOptions = computed<Option[]>(() =>
    withExtra(AUDIT_ACTIONS, this.applied().action).map((value) => ({ value, label: value })),
  );
  protected readonly rangeAria = computed(() => datePickerAriaInvalid(!!this.errors().range));

  constructor() {
    this.loadActors();
    this.loadTimeZone();
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (Object.keys(this.errors()).length > 0) {
        this.errors.set(filterErrors(this.formFilters(this.applied().size)));
      }
    });
  }

  protected when(entry: AuditEntry): string {
    return formatInStoreZone(entry.occurredAt, this.timeZone(), WHEN);
  }

  protected actorEmail(entry: AuditEntry): string | null {
    return (entry.actorId && this.staffById().get(entry.actorId)?.email) || null;
  }

  protected actorName(entry: AuditEntry): string {
    return this.actorEmail(entry) ?? (entry.actorId ? shortId(entry.actorId) : this.roleName(entry));
  }

  protected roleName(entry: AuditEntry): string {
    return actorRoleLabel(entry.actorRole);
  }

  protected typeName(entry: AuditEntry): string {
    return entityTypeLabel(entry.entityType);
  }

  protected short(id: string): string {
    return shortId(id);
  }

  protected detailLabel(entry: AuditEntry): string {
    return $localize`:@@admin.audit.detail.aria:Detalle del cambio ${entry.action}:action: del ${this.when(entry)}:when:`;
  }

  protected open(entry: AuditEntry): void {
    this.selected.set(entry);
  }

  protected close(): void {
    this.selected.set(null);
  }

  protected closeIfHidden(visible: boolean): void {
    if (!visible) {
      this.close();
    }
  }

  protected submit(): void {
    const next = this.formFilters(this.applied().size);
    const found = filterErrors(next);
    this.errors.set(found);
    if (Object.keys(found).length === 0) {
      this.navigate(filtersToParams(next));
    }
  }

  protected clear(): void {
    this.errors.set({});
    this.navigate({});
  }

  protected goTo(event: PaginatorState): void {
    const size = event.rows ?? this.applied().size;
    this.navigate(filtersToParams({ ...this.applied(), page: event.page ?? 0, size }));
  }

  protected retry(): void {
    if (!this.actorsLoaded) {
      this.loadActors();
    }
    if (this.timeZone()) {
      this.search();
    } else {
      this.loadTimeZone();
    }
  }

  private navigate(queryParams: Params): void {
    if (JSON.stringify(queryParams) === JSON.stringify(filtersToParams(this.applied()))) {
      this.apply(this.applied());
      return;
    }
    void this.router.navigate([], { relativeTo: this.route, queryParams });
  }

  private loadActors(): void {
    this.api
      .staffAccounts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (members) => {
        this.actorsLoaded = true;
        this.staff.set(members);
      },
      error: (error: unknown) => this.notifier.show(error),
    });
  }

  private loadTimeZone(): void {
    this.status.set('loading');
    this.api
      .timeZone()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (zone) => {
        this.timeZone.set(zone);
        this.listen();
      },
      error: (error: unknown) => {
        this.status.set('error');
        this.notifier.show(error);
      },
    });
  }

  // The URL is the source of truth: a search, a shared link and the back button all arrive here.
  private listen(): void {
    if (this.listening) {
      return;
    }
    this.listening = true;
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => this.apply(paramsToFilters(params)));
  }

  private apply(filters: AuditFilters): void {
    this.applied.set(filters);
    const from = filters.from ? dayToDate(filters.from) : null;
    const to = filters.to ? dayToDate(filters.to) : null;
    this.form.setValue(
      {
        range: from ? [from, ...(to ? [to] : [])] : null,
        actorId: filters.actorId,
        entityType: filters.entityType,
        entityId: filters.entityId,
        action: filters.action,
      },
      { emitEvent: false },
    );
    const found = filterErrors(filters);
    this.errors.set(found);
    if (Object.keys(found).length > 0) {
      this.lastRequest++;
      this.inFlight?.unsubscribe();
      this.result.set(null);
      this.status.set('idle');
      return;
    }
    this.search();
  }

  private search(): void {
    const request = ++this.lastRequest;
    this.status.set('loading');
    this.inFlight?.unsubscribe();
    this.inFlight = this.api
      .search(filtersToQuery(this.applied(), this.timeZone()))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (page) => {
        if (request !== this.lastRequest) {
          return;
        }
        const lastPage = page.totalPages - 1;
        const pastTheEnd =
          page.content.length === 0 && page.totalElements > 0 && lastPage >= 0 && lastPage < this.applied().page;
        if (pastTheEnd) {
          void this.router.navigate([], {
            relativeTo: this.route,
            queryParams: filtersToParams({ ...this.applied(), page: lastPage }),
            replaceUrl: true,
          });
          return;
        }
        this.result.set(page);
        this.status.set('ready');
      },
      error: (error: unknown) => {
        if (request === this.lastRequest) {
          this.status.set('error');
          this.notifier.show(error);
        }
      },
    });
  }

  // Any edit of the form starts again from the first page.
  private formFilters(size: number): AuditFilters {
    const { range, actorId, entityType, entityId, action } = this.form.getRawValue();
    const [start, end] = range ?? [];
    return {
      from: start ? dateToDay(start) : null,
      to: start ? dateToDay(end ?? start) : null,
      actorId,
      entityType,
      entityId: entityId.trim(),
      action,
      page: 0,
      size,
    };
  }
}

function withExtra(known: readonly string[], extra: string | null): string[] {
  return extra && !known.includes(extra) ? [...known, extra] : [...known];
}
