import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { ApiError } from './api-error';
import { ERROR_MESSAGES, errorMessage } from './error-messages';

function apiError(status: number, code: string | null): ApiError {
  return new ApiError({ status, code: code as ApiError['code'], correlationId: null, fieldErrors: [], properties: {} });
}

describe('errorMessage', () => {
  it('has a message for every error code in the OpenAPI spec and no stale ones', () => {
    const spec = JSON.parse(
      readFileSync(join(process.cwd(), '../backend/api/openapi.json'), 'utf8'),
    ) as { components: { schemas: { ErrorCode: { enum: string[] } } } };

    expect(Object.keys(ERROR_MESSAGES).sort()).toEqual([...spec.components.schemas.ErrorCode.enum].sort());
  });

  it('writes Spanish for users, never the backend text', () => {
    const error = apiError(409, 'identity.last-active-admin-required');

    expect(errorMessage(error)).toBe('Debe quedar al menos un administrador activo.');
  });

  it.each(Object.entries(ERROR_MESSAGES))('%s has a non-empty message', (_code, message) => {
    expect(message.trim().length).toBeGreaterThan(0);
  });

  it.each([
    [0, 'No pudimos conectar con el servidor. Revisa tu conexión a internet.'],
    [401, 'Tu sesión expiró. Inicia sesión de nuevo.'],
    [403, 'No tienes permiso para realizar esta acción.'],
    [404, 'No encontramos lo que buscas.'],
    [429, 'Hay demasiadas solicitudes. Espera un momento e inténtalo de nuevo.'],
    [500, 'Ocurrió un error en el servidor. Inténtalo de nuevo en unos minutos.'],
    [504, 'Ocurrió un error en el servidor. Inténtalo de nuevo en unos minutos.'],
    [418, 'No pudimos completar la operación. Inténtalo de nuevo.'],
  ])('falls back to a generic message for an unknown code on status %i', (status, expected) => {
    expect(errorMessage(apiError(status, 'someday.new-code'))).toBe(expected);
    expect(errorMessage(apiError(status, null))).toBe(expected);
  });

  it('uses the generic message for anything that is not an ApiError', () => {
    expect(errorMessage(new TypeError('boom'))).toBe('No pudimos completar la operación. Inténtalo de nuevo.');
    expect(errorMessage(undefined)).toBe('No pudimos completar la operación. Inténtalo de nuevo.');
  });
});
