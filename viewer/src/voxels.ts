import type { Building, Layer, Role, Scene, Vec3 } from './ir';

export interface Voxel {
  x: number; y: number; z: number; role: Role; layer: Layer; storey: number;
  /** Index into scene.buildings. */
  building: number;
}

/**
 * Local column (lx, lz) of a building whose bounds extent is (sx, sz) to world, exactly as Placement.java:
 * rotation r turns clockwise seen from above; the rotated footprint starts at origin in +x/+z.
 */
export function placeColumn(
  rotation: number, ox: number, oz: number, sx: number, sz: number, lx: number, lz: number,
): [number, number] {
  switch (rotation) {
    case 0: return [ox + lx, oz + lz];
    case 1: return [ox + sz - 1 - lz, oz + lx];
    case 2: return [ox + sx - 1 - lx, oz + sz - 1 - lz];
    case 3: return [ox + lz, oz + sx - 1 - lx];
    default: throw new Error(`bad rotation ${rotation}`);
  }
}

/** World voxels of one building: shell boxes then site boxes, later wins, `air` clears. Sorted (y, z, x). */
export function buildingVoxels(b: Building, index: number): Voxel[] {
  const boxes = [...b.shell.boxes, ...b.siteBoxes];
  if (boxes.length === 0) return [];
  const [ox, oy, oz] = b.origin;
  const [sx, , sz] = b.shell.bounds.size;
  // World bounds of all boxes, from the two corner columns of each (rotation maps boxes to boxes).
  let x0 = Infinity, z0 = Infinity, y0 = Infinity, x1 = -Infinity, z1 = -Infinity, y1 = -Infinity;
  for (const box of boxes) {
    if (box.size[0] <= 0 || box.size[1] <= 0 || box.size[2] <= 0) continue;
    const [ax, az] = placeColumn(b.rotation, ox, oz, sx, sz, box.min[0], box.min[2]);
    const [bx, bz] = placeColumn(b.rotation, ox, oz, sx, sz, box.min[0] + box.size[0] - 1, box.min[2] + box.size[2] - 1);
    x0 = Math.min(x0, ax, bx); x1 = Math.max(x1, ax, bx); z0 = Math.min(z0, az, bz); z1 = Math.max(z1, az, bz);
    y0 = Math.min(y0, oy + box.min[1]); y1 = Math.max(y1, oy + box.min[1] + box.size[1] - 1);
  }
  if (!isFinite(x0)) return [];
  const W = x1 - x0 + 1, D = z1 - z0 + 1, H = y1 - y0 + 1;
  const grid = new Int32Array(W * D * H); // box index + 1; 0 = empty
  boxes.forEach((box, bi) => {
    const [mx, my, mz] = box.min;
    const [bx, by, bz] = box.size;
    const val = box.role === 'air' ? 0 : bi + 1;
    for (let z = 0; z < bz; z++)
      for (let x = 0; x < bx; x++) {
        const [wx, wz] = placeColumn(b.rotation, ox, oz, sx, sz, mx + x, mz + z);
        const col = (wz - z0) * W + (wx - x0);
        for (let y = 0; y < by; y++) grid[(oy + my + y - y0) * W * D + col] = val;
      }
  });
  const out: Voxel[] = [];
  for (let y = 0; y < H; y++)
    for (let z = 0; z < D; z++)
      for (let x = 0; x < W; x++) {
        const v = grid[(y * D + z) * W + x]!;
        if (v === 0) continue;
        const box = boxes[v - 1]!;
        out.push({ x: x0 + x, y: y0 + y, z: z0 + z, role: box.role, layer: box.layer, storey: box.storey, building: index });
      }
  return out;
}

const voxelCache = new WeakMap<Scene, Voxel[]>();
/** All building voxels, sorted (building, y, z, x). Cached per scene object: do not mutate the result or the scene's boxes afterwards. */
export function sceneVoxels(scene: Scene): Voxel[] {
  let vs = voxelCache.get(scene);
  if (!vs) { vs = scene.buildings.flatMap((b, i) => buildingVoxels(b, i)); voxelCache.set(scene, vs); }
  return vs;
}

/** The top block of each terrain column (y = floorY - 1). */
export function terrainVoxels(scene: Scene): { x: number; y: number; z: number }[] {
  const t = scene.terrain;
  if (!t) return [];
  const out: { x: number; y: number; z: number }[] = [];
  for (let j = 0; j < t.nz; j++)
    for (let i = 0; i < t.nx; i++) out.push({ x: t.x0 + i, y: t.floorY[j * t.nx + i]! - 1, z: t.z0 + j });
  return out;
}

/** Axis-aligned world bounds [min, max) of the building voxels and terrain. */
export function sceneBounds(scene: Scene): { min: Vec3; max: Vec3 } {
  const min: Vec3 = [Infinity, Infinity, Infinity];
  const max: Vec3 = [-Infinity, -Infinity, -Infinity];
  const add = (v: { x: number; y: number; z: number }) => {
    min[0] = Math.min(min[0], v.x); min[1] = Math.min(min[1], v.y); min[2] = Math.min(min[2], v.z);
    max[0] = Math.max(max[0], v.x + 1); max[1] = Math.max(max[1], v.y + 1); max[2] = Math.max(max[2], v.z + 1);
  };
  sceneVoxels(scene).forEach(add);
  terrainVoxels(scene).forEach(add);
  if (!isFinite(min[0])) return { min: [0, 0, 0], max: [1, 1, 1] };
  return { min, max };
}

/** World bounds as {@link sceneBounds}, but building voxels above storey `maxStorey` (and foundations) do not count: the cutaway frame. */
export function cutBounds(scene: Scene, maxStorey: number): { min: Vec3; max: Vec3 } {
  const full = sceneBounds(scene);
  let top = -Infinity;
  for (const v of sceneVoxels(scene)) if (v.storey >= 0 && v.storey <= maxStorey) top = Math.max(top, v.y + 1);
  if (!isFinite(top)) return full;
  return { min: full.min, max: [full.max[0], Math.max(top, full.min[1] + 1), full.max[2]] };
}

/** World footprint centre (x, z) of a building (rotation 1 and 3 swap the extent). */
export function buildingCentre(b: Building): [number, number] {
  const [sx, , sz] = b.shell.bounds.size;
  const odd = b.rotation % 2 === 1;
  return [b.origin[0] + (odd ? sz : sx) / 2, b.origin[2] + (odd ? sx : sz) / 2];
}

/**
 * Style of the building nearest (footprint centre) to a world column, so a mixed district takes each style's palette
 * where its buildings are. `override` forces one style everywhere; one building (or none) is that building's style.
 */
export function styleLocator(scene: Scene, override?: string): (x: number, z: number) => string {
  const bs = scene.buildings;
  const fallback = override ?? bs[0]?.shell.spec.style ?? 'plain';
  if (override || bs.length <= 1) return () => fallback;
  const centres = bs.map(buildingCentre);
  const styles = bs.map((b) => b.shell.spec.style);
  if (styles.every((s) => s === styles[0])) return () => fallback;
  return (x, z) => {
    let best = 0, bd = Infinity;
    for (let i = 0; i < centres.length; i++) {
      const dx = centres[i]![0] - x - 0.5, dz = centres[i]![1] - z - 0.5, d = dx * dx + dz * dz;
      if (d < bd) { bd = d; best = i; }
    }
    return styles[best]!;
  };
}

/** Roles that take one style per scene (DS-30): street surfaces and bridge decks; the Java side is `Role.isCarriageway`. */
export const CARRIAGEWAY_ROLES: ReadonlySet<string> = new Set(['road', 'road_town', 'road_main', 'road_city', 'deck']);

/**
 * The one style the roads and bridge decks of a scene take: the most used style among its buildings (ties: the smallest id), as
 * `StyleAt.dominant` in the planner, so the surface never changes where the nearest building's style does. Tall, mass-timber and keep
 * styles do not vote (their deck is smooth stone, DS-30).
 */
export function dominantStyle(scene: Scene, override?: string): string {
  if (override) return override;
  const n = new Map<string, number>();
  for (const b of scene.buildings) if (!/^(tall|mass-timber|keep)/.test(b.shell.spec.style)) n.set(b.shell.spec.style, (n.get(b.shell.spec.style) ?? 0) + 1);
  let best = 'plain', bn = 0;
  for (const id of [...n.keys()].sort()) if (n.get(id)! > bn) { bn = n.get(id)!; best = id; }
  return best;
}
