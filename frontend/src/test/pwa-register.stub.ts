/**
 * Stub del módulo virtual `virtual:pwa-register` (vite-plugin-pwa) para el
 * entorno de test: vitest.config.ts lo mapea vía alias porque el plugin PWA
 * solo está activo en vite.config.ts. Los tests lo sustituyen con vi.mock.
 */
export function registerSW(): (reloadPage?: boolean) => Promise<void> {
  return () => Promise.resolve()
}
