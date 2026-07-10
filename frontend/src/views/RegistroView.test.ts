import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import RegistroView from './RegistroView.vue'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
}))

import { postLogin, postRegistro } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/registro', name: 'registro', component: RegistroView },
      { path: '/cuenta', name: 'cuenta', component: Stub },
    ],
  })
}

async function montar() {
  const router = crearRouter()
  await router.push('/registro')
  const wrapper = mount(RegistroView, { global: { plugins: [createPinia(), router] } })
  return { wrapper, router }
}

async function rellenar(
  wrapper: Awaited<ReturnType<typeof montar>>['wrapper'],
  email: string,
  password: string,
  repite: string,
) {
  await wrapper.find('input[type="email"]').setValue(email)
  const passwords = wrapper.findAll('input[type="password"]')
  await passwords[0].setValue(password)
  await passwords[1].setValue(repite)
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('RegistroView', () => {
  it('rechaza en cliente una contraseña de menos de 10 caracteres', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'corta', 'corta')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postRegistro).not.toHaveBeenCalled()
    expect(wrapper.text()).toMatch(/10 caracteres/)
  })

  it('rechaza en cliente si las contraseñas no coinciden', async () => {
    const { wrapper } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123', 'superclave124')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postRegistro).not.toHaveBeenCalled()
    expect(wrapper.text()).toMatch(/no coinciden/i)
  })

  it('con datos válidos registra, entra y navega a la cuenta', async () => {
    vi.mocked(postRegistro).mockResolvedValue({ email: 'ana@example.com' })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-1', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-1', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const { wrapper, router } = await montar()

    await rellenar(wrapper, 'ana@example.com', 'superclave123', 'superclave123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(postRegistro).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(router.currentRoute.value.path).toBe('/cuenta')
  })
})
