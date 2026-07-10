import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vueTsConfig from '@vue/eslint-config-typescript'

export default [
  // android/ es el proyecto nativo generado por Capacitor (Gradle + assets copiados), no código nuestro
  { ignores: ['dist/**', 'dev-dist/**', 'node_modules/**', 'android/**'] },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  ...vueTsConfig(),
]
