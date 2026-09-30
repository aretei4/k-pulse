import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  // The app is served under https://direco.co.in/kpulse/, not at the domain
  // root, so every emitted asset URL has to carry that prefix. Keep this in
  // step with the router basename in src/app/routes.tsx and the nginx location.
  base: '/kpulse/',
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, 'src') },
  },
  server: {
    port: 5173,
    proxy: {
      // Dev convenience: hit the Spring Boot API without CORS gymnastics.
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
});
