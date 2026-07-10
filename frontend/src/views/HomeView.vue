<script setup lang="ts">
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
</script>

<template>
  <main class="portada">
    <h1>Starter</h1>
    <p class="texto-suave">
      La base de producción ya está hecha: cuentas con sesiones revocables,
      borrado RGPD, rate limiting y errores RFC 7807. Tu app empieza aquí.
    </p>

    <nav class="acciones">
      <RouterLink
        v-if="auth.autenticado"
        class="boton boton--ancho"
        to="/cuenta"
      >
        Tu cuenta
      </RouterLink>
      <template v-else>
        <RouterLink
          class="boton boton--ancho"
          to="/registro"
        >
          Crear cuenta
        </RouterLink>
        <RouterLink
          class="boton-secundario boton--ancho"
          to="/login"
        >
          Entrar
        </RouterLink>
      </template>
    </nav>
  </main>
</template>

<style scoped>
.portada {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-2xl) var(--esp-md);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

.acciones {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}
</style>
