import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// @rto/shared is consumed as TypeScript source rather than a built artifact. It removes a
// build step from the dev loop and means a change to a shared type is reflected in all
// three apps immediately, with no watch-and-rebuild in between.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@rto/shared': fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url)),
    },
  },
  server: { port: 5173, strictPort: true },
});
