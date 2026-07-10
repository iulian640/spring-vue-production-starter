/** Destino por defecto tras iniciar sesión. */
export const RUTA_TRAS_LOGIN = '/cuenta'

/**
 * Valida el `?redirect=` de la pantalla de login. Solo se aceptan rutas
 * internas de la SPA: un solo `/` inicial y sin `\` (el navegador normaliza
 * `/\evil` a `//evil`). Todo lo demás (URLs absolutas, `//host`, valores
 * raros) cae al destino por defecto — defensa contra open redirect.
 */
export function destinoTrasLogin(redirect: unknown): string {
  if (typeof redirect !== 'string') {
    return RUTA_TRAS_LOGIN
  }
  if (!redirect.startsWith('/') || redirect.startsWith('//') || redirect.includes('\\')) {
    return RUTA_TRAS_LOGIN
  }
  return redirect
}
