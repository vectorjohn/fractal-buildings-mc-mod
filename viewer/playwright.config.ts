import { execFileSync } from 'node:child_process';
import { defineConfig } from '@playwright/test';
import { timeoutFactor } from './e2e/load';

// `npm run screenshot` builds the viewer, serves it with `vite preview` and runs e2e/*.spec.ts.
// Browsers come from the Nix dev shell (PLAYWRIGHT_BROWSERS_PATH); WebGL is software (SwiftShader).
// ANGLE/SwiftShader tries to open an X display if DISPLAY is set (even a dead one) and then gets no WebGL,
// so the browser is launched without DISPLAY.
// Port: FB_PORT if set; otherwise a free port picked by the OS, so parallel runs (other worktrees, other
// agents, two test-all.sh at once) never collide. The pick is written back to FB_PORT so the worker
// processes, which re-read this config, use the same port.
function freePort(): number {
  const js = "const s=require('net').createServer();s.listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})";
  return Number(execFileSync(process.execPath, ['-e', js], { encoding: 'utf8' }).trim());
}
const port = Number(process.env['FB_PORT'] || freePort());
process.env['FB_PORT'] = String(port);
const { DISPLAY: _display, ...browserEnv } = process.env;

export default defineConfig({
  testDir: 'e2e',
  // Playwright's default 30 s per test (it covers fixture setup too), stretched up to 4x when the host is
  // loaded (e2e/load.ts); explicit test.setTimeout calls override it.
  timeout: Math.round(30_000 * timeoutFactor()),
  outputDir: 'out/test-results',
  reporter: 'list',
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    viewport: { width: 960, height: 640 },
    launchOptions: {
      env: browserEnv as Record<string, string>,
      args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
    },
  },
  webServer: {
    command: `npx vite preview --host 127.0.0.1 --port ${port} --strictPort`,
    url: `http://127.0.0.1:${port}`,
    reuseExistingServer: false,
  },
});
