import { defineConfig } from '@playwright/test';

// `npm run screenshot` builds the viewer, serves it with `vite preview` and runs e2e/*.spec.ts.
// Browsers come from the Nix dev shell (PLAYWRIGHT_BROWSERS_PATH); WebGL is software (SwiftShader).
// ANGLE/SwiftShader tries to open an X display if DISPLAY is set (even a dead one) and then gets no WebGL,
// so the browser is launched without DISPLAY.
const { DISPLAY: _display, ...browserEnv } = process.env;

export default defineConfig({
  testDir: 'e2e',
  outputDir: 'out/test-results',
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:4173',
    viewport: { width: 960, height: 640 },
    launchOptions: {
      env: browserEnv as Record<string, string>,
      args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
    },
  },
  webServer: {
    command: 'npx vite preview --host 127.0.0.1 --port 4173 --strictPort',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: false,
  },
});
