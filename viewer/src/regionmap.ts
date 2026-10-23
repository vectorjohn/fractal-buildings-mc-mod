// Region map: pure parts (parse, gzip detection, colours, drawing onto a 2D context).
// Reads `fb-region-dump/1` (docs/plans/phase5.md "Region maps"): the region dump from `fractal-cli region`
// (with `layers`), optionally over a terrain dump (same format, `layers: []`, any step) as background.
// Optional per-cell keys: `waterDist` (R2) and `claimedBy` (MC1); the map draws without them.

export interface Cell {
  cx: number; cz: number; siteX: number; siteZ: number;
  city: boolean; reason: string; tier: string;
  centreX: number; centreZ: number; radius: number;
  waterDist?: number;
  /** The cell that claimed this one (MC1), as [cx, cz]. */
  claimedBy?: [number, number];
  /** Port tag (ADR 0004 N6): a town or larger settlement near large water. */
  port?: boolean;
}
/** A link between two settlements (ROADMAP 6.10, `RoadJson`): station polyline `pts` [x,z,...] every 8 blocks, crossing spans `cross` [i,j,...] in station indices. */
export interface RoadEdge { a: [number, number]; b: [number, number]; w: number; pa: [number, number] | null; pb: [number, number] | null; pts: number[]; cross: number[] }
/** A pair no road joined: the ports (null: none found) and why. */
export interface RoadDropped { a: [number, number]; b: [number, number]; pa: [number, number] | null; pb: [number, number] | null; why: string }
export interface RoadNode { cx: number; cz: number; x: number; z: number; r: number; tier: string; port: boolean }
export interface RoadLayer { lgap: number; nodes: RoadNode[]; edges: RoadEdge[]; meet: { a: [number, number]; b: [number, number]; x: number; z: number }[]; dropped: RoadDropped[] }
export interface Layer { cellSize: number; policy: string; cells: Cell[]; roads?: RoadLayer }
export interface Grid { x0: number; z0: number; step: number; nx: number; nz: number }
export interface Dump {
  seed: number; grid: Grid; palette: string[]; biome: number[]; surface: number[]; water: number[]; layers: Layer[];
}

declare global {
  interface Window { __regionReady?: { ok: boolean; settled: number; cells: number; claims: number; heat: boolean; roads: Stats['roads']; error?: string } }
}

/** True for gzip data (`1f 8b` magic), whatever the file is called. */
export function isGzip(b: Uint8Array): boolean {
  return b.length > 2 && b[0] === 0x1f && b[1] === 0x8b;
}

export async function decodeBytes(b: Uint8Array): Promise<string> {
  if (!isGzip(b)) return new TextDecoder().decode(b);
  const stream = new Blob([b as BlobPart]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Response(stream).text();
}

function num(o: Record<string, unknown>, k: string, ctx: string): number {
  const v = o[k];
  if (typeof v !== 'number' || !Number.isFinite(v)) throw new Error(`${ctx}: "${k}" is not a number`);
  return v;
}
function ints(o: Record<string, unknown>, k: string, n: number): number[] {
  const v = o[k];
  if (!Array.isArray(v) || v.length !== n) throw new Error(`dump: "${k}" must be an array of ${n} numbers`);
  return v as number[];
}

/** claimedBy as MC1 may write it: [cx, cz], {cx, cz} or "cx,cz"; anything else (null, absent) means unclaimed. */
export function parseClaim(v: unknown): [number, number] | undefined {
  if (Array.isArray(v) && v.length === 2 && v.every((n) => typeof n === 'number')) return [v[0] as number, v[1] as number];
  if (v !== null && typeof v === 'object') {
    const o = v as Record<string, unknown>;
    if (typeof o['cx'] === 'number' && typeof o['cz'] === 'number') return [o['cx'], o['cz']];
  }
  if (typeof v === 'string') {
    const m = /^(-?\d+)[,\s]+(-?\d+)$/.exec(v);
    if (m) return [Number(m[1]), Number(m[2])];
  }
  return undefined;
}

function pair(v: unknown, ctx: string): [number, number] {
  if (!Array.isArray(v) || v.length !== 2 || v.some((n) => typeof n !== 'number')) throw new Error(`${ctx}: expected [x, z]`);
  return [v[0] as number, v[1] as number];
}

function maybePair(v: unknown, ctx: string): [number, number] | null {
  return v === null || v === undefined ? null : pair(v, ctx);
}

/** The optional `roads` object of a region layer (the core's `RoadJson`). */
export function parseRoads(r: Record<string, unknown>): RoadLayer {
  const list = (k: string): Record<string, unknown>[] => (r[k] as Record<string, unknown>[] | undefined) ?? [];
  return {
    lgap: typeof r['lgap'] === 'number' ? r['lgap'] : 1536,
    nodes: list('nodes').map((n) => ({ cx: num(n, 'cx', 'node'), cz: num(n, 'cz', 'node'), x: num(n, 'x', 'node'), z: num(n, 'z', 'node'), r: num(n, 'r', 'node'), tier: String(n['tier']), port: n['port'] === true })),
    edges: list('edges').map((e) => {
      const pts = ints(e, 'pts', (e['pts'] as unknown[]).length);
      if (pts.length % 2 !== 0) throw new Error('road edge: pts must hold x, z pairs');
      return { a: pair(e['a'], 'edge.a'), b: pair(e['b'], 'edge.b'), w: num(e, 'w', 'edge'), pa: maybePair(e['pa'], 'edge.pa'), pb: maybePair(e['pb'], 'edge.pb'), pts, cross: (e['cross'] as number[] | undefined) ?? [] };
    }),
    meet: list('meet').map((m) => ({ a: pair(m['a'], 'meet.a'), b: pair(m['b'], 'meet.b'), x: num(m, 'x', 'meet'), z: num(m, 'z', 'meet') })),
    dropped: list('dropped').map((d) => ({ a: pair(d['a'], 'dropped.a'), b: pair(d['b'], 'dropped.b'), pa: maybePair(d['pa'], 'dropped.pa'), pb: maybePair(d['pb'], 'dropped.pb'), why: String(d['why'] ?? '') })),
  };
}

export function parseDump(text: string): Dump {
  const j = JSON.parse(text) as Record<string, unknown>;
  if (j['format'] !== 'fb-region-dump/1') throw new Error(`not an fb-region-dump/1 file (format ${String(j['format'])})`);
  const g = j['grid'] as Record<string, unknown>;
  const grid: Grid = { x0: num(g, 'x0', 'grid'), z0: num(g, 'z0', 'grid'), step: num(g, 'step', 'grid'), nx: num(g, 'nx', 'grid'), nz: num(g, 'nz', 'grid') };
  const n = grid.nx * grid.nz;
  const layers: Layer[] = ((j['layers'] as Record<string, unknown>[] | undefined) ?? []).map((l) => ({
    cellSize: num(l, 'cellSize', 'layer'),
    policy: String(l['policy'] ?? ''),
    ...(l['roads'] ? { roads: parseRoads(l['roads'] as Record<string, unknown>) } : {}),
    cells: (l['cells'] as Record<string, unknown>[]).map((c) => {
      const cell: Cell = {
        cx: num(c, 'cx', 'cell'), cz: num(c, 'cz', 'cell'), siteX: num(c, 'siteX', 'cell'), siteZ: num(c, 'siteZ', 'cell'),
        city: c['city'] === true, reason: String(c['reason']), tier: String(c['tier'] ?? ''),
        centreX: num(c, 'centreX', 'cell'), centreZ: num(c, 'centreZ', 'cell'), radius: num(c, 'radius', 'cell'),
      };
      if (typeof c['waterDist'] === 'number' && c['waterDist'] >= 0) cell.waterDist = c['waterDist'];
      if (c['port'] === true) cell.port = true;
      const claim = parseClaim(c['claimedBy']);
      if (claim) cell.claimedBy = claim;
      return cell;
    }),
  }));
  return {
    seed: typeof j['seed'] === 'number' ? j['seed'] : 0, grid, palette: (j['palette'] as string[]) ?? [],
    biome: ints(j, 'biome', n), surface: ints(j, 'surface', n), water: ints(j, 'water', n), layers,
  };
}

export async function loadDump(url: string): Promise<Dump> {
  const r = await fetch(url);
  if (!r.ok) throw new Error(`${url}: HTTP ${r.status}`);
  return parseDump(await decodeBytes(new Uint8Array(await r.arrayBuffer())));
}

// ---- colours --------------------------------------------------------------------------------------------

export type RGB = [number, number, number];

const BIOME_COLOURS: Record<string, RGB> = {
  water: [40, 90, 160], mountain: [140, 140, 140], desert: [222, 200, 120], badlands: [190, 110, 60],
  savanna: [170, 170, 70], jungle: [30, 140, 50], swamp: [70, 100, 70], snow: [235, 240, 245],
  taiga: [60, 120, 100], shore: [225, 215, 160], temperate: [110, 180, 80],
};

/** Biome path (no namespace) to a colour group; same grouping as the core's BiomeClass. */
export function groupOf(b: string): string {
  if (b.endsWith('ocean') || b.endsWith('river') || b === 'mushroom_fields') return 'water';
  if (b.endsWith('peaks') || b.includes('slopes') || b.startsWith('windswept') || b === 'grove') return 'mountain';
  if (b === 'desert') return 'desert';
  if (b.includes('badlands')) return 'badlands';
  if (b.includes('savanna')) return 'savanna';
  if (b.includes('jungle')) return 'jungle';
  if (b.includes('swamp')) return 'swamp';
  if (b.includes('snowy') || b === 'ice_spikes' || b.includes('frozen')) return 'snow';
  if (b.includes('taiga')) return 'taiga';
  if (b === 'beach' || b === 'stony_shore') return 'shore';
  return 'temperate';
}

/** Terrain colour of one lattice column: biome group, shaded by height; water darkens with depth. */
export function terrainColour(group: string, surface: number, waterDepth: number): RGB {
  if (group === 'water' || waterDepth >= 2) {
    const f = Math.max(0.45, 1 - Math.min(waterDepth, 60) / 90);
    return [40 * f, 90 * f, 160 * f + 20];
  }
  const [r, g, b] = BIOME_COLOURS[group] ?? BIOME_COLOURS['temperate']!;
  const f = Math.min(1.15, Math.max(0.7, 0.8 + (surface - 63) / 250));
  return [Math.min(255, r * f), Math.min(255, g * f), Math.min(255, b * f)];
}

export const TIER_COLOUR: Record<string, string> = {
  hamlet: '#e8e8a0', village: '#ffd23f', town: '#ff8c1a', city: '#ff2e63', metropolis: '#c400ff',
};
export const REASON_COLOUR: Record<string, string> = { water: '#7cf', peak: '#fff', unsuitable: '#c96', roll: '#333' };

/** Water-distance heat: near water is hot (red), 768 blocks or more is cold (blue). */
export function heatColour(waterDist: number, max = 768): string {
  const t = 1 - Math.min(Math.max(waterDist, 0), max) / max;
  return `hsla(${Math.round(240 - 240 * t)}, 90%, 50%, 0.35)`;
}

// ---- drawing --------------------------------------------------------------------------------------------

export interface View { x0: number; z0: number; w: number; d: number; scale: number }
export interface Options { layer: number; heat: boolean; claims: boolean; grid: boolean; reasons: boolean; /** Road layer (6.10): links, crossings, meeting points, dropped pairs, port tags; drawn when the layer has `roads`. */ roads?: boolean }
export interface Stats { settled: number; cells: number; claims: number; heat: boolean; /** Links, crossing spans, meeting points, dropped pairs and port-tagged settlements drawn. */ roads: { links: number; crossings: number; meetings: number; dropped: number; ports: number } }

export function defaultView(g: Grid, maxPx = 1100): View {
  const w = g.nx * g.step, d = g.nz * g.step;
  return { x0: g.x0 - g.step / 2, z0: g.z0 - g.step / 2, w, d, scale: Math.min(1, maxPx / Math.max(w, d)) };
}

function paintTerrain(ctx: CanvasRenderingContext2D, t: Dump, v: View): void {
  const g = t.grid, groups = t.palette.map(groupOf);
  const cell = Math.max(1, g.step * v.scale);
  for (let j = 0; j < g.nz; j++) for (let i = 0; i < g.nx; i++) {
    const k = j * g.nx + i;
    const [r, gg, b] = terrainColour(groups[t.biome[k]!] ?? 'temperate', t.surface[k]!, t.water[k]!);
    ctx.fillStyle = `rgb(${r | 0},${gg | 0},${b | 0})`;
    const px = (g.x0 + i * g.step - g.step / 2 - v.x0) * v.scale, pz = (g.z0 + j * g.step - g.step / 2 - v.z0) * v.scale;
    ctx.fillRect(Math.floor(px), Math.floor(pz), Math.ceil(cell) + 1, Math.ceil(cell) + 1);
  }
}

/**
 * Draws terrain (the background dump, else the region dump itself) then the region layer. Returns counts.
 * Layers: cell grid lines, optional water-distance heat per cell, rejected-site dots by reason, settlement
 * disks by tier, claim links (cell site to the claimer's centre).
 */
export function drawRegion(ctx: CanvasRenderingContext2D, region: Dump, terrain: Dump | undefined, v: View, o: Options): Stats {
  ctx.canvas.width = Math.ceil(v.w * v.scale);
  ctx.canvas.height = Math.ceil(v.d * v.scale);
  ctx.fillStyle = '#101216';
  ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
  paintTerrain(ctx, terrain ?? region, v);
  const sx = (x: number): number => (x - v.x0) * v.scale, sz = (z: number): number => (z - v.z0) * v.scale;
  const L = region.layers[o.layer];
  const stats: Stats = { settled: 0, cells: 0, claims: 0, heat: false, roads: { links: 0, crossings: 0, meetings: 0, dropped: 0, ports: 0 } };
  if (!L) return stats;
  stats.cells = L.cells.length;
  const S = L.cellSize;

  if (o.grid) {
    ctx.strokeStyle = 'rgba(0,0,0,0.45)';
    ctx.lineWidth = 1;
    ctx.beginPath();
    for (let x = Math.ceil(v.x0 / S) * S; x < v.x0 + v.w; x += S) { ctx.moveTo(sx(x), 0); ctx.lineTo(sx(x), ctx.canvas.height); }
    for (let z = Math.ceil(v.z0 / S) * S; z < v.z0 + v.d; z += S) { ctx.moveTo(0, sz(z)); ctx.lineTo(ctx.canvas.width, sz(z)); }
    ctx.stroke();
  }
  const byCell = new Map<string, Cell>();
  for (const c of L.cells) byCell.set(`${c.cx},${c.cz}`, c);

  for (const c of L.cells) {
    if (o.heat && c.waterDist !== undefined) {
      stats.heat = true;
      ctx.fillStyle = heatColour(c.waterDist);
      ctx.fillRect(sx(c.cx * S), sz(c.cz * S), S * v.scale, S * v.scale);
    }
  }
  for (const c of L.cells) {
    if (c.city) continue;
    if (!o.reasons) continue;
    ctx.fillStyle = REASON_COLOUR[c.reason] ?? '#000';
    ctx.strokeStyle = '#000';
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.arc(sx(c.siteX), sz(c.siteZ), 3, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }
  for (const c of L.cells) {
    if (!c.city || c.claimedBy) continue; // members are drawn by their winner's disk
    stats.settled++;
    const col = TIER_COLOUR[c.tier] ?? '#fff';
    ctx.strokeStyle = col;
    ctx.fillStyle = `${col}40`;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(sx(c.centreX), sz(c.centreZ), Math.max(2.5, c.radius * v.scale), 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = col;
    ctx.beginPath();
    ctx.arc(sx(c.centreX), sz(c.centreZ), 3, 0, Math.PI * 2);
    ctx.fill();
  }
  if (o.claims) {
    ctx.strokeStyle = '#fff';
    ctx.setLineDash([4, 3]);
    ctx.lineWidth = 1.5;
    for (const c of L.cells) {
      if (!c.claimedBy) continue;
      const w = byCell.get(`${c.claimedBy[0]},${c.claimedBy[1]}`);
      if (!w) continue;
      stats.claims++;
      ctx.beginPath();
      ctx.moveTo(sx(c.siteX), sz(c.siteZ));
      ctx.lineTo(sx(w.centreX), sz(w.centreZ));
      ctx.stroke();
    }
    ctx.setLineDash([]);
  }
  if (o.roads !== false && L.roads) drawRoads(ctx, L, stats, sx, sz, v);
  return stats;
}

// ---- roads (ROADMAP 6.10) -------------------------------------------------------------------------------

export const ROAD_COLOUR = '#ffe14d';
export const CROSSING_COLOUR = '#26d9ff';
export const DROPPED_COLOUR = '#ff4a4a';
export const PORT_COLOUR = '#ffffff';

/** Stroke width of a link in px: the road's width in blocks, at least 2 px so it stays visible zoomed out. */
export function roadPx(widthBlocks: number, scale: number): number {
  return Math.max(2, widthBlocks * scale);
}

/**
 * Road layer of a region map: each link as a dark-edged yellow line along its stations, crossing spans (a flat single span over
 * water, at most 20 blocks) in cyan, a white dot at each meeting point of a touching pair, dropped pairs (no routable witness
 * blocks them but no dry route exists) as red dashes between their ports or centres, and a white ring on port-tagged settlements.
 */
function drawRoads(ctx: CanvasRenderingContext2D, L: Layer, stats: Stats, sx: (x: number) => number, sz: (z: number) => number, v: View): void {
  const R = L.roads!;
  const centre = new Map<string, { x: number; z: number }>();
  for (const n of R.nodes) centre.set(`${n.cx},${n.cz}`, { x: n.x, z: n.z });
  const at = (k: [number, number]): { x: number; z: number } | undefined => centre.get(`${k[0]},${k[1]}`);

  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.strokeStyle = DROPPED_COLOUR;
  ctx.lineWidth = 1.2;
  ctx.setLineDash([5, 4]);
  for (const d of R.dropped) {
    const a = d.pa ? { x: d.pa[0], z: d.pa[1] } : at(d.a), b = d.pb ? { x: d.pb[0], z: d.pb[1] } : at(d.b);
    if (!a || !b) continue;
    stats.roads.dropped++;
    ctx.beginPath();
    ctx.moveTo(sx(a.x), sz(a.z));
    ctx.lineTo(sx(b.x), sz(b.z));
    ctx.stroke();
  }
  ctx.setLineDash([]);

  const path = (e: RoadEdge, i0: number, i1: number): void => {
    ctx.beginPath();
    for (let i = i0; i <= i1; i++) {
      const x = sx(e.pts[2 * i]!), z = sz(e.pts[2 * i + 1]!);
      if (i === i0) ctx.moveTo(x, z);
      else ctx.lineTo(x, z);
    }
    ctx.stroke();
  };
  for (const pass of [0, 1, 2]) {
    for (const e of R.edges) {
      const n = e.pts.length / 2;
      if (n < 2) continue;
      const w = roadPx(e.w, v.scale);
      if (pass === 0) { // dark edge
        ctx.strokeStyle = '#1a1a1a';
        ctx.lineWidth = w + 2;
        path(e, 0, n - 1);
        stats.roads.links++;
      } else if (pass === 1) {
        ctx.strokeStyle = ROAD_COLOUR;
        ctx.lineWidth = w;
        path(e, 0, n - 1);
      } else {
        ctx.strokeStyle = CROSSING_COLOUR;
        ctx.lineWidth = w + 1;
        for (let k = 0; k + 1 < e.cross.length; k += 2) {
          path(e, e.cross[k]!, Math.min(n - 1, e.cross[k + 1]!));
          stats.roads.crossings++;
        }
      }
    }
  }
  ctx.lineCap = 'butt';

  for (const m of R.meet) {
    stats.roads.meetings++;
    ctx.fillStyle = PORT_COLOUR;
    ctx.strokeStyle = '#000';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.arc(sx(m.x), sz(m.z), 4, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }
  ctx.strokeStyle = PORT_COLOUR;
  ctx.lineWidth = 2;
  for (const n of R.nodes) {
    if (!n.port) continue;
    stats.roads.ports++;
    ctx.beginPath();
    ctx.arc(sx(n.x), sz(n.z), 7, 0, Math.PI * 2);
    ctx.stroke();
  }
}
