import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The UI calls the API directly (CORS, withCredentials). localhost:5173 and localhost:8080 are the
// same site, so the SameSite=Strict refresh and CSRF cookies work without a dev proxy.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 4173,
    strictPort: true,
  },
  build: {
    sourcemap: false,
    // AG Grid's core (~750 kB min, ~215 kB gzip) is the largest chunk. It sits in the lazy grid
    // pages only, so the app shell does not load it.
    chunkSizeWarningLimit: 800,
  },
});
