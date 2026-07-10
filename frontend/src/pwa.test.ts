import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('virtual:pwa-register', () => ({
  registerSW: vi.fn(),
}))

import { registerSW } from 'virtual:pwa-register'
import { INTERVALO_COMPROBACION_SW_MS, registrarPWA } from './pwa'

type OpcionesRegistro = {
  immediate: boolean
  onRegisteredSW: (swUrl: string, registro: ServiceWorkerRegistration | undefined) => void
}

/** Registro de SW mínimo para los tests: solo lo que usa registrarPWA. */
function crearRegistro(overrides: Partial<{ installing: object | null }> = {}) {
  return {
    installing: null,
    update: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  } as unknown as ServiceWorkerRegistration & { update: ReturnType<typeof vi.fn> }
}

function opcionesPasadas(): OpcionesRegistro {
  return vi.mocked(registerSW).mock.calls[0][0] as OpcionesRegistro
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
})

afterEach(() => {
  vi.useRealTimers()
  // Restaura el spy del getter navigator.onLine para no contaminar otros tests.
  vi.restoreAllMocks()
})

describe('registrarPWA', () => {
  it('registra el service worker inmediatamente', () => {
    // Act
    registrarPWA()

    // Assert
    expect(registerSW).toHaveBeenCalledTimes(1)
    expect(opcionesPasadas().immediate).toBe(true)
  })

  it('no programa comprobaciones si el navegador no devuelve registro', () => {
    registrarPWA()

    opcionesPasadas().onRegisteredSW('/sw.js', undefined)
    vi.advanceTimersByTime(INTERVALO_COMPROBACION_SW_MS * 2)

    // Sin registro no hay nada que actualizar y no debe explotar.
    expect(vi.getTimerCount()).toBe(0)
  })

  it('comprueba actualizaciones periódicamente en sesiones largas', () => {
    const registro = crearRegistro()
    registrarPWA()

    opcionesPasadas().onRegisteredSW('/sw.js', registro)
    vi.advanceTimersByTime(INTERVALO_COMPROBACION_SW_MS * 3)

    expect(registro.update).toHaveBeenCalledTimes(3)
  })

  it('no comprueba mientras ya hay una instalación en curso', () => {
    const registro = crearRegistro({ installing: {} })
    registrarPWA()

    opcionesPasadas().onRegisteredSW('/sw.js', registro)
    vi.advanceTimersByTime(INTERVALO_COMPROBACION_SW_MS)

    expect(registro.update).not.toHaveBeenCalled()
  })

  it('no comprueba sin conexión', () => {
    const registro = crearRegistro()
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
    registrarPWA()

    opcionesPasadas().onRegisteredSW('/sw.js', registro)
    vi.advanceTimersByTime(INTERVALO_COMPROBACION_SW_MS)

    expect(registro.update).not.toHaveBeenCalled()
  })

  it('un fallo puntual de update no rompe los siguientes ticks', async () => {
    const registro = crearRegistro()
    registro.update.mockRejectedValueOnce(new Error('sin red')).mockResolvedValue(undefined)
    registrarPWA()

    opcionesPasadas().onRegisteredSW('/sw.js', registro)
    await vi.advanceTimersByTimeAsync(INTERVALO_COMPROBACION_SW_MS * 2)

    expect(registro.update).toHaveBeenCalledTimes(2)
  })
})
