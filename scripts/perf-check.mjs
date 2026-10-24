// B8: compare the perf run (mod/build/perf/*.json) with the budget file. Usage: node scripts/perf-check.mjs <budget.json> <dir>
// Each budget: { key | expr, label, budget, ci, unit, file }. Above `ci` fails the run (default 2x the budget, so a loaded machine
// does not flake); above `budget` is a miss, printed as such (a quiet-machine miss is a finding, not a threshold to loosen).
import fs from 'node:fs';
import path from 'node:path';

const [budgetFile, dir, ...flags] = process.argv.slice(2);
const allowAbsent = flags.includes('--allow-absent');
const LOAD_SKIP = Number(process.env.PERF_LOAD_SKIP ?? 8);
const spec = JSON.parse(fs.readFileSync(budgetFile, 'utf8'));
const data = {};
for (const f of ['planner.json', 'world.json']) {
	const p = path.join(dir, f);
	if (fs.existsSync(p)) Object.assign(data, JSON.parse(fs.readFileSync(p, 'utf8')));
}
const load = Math.max(...['planner.load.before', 'planner.load.after', 'mod.load.start', 'mod.load.end', 'base.load.start', 'base.load.end'].map((k) => Number(data[k] ?? 0)));
const loaded = load >= LOAD_SKIP;
const timing = new Set(['ms', 'x', '%']) // 'ratio' (region-file size) is a size, always enforced;
const rows = [];
let failed = 0, missed = 0, absent = 0, skipped = 0;
for (const b of spec.budgets) {
	let v;
	if (b.expr === 'inlineShare') {
		const d = data['flight.lookups.done'], j = data['flight.lookups.joined'], i = data['flight.lookups.inline'];
		v = d === undefined ? undefined : (d + j + i) === 0 ? 0 : (100 * i) / (d + j + i);
	} else v = data[b.key];
	const ci = b.ci ?? b.budget * 2;
	let st = 'ok';
	if (typeof v !== 'number') { st = 'absent'; absent++; }
	else if (loaded && timing.has(b.unit) && v > ci) { st = 'skip(load)'; skipped++; }
	else if (v > ci) { st = 'FAIL'; failed++; }
	else if (v > b.budget) { st = 'miss'; missed++; }
	rows.push({ label: b.label, unit: b.unit, budget: b.budget, ci, v, st });
}
const fmt = (x) => (typeof x === 'number' ? (Math.abs(x) >= 100 ? x.toFixed(0) : x.toFixed(2)) : '-');
let md = `| What | Measured | Budget | CI ceiling | Status |\n|---|---|---|---|---|\n`;
for (const r of rows) md += `| ${r.label} | ${fmt(r.v)} ${r.unit} | ${r.budget} | ${r.ci} | ${r.st} |\n`;
md += `\nloadavg at start of the planner run: ${data['planner.load.before'] ?? '?'}; at its end: ${data['planner.load.after'] ?? '?'}; world run start ${data['mod.load.start'] ?? '?'}, end ${data['mod.load.end'] ?? '?'}\n`;
console.log(md);
fs.writeFileSync(path.join(dir, 'summary.md'), md);
if (load >= 2) console.log(`NOTE: highest loadavg seen during the run was ${load}: treat misses as noise until re-measured on a quiet machine.`);
if (skipped) console.log(`${skipped} timing rows were above their ceiling but not failed because the load reached ${load} (>= ${LOAD_SKIP}); counts, sizes and heap are always enforced.`);
if (absent) { console.log(`${absent} budgets had no measurement`); if (!allowAbsent) { console.log('PERF FAIL: rows are missing from a full run (a step failed or was skipped)'); process.exit(1); } }
if (failed) { console.log(`PERF FAIL: ${failed} above the CI ceiling`); process.exit(1); }
console.log(`perf ok (${missed} above budget but under the ceiling)`);
