import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import Inspector from 'vite-plugin-vue-inspector';

export default defineConfig({

  plugins: [vue(), Inspector({ launchEditor: 'idea' })],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  resolve: {
    alias: {
      '@': '/src',
    },
  },
});
