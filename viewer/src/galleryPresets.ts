/** Gallery preset files (`gallery-presets/<name>.json`): one per type or style, so parallel steps never conflict. Pure; no DOM. */

export const MAX_CELLS = 16;
export const CAMERAS = ['front-left', 'back-right', 'top', 'front', 'cutaway'] as const;
export type CameraName = (typeof CAMERAS)[number];
/** The four Phase 3 cameras; `cutaway` is added by presets that show interiors. */
export const DEFAULT_CAMERAS: readonly CameraName[] = ['front-left', 'back-right', 'top', 'front'];

export interface GalleryPreset {
  name: string;
  /** `cli`: run `fractal-cli batch`; `dump`: run the core's FixtureDumpTest (the TestMassings fixtures). */
  kind: 'cli' | 'dump';
  type: string;
  style: string;
  lot: string;
  terrain: string;
  front: string;
  /** Air rights over the street in front of the lot, in blocks (the CLI's --air; 0 = no street brief). */
  air: number;
  /** The lot is the square's market-hall lot (the CLI's --square; a shop on level ground). */
  square: boolean;
  /** Inclusive seed range `A..B` (at most 16 cells); for `dump`, the fixture numbers. */
  seeds: [number, number];
  gen: string;
  /** Storeys above this are hidden in the `cutaway` camera. */
  cutStorey: number;
  cameras: CameraName[];
}

const KEYS = new Set(['kind', 'type', 'style', 'lot', 'terrain', 'front', 'seeds', 'gen', 'cutStorey', 'cameras', 'air', 'square']);
const FRONTS = ['north', 'east', 'south', 'west'];

function bad(name: string, what: string): never {
  throw new Error(`gallery preset ${name}: ${what}`);
}

/** Parse and validate one preset file's JSON; `name` is the file's basename without `.json`. */
export function parsePreset(name: string, json: unknown): GalleryPreset {
  if (!/^[a-z0-9][a-z0-9-]*$/.test(name)) bad(name, 'file name must be lower-case letters, digits and dashes');
  if (typeof json !== 'object' || json === null || Array.isArray(json)) bad(name, 'must be an object');
  const o = json as Record<string, unknown>;
  for (const k of Object.keys(o)) if (!KEYS.has(k)) bad(name, `unknown key "${k}"`);
  const str = (k: string, dflt?: string): string => {
    const v = o[k];
    if (v === undefined && dflt !== undefined) return dflt;
    if (typeof v !== 'string' || v === '') bad(name, `"${k}" must be a non-empty string`);
    return v;
  };
  const kind = str('kind', 'cli');
  if (kind !== 'cli' && kind !== 'dump') bad(name, `"kind" must be cli or dump, got ${kind}`);
  const style = str('style', 'medieval');
  let seeds: [number, number] = [0, 15];
  let lot = '20x20', terrain = 'flat:64', front = 'north';
  if (kind === 'cli') {
    lot = str('lot');
    if (!/^\d+x\d+$/.test(lot)) bad(name, `"lot" must be WxD, got ${lot}`);
    terrain = str('terrain', 'flat:64');
    front = str('front', 'north');
    if (!FRONTS.includes(front)) bad(name, `"front" must be one of ${FRONTS.join('|')}`);
    const m = /^(\d+)\.\.(\d+)$/.exec(str('seeds', '0..15'));
    if (!m) bad(name, '"seeds" must be A..B');
    seeds = [Number(m[1]), Number(m[2])];
    if (seeds[1] < seeds[0] || seeds[1] - seeds[0] + 1 > MAX_CELLS) bad(name, `"seeds" must span 1..${MAX_CELLS} seeds`);
  }
  const air = o['air'] === undefined ? 0 : o['air'];
  if (typeof air !== 'number' || !Number.isInteger(air) || air < 0 || air > 2) bad(name, '"air" must be an integer 0..2');
  const square = o['square'] === undefined ? false : o['square'];
  if (typeof square !== 'boolean') bad(name, '"square" must be a boolean');
  const cutStorey = o['cutStorey'] === undefined ? 0 : o['cutStorey'];
  if (typeof cutStorey !== 'number' || !Number.isInteger(cutStorey) || cutStorey < 0) bad(name, '"cutStorey" must be an integer >= 0');
  let cameras: CameraName[] = [...DEFAULT_CAMERAS];
  if (o['cameras'] !== undefined) {
    const c = o['cameras'];
    if (!Array.isArray(c) || c.length === 0 || !c.every((x) => (CAMERAS as readonly unknown[]).includes(x))) {
      bad(name, `"cameras" must be a non-empty list of ${CAMERAS.join('|')}`);
    }
    cameras = c as CameraName[];
  }
  return {
    name, kind, type: str('type', 'house'), style, lot, terrain, front, air, square, seeds, gen: str('gen', 'v1'),
    cutStorey, cameras,
  };
}

/** The `fractal-cli batch` arguments for a `cli` preset (output directory excluded). */
export function batchArgs(p: GalleryPreset): string[] {
  return ['batch', '--seeds', `${p.seeds[0]}..${p.seeds[1]}`, '--lot', p.lot, '--style', p.style, '--terrain', p.terrain,
    '--front', p.front, '--type', p.type, '--gen', p.gen, ...(p.air > 0 ? ['--air', String(p.air)] : []),
    ...(p.square ? ['--square'] : [])];
}
