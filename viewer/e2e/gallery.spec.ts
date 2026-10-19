import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { parsePreset } from '../src/galleryPresets';

// Run by `npm run gallery` (GALLERY=1, FB_GALLERY=1 build with the data `npm run gen` wrote). Skipped in the plain `screenshot` run.
// One page per gallery-presets/<name>.json x camera; FB_PRESETS=a,b limits it to those presets.
test.skip(process.env['GALLERY'] !== '1', 'gallery only: npm run gallery');
test.use({ viewport: { width: 1280, height: 1280 } });

const only = (process.env['FB_PRESETS'] ?? '').split(',').filter((s) => s !== '');
const presets = readdirSync('gallery-presets').filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort()
  .filter((n) => only.length === 0 || only.includes(n))
  .map((n) => parsePreset(n, JSON.parse(readFileSync(`gallery-presets/${n}.json`, 'utf8'))));

/** Non-background pixel fraction of each of the first `n` cells (4 columns) of a gallery screenshot. */
async function cellFractions(page: Page, png: Buffer, n: number): Promise<number[]> {
  return page.evaluate(async ({ b64, n }) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const g = c.getContext('2d')!;
    g.drawImage(img, 0, 0);
    const cell = img.width / 4;
    const out: number[] = [];
    for (let k = 0; k < n; k++) {
      const d = g.getImageData((k % 4) * cell + 4, Math.floor(k / 4) * cell + 24, cell - 8, cell - 28).data;
      let non = 0;
      for (let i = 0; i < d.length; i += 4) {
        if (Math.abs(d[i]! - 0x1b) + Math.abs(d[i + 1]! - 0x1d) + Math.abs(d[i + 2]! - 0x22) > 12) non++;
      }
      out.push(non / (d.length / 4));
    }
    return out;
  }, { b64: png.toString('base64'), n });
}

for (const p of presets) for (const camera of p.cameras) {
  test(`gallery ${p.name} ${camera}`, async ({ page }) => {
    test.setTimeout(180_000);
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    const index = JSON.parse(readFileSync(`gallery-data/gen/${p.name}/index.json`, 'utf8')) as { seeds: number[] };
    const expected = Math.min(16, index.seeds.length);
    await page.goto(`/gallery.html?dir=${p.name}&camera=${camera}&cut=${p.cutStorey}&from=0`);
    await page.waitForFunction(() => window.__galleryReady !== undefined, undefined, { timeout: 150_000 });
    const ready = await page.evaluate(() => window.__galleryReady!);
    expect(ready.error).toBeUndefined();
    expect(ready.cells).toHaveLength(expected);
    expect(ready.violations).toBe(0);

    const png = await page.screenshot();
    mkdirSync('out/gallery', { recursive: true });
    writeFileSync(`out/gallery/${p.name}-${camera}.png`, png);
    // Every cell must show something: > 2 % of its pixels differ from the background.
    (await cellFractions(page, png, expected)).forEach((f, k) => expect(f, `cell ${k} (seed ${ready.cells[k]!.seed})`).toBeGreaterThan(0.02));
    expect(errors).toEqual([]);
    console.log(`gallery ${p.name} ${camera}: ${ready.ms} ms for ${expected} cells`);
  });
}

// Roof slope shading: the sample house is a gable; in `role` colours the roof is one hue, so any luminance spread inside it
// is the slope tint. The two slopes must differ by >= 8 % of the mean roof luminance in both the top and the 3/4 camera.
for (const camera of ['top', 'front-left'] as const) {
  test(`roof slopes read differently in ${camera}`, async ({ page }) => {
    await page.goto(`/gallery.html?fixture=sample-house&camera=${camera}&colour=role`);
    await page.waitForFunction(() => window.__galleryReady !== undefined, undefined, { timeout: 60_000 });
    expect((await page.evaluate(() => window.__galleryReady!)).error).toBeUndefined();
    const png = await page.screenshot();
    mkdirSync('out/gallery', { recursive: true });
    writeFileSync(`out/gallery/roof-shading-${camera}.png`, png);
    const r = await page.evaluate(async (b64) => {
      const img = new Image();
      img.src = `data:image/png;base64,${b64}`;
      await img.decode();
      const c = document.createElement('canvas');
      c.width = img.width; c.height = img.height;
      const g = c.getContext('2d')!;
      g.drawImage(img, 0, 0);
      const d = g.getImageData(0, 0, c.width, c.height).data;
      const lum: number[] = [];
      for (let i = 0; i < d.length; i += 4) { // roof hue: red clearly above green and blue (terrain is green, walls beige)
        if (d[i]! > 1.8 * d[i + 1]! && d[i]! > 1.8 * d[i + 2]! && d[i]! > 40) lum.push(0.2126 * d[i]! + 0.7152 * d[i + 1]! + 0.0722 * d[i + 2]!);
      }
      lum.sort((a, b) => a - b);
      const mean = (xs: number[]) => xs.reduce((s, x) => s + x, 0) / Math.max(1, xs.length);
      const n = lum.length;
      return { n, dark: mean(lum.slice(0, Math.floor(n * 0.4))), light: mean(lum.slice(Math.ceil(n * 0.6))) };
    }, png.toString('base64'));
    expect(r.n).toBeGreaterThan(500);
    expect((r.light - r.dark) / ((r.light + r.dark) / 2), `dark ${r.dark.toFixed(0)} light ${r.light.toFixed(0)} over ${r.n} px`).toBeGreaterThan(0.08);
  });
}
