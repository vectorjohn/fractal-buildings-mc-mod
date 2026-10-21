import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

// Plan view over real cell skeletons (step SK): `fractal-cli city` output over recorded terrain, committed under
// fixtures/settlement/skeleton/. The terrain crop (terrain.json.gz) is the background. Screenshots: out/plan/skeleton-*.png.
mkdirSync('out/plan', { recursive: true });
test.use({ viewport: { width: 1200, height: 800 } });

const ROOT = resolve('fixtures/settlement/skeleton');

async function serve(page: Page, name: string): Promise<string> {
  const root = join(ROOT, name);
  await page.route((u) => u.pathname.startsWith(`/city/${name}/`), (r) => {
    const rel = decodeURIComponent(new URL(r.request().url()).pathname.replace(`/city/${name}/`, ''));
    const file = join(root, rel);
    if (!file.startsWith(root) || !existsSync(file) || !statSync(file).isFile()) return r.fulfill({ status: 404, body: 'not found' });
    return r.fulfill({ body: readFileSync(file), contentType: file.endsWith('.json') ? 'application/json' : 'application/octet-stream' });
  });
  return `/city/${name}/`;
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
const hex = (h: string): [number, number, number] => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

async function ready(page: Page): Promise<NonNullable<Window['__planReady']>> {
  await page.waitForFunction(() => { const r = window.__planReady; return r !== undefined && (!r.ok || r.pending === 0); }, undefined, { timeout: 30_000 });
  return page.evaluate(() => window.__planReady!);
}

const settlement = (name: string): { site: { tier: string }; violations: unknown[]; roads: { kind: string; bridges: number[][] }[]; gates: { kind: string }[]; landmarks: unknown[] } =>
  JSON.parse(readFileSync(join(ROOT, name, 'settlement.json'), 'utf8'));

test('town on the recorded river window: skeleton over terrain, no violations', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const s = settlement('river-town');
  expect(s.violations).toEqual([]);
  // (the main roads route round the river since TR widened the urban mask; bridges are checked on the joined metropolis below)
  const dir = await serve(page, 'river-town');
  await page.goto(`/plan.html?city=${dir}&terrain=${dir}terrain.json.gz&layers=+terrain&x=-900&z=-3500&zoom=0.6`);
  const r = await ready(page);
  expect(r.ok, r.error).toBe(true);
  expect(r.districts).toBeGreaterThanOrEqual(10);
  expect(r.roads).toBeGreaterThanOrEqual(3);
  expect(await colourFraction(page, hex('#f2f2f2'), 30)).toBeGreaterThan(0.0003); // main roads drawn
  writeFileSync('out/plan/skeleton-river-town.png', await page.screenshot());
  expect(errors).toEqual([]);
});

test('joined metropolis over five cells: arterials, gates between cells, no violations', async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  const s = settlement('joined-metropolis');
  expect(s.site.tier).toBe('metropolis');
  expect(s.violations).toEqual([]);
  expect(s.roads.filter((r) => r.kind === 'arterial').length).toBeGreaterThan(20);
  expect(s.gates.filter((g) => g.kind === 'cell').length).toBeGreaterThanOrEqual(4);
  expect(s.roads.some((r) => r.bridges.length > 0)).toBe(true);
  const dir = await serve(page, 'joined-metropolis');
  await page.goto(`/plan.html?city=${dir}&terrain=${dir}terrain.json.gz&layers=+terrain&x=6400&z=6528&zoom=0.25`);
  const r = await ready(page);
  expect(r.ok, r.error).toBe(true);
  expect(r.districts).toBeGreaterThan(100);
  expect(await colourFraction(page, hex('#f4d35e'), 30)).toBeGreaterThan(0.0003); // arterials drawn
  writeFileSync('out/plan/skeleton-joined-metropolis.png', await page.screenshot());
  expect(errors).toEqual([]);
});
