import { fileURLToPath } from 'node:url'
import { coverageConfigDefaults, defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // El plugin PWA solo vive en vite.config.ts; en tests el módulo virtual
      // se resuelve a un stub para poder cubrir src/pwa.ts.
      'virtual:pwa-register': fileURLToPath(
        new URL('./src/test/pwa-register.stub.ts', import.meta.url),
      ),
    },
  },
  test: {
    // Los E2E de Playwright viven en e2e/ y corren con `npm run test:e2e`,
    // no con vitest (su runner es incompatible y necesitan el stack real).
    exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
    environment: 'jsdom',
    globals: true,
    // Cobertura solo del código fuente, con gate del 80% en las CUATRO
    // métricas (hoy: líneas 91,2%, statements 90,9%, branches 89,7%,
    // functions 86,1%).
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{ts,vue}'],
      exclude: [...coverageConfigDefaults.exclude, 'src/test/**'],
      reporter: ['text', 'html', 'lcov'],
      thresholds: {
        lines: 80,
        statements: 80,
        branches: 80,
        functions: 80,
      },
    },
  },
})
