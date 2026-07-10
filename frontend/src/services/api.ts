/**
 * Minimal API client over fetch.
 *
 * In dev, Vite proxies /api to the local Spring Boot backend (localhost:8080).
 * In production (same-origin web) the relative default works as-is; once the app
 * is packaged with Capacitor the WebView is a different origin, so the base URL
 * comes from VITE_API_URL when present.
 */
const API_BASE = `${import.meta.env.VITE_API_URL ?? ''}/api/v1`

const REQUEST_TIMEOUT_MS = 15000

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, message: string, body: unknown = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/**
 * Token de sesión SOLO en memoria (requisito de seguridad: nunca localStorage,
 * sessionStorage ni cookies legibles por JS — un XSS no debe poder exfiltrar
 * una credencial persistida). Al recargar la página se pierde y toca hacer
 * login otra vez; aceptado para v1.
 */
let authToken: string | null = null

/** Aviso de sesión inválida (401 con token). Lo registra main.ts para limpiar sesión y llevar a login. */
let onUnauthorized: (() => void) | null = null

/**
 * Renovación de la sesión (B4). Lo registra main.ts: devuelve true si el
 * refresh rotó los tokens (y la petición original puede reintentarse) o false
 * si la sesión ya no tiene arreglo (toca expulsar).
 */
let onRefresh: (() => Promise<boolean>) | null = null

/** Single-flight: N peticiones con 401 a la vez comparten UN solo refresh. */
let refreshEnVuelo: Promise<boolean> | null = null

/** Opciones del cliente además de las de fetch. */
export interface OpcionesApi extends RequestInit {
  /**
   * No adjuntar el Bearer aunque haya sesión. Para /auth/refresh y
   * /auth/logout: viajan con el refresh en el body, y un access CADUCADO en
   * la cabecera haría que el resource server respondiera 401 antes de mirar
   * nada (y de paso evita cualquier bucle refresh→401→refresh).
   */
  anonimo?: boolean
}

export function setAuthToken(token: string | null) {
  authToken = token
}

export function setOnUnauthorized(handler: (() => void) | null) {
  onUnauthorized = handler
}

/**
 * Registra el renovador. Resetea también el guardián single-flight A PROPÓSITO:
 * esta función solo se llama en el arranque (main.ts) y en los tests, y un
 * handler nuevo no debe heredar una promesa del handler anterior. Si algún día
 * se reasignara en caliente, separar ambas cosas.
 */
export function setOnRefresh(handler: (() => Promise<boolean>) | null) {
  onRefresh = handler
  refreshEnVuelo = null
}

async function envia(path: string, options: OpcionesApi, esReintento = false): Promise<Response> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)

  // Capturado antes del await: si la sesión cambia en vuelo, el 401 de esta
  // respuesta solo dispara el handler si ESTA petición iba autenticada.
  const tokenEnviado = options.anonimo ? null : authToken
  // fetch no conoce 'anonimo': fuera antes de pasárselo.
  const { anonimo: _anonimo, ...init } = options
  void _anonimo

  let response: Response
  try {
    // Spread options first so caller headers merge with — not clobber — the defaults.
    response = await fetch(`${API_BASE}${path}`, {
      signal: controller.signal,
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(tokenEnviado ? { Authorization: `Bearer ${tokenEnviado}` } : {}),
        ...init.headers,
      },
    })
  } finally {
    clearTimeout(timeout)
  }

  // 401 con token: antes de expulsar se intenta renovar la sesión UNA vez
  // (B4). Sin token (login fallido, refresh, logout) no hay nada que renovar.
  if (response.status === 401 && tokenEnviado !== null) {
    if (!esReintento && onRefresh !== null) {
      if (refreshEnVuelo === null) {
        refreshEnVuelo = onRefresh().finally(() => {
          refreshEnVuelo = null
        })
      }
      const renovado = await refreshEnVuelo
      if (renovado) {
        // Reintento único con el token ya rotado (envia lo relee del módulo).
        return envia(path, options, true)
      }
    }
    // Solo se expulsa si la sesión actual sigue siendo la que emitió ESTA
    // petición (vue review, CRITICAL): en un dispositivo compartido, el 401
    // tardío de una sesión ya sustituida (Ana salió, Bea entró mientras el
    // refresh viajaba) no puede echar a la persona que está dentro ahora.
    if (authToken === tokenEnviado) {
      onUnauthorized?.()
    }
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null)
    const message =
      (body && typeof body === 'object' && 'message' in body && String(body.message)) ||
      `API ${response.status}: ${response.statusText}`
    throw new ApiError(response.status, message, body)
  }

  return response
}

async function request<T>(path: string, options: OpcionesApi = {}): Promise<T> {
  const response = await envia(path, options)

  if (response.status === 204 || response.headers.get('Content-Length') === '0') {
    return undefined as T
  }

  return response.json() as Promise<T>
}

/**
 * GET binario (el informe PDF): mismas reglas que el resto — timeout, token en
 * memoria, refresh+reintento del 401 y errores RFC 7807 — pero devolviendo el Blob.
 */
async function requestBlob(path: string, options: OpcionesApi = {}): Promise<Blob> {
  const response = await envia(path, options)
  return response.blob()
}

export const api = {
  get: <T>(path: string, options: OpcionesApi = {}) => request<T>(path, options),

  getBlob: (path: string, options: OpcionesApi = {}) => requestBlob(path, options),

  post: <T>(path: string, body: unknown, options: OpcionesApi = {}) =>
    request<T>(path, { ...options, method: 'POST', body: JSON.stringify(body) }),

  put: <T>(path: string, body: unknown, options: OpcionesApi = {}) =>
    request<T>(path, { ...options, method: 'PUT', body: JSON.stringify(body) }),

  // El body es opcional: el borrado de cuenta re-confirma con la contraseña.
  delete: <T>(path: string, body?: unknown, options: OpcionesApi = {}) =>
    request<T>(path, {
      ...options,
      method: 'DELETE',
      ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    }),
}

export interface HealthResponse {
  status: string
}

export const getHealth = () => api.get<HealthResponse>('/health')
