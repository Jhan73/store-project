import { DOCUMENT } from '@angular/common';
import { inject, InjectionToken } from '@angular/core';

// The API lives on the `api.` host of the frontend's own host in every deployed environment, so the same image
// serves test and prod without configuration; local development talks to the backend on its default port.
export function deriveApiOrigin(location: Pick<Location, 'protocol' | 'hostname'>): string {
  if (location.hostname === 'localhost' || location.hostname === '127.0.0.1') {
    return 'http://localhost:8080';
  }
  return `${location.protocol}//api.${location.hostname}`;
}

export const API_ORIGIN = new InjectionToken<string>('API_ORIGIN', {
  providedIn: 'root',
  factory: () => deriveApiOrigin(inject(DOCUMENT).location),
});

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => `${inject(API_ORIGIN)}/api/v1`,
});
