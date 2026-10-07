import { primeTranslation } from './prime-translation';

describe('primeTranslation calendar', () => {
  it('names the days starting on Sunday, as the date picker expects', () => {
    expect(primeTranslation.dayNames).toHaveLength(7);
    expect(primeTranslation.dayNames![0].toLowerCase()).toBe('domingo');
    expect(primeTranslation.dayNamesMin).toHaveLength(7);
    expect(primeTranslation.dayNamesShort).toHaveLength(7);
  });

  it('names the twelve months', () => {
    expect(primeTranslation.monthNames).toHaveLength(12);
    expect(primeTranslation.monthNames![0].toLowerCase()).toBe('enero');
    expect(primeTranslation.monthNamesShort).toHaveLength(12);
  });

  it('starts the week on Monday and labels the shortcut buttons', () => {
    expect(primeTranslation.firstDayOfWeek).toBe(1);
    expect(primeTranslation.today).toBe('Hoy');
    expect(primeTranslation.weekHeader).toBeTruthy();
  });
});
