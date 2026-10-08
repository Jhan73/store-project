import type { Allergen } from '../../../core/api/api-types';

// Typed as a Record so the compiler fails when the API gains an allergen that has no label.
export const ALLERGEN_LABELS: Record<Allergen, string> = {
  CELERY: $localize`:@@allergen.CELERY:Apio`,
  CRUSTACEANS: $localize`:@@allergen.CRUSTACEANS:Crustáceos`,
  EGGS: $localize`:@@allergen.EGGS:Huevos`,
  FISH: $localize`:@@allergen.FISH:Pescado`,
  GLUTEN: $localize`:@@allergen.GLUTEN:Gluten`,
  LUPIN: $localize`:@@allergen.LUPIN:Altramuces`,
  MILK: $localize`:@@allergen.MILK:Leche`,
  MOLLUSCS: $localize`:@@allergen.MOLLUSCS:Moluscos`,
  MUSTARD: $localize`:@@allergen.MUSTARD:Mostaza`,
  PEANUTS: $localize`:@@allergen.PEANUTS:Maní`,
  SESAME: $localize`:@@allergen.SESAME:Sésamo`,
  SOY: $localize`:@@allergen.SOY:Soya`,
  SULPHITES: $localize`:@@allergen.SULPHITES:Sulfitos`,
  TREE_NUTS: $localize`:@@allergen.TREE_NUTS:Frutos secos`,
};
