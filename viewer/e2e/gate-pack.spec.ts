import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { parseCityPreset } from '../src/cityPresets';

// Phase 5 gate pack captures (docs/review/phase5/README.md). Skipped unless GATE_PACK=1. Needs `npm run city -- <names>` first and
// the featured district's scene (`node scripts/gate-pack-scenes.mjs`; the districts are listed in e2e/gate-pack-featured.json). Per city it writes to out/gate-pack/<name>/: plan.png (the
// preset's view), plan-district.png (zoomed on the featured district, lots drawn), plan-centre.png (big cities: the core at 0.75 px per block, lots drawn), plan-height.png (cities and metropolises) and
// fly-1..N.png (palette colours, fly camera stops round the featured district: two aerials, a street-level view along its longest street, the
// square or green when there is one, and for big cities a low skyline view from outside).
const ROOT = resolve(process.env['FB_CITY_OUT'] ?? 'out/city');
const OUT = resolve('out/gate-pack');
const FEATURED = JSON.parse(readFileSync('e2e/gate-pack-featured.json', 'utf8')) as Record<string, string>;
const pick = (process.env['FB_CITIES'] ?? '').split(',').filter((s) => s !== '');
const names = Object.keys(FEATURED).filter((n) => pick.length === 0 || pick.includes(n));

test.use({ viewport: { width: 1200, height: 800 } });
test.skip(process.env['GATE_PACK'] !== '1', 'gate pack captures run only with GATE_PACK=1');

interface Street { kind: string; width: number; points: number[][]; y: number[] }
interface Lot { x: number; z: number; w: number; d: number; front: string; status: string; storeys: number[] }
interface District { district: { x: number; z: number; w: number; d: number }; streets: Street[]; lots: Lot[]; plazas: { x: number; z: number; w: number; d: number; y: number }[] }
interface Terrain { x0: number; z0: number; nx: number; nz: number; floorY: number[] }

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

async function plan(page: Page, url: string, file: string): Promise<void> {
  await page.goto(url);
  await page.waitForFunction(() => { const r = window.__planReady; return r !== undefined && (!r.ok || r.pending === 0); }, undefined, { timeout: 120_000 });
  const ready = await page.evaluate(() => window.__planReady!);
  expect(ready.ok, ready.error).toBe(true);
  writeFileSync(file, await page.screenshot());
}

const r1 = (v: number): number => Math.round(v * 10) / 10;
/** A stop at (x, y, z) looking at (tx, ty, tz): yaw 0 looks toward -z, 90 toward -x; pitch > 0 looks up. */
function look(x: number, y: number, z: number, tx: number, ty: number, tz: number): string {
  const dx = tx - x, dz = tz - z, dy = ty - y;
  const yaw = (Math.atan2(-dx, -dz) * 180) / Math.PI;
  const pitch = (Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI;
  return [x, y, z, yaw, pitch].map(r1).join(',');
}

function stops(dist: District, t: Terrain, big: boolean): string[] {
  // frame the built lots (a hamlet fills a corner of its tile), back off further for tall buildings (about 4 blocks a storey)
  const built = dist.lots.filter((l) => l.status === 'build');
  const box = built.length === 0 ? dist.district : (() => {
    const x0 = Math.min(...built.map((l) => l.x)), z0 = Math.min(...built.map((l) => l.z));
    return { x: x0, z: z0, w: Math.max(...built.map((l) => l.x + l.w)) - x0, d: Math.max(...built.map((l) => l.z + l.d)) - z0 };
  })();
  const top = 4 * Math.max(3, ...built.map((l) => l.storeys[1] ?? 1));
  const ext = Math.max(48, box.w, box.d);
  const { x, z, w, d } = { x: box.x + box.w / 2 - ext / 2, z: box.z + box.d / 2 - ext / 2, w: ext, d: ext };
  const ground = (px: number, pz: number): number => {
    const i = Math.min(t.nx - 1, Math.max(0, Math.round(px - t.x0))), j = Math.min(t.nz - 1, Math.max(0, Math.round(pz - t.z0)));
    return t.floorY[j * t.nx + i] ?? 64;
  };
  const cx = x + w / 2, cz = z + d / 2, gy = ground(cx, cz);
  const out = [
    look(cx, gy + 0.8 * ext + 0.6 * top, z + d + 0.6 * ext + 0.5 * top, cx, gy + top / 4, cz),
    look(x + w + 0.4 * ext + 0.35 * top, gy + 0.55 * ext + 0.5 * top, z - 0.4 * ext - 0.35 * top, cx, gy + top / 4, cz),
  ];
  const len = (s: Street): number => s.points.slice(1).reduce((a, p, i) => a + Math.hypot(p[0]! - s.points[i]![0]!, p[1]! - s.points[i]![1]!), 0);
  // the street to walk: the one most lots front onto (front edge within reach of its centre line), then long and level
  const segDist = (px: number, pz: number, a: number[], b: number[]): number => {
    const vx = b[0]! - a[0]!, vz = b[1]! - a[1]!, l2 = vx * vx + vz * vz;
    const u = l2 === 0 ? 0 : Math.max(0, Math.min(1, ((px - a[0]!) * vx + (pz - a[1]!) * vz) / l2));
    return Math.hypot(px - a[0]! - u * vx, pz - a[1]! - u * vz);
  };
  const fronts = dist.lots.filter((l) => l.status === 'build').map((l) => (l.front === 'south' ? [l.x + l.w / 2, l.z + l.d] : l.front === 'north' ? [l.x + l.w / 2, l.z] : l.front === 'east' ? [l.x + l.w, l.z + l.d / 2] : [l.x, l.z + l.d / 2]));
  const frontage = (s: Street): number => fronts.filter(([px, pz]) => s.points.slice(1).some((b, i) => segDist(px!, pz!, s.points[i]!, b) <= s.width / 2 + 3)).length;
  const score = (s: Street): number => 10 * frontage(s) + len(s) / 4 - 2 * (Math.max(...s.y) - Math.min(...s.y));
  const street = dist.streets.filter((s) => s.points.length >= 2 && len(s) > 24).sort((a, b) => score(b) - score(a))[0];
  if (street !== undefined) {
    const n = street.points.length, a = street.points[Math.floor(n / 5)]!, b = street.points[Math.min(n - 1, Math.floor((4 * n) / 5))]!;
    const ay = street.y[Math.floor(n / 5)] ?? gy, by = street.y[Math.min(n - 1, Math.floor((4 * n) / 5))] ?? gy;
    if (Math.hypot(b[0]! - a[0]!, b[1]! - a[1]!) > 8) out.push(look(a[0]!, ay + 9, a[1]!, b[0]!, by + 4, b[1]!));
  }
  const p = dist.plazas[0];
  if (p !== undefined) {
    const px = p.x + p.w / 2, pz = p.z + p.d / 2;
    out.push(look(px - p.w / 2 - 28, p.y + 24, pz + p.d / 2 + 28, px, p.y, pz));
  }
  if (big) out.push(look(cx, gy + 20, z + d + Math.max(150, 0.9 * top), cx, gy + 0.45 * top, cz));
  return out;
}

for (const name of names) {
  const id = FEATURED[name]!;
  const dir = join(ROOT, name);
  test.describe(name, () => {
    test.skip(!existsSync(join(dir, 'scenes', `${id}.json`)), `no scene ${id} in ${dir}`);
    test('plans and fly-through', async ({ page }) => {
      test.setTimeout(600_000);
      const errors: string[] = [];
      page.on('pageerror', (e) => errors.push(String(e)));
      const out = join(OUT, name);
      mkdirSync(out, { recursive: true });
      const p = parseCityPreset(name, JSON.parse(readFileSync(`city-presets/${name}.json`, 'utf8')));
      const s = JSON.parse(readFileSync(join(dir, 'settlement.json'), 'utf8')) as { site: { tier: string; centre: number[] } };
      const big = s.site.tier === 'city' || s.site.tier === 'metropolis';
      const base = await serve(page, name);
      const q = p.camera.plan;
      const view = `${q.x !== undefined && q.z !== undefined ? `&x=${q.x}&z=${q.z}` : ''}${q.zoom !== undefined ? `&zoom=${q.zoom}` : ''}`;
      const terr = `&terrain=${base}terrain.json.gz`;
      await plan(page, `/plan.html?city=${base}${terr}&layers=${encodeURIComponent(q.layers ?? '+terrain')}${view}`, join(out, 'plan.png'));
      const dist = JSON.parse(readFileSync(join(dir, 'districts', `${id}.json`), 'utf8')) as District;
      const t = dist.district;
      const zoom = Math.min(4, Math.floor((700 / Math.max(t.w, t.d)) * 4) / 4);
      await plan(page, `/plan.html?city=${base}${terr}&layers=${encodeURIComponent('+terrain')}&x=${t.x + t.w / 2}&z=${t.z + t.d / 2}&zoom=${zoom}`, join(out, 'plan-district.png'));
      // the centre at 0.75 px per block: lots are drawn from 0.5, so this shows the core's lots in a big city
      if (big) await plan(page, `/plan.html?city=${base}${terr}&layers=${encodeURIComponent('+terrain')}&x=${s.site.centre[0]}&z=${s.site.centre[1]}&zoom=0.75`, join(out, 'plan-centre.png'));
      if (big) await plan(page, `/plan.html?city=${base}${terr}&layers=${encodeURIComponent('+height,-terrain')}${view}`, join(out, 'plan-height.png'));
      const body = readFileSync(join(dir, 'scenes', `${id}.json`), 'utf8');
      const scene = JSON.parse(body) as { terrain: Terrain; violations: unknown[] };
      expect(scene.violations).toEqual([]);
      await page.route((u) => u.pathname === `/data/${id}.json`, (r) => r.fulfill({ body, contentType: 'application/json' }));
      const st = stops(dist, scene.terrain, big);
      writeFileSync(join(out, 'stops.txt'), `${st.join('\n')}\n`);
      await page.goto(`/index.html?ir=/data/${id}.json&camera=fly&exterior=1&colour=palette&stops=${st.join(';')}`);
      await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 300_000 });
      const ready = await page.evaluate(() => window.__viewerReady!);
      expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
      await page.addStyleTag({ content: 'body > :not(canvas) { display: none !important; }' }); // the controls panel off the pictures
      for (let i = 0; i < st.length; i++) {
        if (i > 0) { await page.keyboard.press(']'); await page.waitForTimeout(1500); }
        writeFileSync(join(out, `fly-${i + 1}.png`), await page.screenshot());
      }
      expect(errors).toEqual([]);
    });
  });
}
