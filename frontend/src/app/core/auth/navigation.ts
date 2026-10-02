import type { Role } from '../api/api-types';

export function homeRouteFor(role: Role): string {
  switch (role) {
    case 'ADMIN':
      return '/admin';
    case 'SERVER':
    case 'CASHIER':
      return '/staff';
    case 'CUSTOMER':
      return '/';
  }
}

// Only in-app paths: anything else would let a crafted login link send the user off-site.
export function safeReturnUrl(url: string | null | undefined): string | null {
  if (!url || !/^\/(?![/\\])/.test(url) || /^\/login(?:[/?#]|$)/.test(url)) {
    return null;
  }
  return url;
}
