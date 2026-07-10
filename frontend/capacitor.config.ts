import type { CapacitorConfig } from '@capacitor/cli'

// Envoltorio nativo (Android) de la PWA. La web normal no pasa por aquí:
// este fichero solo lo lee `npx cap ...` al sincronizar/compilar la app nativa.

/*
 * SOLO desarrollo: con CAP_SERVER_URL el WebView carga la app desde ese
 * servidor en vez de los assets empaquetados. Es la forma de probar en el
 * emulador contra el backend local SIN pelearse con el contenido mixto: el
 * envoltorio por defecto vive en https://localhost y el WebView bloquea en
 * silencio cualquier fetch a un http:// (el login "no hace nada"). Cargando
 * desde el dev server, app y API comparten origen (el proxy de Vite) y todo
 * fluye — con hot reload dentro del emulador de regalo.
 *
 *   CAP_SERVER_URL=http://10.0.2.2:5173 npx cap sync android
 *
 * Sin la variable no cambia nada: assets empaquetados, como siempre. Nunca
 * se usa para release.
 */
const servidorDev = process.env.CAP_SERVER_URL

const config: CapacitorConfig = {
  appId: 'com.example.app',
  appName: 'Starter',
  webDir: 'dist',
  ...(servidorDev ? { server: { url: servidorDev, cleartext: true } } : {}),
}

export default config
