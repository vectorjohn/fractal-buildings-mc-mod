import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { gunzipSync } from 'node:zlib';
import { join, resolve } from 'node:path';

// Road layer of the region map (ROADMAP 6.10, ADR 0004) over the committed roads dumps (fixtures/region/roads-<seed>.json.gz, from
// `fractal-cli roads --seed S --terrain recorded:... --out ...`). Screenshots go to out/region/roads-*.png.
test.use({ viewport: { width: 1400, height: 1140 } });

const worlds = [12345, 987654321];

async function serve(page: Page, name: string, body: Buffer): Promise<string> {
  await page.route((u) => u.pathname === `/data/${name}`, (r) => r.fulfill({ body, contentType: 'application/octet-stream' }));
  return `/data/${name}`;
}

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
  test(`road layer of world ${seed}`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    const url = await serve(page, `roads-${seed}.json.gz`, readFileSync(`fixtures/region/roads-${seed}.json.gz`));
    await page.goto(`/region.html?region=${url}&heat=0&reasons=0&grid=0`);
    const r = await ready(page);
    expect(r.error).toBeUndefined();
    expect(r.roads.links).toBeGreaterThan(20);
    expect(r.roads.meetings).toBeGreaterThan(3);
    expect(r.roads.dropped).toBeGreaterThan(10);
    expect(r.roads.ports).toBeGreaterThan(10); // N6: port-tagged towns and cities
    const on = await colourFraction(page, [255, 225, 77], 40); // road yellow on the map
    mkdirSync('out/region', { recursive: true });
    writeFileSync(`out/region/roads-${seed}.png`, await page.screenshot({ fullPage: true }));
    // the switch hides the whole layer
    await page.goto(`/region.html?region=${url}&roads=0`);
    await page.evaluate(() => { window.__regionReady = undefined; });
    await page.reload();
    const off = await ready(page);
    expect(off.roads.links).toBe(0);
    expect(await colourFraction(page, [255, 225, 77], 40)).toBeLessThan(on - 0.001); // the roads were most of the yellow
    expect(errors).toEqual([]);
  });
}

test('zoomed road layer shows crossings and meeting points', async ({ page }) => {
  const url = await serve(page, 'roads-12345.json.gz', readFileSync('fixtures/region/roads-12345.json.gz'));
  // a window of 1200 blocks round the first crossing, drawn at 1 px per block
  const d = JSON.parse(gunzipSync(readFileSync('fixtures/region/roads-12345.json.gz')).toString('utf8')) as { layers: { roads: { edges: { pts: number[]; cross: number[] }[] } }[] };
  const e = d.layers[0]!.roads.edges.find((x) => x.cross.length > 0)!;
  const cx = e.pts[2 * e.cross[0]!]!, cz = e.pts[2 * e.cross[0]! + 1]!;
  await page.goto(`/region.html?region=${url}&view=${cx - 600},${cz - 600},1200,1200&scale=1&heat=0&reasons=0`);
  const r = await ready(page);
  expect(r.error).toBeUndefined();
  expect(r.roads.crossings).toBeGreaterThanOrEqual(1);
  expect(await colourFraction(page, [38, 217, 255], 40)).toBeGreaterThan(0.00001); // the cyan span (at least a few pixels)
  mkdirSync('out/region', { recursive: true });
  writeFileSync('out/region/roads-12345-zoom.png', await page.screenshot({ fullPage: true }));
});

test('a dump without a roads object draws no road layer', async ({ page }) => {
  const url = await serve(page, 'world.json.gz', readFileSync('fixtures/region/world-12345.json.gz'));
  await page.goto(`/region.html?region=${url}`);
  const r = await ready(page);
  expect(r.error).toBeUndefined();
  expect(r.roads).toEqual({ links: 0, crossings: 0, meetings: 0, dropped: 0, ports: 0 });
});

// Plan view: approaches from the required exits to the road ports (`links` layer).
for (const name of ['city-s2_1', 'town-s3_7']) {
  test(`plan view draws the approaches of ${name}`, async ({ page }) => {
    const root = resolve(`fixtures/settlement/roads/${name}`);
    await page.route((u) => u.pathname.startsWith(`/city/${name}/`), (r) => {
      const rel = decodeURIComponent(new URL(r.request().url()).pathname.replace(`/city/${name}/`, ''));
      return r.fulfill({ body: readFileSync(join(root, rel)), contentType: rel.endsWith('.json') ? 'application/json' : 'application/octet-stream' });
    });
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    await page.setViewportSize({ width: 1200, height: 800 });
    await page.goto(`/plan.html?city=/city/${name}/`);
    await page.waitForFunction(() => window.__planReady !== undefined, undefined, { timeout: 30_000 });
    const r = await page.evaluate(() => window.__planReady!);
    expect(r.error).toBeUndefined();
    expect(r.links).toBeGreaterThanOrEqual(2);
    mkdirSync('out/plan', { recursive: true });
    writeFileSync(`out/plan/links-${name}.png`, await page.screenshot());
    await page.goto(`/plan.html?city=/city/${name}/&layers=%2Dlinks`);
    await page.evaluate(() => { window.__planReady = undefined; });
    await page.reload();
    await page.waitForFunction(() => window.__planReady !== undefined, undefined, { timeout: 30_000 });
    expect((await page.evaluate(() => window.__planReady!)).links).toBe(0);
    expect(errors).toEqual([]);
  });
}
