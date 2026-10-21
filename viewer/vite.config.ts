import { defineConfig } from 'vitest/config';

// FB_GALLERY=1 (set by `npm run gallery` / `npm run dev:gen`): serve and build with the CLI batch output in
// gallery-data/gen/ as public files and a separate dist-gallery/, so the ordinary build and the other e2e specs
// never see generated seeds (the viewer's default source is gen/<style>/ and would pick them up).
const gallery = process.env['FB_GALLERY'] === '1';

export default defineConfig({
  publicDir: gallery ? 'gallery-data' : false,
  build: {
    outDir: gallery ? 'dist-gallery' : 'dist',
    rollupOptions: { input: { main: 'index.html', gallery: 'gallery.html', region: 'region.html' } },
  },
  server: { fs: { allow: ['..'] } }, // the style files under ../planner-core/src/main/resources/styles are bundled (styles.ts)
  test: { environment: 'node', include: ['src/**/*.test.ts'] },
});
