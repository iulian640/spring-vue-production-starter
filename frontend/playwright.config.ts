import { defineConfig, devices } from '@playwright/test'

/**
 * E2E contra el stack REAL: vite dev (proxy /api) + Spring Boot + Postgres.
 * En CI el backend corre en localhost:8080 con un Postgres de servicio; en
 * local vale el stack de desarrollo de siempre (o un backend paralelo con
 * SOFRITO_API, ver vite.config.ts).
 *
 * Solo Chromium a propósito: la app de producción ES un WebView Chromium
 * (Capacitor/Android); cross-browser será relevante si la web se abre a
 * escritorio.
 */
export default defineConfig({
  testDir: './e2e',
  // Los viajes tocan BD real: en serie, sin carreras entre cuentas.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  timeout: 45_000,
  use: {
    baseURL: 'http://localhost:4180',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    locale: 'es-ES',
    timezoneId: 'Europe/Madrid',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port 4180 --strictPort',
    url: 'http://localhost:4180',
    reuseExistingServer: !process.env.CI,
    timeout: 60_000,
  },
})
