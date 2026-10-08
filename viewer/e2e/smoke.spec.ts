import { expect, test } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';

// Smoke path: the viewer loads the CLI's 5x5x5 box, renders it, and we screenshot it to out/smoke.png.
test('viewer renders the 5x5x5 box with two role colours', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto('/');
  await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 30_000 });
  const ready = await page.evaluate(() => window.__viewerReady);
  expect(ready).toEqual({ ok: true, voxels: 125 });

  const png = await page.screenshot();
  mkdirSync('out', { recursive: true });
  writeFileSync('out/smoke.png', png);

  // Count pixels by class inside the browser (no image-decoding dependency).
  const stats = await page.evaluate(async (b64) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width;
    c.height = img.height;
    const g = c.getContext('2d')!;
    g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    let total = 0, nonBg = 0, reddish = 0, beige = 0;
    for (let i = 0; i < d.length; i += 4) {
      const r = d[i]!, gg = d[i + 1]!, b = d[i + 2]!;
      total++;
      if (Math.abs(r - 0x1b) + Math.abs(gg - 0x1d) + Math.abs(b - 0x22) < 24) continue; // background
      if (Math.abs(r - gg) < 12 && Math.abs(gg - b) < 12) continue; // grey grid lines
      nonBg++;
      if (r > gg * 1.3) reddish++;
      else if (r > b * 1.2 && r >= gg) beige++;
    }
    return { total, nonBg, reddish, beige };
  }, png.toString('base64'));
  console.log(JSON.stringify(stats));

  expect(stats.nonBg / stats.total).toBeGreaterThan(0.03);
  expect(stats.reddish / stats.total).toBeGreaterThan(0.005); // roof
  expect(stats.beige / stats.total).toBeGreaterThan(0.005); // walls
  expect(errors).toEqual([]);
});
