import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const backend = process.env.MBM_BACKEND_URL ?? 'http://127.0.0.1:8080';

// Oldest browsers the portal must work in: Chrome 109 is the last one for Windows 7/8,
// Safari 15.4 covers iPhones that stopped at iOS 15, Firefox 115 is the last ESR for Windows 7.
// Syntax and CSS are lowered to these; newer browser APIs are guarded by tests/test_frontend_compat.py.
const browsers = ['chrome109', 'edge109', 'firefox115', 'safari15.4', 'ios15.4'];

export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { target: browsers, cssTarget: browsers },
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
