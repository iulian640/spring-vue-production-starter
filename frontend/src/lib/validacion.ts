/**
 * Validación básica en cliente. La de verdad la hace siempre el backend;
 * esto solo evita viajes inútiles y da el error al teclear.
 */
const RE_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function esEmailValido(valor: string): boolean {
  return RE_EMAIL.test(valor)
}
