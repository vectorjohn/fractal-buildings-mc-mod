import { expect, test } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';

async function redFraction(page: import('@playwright/test').Page, png: Buffer): Promise<number> {
  return page.evaluate(async (b64) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const g = c.getContext('2d')!;
    g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    let red = 0;
    for (let i = 0; i < d.length; i += 4) if (d[i]! > 180 && d[i + 1]! < 90 && d[i + 2]! < 90) red++;
    return red / (d.length / 4);
  }, png.toString('base64'));
}

async function shot(page: import('@playwright/test').Page, fixture: string) {
  await page.goto(`/?fixture=${fixture}`);
  await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 30_000 });
  expect((await page.evaluate(() => window.__viewerReady))!.ok).toBe(true);
  return page.screenshot();
}

test('violations fixture shows red cells and a panel; clean house shows none', async ({ page }) => {
  const bad = await shot(page, 'sample-house-violations');
  mkdirSync('out', { recursive: true });
  writeFileSync('out/overlay.png', bad);
  const ids = await page.locator('#violation-panel .violation-row').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset['invariant']));
  expect(ids).toEqual(['roof-covers', 'roof-closed', 'room-graph', 'rooms-reachable']);
  const f = await redFraction(page, bad);
  console.log('red fraction', f);
  expect(f).toBeGreaterThan(0.002);

  const clean = await shot(page, 'sample-house');
  expect(await page.locator('#violation-panel').count()).toBe(0);
  expect(await redFraction(page, clean)).toBeLessThan(0.0001);
});
