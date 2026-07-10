import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from './HomeView.vue'
import { useAuthStore } from '../stores/auth'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postLogout: vi.fn().mockResolvedValue(undefined),
}))

import { postLogin } from '../services/auth'

const Stub = { template: '<div />' }

function montar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: HomeView },
      { path: '/login', component: Stub },
      { path: '/registro', component: Stub },
      { path: '/cuenta', component: Stub },
    ],
  })
  return { wrapper: mount(HomeView, { global: { plugins: [pinia, router] } }) }
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('HomeView', () => {
  it('sin sesión ofrece crear cuenta y entrar', () => {
    const { wrapper } = montar()

    const enlaces = wrapper.findAll('a').map((a) => a.attributes('href'))
    expect(enlaces).toContain('/registro')
    expect(enlaces).toContain('/login')
  })

  it('con sesión lleva a la cuenta', async () => {
    vi.mocked(postLogin).mockResolvedValue({
      token: 'jwt-1',
      expiraEn: '2026-07-09T00:00:00Z',
      refreshToken: 'refresh-jwt-1',
      refreshExpiraEn: '2026-07-17T00:00:00Z',
    })
    const pinia = createPinia()
    setActivePinia(pinia)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/', component: HomeView },
        { path: '/cuenta', component: Stub },
      ],
    })
    const wrapper = mount(HomeView, { global: { plugins: [pinia, router] } })

    expect(wrapper.findAll('a').map((a) => a.attributes('href'))).toContain('/cuenta')
  })
})
