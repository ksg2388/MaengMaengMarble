import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [
      { find: '@atom', replacement: '/src/atom' },
      { find: '@utils', replacement: '/src/utils' },
      { find: '@routers', replacement: '/src/routers' },
      { find: '@constants', replacement: '/src/constants' },
      { find: '@assets', replacement: '/src/assets' },
      { find: '@apis', replacement: '/src/apis' },
      { find: '@pages', replacement: '/src/components/pages' },
      { find: '@components', replacement: '/src/components' },
      { find: '@', replacement: '/src' },
    ],
  },
});