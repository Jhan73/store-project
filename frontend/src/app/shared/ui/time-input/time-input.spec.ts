import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { fromTimeText, toTimeText } from './time-input';
import { TimeInput } from './time-input';

@Component({
  imports: [ReactiveFormsModule, TimeInput],
  template: `<app-time-input inputId="opens" [formControl]="opens" />`,
})
class Host {
  readonly opens = new FormControl('09:30', { nonNullable: true });
}

describe('time text conversion', () => {
  it('turns HH:mm into a Date on any day and back without touching the zone', () => {
    const date = fromTimeText('07:05')!;

    expect(date.getHours()).toBe(7);
    expect(date.getMinutes()).toBe(5);
    expect(toTimeText(date)).toBe('07:05');
  });

  it.each(['', 'x', '24:00', '9:5', '12:60'])('treats "%s" as no time', (text) => {
    expect(fromTimeText(text)).toBeNull();
  });

  it('writes no text for no time', () => {
    expect(toTimeText(null)).toBe('');
  });
});

describe('TimeInput', () => {
  it('shows the control value as the time of day and exposes the focusable input by id', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('#opens')!;

    expect(input.value).toContain('09:30');
  });
});
