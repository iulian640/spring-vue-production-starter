import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { crearManejador401 } from './sesion401'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { postLogin } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouterPrueba() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: Stub },
    ],
  })
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('crearManejador401', () => {
  it('limpia la sesión y navega a login con la vuelta preparada', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const router = crearRouterPrueba()
    await router.push('/cuenta')
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const manejar = crearManejador401(router)

    manejar()
    await router.isReady()
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))

    expect(auth.autenticado).toBe(false)
    expect(router.currentRoute.value.query.redirect).toBe('/cuenta')
  })

  it('varios 401 simultáneos (Promise.all) solo empujan UNA navegación', async () => {
    const router = crearRouterPrueba()
    await router.push('/cuenta')
    const push = vi.spyOn(router, 'push')
    const manejar = crearManejador401(router)

    manejar()
    manejar()
    manejar()

    expect(push).toHaveBeenCalledTimes(1)
  })

  it('si ya estamos en login no re-navega (sin bucles)', async () => {
    const router = crearRouterPrueba()
    await router.push('/login')
    const push = vi.spyOn(router, 'push')
    const manejar = crearManejador401(router)

    manejar()

    expect(push).not.toHaveBeenCalled()
  })
})
