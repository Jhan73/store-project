import type { Translation } from 'primeng/api';

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
  emptySelectionMessage: $localize`:@@primeng.emptySelectionMessage:No hay elementos seleccionados`,
};
