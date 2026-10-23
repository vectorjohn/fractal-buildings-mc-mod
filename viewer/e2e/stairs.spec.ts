import { expect, test } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

// STAIRS before/after pictures (docs/review/phase6/stairs/). Skipped unless STAIRS=1.
// FB_ST_CASES = a JSON file: [{ name, ir (path to a building IR), x, y, z (centre of a flight, world), storeys (show 0..N), dist? }].
// Per case it writes out/stairs/<name>-<k>.png: oblique close views from the four quarters, roof hidden, storeys above N cut away.
const OUT = resolve('out/stairs');
test.use({ viewport: { width: 1000, height: 640 } });
test.skip(process.env['STAIRS'] !== '1', 'stairs captures run only with STAIRS=1');

interface Case { name: string; ir: string; x: number; y: number; z: number; storeys: number; dist?: number }
const cases: Case[] = process.env['FB_ST_CASES'] ? JSON.parse(readFileSync(process.env['FB_ST_CASES'], 'utf8')) as Case[] : [];

const r1 = (v: number): number => Math.round(v * 10) / 10;
function look(x: number, y: number, z: number, tx: number, ty: number, tz: number): string {
  const dx = tx - x, dz = tz - z, dy = ty - y;
  const yaw = (Math.atan2(-dx, -dz) * 180) / Math.PI;
  const pitch = (Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  return [x, y, z, yaw, pitch].map(r1).join(',');
}

for (const c of cases) {
  test(c.name, async ({ page }) => {
    test.setTimeout(300_000);
    const body = readFileSync(c.ir, 'utf8');
    await page.route((u) => u.pathname === '/data/st.json', (r) => r.fulfill({ body, contentType: 'application/json' }));
    const d = c.dist ?? 6;
    const stops = [[d, d], [-d, d], [-d, -d], [d, -d]].map(([ox, oz]) => look(c.x + ox! * 0.5, c.y + d * 1.3, c.z + oz! * 0.5, c.x, c.y - 1, c.z));
    await page.goto(`/index.html?ir=/data/st.json&camera=fly&storeys=${c.storeys}&layers=terrain,foundation,shell,interior&colour=palette&stops=${stops.join(';')}`);
    await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 300_000 });
    const ready = await page.evaluate(() => window.__viewerReady!);
    expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
    await page.addStyleTag({ content: 'body > :not(canvas) { display: none !important; }' });
    mkdirSync(OUT, { recursive: true });
    for (let i = 0; i < stops.length; i++) {
      if (i > 0) { await page.keyboard.press(']'); await page.waitForTimeout(1000); }
      writeFileSync(join(OUT, `${c.name}-${i + 1}.png`), await page.screenshot());
    }
  });
}
