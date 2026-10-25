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
import { Select } from 'primeng/select';
import { Tooltip } from 'primeng/tooltip';
import type { AuditEntry, AuditPage, StaffMember } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { dateToDay, dayToDate } from '../../../core/time/store-day';
import { formatInStoreZone } from '../../../core/time/store-time';
import { datePickerAriaInvalid } from '../../../shared/forms/aria-invalid';
import { DataColumn, DataTable, type DataTableLazyEvent } from '../../../shared/ui/data-table/data-table';
import { FormField } from '../../../shared/ui/form-field/form-field';
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
    Select,
    TablerIconComponent,
    DataTable,
    FormField,
    Tooltip,
    AuditDetail,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../admin-section.scss', './audit-log.scss'],
  template: `
    <h1 i18n="@@admin.audit.title">Auditoría</h1>

    <form class="filters" [formGroup]="form" (ngSubmit)="submit()" novalidate>
      <app-form-field
        label="Fechas"
        i18n-label="@@admin.audit.range"
        controlId="audit-range"
        [error]="errors().range ? labels.rangeOrder : null"
      >
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
      </app-form-field>
      <app-form-field label="Persona" i18n-label="@@admin.audit.actor" controlId="audit-actor">
        <p-select
          inputId="audit-actor"
          formControlName="actorId"
          [ariaLabel]="labels.actor"
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
      </app-form-field>
      <app-form-field
        label="Tipo de entidad"
        i18n-label="@@admin.audit.entityType"
        controlId="audit-entity-type"
      >
        <p-select
          inputId="audit-entity-type"
          formControlName="entityType"
          [ariaLabel]="labels.entityType"
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
      </app-form-field>
      <app-form-field
        label="Id de la entidad"
        i18n-label="@@admin.audit.entityId"
        controlId="audit-entity-id"
        [error]="errors().entityId ? labels.entityIdInvalid : null"
      >
        <input
          pInputText
          id="audit-entity-id"
          formControlName="entityId"
          autocomplete="off"
          maxlength="36"
          [attr.aria-invalid]="errors().entityId ? 'true' : null"
          fluid
        />
      </app-form-field>
      <app-form-field label="Acción" i18n-label="@@admin.audit.action" controlId="audit-action">
        <p-select
          inputId="audit-action"
          formControlName="action"
          [ariaLabel]="labels.action"
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
      </app-form-field>
      <div class="actions filter-actions">
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
        @if (current.totalElements >= totalCap) {
          <span i18n="@@admin.audit.count.capped">{{ totalCap }} registros o más</span>
        } @else {
          <span i18n="@@admin.audit.count">
            {current.totalElements, plural, =0 {Sin registros} =1 {1 registro} other {{{ current.totalElements }} registros}}
          </span>
        }
      }
    </p>

    @if (status() === 'error') {
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
    } @else {
      @if (status() === 'loading') {
        <p class="muted" i18n="@@admin.audit.loading">Cargando…</p>
      }
      @if (started()) {
        <app-data-table
          label="Registro de auditoría"
          i18n-label="@@admin.audit.tableScroll"
          [value]="result()?.content ?? []"
          [columns]="columns"
          [lazy]="true"
          [searchable]="false"
          [rows]="tableSize()"
          [first]="tableFirst()"
          [totalRecords]="result()?.totalElements ?? 0"
          [pageSizes]="pageSizes"
          [loading]="status() === 'loading'"
          [emptyText]="emptyText"
          (lazyLoad)="tableChanged($event)"
        >
          <ng-template #row let-entry>
            <td>
              <time [attr.datetime]="entry.occurredAt">{{ when(entry) }}</time>
            </td>
            <td>
              @if (actorText(entry); as text) {
                <span>{{ text }}</span>
              } @else if (entry.actorId) {
                <code class="id" [pTooltip]="entry.actorId" tooltipPosition="top">{{
                  short(entry.actorId)
                }}</code>
              } @else {
                <span>{{ roleName(entry) }}</span>
              }
              @if (liveAccount(entry); as live) {
                <small class="muted">{{ live }}</small>
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
              @if (entry.entityLabel) {
                <span class="label" [pTooltip]="entry.entityId" tooltipPosition="top">{{
                  entry.entityLabel
                }}</span>
              } @else {
                <code class="id" [pTooltip]="entry.entityId" tooltipPosition="top">{{
                  short(entry.entityId)
                }}</code>
              }
            </td>
            <td>{{ entry.reason ?? '—' }}</td>
            <td class="actions">
              <div class="row-actions">
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
              </div>
            </td>
          </ng-template>
        </app-data-table>
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
  // The API stops counting here, so a total at the cap means "at least".
  protected readonly totalCap = 10_000;
  protected readonly labels = {
    rangeOrder: $localize`:@@admin.audit.range.order:La fecha inicial no puede ser posterior a la final.`,
    entityIdInvalid: $localize`:@@admin.audit.entityId.invalid:Escribe un id válido (formato UUID).`,
    actor: $localize`:@@admin.audit.actor.aria:Persona`,
    entityType: $localize`:@@admin.audit.entityType.aria:Tipo de entidad`,
    action: $localize`:@@admin.audit.action.aria:Acción`,
  };
  protected readonly emptyText = $localize`:@@admin.audit.empty:No hay registros con estos filtros.`;
  // The API takes no sort, so the columns stay as the server returns them.
  protected readonly columns: readonly DataColumn[] = [
    { field: '', header: $localize`:@@admin.audit.col.when:Cuándo` },
    { field: '', header: $localize`:@@admin.audit.col.actor:Quién` },
    { field: '', header: $localize`:@@admin.audit.col.action:Acción` },
    { field: '', header: $localize`:@@admin.audit.col.entity:Entidad` },
    { field: '', header: $localize`:@@admin.audit.col.reason:Motivo` },
    { field: '', header: $localize`:@@admin.audit.col.detail:Acciones` },
  ];


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
  // The table is built after the URL was read, so its first event never overrides the page in the URL.
  protected readonly started = signal(false);
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
  protected readonly tableSize = computed(() => this.result()?.size ?? this.applied().size);
  protected readonly tableFirst = computed(
    () => (this.result()?.page ?? this.applied().page) * this.tableSize(),
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

  // The label stored with the row wins: the live account may have been renamed or deleted since.
  protected actorText(entry: AuditEntry): string | null {
    return entry.actorLabel ?? this.actorEmail(entry);
  }

  protected liveAccount(entry: AuditEntry): string | null {
    const live = this.actorEmail(entry);
    return entry.actorLabel && live && live !== entry.actorLabel ? live : null;
  }

  protected actorName(entry: AuditEntry): string {
    return this.actorText(entry) ?? (entry.actorId ? shortId(entry.actorId) : this.roleName(entry));
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

  protected tableChanged(event: DataTableLazyEvent): void {
    const size = event.rows ?? this.tableSize();
    const page = Math.floor((event.first ?? 0) / size);
    if (page * size === this.tableFirst() && size === this.tableSize()) {
      return;
    }
    this.navigate(filtersToParams({ ...this.applied(), page, size }));
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
    this.started.set(true);
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
