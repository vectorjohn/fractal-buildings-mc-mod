// Settlement plan formats `fb-settlement/1` (the skeleton) and `fb-district/1` (one tile, loaded on demand):
// strict parsers (errors start with the JSON path, as ir.ts) and a gzip-aware fetch. See docs/IR.md "Settlement plans".
import { decodeBytes } from './regionmap';

export const SETTLEMENT_FORMAT = 'fb-settlement/1';
export const DISTRICT_FORMAT = 'fb-district/1';
export const PLAN_VERSION = 1;

export const ROAD_KINDS = ['arterial', 'main', 'street', 'quay', 'lane', 'path'] as const;
export type RoadKind = (typeof ROAD_KINDS)[number];
export const GATE_KINDS = ['cell', 'wall', 'exit'] as const;
export const LANDMARK_KINDS = ['square', 'market', 'green', 'park', 'temple', 'hall', 'tower'] as const;
export const QUARTER_KINDS = ['downtown', 'old-town', 'market', 'residential', 'workshop', 'waterfront', 'park', 'outskirts'] as const;
export const FRONTS = ['north', 'east', 'south', 'west'] as const;
export type Front = (typeof FRONTS)[number];

export type XZ = [number, number];
export interface PlanViolation { invariant: string; building: string | null; message: string; cells: { min: [number, number, number]; size: [number, number, number] }[] }
export interface Coarse {
  x0: number; z0: number; step: number; nx: number; nz: number;
  floorY: number[]; water: number[]; biome: number[]; biomes: string[]; mask: number[]; density: number[]; quarter: number[];
}
export interface Quarter { id: number; kind: string }
export interface Cell { cx: number; cz: number; site: XZ; polygon: XZ[] }
export interface DistrictSummary {
  id: string; ix: number; iz: number; x: number; z: number; w: number; d: number; origin: XZ; quarter: number; density: number;
  styles: { style: string; weight: number }[]; lots: number; detail: string | null;
}
export interface Road { id: string; kind: RoadKind; width: number; points: XZ[]; bridges: [number, number][] }
export interface Gate { id: string; kind: string; x: number; z: number; cells: [XZ, XZ] | null; road: string | null }
export interface Landmark { id: string; kind: string; x: number; z: number; w: number; d: number; district: string }
export interface Wall { id: string; points: XZ[]; closed: boolean }
export interface Settlement {
  genVersion: number; seed: string;
  site: { id: string; tier: string; centre: XZ; radius: number; claimant: XZ; cells: XZ[] };
  bounds: { x: number; z: number; w: number; d: number };
  coarse: Coarse; quarters: Quarter[]; cells: Cell[]; districts: DistrictSummary[];
  roads: Road[]; gates: Gate[]; landmarks: Landmark[]; walls: Wall[]; violations: PlanViolation[];
}
export interface Street { id: string; kind: RoadKind; width: number; points: XZ[]; y: number[] }
export interface Plaza { id: string; kind: string; x: number; z: number; w: number; d: number; y: number }
export interface PlanLot {
  id: string; x: number; z: number; w: number; d: number; front: Front; type: string; style: string;
  storeys: [number, number]; streets: number; airRights: number; frontY: number; density: number; status: string;
}
export interface District {
  genVersion: number; seed: string;
  district: { id: string; ix: number; iz: number; x: number; z: number; w: number; d: number; quarter: number; density: number };
  streets: Street[]; plazas: Plaza[]; lots: PlanLot[]; scene: string | null; violations: PlanViolation[];
}

// ---- validation ------------------------------------------------------------------------------------------

type Obj = Record<string, unknown>;
const at = (p: string, k: string | number): string => (typeof k === 'number' ? `${p}[${k}]` : p ? `${p}.${k}` : `$.${k}`);
function fail(path: string, what: string): never { throw new Error(`fb-settlement: ${path}: ${what}`); }
function obj(v: unknown, p: string): Obj {
  if (typeof v !== 'object' || v === null || Array.isArray(v)) fail(p, 'must be an object');
  return v as Obj;
}
function field(o: Obj, k: string, p: string): unknown {
  if (!(k in o)) fail(at(p, k), 'missing');
  return o[k];
}
function int(o: Obj, k: string, p: string): number {
  const v = field(o, k, p);
  if (typeof v !== 'number' || !Number.isInteger(v)) fail(at(p, k), `must be an integer, got ${JSON.stringify(v)}`);
  return v;
}
function str(o: Obj, k: string, p: string): string {
  const v = field(o, k, p);
  if (typeof v !== 'string') fail(at(p, k), 'must be a string');
  return v;
}
function strOrNull(o: Obj, k: string, p: string): string | null {
  const v = field(o, k, p);
  if (v !== null && typeof v !== 'string') fail(at(p, k), 'must be a string or null');
  return v;
}
function oneOf<T extends string>(o: Obj, k: string, p: string, allowed: readonly T[]): T {
  const v = str(o, k, p);
  if (!(allowed as readonly string[]).includes(v)) fail(at(p, k), `must be one of ${allowed.join('|')}, got "${v}"`);
  return v as T;
}
function arr(o: Obj, k: string, p: string): unknown[] {
  const v = field(o, k, p);
  if (!Array.isArray(v)) fail(at(p, k), 'must be an array');
  return v;
}
function list<T>(o: Obj, k: string, p: string, f: (v: unknown, p: string) => T): T[] {
  return arr(o, k, p).map((v, i) => f(v, at(at(p, k), i)));
}
function intsN(o: Obj, k: string, p: string, n: number): number[] {
  const v = arr(o, k, p);
  if (v.length !== n || !v.every((x) => typeof x === 'number' && Number.isInteger(x))) fail(at(p, k), `must be an array of ${n} integers`);
  return v as number[];
}
function xz(v: unknown, p: string): XZ {
  if (!Array.isArray(v) || v.length !== 2 || !v.every((x) => typeof x === 'number' && Number.isInteger(x))) fail(p, 'must be [x, z] integers');
  return [v[0] as number, v[1] as number];
}
function xzField(o: Obj, k: string, p: string): XZ { return xz(field(o, k, p), at(p, k)); }
function xzList(o: Obj, k: string, p: string): XZ[] { return arr(o, k, p).map((v, i) => xz(v, at(at(p, k), i))); }
function seed(o: Obj, p: string): string {
  const s = str(o, 'seed', p);
  if (!/^-?\d+$/.test(s)) fail(at(p, 'seed'), 'must be a decimal string');
  return s;
}
function header(o: Obj, format: string): void {
  if (o['format'] !== format) fail('$.format', `expected "${format}", got ${JSON.stringify(o['format'])}`);
  const v = int(o, 'planVersion', '');
  if (v !== PLAN_VERSION) fail('$.planVersion', `unsupported plan version ${v}`);
}

function violation(v: unknown, p: string): PlanViolation {
  const o = obj(v, p);
  return {
    invariant: str(o, 'invariant', p), building: strOrNull(o, 'building', p), message: str(o, 'message', p),
    cells: list(o, 'cells', p, (c, cp) => {
      const co = obj(c, cp);
      return { min: intsN(co, 'min', cp, 3) as [number, number, number], size: intsN(co, 'size', cp, 3) as [number, number, number] };
    }),
  };
}
function uniqueIds(items: { id: string }[], path: string): void {
  const seen = new Set<string>();
  items.forEach((it, i) => {
    if (seen.has(it.id)) fail(`${path}[${i}].id`, `duplicate id "${it.id}"`);
    seen.add(it.id);
  });
}

function parseCoarse(v: unknown, p: string): Coarse {
  const o = obj(v, p);
  const nx = int(o, 'nx', p), nz = int(o, 'nz', p), step = int(o, 'step', p);
  if (nx < 1 || nz < 1 || step < 1) fail(p, 'nx, nz and step must be positive');
  const n = nx * nz;
  return {
    x0: int(o, 'x0', p), z0: int(o, 'z0', p), step, nx, nz,
    floorY: intsN(o, 'floorY', p, n), water: intsN(o, 'water', p, n), biome: intsN(o, 'biome', p, n),
    biomes: arr(o, 'biomes', p).map((b, i) => (typeof b === 'string' ? b : fail(at(at(p, 'biomes'), i), 'must be a string'))),
    mask: intsN(o, 'mask', p, n), density: intsN(o, 'density', p, n), quarter: intsN(o, 'quarter', p, n),
  };
}

export function parseSettlement(text: string): Settlement {
  const o = obj(JSON.parse(text), '$');
  header(o, SETTLEMENT_FORMAT);
  const s = obj(field(o, 'site', '$'), '$.site');
  const b = obj(field(o, 'bounds', '$'), '$.bounds');
  const coarse = parseCoarse(field(o, 'coarse', '$'), '$.coarse');
  const quarters = list(o, 'quarters', '$', (q, p): Quarter => { const qo = obj(q, p); return { id: int(qo, 'id', p), kind: str(qo, 'kind', p) }; });
  const districts = list(o, 'districts', '$', (d, p): DistrictSummary => {
    const d0 = obj(d, p);
    const ix = int(d0, 'ix', p), iz = int(d0, 'iz', p);
    const id = str(d0, 'id', p);
    const expect = `d${ix < 0 ? 'm' + -ix : ix}_${iz < 0 ? 'm' + -iz : iz}`;
    if (id !== expect) fail(at(p, 'id'), `"${id}" does not match ix/iz (${expect})`);
    const w = int(d0, 'w', p), d1 = int(d0, 'd', p);
    if (w <= 0 || d1 <= 0) fail(p, 'w and d must be positive');
    return {
      id, ix, iz, x: int(d0, 'x', p), z: int(d0, 'z', p), w, d: d1, origin: xzField(d0, 'origin', p),
      quarter: int(d0, 'quarter', p), density: int(d0, 'density', p),
      styles: list(d0, 'styles', p, (st, sp) => { const so = obj(st, sp); return { style: str(so, 'style', sp), weight: int(so, 'weight', sp) }; }),
      lots: int(d0, 'lots', p), detail: strOrNull(d0, 'detail', p),
    };
  });
  uniqueIds(districts, '$.districts');
  const roads = list(o, 'roads', '$', (r, p): Road => {
    const ro = obj(r, p);
    const points = xzList(ro, 'points', p);
    const bridges = list(ro, 'bridges', p, (br, bp): [number, number] => {
      const [i, j] = xz(br, bp);
      if (i < 0 || j < i || j >= points.length) fail(bp, `bridge range [${i}, ${j}] outside the ${points.length} points`);
      return [i, j];
    });
    return { id: str(ro, 'id', p), kind: oneOf(ro, 'kind', p, ROAD_KINDS), width: int(ro, 'width', p), points, bridges };
  });
  uniqueIds(roads, '$.roads');
  return {
    genVersion: int(o, 'genVersion', '$'), seed: seed(o, '$'),
    site: {
      id: str(s, 'id', '$.site'), tier: str(s, 'tier', '$.site'), centre: xzField(s, 'centre', '$.site'), radius: int(s, 'radius', '$.site'),
      claimant: xzField(s, 'claimant', '$.site'), cells: xzList(s, 'cells', '$.site'),
    },
    bounds: { x: int(b, 'x', '$.bounds'), z: int(b, 'z', '$.bounds'), w: int(b, 'w', '$.bounds'), d: int(b, 'd', '$.bounds') },
    coarse, quarters,
    cells: list(o, 'cells', '$', (c, p): Cell => {
      const co = obj(c, p);
      return { cx: int(co, 'cx', p), cz: int(co, 'cz', p), site: xzField(co, 'site', p), polygon: xzList(co, 'polygon', p) };
    }),
    districts, roads,
    gates: list(o, 'gates', '$', (g, p): Gate => {
      const go = obj(g, p);
      const cells = field(go, 'cells', p);
      let cs: [XZ, XZ] | null = null;
      if (cells !== null) {
        if (!Array.isArray(cells) || cells.length !== 2) fail(at(p, 'cells'), 'must be null or two [cx, cz] pairs');
        cs = [xz(cells[0], at(at(p, 'cells'), 0)), xz(cells[1], at(at(p, 'cells'), 1))];
      }
      return { id: str(go, 'id', p), kind: oneOf(go, 'kind', p, GATE_KINDS), x: int(go, 'x', p), z: int(go, 'z', p), cells: cs, road: strOrNull(go, 'road', p) };
    }),
    landmarks: list(o, 'landmarks', '$', (l, p): Landmark => {
      const lo = obj(l, p);
      return { id: str(lo, 'id', p), kind: oneOf(lo, 'kind', p, LANDMARK_KINDS), x: int(lo, 'x', p), z: int(lo, 'z', p), w: int(lo, 'w', p), d: int(lo, 'd', p), district: str(lo, 'district', p) };
    }),
    walls: list(o, 'walls', '$', (w, p): Wall => {
      const wo = obj(w, p);
      const closed = field(wo, 'closed', p);
      if (typeof closed !== 'boolean') fail(at(p, 'closed'), 'must be a boolean');
      return { id: str(wo, 'id', p), points: xzList(wo, 'points', p), closed };
    }),
    violations: list(o, 'violations', '$', violation),
  };
}

export function parseDistrict(text: string): District {
  const o = obj(JSON.parse(text), '$');
  header(o, DISTRICT_FORMAT);
  const d = obj(field(o, 'district', '$'), '$.district');
  const p = '$.district';
  const w = int(d, 'w', p), dd = int(d, 'd', p), x = int(d, 'x', p), z = int(d, 'z', p);
  const lots = list(o, 'lots', '$', (l, lp): PlanLot => {
    const lo = obj(l, lp);
    const st = intsN(lo, 'storeys', lp, 2);
    const lot: PlanLot = {
      id: str(lo, 'id', lp), x: int(lo, 'x', lp), z: int(lo, 'z', lp), w: int(lo, 'w', lp), d: int(lo, 'd', lp), front: oneOf(lo, 'front', lp, FRONTS),
      type: str(lo, 'type', lp), style: str(lo, 'style', lp), storeys: [st[0]!, st[1]!], streets: int(lo, 'streets', lp),
      airRights: int(lo, 'airRights', lp), frontY: int(lo, 'frontY', lp), density: int(lo, 'density', lp), status: str(lo, 'status', lp),
    };
    if (lot.w <= 0 || lot.d <= 0) fail(lp, 'w and d must be positive');
    if (lot.x < x || lot.z < z || lot.x + lot.w > x + w || lot.z + lot.d > z + dd) fail(lp, 'lot lies outside its district tile');
    if (lot.status !== 'build' && !lot.status.startsWith('empty:')) fail(at(lp, 'status'), `must be "build" or "empty:<reason>", got "${lot.status}"`);
    return lot;
  });
  uniqueIds(lots, '$.lots');
  return {
    genVersion: int(o, 'genVersion', '$'), seed: seed(o, '$'),
    district: { id: str(d, 'id', p), ix: int(d, 'ix', p), iz: int(d, 'iz', p), x, z, w, d: dd, quarter: int(d, 'quarter', p), density: int(d, 'density', p) },
    streets: list(o, 'streets', '$', (s, sp): Street => {
      const so = obj(s, sp);
      const points = xzList(so, 'points', sp);
      const y = arr(so, 'y', sp).map((v, i) => (typeof v === 'number' && Number.isInteger(v) ? v : fail(at(at(sp, 'y'), i), 'must be an integer')));
      if (y.length !== points.length) fail(at(sp, 'y'), `must have one entry per point (${points.length})`);
      return { id: str(so, 'id', sp), kind: oneOf(so, 'kind', sp, ROAD_KINDS), width: int(so, 'width', sp), points, y };
    }),
    plazas: list(o, 'plazas', '$', (pl, pp): Plaza => {
      const po = obj(pl, pp);
      return { id: str(po, 'id', pp), kind: str(po, 'kind', pp), x: int(po, 'x', pp), z: int(po, 'z', pp), w: int(po, 'w', pp), d: int(po, 'd', pp), y: int(po, 'y', pp) };
    }),
    lots, scene: strOrNull(o, 'scene', '$'), violations: list(o, 'violations', '$', violation),
  };
}

// ---- loading ---------------------------------------------------------------------------------------------

/** Joins a city directory URL (`gallery-data/city/x/` or `fixtures/...`) and a path relative to settlement.json. */
export function joinUrl(dir: string, rel: string): string {
  return dir.replace(/\/+$/, '') + '/' + rel.replace(/^\/+/, '');
}

export async function fetchText(url: string): Promise<string> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`);
  return decodeBytes(new Uint8Array(await r.arrayBuffer()));
}
export async function loadSettlement(dir: string): Promise<Settlement> {
  return parseSettlement(await fetchText(joinUrl(dir, 'settlement.json')));
}
export async function loadDistrict(dir: string, rel: string): Promise<District> {
  return parseDistrict(await fetchText(joinUrl(dir, rel)));
}
