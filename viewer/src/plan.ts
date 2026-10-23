// Plan view page (docs/VIEWER.md "Plan view"). URL: plan.html?city=<dir>[&terrain=<url>][&layers=a,b | +a,-b][&x=&z=&zoom=]
// <dir> holds settlement.json (fb-settlement/1) and the district files it names. Files may be gzip (magic bytes).
import { loadDump } from './regionmap';
import {
  LAYER_IDS, LOT_SCALE, baseLayers, coarseRaster, districtAt, districtsToLoad, drawPlan, fitView, layersParam, lotAt, lotColour, pan,
  parseLayers, showLots, terrainRaster, toWorld, zoomAt, ROAD_COLOUR, LINK_COLOUR, type DrawStats, type LayerId, type PlanView, type Raster,
} from './planDraw';
import { joinUrl, loadDistrict, loadSettlement, type District, type Settlement } from './settlement';

export interface PlanReady {
  ok: boolean; error?: string;
  /** Districts in the plan / loaded now / still loading. */
  districts: number; loaded: number; pending: number;
  draws: number; lots: number; roads: number; links: number; lod: 'overview' | 'detail'; scale: number; lastDrawMs: number;
}
export interface PlanApi {
  setView(x: number, z: number, scale: number): void;
  /** Redraws synchronously and returns the milliseconds the draw took. */
  redraw(): number;
}
declare global {
  interface Window { __planReady?: PlanReady; __planApi?: PlanApi }
}

const CACHE_MAX = 400;
const PARALLEL = 8;

function raster(r: Raster): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = r.w; c.height = r.h;
  c.getContext('2d')!.putImageData(new ImageData(r.data as Uint8ClampedArray<ArrayBuffer>, r.w, r.h), 0, 0);
  return c;
}

async function main(): Promise<void> {
  const q = new URLSearchParams(location.search);
  const city = q.get('city');
  if (!city) throw new Error('missing ?city=<dir> (a directory with settlement.json, e.g. from `fractal-cli sample-city --out-dir`)');
  const s: Settlement = await loadSettlement(city);
  const terrain = q.get('terrain') ? await loadDump(q.get('terrain')!) : undefined;
  let layers: Set<LayerId> = parseLayers(q.get('layers'));

  const canvas = document.getElementById('c') as HTMLCanvasElement;
  const stage = document.getElementById('stage')!;
  const ctx = canvas.getContext('2d')!;
  const size = (): { w: number; h: number } => ({ w: Math.max(100, stage.clientWidth), h: Math.max(100, stage.clientHeight) });
  let view: PlanView = fitView(s.bounds, size());
  if (q.get('zoom')) view = { ...view, scale: Number(q.get('zoom')) };
  if (q.get('x') !== null && q.get('z') !== null) {
    const sz = size();
    view = { x0: Number(q.get('x')) - sz.w / 2 / view.scale, z0: Number(q.get('z')) - sz.h / 2 / view.scale, scale: view.scale };
  }

  const c = s.coarse;
  const coarseRect = { x: c.x0 - c.step / 2, z: c.z0 - c.step / 2, w: c.nx * c.step, d: c.nz * c.step };
  let coarseImg = raster(coarseRaster(s, baseLayers(layers)));
  let coarseKey = JSON.stringify(baseLayers(layers));
  const terrainImg = terrain ? raster(terrainRaster(terrain)) : undefined;
  const terrainRect = terrain
    ? { x: terrain.grid.x0 - terrain.grid.step / 2, z: terrain.grid.z0 - terrain.grid.step / 2, w: terrain.grid.nx * terrain.grid.step, d: terrain.grid.nz * terrain.grid.step }
    : undefined;

  const districts = new Map<string, District>(); // insertion order = LRU order
  const inflight = new Set<string>();
  const failed = new Set<string>();
  let hover: { district: string | null; lot: string | null } = { district: null, lot: null };
  let selected: string | null = null;
  let last: DrawStats = { draws: 0, lots: 0, districtsLoaded: 0, lod: 'overview', roads: 0, links: 0 };
  let lastMs = 0;
  let queued = false;

  const draw = (): number => {
    const t0 = performance.now();
    const k = JSON.stringify(baseLayers(layers));
    if (k !== coarseKey) { coarseImg = raster(coarseRaster(s, baseLayers(layers))); coarseKey = k; }
    const images: { src: CanvasImageSource; rect: typeof coarseRect }[] = [{ src: coarseImg, rect: coarseRect }];
    if (terrainImg && terrainRect && layers.has('terrain')) images.push({ src: terrainImg, rect: terrainRect });
    last = drawPlan(ctx, { s, districts, view, size: size(), layers, images, hover });
    lastMs = performance.now() - t0;
    publish();
    return lastMs;
  };
  const schedule = (): void => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => { queued = false; draw(); loadVisible(); describe(); });
  };

  const publish = (ok = true, error?: string): void => {
    window.__planReady = {
      ok, ...(error ? { error } : {}), districts: s.districts.length, loaded: districts.size, pending: inflight.size,
      draws: last.draws, lots: last.lots, roads: last.roads, links: last.links, lod: last.lod, scale: view.scale, lastDrawMs: lastMs,
    };
  };

  const pump = (): void => {
    const todo = districtsToLoad(s, view, size()).filter((d) => !districts.has(d.id) && !inflight.has(d.id) && !failed.has(d.id));
    for (const d of todo) {
      if (inflight.size >= PARALLEL) break;
      inflight.add(d.id);
      loadDistrict(city, d.detail!).then((dd) => {
        districts.set(d.id, dd);
        while (districts.size > CACHE_MAX) districts.delete(districts.keys().next().value!);
      }).catch((e: unknown) => { failed.add(d.id); console.warn(`district ${d.id}: ${String(e)}`); })
        .finally(() => { inflight.delete(d.id); schedule(); });
    }
  };
  const loadVisible = (): void => { pump(); publish(); };

  // ---- panel
  const layerBox = document.getElementById('layers')!;
  for (const id of LAYER_IDS) {
    const l = document.createElement('label');
    const cb = document.createElement('input');
    cb.type = 'checkbox'; cb.checked = layers.has(id); cb.dataset['layer'] = id;
    cb.addEventListener('change', () => { if (cb.checked) layers.add(id); else layers.delete(id); syncUrl(); schedule(); });
    l.append(cb, ` ${id}`);
    layerBox.append(l);
  }
  document.getElementById('title')!.textContent = `${s.site.id} (${s.site.tier}), seed ${s.seed}`;
  const sw = (col: string): string => `<span class="sw" style="background:${col}"></span>`;
  document.getElementById('legend')!.innerHTML = '<br><b>roads</b><br>' + Object.entries(ROAD_COLOUR).map(([k, col]) => `${sw(col)}${k}`).join('<br>') + `<br>${sw(LINK_COLOUR)}link approach (ring = road port)` +
    '<br><br><b>lots</b><br>' + ['house', 'shop', 'workshop', 'tower', 'block', 'highrise', 'hall', 'temple'].map((t) => `${sw(lotColour(t))}${t}`).join('<br>') +
    `<br><br>lots show from ${LOT_SCALE} px/block`;

  const info = document.getElementById('info')!;
  const describe = (): void => {
    const lines: string[] = [`${view.scale.toFixed(3)} px/block${showLots(view.scale) ? '' : ' (overview: district density)'}`];
    const dsel = selected ?? hover.district;
    const ds = dsel ? s.districts.find((d) => d.id === dsel) : undefined;
    if (ds) {
      lines.push('', `district ${ds.id}`, `quarter ${s.quarters[ds.quarter]?.kind ?? '-'}, density ${ds.density}`,
        `styles ${ds.styles.map((x) => `${x.style} ${x.weight}`).join(', ')}`, `lots ${ds.lots}`);
      const dd = districts.get(ds.id);
      if (dd?.scene) {
        const href = `index.html?ir=${encodeURIComponent(joinUrl(city, dd.scene))}&camera=fly`;
        info.innerHTML = '';
        const pre = document.createElement('span'); pre.textContent = lines.join('\n') + '\n';
        const a = document.createElement('a'); a.href = href; a.textContent = 'open the 3D scene'; a.id = 'scene-link';
        info.append(pre, a);
        if (hover.lot && dd) { const l = dd.lots.find((x) => x.id === hover.lot); if (l) info.append(document.createTextNode('\n\n' + lotText(l))); }
        return;
      }
      const l = hover.lot ? districts.get(ds.id)?.lots.find((x) => x.id === hover.lot) : undefined;
      if (l) lines.push('', lotText(l));
    }
    info.textContent = lines.join('\n');
  };
  const lotText = (l: District['lots'][number]): string =>
    `lot ${l.id}\n${l.type} (${l.style}), ${l.w}x${l.d}, front ${l.front}\nstoreys ${l.storeys[0]}-${l.storeys[1]}, ${l.status}\nfrontY ${l.frontY}`;

  const syncUrl = (): void => {
    const sz = size();
    const [cx, cz] = toWorld(view, sz.w / 2, sz.h / 2);
    const p = new URLSearchParams(location.search);
    p.set('layers', layersParam(layers)); p.set('x', String(Math.round(cx))); p.set('z', String(Math.round(cz))); p.set('zoom', view.scale.toFixed(4));
    history.replaceState(null, '', `?${p.toString()}`);
  };

  // ---- interaction
  let drag: { x: number; y: number } | null = null;
  canvas.addEventListener('pointerdown', (e) => { drag = { x: e.clientX, y: e.clientY }; canvas.setPointerCapture(e.pointerId); canvas.classList.add('drag'); });
  canvas.addEventListener('pointerup', (e) => {
    const moved = drag ? Math.hypot(e.clientX - drag.x, e.clientY - drag.y) : 99;
    drag = null; canvas.classList.remove('drag'); syncUrl();
    if (moved < 4) { // a click selects a district (and loads it for the scene link)
      const r = canvas.getBoundingClientRect();
      const [wx, wz] = toWorld(view, e.clientX - r.left, e.clientY - r.top);
      const d = districtAt(s, wx, wz);
      selected = d?.id ?? null;
      if (d?.detail && !districts.has(d.id) && !inflight.has(d.id)) {
        inflight.add(d.id);
        loadDistrict(city, d.detail).then((dd) => districts.set(d.id, dd)).catch(() => failed.add(d.id)).finally(() => { inflight.delete(d.id); describe(); schedule(); });
      }
      describe();
    }
  });
  canvas.addEventListener('pointermove', (e) => {
    if (drag) { view = pan(view, e.movementX, e.movementY); schedule(); return; }
    const r = canvas.getBoundingClientRect();
    const [wx, wz] = toWorld(view, e.clientX - r.left, e.clientY - r.top);
    const d = districtAt(s, wx, wz);
    const dd = d ? districts.get(d.id) : undefined;
    const l = dd && showLots(view.scale) ? lotAt(dd, wx, wz) : null;
    const nh = { district: d?.id ?? null, lot: l?.id ?? null };
    if (nh.district !== hover.district || nh.lot !== hover.lot) { hover = nh; describe(); schedule(); }
  });
  canvas.addEventListener('wheel', (e) => {
    e.preventDefault();
    const r = canvas.getBoundingClientRect();
    view = zoomAt(view, e.clientX - r.left, e.clientY - r.top, Math.exp(-e.deltaY * 0.0015));
    syncUrl(); describe(); schedule();
  }, { passive: false });
  window.addEventListener('resize', schedule);

  window.__planApi = {
    setView(x, z, scale) {
      const sz = size();
      view = { x0: x - sz.w / 2 / scale, z0: z - sz.h / 2 / scale, scale };
      schedule();
    },
    redraw: () => draw(),
  };
  draw();
  loadVisible();
  describe();
}

main().catch((e: unknown) => {
  document.getElementById('info')!.textContent = String(e);
  window.__planReady = { ok: false, error: String(e), districts: 0, loaded: 0, pending: 0, draws: 0, lots: 0, roads: 0, links: 0, lod: 'overview', scale: 0, lastDrawMs: 0 };
});
