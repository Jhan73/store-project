export type ModifierGroupViolation =
  | 'no-options'
  | 'max-too-low'
  | 'min-required'
  | 'min-optional'
  | 'min-exceeds-options'
  | 'duplicate-option-names';

export interface ModifierGroupDraft {
  readonly required: boolean;
  readonly minChoices: number;
  readonly maxChoices: number;
  readonly optionNames: readonly string[];
}

// Fast feedback only: the backend enforces the same rules and stays the authority.
export function modifierGroupViolations(draft: ModifierGroupDraft): ModifierGroupViolation[] {
  const violations: ModifierGroupViolation[] = [];
  const names = draft.optionNames.map((name) => name.trim().toLowerCase()).filter(Boolean);
  if (draft.optionNames.length === 0) {
    violations.push('no-options');
  }
  if (draft.maxChoices < 1 || draft.maxChoices < draft.minChoices) {
    violations.push('max-too-low');
  }
  if (draft.required && draft.minChoices < 1) {
    violations.push('min-required');
  }
  if (!draft.required && draft.minChoices !== 0) {
    violations.push('min-optional');
  }
  if (draft.minChoices > draft.optionNames.length) {
    violations.push('min-exceeds-options');
  }
  if (new Set(names).size !== names.length) {
    violations.push('duplicate-option-names');
  }
  return violations;
}
