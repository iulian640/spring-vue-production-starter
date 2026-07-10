import { expect, test } from '@playwright/test'
import { emailUnico, PASSWORD_E2E, registra } from './util.js'

/**
 * Borrado de cuenta (RGPD art. 17), de punta a punta: doble confirmación,
 * la sesión se va, y las credenciales dejan de existir en el servidor.
 */
test('borrar la cuenta destruye la sesión y las credenciales', async ({ page }) => {
  const email = emailUnico()
  await registra(page, email)

  // Paso 1: la zona de borrado avisa y pide abrir.
  await page.getByRole('button', { name: 'Quiero borrar mi cuenta' }).click()
  // Paso 2: advertencia final + contraseña.
  await expect(page.getByText(/no hay vuelta atrás/i)).toBeVisible()
  await page.locator('#password-borrado').fill(PASSWORD_E2E)
  await page.getByRole('button', { name: 'Borrar para siempre' }).click()

  // Fuera: a la portada, sin sesión.
  await expect(page).toHaveURL(/\/$/)

  // Las credenciales ya no existen: el login falla con el mensaje neutro.
  await page.goto('/login')
  await page.locator('#email').fill(email)
  await page.locator('#password').fill(PASSWORD_E2E)
  await page.getByRole('button', { name: 'Entrar' }).click()
  await expect(page.getByText('Email o contraseña incorrectos')).toBeVisible()
})

test('la contraseña incorrecta no borra nada y lo dice en el panel', async ({ page }) => {
  await registra(page, emailUnico())

  await page.getByRole('button', { name: 'Quiero borrar mi cuenta' }).click()
  await page.locator('#password-borrado').fill('estaNoEs1234')
  await page.getByRole('button', { name: 'Borrar para siempre' }).click()

  await expect(page.getByText('La contraseña no es correcta')).toBeVisible()
  // Sigue dentro, en su cuenta.
  await expect(page).toHaveURL(/\/cuenta$/)
})
