import { describe, expect, it } from 'vitest'
import { debeRecargarPorChunk, esErrorDeCargaDeChunk, limpiarMarcaRecarga } from './recargaChunks'

/** Doble de sessionStorage: suficiente con getItem/setItem/removeItem. */
function crearStorageFalso() {
  const datos = new Map<string, string>()
  return {
    getItem: (clave: string) => (datos.has(clave) ? datos.get(clave)! : null),
    setItem: (clave: string, valor: string) => void datos.set(clave, valor),
    removeItem: (clave: string) => void datos.delete(clave),
  }
}

describe('esErrorDeCargaDeChunk', () => {
  it('reconoce el mensaje de Chrome', () => {
    const error = new TypeError(
      'Failed to fetch dynamically imported module: https://app.app/assets/PerfilView-a1b2c3.js',
    )
    expect(esErrorDeCargaDeChunk(error)).toBe(true)
  })

  it('reconoce el mensaje de Firefox', () => {
    expect(esErrorDeCargaDeChunk(new TypeError('error loading dynamically imported module'))).toBe(
      true,
    )
  })

  it('reconoce el mensaje de Safari', () => {
    expect(esErrorDeCargaDeChunk(new TypeError('Importing a module script failed.'))).toBe(true)
  })

  it('NO se dispara con otros errores de navegación', () => {
    expect(esErrorDeCargaDeChunk(new Error('NetworkError when attempting to fetch resource'))).toBe(
      false,
    )
    expect(esErrorDeCargaDeChunk(new Error('boom'))).toBe(false)
  })

  it('NO se dispara con valores que no son Error', () => {
    expect(esErrorDeCargaDeChunk('Failed to fetch dynamically imported module')).toBe(false)
    expect(esErrorDeCargaDeChunk(null)).toBe(false)
    expect(esErrorDeCargaDeChunk(undefined)).toBe(false)
  })
})

describe('debeRecargarPorChunk', () => {
  it('la primera vez recarga y deja la marca; la segunda ya no (sin bucles)', () => {
    const storage = crearStorageFalso()

    expect(debeRecargarPorChunk(storage)).toBe(true)
    expect(debeRecargarPorChunk(storage)).toBe(false)
    expect(debeRecargarPorChunk(storage)).toBe(false)
  })

  it('tras limpiar la marca (navegación con éxito) puede volver a recargar', () => {
    const storage = crearStorageFalso()
    expect(debeRecargarPorChunk(storage)).toBe(true)

    limpiarMarcaRecarga(storage)

    expect(debeRecargarPorChunk(storage)).toBe(true)
  })

  it('con un storage roto no recarga: sin marca no se puede garantizar no ciclar', () => {
    const storageRoto = {
      getItem: () => {
        throw new Error('QuotaExceededError')
      },
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
    }

    expect(debeRecargarPorChunk(storageRoto)).toBe(false)
  })
})
