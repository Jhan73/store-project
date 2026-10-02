import { deriveApiOrigin } from './api-config';

describe('deriveApiOrigin', () => {
  it.each([
    ['https:', 'jugueria.jhanantezana.com', 'https://api.jugueria.jhanantezana.com'],
    ['https:', 'test.jugueria.jhanantezana.com', 'https://api.test.jugueria.jhanantezana.com'],
    ['http:', 'localhost', 'http://localhost:8080'],
  ])('maps %s//%s to %s', (protocol, hostname, expected) => {
    expect(deriveApiOrigin({ protocol, hostname })).toBe(expected);
  });
});
