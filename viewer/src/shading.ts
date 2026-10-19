import type { Voxel } from './voxels';

/** Fixed light for the roof tint (world axes, not the camera): from the east, above and a little south. */
const SUN: [number, number, number] = (() => {
  const v: [number, number, number] = [0.5, 0.75, 0.43];
  const n = Math.hypot(...v);
  return [v[0] / n, v[1] / n, v[2] / n];
})();

export const MIN_SHADE = 0.5;
export const MAX_SHADE = 1; // never brighter than the flat colour: the role-colour roof must not become overlay red

/** Brightness factor of a surface tilted so that the height rises by (dx, dz) per voxel; flat = 1. */
export function slopeShade(dhdx: number, dhdz: number): number {
  const len = Math.hypot(dhdx, 1, dhdz);
  const lambert = (-dhdx * SUN[0] + SUN[1] - dhdz * SUN[2]) / len; // normal = (-dh/dx, 1, -dh/dz) / len
  return Math.min(MAX_SHADE, Math.max(MIN_SHADE, 0.35 + 0.65 * (lambert / SUN[1])));
}

/**
 * Per-voxel brightness factors for the roof heightfield: for each (building, x, z) column the top `roof` voxel gives the
 * height; its slope comes from the neighbouring columns (central difference where both exist, one-sided where one does,
 * 0 where none), so flat tops and the one-cell ridge between two slopes stay at 1 and each side of a gable gets its own
 * shade. Returns an array parallel to `voxels` (1 for everything that is not a roof voxel).
 */
export function roofShades(voxels: readonly Voxel[]): number[] {
  const top = new Map<string, number>();
  const key = (b: number, x: number, z: number) => `${b},${x},${z}`;
  for (const v of voxels) {
    if (v.role !== 'roof') continue;
    const k = key(v.building, v.x, v.z);
    const h = top.get(k);
    if (h === undefined || v.y > h) top.set(k, v.y);
  }
  const grad = (lo: number | undefined, here: number, hi: number | undefined): number => {
    if (lo !== undefined && hi !== undefined) return (hi - lo) / 2;
    if (hi !== undefined) return hi - here;
    if (lo !== undefined) return here - lo;
    return 0;
  };
  return voxels.map((v) => {
    if (v.role !== 'roof') return 1;
    const here = top.get(key(v.building, v.x, v.z))!;
    const gx = grad(top.get(key(v.building, v.x - 1, v.z)), here, top.get(key(v.building, v.x + 1, v.z)));
    const gz = grad(top.get(key(v.building, v.x, v.z - 1)), here, top.get(key(v.building, v.x, v.z + 1)));
    return gx === 0 && gz === 0 ? 1 : slopeShade(gx, gz);
  });
}
