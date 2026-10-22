import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const backend = process.env.MBM_BACKEND_URL ?? 'http://127.0.0.1:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    strictPort: true,
    proxy: {
      ...Object.fromEntries(['/admin/api', '/admin/logout', '/api'].map((path) => [path, { target: backend }])),
      // The sign-in page is a React route; only the form submission goes to Spring Security.
      '/admin/login': { target: backend, bypass: (request) => (request.method === 'GET' ? '/index.html' : undefined) },
    },
  },
});
