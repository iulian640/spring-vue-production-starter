import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { setAuthToken } from '../services/api'
import { deleteCuenta, postLogin, postLogout, postRefresh, postRegistro } from '../services/auth'
import { mensajeDeError } from '../lib/formato'

/**
 * Sesión del usuario. REQUISITO DE SEGURIDAD: el JWT vive SOLO aquí, en
 * memoria — nunca localStorage, sessionStorage ni cookies legibles por JS
 * (un XSS no debe encontrar una credencial persistida). Al recargar la
 * página la sesión se pierde y se vuelve a pedir login; aceptado para v1.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const email = ref<string | null>(null)
  const expiraEn = ref<string | null>(null)
  /** Refresh opaco (B4): también SOLO en memoria; rota en cada renovación. */
  const refreshToken = ref<string | null>(null)
  const refreshExpiraEn = ref<string | null>(null)
  const cargando = ref(false)
  const error = ref<string | null>(null)
  /** Mensaje informativo (p. ej. "tu sesión ha caducado") para la pantalla de login. */
  const aviso = ref<string | null>(null)

  const autenticado = computed(() => token.value !== null)

  async function iniciarSesion(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    aviso.value = null
    try {
      const emitido = await postLogin(emailForm, password)
      token.value = emitido.token
      expiraEn.value = emitido.expiraEn
      refreshToken.value = emitido.refreshToken
      refreshExpiraEn.value = emitido.refreshExpiraEn
      email.value = emailForm
      setAuthToken(emitido.token)
      return true
    } catch (e) {
      limpiarSesion()
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
  }

  async function registrarse(emailForm: string, password: string): Promise<boolean> {
    if (cargando.value) {
      return false
    }
    cargando.value = true
    error.value = null
    try {
      await postRegistro(emailForm, password)
    } catch (e) {
      error.value = mensajeDeError(e)
      return false
    } finally {
      cargando.value = false
    }
    // Cuenta creada: entramos directamente con las mismas credenciales.
    return iniciarSesion(emailForm, password)
  }

  /**
   * Punto central de limpieza: por aquí pasan tanto el logout manual
   * (cerrarSesion) como la expulsión por 401 (sesionCaducada). Además del
   * token se vacían los stores de cuenta y de fichajes — son singletons y, en
   * un dispositivo compartido, el siguiente usuario no debe heredar ni los
   * datos salariales ni la libreta del anterior. La dependencia va en un solo
   * sentido (auth → cuenta/fichajes; ninguno importa auth), así que no hay
   * ciclo entre stores.
   */
  function limpiarSesion() {
    token.value = null
    email.value = null
    expiraEn.value = null
    refreshToken.value = null
    refreshExpiraEn.value = null
    setAuthToken(null)
    // Si tu app añade stores con datos del usuario, límpialos AQUÍ: en un
    // dispositivo compartido, el siguiente usuario no debe heredar nada.
  }

  /**
   * Logout voluntario: además de limpiar en local, REVOCA el refresh en el
   * servidor (B4, logout real). En dos pasos y sin esperar la red: la sesión
   * local muere ya aunque el POST tarde o falle (mejor un token huérfano que
   * una sesión viva en un dispositivo compartido).
   */
  function cerrarSesion() {
    const enServidor = refreshToken.value
    if (enServidor !== null) {
      void postLogout(enServidor).catch(() => {
        // Sin red no hay revocación remota: el refresh caduca solo.
      })
    }
    limpiarSesion()
    error.value = null
    aviso.value = null
  }

  /** 401 con token: la sesión ya no vale. Se limpia y se avisa en el login. */
  function sesionCaducada() {
    limpiarSesion()
    aviso.value = 'Tu sesión ha caducado. Entra de nuevo, por favor.'
  }

  /**
   * Renueva la sesión con el refresh (B4). La llama el cliente API (via
   * main.ts) cuando un 401 delata el access caducado: si devuelve true, la
   * petición original se reintenta con el token rotado; si false, expulsión.
   * Mismo blindaje de sesión cruzada que borrarCuenta: si la sesión cambió con
   * el refresh en vuelo, el resultado se descarta (y se revoca, para no dejar
   * una sesión huérfana viva en el servidor).
   */
  async function refrescar(): Promise<boolean> {
    const enUso = refreshToken.value
    if (enUso === null) {
      return false
    }
    try {
      const emitido = await postRefresh(enUso)
      if (refreshToken.value !== enUso) {
        void postLogout(emitido.refreshToken).catch(() => {})
        return false
      }
      token.value = emitido.token
      expiraEn.value = emitido.expiraEn
      refreshToken.value = emitido.refreshToken
      refreshExpiraEn.value = emitido.refreshExpiraEn
      setAuthToken(emitido.token)
      return true
    } catch {
      // Refresh caducado, revocado o reutilizado: no hay renovación posible.
      return false
    }
  }

  const borrando = ref(false)
  /** Error del borrado de cuenta (contraseña incorrecta...), para su propio panel. */
  const errorBorrado = ref<string | null>(null)

  /** El panel de borrado limpia su error al abrirse o cancelarse (review HIGH):
   *  un error de un intento anterior no puede reaparecer en un intento nuevo. */
  function limpiarErrorBorrado() {
    errorBorrado.value = null
  }

  /**
   * Borrado de cuenta (RGPD art. 17). Si el servidor confirma, la sesión se
   * limpia ENTERA (misma rutina que el logout: en un dispositivo compartido no
   * queda nada del usuario borrado) y se deja un aviso de despedida. Si falla
   * (contraseña incorrecta → 403, que a propósito no expulsa), la sesión sigue
   * viva y el error se enseña donde se pidió el borrado.
   */
  async function borrarCuenta(password: string): Promise<boolean> {
    if (borrando.value) {
      return false
    }
    borrando.value = true
    errorBorrado.value = null
    // Capturado ANTES del await (review CRITICAL, mismo patrón que api.ts):
    // si la sesión cambia con el DELETE en vuelo (dispositivo compartido: el
    // dueño sale y entra otra persona), la resolución tardía no puede limpiar
    // la sesión NUEVA ni dejarle el aviso de despedida de la cuenta borrada.
    const tokenAlEmpezar = token.value
    try {
      await deleteCuenta(password)
    } catch (e) {
      errorBorrado.value = mensajeDeError(e)
      return false
    } finally {
      borrando.value = false
    }
    if (token.value === tokenAlEmpezar) {
      limpiarSesion()
      aviso.value = 'Tu cuenta y todos tus datos se han borrado.'
    }
    return true
  }

  return {
    // Solo lectura hacia fuera: nadie puede tocar el token sin pasar por las
    // acciones del store (que mantienen el cliente API sincronizado). El JWT
    // que viaja lo gestiona services/api.ts.
    token: computed(() => token.value),
    refreshToken: computed(() => refreshToken.value),
    refreshExpiraEn: computed(() => refreshExpiraEn.value),
    email,
    expiraEn,
    cargando,
    error,
    aviso,
    autenticado,
    borrando,
    errorBorrado,
    iniciarSesion,
    registrarse,
    cerrarSesion,
    sesionCaducada,
    refrescar,
    borrarCuenta,
    limpiarErrorBorrado,
  }
})
