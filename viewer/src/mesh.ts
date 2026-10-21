// Exposed-face meshing for big scenes (district view). Pure: typed arrays out, no three.js, so it is unit-testable.
// A face is emitted only between a solid voxel and a non-solid neighbour. The instanced path (render.ts) stays for
// small scenes; this one is for scenes above FACE_MESH_THRESHOLD voxels or `?mesh=faces`.
import type { Box, Scene } from './ir';

/** Voxel count above which a scene is meshed as faces instead of instanced cubes. */
export const FACE_MESH_THRESHOLD = 200_000;

export interface Cell { x: number; y: number; z: number }

/** Indexed triangle soup: 4 vertices and 6 indices per quad. */
export interface MeshData {
  positions: Float32Array; normals: Float32Array; colours: Float32Array; indices: Uint32Array;
  quads: number;
}

/** Accumulates quads; vertex colour is linear-ish rgb 0..1 as three.js wants for `vertexColors`. */
export class QuadBuilder {
  private pos: number[] = [];
  private nor: number[] = [];
  private col: number[] = [];
  quads = 0;
  /** Quad corners in counter-clockwise order seen from the normal side. */
  add(c: readonly number[], n: readonly [number, number, number], rgb: number): void {
    const r = ((rgb >> 16) & 255) / 255, g = ((rgb >> 8) & 255) / 255, b = (rgb & 255) / 255;
    for (let k = 0; k < 4; k++) {
      this.pos.push(c[k * 3]!, c[k * 3 + 1]!, c[k * 3 + 2]!);
      this.nor.push(n[0], n[1], n[2]);
      this.col.push(r, g, b);
    }
    this.quads++;
  }
  build(): MeshData {
    const indices = new Uint32Array(this.quads * 6);
    for (let q = 0; q < this.quads; q++) {
      const v = q * 4;
      indices.set([v, v + 1, v + 2, v, v + 2, v + 3], q * 6);
    }
    return {
      positions: new Float32Array(this.pos), normals: new Float32Array(this.nor), colours: new Float32Array(this.col),
      indices, quads: this.quads,
    };
  }
}

type Dir = { n: [number, number, number]; d: [number, number, number]; corners: number[] };
// For direction d: the quad corners of the face on that side of the unit cube at the origin, counter-clockwise from outside.
const DIRS: Dir[] = [
  { n: [1, 0, 0], d: [1, 0, 0], corners: [1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1] },
  { n: [-1, 0, 0], d: [-1, 0, 0], corners: [0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0] },
  { n: [0, 1, 0], d: [0, 1, 0], corners: [0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0] },
  { n: [0, -1, 0], d: [0, -1, 0], corners: [0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1] },
  { n: [0, 0, 1], d: [0, 0, 1], corners: [0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1] },
  { n: [0, 0, -1], d: [0, 0, -1], corners: [1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0] },
];

/** Dense-ish occupancy index over an integer box: numeric keys, no string building. */
export class Occupancy {
  private dy: number; private dz: number;
  constructor(private min: [number, number, number], max: [number, number, number], private readonly cells = new Set<number>()) {
    this.dy = max[1] - min[1] + 3; this.dz = max[2] - min[2] + 3;
  }
  private key(x: number, y: number, z: number): number {
    return ((x - this.min[0] + 1) * this.dz + (z - this.min[2] + 1)) * this.dy + (y - this.min[1] + 1);
  }
  add(x: number, y: number, z: number): void { this.cells.add(this.key(x, y, z)); }
  has(x: number, y: number, z: number): boolean { return this.cells.has(this.key(x, y, z)); }
}

export interface FaceSource { cells: readonly Cell[]; colour(i: number): number }

/**
 * Faces of `source.cells` that touch a cell for which `solid` is false. `solid` must be true for every cell in
 * `cells` (and may be true for more: terrain, other layers). Returns one MeshData per call.
 */
export function exposedFaces(source: FaceSource, solid: (x: number, y: number, z: number) => boolean): MeshData {
  const qb = new QuadBuilder();
  source.cells.forEach((c, i) => {
    for (const dir of DIRS) {
      if (solid(c.x + dir.d[0], c.y + dir.d[1], c.z + dir.d[2])) continue;
      const k = dir.corners;
      const corners = [
        c.x + k[0]!, c.y + k[1]!, c.z + k[2]!, c.x + k[3]!, c.y + k[4]!, c.z + k[5]!,
        c.x + k[6]!, c.y + k[7]!, c.z + k[8]!, c.x + k[9]!, c.y + k[10]!, c.z + k[11]!,
      ];
      qb.add(corners, dir.n, source.colour(i));
    }
  });
  return qb.build();
}

/** Ground boxes applied in list order (later wins, `air` clears) to world cells, sorted (y, z, x) for stable output. */
export function groundCells(boxes: readonly Box[]): { x: number; y: number; z: number; role: Box['role'] }[] {
  const map = new Map<string, { x: number; y: number; z: number; role: Box['role'] }>();
  for (const b of boxes) {
    for (let y = 0; y < b.size[1]; y++)
      for (let z = 0; z < b.size[2]; z++)
        for (let x = 0; x < b.size[0]; x++) {
          const wx = b.min[0] + x, wy = b.min[1] + y, wz = b.min[2] + z;
          const k = `${wx},${wy},${wz}`;
          if (b.role === 'air') map.delete(k); else map.set(k, { x: wx, y: wy, z: wz, role: b.role });
        }
  }
  return [...map.values()].sort((a, b) => a.y - b.y || a.z - b.z || a.x - b.x);
}

/**
 * Effective terrain top per column (exclusive y of the topmost solid block, i.e. `floorY`) after ground `air` boxes
 * cut it: a cut lowers a column to the cut's lowest y. Other ground boxes do not change it (they are voxels on top).
 */
export function terrainTops(scene: Scene): number[] {
  const t = scene.terrain;
  if (!t) return [];
  const tops = t.floorY.slice();
  for (const b of scene.ground) {
    if (b.role !== 'air') continue;
    for (let z = b.min[2]; z < b.min[2] + b.size[2]; z++)
      for (let x = b.min[0]; x < b.min[0] + b.size[0]; x++) {
        if (x < t.x0 || z < t.z0 || x >= t.x0 + t.nx || z >= t.z0 + t.nz) continue;
        const i = (z - t.z0) * t.nx + (x - t.x0);
        if (b.min[1] < tops[i]!) tops[i] = b.min[1];
      }
  }
  return tops;
}

/** How far the terrain skirt drops below the lowest column at the grid edge. */
export const SKIRT = 3;

/**
 * Terrain heightfield: a top quad per column at `tops`, side quads where a neighbour is lower (down to its top; the
 * grid edge drops to the lowest column minus SKIRT). A column's top quad is skipped when `covered(x, y, z)` says the
 * block below it is taken by a ground voxel (the road is the surface there, not coplanar terrain).
 */
export function terrainMesh(
  scene: Scene, tops: readonly number[], colour: (x: number, z: number) => number,
  covered: (x: number, y: number, z: number) => boolean = () => false,
): MeshData {
  const t = scene.terrain;
  const qb = new QuadBuilder();
  if (!t) return qb.build();
  const lowest = minOf(tops) - SKIRT;
  const topAt = (i: number, j: number): number => (i < 0 || j < 0 || i >= t.nx || j >= t.nz ? lowest : tops[j * t.nx + i]!);
  for (let j = 0; j < t.nz; j++)
    for (let i = 0; i < t.nx; i++) {
      const x = t.x0 + i, z = t.z0 + j, h = tops[j * t.nx + i]!, rgb = colour(x, z);
      if (!covered(x, h - 1, z)) qb.add([x, h, z + 1, x + 1, h, z + 1, x + 1, h, z, x, h, z], [0, 1, 0], rgb);
      for (const dir of DIRS) {
        if (dir.n[1] !== 0) continue;
        const nh = topAt(i + dir.d[0], j + dir.d[2]);
        if (nh >= h) continue;
        const k = dir.corners;
        // The side quad spans y 0..1 in the unit cube; stretch it from nh to h.
        const c: number[] = [];
        for (let q = 0; q < 4; q++) c.push(x + k[q * 3]!, k[q * 3 + 1]! === 0 ? nh : h, z + k[q * 3 + 2]!);
        qb.add(c, dir.n, rgb);
      }
    }
  return qb.build();
}

/**
 * Water surface: translucent quads at `floorY + depth` over every column with depth > 0, merged into runs along x
 * of equal level.
 */
export function waterMesh(scene: Scene, rgb: number): MeshData {
  const t = scene.terrain;
  const qb = new QuadBuilder();
  if (!t?.water) return qb.build();
  const level = (i: number, j: number): number => {
    const d = t.water![j * t.nx + i]!;
    return d > 0 ? t.floorY[j * t.nx + i]! + d : -1;
  };
  for (let j = 0; j < t.nz; j++) {
    let i = 0;
    while (i < t.nx) {
      const y = level(i, j);
      if (y < 0) { i++; continue; }
      let e = i + 1;
      while (e < t.nx && level(e, j) === y) e++;
      const x0 = t.x0 + i, x1 = t.x0 + e, z = t.z0 + j;
      qb.add([x0, y, z + 1, x1, y, z + 1, x1, y, z, x0, y, z], [0, 1, 0], rgb);
      i = e;
    }
  }
  return qb.build();
}

/** Min/max of a big array (spread would overflow the call stack). */
export function minOf(a: readonly number[]): number { let m = Infinity; for (const v of a) if (v < m) m = v; return m; }
export function maxOf(a: readonly number[]): number { let m = -Infinity; for (const v of a) if (v > m) m = v; return m; }
