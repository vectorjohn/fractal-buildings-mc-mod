import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

// Plan view over district detail (step DL): `fractal-cli city --districts` output over recorded terrain, committed under
// fixtures/settlement/districts/ (README there). Screenshots: out/plan/districts-*.png. FB_CITY_DIR points at another root.
mkdirSync('out/plan', { recursive: true });
test.use({ viewport: { width: 1200, height: 800 } });

const ROOT = resolve(process.env.FB_CITY_DIR ?? 'fixtures/settlement/districts');

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

async function ready(page: Page): Promise<NonNullable<Window['__planReady']>> {
  await page.waitForFunction(() => { const r = window.__planReady; return r !== undefined && (!r.ok || r.pending === 0); }, undefined, { timeout: 60_000 });
  return page.evaluate(() => window.__planReady!);
}

interface Settlement { site: { tier: string; centre: number[] }; violations: unknown[]; districts: { id: string; lots: number; detail: string | null }[] }
const settlement = (name: string): Settlement => JSON.parse(readFileSync(join(ROOT, name, 'settlement.json'), 'utf8'));

const cities: { name: string; zoom: number }[] = (process.env.FB_CITIES ?? 'river-town:0.7,mixed-city:1,synth-city:0.6').split(',').map((s) => ({ name: s.split(':')[0]!, zoom: Number(s.split(':')[1]) }));

for (const c of cities) {
  test(`${c.name}: districts load in the plan view, no violations`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    const s = settlement(c.name);
    expect(s.violations).toEqual([]);
    const withDetail = s.districts.filter((d) => d.detail !== null);
    expect(withDetail.length).toBeGreaterThan(5);
    const dir = await serve(page, c.name);
    await page.goto(`/plan.html?city=${dir}&terrain=${dir}terrain.json.gz&layers=${process.env.FB_LAYERS ?? "+terrain"}&x=${s.site.centre[0]}&z=${s.site.centre[1]}&zoom=${c.zoom}`);
    const r = await ready(page);
    expect(r.ok, r.error).toBe(true);
    expect(r.lots).toBeGreaterThan(20);
    writeFileSync(`out/plan/districts-${c.name}.png`, await page.screenshot());
    expect(errors).toEqual([]);
  });
}

// The 3D view of district scenes planned by DL (`--scene`): an overview and a street-level stop per scene; the settlement's own
// `violations` and the scene's building violations must be empty. Scenes come from fixtures/settlement/districts/synth-city/scenes.
mkdirSync('out/district', { recursive: true });
const sceneCity = process.env.FB_SCENE_CITY ?? join(ROOT, 'synth-city'); // a city dir with scenes/ (written by city --scene d0_0,d1_0)
const scenesDir = join(sceneCity, 'scenes');
const scenes = existsSync(scenesDir) ? ['d0_0', 'd1_0'].filter((n) => existsSync(join(scenesDir, `${n}.json`))) : [];
for (const n of scenes) {
  test(`district ${n}: 3D view of the planned scene`, async ({ page }) => {
    test.setTimeout(180_000);
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(String(e)));
    const body = readFileSync(join(scenesDir, `${n}.json`), 'utf8');
    const d = JSON.parse(readFileSync(join(sceneCity, 'districts', `${n}.json`), 'utf8')) as { district: { x: number; z: number; w: number; d: number } };
    const t = d.district;
    await page.route((u) => u.pathname === `/data/${n}.json`, (r) => r.fulfill({ body, contentType: 'application/json' }));
    const cx = t.x + t.w / 2, cz = t.z + t.d / 2;
    const stops = `${cx},170,${t.z + t.d + 70},0,-42;${cx},95,${cz + 40},0,-12`;
    await page.goto(`/index.html?ir=/data/${n}.json&camera=fly&exterior=1&stops=${stops}`);
    await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 150_000 });
    const ready = await page.evaluate(() => window.__viewerReady!);
    expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
    expect(JSON.parse(body).violations).toEqual([]);
    writeFileSync(`out/district/dl-${n}-overview.png`, await page.screenshot());
    await page.keyboard.press(']');
    await page.waitForTimeout(1500);
    writeFileSync(`out/district/dl-${n}-street.png`, await page.screenshot());
    expect(errors).toEqual([]);
  });
}
