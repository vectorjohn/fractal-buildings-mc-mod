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
  it('rejects an unknown lot id', () => {
    expect(() => parseScene(mutate((j) => { j.buildings[0].lots[0] = 'nope'; }))).toThrow(/buildings\[0\]\.lots\[0\].*nope/);
  });
  it('rejects a terrain size mismatch and non-object input', () => {
    expect(() => parseScene(mutate((j) => { j.terrain.nx = 3; }))).toThrow(/terrain\.floorY/);
    expect(() => parseScene(null)).toThrow();
  });
});
