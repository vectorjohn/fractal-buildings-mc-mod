import { readdirSync, readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { batchArgs, CAMERAS, parsePreset } from './galleryPresets';
import { PRESET_NAMES } from './presets';

const base = { type: 'house', style: 'medieval', lot: '20x20', terrain: 'flat:64', front: 'north', seeds: '0..15', gen: 'v1' };

describe('gallery presets', () => {
  it('knows the same cameras as the viewer', () => expect([...CAMERAS]).toEqual([...PRESET_NAMES]));
  it('parses a full preset and builds the CLI arguments', () => {
    const p = parsePreset('house-medieval', base);
    expect(p).toMatchObject({ kind: 'cli', seeds: [0, 15], cutStorey: 0, cameras: ['front-left', 'back-right', 'top', 'front'] });
    expect(batchArgs(p)).toEqual(['batch', '--seeds', '0..15', '--lot', '20x20', '--style', 'medieval', '--terrain', 'flat:64',
      '--front', 'north', '--type', 'house', '--gen', 'v1']);
  });
  it('passes square lots to the CLI as --square', () => {
    const p = parsePreset('shop-market', { ...base, type: 'shop', square: true });
    expect(p.square).toBe(true);
    expect(batchArgs(p).slice(-1)).toEqual(['--square']);
    expect(batchArgs(parsePreset('x', base))).not.toContain('--square');
    expect(() => parsePreset('p', { ...base, square: 'yes' })).toThrow(/"square"/);
  });
  it('applies defaults and accepts the cutaway camera and a dump preset', () => {
    expect(parsePreset('x', { lot: '8x8' })).toMatchObject({ type: 'house', style: 'medieval', terrain: 'flat:64', front: 'north', gen: 'v1' });
    const d = parsePreset('fixtures', { kind: 'dump', cameras: ['top', 'cutaway'], cutStorey: 1 });
    expect(d).toMatchObject({ kind: 'dump', cameras: ['top', 'cutaway'], cutStorey: 1 });
  });
  it('rejects bad files with the preset name in the message', () => {
    const rej = (o: unknown, re: RegExp, name = 'p') => expect(() => parsePreset(name, o)).toThrow(re);
    rej({ ...base, seeds: '0..16' }, /p: "seeds"/); // 17 cells
    rej({ ...base, seeds: '5..2' }, /"seeds"/);
    rej({ ...base, lot: '20' }, /"lot"/);
    rej({ ...base, front: 'up' }, /"front"/);
    rej({ ...base, cameras: ['sideways'] }, /"cameras"/);
    rej({ ...base, cutStorey: -1 }, /"cutStorey"/);
    rej({ ...base, bogus: 1 }, /unknown key "bogus"/);
    rej({ ...base, kind: 'other' }, /"kind"/);
    rej(base, /file name/, 'Bad_Name');
    rej([], /object/);
  });
  it('every committed preset file parses and its file name is unique per type or style', () => {
    const dir = new URL('../gallery-presets/', import.meta.url);
    const files = readdirSync(dir).filter((f) => f.endsWith('.json'));
    expect(files.length).toBeGreaterThanOrEqual(5);
    for (const f of files) parsePreset(f.slice(0, -5), JSON.parse(readFileSync(new URL(f, dir), 'utf8')));
    for (const n of ['house-plain', 'house-medieval', 'house-plain-flat', 'house-medieval-flat', 'fixtures']) expect(files).toContain(`${n}.json`);
  });
});
