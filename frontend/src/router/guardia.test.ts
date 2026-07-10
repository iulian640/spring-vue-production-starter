import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { RouteLocationNormalized } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { guardiaSesion } from './guardia'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { postLogin } from '../services/auth'

function ruta(parcial: Partial<RouteLocationNormalized>): RouteLocationNormalized {
  return { meta: {}, fullPath: '/', name: undefined, ...parcial } as RouteLocationNormalized
}

/** El token es de solo lectura: la sesión de prueba se abre por la puerta de verdad. */
async function conSesion(): Promise<ReturnType<typeof useAuthStore>> {
  vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
  const auth = useAuthStore()
  await auth.iniciarSesion('ana@example.com', 'superclave123')
  return auth
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('guardiaSesion', () => {
  it('deja pasar a rutas públicas sin sesión', () => {
    expect(guardiaSesion(ruta({ fullPath: '/perfil' }))).toBe(true)
  })

  it('manda a login (con redirect de vuelta) si la ruta exige sesión y no la hay', () => {
    const resultado = guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))

    expect(resultado).toEqual({ name: 'login', query: { redirect: '/cuenta' } })
  })

  it('deja pasar a rutas protegidas con sesión iniciada', async () => {
    await conSesion()

    expect(guardiaSesion(ruta({ meta: { requiereSesion: true }, fullPath: '/cuenta' }))).toBe(true)
  })

  it('con sesión iniciada, login y registro redirigen a la cuenta', async () => {
    await conSesion()

    expect(guardiaSesion(ruta({ name: 'login', fullPath: '/login' }))).toEqual({ name: 'cuenta' })
    expect(guardiaSesion(ruta({ name: 'registro', fullPath: '/registro' }))).toEqual({
      name: 'cuenta',
    })
  })
})
