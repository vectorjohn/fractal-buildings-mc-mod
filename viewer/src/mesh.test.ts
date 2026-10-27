import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { Occupancy, exposedFaces, groundCells, terrainMesh, terrainTops, waterMesh } from './mesh';
import type { Cell } from './mesh';
import { parseScene } from './ir';
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

describe('cuts: footprint and areaway lower the terrain (SHOPLOW S1v)', () => {
  // fixtures/shoplow/cut-fixture.json (scripts/gen-cut-fixture.mjs): terrain 64 north of z 5 rising 1 a row to 68; a shop
  // 8x8 at origin (6, 64, 6), its slab at the street level, a 1-wide areaway of air boxes + paving round the sides and back.
  const load = (): Scene => parseScene(JSON.parse(readFileSync(new URL('../fixtures/shoplow/cut-fixture.json', import.meta.url), 'utf8')));
  const at = (tops: number[], s: Scene, x: number, z: number): number => tops[(z - s.terrain!.z0) * s.terrain!.nx + (x - s.terrain!.x0)]!;

  it('no terrain above the slab inside the footprint; the areaway columns drop to the slab; beyond them it is untouched', () => {
    const s = load(), tops = terrainTops(s);
    for (let z = 6; z < 14; z++) for (let x = 6; x < 14; x++) expect(at(tops, s, x, z)).toBe(64);
    for (let z = 5; z < 15; z++) for (const x of [5, 14]) expect(at(tops, s, x, z)).toBe(64); // side ring
    for (let x = 6; x < 14; x++) expect(at(tops, s, x, 14)).toBe(64); // back ring
    expect(at(tops, s, 4, 12)).toBe(68); // outside the ring: the natural cut face
    expect(at(tops, s, 15, 9)).toBe(68);
    expect(at(tops, s, 10, 15)).toBe(68);
    expect(at(tops, s, 10, 2)).toBe(64);
    // a raw column that was higher in the footprint did get lowered
    expect(at(s.terrain!.floorY, s, 10, 12)).toBe(68);
  });
  it('an air box that ends at the top block (S2: y range [slab - 1, floorY), exclusive top = floorY) cuts like one that ends a block above', () => {
    const s = load(), full = terrainTops(s);
    const trimmed = { ...s, buildings: s.buildings.map((b) => ({ ...b, siteBoxes: b.siteBoxes.map((q) => q.role === 'air' ? { ...q, size: [q.size[0], q.size[1] - 1, q.size[2]] as [number, number, number] } : q) })) };
    const t = terrainTops(trimmed);
    for (let z = 5; z < 15; z++) for (const x of [5, 14]) expect(at(t, s, x, z)).toBe(at(full, s, x, z));
  });
  it('without the building cuts the same columns keep their earth (the mutation)', () => {
    const s = load();
    const bare = terrainTops({ ...s, buildings: s.buildings.map((b) => ({ ...b, siteBoxes: [], shell: { ...b.shell, volumes: [] } })) });
    expect(at(bare, s, 10, 12)).toBe(68);
    expect(at(bare, s, 5, 12)).toBe(68);
  });
  it('the cut follows the building rotation', () => {
    const s = load();
    const b = s.buildings[0]!;
    // origin (6,6), local extent 8x8; rotate 2: the back ring (local z 8) becomes world z 5, which is street level
    // (raw 64, not spanning), and the front ring (local z -1) lands on world z 14, which is earth.
    const r = terrainTops({ ...s, buildings: [{ ...b, rotation: 2 }] });
    expect(at(r, s, 10, 5)).toBe(64);
    // air box local z -1..8 on x -1 and x 8 are symmetric; the front-ring paving is not air, so z 14 stays high
    expect(at(r, s, 10, 14)).toBe(68);
  });
  it('a building whose slab is at or above the ground changes nothing', () => {
    const s = load();
    const b = s.buildings[0]!;
    const raised = { ...b, origin: [6, 80, 6] as [number, number, number], siteBoxes: [] };
    expect(terrainTops({ ...s, buildings: [raised] })).toEqual(s.terrain!.floorY);
  });
  it('the terrain mesh has no top quad in the footprint above the slab', () => {
    const s = load(), tops = terrainTops(s);
    const m = terrainMesh(s, tops, () => 0);
    let above = 0;
    for (let i = 0; i < m.positions.length; i += 3) {
      const x = m.positions[i]!, y = m.positions[i + 1]!, z = m.positions[i + 2]!;
      if (x > 6 && x < 14 && z > 6 && z < 14 && y > 64) above++;
    }
    expect(above).toBe(0);
  });
});
