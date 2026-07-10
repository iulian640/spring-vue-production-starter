import { describe, expect, it } from 'vitest'
import { esEmailValido } from './validacion'

describe('esEmailValido', () => {
  it('acepta emails normales', () => {
    expect(esEmailValido('ana@example.com')).toBe(true)
    expect(esEmailValido('ana.perez+trabajo@sub.dominio.es')).toBe(true)
  })

  it('rechaza formatos sin pinta de email', () => {
    expect(esEmailValido('')).toBe(false)
    expect(esEmailValido('ana')).toBe(false)
    expect(esEmailValido('ana@')).toBe(false)
    expect(esEmailValido('ana@dominio')).toBe(false)
    expect(esEmailValido('ana con espacios@x.com')).toBe(false)
  })
})
