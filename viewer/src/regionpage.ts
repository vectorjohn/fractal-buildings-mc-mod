// Region map page. URL: region.html?region=<url>[&terrain=<url>][&layer=0][&view=x0,z0,w,d][&scale=0.05]
//   [&heat=0][&claims=0][&grid=0][&reasons=0][&roads=0]. Files may be gzip (detected by magic, not name).
import { CROSSING_COLOUR, DROPPED_COLOUR, PORT_COLOUR, ROAD_COLOUR, defaultView, drawRegion, loadDump, REASON_COLOUR, TIER_COLOUR, type View } from './regionmap';

async function main(): Promise<void> {
  const q = new URLSearchParams(location.search);
  const regionUrl = q.get('region');
  if (!regionUrl) throw new Error('missing ?region=<url> (a fb-region-dump/1 from `fractal-cli region`)');
  const region = await loadDump(regionUrl);
  const terrainUrl = q.get('terrain');
  const terrain = terrainUrl ? await loadDump(terrainUrl) : undefined;
  const base = terrain ?? region;
  let v: View = defaultView(base.grid);
  const view = q.get('view');
  if (view) {
    const [x0, z0, w, d] = view.split(',').map(Number);
    v = { ...v, x0: x0!, z0: z0!, w: w!, d: d! };
    v.scale = Math.min(1, 1100 / Math.max(v.w, v.d));
  }
  if (q.get('scale')) v.scale = Number(q.get('scale'));
  const on = (k: string): boolean => q.get(k) !== '0';
  const canvas = document.getElementById('c') as HTMLCanvasElement;
  const stats = drawRegion(canvas.getContext('2d')!, region, terrain, v, {
    layer: Number(q.get('layer') ?? '0'), heat: on('heat'), claims: on('claims'), grid: on('grid'), reasons: on('reasons'), roads: on('roads'),
  });
  const L = region.layers[Number(q.get('layer') ?? '0')];
  const sw = (c: string): string => `<span class="sw" style="background:${c}"></span>`;
  document.getElementById('info')!.innerHTML =
    `<b>seed ${region.seed}</b>\ncell ${L?.cellSize ?? '-'} ${L?.policy ?? ''}\ncells ${stats.cells}, settled ${stats.settled}\n` +
    `1 px = ${(1 / v.scale).toFixed(1)} blocks\nterrain step ${base.grid.step}\n` +
    `roads: ${stats.roads.links} links, ${stats.roads.crossings} crossings, ${stats.roads.meetings} meeting points, ${stats.roads.dropped} dropped, ${stats.roads.ports} port-tagged\n` +
    `water heat: ${stats.heat ? 'shown' : 'off (no waterDist in the dump, or heat=0)'}\nclaims: ${stats.claims}\n\n<b>settlements</b>\n` +
    Object.entries(TIER_COLOUR).map(([k, c]) => `${sw(c)}${k}`).join('\n') + '\n\n<b>rejected (site dot)</b>\n' +
    Object.entries(REASON_COLOUR).map(([k, c]) => `${sw(c)}${k}`).join('\n') +
    `\n\n<b>roads</b>\n${sw(ROAD_COLOUR)}link\n${sw(CROSSING_COLOUR)}crossing (single span)\n${sw(PORT_COLOUR)}meeting point, port ring\n${sw(DROPPED_COLOUR)}dropped pair`;
  window.__regionReady = { ok: true, settled: stats.settled, cells: stats.cells, claims: stats.claims, heat: stats.heat, roads: stats.roads };
}

main().catch((e: unknown) => {
  document.getElementById('info')!.textContent = String(e);
  window.__regionReady = { ok: false, settled: 0, cells: 0, claims: 0, heat: false, roads: { links: 0, crossings: 0, meetings: 0, dropped: 0, ports: 0 }, error: String(e) };
});
