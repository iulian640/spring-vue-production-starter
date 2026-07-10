import { animate, stagger } from 'animejs'

/**
 * Todo el movimiento de la app pasa por aquí, por una regla de la casa:
 * quien pide menos movimiento (prefers-reduced-motion) recibe el estado
 * final al instante, sin excepciones. La media query de CSS corta las
 * transiciones declarativas; este módulo corta las de anime.js.
 *
 * Un entorno sin matchMedia (jsdom en los tests) también se queda quieto:
 * así los tests ven el estado final de forma síncrona en vez de depender
 * de frames de animación.
 */
export function movimientoReducido(): boolean {
  return (
    typeof window === 'undefined' ||
    typeof window.matchMedia !== 'function' ||
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/** Duraciones compartidas con los tokens de style.css (ms). */
export const DURACION = {
  toque: 120,
  estado: 200,
  panel: 320,
  entrada: 550,
  cuenta: 800,
  trazo: 450,
} as const

/**
 * La cifra protagonista cuenta desde 0 hasta el importe. El elemento debe
 * llevar numerales tabulares (clase .num) para que la anchura no baile.
 * Resuelve cuando el texto ya es el valor final exacto.
 */
export function cuentaImporte(
  el: HTMLElement,
  hasta: number,
  formatear: (valor: number) => string,
  duracion: number = DURACION.cuenta,
): Promise<void> {
  if (movimientoReducido() || hasta <= 0) {
    el.textContent = formatear(hasta)
    return Promise.resolve()
  }
  const contador = { valor: 0 }
  return new Promise((resolve) => {
    animate(contador, {
      valor: hasta,
      duration: duracion,
      ease: 'outExpo',
      onUpdate: () => {
        el.textContent = formatear(contador.valor)
      },
      onComplete: () => {
        // El último frame puede quedarse a un decimal del destino: se fija.
        el.textContent = formatear(hasta)
        resolve()
      },
    })
  })
}

/**
 * El trazo verde que subraya la cifra al terminar la cuenta: se dibuja de
 * izquierda a derecha (scaleX con origen a la izquierda, vía CSS del
 * componente). Con movimiento reducido el trazo simplemente está.
 */
export function dibujaTrazo(el: HTMLElement, duracion: number = DURACION.trazo): Promise<void> {
  if (movimientoReducido()) {
    el.style.transform = ''
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    animate(el, {
      scaleX: [0, 1],
      duration: duracion,
      ease: 'outQuint',
      onComplete: () => resolve(),
    })
  })
}

/**
 * Prepara el trazo antes de la cuenta: oculto solo si va a haber animación.
 * Separado de dibujaTrazo para que el estado "sin dibujar" nunca exista
 * cuando el movimiento está reducido (el contenido visible es el default).
 */
export function preparaTrazo(el: HTMLElement): void {
  if (!movimientoReducido()) {
    el.style.transform = 'scaleX(0)'
  }
}

/**
 * Entrada escalonada de una lista recién cargada (días de la semana,
 * resultados…). El escalón se acota para que una lista larga no convierta
 * la entrada en una espera: nunca más de `topeTotal` ms de principio a fin.
 */
export function revelaEscalonado(
  elementos: Element[] | NodeListOf<Element>,
  paso = 40,
  topeTotal = 400,
): Promise<void> {
  const lista = Array.from(elementos)
  if (movimientoReducido() || lista.length === 0) {
    return Promise.resolve()
  }
  const pasoAcotado = Math.min(paso, topeTotal / lista.length)
  return new Promise((resolve) => {
    animate(lista, {
      opacity: [0, 1],
      y: [8, 0],
      duration: 350,
      delay: stagger(pasoAcotado),
      ease: 'outQuint',
      onComplete: () => resolve(),
    })
  })
}

/**
 * Pulso de confirmación sobre un elemento (fichaje apuntado, cambio
 * guardado): un latido sutil, nunca un rebote.
 */
export function pulsoExito(el: HTMLElement): Promise<void> {
  if (movimientoReducido()) {
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    animate(el, {
      scale: [1, 1.02, 1],
      duration: 280,
      ease: 'outQuad',
      onComplete: () => resolve(),
    })
  })
}
