import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { parseScene } from './ir';

const raw = () => JSON.parse(readFileSync(new URL('../fixtures/sample-house.json', import.meta.url), 'utf8'));
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const mutate = (f: (j: any) => void) => { const j = raw(); f(j); return j; };

describe('parseScene', () => {
  it('parses the sample house', () => {
    const s = parseScene(raw());
    expect(s.buildings).toHaveLength(1);
    expect(s.seed).toBe('42');
    expect(s.buildings[0]!.shell.boxes).toHaveLength(22);
    expect(s.terrain!.floorY).toHaveLength(99);
  });
  it('accepts the arch opening kind (5.11) and rejects an unknown one', () => {
    expect(parseScene(mutate((j) => { j.buildings[0].shell.openings[0].kind = 'arch'; })).buildings[0]!.shell.openings[0]!.kind).toBe('arch');
    expect(() => parseScene(mutate((j) => { j.buildings[0].shell.openings[0].kind = 'gate'; }))).toThrow(/openings\[0\]\.kind/);
  });
  it('accepts terrain null', () => {
    expect(parseScene(mutate((j) => { j.terrain = null; })).terrain).toBeNull();
  });
  it('rejects a wrong format', () => {
    expect(() => parseScene(mutate((j) => { j.format = 'fb-scene/2'; }))).toThrow(/format/);
  });
  it('rejects a float with its path', () => {
    expect(() => parseScene(mutate((j) => { j.buildings[0].origin[1] = 64.5; }))).toThrow(/buildings\[0\]\.origin/);
  });
  it('rejects a missing key with its path', () => {
    expect(() => parseScene(mutate((j) => { delete j.buildings[0].shell.specHash; }))).toThrow(/buildings\[0\]\.shell\.specHash: missing/);
  });
  it('rejects a bad role with its path', () => {
    expect(() => parseScene(mutate((j) => { j.buildings[0].shell.boxes[3].role = 'glass'; }))).toThrow(/buildings\[0\]\.shell\.boxes\[3\]\.role/);
  });
  it('reads genVersion 2 keys (hooks, maxHeight, streets) and the beam and fence roles', () => {
    const s = parseScene(mutate((j) => {
      j.buildings[0].shell.spec.maxHeight = 200;
      j.buildings[0].shell.spec.streets = 1;
      j.buildings[0].shell.hooks = [{ id: 'h0', kind: 'bed', room: 'r0', pos: [1, 1, 1], facing: 'south' }];
      j.buildings[0].shell.boxes[3].role = 'beam';
      j.buildings[0].shell.boxes[4].role = 'fence';
    }));
    expect(s.buildings[0]!.shell.hooks[0]!.kind).toBe('bed');
    expect(s.buildings[0]!.shell.spec.maxHeight).toBe(200);
    expect(parseScene(raw()).buildings[0]!.shell.hooks).toEqual([]);
    expect(() => parseScene(mutate((j) => { j.buildings[0].shell.hooks = [{ id: 'h0' }]; }))).toThrow(/hooks\[0\]\.kind/);
  });
  it('rejects an unknown lot id', () => {
    expect(() => parseScene(mutate((j) => { j.buildings[0].lots[0] = 'nope'; }))).toThrow(/buildings\[0\]\.lots\[0\].*nope/);
  });
  it('rejects a terrain size mismatch and non-object input', () => {
    expect(() => parseScene(mutate((j) => { j.terrain.nx = 3; }))).toThrow(/terrain\.floorY/);
    expect(() => parseScene(null)).toThrow();
  });
});

describe('parseScene, district scene keys', () => {
  it('reads terrain.water and ground boxes, and defaults them when absent', () => {
    expect(parseScene(raw()).ground).toEqual([]);
    expect(parseScene(raw()).terrain!.water).toBeUndefined();
    const s = parseScene(mutate((j) => {
      j.terrain.water = j.terrain.floorY.map((_: number, i: number) => (i === 2 ? 3 : 0));
      j.ground = [{ min: [0, 60, 0], size: [2, 1, 2], role: 'road', layer: 'ground', storey: -1 }];
    }));
    expect(s.terrain!.water![2]).toBe(3);
    expect(s.ground[0]!.role).toBe('road');
    expect(s.ground[0]!.layer).toBe('ground');
  });
  it('rejects bad water and bad ground boxes with their paths', () => {
    expect(() => parseScene(mutate((j) => { j.terrain.water = [1, 2]; }))).toThrow(/terrain\.water/);
    expect(() => parseScene(mutate((j) => { j.terrain.water = j.terrain.floorY.map(() => -1); }))).toThrow(/terrain\.water\[0\]/);
    expect(() => parseScene(mutate((j) => { j.ground = [{ min: [0, 0, 0], size: [1, 1, 1], role: 'asphalt', layer: 'ground', storey: -1 }]; }))).toThrow(/ground\[0\]\.role/);
  });
  it('parses the sample district scene', () => {
    const s = parseScene(JSON.parse(readFileSync(new URL('../fixtures/settlement/sample/scenes/d0_0.json', import.meta.url), 'utf8')));
    expect(s.buildings.length).toBe(12);
    expect(s.ground.length).toBeGreaterThan(0);
    expect(s.ground.every((b) => b.layer === 'ground' && b.storey === -1)).toBe(true);
    expect(s.terrain!.water!.some((w) => w > 0)).toBe(true);
  });
});

describe('opening kinds', () => {
  it('accepts arch (5.11)', async () => {
    const { OPENING_KINDS } = await import('./ir');
    expect(OPENING_KINDS).toContain('arch');
  });
});
