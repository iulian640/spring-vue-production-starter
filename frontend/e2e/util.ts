import { expect, type Page } from '@playwright/test'

/** Cada viaje estrena cuenta: la BD es real y los tests no comparten estado. */
export function emailUnico(): string {
  return `e2e-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@sofrito.test`
}

export const PASSWORD_E2E = 'Clave-e2e-2026!'

/**
 * Crea una cuenta nueva y espera a estar dentro (el registro encadena el
 * login y aterriza en /cuenta). OJO: el token vive SOLO en memoria — a partir
 * de aquí se navega por la interfaz (barra inferior, enlaces), nunca con
 * page.goto(), que recarga la SPA y pierde la sesión.
 */
export async function registra(page: Page, email: string): Promise<void> {
  await page.goto('/registro')
  await page.locator('#email').fill(email)
  await page.locator('#password').fill(PASSWORD_E2E)
  await page.locator('#repite').fill(PASSWORD_E2E)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await expect(page).toHaveURL(/\/cuenta$/)
}
