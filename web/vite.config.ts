import { configDefaults, defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig(() => {
  // Public site origin used by index.html for og:url, canonical and social images.
  // Vite substitutes %VITE_SITE_URL% into HTML; override it at build time with the
  // final domain (no trailing slash) when it differs from this placeholder-free default.
  process.env.VITE_SITE_URL ||= 'https://alaa157.github.io/caregiver-daily-notes';
  return {
    plugins: [react()],
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/tests/setup.ts'],
      css: true,
      restoreMocks: true,
      exclude: [...configDefaults.exclude, 'e2e/**'],
    },
  };
});
