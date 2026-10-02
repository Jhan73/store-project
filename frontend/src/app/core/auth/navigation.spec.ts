import { homeRouteFor, safeReturnUrl } from './navigation';

describe('homeRouteFor', () => {
  it.each([
    ['ADMIN', '/admin'],
    ['CASHIER', '/staff'],
    ['SERVER', '/staff'],
    ['CUSTOMER', '/'],
  ] as const)('sends %s to %s', (role, route) => {
    expect(homeRouteFor(role)).toBe(route);
  });
});

describe('safeReturnUrl', () => {
  it.each(['/staff', '/admin/tables', '/staff?tab=1'])('keeps the in-app path %s', (url) => {
    expect(safeReturnUrl(url)).toBe(url);
  });

  it.each([
    null,
    undefined,
    '',
    'staff',
    '//evil.example.com',
    '/\\evil.example.com',
    'https://evil.example.com',
    'javascript:alert(1)',
    '/login',
    '/login?returnUrl=%2Fstaff',
  ])('rejects %s', (url) => {
    expect(safeReturnUrl(url)).toBeNull();
  });
});
