import type { ErrorCode } from '../api/api-types';
import { ApiError } from './api-error';

// One message per backend code; the compiler fails when the OpenAPI spec gains a code that has none.
export const ERROR_MESSAGES = {
  'auth.account-locked': $localize`:@@error.auth.account-locked:Tu cuenta está bloqueada temporalmente por demasiados intentos fallidos. Inténtalo más tarde.`,
  'auth.forbidden': $localize`:@@error.auth.forbidden:No tienes permiso para realizar esta acción.`,
  'auth.invalid-credentials': $localize`:@@error.auth.invalid-credentials:El correo o la contraseña no son correctos.`,
  'auth.invalid-refresh-token': $localize`:@@error.auth.invalid-refresh-token:Tu sesión expiró. Inicia sesión de nuevo.`,
  'auth.invalid-set-password-token': $localize`:@@error.auth.invalid-set-password-token:El enlace para crear tu contraseña no es válido o ya venció.`,
  'auth.unauthenticated': $localize`:@@error.auth.unauthenticated:Tu sesión expiró. Inicia sesión de nuevo.`,
  'catalog.category-name-already-used': $localize`:@@error.catalog.category-name-already-used:Ya existe una categoría con ese nombre.`,
  'catalog.category-not-found': $localize`:@@error.catalog.category-not-found:La categoría no existe.`,
  'catalog.currency-mismatch': $localize`:@@error.catalog.currency-mismatch:La moneda no coincide con la del catálogo.`,
  'catalog.invalid-image': $localize`:@@error.catalog.invalid-image:La imagen no es válida.`,
  'catalog.invalid-modifier-group': $localize`:@@error.catalog.invalid-modifier-group:El grupo de modificadores no es válido.`,
  'catalog.invalid-modifier-selection': $localize`:@@error.catalog.invalid-modifier-selection:La selección de modificadores no cumple las reglas del producto.`,
  'catalog.invalid-price': $localize`:@@error.catalog.invalid-price:El precio no es válido.`,
  'catalog.invalid-product': $localize`:@@error.catalog.invalid-product:Los datos del producto no son válidos.`,
  'catalog.modifier-group-in-use': $localize`:@@error.catalog.modifier-group-in-use:El grupo de modificadores está en uso y no se puede eliminar.`,
  'catalog.modifier-group-name-already-used': $localize`:@@error.catalog.modifier-group-name-already-used:Ya existe un grupo de modificadores con ese nombre.`,
  'catalog.modifier-group-not-found': $localize`:@@error.catalog.modifier-group-not-found:El grupo de modificadores no existe.`,
  'catalog.modifier-option-not-found': $localize`:@@error.catalog.modifier-option-not-found:La opción de modificador no existe.`,
  'catalog.modifier-option-unavailable': $localize`:@@error.catalog.modifier-option-unavailable:Una de las opciones elegidas no está disponible.`,
  'catalog.price-currency-mismatch': $localize`:@@error.catalog.price-currency-mismatch:La moneda del precio no coincide con la de la tienda.`,
  'catalog.product-name-already-used': $localize`:@@error.catalog.product-name-already-used:Ya existe un producto con ese nombre.`,
  'catalog.product-not-found': $localize`:@@error.catalog.product-not-found:El producto no existe.`,
  'catalog.product-unavailable': $localize`:@@error.catalog.product-unavailable:El producto no está disponible por ahora.`,
  'catalog.provider-unavailable': $localize`:@@error.catalog.provider-unavailable:No pudimos procesar la imagen en este momento. Inténtalo de nuevo en unos minutos.`,
  'catalog.station-name-already-used': $localize`:@@error.catalog.station-name-already-used:Ya existe una estación con ese nombre.`,
  'catalog.station-not-found': $localize`:@@error.catalog.station-not-found:La estación no existe.`,
  'catalog.unknown-category': $localize`:@@error.catalog.unknown-category:La categoría elegida no existe.`,
  'catalog.unknown-modifier-group': $localize`:@@error.catalog.unknown-modifier-group:El grupo de modificadores elegido no existe.`,
  'catalog.unknown-station': $localize`:@@error.catalog.unknown-station:La estación elegida no existe.`,
  'common.concurrent-modification': $localize`:@@error.common.concurrent-modification:Otra persona modificó este dato al mismo tiempo. Recarga e inténtalo de nuevo.`,
  'common.content-too-large': $localize`:@@error.common.content-too-large:El contenido enviado es demasiado grande.`,
  'common.idempotency-in-progress': $localize`:@@error.common.idempotency-in-progress:Esta operación ya se está procesando. Espera un momento e inténtalo de nuevo.`,
  'common.idempotency-key-required': $localize`:@@error.common.idempotency-key-required:No pudimos procesar la solicitud. Recarga la página e inténtalo de nuevo.`,
  'common.idempotency-key-reused': $localize`:@@error.common.idempotency-key-reused:Esta solicitud ya se envió con otros datos. Recarga la página e inténtalo de nuevo.`,
  'common.internal-error': $localize`:@@error.common.internal-error:Ocurrió un error inesperado. Inténtalo de nuevo.`,
  'common.malformed-request': $localize`:@@error.common.malformed-request:No pudimos procesar la solicitud. Revisa los datos e inténtalo de nuevo.`,
  'common.method-not-allowed': $localize`:@@error.common.method-not-allowed:Esta operación no está permitida.`,
  'common.not-acceptable': $localize`:@@error.common.not-acceptable:No pudimos entregar la respuesta en el formato pedido.`,
  'common.not-found': $localize`:@@error.common.not-found:No encontramos lo que buscas.`,
  'common.precondition-failed': $localize`:@@error.common.precondition-failed:Los datos cambiaron mientras los editabas. Recarga e inténtalo de nuevo.`,
  'common.precondition-required': $localize`:@@error.common.precondition-required:Recarga la página e inténtalo de nuevo.`,
  'common.service-unavailable': $localize`:@@error.common.service-unavailable:El servicio no está disponible por ahora. Inténtalo en unos minutos.`,
  'common.unsupported-media-type': $localize`:@@error.common.unsupported-media-type:El tipo de archivo no es compatible.`,
  'common.validation-failed': $localize`:@@error.common.validation-failed:Revisa los campos marcados.`,
  'identity.cannot-modify-own-account': $localize`:@@error.identity.cannot-modify-own-account:No puedes modificar tu propia cuenta.`,
  'identity.email-already-registered': $localize`:@@error.identity.email-already-registered:Ya existe una cuenta con ese correo.`,
  'identity.invalid-staff-role': $localize`:@@error.identity.invalid-staff-role:El rol elegido no es válido para el personal.`,
  'identity.last-active-admin-required': $localize`:@@error.identity.last-active-admin-required:Debe quedar al menos un administrador activo.`,
  'instore.table-name-already-used': $localize`:@@error.instore.table-name-already-used:Ya existe una mesa con ese nombre.`,
  'instore.table-not-found': $localize`:@@error.instore.table-not-found:La mesa no existe.`,
  'store.delivery-zone-currency-mismatch': $localize`:@@error.store.delivery-zone-currency-mismatch:La moneda de la zona de reparto no coincide con la de la tienda.`,
  'store.delivery-zone-name-already-used': $localize`:@@error.store.delivery-zone-name-already-used:Ya existe una zona de reparto con ese nombre.`,
  'store.delivery-zone-not-found': $localize`:@@error.store.delivery-zone-not-found:La zona de reparto no existe.`,
  'store.invalid-board-thresholds': $localize`:@@error.store.invalid-board-thresholds:Los tiempos del tablero no son válidos.`,
  'store.invalid-delivery-zone': $localize`:@@error.store.invalid-delivery-zone:Los datos de la zona de reparto no son válidos.`,
  'store.invalid-opening-hours': $localize`:@@error.store.invalid-opening-hours:El horario de atención no es válido.`,
  'store.invalid-settings-value': $localize`:@@error.store.invalid-settings-value:Uno de los valores de la configuración no es válido.`,
  'store.reason-code-already-used': $localize`:@@error.store.reason-code-already-used:Ya existe un motivo con ese código.`,
  'store.reason-not-found': $localize`:@@error.store.reason-not-found:El motivo no existe.`,
} as const satisfies Record<ErrorCode, string>;

const GENERIC = $localize`:@@error.generic:No pudimos completar la operación. Inténtalo de nuevo.`;

const STATUS_MESSAGES: Record<number, string> = {
  0: $localize`:@@error.status.0:No pudimos conectar con el servidor. Revisa tu conexión a internet.`,
  400: $localize`:@@error.status.400:No pudimos procesar la solicitud. Revisa los datos e inténtalo de nuevo.`,
  401: $localize`:@@error.status.401:Tu sesión expiró. Inicia sesión de nuevo.`,
  403: $localize`:@@error.status.403:No tienes permiso para realizar esta acción.`,
  404: $localize`:@@error.status.404:No encontramos lo que buscas.`,
  409: $localize`:@@error.status.409:La operación no se puede completar en el estado actual. Recarga e inténtalo de nuevo.`,
  422: $localize`:@@error.status.422:Los datos no cumplen las reglas de la operación.`,
  429: $localize`:@@error.status.429:Hay demasiadas solicitudes. Espera un momento e inténtalo de nuevo.`,
  500: $localize`:@@error.status.500:Ocurrió un error en el servidor. Inténtalo de nuevo en unos minutos.`,
};

function isKnownCode(code: string): code is ErrorCode {
  return Object.hasOwn(ERROR_MESSAGES, code);
}

// Never the backend's title or detail: those are English text for developers.
export function errorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return GENERIC;
  }
  if (error.code !== null && isKnownCode(error.code)) {
    return ERROR_MESSAGES[error.code];
  }
  const status = error.status >= 500 ? 500 : error.status;
  return STATUS_MESSAGES[status] ?? GENERIC;
}

export function supportCodeMessage(correlationId: string): string {
  return $localize`:@@error.supportCode:Código de soporte: ${correlationId}:correlationId:`;
}
