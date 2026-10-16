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
  const cells = new Map<string, Voxel>();
  const [ox, oy, oz] = b.origin;
  const [sx, , sz] = b.shell.bounds.size;
  for (const box of [...b.shell.boxes, ...b.siteBoxes]) {
    const [mx, my, mz] = box.min;
    const [bx, by, bz] = box.size;
    for (let y = 0; y < by; y++)
      for (let z = 0; z < bz; z++)
        for (let x = 0; x < bx; x++) {
          const [wx, wz] = placeColumn(b.rotation, ox, oz, sx, sz, mx + x, mz + z);
          const wy = oy + my + y;
          const key = `${wx},${wy},${wz}`;
          if (box.role === 'air') cells.delete(key);
          else cells.set(key, { x: wx, y: wy, z: wz, role: box.role, layer: box.layer, storey: box.storey, building: index });
        }
  }
  return [...cells.values()].sort((a, b2) => a.y - b2.y || a.z - b2.z || a.x - b2.x);
}

/** All building voxels, sorted (building, y, z, x). */
export function sceneVoxels(scene: Scene): Voxel[] {
  return scene.buildings.flatMap((b, i) => buildingVoxels(b, i));
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
