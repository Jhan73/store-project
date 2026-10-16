import type { MenuModifierGroup, MenuProduct, Money } from '../../../core/api/api-types';
import { fromMinorUnits, toMinorUnits } from '../../../core/money/money';

export type GroupIssue = 'MIN' | 'MAX';

export function minimumOf(group: MenuModifierGroup): number {
  return group.required ? Math.max(1, group.minChoices) : group.minChoices;
}

function chosenIn(group: MenuModifierGroup, selected: readonly string[]): string[] {
  return selected.filter((id) => group.options.some((option) => option.id === id));
}

export function groupIssue(group: MenuModifierGroup, selected: readonly string[]): GroupIssue | null {
  const count = chosenIn(group, selected).length;
  if (count < minimumOf(group)) {
    return 'MIN';
  }
  return count > group.maxChoices ? 'MAX' : null;
}

export function isValidSelection(product: MenuProduct, selected: readonly string[]): boolean {
  return product.modifierGroups.every((group) => groupIssue(group, selected) === null);
}

// A single-choice group swaps its choice; a multiple-choice group refuses a choice beyond its maximum.
export function toggleOption(
  group: MenuModifierGroup,
  selected: readonly string[],
  optionId: string,
): string[] {
  if (selected.includes(optionId)) {
    return selected.filter((id) => id !== optionId);
  }
  const option = group.options.find((candidate) => candidate.id === optionId);
  if (!option?.available) {
    return [...selected];
  }
  if (group.maxChoices === 1) {
    return [...selected.filter((id) => !chosenIn(group, [id]).length), optionId];
  }
  return chosenIn(group, selected).length >= group.maxChoices ? [...selected] : [...selected, optionId];
}

// A preview only: the ticket's own price comes back from the API.
export function unitPriceOf(product: MenuProduct, selected: readonly string[]): Money {
  const deltas = product.modifierGroups
    .flatMap((group) => group.options)
    .filter((option) => selected.includes(option.id))
    .reduce((sum, option) => sum + toMinorUnits(option.priceDelta), 0);
  return fromMinorUnits(toMinorUnits(product.price) + deltas, product.price.currency);
}
