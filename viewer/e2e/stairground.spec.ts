import { expect, test } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

// STAIRGROUND before/after pictures (docs/review/phase5/stairground/). Skipped unless STAIRGROUND=1.
// FB_SG_CASES = a JSON file: [{ name, scene (path to a district scene), x, y, z (the door's floor cell, world) }]. Per case it writes
// out/stairground/<name>.png: two close views of the door from opposite quarters (the building is in the way of one of them).
const OUT = resolve('out/stairground');
test.use({ viewport: { width: 1000, height: 640 } });
test.skip(process.env['STAIRGROUND'] !== '1', 'stairground captures run only with STAIRGROUND=1');

interface Case { name: string; scene: string; x: number; y: number; z: number; ox: number; oz: number }
const cases: Case[] = process.env['FB_SG_CASES'] ? JSON.parse(readFileSync(process.env['FB_SG_CASES'], 'utf8')) as Case[] : [];

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
    const body = readFileSync(c.scene, 'utf8');
    await page.route((u) => u.pathname === '/data/sg.json', (r) => r.fulfill({ body, contentType: 'application/json' }));
    const lx = -c.oz, lz = c.ox; // along the facade
    const at = (o: number, l: number, h: number): string => look(c.x + c.ox * o + lx * l, c.y + h, c.z + c.oz * o + lz * l, c.x + c.ox * 2, c.y - 2, c.z + c.oz * 2);
    const stops = [at(13, 0, 8), at(10, 7, 6), at(10, -7, 6), at(4, 12, 4)];
    await page.goto(`/index.html?ir=/data/sg.json&camera=fly&exterior=1&colour=palette&stops=${stops.join(';')}`);
    await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 300_000 });
    const ready = await page.evaluate(() => window.__viewerReady!);
    expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
    await page.addStyleTag({ content: 'body > :not(canvas) { display: none !important; }' });
    mkdirSync(OUT, { recursive: true });
    for (let i = 0; i < stops.length; i++) {
      if (i > 0) { await page.keyboard.press(']'); await page.waitForTimeout(1200); }
      writeFileSync(join(OUT, `${c.name}-${i + 1}.png`), await page.screenshot());
    }
  });
}
