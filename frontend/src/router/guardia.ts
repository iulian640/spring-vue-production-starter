import type { RouteLocationNormalized, RouteLocationRaw } from 'vue-router'
import { useAuthStore } from '../stores/auth'

/**
 * Guardia de navegación:
 * - Las rutas con `meta.requiereSesion` piden sesión; sin ella, a login con
 *   `?redirect=` para volver después (validado en destinoTrasLogin).
 * - Login y registro con sesión ya iniciada no tienen sentido: a la cuenta.
 * - Todo lo demás sigue público (el flujo anónimo funciona sin cuenta).
 */
export function guardiaSesion(to: RouteLocationNormalized): boolean | RouteLocationRaw {
  const auth = useAuthStore()
  if (to.meta.requiereSesion && !auth.autenticado) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if ((to.name === 'login' || to.name === 'registro') && auth.autenticado) {
    return { name: 'cuenta' }
  }
  return true
}
