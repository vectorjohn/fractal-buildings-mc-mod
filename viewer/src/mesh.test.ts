import { describe, expect, it } from 'vitest';
import { Occupancy, exposedFaces, groundCells, terrainMesh, terrainTops, waterMesh } from './mesh';
import type { Cell } from './mesh';
import type { Scene } from './ir';

function faces(cells: Cell[], extraSolid: Cell[] = []): number {
  const occ = new Occupancy([-5, -5, -5], [10, 10, 10]);
  for (const c of [...cells, ...extraSolid]) occ.add(c.x, c.y, c.z);
  return exposedFaces({ cells, colour: () => 0xffffff }, (x, y, z) => occ.has(x, y, z)).quads;
}
const cube = (n: number): Cell[] => {
  const out: Cell[] = [];
  for (let y = 0; y < n; y++) for (let z = 0; z < n; z++) for (let x = 0; x < n; x++) out.push({ x, y, z });
  return out;
};

describe('exposed faces', () => {
  it('one voxel has 6 faces, two touching have 10', () => {
    expect(faces([{ x: 0, y: 0, z: 0 }])).toBe(6);
    expect(faces([{ x: 0, y: 0, z: 0 }, { x: 1, y: 0, z: 0 }])).toBe(10);
  });
  it('a closed 3x3x3 cube has 54 faces and its interior voxel adds none', () => {
    expect(faces(cube(3))).toBe(54);
    expect(faces(cube(3).filter((c) => !(c.x === 1 && c.y === 1 && c.z === 1)))).toBe(54 + 6); // hollow: the hole's 6 faces show
  });
  it('a neighbour from another layer hides the shared face', () => {
    expect(faces([{ x: 0, y: 0, z: 0 }], [{ x: 0, y: -1, z: 0 }])).toBe(5);
  });
  it('winding: every face normal agrees with its triangle', () => {
    const occ = new Occupancy([0, 0, 0], [1, 1, 1]);
    occ.add(0, 0, 0);
    const m = exposedFaces({ cells: [{ x: 0, y: 0, z: 0 }], colour: () => 0 }, (x, y, z) => occ.has(x, y, z));
    for (let t = 0; t < m.indices.length; t += 3) {
      const p = (k: number) => [0, 1, 2].map((a) => m.positions[m.indices[t + k]! * 3 + a]!);
      const [a, b, c] = [p(0), p(1), p(2)] as [number[], number[], number[]];
      const e1 = b.map((v, i) => v - a[i]!), e2 = c.map((v, i) => v - a[i]!);
      const cr = [e1[1]! * e2[2]! - e1[2]! * e2[1]!, e1[2]! * e2[0]! - e1[0]! * e2[2]!, e1[0]! * e2[1]! - e1[1]! * e2[0]!];
      const n = [0, 1, 2].map((a2) => m.normals[m.indices[t]! * 3 + a2]!);
      expect(cr[0]! * n[0]! + cr[1]! * n[1]! + cr[2]! * n[2]!).toBeGreaterThan(0);
    }
  });
});

function scene(nx: number, nz: number, floorY: number[], extra: Partial<Scene> = {}): Scene {
  return {
    format: 'fb-scene/1', genVersion: 1, seed: '1', terrain: { x0: 10, z0: 20, nx, nz, floorY }, lots: [], buildings: [],
    ground: [], violations: [], ...extra,
  };
}

describe('ground and terrain', () => {
  it('ground boxes apply in order; air clears', () => {
    const cells = groundCells([
      { min: [0, 0, 0], size: [3, 1, 1], role: 'road', layer: 'ground', storey: -1 },
      { min: [1, 0, 0], size: [1, 1, 1], role: 'air', layer: 'ground', storey: -1 },
      { min: [2, 0, 0], size: [1, 1, 1], role: 'paving', layer: 'ground', storey: -1 },
    ]);
    expect(cells.map((c) => `${c.x}:${c.role}`)).toEqual(['0:road', '2:paving']);
  });
  it('an air ground box lowers the terrain column it covers, and only that one', () => {
    const s = scene(2, 1, [60, 60], { ground: [{ min: [10, 57, 20], size: [1, 3, 1], role: 'air', layer: 'ground', storey: -1 }] });
    expect(terrainTops(s)).toEqual([57, 60]);
    // a cut above the surface changes nothing
    const s2 = scene(2, 1, [60, 60], { ground: [{ min: [10, 61, 20], size: [1, 3, 1], role: 'air', layer: 'ground', storey: -1 }] });
    expect(terrainTops(s2)).toEqual([60, 60]);
  });
  it('terrain faces: flat 2x2 is 4 tops + 8 edge skirts; a step adds a side', () => {
    const flat = scene(2, 2, [5, 5, 5, 5]);
    expect(terrainMesh(flat, terrainTops(flat), () => 0).quads).toBe(4 + 8);
    const step = scene(2, 1, [5, 4]);
    // 2 tops + skirts (2 columns x 3 outward sides = 6) + the step side between them (1)
    expect(terrainMesh(step, terrainTops(step), () => 0).quads).toBe(2 + 6 + 1);
  });
  it('a covered column loses its top quad', () => {
    const s = scene(1, 1, [5]);
    expect(terrainMesh(s, [5], () => 0, (x, y, z) => x === 10 && y === 4 && z === 20).quads).toBe(4);
  });
  it('water merges runs of equal level and skips dry columns', () => {
    const s = scene(4, 1, [5, 5, 5, 5]);
    s.terrain!.water = [2, 2, 0, 3];
    const m = waterMesh(s, 0x3060c0);
    expect(m.quads).toBe(2);
    expect(Array.from(m.positions.slice(1, 2))).toEqual([7]); // surface = floorY + depth
    expect(waterMesh(scene(1, 1, [5]), 0).quads).toBe(0);
  });
});
