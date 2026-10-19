import { describe, expect, it } from 'vitest';
import { MAX_SHADE, MIN_SHADE, roofShades, slopeShade } from './shading';
import type { Voxel } from './voxels';

const roof = (x: number, y: number, z: number, building = 0): Voxel => ({ x, y, z, role: 'roof', layer: 'roof', storey: 1, building });

/** Gable with the ridge along z at x = 3: heights 0,1,2,3,2,1,0 across x = 0..6, columns z = 0..3. */
function gable(): Voxel[] {
  const out: Voxel[] = [];
  for (let z = 0; z < 4; z++) for (let x = 0; x <= 6; x++) out.push(roof(x, 10 + 3 - Math.abs(x - 3), z));
  return out;
}

describe('roof slope shading', () => {
  it('flat is exactly 1 and tilts away from the sun are darker than towards it', () => {
    expect(slopeShade(0, 0)).toBeCloseTo(1, 12);
    expect(slopeShade(-1, 0)).toBe(1); // rises to the west: faces east, the sun side (capped at the flat colour)
    expect(slopeShade(1, 0)).toBeLessThan(1);
    for (const g of [-5, -1, 0, 1, 5]) for (const h of [-5, 0, 1, 5]) {
      const s = slopeShade(g, h);
      expect(s).toBeGreaterThanOrEqual(MIN_SHADE);
      expect(s).toBeLessThanOrEqual(MAX_SHADE);
    }
  });

  it('the two sides of a gable differ, the ridge is untinted, columns along the ridge agree', () => {
    const vs = gable();
    const s = roofShades(vs);
    const at = (x: number, z: number) => s[vs.findIndex((v) => v.x === x && v.z === z)]!;
    expect(at(3, 1)).toBe(1); // ridge
    for (const z of [0, 1, 2, 3]) {
      expect(at(1, z)).toBeLessThan(0.75); // rises to the east: faces west, away from the sun
      expect(at(1, z)).toBe(at(1, 0));
      expect(at(5, z)).toBe(1); // faces east, the sun side (capped)
    }
    expect(at(5, 0) - at(1, 0)).toBeGreaterThan(0.25);
    expect(at(0, 0)).toBe(at(1, 0)); // eave: one-sided difference gives the same slope
  });

  it('only the top roof voxel of a column counts and non-roof voxels stay 1', () => {
    const wall: Voxel = { ...roof(0, 0, 0), role: 'wall', layer: 'shell' };
    const vs = [wall, roof(0, 5, 0), roof(0, 6, 0), roof(1, 7, 0), roof(1, 6, 0)];
    const s = roofShades(vs);
    expect(s[0]).toBe(1);
    expect(s[1]).toBe(s[2]); // same column, same slope
    expect(s[1]).toBeLessThan(1); // (0,*) top 6, (1,*) top 7: rises to the east: faces west, dark
  });

  it('is deterministic and keeps buildings apart', () => {
    const a = gable();
    const b = a.map((v) => ({ ...v, building: 1, x: v.x + 100 }));
    expect(roofShades([...a, ...b])).toEqual([...roofShades(a), ...roofShades(b)]);
  });
});
