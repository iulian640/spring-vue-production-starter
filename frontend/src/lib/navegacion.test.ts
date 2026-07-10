import { describe, expect, it } from 'vitest'
import { destinoTrasLogin } from './navegacion'

describe('destinoTrasLogin', () => {
  it('acepta una ruta interna', () => {
    expect(destinoTrasLogin('/cuenta')).toBe('/cuenta')
    expect(destinoTrasLogin('/perfil?x=1')).toBe('/perfil?x=1')
  })

  it('rechaza URLs absolutas (open redirect)', () => {
    expect(destinoTrasLogin('https://evil.example')).toBe('/cuenta')
    expect(destinoTrasLogin('http://evil.example/cuenta')).toBe('/cuenta')
  })

  it('rechaza rutas protocol-relative (//evil.example)', () => {
    expect(destinoTrasLogin('//evil.example')).toBe('/cuenta')
  })

  it('rechaza rutas con backslash que el navegador normaliza a // (/\\evil)', () => {
    expect(destinoTrasLogin('/\\evil.example')).toBe('/cuenta')
  })

  it('cae al destino por defecto si no hay redirect o no es un string', () => {
    expect(destinoTrasLogin(undefined)).toBe('/cuenta')
    expect(destinoTrasLogin(null)).toBe('/cuenta')
    expect(destinoTrasLogin(['/a', '/b'])).toBe('/cuenta')
    expect(destinoTrasLogin('cuenta')).toBe('/cuenta')
  })
})
