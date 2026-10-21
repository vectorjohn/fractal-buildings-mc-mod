import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import {
  DEFAULT_LAYERS, LOT_SCALE, MAX_SCALE, MIN_SCALE, baseLayers, coarseRaster, districtAt, districtsToLoad, fitView, hillshade, lotAt,
  parseLayers, showLots, toScreen, toWorld, visibleDistricts, zoomAt, pan, layersParam,
} from './planDraw';
import { parseDistrict, parseSettlement } from './settlement';

const dir = 'fixtures/settlement/sample';
const s = parseSettlement(readFileSync(`${dir}/settlement.json`, 'utf8'));
const size = { w: 800, h: 600 };

describe('view transform', () => {
  it('toScreen and toWorld are inverse', () => {
    const v = { x0: -120, z0: 77, scale: 0.37 };
    const [px, py] = toScreen(v, 301, -15);
    const [x, z] = toWorld(v, px, py);
    expect(x).toBeCloseTo(301, 9);
    expect(z).toBeCloseTo(-15, 9);
  });
  it('zoomAt keeps the point under the cursor fixed and clamps', () => {
    const v = fitView(s.bounds, size);
    const before = toWorld(v, 200, 150);
    const z = zoomAt(v, 200, 150, 3);
    const after = toWorld(z, 200, 150);
    expect(after[0]).toBeCloseTo(before[0], 6);
    expect(after[1]).toBeCloseTo(before[1], 6);
    expect(zoomAt(v, 0, 0, 1e9).scale).toBe(MAX_SCALE);
    expect(zoomAt(v, 0, 0, 1e-9).scale).toBe(MIN_SCALE);
  });
  it('pan moves the world opposite to the drag', () => {
    const v = pan({ x0: 0, z0: 0, scale: 2 }, 10, -20);
    expect(v).toEqual({ x0: -5, z0: 10, scale: 2 });
  });
  it('fitView shows the whole plan', () => {
    const v = fitView(s.bounds, size);
    const [x1, z1] = toScreen(v, s.bounds.x + s.bounds.w, s.bounds.z + s.bounds.d);
    const [x0, z0] = toScreen(v, s.bounds.x, s.bounds.z);
    expect(x0).toBeGreaterThanOrEqual(0); expect(z0).toBeGreaterThanOrEqual(0);
    expect(x1).toBeLessThanOrEqual(size.w); expect(z1).toBeLessThanOrEqual(size.h);
  });
});

describe('level of detail', () => {
  it('shows lots from 1/2 px per block', () => {
    expect(LOT_SCALE).toBe(0.5);
    expect(showLots(0.49)).toBe(false);
    expect(showLots(0.5)).toBe(true);
  });
  it('loads nothing in the overview and only visible districts in detail', () => {
    expect(districtsToLoad(s, { x0: 0, z0: 0, scale: 0.1 }, size)).toEqual([]);
    const near = districtsToLoad(s, { x0: 0, z0: 0, scale: 1 }, { w: 100, h: 100 });
    expect(near.map((d) => d.id)).toEqual(['d0_0']);
    const all = districtsToLoad(s, { x0: -10, z0: -10, scale: 1 }, { w: 400, h: 400 });
    expect(all).toHaveLength(4);
  });
  it('skips districts without a detail file', () => {
    const s2 = { ...s, districts: s.districts.map((d, i) => (i === 0 ? { ...d, detail: null } : d)) };
    expect(districtsToLoad(s2, { x0: 0, z0: 0, scale: 1 }, { w: 100, h: 100 })).toEqual([]);
    expect(visibleDistricts(s2, { x0: 0, z0: 0, scale: 1 }, { w: 100, h: 100 })).toHaveLength(1);
  });
});

describe('layers', () => {
  it('defaults, explicit lists, edits and errors', () => {
    expect([...parseLayers(null)]).toEqual([...DEFAULT_LAYERS]);
    expect([...parseLayers('roads,lots')]).toEqual(['roads', 'lots']);
    const e = parseLayers('+density,-lots');
    expect(e.has('density')).toBe(true);
    expect(e.has('lots')).toBe(false);
    expect(e.has('roads')).toBe(true);
    expect(parseLayers(' density').has('density')).toBe(true); // '+' decoded by URLSearchParams
    expect(() => parseLayers('roads,nope')).toThrow(/unknown layer "nope"/);
    expect(layersParam(parseLayers('lots,roads'))).toBe('roads,lots');
  });
});

describe('rasters and hit tests', () => {
  it('coarse raster has one pixel per lattice column, water blue, hillshade 1 on flat ground', () => {
    const r = coarseRaster(s, baseLayers(parseLayers('terrain,water,biome')));
    expect(r.w * r.h * 4).toBe(r.data.length);
    expect(r.w).toBe(s.coarse.nx);
    const k = s.coarse.water.findIndex((w) => w >= 2);
    expect(k).toBeGreaterThanOrEqual(0);
    expect(r.data[k * 4 + 2]!).toBeGreaterThan(r.data[k * 4]!); // blue over red
    expect(hillshade([5, 5, 5, 5], 2, 2, 0, 0, 32)).toBe(1);
  });
  it('quarters and density layers change pixels', () => {
    const a = coarseRaster(s, baseLayers(parseLayers('terrain')));
    const b = coarseRaster(s, baseLayers(parseLayers('terrain,quarters,density')));
    expect(Array.from(a.data)).not.toEqual(Array.from(b.data));
  });
  it('finds the district and lot under a world point', () => {
    expect(districtAt(s, 10, 10)?.id).toBe('d0_0');
    expect(districtAt(s, 5000, 5000)).toBeNull();
    const d = parseDistrict(readFileSync(`${dir}/districts/d0_0.json`, 'utf8'));
    const l = d.lots[0]!;
    expect(lotAt(d, l.x + 1, l.z + 1)?.id).toBe(l.id);
    expect(lotAt(d, l.x + l.w, l.z)?.id).not.toBe(l.id);
  });
});
