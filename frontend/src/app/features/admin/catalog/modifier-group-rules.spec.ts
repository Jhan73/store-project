import { modifierGroupViolations } from './modifier-group-rules';

const valid = { required: true, minChoices: 1, maxChoices: 2, optionNames: ['Small', 'Large'] };

describe('modifierGroupViolations', () => {
  it('accepts a coherent group', () => {
    expect(modifierGroupViolations(valid)).toEqual([]);
  });

  it('asks for at least one option', () => {
    expect(modifierGroupViolations({ ...valid, optionNames: [] })).toContain('no-options');
  });

  it('asks for a maximum of at least one and not below the minimum', () => {
    expect(modifierGroupViolations({ ...valid, maxChoices: 0 })).toContain('max-too-low');
    expect(modifierGroupViolations({ ...valid, minChoices: 2, maxChoices: 1 })).toContain(
      'max-too-low',
    );
  });

  it('asks a required group for a minimum of at least one', () => {
    expect(modifierGroupViolations({ ...valid, minChoices: 0 })).toContain('min-required');
  });

  it('asks an optional group for a minimum of exactly zero', () => {
    expect(modifierGroupViolations({ ...valid, required: false, minChoices: 1 })).toContain(
      'min-optional',
    );
    expect(modifierGroupViolations({ ...valid, required: false, minChoices: 0 })).toEqual([]);
  });

  it('does not allow a minimum above the number of options', () => {
    expect(
      modifierGroupViolations({ ...valid, minChoices: 3, maxChoices: 3, optionNames: ['A', 'B'] }),
    ).toContain('min-exceeds-options');
  });

  it('does not allow repeated option names, ignoring case and spaces around', () => {
    expect(modifierGroupViolations({ ...valid, optionNames: ['Large', ' large '] })).toContain(
      'duplicate-option-names',
    );
  });

  it('ignores blank option names, which the form reports on their own field', () => {
    expect(modifierGroupViolations({ ...valid, optionNames: ['', ' '] })).not.toContain(
      'duplicate-option-names',
    );
  });
});
