import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'

const sourceFiles = ['**/*.{js,jsx}']

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  { ...js.configs.recommended, files: sourceFiles },
  { ...reactHooks.configs.flat.recommended, files: sourceFiles },
  { ...reactRefresh.configs.vite, files: sourceFiles },
  {
    files: sourceFiles,
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.node },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^[A-Z_]' }],
      // Existing dashboard effects intentionally coordinate async simulator state.
      // Keep the stricter React 19 recommendation opt-in to avoid changing that flow here.
      'react-hooks/set-state-in-effect': 'off',
    },
  },
]
