/**
 * Recuperación ante deploys: tras publicar una versión nueva, una pestaña
 * abierta sigue ejecutando el JS antiguo y sus import() dinámicos (rutas
 * lazy) apuntan a chunks con hash viejo que ya no existen — la única salida
 * es recargar para traer el index.html nuevo. Lógica pura y testeable; el
 * cableado con el router vive en router/recargaPorChunk.ts.
 */

/** Marca en sessionStorage para recargar UNA sola vez y no entrar en bucle. */
const CLAVE_MARCA_RECARGA = 'app.recarga-chunk'

/** Mensajes de fallo de import() dinámico según navegador (Chrome / Firefox / Safari). */
const PATRONES_FALLO_CHUNK = [
  /failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /importing a module script failed/i,
]

/** ¿Es un fallo de carga de chunk (import dinámico) y no otro error de navegación? */
export function esErrorDeCargaDeChunk(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false
  }
  return PATRONES_FALLO_CHUNK.some((patron) => patron.test(error.message))
}

/**
 * Decide si procede recargar por un fallo de chunk y deja la marca anti-bucle:
 * la primera vez sí; si ya se recargó y el fallo persiste (deploy roto), no.
 */
export function debeRecargarPorChunk(storage: Pick<Storage, 'getItem' | 'setItem'>): boolean {
  try {
    if (storage.getItem(CLAVE_MARCA_RECARGA) !== null) {
      return false
    }
    storage.setItem(CLAVE_MARCA_RECARGA, '1')
    return true
  } catch {
    // Storage inutilizable (modo privado antiguo, cuota...): sin marca no hay
    // forma de garantizar que no ciclamos, así que mejor no recargar.
    return false
  }
}

/** Una navegación completada con éxito retira la marca: un deploy futuro podrá volver a recuperarse. */
export function limpiarMarcaRecarga(storage: Pick<Storage, 'removeItem'>): void {
  try {
    storage.removeItem(CLAVE_MARCA_RECARGA)
  } catch {
    // Mismo caso que arriba: si el storage falla, no hay marca que limpiar.
  }
}
