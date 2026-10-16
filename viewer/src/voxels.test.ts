import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { parseScene } from './ir';
import type { Building, Scene } from './ir';
import { buildingVoxels, placeColumn, sceneVoxels, terrainVoxels } from './voxels';

const sample = (): Scene => parseScene(JSON.parse(readFileSync(new URL('../fixtures/sample-house.json', import.meta.url), 'utf8')));

describe('sceneVoxels (sample house)', () => {
  const vs = sceneVoxels(sample());
  it('has 196 voxels with the per-role counts', () => {
    expect(vs).toHaveLength(196);
    const counts: Record<string, number> = {};
    for (const v of vs) counts[v.role] = (counts[v.role] ?? 0) + 1;
    expect(counts).toEqual({ foundation: 14, floor: 70, wall: 69, roof: 35, door: 4, window: 4 });
  });
  it('is sorted (building, y, z, x) and deterministic', () => {
    const key = (v: { building: number; y: number; z: number; x: number }) => [v.building, v.y, v.z, v.x];
    for (let i = 1; i < vs.length; i++) expect(cmp(key(vs[i - 1]!), key(vs[i]!))).toBeLessThan(0);
    expect(sceneVoxels(sample())).toEqual(vs);
  });
  it('places the footprint at the origin and the foundation below it', () => {
    expect(Math.min(...vs.map((v) => v.x))).toBe(100);
    expect(Math.max(...vs.map((v) => v.x))).toBe(106);
    expect(Math.min(...vs.map((v) => v.y))).toBe(63);
    expect(vs.filter((v) => v.role === 'foundation').every((v) => v.y === 63)).toBe(true);
  });
  it('air clears and later boxes win', () => {
    const s = sample();
    const b: Building = s.buildings[0]!;
    b.siteBoxes.push({ min: [0, 0, 0], size: [7, 1, 5], role: 'air', layer: 'shell', storey: 0 });
    const n = buildingVoxels(b, 0).filter((v) => v.y === 64).length;
    expect(n).toBe(0);
  });
});

function cmp(a: number[], b: number[]): number {
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return a[i]! - b[i]!;
  return 0;
}

describe('placeColumn (formulas from Placement.java, 5x3 footprint at origin (10, 20))', () => {
  // bounds extent sx=5, sz=3; corners (0,0) (4,0) (0,2) (4,2)
  const corners: [number, number][] = [[0, 0], [4, 0], [0, 2], [4, 2]];
  const expected: Record<number, [number, number][]> = {
    0: [[10, 20], [14, 20], [10, 22], [14, 22]],
    1: [[12, 20], [12, 24], [10, 20], [10, 24]],
    2: [[14, 22], [10, 22], [14, 20], [10, 20]],
    3: [[10, 24], [10, 20], [12, 24], [12, 20]],
  };
  for (const r of [0, 1, 2, 3]) {
    it(`rotation ${r}`, () => {
      expect(corners.map(([lx, lz]) => placeColumn(r, 10, 20, 5, 3, lx, lz))).toEqual(expected[r]);
    });
  }
});

describe('terrainVoxels', () => {
  it('uses floorY - 1 as the top block', () => {
    const t = terrainVoxels(sample());
    expect(t).toHaveLength(99);
    expect(t[0]).toEqual({ x: 98, y: 62, z: -42 });
  });
});
