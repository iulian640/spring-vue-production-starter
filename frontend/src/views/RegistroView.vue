<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { PASSWORD_MAX, PASSWORD_MIN } from '../services/auth'
import { destinoTrasLogin } from '../lib/navegacion'
import { esEmailValido } from '../lib/validacion'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const email = ref('')
const password = ref('')
const repite = ref('')
const errorCliente = ref<string | null>(null)

/** Mismos límites que el backend (RegistroRequest): así el error sale al teclear, no tras el viaje. */
function validar(): string | null {
  if (!email.value.trim() || !password.value || !repite.value) {
    return 'Rellena todos los campos.'
  }
  if (!esEmailValido(email.value.trim())) {
    return 'Ese email no tiene pinta de email. Revísalo.'
  }
  if (password.value.length < PASSWORD_MIN) {
    return `La contraseña necesita al menos ${PASSWORD_MIN} caracteres. Truco: una frase corta vale.`
  }
  if (password.value.length > PASSWORD_MAX) {
    return `La contraseña no puede pasar de ${PASSWORD_MAX} caracteres.`
  }
  if (password.value !== repite.value) {
    return 'Las contraseñas no coinciden.'
  }
  return null
}

async function crearCuenta() {
  errorCliente.value = validar()
  if (errorCliente.value) {
    return
  }
  const ok = await auth.registrarse(email.value.trim(), password.value)
  if (ok) {
    router.push(destinoTrasLogin(route.query.redirect))
  }
}

/*
 * Igual criterio que en LoginView: el error puede venir de cualquiera de
 * los tres campos y el mensaje es uno solo, así que se marca el grupo
 * entero en vez de señalar un campo concreto sin estar seguros.
 */
const hayErrorCampo = computed(() => errorCliente.value !== null || auth.error !== null)
</script>

<template>
  <main class="auth">
    <h1>Crea tu cuenta</h1>
    <p class="texto-suave">
      Solo pedimos un email y una contraseña. Nada más: ni nombre, ni teléfono, ni empresa.
    </p>

    <form
      novalidate
      @submit.prevent="crearCuenta"
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
          autocomplete="new-password"
          :minlength="PASSWORD_MIN"
          required
          :aria-invalid="hayErrorCampo || undefined"
          :aria-describedby="hayErrorCampo ? 'error-formulario' : undefined"
        >
        <p class="campo-ayuda">
          Mínimo {{ PASSWORD_MIN }} caracteres. Una frase que recuerdes vale de sobra.
        </p>
      </div>

      <div
        class="campo"
        :class="{ 'campo--error': hayErrorCampo }"
      >
        <label for="repite">Repite la contraseña</label>
        <input
          id="repite"
          v-model="repite"
          type="password"
          autocomplete="new-password"
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
        {{ auth.cargando ? 'Creando cuenta...' : 'Crear cuenta' }}
      </button>
    </form>

    <p class="texto-sm texto-suave">
      ¿Ya tienes cuenta?
      <RouterLink :to="{ name: 'login', query: route.query }">
        Entra
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
