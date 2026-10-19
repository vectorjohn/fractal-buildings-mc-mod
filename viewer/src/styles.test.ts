import { describe, expect, it } from 'vitest';
import { bundledStyles, cellHash, parseStyles, styleColour, styleIds } from './styles';

const plain = JSON.stringify({ id: 'plain', colours: { wall: ['#102030'], roof: ['#ff0000', '#00ff00', '#0000ff'] } });
const child = JSON.stringify({ id: 'kid', extends: 'plain', colours: { wall: ['#ffffff'] } });

describe('style colours', () => {
  it('resolves extends: child roles replace, others are inherited', () => {
    const m = parseStyles({ 'a/plain.json': plain, 'a/kid.json': child });
    expect(m.get('kid')!.colours['wall']).toEqual([0xffffff]);
    expect(m.get('kid')!.colours['roof']).toEqual([0xff0000, 0x00ff00, 0x0000ff]);
  });
  it('rejects cycles, unknown parents and bad colours', () => {
    expect(() => parseStyles({ a: JSON.stringify({ id: 'a', extends: 'b' }), b: JSON.stringify({ id: 'b', extends: 'a' }) })).toThrow(/cycle/);
    expect(() => parseStyles({ a: JSON.stringify({ id: 'a', extends: 'zzz' }) })).toThrow(/unknown/);
    expect(() => parseStyles({ a: JSON.stringify({ id: 'a', colours: { wall: ['red'] } }) })).toThrow(/bad colour/);
  });
  it('picks colours[role][hash % n], deterministically, using every entry', () => {
    const m = parseStyles({ p: plain.replace('plain', 'plain') });
    const seen = new Set<number>();
    for (let x = 0; x < 20; x++) for (let y = 0; y < 5; y++) {
      const c = styleColour('plain', 'roof', x, y, 3, m);
      expect(c).toBe(styleColour('plain', 'roof', x, y, 3, m));
      seen.add(c);
    }
    expect(seen.size).toBe(3);
    expect(cellHash(1, 2, 3)).not.toBe(cellHash(3, 2, 1));
  });
  it('unknown style falls back to plain, unknown role to the debug colour', () => {
    const m = parseStyles({ p: plain });
    expect(styleColour('nope', 'wall', 0, 0, 0, m)).toBe(0x102030);
    expect(styleColour('plain', 'door', 0, 0, 0, m)).toBe(0xe08a1e);
  });
  it('bundles the real style files', () => {
    expect(styleIds()).toEqual(expect.arrayContaining(['plain', 'medieval']));
    const med = bundledStyles().get('medieval')!;
    expect(med.colours['roof']!.length).toBeGreaterThan(1); // the weathering gradient
    expect(med.colours['wall']).toBeDefined();
  });
});
