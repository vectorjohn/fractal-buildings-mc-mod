/** City preset files (`city-presets/<name>.json`, step D0): one settlement on one terrain, with what to render. Pure; no DOM. */

export interface CityPreset {
  name: string;
  seed: number;
  /** A `--terrain` spec; `recorded:` paths are relative to the repository root (the CLI is run from there). */
  terrain: string;
  site: { cell: [number, number] } | { at: [number, number]; tier: string; radius: number };
  cellSize: number | null;
  /** `all` plans every district, `auto:N` plans all and builds scenes for N chosen ones. */
  scenes: 'none' | 'all' | `auto:${number}` | string[];
  camera: { plan: { x?: number; z?: number; zoom?: number; layers?: string }; stops: number[][] | null };
}

const KEYS = new Set(['seed', 'terrain', 'site', 'cellSize', 'scenes', 'camera']);
const TIERS = ['hamlet', 'village', 'town', 'city', 'metropolis'];

function bad(name: string, what: string): never {
  throw new Error(`city preset ${name}: ${what}`);
}

const num = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v);
const pair = (v: unknown): v is [number, number] => Array.isArray(v) && v.length === 2 && v.every(Number.isInteger);

/** Parse and validate one preset's JSON; `name` is the file's basename without `.json`. */
export function parseCityPreset(name: string, json: unknown): CityPreset {
  if (!/^[a-z0-9][a-z0-9-]*$/.test(name)) bad(name, 'file name must be lower-case letters, digits and dashes');
  if (typeof json !== 'object' || json === null || Array.isArray(json)) bad(name, 'must be an object');
  const o = json as Record<string, unknown>;
  for (const k of Object.keys(o)) if (!KEYS.has(k)) bad(name, `unknown key "${k}"`);
  if (!Number.isInteger(o['seed'])) bad(name, 'seed must be an integer');
  if (typeof o['terrain'] !== 'string' || o['terrain'] === '') bad(name, 'terrain must be a spec string');
  const s = o['site'] as Record<string, unknown> | undefined;
  if (typeof s !== 'object' || s === null) bad(name, 'site must be {cell: [cx, cz]} or {at: [x, z], tier, radius}');
  let site: CityPreset['site'];
  if ('cell' in s) {
    if (!pair(s['cell'])) bad(name, 'site.cell must be [cx, cz]');
    site = { cell: s['cell'] };
  } else {
    if (!pair(s['at'])) bad(name, 'site.at must be [x, z]');
    if (typeof s['tier'] !== 'string' || !TIERS.includes(s['tier'])) bad(name, `site.tier must be one of ${TIERS.join(', ')}`);
    if (!Number.isInteger(s['radius']) || (s['radius'] as number) <= 0) bad(name, 'site.radius must be a positive integer');
    site = { at: s['at'], tier: s['tier'], radius: s['radius'] as number };
  }
  const cellSize = o['cellSize'] === undefined ? null : o['cellSize'];
  if (cellSize !== null && (!Number.isInteger(cellSize) || (cellSize as number) < 256)) bad(name, 'cellSize must be an integer >= 256');
  const sc = o['scenes'] ?? 'none';
  let scenes: CityPreset['scenes'];
  if (sc === 'none' || sc === 'all' || (typeof sc === 'string' && /^auto:[1-9][0-9]?$/.test(sc))) scenes = sc as CityPreset['scenes'];
  else if (Array.isArray(sc) && sc.length > 0 && sc.every((i) => typeof i === 'string' && /^d[m0-9]+_[m0-9]+$/.test(i))) scenes = sc as string[];
  else bad(name, 'scenes must be "none", "all", "auto:N" or a list of district ids');
  const cam = (o['camera'] ?? {}) as Record<string, unknown>;
  const plan = (cam['plan'] ?? {}) as Record<string, unknown>;
  for (const k of ['x', 'z', 'zoom']) if (plan[k] !== undefined && !num(plan[k])) bad(name, `camera.plan.${k} must be a number`);
  if (plan['layers'] !== undefined && typeof plan['layers'] !== 'string') bad(name, 'camera.plan.layers must be a string');
  const stops = cam['stops'] === undefined ? null : cam['stops'];
  if (stops !== null && !(Array.isArray(stops) && stops.length > 0 && stops.every((p) => Array.isArray(p) && p.length === 5 && p.every(num)))) bad(name, 'camera.stops must be [[x, y, z, yaw, pitch], ...]');
  return {
    name, seed: o['seed'] as number, terrain: o['terrain'] as string, site, cellSize: cellSize as number | null, scenes,
    camera: { plan: plan as CityPreset['camera']['plan'], stops: stops as number[][] | null },
  };
}

/** The `fractal-cli city` arguments (without `--out-dir`) that plan this preset. Scenes are always exterior-only. */
export function cityArgs(p: CityPreset): string[] {
  const a = ['city', '--seed', String(p.seed), '--terrain', p.terrain];
  if ('cell' in p.site) a.push('--cell', p.site.cell.join(','));
  else a.push('--site', [...p.site.at, p.site.tier, p.site.radius].join(','));
  if (p.cellSize !== null) a.push('--cell-size', String(p.cellSize));
  a.push('--districts');
  if (p.scenes !== 'none') a.push('--scenes', Array.isArray(p.scenes) ? p.scenes.join(',') : p.scenes);
  return a;
}
