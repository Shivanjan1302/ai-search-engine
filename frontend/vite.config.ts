import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const backendUrl = env.VITE_API_BASE_URL || 'http://localhost:8080';
  return {
    plugins: [react()],
    server: {
      proxy: {
        '/auth': backendUrl,
        '/oauth2': backendUrl,
        '/login/oauth2': backendUrl,
        '/documents': backendUrl,
        '/search': backendUrl,
        '/rag': backendUrl,
        '/notes': backendUrl,
      },
    },
  };
});
