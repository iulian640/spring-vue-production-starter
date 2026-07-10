import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        // La fuente de la casa (woff2) también va precacheada: la app debe
        // verse igual sin red. El resto es el glob por defecto del plugin.
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
      },
      manifest: {
        name: 'Starter',
        short_name: 'Starter',
        description:
          'Base de producción para apps Spring Boot + Vue: cuentas, sesiones revocables y RGPD ya resueltos.',
        lang: 'es',
        // La app arranca en claro; el tema oscuro lo pone el sistema en
        // runtime (el manifest solo admite un color).
        theme_color: '#ffffff',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      // Dev only: forward API calls to the local Spring Boot backend.
      // APP_API permite apuntar a otro puerto (p. ej. un backend paralelo
      // durante los E2E) sin tocar este fichero.
      '/api': {
        target: process.env.APP_API ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
