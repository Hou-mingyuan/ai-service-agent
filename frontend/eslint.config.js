import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import tseslint from 'typescript-eslint'

export default [
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'src/env.d.ts'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: { parser: tseslint.parser, extraFileExtensions: ['.vue'] }
    }
  },
  {
    files: ['src/**/*.{ts,vue}', 'vite.config.ts', 'vitest.config.ts'],
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/attributes-order': 'off',
      'no-undef': 'off',
      '@typescript-eslint/no-explicit-any': 'error'
    }
  },
  {
    files: ['src/**/*.spec.ts', 'src/test/**/*.ts'],
    languageOptions: { globals: { beforeEach: 'readonly', afterEach: 'readonly', describe: 'readonly', expect: 'readonly', it: 'readonly', vi: 'readonly' } }
  }
]
