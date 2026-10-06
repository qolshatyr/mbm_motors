import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: Object.fromEntries(['/api', '/catalog', '/cart', '/images', '/css', '/webjars'].map((path) => [path, { target: process.env.MBM_BACKEND_URL ?? 'http://127.0.0.1:8080' }])),
  },
});
