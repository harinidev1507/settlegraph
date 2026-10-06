import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Vitest reads this block; `vite build` ignores it.
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
  },
})
