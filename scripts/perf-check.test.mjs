// Self-test of scripts/perf-check.mjs: node scripts/perf-check.test.mjs  (PERFSRV r4: a flight with no building lookups at all is "no
// measurement", not 0 % inline, so a full run with dead lane counters fails instead of passing vacuously).
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const budget = { budgets: [{ expr: 'inlineShare', label: 'inline', unit: '%', budget: 5, ci: 10 }] };
function run(world, extra = []) {
	const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'perfcheck-'));
	fs.writeFileSync(path.join(dir, 'b.json'), JSON.stringify(budget));
	fs.writeFileSync(path.join(dir, 'world.json'), JSON.stringify(world));
	const r = spawnSync('node', [path.join(import.meta.dirname, 'perf-check.mjs'), path.join(dir, 'b.json'), dir, ...extra], { encoding: 'utf8' });
	fs.rmSync(dir, { recursive: true });
	return r;
}
let bad = 0;
const check = (name, ok) => { if (!ok) { bad++; console.error('FAIL', name); } else console.log('ok', name); };
const dead = run({ 'flight.lookups.done': 0, 'flight.lookups.joined': 0, 'flight.lookups.inline': 0 });
check('zero lookups fails a full run', dead.status === 1 && dead.stdout.includes('no measurement'));
const good = run({ 'flight.lookups.done': 90, 'flight.lookups.joined': 8, 'flight.lookups.inline': 2 });
check('2 % inline passes', good.status === 0);
const worse = run({ 'flight.lookups.done': 50, 'flight.lookups.joined': 0, 'flight.lookups.inline': 50 });
check('50 % inline fails', worse.status === 1 && worse.stdout.includes('FAIL'));
process.exit(bad ? 1 : 0);
