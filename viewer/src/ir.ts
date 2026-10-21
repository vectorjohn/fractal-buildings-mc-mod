// fb-scene/1: the one document format for every scale (see docs/plans/phase3.md "IR v1", later docs/IR.md).
export const SCENE_FORMAT = 'fb-scene/1';

export type Vec3 = [number, number, number];
export const FACINGS = ['north', 'east', 'south', 'west'] as const;
export type Facing = (typeof FACINGS)[number];
export const ROLES = ['air', 'foundation', 'floor', 'wall', 'trim', 'window', 'door', 'roof', 'stair', 'beam', 'fence', 'road', 'paving', 'ground', 'deck'] as const;
export type Role = (typeof ROLES)[number];
export const LAYERS = ['foundation', 'shell', 'interior', 'roof', 'ground'] as const;
export type Layer = (typeof LAYERS)[number];
export const OPENING_KINDS = ['door', 'window', 'stair', 'arch'] as const;
export type OpeningKind = (typeof OPENING_KINDS)[number];

export interface Box { min: Vec3; size: Vec3; role: Role; layer: Layer; storey: number }
export interface Part { min: Vec3; size: Vec3 }
export interface Terrain { x0: number; z0: number; nx: number; nz: number; floorY: number[];
  /** Water depth above floorY per column (district scenes); absent when the file has none. */
  water?: number[];
}
export interface Lot { id: string; x: number; z: number; w: number; d: number; front: Facing;
  /** Air rights over the street in front (blocks) and the street surface y; only when above 0 (docs/IR.md). */
  airRights?: number; frontY?: number }
export interface Spec {
  genVersion: number; specSeed: string; style: string; type: string;
  width: number; depth: number; storeys: number; roof: string;
  /** genVersion 2+: exclusive local y bound and street-side mask (docs/IR.md). */
  maxHeight?: number; streets?: number; lead?: number;
}
export interface Storey { index: number; y: number; height: number }
export interface Volume { id: string; min: Vec3; size: Vec3; parent: string | null }
export interface Room { id: string; storey: number; volume: string; use: string; parts: Part[] }
export interface Opening {
  id: string; kind: OpeningKind; min: Vec3; size: Vec3; facing: Facing; from: string; to: string;
}
/** Furnishing hook (genVersion 2+): a room cell where the mod may place furniture. */
export interface Hook { id: string; kind: string; room: string; pos: Vec3; facing: Facing }
export interface Roof { form: string; volume: string; ridgeAxis: 'x' | 'z' | null; baseY: number; rise: number; run: number }
export interface Shell {
  spec: Spec; specHash: string; bounds: Part; storeys: Storey[]; volumes: Volume[];
  rooms: Room[]; openings: Opening[]; roofs: Roof[]; boxes: Box[];
  /** Empty for genVersion 1 files, which have no hooks key. */
  hooks: Hook[];
}
export interface Building {
  id: string; lots: string[]; origin: Vec3; rotation: 0 | 1 | 2 | 3; shell: Shell; siteBoxes: Box[];
}
export interface Violation { invariant: string; building: string | null; message: string; cells: Part[] }
export interface Scene {
  format: typeof SCENE_FORMAT; genVersion: number; seed: string;
  terrain: Terrain | null; lots: Lot[]; buildings: Building[];
  /** World-frame ground boxes (roads, paving, fill; layer ground); empty when the file has none. */
  ground: Box[]; violations: Violation[];
}

// ---- validation: every failure throws with a path such as `buildings[0].shell.boxes[3].role` ----

type Obj = Record<string, unknown>;

function fail(path: string, what: string): never {
  throw new Error(`fb-scene: ${path}: ${what}`);
}
function obj(v: unknown, path: string): Obj {
  if (typeof v !== 'object' || v === null || Array.isArray(v)) fail(path, 'must be an object');
  return v as Obj;
}
function field(o: Obj, key: string, path: string): unknown {
  if (!(key in o)) fail(`${path}.${key}`.replace(/^\./, ''), 'missing');
  return o[key];
}
const at = (path: string, key: string) => (path ? `${path}.${key}` : key);
function int(o: Obj, key: string, path: string): number {
  const v = field(o, key, path);
  if (typeof v !== 'number' || !Number.isInteger(v)) fail(at(path, key), `must be an integer, got ${JSON.stringify(v)}`);
  return v;
}
function str(o: Obj, key: string, path: string): string {
  const v = field(o, key, path);
  if (typeof v !== 'string') fail(at(path, key), 'must be a string');
  return v;
}
function oneOf<T extends string>(o: Obj, key: string, path: string, allowed: readonly T[]): T {
  const v = str(o, key, path);
  if (!(allowed as readonly string[]).includes(v)) fail(at(path, key), `must be one of ${allowed.join('|')}, got "${v}"`);
  return v as T;
}
function arr(o: Obj, key: string, path: string): unknown[] {
  const v = field(o, key, path);
  if (!Array.isArray(v)) fail(at(path, key), 'must be an array');
  return v;
}
function list<T>(o: Obj, key: string, path: string, f: (v: unknown, p: string) => T): T[] {
  return arr(o, key, path).map((v, i) => f(v, `${at(path, key)}[${i}]`));
}
function vec3(o: Obj, key: string, path: string): Vec3 {
  const v = field(o, key, path);
  if (!Array.isArray(v) || v.length !== 3 || !v.every((n) => typeof n === 'number' && Number.isInteger(n))) {
    fail(at(path, key), 'must be an array of 3 integers');
  }
  return v as Vec3;
}
function seedStr(o: Obj, key: string, path: string): string {
  const s = str(o, key, path);
  if (!/^-?\d+$/.test(s)) fail(at(path, key), `must be a decimal int64 string, got "${s}"`);
  return s;
}

const parseBox = (v: unknown, p: string): Box => {
  const o = obj(v, p);
  return {
    min: vec3(o, 'min', p), size: vec3(o, 'size', p),
    role: oneOf(o, 'role', p, ROLES), layer: oneOf(o, 'layer', p, LAYERS), storey: int(o, 'storey', p),
  };
};
const parsePart = (v: unknown, p: string): Part => {
  const o = obj(v, p);
  return { min: vec3(o, 'min', p), size: vec3(o, 'size', p) };
};
const parseLot = (v: unknown, p: string): Lot => {
  const o = obj(v, p);
  return {
    id: str(o, 'id', p), x: int(o, 'x', p), z: int(o, 'z', p), w: int(o, 'w', p), d: int(o, 'd', p),
    front: oneOf(o, 'front', p, FACINGS),
    ...('airRights' in o ? { airRights: int(o, 'airRights', p), frontY: int(o, 'frontY', p) } : {}),
  };
};

function parseShell(v: unknown, p: string): Shell {
  const o = obj(v, p);
  const sp = at(p, 'spec');
  const so = obj(field(o, 'spec', p), sp);
  const spec: Spec = {
    genVersion: int(so, 'genVersion', sp), specSeed: seedStr(so, 'specSeed', sp), style: str(so, 'style', sp),
    type: str(so, 'type', sp), width: int(so, 'width', sp), depth: int(so, 'depth', sp),
    storeys: int(so, 'storeys', sp), roof: str(so, 'roof', sp),
  };
  if ('maxHeight' in so) spec.maxHeight = int(so, 'maxHeight', sp);
  if ('streets' in so) spec.streets = int(so, 'streets', sp);
  if ('lead' in so) spec.lead = int(so, 'lead', sp);
  const specHash = str(o, 'specHash', p);
  if (!/^[0-9a-f]{16}$/.test(specHash)) fail(at(p, 'specHash'), 'must be 16 lower-case hex digits');
  return {
    spec, specHash,
    bounds: parsePart(field(o, 'bounds', p), at(p, 'bounds')),
    storeys: list(o, 'storeys', p, (x, q) => {
      const s = obj(x, q);
      return { index: int(s, 'index', q), y: int(s, 'y', q), height: int(s, 'height', q) };
    }),
    volumes: list(o, 'volumes', p, (x, q) => {
      const s = obj(x, q);
      const parent = field(s, 'parent', q);
      if (parent !== null && typeof parent !== 'string') fail(at(q, 'parent'), 'must be a string or null');
      return { id: str(s, 'id', q), min: vec3(s, 'min', q), size: vec3(s, 'size', q), parent };
    }),
    rooms: list(o, 'rooms', p, (x, q) => {
      const s = obj(x, q);
      return {
        id: str(s, 'id', q), storey: int(s, 'storey', q), volume: str(s, 'volume', q), use: str(s, 'use', q),
        parts: list(s, 'parts', q, parsePart),
      };
    }),
    openings: list(o, 'openings', p, (x, q) => {
      const s = obj(x, q);
      return {
        id: str(s, 'id', q), kind: oneOf(s, 'kind', q, OPENING_KINDS), min: vec3(s, 'min', q), size: vec3(s, 'size', q),
        facing: oneOf(s, 'facing', q, FACINGS), from: str(s, 'from', q), to: str(s, 'to', q),
      };
    }),
    roofs: list(o, 'roofs', p, (x, q) => {
      const s = obj(x, q);
      const ra = field(s, 'ridgeAxis', q);
      if (ra !== null && ra !== 'x' && ra !== 'z') fail(at(q, 'ridgeAxis'), 'must be "x", "z" or null');
      return {
        form: str(s, 'form', q), volume: str(s, 'volume', q), ridgeAxis: ra,
        baseY: int(s, 'baseY', q), rise: int(s, 'rise', q), run: int(s, 'run', q),
      };
    }),
    boxes: list(o, 'boxes', p, parseBox),
    hooks: 'hooks' in o ? list(o, 'hooks', p, (x, q) => {
      const s = obj(x, q);
      return {
        id: str(s, 'id', q), kind: str(s, 'kind', q), room: str(s, 'room', q), pos: vec3(s, 'pos', q),
        facing: oneOf(s, 'facing', q, FACINGS),
      };
    }) : [],
  };
}

/** Validate untrusted JSON and return it as a Scene. Throws `fb-scene: <path>: <problem>`. */
export function parseScene(json: unknown): Scene {
  const o = obj(json, '$');
  const format = field(o, 'format', '');
  if (format !== SCENE_FORMAT) fail('format', `unsupported "${String(format)}" (expected ${SCENE_FORMAT})`);

  let terrain: Terrain | null = null;
  const t = field(o, 'terrain', '');
  if (t !== null) {
    const to = obj(t, 'terrain');
    const nx = int(to, 'nx', 'terrain');
    const nz = int(to, 'nz', 'terrain');
    const floorY = list(to, 'floorY', 'terrain', (x, q) => {
      if (typeof x !== 'number' || !Number.isInteger(x)) fail(q, 'must be an integer');
      return x;
    });
    if (floorY.length !== nx * nz) fail('terrain.floorY', `length ${floorY.length} != nx*nz ${nx * nz}`);
    terrain = { x0: int(to, 'x0', 'terrain'), z0: int(to, 'z0', 'terrain'), nx, nz, floorY };
    if ('water' in to) {
      const water = list(to, 'water', 'terrain', (x, q) => {
        if (typeof x !== 'number' || !Number.isInteger(x) || x < 0) fail(q, 'must be a non-negative integer');
        return x;
      });
      if (water.length !== nx * nz) fail('terrain.water', `length ${water.length} != nx*nz ${nx * nz}`);
      terrain.water = water;
    }
  }

  const lots = list(o, 'lots', '', parseLot);
  const lotIds = new Set(lots.map((l) => l.id));
  const buildings = list(o, 'buildings', '', (x, p): Building => {
    const b = obj(x, p);
    const ids = list(b, 'lots', p, (s, q) => {
      if (typeof s !== 'string') fail(q, 'must be a string');
      if (!lotIds.has(s)) fail(q, `unknown lot id "${s}"`);
      return s;
    });
    const rotation = int(b, 'rotation', p);
    if (rotation < 0 || rotation > 3) fail(at(p, 'rotation'), `must be 0..3, got ${rotation}`);
    return {
      id: str(b, 'id', p), lots: ids, origin: vec3(b, 'origin', p), rotation: rotation as 0 | 1 | 2 | 3,
      shell: parseShell(field(b, 'shell', p), at(p, 'shell')),
      siteBoxes: list(b, 'siteBoxes', p, parseBox),
    };
  });
  const buildingIds = new Set(buildings.map((b) => b.id));
  const violations = list(o, 'violations', '', (x, p): Violation => {
    const v = obj(x, p);
    const b = field(v, 'building', p);
    if (b !== null && (typeof b !== 'string' || !buildingIds.has(b))) fail(at(p, 'building'), 'must be null or a known building id');
    return { invariant: str(v, 'invariant', p), building: b, message: str(v, 'message', p), cells: list(v, 'cells', p, parsePart) };
  });
  return {
    format: SCENE_FORMAT, genVersion: int(o, 'genVersion', ''), seed: seedStr(o, 'seed', ''),
    terrain, lots, buildings, ground: 'ground' in o ? list(o, 'ground', '', parseBox) : [], violations,
  };
}
