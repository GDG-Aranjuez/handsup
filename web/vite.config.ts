import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vitest/config';

// GitHub Pages serves the app under /handsup; local dev and tests use the root.
const base = (process.env.BASE_PATH ?? '') as '' | `/${string}`;

export default defineConfig({
  resolve: { alias: { '#lib': fileURLToPath(new URL('./src/lib', import.meta.url)) } },
  plugins: [
    tailwindcss(),
    sveltekit({
      // SPA fallback so deep links (/e/slug/t/CODE) work on Pages
      adapter: adapter({ fallback: '404.html' }),
      paths: { base, relative: false },
    }),
  ],
  test: { include: ['src/**/*.test.ts'] },
});
