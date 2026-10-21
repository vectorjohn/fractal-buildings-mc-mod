import { readFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { describe, expect, it } from 'vitest';
import { decodeBytes } from './regionmap';
import { joinUrl, parseDistrict, parseSettlement } from './settlement';

const dir = 'fixtures/settlement/sample';
const read = (p: string): string => readFileSync(`${dir}/${p}`, 'utf8');
const settlement = (): Record<string, unknown> => JSON.parse(read('settlement.json')) as Record<string, unknown>;
const district = (): Record<string, unknown> => JSON.parse(read('districts/d0_0.json')) as Record<string, unknown>;

describe('fb-settlement/1', () => {
  it('parses the sample fixture', () => {
    const s = parseSettlement(read('settlement.json'));
    expect(s.districts.map((d) => d.id)).toEqual(['d0_0', 'd1_0', 'd0_1', 'd1_1']);
    expect(s.coarse.floorY).toHaveLength(s.coarse.nx * s.coarse.nz);
    expect(s.roads[0]!.bridges).toEqual([[1, 2]]);
    expect(s.gates[0]!.cells).toBeNull();
    expect(s.seed).toBe('4242');
  });
  it('round-trips through JSON', () => {
    const s = parseSettlement(read('settlement.json'));
    expect(parseSettlement(JSON.stringify({ format: 'fb-settlement/1', planVersion: 1, ...s2raw(s) })).districts).toHaveLength(4);
  });
  const bad: [string, (o: Record<string, unknown>) => void, RegExp][] = [
    ['wrong format', (o) => { o['format'] = 'fb-scene/1'; }, /\$\.format/],
    ['wrong plan version', (o) => { o['planVersion'] = 2; }, /\$\.planVersion/],
    ['missing bounds', (o) => { delete o['bounds']; }, /\$\.bounds: missing/],
    ['short raster', (o) => { ((o['coarse'] as Record<string, unknown>)['floorY'] as number[]).pop(); }, /\$\.coarse\.floorY/],
    ['bad road kind', (o) => { ((o['roads'] as Record<string, unknown>[])[0]!)['kind'] = 'highway'; }, /\$\.roads\[0\]\.kind/],
    ['bridge out of range', (o) => { ((o['roads'] as Record<string, unknown>[])[0]!)['bridges'] = [[1, 9]]; }, /\$\.roads\[0\]\.bridges\[0\]/],
    ['district id mismatch', (o) => { ((o['districts'] as Record<string, unknown>[])[1]!)['id'] = 'd5_5'; }, /\$\.districts\[1\]\.id/],
    ['duplicate district', (o) => { const ds = o['districts'] as Record<string, unknown>[]; ds[1] = { ...ds[0]! }; }, /duplicate/],
    ['non-integer point', (o) => { ((o['roads'] as { points: unknown[] }[])[0]!).points[0] = [1.5, 2]; }, /\$\.roads\[0\]\.points\[0\]/],
    ['seed not a string', (o) => { o['seed'] = 4242; }, /\$\.seed/],
  ];
  for (const [name, f, re] of bad) {
    it(`rejects ${name}`, () => {
      const o = settlement();
      f(o);
      expect(() => parseSettlement(JSON.stringify(o))).toThrow(re);
    });
  }
  it('ignores unknown keys', () => {
    const o = settlement();
    o['future'] = { a: 1 };
    expect(() => parseSettlement(JSON.stringify(o))).not.toThrow();
  });
});

function s2raw(s: ReturnType<typeof parseSettlement>): Record<string, unknown> {
  return { genVersion: s.genVersion, seed: s.seed, site: s.site, bounds: s.bounds, coarse: s.coarse, quarters: s.quarters, cells: s.cells,
    districts: s.districts, roads: s.roads, gates: s.gates, landmarks: s.landmarks, walls: s.walls, violations: s.violations };
}

describe('fb-district/1', () => {
  it('parses every district of the fixture', () => {
    for (const id of ['d0_0', 'd0_1', 'd1_0', 'd1_1']) {
      const d = parseDistrict(read(`districts/${id}.json`));
      expect(d.district.id).toBe(id);
      expect(d.lots.length).toBeGreaterThan(0);
    }
    expect(parseDistrict(read('districts/d0_0.json')).scene).toBe('scenes/d0_0.json');
  });
  it('rejects a lot outside its tile', () => {
    const o = district();
    ((o['lots'] as Record<string, unknown>[])[0]!)['x'] = 9999;
    expect(() => parseDistrict(JSON.stringify(o))).toThrow(/\$\.lots\[0\].*outside/);
  });
  it('rejects a bad front, status and street y length', () => {
    let o = district();
    ((o['lots'] as Record<string, unknown>[])[0]!)['front'] = 'up';
    expect(() => parseDistrict(JSON.stringify(o))).toThrow(/\$\.lots\[0\]\.front/);
    o = district();
    ((o['lots'] as Record<string, unknown>[])[0]!)['status'] = 'maybe';
    expect(() => parseDistrict(JSON.stringify(o))).toThrow(/status/);
    o = district();
    ((o['streets'] as { y: number[] }[])[0]!).y.pop();
    expect(() => parseDistrict(JSON.stringify(o))).toThrow(/\$\.streets\[0\]\.y/);
  });
  it('rejects a settlement file', () => {
    expect(() => parseDistrict(read('settlement.json'))).toThrow(/\$\.format/);
  });
});

describe('loading helpers', () => {
  it('decodes gzip by magic bytes, whatever the name', async () => {
    const raw = read('settlement.json');
    expect(await decodeBytes(new Uint8Array(gzipSync(raw)))).toBe(raw);
    expect(await decodeBytes(new TextEncoder().encode(raw))).toBe(raw);
  });
  it('joins urls', () => {
    expect(joinUrl('a/b/', '/c.json')).toBe('a/b/c.json');
    expect(joinUrl('a/b', 'districts/d.json')).toBe('a/b/districts/d.json');
  });
});
