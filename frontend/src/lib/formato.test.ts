import { describe, expect, it } from 'vitest'
import { ApiError } from '../services/api'
import { mensajeDeError } from './formato'

describe('mensajeDeError', () => {
  it('usa el detail de un error RFC 7807 del backend', () => {
    const error = new ApiError(401, 'API 401', {
      status: 401,
      detail: 'Email o contraseña incorrectos',
    })
    expect(mensajeDeError(error)).toBe('Email o contraseña incorrectos')
  })

  it('cae al message del error si no hay detail', () => {
    expect(mensajeDeError(new ApiError(500, 'API 500: Internal Server Error'))).toBe(
      'API 500: Internal Server Error',
    )
  })

  it('da un mensaje genérico para errores que no son Error', () => {
    expect(mensajeDeError('boom')).toBe('Algo ha fallado. Inténtalo de nuevo.')
  })
})
