import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cuentaImporte,
  dibujaTrazo,
  movimientoReducido,
  preparaTrazo,
  pulsoExito,
  revelaEscalonado,
} from './animacion'
import { animate, stagger } from 'animejs'

// anime.js se mockea entero: aquí no se prueban frames, se prueba el contrato
// del wrapper (cuándo anima, cuándo salta al estado final, qué recibe anime).
vi.mock('animejs', () => ({
  animate: vi.fn(
    (
      objetivo: Record<string, unknown> | Element | Element[],
      opciones: Record<string, unknown>,
    ) => {
      // Simula la animación completada: valores finales + callbacks.
      if (
        !Array.isArray(objetivo) &&
        !(objetivo instanceof Element) &&
        typeof opciones.valor === 'number'
      ) {
        ;(objetivo as Record<string, unknown>).valor = opciones.valor
      }
      ;(opciones.onUpdate as (() => void) | undefined)?.()
      ;(opciones.onComplete as (() => void) | undefined)?.()
    },
  ),
  stagger: vi.fn((paso: number) => paso),
}))

/** matchMedia con preferencia de movimiento configurable (jsdom no lo trae). */
function stubMatchMedia(reducido: boolean) {
  vi.stubGlobal('matchMedia', (consulta: string) => ({
    matches: consulta.includes('prefers-reduced-motion') ? reducido : false,
    media: consulta,
  }))
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('movimientoReducido', () => {
  it('sin matchMedia (jsdom), la app se queda quieta', () => {
    expect(movimientoReducido()).toBe(true)
  })

  it('respeta prefers-reduced-motion: reduce', () => {
    stubMatchMedia(true)
    expect(movimientoReducido()).toBe(true)
  })

  it('con matchMedia y sin preferencia, hay movimiento', () => {
    stubMatchMedia(false)
    expect(movimientoReducido()).toBe(false)
  })
})

describe('cuentaImporte', () => {
  it('con movimiento reducido escribe el valor final al instante, sin animar', async () => {
    const el = document.createElement('span')
    await cuentaImporte(el, 127.4, (v) => v.toFixed(2))
    expect(el.textContent).toBe('127.40')
    expect(animate).not.toHaveBeenCalled()
  })

  it('con importe 0 no anima: no hay nada que contar', async () => {
    stubMatchMedia(false)
    const el = document.createElement('span')
    await cuentaImporte(el, 0, (v) => `${v}`)
    expect(el.textContent).toBe('0')
    expect(animate).not.toHaveBeenCalled()
  })

  it('con movimiento anima hasta el importe y fija el texto final exacto', async () => {
    stubMatchMedia(false)
    const el = document.createElement('span')
    await cuentaImporte(el, 42, (v) => v.toFixed(2))
    expect(animate).toHaveBeenCalledOnce()
    expect(el.textContent).toBe('42.00')
  })
})

describe('trazo del subrayado', () => {
  it('preparaTrazo no oculta nada si no va a haber animación', () => {
    const el = document.createElement('span')
    preparaTrazo(el)
    expect(el.style.transform).toBe('')
  })

  it('preparaTrazo lo recoge (scaleX 0) solo cuando hay movimiento', () => {
    stubMatchMedia(false)
    const el = document.createElement('span')
    preparaTrazo(el)
    expect(el.style.transform).toBe('scaleX(0)')
  })

  it('dibujaTrazo con movimiento reducido deja el trazo visible', async () => {
    const el = document.createElement('span')
    el.style.transform = 'scaleX(0)'
    await dibujaTrazo(el)
    expect(el.style.transform).toBe('')
    expect(animate).not.toHaveBeenCalled()
  })

  it('dibujaTrazo anima de 0 a 1 cuando hay movimiento', async () => {
    stubMatchMedia(false)
    const el = document.createElement('span')
    await dibujaTrazo(el)
    expect(animate).toHaveBeenCalledWith(
      el,
      expect.objectContaining({ scaleX: [0, 1] }),
    )
  })
})

describe('revelaEscalonado', () => {
  it('sin movimiento o sin elementos, resuelve sin animar', async () => {
    await revelaEscalonado([document.createElement('li')])
    stubMatchMedia(false)
    await revelaEscalonado([])
    expect(animate).not.toHaveBeenCalled()
  })

  it('acota el escalón para que la lista entera no pase del tope', async () => {
    stubMatchMedia(false)
    const veinte = Array.from({ length: 20 }, () => document.createElement('li'))
    await revelaEscalonado(veinte, 40, 400)
    // 40ms × 20 = 800ms pasaría del tope: se acota a 400/20 = 20ms por elemento.
    expect(stagger).toHaveBeenCalledWith(20)
  })
})

describe('pulsoExito', () => {
  it('con movimiento reducido no hace nada', async () => {
    await pulsoExito(document.createElement('div'))
    expect(animate).not.toHaveBeenCalled()
  })

  it('con movimiento late una vez, sin rebote', async () => {
    stubMatchMedia(false)
    const el = document.createElement('div')
    await pulsoExito(el)
    expect(animate).toHaveBeenCalledWith(
      el,
      expect.objectContaining({ scale: [1, 1.02, 1] }),
    )
  })
})
