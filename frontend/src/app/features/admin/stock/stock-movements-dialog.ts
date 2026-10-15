import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { IconRefresh, IconX, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { Paginator, type PaginatorState } from 'primeng/paginator';
import { Tooltip } from 'primeng/tooltip';
import type {
  StaffMember,
  StockItem,
  StockMovement,
  StockMovementPage,
} from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { reasonLabel } from '../../../core/reasons/reason-rules';
import { formatInStoreZone } from '../../../core/time/store-time';
import { TableScroll } from '../../../shared/ui/table-scroll/table-scroll';
import { actorRoleLabel, shortId } from '../audit/audit-labels';
import { StockApi } from './stock-api';
import { movementTypeLabel } from './stock-labels';

type Status = 'loading' | 'ready' | 'error';

const PAGE_SIZE = 20;
const WHEN: Intl.DateTimeFormatOptions = { dateStyle: 'short', timeStyle: 'medium', hourCycle: 'h23' };
const SIGNED = new Intl.NumberFormat('es-PE', { signDisplay: 'exceptZero' });

// The ledger of one product, newest first.
@Component({
  selector: 'app-stock-movements-dialog',
  imports: [ButtonDirective, Dialog, Paginator, TableScroll, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: '../admin-section.scss',
  template: `
    <p-dialog
      header="Movimientos de stock"
      i18n-header="@@admin.stock.movements.title"
      [modal]="true"
      [dismissableMask]="true"
      [draggable]="false"
      [style]="{ width: 'min(60rem, calc(100vw - 1rem))' }"
      [visible]="visible()"
      (visibleChange)="visibleChange($event)"
    >
      <strong data-testid="movements-product">{{ productName() }}</strong>
      @switch (status()) {
        @case ('loading') {
          <p class="muted" i18n="@@admin.stock.movements.loading">Cargando…</p>
        }
        @case ('error') {
          <div role="alert">
            <p i18n="@@admin.stock.movements.error">No se pudieron cargar los movimientos.</p>
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="movements-retry"
              (click)="retry()"
              pTooltip="Volver a cargar los movimientos"
              i18n-pTooltip="@@admin.stock.movements.retry.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.retry" aria-hidden="true" />
              <span i18n="@@admin.stock.movements.retry">Reintentar</span>
            </button>
          </div>
        }
        @case ('ready') {
          @if (result(); as current) {
            @if (current.content.length === 0) {
              <p class="muted" data-testid="movements-empty" i18n="@@admin.stock.movements.empty">
                Este producto todavía no tiene movimientos.
              </p>
            } @else {
              <app-table-scroll label="Movimientos de stock" i18n-label="@@admin.stock.movements.tableScroll">
                <table>
                  <caption class="sr-only" i18n="@@admin.stock.movements.caption">
                    Movimientos del producto, del más reciente al más antiguo
                  </caption>
                  <thead>
                    <tr>
                      <th i18n="@@admin.stock.movements.col.when">Cuándo</th>
                      <th i18n="@@admin.stock.movements.col.type">Tipo</th>
                      <th i18n="@@admin.stock.movements.col.onHand">En existencia</th>
                      <th i18n="@@admin.stock.movements.col.reserved">Reservado</th>
                      <th i18n="@@admin.stock.movements.col.reason">Motivo</th>
                      <th i18n="@@admin.stock.movements.col.note">Nota</th>
                      <th i18n="@@admin.stock.movements.col.actor">Quién</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (entry of current.content; track entry.id) {
                      <tr>
                        <td>
                          <time [attr.datetime]="entry.occurredAt">{{ when(entry) }}</time>
                        </td>
                        <td>{{ typeName(entry) }}</td>
                        <td>{{ signed(entry.onHandDelta) }}</td>
                        <td>{{ signed(entry.reservedDelta) }}</td>
                        <td>{{ reason(entry) }}</td>
                        <td>{{ entry.note ?? '—' }}</td>
                        <td>{{ actor(entry) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </app-table-scroll>
              @if (current.totalPages > 1) {
                <p-paginator
                  [first]="current.page * current.size"
                  [rows]="current.size"
                  [totalRecords]="current.totalElements"
                  (onPageChange)="goTo($event)"
                />
              }
            }
          }
        }
      }
      <ng-template #footer>
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="movements-close"
          (click)="dismissed.emit()"
          pTooltip="Cerrar los movimientos"
          i18n-pTooltip="@@admin.stock.movements.close.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.close" aria-hidden="true" />
          <span i18n="@@admin.stock.movements.close">Cerrar</span>
        </button>
      </ng-template>
    </p-dialog>
  `,
})
export class StockMovementsDialog {
  readonly visible = input.required<boolean>();
  readonly item = input<StockItem | null>(null);
  readonly productName = input('');

  readonly dismissed = output<void>();

  private readonly api = inject(StockApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly icons = { retry: IconRefresh, close: IconX };
  private readonly movementsState = signal<Status>('loading');
  private readonly zoneFailed = signal(false);
  protected readonly result = signal<StockMovementPage | null>(null);
  private readonly timeZone = signal('');
  // Times need the store zone, so the table waits for it as well as for the page.
  protected readonly status = computed<Status>(() => {
    if (this.zoneFailed() || this.movementsState() === 'error') {
      return 'error';
    }
    return this.movementsState() === 'loading' || !this.timeZone() ? 'loading' : 'ready';
  });
  private readonly staff = signal<readonly StaffMember[]>([]);
  private readonly staffById = computed(() => new Map(this.staff().map((member) => [member.id, member])));
  private page = 0;
  private lastRequest = 0;
  private staffLoaded = false;

  constructor() {
    effect(() => {
      if (this.visible() && this.item()) {
        untracked(() => {
          this.page = 0;
          this.result.set(null);
          this.retry();
        });
      }
    });
  }

  protected when(entry: StockMovement): string {
    return formatInStoreZone(entry.occurredAt, this.timeZone(), WHEN);
  }

  protected typeName(entry: StockMovement): string {
    return movementTypeLabel(entry.type);
  }

  protected signed(delta: number): string {
    return SIGNED.format(delta);
  }

  protected reason(entry: StockMovement): string {
    return entry.reasonCode ? reasonLabel(entry.reasonCode) : '—';
  }

  protected actor(entry: StockMovement): string {
    if (entry.actorId) {
      return this.staffById().get(entry.actorId)?.email ?? shortId(entry.actorId);
    }
    return entry.actorRole ? actorRoleLabel(entry.actorRole) : '—';
  }

  protected visibleChange(visible: boolean): void {
    if (!visible) {
      this.dismissed.emit();
    }
  }

  protected goTo(event: PaginatorState): void {
    this.page = event.page ?? 0;
    this.load();
  }

  protected retry(): void {
    if (!this.timeZone()) {
      this.loadTimeZone();
    }
    if (!this.staffLoaded) {
      this.loadStaff();
    }
    this.load();
  }

  private load(): void {
    const target = this.item();
    if (!target) {
      return;
    }
    const request = ++this.lastRequest;
    this.movementsState.set('loading');
    this.api
      .movements(target.productId, this.page, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (request === this.lastRequest) {
            this.result.set(page);
            this.movementsState.set('ready');
          }
        },
        error: (error: unknown) => {
          if (request === this.lastRequest) {
            this.movementsState.set('error');
            this.notifier.show(error);
          }
        },
      });
  }

  private loadTimeZone(): void {
    this.zoneFailed.set(false);
    this.api
      .timeZone()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (zone) => this.timeZone.set(zone),
        error: (error: unknown) => {
          this.zoneFailed.set(true);
          this.notifier.show(error);
        },
      });
  }

  private loadStaff(): void {
    this.api
      .staffAccounts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (members) => {
          this.staffLoaded = true;
          this.staff.set(members);
        },
        error: (error: unknown) => this.notifier.show(error),
      });
  }
}
