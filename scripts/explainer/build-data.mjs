// Builds the data block of docs/review/explainer/index.html (the "Inside the City Planner" page) from real planner output.
// Inputs (see scripts/explainer/README.md for how each was made):
//   ROADS  fractal-cli roads over fixtures/terrain/world-987654321-s64.json.gz      (region cells, joins, intercity roads, 64-block terrain)
//   CITY   fractal-cli city --site 2432,-5696,metropolis,1430 --districts         (metropolis s2_m6 skeleton, districts, streets, lots)
//   TERR   fixtures/terrain/gate-metropolis-temperate.json.gz                      (16-block terrain under the metropolis)
//   REACH  .claude/reports/profile-evidence/reach-7ba928e426/reach-units.tsv      (PerfReachTest: every noise unit of one cold plan, live vanilla noise)
//   DUMP   ExplainerDumpTest output (junction pins, whole vs lazy quay chunk sets on recorded terrain)
// Usage: node build-data.mjs ROADS CITYDIR TERR REACH DUMP OUT.json
import fs from 'fs';
import zlib from 'zlib';
import path from 'path';

const [ROADS, CITYDIR, TERR, REACH, DUMP, OUT] = process.argv.slice(2);
const readJson = (p) => { let b = fs.readFileSync(p); if (b[0] === 0x1f) b = zlib.gunzipSync(b); return JSON.parse(b); };
const b64 = (typed) => Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength).toString('base64');
const i16 = (arr) => b64(Int16Array.from(arr));
const SEP = -32768;

// ---- terrain: one byte per lattice column. 0..63 = water (depth, capped), 64..255 = dry surface y - 40 + 64
function terrainBytes(d, crop) {
  const g = d.grid; const out = [];
  const [i0, j0, ni, nj] = crop || [0, 0, g.nx, g.nz];
  const bytes = new Uint8Array(ni * nj);
  for (let j = 0; j < nj; j++) for (let i = 0; i < ni; i++) {
    const k = (j0 + j) * g.nx + (i0 + i);
    const w = d.water[k], s = d.surface[k];
    bytes[j * ni + i] = w > 0 ? Math.max(1, Math.min(63, w)) : 64 + Math.max(0, Math.min(191, s - 40));
  }
  return { x0: g.x0 + i0 * g.step, z0: g.z0 + j0 * g.step, step: g.step, nx: ni, nz: nj, b: b64(bytes) };
}

// ---- region (world 987654321)
const roads = readJson(ROADS);
const L = roads.layers[0];
const REASON = { settled: 0, water: 1, peak: 2, unsuitable: 3, roll: 4 };
const TIER = { hamlet: 0, village: 1, town: 2, city: 3, metropolis: 4 };
const cells = L.cells.map((c) => [c.cx, c.cz, c.siteX, c.siteZ, REASON[c.reason] ?? 5, Math.round(c.roll * 1000), TIER[c.tier] ?? -1,
  c.centreX, c.centreZ, c.radius, c.claimedBy ? c.claimedBy[0] : null, c.claimedBy ? c.claimedBy[1] : null, Math.round((c.water || 0) * 100), Math.round((c.chance || 0) * 1000)]);
const unknownReasons = L.cells.filter((c) => !(c.reason in REASON)).map((c) => c.reason);
if (unknownReasons.length) console.error('unknown reasons', [...new Set(unknownReasons)]);
const simplify = (pts, every) => { const o = []; for (let i = 0; i < pts.length; i += 2) if ((i / 2) % every === 0 || i === pts.length - 2) o.push(pts[i], pts[i + 1]); return o; };
const edges = L.roads.edges.map((e) => ({ a: e.a, b: e.b, n: e.pts.length / 2, cross: (e.cross || []).length, p: i16(simplify(e.pts, 4)) }));
const region = {
  terrain: terrainBytes(roads),
  cellSize: L.cellSize,
  cells,
  sites: L.sites,
  nodes: L.roads.nodes.map((n) => [n.x, n.z, n.r, TIER[n.tier], n.port ? 1 : 0, n.cx, n.cz]),
  edges,
  meet: L.roads.meet.map((m) => [m.x, m.z]),
  dropped: L.roads.dropped.map((d) => [d.pa ? d.pa[0] : null, d.pa ? d.pa[1] : null, d.pb ? d.pb[0] : null, d.pb ? d.pb[1] : null, d.why, d.a, d.b]),
  stationsTotal: L.roads.edges.reduce((s, e) => s + e.pts.length / 2, 0),
};

// ---- reach units (PerfReachTest at 7ba928e4)
const PURPOSE_ORDER = [
  ['1 warm', 'lattice column', 'region decisions (cells around the point)', 'regionNear'],
  ['1 warm', 'lattice column', 'region decisions (for the road graph)', 'regionGraph'],
  ['1 warm', 'lattice column', 'intercity roads (routes, stations)', 'routeLattice'],
  ['1 warm', 'road column', 'intercity roads (routes, stations)', 'stations'],
  ['1 warm', 'lattice column', 'streets and skeleton', 'skeleton'],
  ['1 warm', 'lattice column', 'junction field', 'junctionCols'],
  ['1 warm', 'exact chunk', 'junction field', 'junction'],
  ['2 warmQuays', 'exact chunk', 'quays', 'quays'],
  ['2 warmQuays', 'lattice column', 'quays', 'quayCols'],
  ['2 warmQuays', 'road column', 'quays', 'quayRoadCols'],
  ['3 centre tile', 'exact chunk', 'tile plan (other)', 'tile'],
];
const groups = {}; for (const p of PURPOSE_ORDER) groups[p[3]] = [];
const lines = fs.readFileSync(REACH, 'utf8').split('\n');
for (let i = 1; i < lines.length; i++) {
  const f = lines[i].split('\t'); if (f.length < 5) continue;
  const p = PURPOSE_ORDER.find((q) => q[0] === f[0] && q[1] === f[1] && q[2] === f[2]);
  if (!p) { console.error('unmapped', f.slice(0, 3)); continue; }
  groups[p[3]].push([+f[3], +f[4]]);
}
const MCX = Math.floor(2432 / 16), MCZ = Math.floor(-5696 / 16);
const reach = { centreChunk: [MCX, MCZ], groups: {} };
for (const p of PURPOSE_ORDER) {
  const g = groups[p[3]];
  // animation order: outward from the metropolis centre (the real order is not recorded)
  g.sort((u, v) => (Math.hypot(u[0] - MCX, u[1] - MCZ) - Math.hypot(v[0] - MCX, v[1] - MCZ)) || u[0] - v[0] || u[1] - v[1]);
  reach.groups[p[3]] = { kind: p[1], purpose: p[2], step: p[0], n: g.length, c: i16(g.flat()) };
}

// ---- city: metropolis s2_m6
const st = readJson(path.join(CITYDIR, 'settlement.json'));
const terr = readJson(TERR);
const streetsByKind = {}; const seen = new Set();
const lots = []; const lotTypes = []; const TYPES = {};
const collinear = (a, b, c) => (b[0] - a[0]) * (c[1] - b[1]) === (b[1] - a[1]) * (c[0] - b[0]);
function pushLine(kind, pts) {
  const o = [pts[0]];
  for (let i = 1; i < pts.length - 1; i++) if (!collinear(o[o.length - 1], pts[i], pts[i + 1])) o.push(pts[i]);
  if (pts.length > 1) o.push(pts[pts.length - 1]);
  (streetsByKind[kind] ||= []).push(...o.flat(), SEP, SEP);
}
const districts = [];
for (const d of st.districts) {
  districts.push([d.ix, d.iz, d.x, d.z, d.w, d.d, d.quarter, d.lots]);
  const det = readJson(path.join(CITYDIR, d.detail));
  for (const s of det.streets) {
    const key = s.id + ':' + s.points[0] + ':' + s.points[s.points.length - 1];
    if (seen.has(key)) continue; seen.add(key);
    pushLine(s.kind, s.points);
  }
  for (const l of det.lots) {
    if (!(l.type in TYPES)) TYPES[l.type] = Object.keys(TYPES).length;
    lots.push(l.x, l.z, l.w, l.d); lotTypes.push(l.status === 'build' ? TYPES[l.type] : 255);
  }
}
const city = {
  site: st.site, bounds: st.bounds,
  terrain: terrainBytes(terr),
  coarse: { x0: st.coarse.x0, z0: st.coarse.z0, step: st.coarse.step, nx: st.coarse.nx, nz: st.coarse.nz,
    mask: b64(Uint8Array.from(st.coarse.mask)), quarter: b64(Int8Array.from(st.coarse.quarter)), density: b64(Uint8Array.from(st.coarse.density)) },
  quarters: st.quarters.map((q) => q.kind),
  cells: st.cells.map((c) => ({ cx: c.cx, cz: c.cz, site: c.site, poly: c.polygon })),
  roads: st.roads.map((r) => ({ kind: r.kind, w: r.width, p: r.points.flat() })),
  gates: st.gates.map((g) => [g.x, g.z, g.kind]),
  landmarks: st.landmarks.map((l) => [l.x, l.z, l.w, l.d, l.kind]),
  districts,
  streets: Object.fromEntries(Object.entries(streetsByKind).map(([k, v]) => [k, i16(v)])),
  lots: i16(lots), lotTypes: b64(Uint8Array.from(lotTypes)), types: Object.keys(TYPES), lotCount: lots.length / 4,
};

// ---- dump: junction pins, whole vs lazy quays (recorded terrain)
const dump = JSON.parse(fs.readFileSync(DUMP, 'utf8'));
const junctions = dump.junctions.map((j) => {
  let sx = 0, sz = 0, n = 0, len = 0;
  for (const p of j.pads) {
    const m = p.match(/ (-?\d+),(-?\d+)\.\.(-?\d+),(-?\d+)$/); if (!m) continue;
    const [a, b, c, d] = m.slice(1).map(Number); sx += a + c; sz += b + d; n += 2; len = Math.max(len, Math.hypot(c - a, d - b));
  }
  return [Math.round(sx / n), Math.round(sz / n), j.level, j.ground, j.pads.length, Math.round(len)];
});
const quayRuns = [];
for (const r of dump.quayRuns) {
  const cols = r.split(' ').map((t) => { const [xz, y] = t.split(':'); const [x, z] = xz.split(',').map(Number); return [x, z]; });
  const o = cols.filter((_, i) => i % 3 === 0 || i === cols.length - 1);
  quayRuns.push(...o.flat(), SEP, SEP);
}
const tiles = dump.tiles.map((t) => [t.ix, t.iz, t.chunks ? t.chunks.length / 2 : -1, t.runs ?? -1]);
const measured = dump.tiles.filter((t) => t.chunks).sort((a, b) => a.chunks.length - b.chunks.length);
const pick = [];
if (measured.length) for (const q of [0.1, 0.9, 0.5]) pick.push(measured[Math.min(measured.length - 1, Math.floor(q * measured.length))]);
const near = dump.tiles.filter((t) => t.chunks).map((t) => ({ t, d: Math.hypot(t.ix * 160 - 2432, t.iz * 160 + 5696) })).sort((a, b) => a.d - b.d);
const demo = { };
for (const t of pick) demo[t.ix + ',' + t.iz] = { runs: t.runs, c: i16(t.chunks) };
const dumpOut = {
  junctions, junctionChunks: i16(dump.junctionChunks), junctionChunkCount: dump.junctionChunks.length / 2,
  wholeQuayChunks: i16(dump.wholeQuayChunks), wholeQuayChunkCount: dump.wholeQuayChunks.length / 2,
  quayRuns: i16(quayRuns), quayRunCount: dump.quayRuns.length,
  tiles, demo, centreTile: { ix: dump.centreTile.ix, iz: dump.centreTile.iz, c: i16(dump.centreTile.chunks), n: dump.centreTile.chunks.length / 2 },
};

const out = { region, reach, city, dump: dumpOut };
fs.writeFileSync(OUT, JSON.stringify(out));
const kb = (o) => (JSON.stringify(o).length / 1024).toFixed(0) + ' KB';
console.log('region', kb(region), 'reach', kb(reach), 'city', kb(city), 'dump', kb(dumpOut), 'total', kb(out));
console.log('tiles measured', measured.length, 'of', tiles.length, 'counts', measured.map((t) => t.chunks.length / 2).join(' '));
