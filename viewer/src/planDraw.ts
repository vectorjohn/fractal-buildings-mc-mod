// Plan view: pure parts (view transform, LOD rule, base rasters, drawing onto a 2D context). The page is plan.ts.
import { groupOf, terrainColour, type Dump, type RGB } from './regionmap';
import type { District, DistrictSummary, PlanLot, PlanViolation, RoadKind, Settlement } from './settlement';

// ---- view transform and LOD ------------------------------------------------------------------------------

export const MIN_SCALE = 1 / 16;
export const MAX_SCALE = 8;
/** Lots (and district streets) are drawn and loaded only at or above this many pixels per block. */
export const LOT_SCALE = 0.5;

/** `x0`, `z0`: the world column at the canvas' top-left corner; `scale`: pixels per block. */
export interface PlanView { x0: number; z0: number; scale: number }
export interface Size { w: number; h: number }

export const toScreen = (v: PlanView, x: number, z: number): [number, number] => [(x - v.x0) * v.scale, (z - v.z0) * v.scale];
export const toWorld = (v: PlanView, px: number, py: number): [number, number] => [px / v.scale + v.x0, py / v.scale + v.z0];
export const clampScale = (s: number): number => Math.min(MAX_SCALE, Math.max(MIN_SCALE, s));

/** Zooms by `factor` keeping the world point under pixel (px, py) fixed. */
export function zoomAt(v: PlanView, px: number, py: number, factor: number): PlanView {
  const scale = clampScale(v.scale * factor);
  const [wx, wz] = toWorld(v, px, py);
  return { x0: wx - px / scale, z0: wz - py / scale, scale };
}
export function pan(v: PlanView, dxPx: number, dyPx: number): PlanView {
  return { ...v, x0: v.x0 - dxPx / v.scale, z0: v.z0 - dyPx / v.scale };
}
/** View showing the whole rectangle with a margin of 4 percent. */
export function fitView(b: { x: number; z: number; w: number; d: number }, size: Size): PlanView {
  const scale = clampScale(Math.min(size.w / (b.w * 1.08), size.h / (b.d * 1.08)));
  return { x0: b.x + b.w / 2 - size.w / 2 / scale, z0: b.z + b.d / 2 - size.h / 2 / scale, scale };
}
export const showLots = (scale: number): boolean => scale >= LOT_SCALE;

export interface Rect { x: number; z: number; w: number; d: number }
export const viewRect = (v: PlanView, s: Size): Rect => ({ x: v.x0, z: v.z0, w: s.w / v.scale, d: s.h / v.scale });
export const overlaps = (a: Rect, b: Rect): boolean => a.x < b.x + b.w && b.x < a.x + a.w && a.z < b.z + b.d && b.z < a.z + a.d;

/** Districts whose tile intersects the view (all of them when the view is outside the plan: none). */
export function visibleDistricts(s: Settlement, v: PlanView, size: Size): DistrictSummary[] {
  const r = viewRect(v, size);
  return s.districts.filter((d) => overlaps(r, d));
}
/** The districts to load now: visible, with a detail file, and only when lots are shown at this zoom. */
export function districtsToLoad(s: Settlement, v: PlanView, size: Size): DistrictSummary[] {
  return showLots(v.scale) ? visibleDistricts(s, v, size).filter((d) => d.detail !== null) : [];
}

// ---- layers ----------------------------------------------------------------------------------------------

export const LAYER_IDS = [
  'terrain', 'water', 'biome', 'mask', 'density', 'quarters', 'cells', 'tiles', 'roads', 'gates', 'landmarks', 'walls', 'lots', 'violations',
] as const;
export type LayerId = (typeof LAYER_IDS)[number];
export const DEFAULT_LAYERS: readonly LayerId[] = ['terrain', 'water', 'tiles', 'roads', 'gates', 'landmarks', 'walls', 'lots', 'violations'];

/** `layers=a,b` selects exactly those; `layers=+a,-b` edits the defaults. Unknown names throw. */
export function parseLayers(q: string | null): Set<LayerId> {
  if (!q) return new Set(DEFAULT_LAYERS);
  const parts = q.split(',').map((p) => (p.startsWith(' ') ? '+' + p.trim() : p.trim())).filter((p) => p !== ''); // a raw + in a URL query decodes to a space
  const edit = parts.some((p) => p.startsWith('+') || p.startsWith('-'));
  const out = new Set<LayerId>(edit ? DEFAULT_LAYERS : []);
  for (const p of parts) {
    const name = p.replace(/^[+-]/, '');
    if (!(LAYER_IDS as readonly string[]).includes(name)) throw new Error(`unknown layer "${name}" (known: ${LAYER_IDS.join(', ')})`);
    if (p.startsWith('-')) out.delete(name as LayerId); else out.add(name as LayerId);
  }
  return out;
}
export const layersParam = (l: Set<LayerId>): string => LAYER_IDS.filter((x) => l.has(x)).join(',');

// ---- colours ---------------------------------------------------------------------------------------------

export const ROAD_COLOUR: Record<RoadKind, string> = { arterial: '#f4d35e', main: '#f2f2f2', street: '#d6d0c4', lane: '#a89f8c', path: '#8a7a5c' };
export const LOT_COLOUR: Record<string, string> = {
  house: '#c9805a', cottage: '#d49a6a', shop: '#4aa3d8', workshop: '#8a6bbf', tower: '#c8c8d8', inn: '#e0a030', hall: '#d05a8a',
  temple: '#f0e68c', barn: '#9a8a3a', warehouse: '#7a8a96', block: '#b23a3a', highrise: '#3a4ab2',
};
const QUARTER_COLOUR: Record<string, RGB> = {
  downtown: [220, 60, 60], 'old-town': [200, 140, 60], market: [240, 200, 40], residential: [90, 170, 90], workshop: [140, 100, 190],
  waterfront: [60, 160, 200], park: [60, 200, 100], outskirts: [150, 150, 150],
};
export const STYLE_COLOUR: Record<string, string> = {
  plains: '#3b6e2a', plain: '#44505a', desert: '#8a6a10', medieval: '#7a1f1f', jungle: '#0f6a30', snowy: '#7aa0c0', taiga: '#2a5a6a',
};
export const VIOLATION_COLOUR = '#ff2020';

function hashColour(s: string): string {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return `hsl(${Math.abs(h) % 360}, 55%, 60%)`;
}
export const lotColour = (type: string): string => LOT_COLOUR[type] ?? hashColour(type);
export const styleColour = (style: string): string => STYLE_COLOUR[style] ?? hashColour(style);

/** Density heat 0..100: transparent-ish blue to hot red. */
export function densityColour(d: number): string {
  const t = Math.min(100, Math.max(0, d)) / 100;
  return `hsla(${Math.round(220 - 220 * t)}, 85%, 50%, 0.55)`;
}

// ---- base rasters ----------------------------------------------------------------------------------------

export interface Raster { w: number; h: number; data: Uint8ClampedArray }

/** Lambert-style shade from the height gradient (light from the north-west); 1 on flat ground. */
export function hillshade(h: ArrayLike<number>, nx: number, nz: number, i: number, j: number, step: number): number {
  const at = (a: number, b: number): number => h[Math.min(nz - 1, Math.max(0, b)) * nx + Math.min(nx - 1, Math.max(0, a))]!;
  const gx = (at(i + 1, j) - at(i - 1, j)) / (2 * step), gz = (at(i, j + 1) - at(i, j - 1)) / (2 * step);
  return Math.min(1.35, Math.max(0.65, 1 + 4 * (-gx - gz) * 0.5));
}

export interface BaseLayers { terrain: boolean; water: boolean; biome: boolean; mask: boolean; density: boolean; quarters: boolean }
export const baseLayers = (l: Set<LayerId>): BaseLayers => ({
  terrain: l.has('terrain'), water: l.has('water'), biome: l.has('biome'), mask: l.has('mask'), density: l.has('density'), quarters: l.has('quarters'),
});

const blend = (dst: Uint8ClampedArray, o: number, c: RGB, a: number): void => {
  dst[o] = dst[o]! * (1 - a) + c[0] * a; dst[o + 1] = dst[o + 1]! * (1 - a) + c[1] * a; dst[o + 2] = dst[o + 2]! * (1 - a) + c[2] * a;
};
const MASK_COLOUR: Record<number, RGB> = { 1: [255, 255, 255], 2: [40, 90, 200], 3: [90, 60, 40], 4: [60, 220, 90] };

/** One pixel per coarse lattice column: hillshaded terrain, water, biome tint, urban mask, density heat, quarters. */
export function coarseRaster(s: Settlement, l: BaseLayers): Raster {
  const c = s.coarse, n = c.nx * c.nz;
  const data = new Uint8ClampedArray(n * 4);
  const groups = c.biomes.map((b) => groupOf(b.replace(/^[^:]*:/, '')));
  for (let j = 0; j < c.nz; j++) for (let i = 0; i < c.nx; i++) {
    const k = j * c.nx + i, o = k * 4;
    let col: RGB = [38, 40, 46];
    const wet = l.water && c.water[k]! >= 2;
    if (wet) col = terrainColour('water', c.floorY[k]!, c.water[k]!);
    else if (l.biome || l.terrain) {
      col = l.biome ? terrainColour(groups[c.biome[k]!] ?? 'temperate', c.floorY[k]!, 0) : [125, 140, 115];
      if (l.terrain) { const sh = hillshade(c.floorY, c.nx, c.nz, i, j, c.step); col = [col[0] * sh, col[1] * sh, col[2] * sh]; }
    }
    data[o] = col[0]; data[o + 1] = col[1]; data[o + 2] = col[2]; data[o + 3] = 255;
    if (l.mask && MASK_COLOUR[c.mask[k]!]) blend(data, o, MASK_COLOUR[c.mask[k]!]!, c.mask[k] === 1 ? 0.25 : 0.4);
    if (l.quarters && c.quarter[k]! >= 0) blend(data, o, QUARTER_COLOUR[s.quarters[c.quarter[k]!]?.kind ?? ''] ?? [200, 200, 200], 0.45);
    if (l.density && c.density[k]! > 0) { const d = c.density[k]! / 100; blend(data, o, [255 * d + 40, 220 * (1 - d) + 20, 40], 0.25 + 0.4 * d); }
  }
  return { w: c.nx, h: c.nz, data };
}

/** The same for a fine terrain dump (`fb-region-dump/1`, hillshaded). */
export function terrainRaster(t: Dump): Raster {
  const g = t.grid, groups = t.palette.map(groupOf);
  const data = new Uint8ClampedArray(g.nx * g.nz * 4);
  for (let j = 0; j < g.nz; j++) for (let i = 0; i < g.nx; i++) {
    const k = j * g.nx + i, o = k * 4;
    let col = terrainColour(groups[t.biome[k]!] ?? 'temperate', t.surface[k]!, t.water[k]!);
    if (t.water[k]! < 2) { const sh = hillshade(t.surface, g.nx, g.nz, i, j, g.step); col = [col[0] * sh, col[1] * sh, col[2] * sh]; }
    data[o] = col[0]; data[o + 1] = col[1]; data[o + 2] = col[2]; data[o + 3] = 255;
  }
  return { w: g.nx, h: g.nz, data };
}

// ---- drawing ---------------------------------------------------------------------------------------------

export interface PlanState {
  s: Settlement;
  districts: ReadonlyMap<string, District>;
  view: PlanView;
  size: Size;
  layers: Set<LayerId>;
  /** Cached canvases (coarse base, terrain dump) with the world rectangle each covers. */
  images: { src: CanvasImageSource; rect: Rect }[];
  hover?: { district: string | null; lot: string | null };
}
export interface DrawStats { draws: number; lots: number; districtsLoaded: number; lod: 'overview' | 'detail'; roads: number }

function polyline(ctx: CanvasRenderingContext2D, v: PlanView, pts: readonly (readonly [number, number])[], from = 0, to = pts.length - 1): void {
  ctx.beginPath();
  for (let i = from; i <= to; i++) {
    const [px, py] = toScreen(v, pts[i]![0] + 0.5, pts[i]![1] + 0.5);
    if (i === from) ctx.moveTo(px, py); else ctx.lineTo(px, py);
  }
  ctx.stroke();
}

function drawRoadSet(ctx: CanvasRenderingContext2D, v: PlanView, roads: readonly { kind: RoadKind; width: number; points: [number, number][]; bridges?: [number, number][] }[]): number {
  let n = 0;
  ctx.lineCap = 'butt'; ctx.lineJoin = 'round';
  const order: RoadKind[] = ['path', 'lane', 'street', 'main', 'arterial'];
  for (const kind of order) {
    for (const r of roads) {
      if (r.kind !== kind || r.points.length < 2) continue;
      const wpx = Math.max(1, r.width * v.scale);
      ctx.lineWidth = wpx + (wpx > 3 ? 2 : 0); ctx.strokeStyle = '#1b1d22';
      if (wpx > 3) polyline(ctx, v, r.points);
      ctx.lineWidth = wpx; ctx.strokeStyle = ROAD_COLOUR[kind];
      polyline(ctx, v, r.points);
      n++;
      for (const [i, j] of r.bridges ?? []) {
        ctx.lineWidth = wpx + 2; ctx.strokeStyle = '#2a2018'; polyline(ctx, v, r.points, i, j);
        ctx.lineWidth = wpx; ctx.strokeStyle = '#c9a66b'; polyline(ctx, v, r.points, i, j);
      }
    }
  }
  return n;
}

function drawLot(ctx: CanvasRenderingContext2D, v: PlanView, l: PlanLot, hover: boolean): void {
  const [px, py] = toScreen(v, l.x, l.z), w = l.w * v.scale, h = l.d * v.scale;
  const built = l.status === 'build';
  ctx.globalAlpha = built ? 1 : 0.35;
  ctx.fillStyle = lotColour(l.type);
  ctx.fillRect(px, py, w, h);
  ctx.globalAlpha = 1;
  ctx.strokeStyle = hover ? '#ffffff' : styleColour(l.style);
  ctx.lineWidth = hover ? 2 : Math.min(2, Math.max(1, v.scale * 0.5));
  ctx.strokeRect(px + 0.5, py + 0.5, Math.max(0, w - 1), Math.max(0, h - 1));
  if (v.scale >= 2) { // front edge marker
    ctx.strokeStyle = '#111'; ctx.lineWidth = 2; ctx.beginPath();
    if (l.front === 'north') { ctx.moveTo(px, py); ctx.lineTo(px + w, py); }
    else if (l.front === 'south') { ctx.moveTo(px, py + h); ctx.lineTo(px + w, py + h); }
    else if (l.front === 'west') { ctx.moveTo(px, py); ctx.lineTo(px, py + h); }
    else { ctx.moveTo(px + w, py); ctx.lineTo(px + w, py + h); }
    ctx.stroke();
  }
}

function drawViolations(ctx: CanvasRenderingContext2D, v: PlanView, list: readonly PlanViolation[]): number {
  let n = 0;
  ctx.strokeStyle = VIOLATION_COLOUR; ctx.fillStyle = 'rgba(255,32,32,0.45)'; ctx.lineWidth = 2;
  for (const viol of list) for (const c of viol.cells) {
    const [px, py] = toScreen(v, c.min[0], c.min[2]);
    const w = Math.max(6, c.size[0] * v.scale), h = Math.max(6, c.size[2] * v.scale);
    ctx.fillRect(px, py, w, h); ctx.strokeRect(px, py, w, h);
    n++;
  }
  return n;
}

/** Draws the whole plan for the state; returns counts the page and the tests read. */
export function drawPlan(ctx: CanvasRenderingContext2D, st: PlanState): DrawStats {
  const { s, view: v, size, layers } = st;
  ctx.canvas.width = size.w; ctx.canvas.height = size.h;
  ctx.fillStyle = '#101216'; ctx.fillRect(0, 0, size.w, size.h);
  const stats: DrawStats = { draws: 0, lots: 0, districtsLoaded: 0, lod: showLots(v.scale) ? 'detail' : 'overview', roads: 0 };
  const smooth = v.scale < 1;
  ctx.imageSmoothingEnabled = smooth;
  for (const im of st.images) {
    const [px, py] = toScreen(v, im.rect.x, im.rect.z);
    ctx.drawImage(im.src, px, py, im.rect.w * v.scale, im.rect.d * v.scale);
    stats.draws++;
  }
  const vis = visibleDistricts(s, v, size);
  const rect = (d: Rect): [number, number, number, number] => { const [px, py] = toScreen(v, d.x, d.z); return [px, py, d.w * v.scale, d.d * v.scale]; };

  if (layers.has('cells')) {
    ctx.strokeStyle = 'rgba(255,255,255,0.5)'; ctx.setLineDash([6, 4]); ctx.lineWidth = 1.5;
    for (const c of s.cells) {
      if (c.polygon.length < 2) continue;
      ctx.beginPath();
      c.polygon.forEach((p, i) => { const [px, py] = toScreen(v, p[0], p[1]); if (i === 0) ctx.moveTo(px, py); else ctx.lineTo(px, py); });
      ctx.closePath(); ctx.stroke();
    }
    ctx.setLineDash([]);
  }
  if (!showLots(v.scale) && (layers.has('lots') || layers.has('density'))) {
    // overview: the district density stands in for lots
    for (const d of vis) {
      ctx.fillStyle = densityColour(d.density);
      ctx.fillRect(...rect(d));
      stats.draws++;
    }
  }
  if (layers.has('tiles')) {
    ctx.strokeStyle = 'rgba(0,0,0,0.55)'; ctx.lineWidth = 1;
    for (const d of vis) { const [px, py, w, h] = rect(d); ctx.strokeRect(px + 0.5, py + 0.5, w, h); }
  }
  const loaded = vis.map((d) => st.districts.get(d.id)).filter((d): d is District => d !== undefined);
  stats.districtsLoaded = loaded.length;
  if (showLots(v.scale) && layers.has('lots')) {
    for (const d of loaded) {
      for (const p of d.plazas) { const [px, py, w, h] = rect(p); ctx.fillStyle = '#b8ad96'; ctx.fillRect(px, py, w, h); }
    }
  }
  if (layers.has('roads')) {
    stats.roads += drawRoadSet(ctx, v, s.roads);
    if (showLots(v.scale)) {
      const known = new Set(s.roads.map((r) => r.id));
      for (const d of loaded) stats.roads += drawRoadSet(ctx, v, d.streets.filter((x) => !known.has(x.id)));
    }
  }
  if (showLots(v.scale) && layers.has('lots')) {
    for (const d of loaded) for (const l of d.lots) {
      if (!overlaps(viewRect(v, size), l)) continue;
      drawLot(ctx, v, l, st.hover?.lot === l.id);
      stats.lots++;
    }
  }
  if (layers.has('walls')) {
    ctx.strokeStyle = '#6b3a2a'; ctx.lineWidth = Math.max(2, 3 * v.scale);
    for (const w of s.walls) {
      if (w.points.length < 2) continue;
      polyline(ctx, v, w.points);
      if (w.closed) {
        const a = toScreen(v, w.points[w.points.length - 1]![0], w.points[w.points.length - 1]![1]), b = toScreen(v, w.points[0]![0], w.points[0]![1]);
        ctx.beginPath(); ctx.moveTo(a[0], a[1]); ctx.lineTo(b[0], b[1]); ctx.stroke();
      }
    }
  }
  if (layers.has('landmarks')) {
    for (const l of s.landmarks) {
      const [px, py, w, h] = rect(l);
      ctx.fillStyle = 'rgba(255,230,120,0.28)'; ctx.fillRect(px, py, w, h);
      ctx.strokeStyle = '#ffe678'; ctx.lineWidth = 2; ctx.strokeRect(px, py, w, h);
      ctx.fillStyle = '#ffe678'; ctx.font = '11px sans-serif';
      if (v.scale >= 0.1) ctx.fillText(l.kind, px + 3, py + 12);
      stats.draws++;
    }
  }
  if (layers.has('gates')) {
    for (const g of s.gates) {
      const [px, py] = toScreen(v, g.x + 0.5, g.z + 0.5);
      ctx.fillStyle = g.kind === 'wall' ? '#d08040' : g.kind === 'cell' ? '#40d0d0' : '#ffffff';
      ctx.strokeStyle = '#000'; ctx.lineWidth = 1.5;
      ctx.beginPath(); ctx.rect(px - 4, py - 4, 8, 8); ctx.fill(); ctx.stroke();
    }
  }
  if (layers.has('violations')) {
    stats.draws += drawViolations(ctx, v, s.violations);
    for (const d of loaded) stats.draws += drawViolations(ctx, v, d.violations);
  }
  if (st.hover?.district) {
    const d = s.districts.find((x) => x.id === st.hover!.district);
    if (d) { ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 2; ctx.strokeRect(...rect(d)); }
  }
  stats.draws += stats.roads + stats.lots;
  return stats;
}

// ---- hover -----------------------------------------------------------------------------------------------

export function districtAt(s: Settlement, x: number, z: number): DistrictSummary | null {
  for (const d of s.districts) if (x >= d.x && x < d.x + d.w && z >= d.z && z < d.z + d.d) return d;
  return null;
}
export function lotAt(d: District, x: number, z: number): PlanLot | null {
  for (const l of d.lots) if (x >= l.x && x < l.x + l.w && z >= l.z && z < l.z + l.d) return l;
  return null;
}
