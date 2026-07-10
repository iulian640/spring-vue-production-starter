import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { ApiError } from '../services/api'
import CuentaView from './CuentaView.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
  deleteCuenta: vi.fn(),
  postRefresh: vi.fn(),
  postLogout: vi.fn(),
}))

import { deleteCuenta, postLogin, postLogout } from '../services/auth'

const Stub = { template: '<div />' }

function crearRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: Stub },
      { path: '/login', name: 'login', component: Stub },
      { path: '/cuenta', name: 'cuenta', component: CuentaView },
    ],
  })
}

async function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  // El token es de solo lectura: la sesión de prueba se abre por la puerta de verdad.
  vi.mocked(postLogin).mockResolvedValue({
    token: 'jwt-1',
    expiraEn: '2026-07-09T00:00:00Z',
    refreshToken: 'refresh-jwt-1',
    refreshExpiraEn: '2026-07-17T00:00:00Z',
  })
  const auth = useAuthStore()
  await auth.iniciarSesion('ana@example.com', 'superclave123')
  const router = crearRouter()
  await router.push('/cuenta')
  const wrapper = mount(CuentaView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router, auth }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(postLogout).mockResolvedValue(undefined)
})

describe('CuentaView', () => {
  it('enseña el email de la sesión y permite cerrar sesión (vuelve a la portada)', async () => {
    const { wrapper, router, auth } = await montar()

    expect(wrapper.text()).toContain('ana@example.com')
    await wrapper.find('button.salir').trigger('click')
    await flushPromises()

    expect(auth.autenticado).toBe(false)
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('la zona de borrado NO enseña la contraseña de primeras (doble confirmación)', async () => {
    const { wrapper } = await montar()

    expect(wrapper.text()).toMatch(/borrar tu cuenta/i)
    expect(wrapper.find('#password-borrado').exists()).toBe(false)

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    expect(wrapper.find('#password-borrado').exists()).toBe(true)
    expect(wrapper.text()).toMatch(/no hay vuelta atrás/i)
  })

  it('confirmar con contraseña borra la cuenta, cierra la sesión y va a la portada', async () => {
    vi.mocked(deleteCuenta).mockResolvedValue(undefined)
    const { wrapper, router, auth } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('#password-borrado').setValue('superclave123')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()

    expect(deleteCuenta).toHaveBeenCalledWith('superclave123')
    expect(auth.autenticado).toBe(false)
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('contraseña incorrecta (403): el error se ve en el panel y la sesión sigue viva', async () => {
    vi.mocked(deleteCuenta).mockRejectedValue(
      new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
    )
    const { wrapper, router, auth } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('#password-borrado').setValue('laMala1234')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('La contraseña no es correcta')
    expect(auth.autenticado).toBe(true)
    expect(router.currentRoute.value.path).toBe('/cuenta')
  })

  it('cancelar un intento fallido y reabrir NO enseña el error viejo', async () => {
    vi.mocked(deleteCuenta).mockRejectedValue(
      new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
    )
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('#password-borrado').setValue('laMala1234')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()
    await wrapper.find('button.boton-cancelar-borrado').trigger('click')
    await wrapper.find('button.boton-abrir-borrado').trigger('click')

    expect(wrapper.text()).not.toContain('La contraseña no es correcta')
  })

  it('el botón de confirmar exige contraseña: vacío no dispara nada', async () => {
    const { wrapper } = await montar()

    await wrapper.find('button.boton-abrir-borrado').trigger('click')
    await wrapper.find('form.form-borrado').trigger('submit')
    await flushPromises()

    expect(deleteCuenta).not.toHaveBeenCalled()
  })
})
