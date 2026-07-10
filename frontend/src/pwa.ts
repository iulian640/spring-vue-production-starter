import { registerSW } from 'virtual:pwa-register'

/** Cada cuánto se comprueba si hay versión nueva del SW en sesiones largas. */
export const INTERVALO_COMPROBACION_SW_MS = 60 * 60 * 1000

/**
 * Registro del service worker (registerType 'autoUpdate'). Con el cliente de
 * virtual:pwa-register, cuando aparece una versión nueva el SW se activa solo
 * (skipWaiting + clientsClaim) y la página se recarga en cuanto toma el
 * control — patrón recomendado por la doc de vite-plugin-pwa. Sin esta
 * recarga, la pestaña abierta se quedaría con el JS viejo pidiendo chunks que
 * ya no existen tras el deploy. No hay bucle posible: solo se recarga cuando
 * se ACTIVA una actualización real, y tras recargar ya no hay actualización.
 */
export function registrarPWA(): void {
  registerSW({
    immediate: true,
    onRegisteredSW(_swUrl, registro) {
      if (!registro) {
        return
      }
      // Sesiones largas (la app abierta una jornada entera): sin comprobación
      // periódica, el navegador puede tardar horas en ver la versión nueva.
      setInterval(() => {
        if (registro.installing || !navigator.onLine) {
          return
        }
        registro.update().catch(() => {
          // Sin red u otro fallo puntual: se reintenta en el siguiente tick.
        })
      }, INTERVALO_COMPROBACION_SW_MS)
    },
  })
}
