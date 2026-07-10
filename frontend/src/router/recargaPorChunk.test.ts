import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { instalarRecargaPorChunk } from './recargaPorChunk'

const Stub = { template: '<div />' }

/** Doble de sessionStorage: suficiente con getItem/setItem/removeItem. */
function crearStorageFalso() {
  const datos = new Map<string, string>()
  return {
    getItem: (clave: string) => (datos.has(clave) ? datos.get(clave)! : null),
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  }
}

/**
 * Router de memoria con el cableado REAL instalado y dos rutas lazy cuyo
 * import() se controla desde el test: fallando simulan la pestaña con JS
 * viejo pidiendo chunks que ya no existen tras un deploy.
 */
function crearEntorno(error: () => Error = errorDeChunk) {
  const estado = { falla: true }
  const cargaLazy = () =>
    estado.falla ? Promise.reject(error()) : Promise.resolve(Stub)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Stub },
      { path: '/perfil', component: cargaLazy },
      { path: '/cuenta', component: cargaLazy },
    ],
  })
  const storage = crearStorageFalso()
  const recargar = vi.fn()
  instalarRecargaPorChunk(router, storage, recargar)
  return { router, storage, recargar, estado }
}

function errorDeChunk() {
  // Mensaje real de Chrome cuando el chunk con hash viejo ya no existe.
  return new TypeError(
    'Failed to fetch dynamically imported module: https://sofrito.app/assets/PerfilView-a1b2c3.js',
  )
}

/** La navegación fallida rechaza el push: aquí el fallo es el caso esperado. */
async function navegarFallando(router: ReturnType<typeof crearEntorno>['router'], destino: string) {
  await router.push(destino).catch(() => {})
}

describe('instalarRecargaPorChunk (cableado real sobre un router de memoria)', () => {
  it('un fallo de chunk recarga UNA sola vez hacia la ruta destino', async () => {
    const { router, recargar } = crearEntorno()

    await navegarFallando(router, '/perfil')

    expect(recargar).toHaveBeenCalledTimes(1)
    expect(recargar).toHaveBeenCalledWith('/perfil')
  })

  it('si el fallo persiste tras recargar (deploy roto), no entra en bucle', async () => {
    const { router, recargar } = crearEntorno()

    await navegarFallando(router, '/perfil')
    await navegarFallando(router, '/perfil')

    expect(recargar).toHaveBeenCalledTimes(1)
  })

  it('una navegación con éxito limpia la marca: un deploy futuro vuelve a recuperarse', async () => {
    const { router, recargar, estado } = crearEntorno()
    await navegarFallando(router, '/perfil')
    expect(recargar).toHaveBeenCalledTimes(1)

    // La recarga funcionó: la app nueva navega bien y retira la marca.
    estado.falla = false
    await router.push('/perfil')

    // Deploy futuro: otro chunk viejo falla y puede volver a recargarse.
    estado.falla = true
    await navegarFallando(router, '/cuenta')

    expect(recargar).toHaveBeenCalledTimes(2)
    expect(recargar).toHaveBeenLastCalledWith('/cuenta')
  })

  it('otros errores de navegación NO fuerzan recarga', async () => {
    const { router, recargar } = crearEntorno(() => new Error('boom'))

    await navegarFallando(router, '/perfil')

    expect(recargar).not.toHaveBeenCalled()
  })
})
