import { describe, expect, it } from 'vitest';
import { cityArgs, parseCityPreset } from './cityPresets';

const base = { seed: 7, terrain: 'flat:64', site: { at: [10, -20], tier: 'town', radius: 300 }, scenes: 'auto:2' };

describe('city presets', () => {
  it('parses a site preset into CLI arguments', () => {
    const p = parseCityPreset('a-town', { ...base, cellSize: 512, camera: { plan: { zoom: 0.5 }, stops: [[0, 90, 0, 0, -30]] } });
    expect(cityArgs(p)).toEqual(['city', '--seed', '7', '--terrain', 'flat:64', '--site', '10,-20,town,300', '--cell-size', '512', '--districts', '--scenes', 'auto:2']);
    expect(p.camera.stops).toEqual([[0, 90, 0, 0, -30]]);
  });
  it('parses a cell preset and a district list', () => {
    const p = parseCityPreset('c', { seed: 1, terrain: 'flat:64', site: { cell: [2, -3] }, scenes: ['d0_0', 'dm1_2'] });
    expect(cityArgs(p)).toContain('--cell');
    expect(cityArgs(p).slice(-2)).toEqual(['--scenes', 'd0_0,dm1_2']);
  });
  it('passes diagonals off to the CLI and omits it by default', () => {
    expect(cityArgs(parseCityPreset('a', base))).not.toContain('--diagonals');
    expect(cityArgs(parseCityPreset('a', { ...base, diagonals: 'off' })).slice(-2)).toEqual(['--diagonals', 'off']);
    expect(() => parseCityPreset('a', { ...base, diagonals: 'maybe' })).toThrow(/diagonals/);
  });
  it('rejects bad input', () => {
    expect(() => parseCityPreset('Bad', base)).toThrow(/file name/);
    expect(() => parseCityPreset('x', { ...base, extra: 1 })).toThrow(/unknown key/);
    expect(() => parseCityPreset('x', { ...base, site: { at: [1, 2], tier: 'castle', radius: 5 } })).toThrow(/tier/);
    expect(() => parseCityPreset('x', { ...base, scenes: 'auto:0' })).toThrow(/scenes/);
    expect(() => parseCityPreset('x', { ...base, scenes: ['nope'] })).toThrow(/scenes/);
    expect(() => parseCityPreset('x', { ...base, camera: { stops: [[1, 2]] } })).toThrow(/stops/);
  });
});
