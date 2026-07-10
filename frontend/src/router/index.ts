import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { guardiaSesion } from './guardia'
import { instalarRecargaPorChunk } from './recargaPorChunk'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/registro',
      name: 'registro',
      component: () => import('../views/RegistroView.vue'),
    },
    {
      path: '/cuenta',
      name: 'cuenta',
      meta: { requiereSesion: true },
      component: () => import('../views/CuentaView.vue'),
    },
  ],
})

router.beforeEach(guardiaSesion)

// Recuperación ante deploys (chunks lazy con hash viejo): el cableado vive en
// recargaPorChunk.ts, testeado con un router de memoria; aquí solo se inyecta
// el mundo real (sessionStorage y location.assign).
instalarRecargaPorChunk(router, window.sessionStorage, (destino) =>
  window.location.assign(destino),
)

export default router
