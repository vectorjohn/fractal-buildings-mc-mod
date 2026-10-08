import type { Ir, Voxel } from './ir';

/** Expand IR boxes into unit voxels, sorted (y, z, x) and de-duplicated (later boxes win). Pure. */
export function irToVoxels(ir: Ir): Voxel[] {
  const cells = new Map<string, Voxel>();
  for (const b of ir.boxes) {
    const [mx, my, mz] = b.min;
    const [sx, sy, sz] = b.size;
    for (let y = 0; y < sy; y++)
      for (let z = 0; z < sz; z++)
        for (let x = 0; x < sx; x++) {
          const v = { x: mx + x, y: my + y, z: mz + z, role: b.role };
          cells.set(`${v.x},${v.y},${v.z}`, v);
        }
  }
  return [...cells.values()].sort((a, b) => a.y - b.y || a.z - b.z || a.x - b.x);
}
