import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-table-scroll',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './table-scroll.scss',
  host: {
    role: 'region',
    tabindex: '0',
    '[attr.aria-label]': 'label()',
  },
  template: `<ng-content />`,
})
export class TableScroll {
  readonly label = input.required<string>();
}
