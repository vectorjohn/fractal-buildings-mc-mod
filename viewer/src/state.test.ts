import { describe, expect, it } from 'vitest';
import { ALL_LAYERS, latestGate, defaultState, parseState, resolveSeed, serializeState, stepSeed, storeyVisible, toggleLayer } from './state';

describe('state <-> URL', () => {
  it('defaults serialise to an empty query', () => {
    expect(serializeState(defaultState())).toBe('');
    expect(parseState('').layers.size).toBe(ALL_LAYERS.length);
  });

  it('parses the documented example', () => {
    const s = parseState('?style=plain&seed=12&layers=shell,roof&colour=palette&storeys=1');
    expect(s.style).toBe('plain');
    expect(s.seed).toBe(12);
    expect([...s.layers].sort()).toEqual(['roof', 'shell']);
    expect(s.colour).toBe('palette');
    expect(s.maxStorey).toBe(1);
  });

  it('round-trips, with canonical layer order', () => {
    const q = 'fixture=sample-house&style=medieval&seed=3&layers=shell,roof&colour=palette&storeys=0';
    expect(serializeState(parseState(q))).toBe(q);
    expect(serializeState(parseState('layers=roof,shell'))).toBe('layers=shell,roof');
  });

  it('empty layers= means none, unknown layers and junk numbers are ignored', () => {
    expect(parseState('layers=').layers.size).toBe(0);
    expect(serializeState(parseState('layers='))).toBe('layers=');
    expect([...parseState('layers=bogus,shell').layers]).toEqual(['shell']);
    expect(parseState('seed=abc&storeys=-2').seed).toBeNull();
    expect(parseState('seed=abc&storeys=-2').maxStorey).toBeNull();
    expect(parseState('seed=-5').seed).toBe(-5);
  });

  it('toggleLayer does not mutate its input', () => {
    const a = defaultState();
    const b = toggleLayer(a, 'roof', false);
    expect(a.layers.has('roof')).toBe(true);
    expect(b.layers.has('roof')).toBe(false);
    expect(toggleLayer(b, 'roof', true).layers.has('roof')).toBe(true);
  });
});

describe('seed stepping', () => {
  const seeds = [4, 7, 9];
  it('steps and clamps', () => {
    expect(stepSeed(seeds, 4, 1)).toBe(7);
    expect(stepSeed(seeds, 9, 1)).toBe(9);
    expect(stepSeed(seeds, 4, -1)).toBe(4);
    expect(stepSeed(seeds, null, 1)).toBe(4);
    expect(stepSeed(seeds, 5, 1)).toBe(4);
    expect(stepSeed([], 1, 1)).toBeNull();
  });
  it('resolveSeed falls back to the first', () => {
    expect(resolveSeed(seeds, 7)).toBe(7);
    expect(resolveSeed(seeds, 8)).toBe(4);
    expect(resolveSeed(seeds, null)).toBe(4);
    expect(resolveSeed([], 3)).toBeNull();
  });
});

describe('storeyVisible', () => {
  it('hides storeys above N and the foundation unless N is the top', () => {
    expect(storeyVisible(2, 1, 2)).toBe(false);
    expect(storeyVisible(1, 1, 2)).toBe(true);
    expect(storeyVisible(-1, 1, 2)).toBe(false);
    expect(storeyVisible(-1, 2, 2)).toBe(true);
    expect(storeyVisible(2, null, 2)).toBe(true);
  });
});

describe('latestGate', () => {
  it('only the newest ticket is current', () => {
    const g = latestGate();
    const a = g.begin();
    expect(g.isLatest(a)).toBe(true);
    const b = g.begin();
    expect(g.isLatest(a)).toBe(false);
    expect(g.isLatest(b)).toBe(true);
  });
});
