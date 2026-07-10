import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import LoginView from './LoginView.vue'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { postLogin } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: LoginView },
      { path: '/registro', name: 'registro', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: Stub },
    ],
  })
}

async function montar(url = '/login') {
  const router = crearRouter()
  await router.push(url)
  const wrapper = mount(LoginView, { global: { plugins: [createPinia(), router] } })
  return { wrapper, router }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('LoginView', () => {
  it('no llama a la API si faltan email o contraseña', async () => {
    const { wrapper } = await montar()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postLogin).not.toHaveBeenCalled()
    expect(wrapper.text()).toMatch(/email|contraseña/i)
  })

  it('con login correcto navega al destino por defecto (/cuenta)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const { wrapper, router } = await montar()

    await wrapper.find('input[type="email"]').setValue('ana@example.com')
    await wrapper.find('input[type="password"]').setValue('superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postLogin).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(router.currentRoute.value.path).toBe('/cuenta')
  })

  it('ignora un redirect externo (open redirect) y va a /cuenta', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const { wrapper, router } = await montar('/login?redirect=https://evil.example')

    await wrapper.find('input[type="email"]').setValue('ana@example.com')
    await wrapper.find('input[type="password"]').setValue('superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/cuenta')
  })

  it('vuelve a la ruta interna del redirect tras entrar', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const { wrapper, router } = await montar('/login?redirect=/cuenta')

    await wrapper.find('input[type="email"]').setValue('ana@example.com')
    await wrapper.find('input[type="password"]').setValue('superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/cuenta')
  })

  it('enseña el detail RFC 7807 cuando el backend rechaza las credenciales', async () => {
    vi.mocked(postLogin).mockRejectedValue(
      new ApiError(401, 'API 401', { status: 401, detail: 'Email o contraseña incorrectos' }),
    )
    const { wrapper, router } = await montar()

    await wrapper.find('input[type="email"]').setValue('ana@example.com')
    await wrapper.find('input[type="password"]').setValue('malaclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toBe('Email o contraseña incorrectos')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
