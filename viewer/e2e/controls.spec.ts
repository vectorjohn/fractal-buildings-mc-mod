import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';

const house = readFileSync('fixtures/sample-house.json', 'utf8');

async function ready(page: Page, loads?: number): Promise<void> {
  await page.waitForFunction((n) => window.__viewerReady !== undefined && window.__viewerReady.ok && (n === undefined || window.__viewerReady.loads >= n), loads, { timeout: 30_000 });
}

/** Screenshot to out/<name>.png and the fraction of pixels in some colour classes (counted in the browser). */
async function shot(page: Page, name: string) {
  const png = await page.screenshot();
  mkdirSync('out', { recursive: true });
  writeFileSync(`out/${name}.png`, png);
  return page.evaluate(async (b64) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const g = c.getContext('2d')!;
    g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    let total = 0, reddish = 0, beige = 0;
    for (let i = 0; i < d.length; i += 4) {
      const r = d[i]!, gg = d[i + 1]!, b = d[i + 2]!;
      total++;
      if (Math.abs(r - 0x1b) + Math.abs(gg - 0x1d) + Math.abs(b - 0x22) < 24) continue;
      if (Math.abs(r - gg) < 12 && Math.abs(gg - b) < 12) continue;
      if (r > gg * 1.3) reddish++;
      else if (r > b * 1.2 && r >= gg) beige++;
    }
    return { reddish: reddish / total, beige: beige / total };
  }, png.toString('base64'));
}

test('layers in the URL and checkboxes hide groups without reloading', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  await page.goto('/?fixture=sample-house&layers=shell,foundation');
  await ready(page);
  const shellOnly = await shot(page, 'controls-shell-foundation');
  expect(shellOnly.reddish).toBeLessThan(0.004); // no roof (the orange door still counts as reddish)

  await page.goto('/?fixture=sample-house');
  await ready(page);
  const all = await shot(page, 'controls-all');
  expect(all.reddish).toBeGreaterThan(0.005);
  expect(all.reddish).toBeGreaterThan(shellOnly.reddish * 2);
  expect(all.reddish).toBeGreaterThan(shellOnly.reddish * 2);

  await page.locator('#layer-roof').uncheck();
  await expect(page).toHaveURL(/layers=terrain,foundation,shell,interior,violations/);
  await page.waitForTimeout(300);
  const noRoof = await shot(page, 'controls-roof-off');
  expect(noRoof.reddish).toBeLessThan(0.004);
  expect(noRoof.reddish).toBeLessThan(all.reddish / 2);
  expect((await page.evaluate(() => window.__viewerReady))!.loads).toBe(1); // no rebuild

  await page.locator('#layer-roof').check();
  await expect(page).not.toHaveURL(/layers=/);
  expect(errors).toEqual([]);
});

test('storeys slider hides the upper storeys', async ({ page }) => {
  // The fixture has one storey; serve a copy whose roof is tagged storey 1 so there is something to hide.
  const scene = JSON.parse(house);
  for (const b of scene.buildings) for (const box of b.shell.boxes) if (box.layer === 'roof') box.storey = 1;
  await page.route('**/gen/**', (route) => route.request().url().endsWith('/index.json')
    ? route.fulfill({ json: { seeds: [1] } })
    : route.fulfill({ json: scene }));
  await page.goto('/');
  await ready(page);
  const full = await shot(page, 'controls-storey-all');
  await page.locator('#storeys').fill('0');
  await expect(page).toHaveURL(/storeys=0/);
  await page.waitForTimeout(300);
  const s = await shot(page, 'controls-storey-0');
  expect(s.reddish).toBeLessThan(full.reddish / 2); // roof sits above storey 0
  await page.locator('#storeys').fill('1');
  await expect(page).not.toHaveURL(/storeys=/);
});

test('seed keys, scrubber and style switch drive a batch and the URL', async ({ page }) => {
  const seen: string[] = [];
  await page.route('**/gen/**', async (route) => {
    const url = new URL(route.request().url());
    seen.push(url.pathname);
    if (url.pathname.endsWith('/index.json')) return route.fulfill({ json: { seeds: [3, 5, 8] } });
    return route.fulfill({ contentType: 'application/json', body: house });
  });
  await page.goto('/');
  await ready(page);
  expect(await page.evaluate(() => window.__viewerReady!.seed)).toBe(3);
  await page.keyboard.press('ArrowRight');
  await expect(page).toHaveURL(/seed=5/);
  await ready(page, 2);
  await page.keyboard.press('ArrowRight');
  await expect(page).toHaveURL(/seed=8/);
  await page.keyboard.press('ArrowLeft');
  await expect(page).toHaveURL(/seed=5/);
  await page.locator('#seed-slider').fill('0');
  await expect(page).toHaveURL(/seed=3/);
  // let the seed-3 load settle before typing (its UI sync would overwrite the box); intermediate loads may be superseded, so wait on the seed shown
  await page.waitForFunction(() => window.__viewerReady?.ok && window.__viewerReady.seed === 3);
  await page.locator('#seed').fill('8');
  await page.locator('#seed').press('Enter');
  await expect(page).toHaveURL(/seed=8/);
  expect(seen).toContain('/gen/plain/seed-8.json');

  await page.waitForFunction(() => window.__viewerReady?.ok && window.__viewerReady.seed === 8);
  const before = await page.evaluate(() => window.__viewerReady!.loads);
  await page.locator('#style').selectOption('medieval');
  await expect(page).toHaveURL(/style=medieval/);
  await page.waitForFunction((n) => window.__viewerReady!.loads > n, before);
  expect(seen).toContain('/gen/medieval/index.json');
  await page.locator('#colour').selectOption('palette');
  await expect(page).toHaveURL(/colour=palette/);
  await page.waitForTimeout(300);
  await shot(page, 'controls-medieval-palette');
});

test('state in the URL is restored on load', async ({ page }) => {
  await page.goto('/?fixture=sample-house&style=medieval&colour=palette&layers=shell&storeys=0');
  await ready(page);
  expect(await page.locator('#style').inputValue()).toBe('medieval');
  expect(await page.locator('#colour').inputValue()).toBe('palette');
  expect(await page.locator('#layer-shell').isChecked()).toBe(true);
  expect(await page.locator('#layer-roof').isChecked()).toBe(false);
  expect(await page.locator('#storeys').inputValue()).toBe('0');
});

test('scrubbing between a violating and a clean scene remounts the violation panel and red overlay', async ({ page }) => {
  // The bundled fixture batch is [sample-house, sample-house-violations].
  const redPixels = async (): Promise<number> => {
    const png = await page.screenshot();
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
  };
  await page.goto('/');
  await ready(page);
  expect(await page.locator('#violation-panel').count()).toBe(0);
  expect(await redPixels()).toBeLessThan(0.0001);

  await page.keyboard.press('ArrowRight');
  await expect(page).toHaveURL(/seed=1/);
  await ready(page, 2);
  await expect(page.locator('#violation-panel .violation-row')).toHaveCount(4);
  await page.waitForTimeout(300);
  const bad = await redPixels();
  await shot(page, 'controls-violations-scrubbed');
  expect(bad).toBeGreaterThan(0.002);

  await page.keyboard.press('ArrowLeft');
  await expect(page).toHaveURL(/seed=0/);
  await ready(page, 3);
  await expect(page.locator('#violation-panel')).toHaveCount(0);
  await page.waitForTimeout(300);
  expect(await redPixels()).toBeLessThan(0.0001);

  await page.keyboard.press('ArrowRight');
  await ready(page, 4);
  await expect(page.locator('#violation-panel .violation-row')).toHaveCount(4);
});
