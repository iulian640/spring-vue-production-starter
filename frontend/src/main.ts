import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { crearManejador401 } from './router/sesion401'
import { setOnRefresh, setOnUnauthorized } from './services/api'
import { useAuthStore } from './stores/auth'
import { registrarPWA } from './pwa'

const pinia = createPinia()

createApp(App).use(pinia).use(router).mount('#app')

// Un 401 con access caducado intenta renovar la sesión ANTES de expulsar (B4);
// si el refresh tampoco vale, cae al manejador de sesión caducada de siempre.
setOnRefresh(() => useAuthStore(pinia).refrescar())
setOnUnauthorized(crearManejador401(router, pinia))

// Service worker con recarga al actualizar y comprobación periódica (ver pwa.ts).
registrarPWA()
