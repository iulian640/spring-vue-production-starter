import type { Router } from 'vue-router'
import type { Pinia } from 'pinia'
import { useAuthStore } from '../stores/auth'

/**
 * Manejador del aviso de 401 del cliente API: la sesión ya no vale, así que
 * se limpia y se va a login con la vuelta preparada. Sin bucles ni empujones
 * duplicados:
 * - el login hace sus peticiones sin token, así que nunca dispara esto;
 * - si ya estamos en login, no se re-navega;
 * - varios 401 simultáneos (p. ej. un Promise.all de tres peticiones con el
 *   token caducado) solo empujan UNA navegación gracias al flag en vuelo.
 */
export function crearManejador401(router: Router, pinia?: Pinia): () => void {
  let redirigiendo = false
  return () => {
    const auth = useAuthStore(pinia)
    auth.sesionCaducada()
    if (redirigiendo) {
      return
    }
    const actual = router.currentRoute.value
    if (actual.name === 'login') {
      return
    }
    redirigiendo = true
    router.push({ name: 'login', query: { redirect: actual.fullPath } }).finally(() => {
      redirigiendo = false
    })
  }
}
