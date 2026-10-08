import { describe, expect, it } from 'vitest';
import { irToVoxels } from './voxels';

describe('irToVoxels', () => {
  it('expands a box to size.x*y*z voxels', () => {
    const v = irToVoxels({ boxes: [{ min: [0, 0, 0], size: [5, 5, 5], role: 'wall' }] });
    expect(v).toHaveLength(125);
    expect(v.every((c) => c.role === 'wall')).toBe(true);
  });

  it('is empty for no boxes', () => {
    expect(irToVoxels({ boxes: [] })).toEqual([]);
  });

  it('later boxes override overlapping cells, output is ordered and deterministic', () => {
    const ir = {
      boxes: [
        { min: [0, 0, 0] as [number, number, number], size: [2, 1, 1] as [number, number, number], role: 'wall' },
        { min: [1, 0, 0] as [number, number, number], size: [2, 1, 1] as [number, number, number], role: 'roof' },
      ],
    };
    const v = irToVoxels(ir);
    expect(v.map((c) => `${c.x}:${c.role}`)).toEqual(['0:wall', '1:roof', '2:roof']);
    expect(irToVoxels(ir)).toEqual(v);
  });
});
