import { describe, expect, it } from 'vitest';
import { parseScene } from './ir';
import { hookColour } from './palettes';
import { hookMarkers } from './render';
import { cutBounds, sceneBounds } from './voxels';
import { readFileSync } from 'node:fs';

const house = JSON.parse(readFileSync(new URL('../fixtures/sample-house.json', import.meta.url), 'utf8'));

describe('hook markers and the cutaway frame', () => {
  it('places each hook like the boxes of its building (rotation, origin) with the storey of its room', () => {
    const doc = structuredClone(house);
    const b = doc.buildings[0];
    const [sx, , sz] = b.shell.bounds.size;
    const room = b.shell.rooms[0];
    b.shell.hooks = [{ id: 'h0', kind: 'bed', room: room.id, pos: [1, 0, 2], facing: 'north' }];
    const at = (rotation: number) => {
      b.rotation = rotation;
      const s = parseScene(structuredClone(doc));
      return hookMarkers(s)[0]!;
    };
    const [ox, oy, oz] = b.origin;
    expect(at(0)).toMatchObject({ x: ox + 1, y: oy + 0, z: oz + 2, kind: 'bed', storey: room.storey });
    expect(at(1)).toMatchObject({ x: ox + sz - 1 - 2, z: oz + 1 });
    expect(at(2)).toMatchObject({ x: ox + sx - 1 - 1, z: oz + sz - 1 - 2 });
    expect(at(3)).toMatchObject({ x: ox + 2, z: oz + sx - 1 - 1 });
  });
  it('colours known kinds fixed and unknown kinds by hash, distinct and stable', () => {
    expect(hookColour('bed')).not.toBe(hookColour('table'));
    expect(hookColour('zzz')).toBe(hookColour('zzz'));
    expect(hookColour('zzz')).not.toBe(hookColour('yyy'));
  });
  it('cutBounds lowers the top to the kept storeys and keeps the footprint', () => {
    const s = parseScene(structuredClone(house));
    const full = sceneBounds(s);
    const cut = cutBounds(s, 0);
    expect(cut.min).toEqual(full.min);
    expect(cut.max[0]).toBe(full.max[0]); expect(cut.max[2]).toBe(full.max[2]);
    expect(cut.max[1]).toBeLessThan(full.max[1]);
    expect(cutBounds(s, 99).max[1]).toBeLessThanOrEqual(full.max[1]);
  });
});
