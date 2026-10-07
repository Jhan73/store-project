import type { Translation } from 'primeng/api';

const LOCALE = 'es-PE';

function names(options: Intl.DateTimeFormatOptions, count: number, firstOf: (index: number) => Date): string[] {
  const format = new Intl.DateTimeFormat(LOCALE, { ...options, timeZone: 'UTC' });
  return Array.from({ length: count }, (_, index) => format.format(firstOf(index)));
}

// 2024-01-07 is a Sunday and PrimeNG lists the week from Sunday.
const dayNames = names({ weekday: 'long' }, 7, (index) => new Date(Date.UTC(2024, 0, 7 + index)));
const dayNamesShort = names({ weekday: 'short' }, 7, (index) => new Date(Date.UTC(2024, 0, 7 + index)));
const monthNames = names({ month: 'long' }, 12, (index) => new Date(Date.UTC(2024, index, 1)));
const monthNamesShort = names({ month: 'short' }, 12, (index) => new Date(Date.UTC(2024, index, 1)));

// Only the strings the app's PrimeNG components can show; each keeps a stable i18n ID.
export const primeTranslation: Translation = {
  accept: $localize`:@@primeng.accept:Aceptar`,
  reject: $localize`:@@primeng.reject:Cancelar`,
  choose: $localize`:@@primeng.choose:Elegir`,
  clear: $localize`:@@primeng.clear:Limpiar`,
  apply: $localize`:@@primeng.apply:Aplicar`,
  cancel: $localize`:@@primeng.cancel:Cancelar`,
  emptyMessage: $localize`:@@primeng.emptyMessage:No hay resultados`,
  emptyFilterMessage: $localize`:@@primeng.emptyFilterMessage:No hay resultados`,
  searchMessage: $localize`:@@primeng.searchMessage:Hay resultados disponibles`,
  selectionMessage: $localize`:@@primeng.selectionMessage:Elementos seleccionados`,
  emptySearchMessage: $localize`:@@primeng.emptySearchMessage:No hay resultados`,
  dayNames,
  dayNamesShort,
  dayNamesMin: dayNamesShort.map((day) => day.replace('.', '').slice(0, 2)),
  monthNames,
  monthNamesShort,
  firstDayOfWeek: 1,
  today: $localize`:@@primeng.today:Hoy`,
  weekHeader: $localize`:@@primeng.weekHeader:Sem`,
  emptySelectionMessage: $localize`:@@primeng.emptySelectionMessage:No hay elementos seleccionados`,
};
