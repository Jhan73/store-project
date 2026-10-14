// The reason "Other" is the one that needs a note; the backend compares it without regard to letter case.
export function requiresNote(code: string | null): boolean {
  return code !== null && code.trim().toLowerCase() === 'other';
}
