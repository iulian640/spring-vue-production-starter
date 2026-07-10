import type { Router } from 'vue-router'
import {
  debeRecargarPorChunk,
  esErrorDeCargaDeChunk,
  limpiarMarcaRecarga,
} from '../lib/recargaChunks'

/**
 * Cableado de la recuperación ante deploys sobre el router. Tras publicar una
 * versión nueva, esta pestaña puede seguir con el JS viejo pidiendo chunks
 * (rutas lazy) que ya no existen: se recarga UNA vez hacia la ruta destino
 * para traer el index.html nuevo; la marca anti-bucle evita ciclar si el
 * fallo persiste tras recargar.
 *
 * `storage` y `recargar` se inyectan (sessionStorage y location.assign en
 * producción) para poder probar el cableado real con un router de memoria.
 */
export function instalarRecargaPorChunk(
  router: Router,
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>,
  recargar: (destino: string) => void,
): void {
  router.onError((error, to) => {
    if (esErrorDeCargaDeChunk(error) && debeRecargarPorChunk(storage)) {
      recargar(to.fullPath)
    }
  })

  // Navegación completada: si hubo recarga por chunk, ya funcionó. Se retira
  // la marca para que un deploy futuro pueda volver a recuperarse igual.
  router.afterEach(() => {
    limpiarMarcaRecarga(storage)
  })
}
