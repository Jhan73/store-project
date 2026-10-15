import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import {
  IconAlertTriangle,
  IconArrowBackUp,
  IconBan,
  IconCheck,
  IconChecks,
  IconPackageExport,
  IconPlayerPlay,
  IconRotate2,
  TablerIconComponent,
} from '@tabler/icons-angular';
import { ButtonDirective } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';
import type { BoardLine, BoardMarker, BoardOrder } from '../../../core/api/api-types';
import { formatInStoreZone } from '../../../core/time/store-time';

export type BoardCardLevel = 'normal' | 'warning' | 'late';

const CHANNELS = {
  TABLE: $localize`:@@board.card.channel.table:Mesa`,
  COUNTER: $localize`:@@board.card.channel.counter:Mostrador`,
  PICKUP: $localize`:@@board.card.channel.pickup:Recojo`,
  DELIVERY: $localize`:@@board.card.channel.delivery:Delivery`,
} as const;

const MARKER_LABELS = {
  NEW: $localize`:@@board.card.marker.new:NUEVO`,
  MODIFIED: $localize`:@@board.card.marker.modified:MODIFICADO`,
  VOID: $localize`:@@board.card.marker.void:ANULADO`,
} as const;

@Component({
  selector: 'app-board-card',
  imports: [ButtonDirective, NgTemplateOutlet, TablerIconComponent, Tooltip],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board-card.scss',
  template: `
    <article [attr.data-testid]="'card-' + order().id">
      <header [class]="level()">
        <span class="number">{{ order().shortNumber }}</span>
        <span class="who">
          {{ channel() }}@if (order().label) {
            · {{ order().label }}
          }
        </span>
        <span class="age">
          <span class="elapsed" data-testid="elapsed" i18n="@@board.card.elapsed">{{ minutes() }} min</span>
          @if (level() === 'late') {
            <span class="level" data-testid="level" i18n="@@board.card.level.late">Atrasado</span>
          } @else if (level() === 'warning') {
            <span class="level" data-testid="level" i18n="@@board.card.level.warning">Atención</span>
          }
        </span>
        @if (scheduledTime(); as time) {
          <span class="scheduled" data-testid="scheduled" i18n="@@board.card.scheduled"
            >Programado {{ time }}</span
          >
        }
      </header>

      @for (item of headerMarkers(); track item.id) {
        <div class="marker" [class]="item.kind.toLowerCase()" [attr.data-testid]="'marker-' + item.id">
          <span class="label">{{ markerLabel(item) }}</span>
          <ng-container [ngTemplateOutlet]="ack" [ngTemplateOutletContext]="{ marker: item }" />
        </div>
      }

      <ul class="lines">
        @for (item of order().lines; track item.id) {
          <li
            class="line"
            [class.allergy]="item.allergy"
            [class.voided]="item.state === 'VOIDED'"
            [attr.data-testid]="'line-' + item.id"
          >
            <div class="what">
              <span class="quantity">{{ item.quantity }}×</span>
              <span class="name">{{ item.displayName }}</span>
              @if (item.allergy) {
                <span class="badge allergy-badge" data-testid="allergy">
                  <tabler-icon [icon]="icons.allergy" aria-hidden="true" />
                  <span i18n="@@board.card.allergy">Alergia</span>
                </span>
              }
            </div>
            @if (item.modifiersText) {
              <div class="modifiers">{{ item.modifiersText }}</div>
            }
            @if (item.note) {
              <div class="note">{{ item.note }}</div>
            }
            <div class="line-state">
              @if (stateOf(item); as state) {
                <span class="state" [class]="state" [attr.data-testid]="'line-state-' + item.id">
                  @switch (state) {
                    @case ('ready') {
                      <tabler-icon [icon]="icons.ready" aria-hidden="true" />
                      <span i18n="@@board.card.line.ready">Lista</span>
                    }
                    @case ('flagged') {
                      <tabler-icon [icon]="icons.flag" aria-hidden="true" />
                      <span i18n="@@board.card.line.flagged">Agotado</span>
                    }
                    @case ('voided') {
                      <tabler-icon [icon]="icons.flag" aria-hidden="true" />
                      <span i18n="@@board.card.line.voided">Anulada</span>
                    }
                    @case ('comped') {
                      <span i18n="@@board.card.line.comped">Cortesía</span>
                    }
                  }
                </span>
              }
              @for (tag of markersOf(item); track tag.id) {
                <span class="marker inline" [class]="tag.kind.toLowerCase()" [attr.data-testid]="'marker-' + tag.id">
                  <span class="label">{{ markerLabel(tag) }}</span>
                  <ng-container [ngTemplateOutlet]="ack" [ngTemplateOutletContext]="{ marker: tag }" />
                </span>
              }
              @if (queued().has(item.id)) {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [attr.data-testid]="'undo-line-' + item.id"
                  pTooltip="Cancelar el aviso de línea lista antes de que se envíe"
                  i18n-pTooltip="@@board.card.line.undo.tooltip"
                  tooltipPosition="top"
                  (click)="undo.emit(item.id)"
                >
                  <tabler-icon [icon]="icons.undo" aria-hidden="true" />
                  <span i18n="@@board.card.line.undo">Deshacer</span>
                </button>
              } @else if (mayReadyLine(item)) {
                <button
                  pButton
                  type="button"
                  [attr.data-testid]="'ready-line-' + item.id"
                  [disabled]="!enabled() || pending().has(item.id)"
                  pTooltip="Marcar esta línea como lista"
                  i18n-pTooltip="@@board.card.line.ready.tooltip"
                  tooltipPosition="top"
                  (click)="readyLine.emit(item.id)"
                >
                  <tabler-icon [icon]="icons.ready" aria-hidden="true" />
                  <span i18n="@@board.card.line.readyAction">Lista</span>
                </button>
              }
              @if (mayFlag(item)) {
                <button
                  pButton
                  type="button"
                  severity="secondary"
                  [attr.data-testid]="'flag-' + item.id"
                  [disabled]="!enabled() || pending().has(item.id)"
                  pTooltip="Avisar que este producto se agotó"
                  i18n-pTooltip="@@board.card.line.flag.tooltip"
                  tooltipPosition="top"
                  (click)="flag.emit(item.id)"
                >
                  <tabler-icon [icon]="icons.flag" aria-hidden="true" />
                  <span i18n="@@board.card.line.flag">Agotado</span>
                </button>
              }
            </div>
          </li>
        }
      </ul>

      <footer>
        @switch (order().status) {
          @case ('WAITING') {
            <button
              pButton
              type="button"
              data-testid="start"
              [disabled]="!enabled() || pending().has(order().id)"
              pTooltip="Empezar a preparar este pedido"
              i18n-pTooltip="@@board.card.start.tooltip"
              tooltipPosition="top"
              (click)="start.emit(order().id)"
            >
              <tabler-icon [icon]="icons.start" aria-hidden="true" />
              <span i18n="@@board.card.start">Iniciar</span>
            </button>
          }
          @case ('PREPARING') {
            @if (queued().has(order().id)) {
              <button
                pButton
                type="button"
                severity="secondary"
                data-testid="undo-order"
                pTooltip="Cancelar el aviso de pedido listo antes de que se envíe"
                i18n-pTooltip="@@board.card.undo.tooltip"
                tooltipPosition="top"
                (click)="undo.emit(order().id)"
              >
                <tabler-icon [icon]="icons.undo" aria-hidden="true" />
                <span i18n="@@board.card.undo">Deshacer</span>
              </button>
            } @else {
              <button
                pButton
                type="button"
                data-testid="ready-order"
                [disabled]="!enabled() || pending().has(order().id)"
                pTooltip="Marcar todo el pedido como listo"
                i18n-pTooltip="@@board.card.ready.tooltip"
                tooltipPosition="top"
                (click)="readyOrder.emit(order().id)"
              >
                <tabler-icon [icon]="icons.readyAll" aria-hidden="true" />
                <span i18n="@@board.card.ready">Todo listo</span>
              </button>
            }
          }
          @case ('READY') {
            <button
              pButton
              type="button"
              data-testid="handover"
              [disabled]="!enabled() || pending().has(order().id)"
              pTooltip="Entregar el pedido al cliente"
              i18n-pTooltip="@@board.card.handover.tooltip"
              tooltipPosition="top"
              (click)="handover.emit(order().id)"
            >
              <tabler-icon [icon]="icons.handover" aria-hidden="true" />
              <span i18n="@@board.card.handover">Entregar</span>
            </button>
            <button
              pButton
              type="button"
              severity="secondary"
              data-testid="recall"
              [disabled]="!enabled() || pending().has(order().id)"
              pTooltip="Volver a preparación un pedido terminado hace poco"
              i18n-pTooltip="@@board.card.recall.tooltip"
              tooltipPosition="top"
              (click)="recall.emit(order().id)"
            >
              <tabler-icon [icon]="icons.recall" aria-hidden="true" />
              <span i18n="@@board.card.recall">Reabrir</span>
            </button>
          }
        }
      </footer>
    </article>

    <ng-template #ack let-marker="marker">
      <button
        pButton
        type="button"
        severity="secondary"
        [attr.data-testid]="'ack-' + marker.id"
        [disabled]="!enabled() || pending().has(marker.id)"
        pTooltip="Confirmar que viste este aviso"
        i18n-pTooltip="@@board.card.marker.ack.tooltip"
        tooltipPosition="top"
        (click)="acknowledge.emit(marker.id)"
      >
        <tabler-icon [icon]="icons.ack" aria-hidden="true" />
        <span i18n="@@board.card.marker.ack">Confirmar</span>
      </button>
    </ng-template>
  `,
})
export class BoardCard {
  readonly order = input.required<BoardOrder>();
  readonly minutes = input.required<number>();
  readonly level = input.required<BoardCardLevel>();
  readonly enabled = input.required<boolean>();
  // Ids with a request in flight, and ids whose Ready is still inside the undo window.
  readonly pending = input.required<ReadonlySet<string>>();
  readonly queued = input.required<ReadonlySet<string>>();

  readonly start = output<string>();
  readonly readyOrder = output<string>();
  readonly readyLine = output<string>();
  readonly undo = output<string>();
  readonly handover = output<string>();
  readonly recall = output<string>();
  readonly flag = output<string>();
  readonly acknowledge = output<string>();

  protected readonly icons = {
    allergy: IconAlertTriangle,
    ack: IconChecks,
    flag: IconBan,
    handover: IconPackageExport,
    ready: IconCheck,
    readyAll: IconChecks,
    recall: IconRotate2,
    start: IconPlayerPlay,
    undo: IconArrowBackUp,
  };

  protected readonly channel = computed(() => CHANNELS[this.order().channel]);
  protected readonly scheduledTime = computed(() => {
    const fireAt = this.order().fireAt;
    // No staff-readable endpoint exposes the store zone, and the board device sits in the store.
    return fireAt
      ? formatInStoreZone(fireAt, Intl.DateTimeFormat().resolvedOptions().timeZone, { timeStyle: 'short' })
      : null;
  });
  private readonly unacknowledged = computed(() =>
    this.order().markers.filter((marker) => marker.acknowledgedAt === null),
  );
  protected readonly headerMarkers = computed(() => {
    const lineIds = new Set(this.order().lines.map((line) => line.id));
    return this.unacknowledged().filter((marker) => marker.lineId === null || !lineIds.has(marker.lineId));
  });

  protected markersOf(line: BoardLine): BoardMarker[] {
    return this.unacknowledged().filter((marker) => marker.lineId === line.id);
  }

  protected markerLabel(marker: BoardMarker): string {
    return MARKER_LABELS[marker.kind];
  }

  protected stateOf(line: BoardLine): 'ready' | 'flagged' | 'voided' | 'comped' | null {
    switch (line.state) {
      case 'VOIDED':
        return 'voided';
      case 'COMPED':
        return 'comped';
      case 'READY':
        return 'ready';
      default:
        if (line.flaggedUnavailable) {
          return 'flagged';
        }
        // A line already counts as ready while its Ready, or the whole order's, waits out the undo window.
        return this.queued().has(line.id) || this.queued().has(this.order().id) ? 'ready' : null;
    }
  }

  protected mayReadyLine(line: BoardLine): boolean {
    return (
      this.order().status === 'PREPARING' &&
      line.state === 'PENDING' &&
      !line.flaggedUnavailable &&
      !this.queued().has(this.order().id)
    );
  }

  protected mayFlag(line: BoardLine): boolean {
    const open = this.order().status === 'WAITING' || this.order().status === 'PREPARING';
    const queued = this.queued().has(line.id) || this.queued().has(this.order().id);
    return open && line.state === 'PENDING' && !line.flaggedUnavailable && !queued;
  }
}
