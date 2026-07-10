import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { ApiError } from '../services/api'
import { useAuthStore } from './auth'

vi.mock('../services/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/auth')>()),
  postLogin: vi.fn(),
  postRegistro: vi.fn(),
  deleteCuenta: vi.fn(),
  postRefresh: vi.fn(),
  postLogout: vi.fn(),
}))
vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/api')>()),
  setAuthToken: vi.fn(),
}))

import { deleteCuenta, postLogin, postLogout, postRefresh, postRegistro } from '../services/auth'
import { setAuthToken } from '../services/api'

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  // El logout remoto es fire-and-forget: por defecto resuelve, y los tests
  // que quieren red rota lo re-stubbean.
  vi.mocked(postLogout).mockResolvedValue(undefined)
})

describe('auth store', () => {
  it('login OK: guarda token y email en memoria y lo registra en el cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBe('ana@example.com')
    expect(setAuthToken).toHaveBeenCalledWith('jwt-123')
    expect(auth.error).toBeNull()
  })

  it('login KO: expone el detail RFC 7807 y no deja sesión a medias', async () => {
    vi.mocked(postLogin).mockRejectedValue(
      new ApiError(401, 'API 401', { status: 401, detail: 'Email o contraseña incorrectos' }),
    )
    const auth = useAuthStore()

    const ok = await auth.iniciarSesion('ana@example.com', 'mala')

    expect(ok).toBe(false)
    expect(auth.autenticado).toBe(false)
    expect(auth.email).toBeNull()
    expect(auth.error).toBe('Email o contraseña incorrectos')
    expect(setAuthToken).not.toHaveBeenCalledWith(expect.stringContaining('jwt'))
  })

  it('registro OK encadena el login con las mismas credenciales', async () => {
    vi.mocked(postRegistro).mockResolvedValue({ email: 'ana@example.com' })
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-456', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-456', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()

    const ok = await auth.registrarse('ana@example.com', 'superclave123')

    expect(ok).toBe(true)
    expect(postRegistro).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(postLogin).toHaveBeenCalledWith('ana@example.com', 'superclave123')
    expect(auth.autenticado).toBe(true)
  })

  it('registro KO (409 email ya registrado): error legible y sin login', async () => {
    vi.mocked(postRegistro).mockRejectedValue(
      new ApiError(409, 'API 409', { status: 409, detail: 'Ese email ya está registrado' }),
    )
    const auth = useAuthStore()

    const ok = await auth.registrarse('ana@example.com', 'superclave123')

    expect(ok).toBe(false)
    expect(auth.error).toBe('Ese email ya está registrado')
    expect(postLogin).not.toHaveBeenCalled()
  })

  it('cerrarSesion limpia todo y desregistra el token del cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.cerrarSesion()

    expect(auth.autenticado).toBe(false)
    expect(auth.email).toBeNull()
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
  })

  it('sesionCaducada limpia la sesión y deja un aviso para la pantalla de login', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.sesionCaducada()

    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    expect(auth.aviso).toMatch(/sesión/i)
  })




  it('borrarCuenta OK: borra en el servidor, limpia la sesión ENTERA y deja el aviso de despedida', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(deleteCuenta).mockResolvedValue(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')
    const ok = await auth.borrarCuenta('superclave123')

    expect(ok).toBe(true)
    expect(deleteCuenta).toHaveBeenCalledWith('superclave123')
    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    expect(auth.aviso).toMatch(/borrado/i)
  })

  it('borrarCuenta con contraseña incorrecta (403): error legible y la sesión NO se toca', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(deleteCuenta).mockRejectedValue(
      new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
    )
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.borrarCuenta('laMala1234')

    expect(ok).toBe(false)
    expect(auth.errorBorrado).toBe('La contraseña no es correcta')
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBe('ana@example.com')
  })

  it('CRITICAL review: un borrado que resuelve tarde NO pisa la sesión de OTRO usuario', async () => {
    // Dispositivo compartido: Ana lanza el borrado, cierra sesión antes de que
    // resuelva, y Bea inicia sesión. La promesa vieja no puede limpiar la
    // sesión de Bea ni dejarle el aviso de despedida de Ana.
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-ana', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-ana', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    let resolverBorrado: () => void = () => {}
    vi.mocked(deleteCuenta).mockReturnValue(
      new Promise<void>((resolve) => {
        resolverBorrado = () => resolve()
      }),
    )
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const enVuelo = auth.borrarCuenta('superclave123')
    // Mientras el DELETE viaja: Ana sale y entra Bea.
    auth.cerrarSesion()
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-bea', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-bea', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    await auth.iniciarSesion('bea@example.com', 'otraclave123')

    resolverBorrado()
    await enVuelo

    // La sesión de Bea sigue intacta y sin el aviso de borrado de Ana.
    expect(auth.autenticado).toBe(true)
    expect(auth.email).toBe('bea@example.com')
    expect(setAuthToken).toHaveBeenLastCalledWith('jwt-bea')
    expect(auth.aviso).toBeNull()
  })

  it('un intento nuevo de borrado limpia el error del intento anterior', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(deleteCuenta)
      .mockRejectedValueOnce(
        new ApiError(403, 'API 403', { status: 403, detail: 'La contraseña no es correcta' }),
      )
      .mockResolvedValueOnce(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    await auth.borrarCuenta('laMala1234')
    const ok = await auth.borrarCuenta('superclave123')

    expect(ok).toBe(true)
    expect(auth.errorBorrado).toBeNull()
  })

  // --- Refresh y logout real (B4) ---

  it('refrescar rota los DOS tokens y sincroniza el cliente API', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(postRefresh).mockResolvedValue({
      token: 'jwt-rotado',
      expiraEn: '2026-07-09T00:15:00Z',
      refreshToken: 'refresh-rotado',
      refreshExpiraEn: '2026-07-17T00:15:00Z',
    })
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const ok = await auth.refrescar()

    expect(ok).toBe(true)
    expect(postRefresh).toHaveBeenCalledWith('refresh-jwt-123')
    expect(auth.token).toBe('jwt-rotado')
    expect(auth.refreshToken).toBe('refresh-rotado')
    expect(setAuthToken).toHaveBeenLastCalledWith('jwt-rotado')
  })

  it('refrescar sin sesión → false, sin llamar a la red', async () => {
    const auth = useAuthStore()

    expect(await auth.refrescar()).toBe(false)
    expect(postRefresh).not.toHaveBeenCalled()
  })

  it('refrescar con el refresh rechazado (revocado/caducado) → false y la sesión local queda como estaba', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(postRefresh).mockRejectedValue(new ApiError(401, 'API 401', null))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(await auth.refrescar()).toBe(false)
    // La expulsión la decide el cliente API, no este método.
    expect(auth.token).toBe('jwt-123')
  })

  it('un refresh que resuelve tarde NO resucita una sesión ya cerrada (y revoca el token nuevo)', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    let resolverRefresh: () => void = () => {}
    vi.mocked(postRefresh).mockReturnValue(
      new Promise((resolve) => {
        resolverRefresh = () =>
          resolve({
            token: 'jwt-zombi',
            expiraEn: '2026-07-09T00:15:00Z',
            refreshToken: 'refresh-zombi',
            refreshExpiraEn: '2026-07-17T00:15:00Z',
          })
      }),
    )
    vi.mocked(postLogout).mockResolvedValue(undefined)
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    const enVuelo = auth.refrescar()
    auth.cerrarSesion()
    resolverRefresh()

    expect(await enVuelo).toBe(false)
    expect(auth.autenticado).toBe(false)
    expect(setAuthToken).toHaveBeenLastCalledWith(null)
    // El refresh rotado que nadie va a usar se revoca para no dejarlo vivo.
    expect(postLogout).toHaveBeenCalledWith('refresh-zombi')
  })

  it('cerrarSesion revoca el refresh en el servidor (logout real) y limpia aunque la red falle', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    vi.mocked(postLogout).mockRejectedValue(new ApiError(0, 'sin red', null))
    const auth = useAuthStore()
    await auth.iniciarSesion('ana@example.com', 'superclave123')

    auth.cerrarSesion()

    expect(postLogout).toHaveBeenCalledWith('refresh-jwt-123')
    expect(auth.autenticado).toBe(false)
  })

  it('un login nuevo limpia el aviso de sesión caducada anterior', async () => {
    vi.mocked(postLogin).mockResolvedValue({ token: 'jwt-123', expiraEn: '2026-07-09T00:00:00Z', refreshToken: 'refresh-jwt-123', refreshExpiraEn: '2026-07-17T00:00:00Z' })
    const auth = useAuthStore()
    auth.sesionCaducada()

    await auth.iniciarSesion('ana@example.com', 'superclave123')

    expect(auth.aviso).toBeNull()
  })
})
