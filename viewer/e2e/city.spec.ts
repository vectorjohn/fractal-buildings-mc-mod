import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { parseCityPreset } from '../src/cityPresets';

// End to end over a planned city (step D0): `npm run city` writes out/city/<name>/ (settlement, districts, scenes) for each preset in
// city-presets/; this spec loads the plan view, then the 3D view of the first two district scenes, and saves screenshots next to the
// data (out/city/<name>/plan.png, <district>-overview.png, <district>-street.png). A plain run checks `synth-city` if its data exists;
// `npm run city-gallery` (CITY_GALLERY=1) checks every preset. FB_CITIES=a,b picks others. Open the PNGs: nothing here is a golden image.
const ROOT = resolve(process.env['FB_CITY_OUT'] ?? 'out/city');
const gallery = process.env['CITY_GALLERY'] === '1';
const pick = (process.env['FB_CITIES'] ?? '').split(',').filter((s) => s !== '');
const presets = readdirSync('city-presets').filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort()
  .filter((n) => (pick.length > 0 ? pick.includes(n) : gallery || n === 'synth-city'))
  .map((n) => parseCityPreset(n, JSON.parse(readFileSync(`city-presets/${n}.json`, 'utf8'))));

test.use({ viewport: { width: 1200, height: 800 } });

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

interface Settlement { site: { tier: string; centre: number[] }; violations: { invariant: string; message: string }[]; districts: { id: string; lots: number; detail: string | null }[] }

for (const p of presets) {
  const dir = join(ROOT, p.name);
  test.describe(p.name, () => {
    test.skip(!existsSync(join(dir, 'settlement.json')), `no data in ${dir}: run \`npm run city ${p.name}\``);

    test('plan view loads the planned districts', async ({ page }) => {
      test.setTimeout(120_000);
      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(String(e)));
      const s = JSON.parse(readFileSync(join(dir, 'settlement.json'), 'utf8')) as Settlement;
      const byRule = new Map<string, number>();
      for (const v of s.violations) byRule.set(v.invariant, (byRule.get(v.invariant) ?? 0) + 1);
      test.info().annotations.push({ type: 'violations', description: JSON.stringify(Object.fromEntries(byRule)) });
      // the tile borders of neighbouring districts always agree; roads-connected is reported (known hub-area gaps), not asserted here
      expect(byRule.get('tile-border-agree') ?? 0).toBe(0);
      const withDetail = s.districts.filter((d) => d.detail !== null);
      expect(withDetail.length).toBeGreaterThan(s.site.tier === "hamlet" ? 0 : 3); // a hamlet is one or two tiles
      const base = await serve(page, p.name);
      const q = p.camera.plan;
      const view = `${q.x !== undefined && q.z !== undefined ? `&x=${q.x}&z=${q.z}` : ''}${q.zoom !== undefined ? `&zoom=${q.zoom}` : ''}`;
      await page.goto(`/plan.html?city=${base}&terrain=${base}terrain.json.gz&layers=${encodeURIComponent(process.env['FB_PLAN_LAYERS'] ?? q.layers ?? '+terrain')}${view}`);
      await page.waitForFunction(() => { const r = window.__planReady; return r !== undefined && (!r.ok || r.pending === 0); }, undefined, { timeout: 90_000 });
      const ready = await page.evaluate(() => window.__planReady!);
      expect(ready.ok, ready.error).toBe(true);
      expect(ready.districts).toBe(s.districts.length);
      writeFileSync(join(dir, `plan${process.env['FB_PLAN_SUFFIX'] ?? ''}.png`), await page.screenshot());
      expect(errors).toEqual([]);
    });

    const scenesDir = join(dir, 'scenes');
    const ids = existsSync(scenesDir) ? readdirSync(scenesDir).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort().slice(0, 2) : [];
    for (const id of ids) {
      test(`district ${id}: 3D view of the planned scene`, async ({ page }) => {
        test.setTimeout(240_000);
        const errors: string[] = [];
        page.on('pageerror', (e) => errors.push(String(e)));
        const body = readFileSync(join(scenesDir, `${id}.json`), 'utf8');
        const t = (JSON.parse(readFileSync(join(dir, 'districts', `${id}.json`), 'utf8')) as { district: { x: number; z: number; w: number; d: number } }).district;
        await page.route((u) => u.pathname === `/data/${id}.json`, (r) => r.fulfill({ body, contentType: 'application/json' }));
        const cx = t.x + t.w / 2, cz = t.z + t.d / 2;
        const stops = ids[0] === id && p.camera.stops !== null ? p.camera.stops.map((s) => s.join(',')).join(';') : `${cx},170,${t.z + t.d + 70},0,-42;${cx},95,${cz + 40},0,-12`;
        await page.goto(`/index.html?ir=/data/${id}.json&camera=fly&exterior=1&stops=${stops}`);
        await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 200_000 });
        const ready = await page.evaluate(() => window.__viewerReady!);
        expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
        expect((JSON.parse(body) as { violations: unknown[] }).violations).toEqual([]);
        writeFileSync(join(dir, `${id}-overview.png`), await page.screenshot());
        await page.keyboard.press(']');
        await page.waitForTimeout(1500);
        writeFileSync(join(dir, `${id}-street.png`), await page.screenshot());
        expect(errors).toEqual([]);
      });
    }
  });
}
