import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { budget, loadFactor, timeoutFactor } from './load';

// District 3D view (V2): the P0 sample district scene (12 buildings, a river, road boxes) in the main viewer page,
// face-meshed, with the fly camera at three stops. Screenshots go to out/district/. Software WebGL: keep it light.
const SAMPLE = 'fixtures/settlement/sample/scenes/d0_0.json';
test.use({ viewport: { width: 960, height: 640 } });


/** The sample scene tiled k x k times (terrain, buildings, lots, ground): a stand-in for a downtown district. */
function tiled(k: number): string {
  const d = JSON.parse(readFileSync(SAMPLE, 'utf8'));
  const { nx, nz } = d.terrain;
  const floorY: number[] = [], water: number[] = [];
  for (let tz = 0; tz < k; tz++)
    for (let j = 0; j < nz; j++)
      for (let tx = 0; tx < k; tx++)
        for (let i = 0; i < nx; i++) { floorY.push(d.terrain.floorY[j * nx + i]); water.push(d.terrain.water[j * nx + i]); }
  const lots: unknown[] = [], buildings: unknown[] = [], ground: unknown[] = [];
  for (let tz = 0; tz < k; tz++)
    for (let tx = 0; tx < k; tx++) {
      const dx = tx * nx, dz = tz * nz, tag = `t${tx}_${tz}_`;
      for (const l of d.lots) lots.push({ ...l, id: tag + l.id, x: l.x + dx, z: l.z + dz });
      for (const b of d.buildings) buildings.push({ ...b, id: tag + b.id, lots: b.lots.map((s: string) => tag + s), origin: [b.origin[0] + dx, b.origin[1], b.origin[2] + dz] });
      for (const g of d.ground) ground.push({ ...g, min: [g.min[0] + dx, g.min[1], g.min[2] + dz] });
    }
  return JSON.stringify({ ...d, terrain: { ...d.terrain, nx: nx * k, nz: nz * k, floorY, water }, lots, buildings, ground, violations: [] });
}

async function serve(page: Page, name: string, body: string): Promise<string> {
  await page.route((u) => u.pathname === `/data/${name}`, (r) => r.fulfill({ body, contentType: 'application/json' }));
  return `/data/${name}`;
}

async function ready(page: Page): Promise<NonNullable<Window['__viewerReady']>> {
  await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 90_000 });
  return page.evaluate(() => window.__viewerReady!);
}

/** Share of canvas pixels within `tol` (sum of abs channel difference) of an rgb colour, from a page screenshot. */
async function fractions(page: Page, targets: Record<string, [number, number, number]>, tol: number): Promise<Record<string, number>> {
  const png = await page.screenshot();
  return page.evaluate(async ({ b64, targets, tol }) => {
    const img = new Image();
    img.src = `data:image/png;base64,${b64}`;
    await img.decode();
    const c = document.createElement('canvas');
    c.width = img.width; c.height = img.height;
    const g = c.getContext('2d')!;
    g.drawImage(img, 0, 0);
    const d = g.getImageData(0, 0, c.width, c.height).data;
    const out: Record<string, number> = {};
    for (const k of Object.keys(targets)) out[k] = 0;
    for (let i = 0; i < d.length; i += 4)
      for (const [k, t] of Object.entries(targets))
        if (Math.abs(d[i]! - t[0]) + Math.abs(d[i + 1]! - t[1]) + Math.abs(d[i + 2]! - t[2]) <= tol) out[k]!++;
    for (const k of Object.keys(out)) out[k]! /= d.length / 4;
    return out;
  }, { b64: png.toString('base64'), targets, tol });
}

const STOPS = '72,150,245,0,-38;28,72,168,0,-12;175,90,60,90,-35';

test('sample district: faces mesh, fly camera, three stops, ground and water visible', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const url = await serve(page, 'd0_0.json', readFileSync(SAMPLE, 'utf8'));
  await page.goto(`/?ir=${url}&mesh=faces&camera=fly&stops=${STOPS}`);
  const r = await ready(page);
  expect(r.ok, r.error).toBe(true);
  expect(r.stats).toMatchObject({ mode: 'faces', district: true });
  expect(r.stats!.groundCells).toBeGreaterThan(500); // the road boxes reach the renderer
  expect(r.stats!.waterQuads).toBeGreaterThan(0);
  expect(r.stats!.triangles).toBeGreaterThan(5000);
  expect(await page.evaluate(() => window.__viewer!.stops)).toBe(3);
  mkdirSync('out/district', { recursive: true });
  const seen: Record<string, number>[] = [];
  for (let i = 0; i < 3; i++) {
    await page.evaluate((n) => { window.__viewerReady = { ...window.__viewerReady!, stop: -1 }; window.__viewer!.goStop(n); }, i);
    await page.waitForFunction((n) => window.__viewerReady?.stop === n, i, { timeout: 30_000 });
    writeFileSync(`out/district/sample-stop${i}.png`, await page.screenshot());
    seen.push(await fractions(page, { water: [130, 170, 205], road: [205, 188, 150], grass: [155, 180, 130] }, 45));
  }
  // The overview has all three; the street-level stop has road and grass; the river stop has water.
  console.log('sample', JSON.stringify({ voxels: r.voxels, meshTriangles: r.stats!.triangles, ...(await page.evaluate(() => window.__viewer!.measure(5))), loadavg: readFileSync('/proc/loadavg', 'utf8').trim() }));
  expect(seen[0]!['water']).toBeGreaterThan(0.002);
  expect(seen[0]!['grass']).toBeGreaterThan(0.05);
  expect(seen[1]!['road']).toBeGreaterThan(0.005);
  expect(seen[2]!['water']).toBeGreaterThan(0.01);
  expect(errors).toEqual([]);
});

test('ground and water are layers that can be switched off', async ({ page }) => {
  const url = await serve(page, 'd0_0.json', readFileSync(SAMPLE, 'utf8'));
  const at = async (layers: string): Promise<Record<string, number>> => {
    await page.goto(`/?ir=${url}&camera=fly&stops=${STOPS}${layers}`);
    expect((await ready(page)).ok).toBe(true);
    await page.evaluate(() => window.__viewer!.goStop(2));
    await page.waitForFunction(() => window.__viewerReady?.stop === 2);
    return fractions(page, { water: [130, 170, 205], road: [205, 188, 150] }, 45);
  };
  const on = await at('');
  const off = await at('&layers=terrain,foundation,shell,interior,roof');
  expect(on['water']).toBeGreaterThan(0.01);
  expect(off['water']).toBeLessThan(on['water']! * 0.1);
});

test('large synthetic district at ?exterior=1 loads within budget (voxels, triangles, frame time recorded)', async ({ page }) => {
  test.setTimeout(Math.round(180_000 * timeoutFactor()));
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const k = Number(process.env['FB_TILES'] ?? 6);
  const url = await serve(page, 'big.json', tiled(k));
  const f0 = loadFactor();
  const t0 = Date.now();
  await page.goto(`/?ir=${url}&exterior=1&camera=fly&stops=${k * 72},${110 + k * 6},${k * 160 + 60},0,-35`);
  const r = await ready(page);
  const loadMs = Date.now() - t0;
  expect(r.ok, r.error).toBe(true);
  expect(r.stats!.mode).toBe('faces'); // above the 200k-voxel threshold
  const m = await page.evaluate(() => window.__viewer!.measure(5));
  const load = readFileSync('/proc/loadavg', 'utf8').trim();
  // 20 s on a quiet machine (5-7 s measured at load 25, V2 2026-10-21), stretched by load per CPU (e2e/load.ts).
  const loadBudgetMs = budget(20_000, f0);
  console.log(JSON.stringify({ meshTriangles: r.stats!.triangles, tiles: k * k, buildings: k * k * 12, voxels: r.voxels, loadMs, loadBudgetMs, ...m, loadavg: load }));
  writeFileSync('out/district/big.png', await page.screenshot());
  expect(r.voxels).toBeGreaterThan(200_000);
  expect(loadMs).toBeLessThan(loadBudgetMs);
  expect(errors).toEqual([]);
});

// Optional: real DistrictScenes output. FB_DISTRICT_DIR=<dir of scene .json> (e.g. viewer/out/district/ written by DistrictScenesTest)
// renders every scene in it at the default fly pose and records the stats; skipped when unset.
const dir = process.env['FB_DISTRICT_DIR'];
test('real district scenes render', async ({ page }) => {
  test.skip(!dir, 'FB_DISTRICT_DIR not set');
  test.setTimeout(Math.round(240_000 * timeoutFactor()));
  const { readdirSync } = await import('node:fs');
  mkdirSync('out/district', { recursive: true });
  for (const f of readdirSync(dir!).filter((n) => n.endsWith('.json')).sort()) {
    const errors: string[] = [];
    const onErr = (e: Error): number => errors.push(String(e));
    page.on('pageerror', onErr);
    const url = await serve(page, f, readFileSync(`${dir}/${f}`, 'utf8'));
    await page.goto(`/?ir=${url}&camera=fly`);
    const r = await ready(page);
    expect(r.ok, `${f}: ${r.error}`).toBe(true);
    console.log(f, JSON.stringify(r.stats));
    writeFileSync(`out/district/real-${f.replace('.json', '')}.png`, await page.screenshot());
    page.off('pageerror', onErr);
    expect(errors).toEqual([]);
  }
});
