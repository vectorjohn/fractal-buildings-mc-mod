// Lot statistics of a planned city by distance from the centre: node scripts/city-stats.mjs <preset> [--transect]
// Prints, per band of 0.1 radius, lot counts by type, mean storeys, max storeys. For D3 skyline evidence.
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
const name = process.argv[2];
const dir = join('out', 'city', name);
const s = JSON.parse(readFileSync(join(dir, 'settlement.json'), 'utf8'));
const [cx, cz] = s.site.centre, R = s.site.radius;
const lots = [];
for (const f of readdirSync(join(dir, 'districts'))) {
  const d = JSON.parse(readFileSync(join(dir, 'districts', f), 'utf8'));
  for (const l of d.lots) if (l.status === 'build') lots.push({ ...l, dist: Math.hypot(l.x + l.w / 2 - cx, l.z + l.d / 2 - cz) / R });
}
const bands = new Map();
for (const l of lots) {
  const b = Math.min(9, Math.floor(l.dist * 10));
  if (!bands.has(b)) bands.set(b, []);
  bands.get(b).push(l);
}
console.log(`${name}: ${lots.length} built lots, radius ${R}`);
for (const b of [...bands.keys()].sort((a, c) => a - c)) {
  const ls = bands.get(b);
  const t = {};
  for (const l of ls) t[l.type] = (t[l.type] ?? 0) + 1;
  const st = ls.map((l) => l.storeys[1]);
  const mean = st.reduce((a, c) => a + c, 0) / st.length;
  const dens = ls.reduce((a, l) => a + l.density, 0) / ls.length;
  console.log(`d/R ${(b / 10).toFixed(1)}-${((b + 1) / 10).toFixed(1)}: n=${ls.length} dens=${dens.toFixed(0)} meanMax=${mean.toFixed(1)} max=${Math.max(...st)} ${JSON.stringify(t)}`);
}
const tall = lots.filter((l) => l.type === 'highrise').sort((a, b) => b.storeys[1] - a.storeys[1]).slice(0, 5);
console.log('tallest', tall.map((l) => `${l.storeys[1]}@${l.dist.toFixed(2)}R`).join(' '));
// Skyline histogram (P5 skyline): share of built lots by top storey, and the radius band that holds each share.
const edges = [[1, 2], [3, 4], [5, 5], [6, 9], [10, 19], [20, 29], [30, 44], [45, 60]];
const total = lots.length;
console.log('storey histogram (top storey, share of built lots, median d/R):');
for (const [a, b] of edges) {
  const ls = lots.filter((l) => l.storeys[1] >= a && l.storeys[1] <= b).sort((x, y) => x.dist - y.dist);
  const med = ls.length ? ls[Math.floor(ls.length / 2)].dist.toFixed(2) : '-';
  console.log(`  ${String(a).padStart(2)}-${String(b).padEnd(2)} ${((100 * ls.length) / total).toFixed(1).padStart(5)}%  n=${String(ls.length).padStart(4)}  median d/R ${med}`);
}
const tallLots = lots.filter((l) => l.storeys[1] >= 20);
console.log(`>=20 storeys: ${tallLots.length} lots (${((100 * tallLots.length) / total).toFixed(1)}%), ${lots.filter((l) => l.type === 'block').length} blocks`);
