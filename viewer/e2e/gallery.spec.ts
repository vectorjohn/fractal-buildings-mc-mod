import { expect, test } from '@playwright/test';
import { mkdirSync, writeFileSync } from 'node:fs';
import { PRESET_NAMES } from '../src/presets';

// Run by `npm run gallery` (GALLERY=1, FB_GALLERY=1 build with the CLI batch output). Skipped in the plain `screenshot` run.
test.skip(process.env['GALLERY'] !== '1', 'gallery only: npm run gallery');
test.use({ viewport: { width: 1280, height: 1280 } });

const STYLES = ['plain', 'medieval'];
const TERRAINS = ['noise', 'flat'];

for (const style of STYLES) for (const terrain of TERRAINS) for (const preset of PRESET_NAMES) {
  test(`gallery ${style} ${terrain} ${preset}`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    await page.goto(`/gallery.html?style=${style}&terrain=${terrain}&from=0&preset=${preset}`);
    await page.waitForFunction(() => window.__galleryReady !== undefined, undefined, { timeout: 60_000 });
    const ready = await page.evaluate(() => window.__galleryReady!);
    expect(ready.error).toBeUndefined();
    expect(ready.cells).toHaveLength(16);
    expect(ready.violations).toBe(0);

    const png = await page.screenshot();
    mkdirSync('out/gallery', { recursive: true });
    writeFileSync(`out/gallery/${style}${terrain === 'flat' ? '-flat' : ''}-${preset}.png`, png);

    // Every cell must show something: > 2 % of its pixels differ from the background.
    const fractions = await page.evaluate(async (b64) => {
      const img = new Image();
      img.src = `data:image/png;base64,${b64}`;
      await img.decode();
      const c = document.createElement('canvas');
      c.width = img.width; c.height = img.height;
      const g = c.getContext('2d')!;
      g.drawImage(img, 0, 0);
      const cell = img.width / 4;
      const out: number[] = [];
      for (let k = 0; k < 16; k++) {
        const d = g.getImageData((k % 4) * cell + 4, Math.floor(k / 4) * cell + 24, cell - 8, cell - 28).data;
        let non = 0;
        for (let i = 0; i < d.length; i += 4) {
          if (Math.abs(d[i]! - 0x1b) + Math.abs(d[i + 1]! - 0x1d) + Math.abs(d[i + 2]! - 0x22) > 12) non++;
        }
        out.push(non / (d.length / 4));
      }
      return out;
    }, png.toString('base64'));
    fractions.forEach((f, k) => expect(f, `cell ${k} (seed ${ready.cells[k]!.seed})`).toBeGreaterThan(0.02));
    expect(errors).toEqual([]);
  });
}
