import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { IconPlus, IconReceipt, IconRefresh, TablerIconComponent } from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Tooltip } from 'primeng/tooltip';
import type { Ticket } from '../../../core/api/api-types';
import { TableGridStore } from './table-grid-store';

@Component({
  selector: 'app-table-grid',
  imports: [ReactiveFormsModule, RouterLink, ButtonDirective, InputText, TablerIconComponent, Tooltip],
  providers: [TableGridStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './table-grid.scss',
  template: `
    <h1 i18n="@@staff.tables.title">Salón</h1>

    @if (store.failed()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.tables.failed"
          >No se pudieron cargar las mesas. Las acciones están deshabilitadas.</ng-container
        >
        <button
          pButton
          type="button"
          severity="secondary"
          data-testid="retry"
          (click)="store.load()"
          pTooltip="Volver a cargar las mesas"
          i18n-pTooltip="@@staff.tables.retry.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.retry" aria-hidden="true" />
          <span i18n="@@staff.tables.retry">Reintentar</span>
        </button>
      </div>
    } @else if (!store.connected()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.tables.offline"
          >Sin conexión en tiempo real. Las acciones se habilitan al reconectar.</ng-container
        >
      </div>
    } @else if (!store.enabled()) {
      <div class="offline" role="status" data-testid="offline-banner">
        <ng-container i18n="@@staff.tables.updating"
          >Actualizando las mesas. Las acciones se habilitan en unos segundos.</ng-container
        >
      </div>
    }

    <section [attr.aria-labelledby]="'tables-heading'">
      <h2 id="tables-heading" i18n="@@staff.tables.tables">Mesas</h2>
      <ul class="tiles">
        @for (tile of store.tiles(); track tile.id) {
          <li>
            @if (tile.status === 'FREE') {
              <button
                pButton
                type="button"
                severity="secondary"
                class="tile"
                [attr.data-testid]="'table-' + tile.id"
                [disabled]="!store.enabled() || store.pending.has(tile.id)"
                pTooltip="Abrir una cuenta en esta mesa"
                i18n-pTooltip="@@staff.tables.open.tooltip"
                tooltipPosition="top"
                (click)="openTable(tile.id)"
              >
                <tabler-icon [icon]="icons.open" aria-hidden="true" />
                <span class="body">
                  <span class="name">{{ tile.name }}</span>
                  @if (tile.area) {
                    <span class="area">{{ tile.area }}</span>
                  }
                  <span class="status free" i18n="@@staff.tables.status.free">Libre</span>
                </span>
              </button>
            } @else {
              <a
                pButton
                class="tile"
                [class.waiting]="tile.status === 'WAITING_FOR_PAYMENT'"
                [routerLink]="['/staff/tickets', tile.ticketId]"
                [attr.data-testid]="'table-' + tile.id"
                pTooltip="Abrir la cuenta de esta mesa"
                i18n-pTooltip="@@staff.tables.view.tooltip"
                tooltipPosition="top"
              >
                <tabler-icon [icon]="icons.ticket" aria-hidden="true" />
                <span class="body">
                  <span class="name">{{ tile.name }}</span>
                  @if (tile.area) {
                    <span class="area">{{ tile.area }}</span>
                  }
                  @if (tile.status === 'WAITING_FOR_PAYMENT') {
                    <span class="status waiting" i18n="@@staff.tables.status.waiting"
                      >Esperando el pago</span
                    >
                  } @else {
                    <span class="status occupied" i18n="@@staff.tables.status.occupied">Ocupada</span>
                  }
                  @if (tile.minutes !== null) {
                    <span class="elapsed" i18n="@@staff.tables.elapsed">{{ tile.minutes }} min</span>
                  }
                </span>
              </a>
            }
          </li>
        } @empty {
          @if (store.loaded()) {
            <li class="muted" i18n="@@staff.tables.empty">Todavía no hay mesas configuradas.</li>
          }
        }
      </ul>
    </section>

    <section [attr.aria-labelledby]="'counter-heading'">
      <h2 id="counter-heading" i18n="@@staff.tables.counter">Mostrador</h2>
      <form class="counter-form" (submit)="openCounter($event)">
        <label for="counter-name" i18n="@@staff.tables.counter.name">Nombre del cliente</label>
        <input
          pInputText
          id="counter-name"
          data-testid="counter-name"
          maxlength="100"
          autocomplete="off"
          [formControl]="counterName"
        />
        <button
          pButton
          type="submit"
          data-testid="open-counter"
          [disabled]="!store.enabled() || store.pending.has('counter') || blankName()"
          pTooltip="Abrir una cuenta de mostrador para este cliente"
          i18n-pTooltip="@@staff.tables.counter.open.tooltip"
          tooltipPosition="top"
        >
          <tabler-icon [icon]="icons.open" aria-hidden="true" />
          <span i18n="@@staff.tables.counter.open">Abrir cuenta</span>
        </button>
      </form>
      <ul class="counters">
        @for (ticket of store.counters(); track ticket.id) {
          <li>
            <a
              pButton
              severity="secondary"
              [routerLink]="['/staff/tickets', ticket.id]"
              [attr.data-testid]="'counter-' + ticket.id"
              pTooltip="Abrir la cuenta de este cliente"
              i18n-pTooltip="@@staff.tables.counter.view.tooltip"
              tooltipPosition="top"
            >
              <tabler-icon [icon]="icons.ticket" aria-hidden="true" />
              <span>{{ ticket.counterName }}</span>
            </a>
          </li>
        }
      </ul>
    </section>
  `,
})
export class TableGrid {
  protected readonly store = inject(TableGridStore);
  private readonly router = inject(Router);

  protected readonly icons = { open: IconPlus, ticket: IconReceipt, retry: IconRefresh };
  protected readonly counterName = new FormControl('', { nonNullable: true });

  protected blankName(): boolean {
    return this.counterName.value.trim() === '';
  }

  protected openTable(tableId: string): void {
    this.store.openTable(tableId, (ticket) => this.goTo(ticket));
  }

  protected openCounter(event: Event): void {
    event.preventDefault();
    if (this.blankName() || !this.store.enabled()) {
      return;
    }
    this.store.openCounter(this.counterName.value, (ticket) => {
      this.counterName.reset();
      this.goTo(ticket);
    });
  }

  private goTo(ticket: Ticket): void {
    void this.router.navigate(['/staff/tickets', ticket.id]);
  }
}
