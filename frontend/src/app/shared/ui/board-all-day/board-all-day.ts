import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { IconAlertTriangle, TablerIconComponent } from '@tabler/icons-angular';
import type { BoardAllDay } from '../../../core/api/api-types';

@Component({
  selector: 'app-board-all-day',
  imports: [TablerIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './board-all-day.scss',
  template: `
    @if (allDay().groups.length === 0 && allDay().individual.length === 0) {
      <p class="empty" i18n="@@board.allDay.empty">No hay nada pendiente.</p>
    }

    @if (allDay().groups.length > 0) {
      <section [attr.aria-labelledby]="'all-day-groups'">
        <h2 id="all-day-groups" i18n="@@board.allDay.groups">Por preparar</h2>
        <ul class="groups">
          @for (group of allDay().groups; track group.productId + (group.modifiersText ?? '')) {
            <li data-testid="group">
              <span class="quantity">{{ group.quantity }}×</span>{{ ' ' }}<span class="name">{{ group.displayName }}</span>
              @if (group.modifiersText) {
                {{ ' ' }}<span class="modifiers">({{ group.modifiersText }})</span>
              }
            </li>
          }
        </ul>
      </section>
    }

    @if (allDay().individual.length > 0) {
      <section [attr.aria-labelledby]="'all-day-individual'">
        <h2 id="all-day-individual" i18n="@@board.allDay.individual">Con nota o alergia</h2>
        <ul class="individual">
          @for (item of allDay().individual; track item.lineId) {
            <li [class.allergy]="item.allergy" [attr.data-testid]="'individual-' + item.lineId">
              <span class="number">{{ item.shortNumber }}</span>{{ ' ' }}
              <span class="quantity">{{ item.quantity }}×</span>{{ ' ' }}<span class="name">{{ item.displayName }}</span>
              @if (item.modifiersText) {
                {{ ' ' }}<span class="modifiers">({{ item.modifiersText }})</span>
              }
              @if (item.allergy) {
                <span class="badge" data-testid="allergy">
                  <tabler-icon [icon]="icons.allergy" aria-hidden="true" />
                  <span i18n="@@board.allDay.allergy">Alergia</span>
                </span>
              }
              @if (item.note) {
                <span class="note">{{ item.note }}</span>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
})
export class BoardAllDayView {
  readonly allDay = input.required<BoardAllDay>();

  protected readonly icons = { allergy: IconAlertTriangle };
}
