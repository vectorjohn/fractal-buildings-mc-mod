import { expect, test } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

// SHOPLOW S1v: the dug-in shop fixture (fixtures/shoplow/cut-fixture.json). Three views, roof hidden so the room shows: from
// above (the room with no earth in it, the areaway ring open to its floor), from the street (the door at street level, the
// cut face rising behind) and from the east side. Writes out/shoplow-cut-1..3.png; the check is coarse: it loads and draws.
test.use({ viewport: { width: 1000, height: 640 } });

function look(x: number, y: number, z: number, tx: number, ty: number, tz: number): string {
  const dx = tx - x, dz = tz - z, dy = ty - y;
  const yaw = (Math.atan2(-dx, -dz) * 180) / Math.PI;
  const pitch = (Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  return [x, y, z, yaw, pitch].map((v) => Math.round(v * 10) / 10).join(',');
}

test('dug-in shop: no earth in the room, areaway open to its floor', async ({ page }) => {
  const body = readFileSync('fixtures/shoplow/cut-fixture.json', 'utf8');
  await page.route((u) => u.pathname === '/data/cut.json', (r) => r.fulfill({ body, contentType: 'application/json' }));
  const stops = [look(10, 98, 20, 10, 64, 10), look(3, 69, -6, 10, 64, 10), look(30, 72, 4, 10, 65, 10)];
  await page.goto(`/index.html?ir=/data/cut.json&camera=fly&colour=palette&layers=terrain,foundation,shell,interior,ground&stops=${stops.join(';')}`);
  await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 60_000 });
  const ready = await page.evaluate(() => window.__viewerReady!);
  expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
  await page.addStyleTag({ content: 'body > :not(canvas) { display: none !important; }' });
  mkdirSync('out', { recursive: true });
  for (let i = 0; i < stops.length; i++) {
    if (i > 0) { await page.keyboard.press(']'); await page.waitForTimeout(1200); }
    writeFileSync(`out/shoplow-cut-${i + 1}.png`, await page.screenshot());
  }
});
