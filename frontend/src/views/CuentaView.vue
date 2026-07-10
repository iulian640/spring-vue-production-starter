<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()

function salir() {
  auth.cerrarSesion()
  router.push('/')
}

/* Borrado de cuenta (RGPD art. 17), en dos pasos: primero el aviso con el
 * botón de abrir, y solo entonces el panel con la advertencia final y la
 * contraseña. Es irreversible: que no pase por un toque de más. */
const borradoAbierto = ref(false)
const passwordBorrado = ref('')
const inputPasswordBorrado = ref<HTMLInputElement | null>(null)
const botonAbrirBorrado = ref<HTMLButtonElement | null>(null)

/* El foco sigue al panel: el botón que lo abre se desmonta del DOM y, sin
 * traslado explícito, el foco cae a <body> y un usuario de teclado o lector
 * de pantalla se queda flotando al principio de la página. */
async function abrirBorrado() {
  auth.limpiarErrorBorrado()
  borradoAbierto.value = true
  await nextTick()
  inputPasswordBorrado.value?.focus()
}

async function cancelarBorrado() {
  auth.limpiarErrorBorrado()
  borradoAbierto.value = false
  passwordBorrado.value = ''
  await nextTick()
  botonAbrirBorrado.value?.focus()
}

async function confirmarBorrado() {
  if (passwordBorrado.value === '' || auth.borrando) {
    return
  }
  const ok = await auth.borrarCuenta(passwordBorrado.value)
  if (ok) {
    router.push('/')
  }
}
</script>

<template>
  <main class="cuenta">
    <header class="cabecera">
      <div>
        <h1>Tu cuenta</h1>
        <p class="email texto-suave texto-sm">
          {{ auth.email }}
        </p>
      </div>
      <button
        type="button"
        class="salir boton-secundario"
        @click="salir"
      >
        Cerrar sesión
      </button>
    </header>

    <section class="tarjeta seccion-borrado">
      <h2 class="titulo-seccion">
        Borrar tu cuenta
      </h2>
      <p class="texto-sm texto-suave">
        Borra tu cuenta y todos tus datos del servidor. No hay copia que
        recuperar después.
      </p>

      <button
        v-if="!borradoAbierto"
        ref="botonAbrirBorrado"
        type="button"
        class="boton-secundario boton--ancho boton-abrir-borrado"
        @click="abrirBorrado"
      >
        Quiero borrar mi cuenta
      </button>

      <form
        v-else
        class="form-borrado"
        novalidate
        @submit.prevent="confirmarBorrado"
      >
        <!-- role=alert: la frase más importante del flujo también tiene que
             sonar en un lector de pantalla, no solo verse. -->
        <p
          class="aviso-bloque"
          role="alert"
        >
          <strong>No hay vuelta atrás:</strong> se borra todo, ahora mismo y
          para siempre. Escribe tu contraseña para confirmar que eres tú.
        </p>
        <div class="campo">
          <label for="password-borrado">Tu contraseña</label>
          <input
            id="password-borrado"
            ref="inputPasswordBorrado"
            v-model="passwordBorrado"
            type="password"
            autocomplete="current-password"
          >
        </div>
        <p
          v-if="auth.errorBorrado"
          class="aviso-bloque"
          role="alert"
        >
          {{ auth.errorBorrado }}
        </p>
        <div class="acciones-borrado">
          <button
            type="button"
            class="boton-secundario boton-cancelar-borrado"
            @click="cancelarBorrado"
          >
            Cancelar
          </button>
          <button
            type="submit"
            class="boton-borrar"
            :disabled="passwordBorrado === '' || auth.borrando"
          >
            {{ auth.borrando ? 'Borrando...' : 'Borrar para siempre' }}
          </button>
        </div>
      </form>
    </section>
  </main>
</template>

<style scoped>
.cuenta {
  max-width: 30rem;
  margin: 0 auto;
  padding: var(--esp-lg) var(--esp-md) var(--esp-2xl);
  display: flex;
  flex-direction: column;
  gap: var(--esp-md);
}

.cabecera {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--esp-md);
}

h1 {
  font-size: var(--tipo-titulo);
}

.email {
  overflow-wrap: anywhere;
}

.salir {
  white-space: nowrap;
}

.seccion-borrado,
.form-borrado {
  display: flex;
  flex-direction: column;
  gap: var(--esp-sm);
}

.acciones-borrado {
  display: flex;
  gap: var(--esp-sm);
}

.acciones-borrado > * {
  flex: 1;
}

/* El único botón rojo de la app: lo destructivo no se viste del color de
 * siempre. Mismo esqueleto que .boton, en --alerta con su --sobre-alerta. */
.boton-borrar {
  border: 1px solid var(--alerta);
  border-radius: var(--radio-control, 8px);
  background: var(--alerta);
  color: var(--sobre-alerta);
  padding: var(--esp-sm) var(--esp-md);
  font: inherit;
  font-weight: var(--peso-etiqueta);
  cursor: pointer;
}

.boton-borrar:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
</style>
