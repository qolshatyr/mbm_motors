import { defineConfig, mergeConfig } from 'vite';
import base from './vite.config';

// Single-file demo for a claude.ai artifact: one script, one stylesheet, fonts inlined.
export default mergeConfig(base, defineConfig({
  base: './',
  build: {
    outDir: 'dist-artifact',
    emptyOutDir: true,
    cssCodeSplit: false,
    assetsInlineLimit: 10_000_000,
    rollupOptions: { output: { inlineDynamicImports: true } },
  },
}));
