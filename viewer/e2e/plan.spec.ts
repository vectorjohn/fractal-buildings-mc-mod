import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from 'node:fs';
import { loadavg } from 'node:os';
import { join, resolve } from 'node:path';

// Plan view (plan.html) over the sample fixture and a synthetic big city (`fractal-cli sample-city --big N`, generated
// here into out/city-big/). City files are served through a Playwright route under /city/<name>/. Screenshots: out/plan/.
mkdirSync('out/plan', { recursive: true });
test.use({ viewport: { width: 1200, height: 800 } });

const SAMPLE = resolve('fixtures/settlement/sample');
const BIG = resolve('out/city-big');
const BIG_SIZE = 4096;

async function serveCity(page: Page, name: string, root: string): Promise<string> {
  await page.route((u) => u.pathname.startsWith(`/city/${name}/`), (r) => {
    const rel = decodeURIComponent(new URL(r.request().url()).pathname.replace(`/city/${name}/`, ''));
    const file = join(root, rel);
    if (!file.startsWith(root) || !existsSync(file) || !statSync(file).isFile()) return r.fulfill({ status: 404, body: 'not found' });
    return r.fulfill({ body: readFileSync(file), contentType: file.endsWith('.json') ? 'application/json' : 'application/octet-stream' });
  });
  return `/city/${name}/`;
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
const hex = (h: string): [number, number, number] => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];

/** Waits until the page has drawn and no district load is pending. */
async function settled(page: Page, pred: (r: NonNullable<Window['__planReady']>) => boolean = () => true): Promise<NonNullable<Window['__planReady']>> {
  await page.waitForFunction((src) => {
    const r = window.__planReady;
    return r !== undefined && (!r.ok || (r.pending === 0 && (new Function('r', `return (${src})(r)`) as (r: unknown) => boolean)(r)));
  }, pred.toString(), { timeout: 30_000 });
  return page.evaluate(() => window.__planReady!);
}

function trackErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(String(e)));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  return errors;
}

test('sample fixture: overview shows district density, roads and landmarks, no lots yet', async ({ page }) => {
  const errors = trackErrors(page);
  const dir = await serveCity(page, 'sample', SAMPLE);
  await page.goto(`/plan.html?city=${dir}&x=136&z=144&zoom=0.4`);
  const r = await settled(page);
  expect(r.ok, r.error).toBe(true);
  expect(r.districts).toBe(4);
  expect(r.lod).toBe('overview');
  expect(r.loaded).toBe(0); // lazy: nothing loaded below 1/2 px per block
  expect(r.roads).toBeGreaterThanOrEqual(3);
  expect(await colourFraction(page, hex('#f2f2f2'), 30)).toBeGreaterThan(0.0001); // main road
  writeFileSync('out/plan/sample-overview.png', await page.screenshot());
  expect(errors).toEqual([]);
});

test('sample fixture: detail zoom loads districts and draws lots by type', async ({ page }) => {
  const errors = trackErrors(page);
  const dir = await serveCity(page, 'sample', SAMPLE);
  await page.goto(`/plan.html?city=${dir}&x=136&z=144&zoom=3&layers=+quarters`);
  const r = await settled(page, (s) => s.loaded >= 4 && s.lots > 20);
  expect(r.lod).toBe('detail');
  expect(r.loaded).toBe(4);
  expect(r.lots).toBeGreaterThan(20);
  for (const col of ['#c9805a', '#4aa3d8', '#8a6bbf']) expect(await colourFraction(page, hex(col), 24), col).toBeGreaterThan(0.002);
  expect(await colourFraction(page, hex('#d6d0c4'), 24)).toBeGreaterThan(0.002); // street
  writeFileSync('out/plan/sample-detail.png', await page.screenshot());
  expect(errors).toEqual([]);
});

test('sample fixture: layer toggles, hover info and the 3D scene link', async ({ page }) => {
  const errors = trackErrors(page);
  const dir = await serveCity(page, 'sample', SAMPLE);
  await page.goto(`/plan.html?city=${dir}&x=72&z=80&zoom=4`);
  await settled(page, (s) => s.loaded >= 1 && s.lots > 0);
  // hover a lot of district d0_0 (its first lot is at 8,123 and lies below the centre of the view)
  const box = (await page.locator('#c').boundingBox())!;
  const lot = { x: 12, z: 130 };
  const px = box.x + box.width / 2 + (lot.x - 72) * 4, py = box.y + box.height / 2 + (lot.z - 80) * 4;
  await page.mouse.move(px, py);
  await expect(page.locator('#info')).toContainText('lot d0_0.l');
  await page.mouse.click(px, py);
  await expect(page.locator('#scene-link')).toHaveAttribute('href', /index\.html\?ir=.*scenes%2Fd0_0\.json&camera=fly/);
  // toggling roads off removes the road colour
  const before = await colourFraction(page, hex('#f2f2f2'), 20);
  await page.locator('input[data-layer="roads"]').uncheck();
  await page.waitForTimeout(100);
  const after = await colourFraction(page, hex('#f2f2f2'), 20);
  expect(after).toBeLessThan(before);
  expect(new URL(page.url()).searchParams.get('layers')).not.toContain('roads');
  expect(errors).toEqual([]);
});

test('a bad city directory reports an error instead of drawing', async ({ page }) => {
  await page.route((u) => u.pathname.startsWith('/city/none/'), (r) => r.fulfill({ status: 404, body: 'nope' }));
  await page.goto('/plan.html?city=/city/none/');
  const r = await settled(page);
  expect(r.ok).toBe(false);
  expect(r.error).toContain('404');
});

test.describe('big city', () => {
  test.beforeAll(() => {
    if (!existsSync(BIG) || !existsSync(join(BIG, 'settlement.json'))) {
      const cli = resolve('../cli/build/install/fractal-cli/bin/fractal-cli');
      if (!existsSync(cli)) execFileSync(resolve('../gradlew'), [':cli:installDist', '-q'], { cwd: resolve('..'), stdio: 'inherit' });
      mkdirSync(BIG, { recursive: true });
      execFileSync(cli, ['sample-city', '--out-dir', BIG, '--big', String(BIG_SIZE)], { stdio: 'inherit' });
    }
    expect(readdirSync(join(BIG, 'districts')).length).toBeGreaterThan(100);
  });

  test(`sample-city --big ${BIG_SIZE}: overview in under 3 s, zoomed pan frames under 100 ms`, async ({ page }) => {
    const errors = trackErrors(page);
    const dir = await serveCity(page, 'big', BIG);
    const t0 = Date.now();
    await page.goto(`/plan.html?city=${dir}&layers=+density`);
    const r = await settled(page);
    const overviewMs = Date.now() - t0;
    expect(r.ok, r.error).toBe(true);
    expect(r.districts).toBeGreaterThan(900);
    expect(r.lod).toBe('overview');
    expect(r.loaded).toBe(0);
    expect(overviewMs).toBeLessThan(3000);
    writeFileSync('out/plan/big-overview.png', await page.screenshot());

    // zoom in: only visible districts load
    await page.evaluate(() => window.__planApi!.setView(2048, 2048, 1));
    const d = await settled(page, (s) => s.scale === 1 && s.loaded > 0 && s.lots > 0);
    expect(d.lod).toBe('detail');
    expect(d.loaded).toBeLessThan(100);
    writeFileSync('out/plan/big-detail.png', await page.screenshot());

    // pan frames: redraw at 20 shifted views, take the worst
    const frames = await page.evaluate(() => {
      const ms: number[] = [];
      for (let i = 0; i < 20; i++) { window.__planApi!.setView(2048 + i * 3, 2048 + i * 2, 1); ms.push(window.__planApi!.redraw()); }
      return ms;
    });
    const worst = Math.max(...frames);
    console.log(`big ${BIG_SIZE}: overview ${overviewMs} ms, worst pan frame ${worst.toFixed(1)} ms, ${d.loaded} districts / ${d.lots} lots drawn, load ${loadavg()[0]!.toFixed(1)}`);
    expect(worst).toBeLessThan(100);
    expect(errors).toEqual([]);
  });
});
