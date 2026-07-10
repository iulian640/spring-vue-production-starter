<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { destinoTrasLogin } from '../lib/navegacion'
import { esEmailValido } from '../lib/validacion'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const errorCliente = ref<string | null>(null)

async function entrar() {
  errorCliente.value = null
  if (!email.value.trim() || !password.value) {
    errorCliente.value = 'Escribe tu email y tu contraseña.'
    return
  }
  if (!esEmailValido(email.value.trim())) {
    errorCliente.value = 'Ese email no tiene pinta de email. Revísalo.'
    return
  }
  const ok = await auth.iniciarSesion(email.value.trim(), password.value)
  if (ok) {
    router.push(destinoTrasLogin(route.query.redirect))
  }
}

/*
 * No sabemos con certeza si el fallo es del email o de la contraseña (el
 * mensaje es uno solo, tanto en cliente como en el 401 del backend): se
 * marca el grupo entero en vez de arriesgarse a señalar el campo que sí
 * estaba bien — un dato erróneo es peor que uno ausente (PRODUCT.md).
 */
const hayErrorCampo = computed(() => errorCliente.value !== null || auth.error !== null)
</script>

<template>
  <main class="auth">
    <h1>Entra en tu cuenta</h1>
    <p class="texto-suave">
      Con cuenta, tu perfil laboral queda guardado y no tienes que repetirlo cada vez.
    </p>

    <p
      v-if="auth.aviso"
      class="aviso-bloque"
      role="status"
      aria-live="polite"
    >
      {{ auth.aviso }}
    </p>

    <form
      novalidate
      @submit.prevent="entrar"
    >
      <div
        class="campo"
        :class="{ 'campo--error': hayErrorCampo }"
      >
        <label for="email">Email</label>
        <!-- El estado de error también en aria: el borde rojo solo lo ve quien ve. -->
        <input
          id="email"
          v-model="email"
          type="email"
          autocomplete="email"
          required
          :aria-invalid="hayErrorCampo || undefined"
          :aria-describedby="hayErrorCampo ? 'error-formulario' : undefined"
        >
      </div>

      <div
        class="campo"
        :class="{ 'campo--error': hayErrorCampo }"
      >
        <label for="password">Contraseña</label>
        <input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
          :aria-invalid="hayErrorCampo || undefined"
          :aria-describedby="hayErrorCampo ? 'error-formulario' : undefined"
        >
      </div>

      <p
        v-if="errorCliente || auth.error"
        id="error-formulario"
        class="campo-error"
        role="alert"
      >
        {{ errorCliente ?? auth.error }}
      </p>

      <button
        type="submit"
        class="boton boton--ancho"
        :disabled="auth.cargando"
      >
        {{ auth.cargando ? 'Entrando...' : 'Entrar' }}
      </button>
    </form>

    <p class="texto-sm texto-suave">
      ¿No tienes cuenta?
      <RouterLink :to="{ name: 'registro', query: route.query }">
        Créala en un minuto
      </RouterLink>
    </p>
  </main>
</template>

<style scoped>
/* Layout de vista estándar (DESIGN.md): columna centrada, ritmo apretado
 * dentro de un grupo y generoso entre bloques. */
.auth {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}
</style>
