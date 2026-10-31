// Assembles docs/review/explainer/index.html from template.html, the data JSON (build-data.mjs) and the road-refine profile picture.
// Usage: node build-page.mjs DATA.json OUT.html
import fs from 'fs';
import path from 'path';
import url from 'url';

const here = path.dirname(url.fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../..');
const [DATA, OUT] = process.argv.slice(2);

// ---- architecture map (SVG drawn from a box list; colours are the page's CSS tokens)
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
function box(x, y, w, h, title, lines, opt = {}) {
  let s = `<g><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="5" fill="${opt.fill || 'var(--surface)'}" stroke="${opt.stroke || 'var(--rule)'}" stroke-width="${opt.sw || 1}"${opt.dash ? ' stroke-dasharray="5 4"' : ''}/>`;
  if (opt.stripe) s += `<rect x="${x}" y="${y}" width="${w}" height="5" rx="2" fill="var(${opt.stripe})"/>`;
  s += `<text x="${x + 10}" y="${y + 22}" font-size="13.5" font-weight="600" fill="var(--ink)">${esc(title)}</text>`;
  lines.forEach((l, i) => {
    const mono = l.startsWith('`');
    const t = mono ? l.slice(1) : l;
    s += `<text x="${x + 10}" y="${y + 41 + i * 16}" font-size="11.5" fill="var(${mono ? "--ink" : "--ink2"})"${mono ? " style=\"font-family: 'IBM Plex Mono', monospace\"" : ""}>${esc(t)}</text>`;
  });
  return s + '</g>';
}
function arrow(x1, y1, x2, y2, label, opt = {}) {
  let s = `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="var(${opt.color || '--muted'})" stroke-width="${opt.w || 1.3}" marker-end="url(#ah)"${opt.dash ? ' stroke-dasharray="4 3"' : ''}/>`;
  if (label) s += `<text x="${opt.lx ?? (x1 + x2) / 2 + 4}" y="${opt.ly ?? (y1 + y2) / 2}" font-size="11" fill="var(--ink2)"${opt.anchor ? ` text-anchor="${opt.anchor}"` : ''}>${esc(label)}</text>`;
  return s;
}
const COLS = [
  { x: 20, title: 'Region', stripe: '--s1', lines: ['`RegionField', 'cells, jittered sites', '`RegionPlanner.decide', 'roll, 8 candidates, samples', '`FieldRegionView', 'join rule: claims, members', '`Tiers, CellDecision'] },
  { x: 222, title: 'Roads between', stripe: '--s2', lines: ['`RoadNet', 'graph (LGAP, witnesses), A*', 'on the 64-block lattice', '`Profile, Curve', 'stations every 8 blocks', '`LinkExits, ExitTrim', 'approaches; MAINSTUB cuts'] },
  { x: 424, title: 'Settlement skeleton', stripe: '--s3', lines: ['`Skeletons, CellSkeleton', 'coarse 32-block screen', '`DensityField, QuarterField', '`RoadNetwork, Arterials', 'main roads, street lattice', '`DistrictGrid', '160-block tiles'] },
  { x: 626, title: 'District detail', stripe: '--s4', lines: ['`JunctionField', 'one level per junction', '`Quays, QuayTrace', 'quay contour (lazy per tile)', '`Districts.plan, StreetRules', 'streets, ports, lots', '`Briefs, terrain/Grading'] },
  { x: 828, title: 'Buildings', stripe: '--s6', lines: ['`gen/v1/GeneratorV1', 'massing, storeys, facades,', 'roofs, interiors per lot', '`world/Pieces', '`raster/PieceRaster', 'cells per piece, one owner', 'per block'] },
];
let svg = `<svg viewBox="0 0 1040 800" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="Architecture map: planner phases, the mod classes that run them, and the probes that read terrain">
<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0L10 5L0 10z" fill="var(--muted)"/></marker></defs>
<rect x="0" y="0" width="1040" height="800" fill="var(--surface)"/>`;
const bandLabel = (x, y, label, anchor) => `<text x="${x}" y="${y}" font-size="11" style="font-family: 'IBM Plex Mono', monospace" letter-spacing="1.2" fill="var(--muted)"${anchor ? ` text-anchor="${anchor}"` : ''}>${esc(label)}</text>`;
const bandRect = (y, h) => `<rect x="6" y="${y}" width="1028" height="${h}" rx="7" fill="var(--sunk)" opacity="0.55"/>`;
// mod band: 8..196, label at the top
svg += bandRect(8, 188) + bandLabel(16, 27, 'MOD: WHEN IT RUNS IN THE GAME') + bandLabel(1024, 27, 'mod/src/main/java/dev/fractalbuildings/mod/', 'end');
svg += box(20, 40, 300, 140, 'worldgen/RegionWarmer', ['once a second per player, and 5 s ahead', 'thread: fractalbuildings-worker (1 to 4)', '`WorldPlanner.warm(x, z, 2048, quays)', 'then prefetch the tile under the point', 'teleport/TeleportHold → warmPoint'], { stroke: 'var(--s1)' });
svg += box(332, 40, 330, 140, 'worldgen/DistrictStructure, GroundStructure', ['structure start per 160-block tile', 'thread: vanilla worldgen worker', '`DistrictPlacement: start chunk? (geometry)', '`WorldPlanner.tile() → lazy pieces', 'plan → buildings / ground on demand'], { stroke: 'var(--s2)' });
svg += box(674, 40, 350, 140, 'worldgen/TilePiece + roads/RoadFeature', ['features step, vanilla worldgen worker', '`TilePiece.postProcess → WorldPlanner.raster()', 'writes only this chunk’s cells', '`RoadFeature → roadColumns(chunk)', 'then SpillClear, BuildingAirClear, FoliageClear'], { stroke: 'var(--s3)' });
// planner band: 206..540, label at the bottom
svg += bandRect(206, 334) + bandLabel(16, 530, 'PLANNER-CORE: PURE JAVA, NO MINECRAFT') + bandLabel(1024, 530, 'planner-core/src/main/java/dev/fractalbuildings/core/', 'end');
svg += box(20, 236, 1004, 52, 'world/WorldPlanner: one per world, thread-safe; caches only memoise', ['`warm()  tile()  plan()  buildings()  ground()  raster()  roadColumns()  warmQuays()'], { fill: 'var(--accent-soft)', stroke: 'var(--accent)' });
for (const c of COLS) svg += box(c.x, 304, 196, 196, c.title, c.lines, { stripe: c.stripe });
for (let i = 0; i < COLS.length - 1; i++) svg += arrow(COLS[i].x + 196, 326, COLS[i + 1].x - 2, 326, '', {});
svg += arrow(170, 180, 170, 234, 'warm', { lx: 178, ly: 212 });
svg += arrow(497, 180, 497, 234, 'tile, plan, buildings', { lx: 505, ly: 212 });
svg += arrow(849, 180, 849, 234, 'raster, roadColumns', { lx: 857, ly: 212 });
// probes band: 550..792, label at the top
svg += bandRect(550, 242) + bandLabel(16, 569, 'PROBES: HOW TERRAIN IS READ') + bandLabel(1024, 569, 'blue = column reads, yellow = chunk fills', 'end');
const PI = [
  [20, 'RegionProbe', ['64-block lattice', 'region cells, route nodes'], '--s1'],
  [222, 'roadExact', ['exact column, every 8 blocks', 'road stations'], '--s2'],
  [424, 'CoarseProbe', ['32-block lattice', 'skeleton screens'], '--s3'],
  [626, 'TerrainProbe (exact)', ['any column; junctions,', 'quays, districts, lots'], '--s4'],
  [828, 'ParallelReads', ['Fanout over the reads pool', 'prefetch(boxes)'], '--s7'],
];
for (const [x, t, l, c] of PI) svg += box(x, 580, 196, 72, t, l, { stripe: c });
svg += box(20, 684, 398, 100, 'mod/probe/SparseColumns → COLUMN READ', ['one column at a time: vanilla getBaseColumn', '~0.35 to 0.55 ms each; memo of single columns', 'for sparse lattices where a chunk would be waste'], { stroke: 'var(--s1)', sw: 1.6 });
svg += box(430, 684, 394, 100, 'mod/probe/ColumnMemo + ChunkFill → CHUNK FILL', ['all 256 columns of a chunk, windowed fill', '~1.9 ms per chunk (~0.008 ms a column)', 'memo of 8,192 chunks; filled once, any thread'], { stroke: 'var(--s4)', sw: 1.6 });
svg += box(836, 684, 188, 100, 'Recorded / synthetic', ['RecordedProbe: dumps', 'for cli, viewer, tests', 'SyntheticTerrain: tests'], { dash: true });
svg += arrow(118, 652, 140, 682, '', { color: '--s1' });
svg += arrow(320, 652, 260, 682, '', { color: '--s1' });
svg += arrow(522, 652, 380, 682, '', { color: '--s1' });
svg += arrow(724, 652, 640, 682, '', { color: '--s4' });
svg += arrow(926, 652, 780, 682, 'LiveReads fills memos', { color: '--s7', lx: 862, ly: 674, anchor: 'start' });
svg += '</svg>';

let html = fs.readFileSync(path.join(here, 'template.html'), 'utf8');
const data = fs.readFileSync(DATA, 'utf8');
const img = fs.readFileSync(path.join(repo, 'docs/review/road-refine/profile-r3_m2_2.png')).toString('base64');
html = html.replace('__ARCH_SVG__', () => svg).replace('__DATA__', () => data).replace('__PADIMG__', () => 'data:image/png;base64,' + img);
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, html);
console.log('wrote', OUT, (html.length / 1024).toFixed(0), 'KB');
