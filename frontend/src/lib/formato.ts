import { ApiError } from '../services/api'

/**
 * Mensaje legible de un error: el backend habla RFC 7807 ({status, detail}),
 * así que el detail va primero. Siempre texto plano, nunca HTML.
 */
export function mensajeDeError(error: unknown): string {
  if (error instanceof ApiError && error.body && typeof error.body === 'object') {
    const detail = (error.body as Record<string, unknown>).detail
    if (typeof detail === 'string' && detail.length > 0) {
      return detail
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Algo ha fallado. Inténtalo de nuevo.'
}
