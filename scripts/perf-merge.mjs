// PERFSRV: merge the two dedicated-server runs of scripts/perf.sh (world-mod.json, world-base.json) into world.json and derive the ratio rows,
// the same ones PerfSupport.derive() computes for the old in-client run. Usage: node scripts/perf-merge.mjs <dir>
import fs from 'node:fs';
import path from 'node:path';

const dir = process.argv[2];
if (!dir) { console.error('usage: perf-merge.mjs <dir>'); process.exit(2); }
const read = (n) => JSON.parse(fs.readFileSync(path.join(dir, n), 'utf8'));
const out = { ...read('world-mod.json'), ...read('world-base.json') };
const r2 = (x) => Math.round(x * 100) / 100;
const need = ['mod.warm.chunk.mean.ms', 'base.warm.chunk.mean.ms', 'mod.cold.chunk.mean.ms', 'base.cold.chunk.mean.ms', 'mod.region.growth.bytes', 'base.region.growth.bytes', 'mod.heap.mb', 'base.heap.mb'];
for (const k of need) if (typeof out[k] !== 'number') { console.error(`perf-merge: row ${k} missing (a world run failed?)`); process.exit(1); }
out['warm.chunk.ratio'] = r2(out['base.warm.chunk.mean.ms'] > 0 ? out['mod.warm.chunk.mean.ms'] / out['base.warm.chunk.mean.ms'] : 0);
out['cold.chunk.ratio'] = r2(out['base.cold.chunk.mean.ms'] > 0 ? out['mod.cold.chunk.mean.ms'] / out['base.cold.chunk.mean.ms'] : 0);
out['region.growth.ratio'] = r2(out['base.region.growth.bytes'] > 0 ? out['mod.region.growth.bytes'] / out['base.region.growth.bytes'] - 1 : 0);
out['heap.growth.mb'] = r2(out['mod.heap.mb'] - out['base.heap.mb']);
fs.writeFileSync(path.join(dir, 'world.json'), JSON.stringify(out, null, 2) + '\n');
console.log(`perf-merge: ${Object.keys(out).length} rows -> ${path.join(dir, 'world.json')}`);
