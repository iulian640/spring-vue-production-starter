<script setup lang="ts">
</script>

<template>
  <div>
    <!-- Transición entre pantallas: un respiro corto (clases en style.css),
         que prefers-reduced-motion apaga junto al resto. La duración va
         explícita a propósito: con ella Vue cierra la transición por timer
         en vez de esperar un transitionend que, con el webview en segundo
         plano (rendering parado), puede no llegar — y con mode="out-in" eso
         dejaría la pantalla en blanco. Mismos valores que --dur-estado y
         --dur-vista-salida en style.css. -->
    <RouterView v-slot="{ Component }">
      <Transition
        name="vista"
        mode="out-in"
        :duration="{ enter: 200, leave: 140 }"
      >
        <component :is="Component" />
      </Transition>
    </RouterView>
  </div>
</template>

