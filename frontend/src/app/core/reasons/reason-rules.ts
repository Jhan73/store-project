// The reason "Other" is the one that needs a note; the backend compares it without regard to letter case.
// The admin writes every code except this one, which the store seeds in English; the screen words it in Spanish.
export function reasonLabel(code: string): string {
  return requiresNote(code) ? $localize`:@@reason.other:Otro` : code;
}

export function requiresNote(code: string | null): boolean {
  return code !== null && code.trim().toLowerCase() === 'other';
}
