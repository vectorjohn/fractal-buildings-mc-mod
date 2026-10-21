/** View state <-> URL query. Pure; no DOM. */

export const ALL_LAYERS = ['terrain', 'water', 'foundation', 'shell', 'interior', 'roof', 'ground', 'violations'] as const;
export type LayerName = (typeof ALL_LAYERS)[number];

export interface ViewState {
  /** Palette style / batch directory; null = the scene's own style (or `plain`). */
  style: string | null;
  /** Seed within the current batch; null = the first one. */
  seed: number | null;
  layers: Set<string>;
  colour: 'role' | 'palette';
  /** Show storeys 0..maxStorey only; null = all. */
  maxStorey: number | null;
  /** Source overrides, passed through untouched: `?ir=<url>`, `?fixture=<name>`. */
  ir: string | null;
  fixture: string | null;
  /** `?mesh=faces|instanced`: force the mesher; auto = faces above FACE_MESH_THRESHOLD voxels. */
  mesh: 'auto' | 'faces' | 'instanced';
  /** `?exterior=1`: drop the interior layer (and hook markers). */
  exterior: boolean;
  /** `?camera=fly`: FlyControls instead of orbit. */
  camera: 'orbit' | 'fly';
  /** `?stops=x,y,z,yaw,pitch;...` raw; parsed by fly.ts. */
  stops: string | null;
}

export function defaultState(): ViewState {
  return { style: null, seed: null, layers: new Set(ALL_LAYERS), colour: 'role', maxStorey: null, ir: null, fixture: null, mesh: 'auto', exterior: false, camera: 'orbit', stops: null };
}

function intParam(v: string | null): number | null {
  if (v === null || !/^-?\d+$/.test(v)) return null;
  return Number(v);
}

export function parseState(query: string): ViewState {
  const p = new URLSearchParams(query);
  const s = defaultState();
  s.style = p.get('style') || null;
  s.seed = intParam(p.get('seed'));
  const layers = p.get('layers');
  // `layers=` (present, empty) means no layers; absent means all.
  if (layers !== null) s.layers = new Set(layers.split(',').filter((l) => (ALL_LAYERS as readonly string[]).includes(l)));
  s.colour = p.get('colour') === 'palette' ? 'palette' : 'role';
  const ms = intParam(p.get('storeys'));
  s.maxStorey = ms !== null && ms >= 0 ? ms : null;
  s.ir = p.get('ir') || null;
  s.fixture = p.get('fixture') || null;
  const mesh = p.get('mesh');
  s.mesh = mesh === 'faces' || mesh === 'instanced' ? mesh : 'auto';
  s.exterior = p.get('exterior') === '1';
  s.camera = p.get('camera') === 'fly' ? 'fly' : 'orbit';
  s.stops = p.get('stops') || null;
  return s;
}

/** Query string (no leading `?`) with defaults omitted; layer order is canonical so equal states give equal URLs. */
export function serializeState(s: ViewState): string {
  const p = new URLSearchParams();
  if (s.ir) p.set('ir', s.ir);
  if (s.fixture) p.set('fixture', s.fixture);
  if (s.style) p.set('style', s.style);
  if (s.seed !== null) p.set('seed', String(s.seed));
  if (s.layers.size !== ALL_LAYERS.length) p.set('layers', ALL_LAYERS.filter((l) => s.layers.has(l)).join(','));
  if (s.colour !== 'role') p.set('colour', s.colour);
  if (s.maxStorey !== null) p.set('storeys', String(s.maxStorey));
  if (s.mesh !== 'auto') p.set('mesh', s.mesh);
  if (s.exterior) p.set('exterior', '1');
  if (s.camera === 'fly') p.set('camera', 'fly');
  if (s.stops) p.set('stops', s.stops);
  return p.toString().replace(/%2C/g, ',').replace(/%3B/g, ';');
}

/** The seed `delta` places along `seeds` from `current` (clamped, not wrapped); the first seed if `current` is absent or unknown. */
export function stepSeed(seeds: readonly number[], current: number | null, delta: number): number | null {
  if (seeds.length === 0) return null;
  const i = current === null ? -1 : seeds.indexOf(current);
  if (i < 0) return seeds[0]!;
  return seeds[Math.min(seeds.length - 1, Math.max(0, i + delta))]!;
}

/** Seed to show given the batch's seeds: the requested one if it exists, else the first. */
export function resolveSeed(seeds: readonly number[], wanted: number | null): number | null {
  if (seeds.length === 0) return null;
  return wanted !== null && seeds.includes(wanted) ? wanted : seeds[0]!;
}

export function toggleLayer(s: ViewState, layer: string, on: boolean): ViewState {
  const layers = new Set(s.layers);
  if (on) layers.add(layer); else layers.delete(layer);
  return { ...s, layers };
}

/**
 * Is a box of this storey shown under "storeys up to max"? Storey -1 (foundation) is kept only when max
 * reaches the top storey, i.e. when nothing is hidden.
 */
export function storeyVisible(storey: number, max: number | null, top: number): boolean {
  if (max === null || max >= top) return true;
  return storey >= 0 && storey <= max;
}

/** Latest-wins ticket gate for overlapping async rebuilds: a stale one must not publish its result. */
export function latestGate(): { begin(): number; isLatest(ticket: number): boolean } {
  let n = 0;
  return { begin: () => ++n, isLatest: (t) => t === n };
}
