import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, getHealth, setAuthToken, setOnRefresh, setOnUnauthorized } from './api'

function mockFetch(response: Partial<Response> & { jsonValue?: unknown; blobValue?: Blob }) {
  const fetchMock = vi.fn().mockResolvedValue({
    ok: response.ok ?? true,
    status: response.status ?? 200,
    statusText: response.statusText ?? 'OK',
    headers: response.headers ?? new Headers(),
    json: async () => response.jsonValue ?? {},
    blob: async () => response.blobValue ?? new Blob(),
  } as Response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  setAuthToken(null)
  setOnUnauthorized(null)
  setOnRefresh(null)
})

describe('api client', () => {
  it('sends the default Content-Type on a plain GET', async () => {
    const fetchMock = mockFetch({ jsonValue: { status: 'ok' } })

    await getHealth()

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json')
  })

  it('keeps the default Content-Type when the caller adds their own header', async () => {
    // Regression: spreading options after headers used to clobber Content-Type,
    // silently breaking every authenticated (Bearer) request once JWT lands.
    const fetchMock = mockFetch({ jsonValue: {} })

    await api.get('/whatever', { headers: { Authorization: 'Bearer token' } })

    const [, init] = fetchMock.mock.calls[0]
    const headers = init.headers as Record<string, string>
    expect(headers['Content-Type']).toBe('application/json')
    expect(headers['Authorization']).toBe('Bearer token')
  })

  it('does not clobber method/body when merging headers', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })

    await api.post('/x', { a: 1 })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.body).toBe(JSON.stringify({ a: 1 }))
  })

  it('getBlob devuelve el binario y manda el token igual que el resto', async () => {
    const pdf = new Blob(['%PDF'], { type: 'application/pdf' })
    const fetchMock = mockFetch({ blobValue: pdf })
    setAuthToken('jwt-1')

    const resultado = await api.getBlob('/informes/mes/2026-07')

    expect(resultado).toBe(pdf)
    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer jwt-1')
  })

  it('getBlob convierte un error RFC 7807 en ApiError, como el resto del cliente', async () => {
    mockFetch({
      ok: false,
      status: 422,
      statusText: 'Unprocessable Entity',
      jsonValue: { detail: 'No has definido tu horario' },
    })

    await expect(api.getBlob('/informes/mes/2026-07')).rejects.toBeInstanceOf(ApiError)
  })

  it('throws ApiError with the backend message on a non-ok response', async () => {
    mockFetch({
      ok: false,
      status: 422,
      statusText: 'Unprocessable Entity',
      jsonValue: { message: 'convenio no encontrado' },
    })

    await expect(getHealth()).rejects.toMatchObject({
      name: 'ApiError',
      status: 422,
      message: 'convenio no encontrado',
    })
    await expect(getHealth()).rejects.toBeInstanceOf(ApiError)
  })

  it('returns undefined on a 204 No Content instead of parsing an empty body', async () => {
    mockFetch({ status: 204, headers: new Headers({ 'Content-Length': '0' }) })

    await expect(api.delete('/x')).resolves.toBeUndefined()
  })

  it('delete admite body JSON (el borrado de cuenta re-confirma con la contraseña)', async () => {
    const fetchMock = mockFetch({ status: 204, headers: new Headers({ 'Content-Length': '0' }) })

    await api.delete('/cuenta', { password: 'superclave123' })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('DELETE')
    expect(init.body).toBe(JSON.stringify({ password: 'superclave123' }))
  })

  it('delete sin body sigue sin mandar body (no rompe a los llamadores de siempre)', async () => {
    const fetchMock = mockFetch({ status: 204, headers: new Headers({ 'Content-Length': '0' }) })

    await api.delete('/x')

    const [, init] = fetchMock.mock.calls[0]
    expect(init.body).toBeUndefined()
  })
})

describe('api client auth token', () => {
  it('attaches Authorization: Bearer when a token is set', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })
    setAuthToken('mi-jwt')

    await api.get('/perfil')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer mi-jwt')
  })

  it('sends no Authorization header when there is no token', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })

    await api.get('/provincias')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBeUndefined()
  })

  it('stops attaching the token after clearing it', async () => {
    const fetchMock = mockFetch({ jsonValue: {} })
    setAuthToken('mi-jwt')
    setAuthToken(null)

    await api.get('/provincias')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBeUndefined()
  })

  it('notifies the unauthorized handler on a 401 from an authenticated request', async () => {
    mockFetch({ ok: false, status: 401, statusText: 'Unauthorized', jsonValue: null })
    const onUnauthorized = vi.fn()
    setAuthToken('jwt-caducado')
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('does NOT notify the handler on a 401 without token (failed login is not an expired session)', async () => {
    mockFetch({ ok: false, status: 401, statusText: 'Unauthorized', jsonValue: null })
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    await expect(api.post('/auth/login', { email: 'a@b.c', password: 'x' })).rejects.toBeInstanceOf(
      ApiError,
    )
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('does NOT notify the handler on non-401 errors', async () => {
    mockFetch({ ok: false, status: 404, statusText: 'Not Found', jsonValue: null })
    const onUnauthorized = vi.fn()
    setAuthToken('mi-jwt')
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})

describe('renovación de sesión (B4)', () => {
  function respuesta(status: number, jsonValue: unknown = null) {
    return {
      ok: status < 400,
      status,
      statusText: String(status),
      headers: new Headers(),
      json: async () => jsonValue,
      blob: async () => new Blob(),
    } as Response
  }

  /** fetch que responde 401 al token viejo y 200 al rotado. */
  function fetchQueExigeTokenNuevo() {
    const fetchMock = vi.fn().mockImplementation(async (_url: string, init: RequestInit) => {
      const auth = (init.headers as Record<string, string>)['Authorization']
      return auth === 'Bearer jwt-nuevo' ? respuesta(200, { dato: 'ok' }) : respuesta(401)
    })
    vi.stubGlobal('fetch', fetchMock)
    return fetchMock
  }

  it('un 401 con token refresca la sesión y reintenta la petición con el token rotado', async () => {
    const fetchMock = fetchQueExigeTokenNuevo()
    setAuthToken('jwt-caducado')
    const onRefresh = vi.fn().mockImplementation(async () => {
      setAuthToken('jwt-nuevo')
      return true
    })
    setOnRefresh(onRefresh)
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).resolves.toEqual({ dato: 'ok' })

    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(onUnauthorized).not.toHaveBeenCalled()
    const [, reintento] = fetchMock.mock.calls[1]
    expect((reintento.headers as Record<string, string>)['Authorization']).toBe('Bearer jwt-nuevo')
  })

  it('dos 401 simultáneos comparten UN solo refresh (single-flight)', async () => {
    fetchQueExigeTokenNuevo()
    setAuthToken('jwt-caducado')
    const onRefresh = vi.fn().mockImplementation(async () => {
      setAuthToken('jwt-nuevo')
      return true
    })
    setOnRefresh(onRefresh)

    await expect(Promise.all([api.get('/a'), api.get('/b')])).resolves.toEqual([
      { dato: 'ok' },
      { dato: 'ok' },
    ])
    expect(onRefresh).toHaveBeenCalledTimes(1)
  })

  it('si el refresh falla, expulsión de siempre y la petición revienta con 401', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuesta(401)))
    setAuthToken('jwt-caducado')
    setOnRefresh(vi.fn().mockResolvedValue(false))
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('el reintento es ÚNICO: si el 401 persiste tras refrescar, expulsión (sin bucle)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuesta(401)))
    setAuthToken('jwt-caducado')
    const onRefresh = vi.fn().mockResolvedValue(true)
    setOnRefresh(onRefresh)
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    await expect(api.get('/perfil')).rejects.toBeInstanceOf(ApiError)
    expect(onRefresh).toHaveBeenCalledTimes(1)
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('CRITICAL review: el 401 tardío de una sesión VIEJA no expulsa a quien está dentro ahora', async () => {
    // Tablet compartida: la petición de Ana recibe 401 y su refresh queda en
    // vuelo; Bea inicia sesión mientras tanto; el refresh viejo falla. La
    // petición vieja debe morir en silencio, sin tocar la sesión de Bea.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuesta(401)))
    setAuthToken('jwt-ana')
    let resolverRefresh: (v: boolean) => void = () => {}
    setOnRefresh(
      vi.fn().mockReturnValue(
        new Promise<boolean>((resolve) => {
          resolverRefresh = resolve
        }),
      ),
    )
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    const peticionDeAna = api.get('/perfil')
    // Deja que el 401 llegue y el refresh quede pendiente.
    await new Promise((r) => setTimeout(r, 0))
    setAuthToken('jwt-bea') // Bea entra con el refresh de Ana aún en vuelo.
    resolverRefresh(false)

    await expect(peticionDeAna).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('getBlob también renueva y reintenta tras un 401 (la descarga del PDF no se queda muda)', async () => {
    const pdf = new Blob(['%PDF'])
    const fetchMock = vi.fn().mockImplementation(async (_url: string, init: RequestInit) => {
      const auth = (init.headers as Record<string, string>)['Authorization']
      if (auth === 'Bearer jwt-nuevo') {
        return { ...respuesta(200), blob: async () => pdf } as Response
      }
      return respuesta(401)
    })
    vi.stubGlobal('fetch', fetchMock)
    setAuthToken('jwt-caducado')
    setOnRefresh(
      vi.fn().mockImplementation(async () => {
        setAuthToken('jwt-nuevo')
        return true
      }),
    )

    await expect(api.getBlob('/informes/mes/2026-07')).resolves.toBe(pdf)
  })

  it('una petición anonimo no manda Authorization ni dispara refresh (así viaja el propio /auth/refresh)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(respuesta(401))
    vi.stubGlobal('fetch', fetchMock)
    setAuthToken('jwt-caducado')
    const onRefresh = vi.fn()
    setOnRefresh(onRefresh)
    const onUnauthorized = vi.fn()
    setOnUnauthorized(onUnauthorized)

    await expect(api.post('/auth/refresh', { refreshToken: 'x' }, { anonimo: true }))
      .rejects.toBeInstanceOf(ApiError)

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>)['Authorization']).toBeUndefined()
    expect(onRefresh).not.toHaveBeenCalled()
    expect(onUnauthorized).not.toHaveBeenCalled()
  })
})
