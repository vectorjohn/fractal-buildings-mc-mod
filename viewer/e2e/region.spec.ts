import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { gunzipSync, gzipSync } from 'node:zlib';

// Region map page (region.html) over the committed world region dumps (fixtures/region/, from `fractal-cli region`)
// and terrain dumps (../fixtures/terrain/, T1). Files are served through Playwright routes, gzip untouched.
// Screenshots go to out/region/. Regenerate the dumps: see docs/VIEWER.md "Region map".
test.use({ viewport: { width: 1400, height: 1140 } });

const worlds = [12345, 987654321];
const region = (s: number): Buffer => readFileSync(`fixtures/region/world-${s}.json.gz`);

async function serve(page: Page, name: string, body: Buffer): Promise<string> {
  await page.route((u) => u.pathname === `/data/${name}`, (r) => r.fulfill({ body, contentType: 'application/octet-stream' }));
  return `/data/${name}`;
}

/** Fraction of canvas pixels within `tol` (sum of abs channel difference) of an rgb colour. */
async function colourFraction(page: Page, rgb: [number, number, number], tol: number): Promise<number> {
  return page.evaluate(({ rgb, tol }) => {
    const c = document.getElementById('c') as HTMLCanvasElement;
    const d = c.getContext('2d')!.getImageData(0, 0, c.width, c.height).data;
    let n = 0;
    for (let i = 0; i < d.length; i += 4) if (Math.abs(d[i]! - rgb[0]) + Math.abs(d[i + 1]! - rgb[1]) + Math.abs(d[i + 2]! - rgb[2]) <= tol) n++;
    return n / (d.length / 4);
  }, { rgb, tol });
}

async function ready(page: Page): Promise<NonNullable<Window['__regionReady']>> {
  await page.waitForFunction(() => window.__regionReady !== undefined, undefined, { timeout: 30_000 });
  return page.evaluate(() => window.__regionReady!);
}

for (const seed of worlds) {
  test(`region map of world ${seed}`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    const url = await serve(page, `world-${seed}.json.gz`, region(seed));
    await page.goto(`/region.html?region=${url}`);
    const r = await ready(page);
    expect(r.error).toBeUndefined();
    expect(r.cells).toBeGreaterThan(300);
    expect(r.settled).toBeGreaterThan(50);
    expect(r.heat).toBe(true); // waterDist is in the dump (R2)
    expect(r.claims).toBeGreaterThan(5); // MC1: big cities claim neighbouring cells
    // a world map: both water and land colours are present
    expect(await colourFraction(page, [40, 90, 160], 60)).toBeGreaterThan(0.05);
    expect(await colourFraction(page, [110, 180, 80], 60)).toBeGreaterThan(0.02);
    mkdirSync('out/region', { recursive: true });
    writeFileSync(`out/region/world-${seed}.png`, await page.screenshot({ fullPage: true }));
    expect(errors).toEqual([]);
  });
}

test('region over a fine terrain dump, uncompressed region file', async ({ page }) => {
  const plain = gunzipSync(region(12345)); // plain JSON under a .gz-less name: detection is by magic
  const r1 = await serve(page, 'region.json', plain);
  const t1 = await serve(page, 'terrain.bin', readFileSync('../fixtures/terrain/w12345-river-s8.json.gz'));
  await page.goto(`/region.html?region=${r1}&terrain=${t1}&view=-1408,-4160,2048,2048&scale=0.5`);
  const r = await ready(page);
  expect(r.error).toBeUndefined();
  expect(await colourFraction(page, [40, 90, 160], 60)).toBeGreaterThan(0.02); // the river and bay
  mkdirSync('out/region', { recursive: true });
  writeFileSync('out/region/world-12345-river-s8.png', await page.screenshot({ fullPage: true }));
});

test('waterDist heat and claimedBy links appear when the dump has them', async ({ page }) => {
  const d = JSON.parse(gunzipSync(region(12345)).toString('utf8')) as { layers: { cells: Record<string, unknown>[] }[] };
  const cells = d.layers[0]!.cells;
  cells.forEach((c, i) => { c['waterDist'] = (i * 37) % 900; });
  const settled = cells.filter((c) => c['city'] === true);
  const loser = cells.find((c) => c['city'] === false)!;
  loser["claimedBy"] = [settled[0]!["cx"], settled[0]!["cz"]];
  const url = await serve(page, 'claims.json.gz', gzipSync(JSON.stringify(d)));
  await page.goto(`/region.html?region=${url}`);
  const r = await ready(page);
  expect(r.error).toBeUndefined();
  expect(r.heat).toBe(true);
  expect(r.claims).toBeGreaterThanOrEqual(1); // the injected link, on top of the real ones
  mkdirSync('out/region', { recursive: true });
  writeFileSync('out/region/world-12345-heat-claims.png', await page.screenshot({ fullPage: true }));
  // and off by switch
  await page.goto(`/region.html?region=${url}&heat=0&claims=0`);
  await page.evaluate(() => { window.__regionReady = undefined; });
  await page.reload();
  const r2 = await ready(page);
  expect(r2).toMatchObject({ heat: false, claims: 0 });
});

test('a missing file reports an error instead of hanging', async ({ page }) => {
  await page.goto('/region.html?region=/data/nope.json');
  const r = await ready(page);
  expect(r.ok).toBe(false);
});
