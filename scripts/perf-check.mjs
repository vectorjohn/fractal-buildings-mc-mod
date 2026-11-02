// B8: compare the perf run (mod/build/perf/*.json) with the budget file. Usage: node scripts/perf-check.mjs <budget.json> <dir>
// Each budget: { key | expr, label, budget, ci, unit, file, loadSkipGuard? }. Above `ci` fails the run (default 2x the budget, so a
// loaded machine does not flake); above `budget` is a miss, printed as such (a quiet-machine miss is a finding, not a threshold to loosen).
// A timing row above `ci` while the load reached PERF_LOAD_SKIP is skip(load), not a failure, but never silent: each prints a
// `PERF SKIPPED (load)` line, and a row with `loadSkipGuard: F` (true = 3; env PERF_SKIP_FAR overrides every F) fails with
// `PERF SKIPPED ROW FAR OVER CEILING` (exit 5) when its skipped value is above F x ci (MAINSTUB: a 50-60 s first tile passed as
// skip(load)). Exit: 0 ok | 1 FAIL above a ceiling, or a missing row in a full run | 5 a guarded row far over its ceiling while skipped.
// Flags: --allow-absent (planner-, world- or check-only runs: no missing row fails); --allow-absent-prefix P (repeatable: only missing
// rows whose key starts with P are allowed; perf.sh passes `arrival.` when FB_PERF_ARRIVAL=0 skipped the cold-arrival worlds).
// PERFKNOWN (John 2026-11-02: a ceiling is never raised to make a run pass; exceeding one is a bug, accepted for 0.1.0): a row may
// carry `knownOver: { issue, since, accepted, last, lastAt?, margin? }`. Above its ci it prints `PERF KNOWN OVER (bug, accepted for
// <accepted>: <issue>)`, shows KNOWN OVER in summary.md and does not fail the run (not a pass either). It still FAILS when the value
// is more than `margin` (default 0.25) above `last` (a regression on top of the known bug: `PERF KNOWN OVER WORSE`), or when `last`
// is not a positive number. Back under its ci it prints `PERF KNOWN OVER NOW UNDER CEILING ... remove the knownOver field`. The
// load skip above still comes first. Never edit `budget` or `ci` to make a row pass; update `last` only from a quiet measurement.
import fs from 'node:fs';
import path from 'node:path';

const [budgetFile, dir, ...flags] = process.argv.slice(2);
const allowAbsent = flags.includes('--allow-absent');
const absentPrefixes = flags.flatMap((f, i) => (f === '--allow-absent-prefix' && flags[i + 1] ? [flags[i + 1]] : []));
const absentAllowed = (b) => allowAbsent || (typeof b.key === 'string' && absentPrefixes.some((p) => b.key.startsWith(p)));
const LOAD_SKIP = Number(process.env.PERF_LOAD_SKIP ?? 8);
const FAR_ENV = process.env.PERF_SKIP_FAR;
const farOf = (b) => (FAR_ENV !== undefined ? Number(FAR_ENV) : b.loadSkipGuard === true ? 3 : Number(b.loadSkipGuard));
const spec = JSON.parse(fs.readFileSync(budgetFile, 'utf8'));
const data = {};
for (const f of ['planner.json', 'world.json']) {
	const p = path.join(dir, f);
	if (fs.existsSync(p)) Object.assign(data, JSON.parse(fs.readFileSync(p, 'utf8')));
}
const load = Math.max(...['planner.load.before', 'planner.load.after', 'mod.load.start', 'mod.load.end', 'base.load.start', 'base.load.end'].map((k) => Number(data[k] ?? 0)));
const loaded = load >= LOAD_SKIP;
const timing = new Set(['ms', 'x', '%']) // 'ratio' (region-file size) is a size, always enforced;
const fmt = (x) => (typeof x === 'number' ? (Math.abs(x) >= 100 ? x.toFixed(0) : x.toFixed(2)) : '-');
const rows = [];
const skipLines = [], farLines = [];
const knownLines = [], worseLines = [], underLines = [];
const KNOWN_MARGIN = 0.25;
let failed = 0, missed = 0, absent = 0, absentBad = 0, skipped = 0, known = 0;
for (const b of spec.budgets) {
	let v;
	if (b.expr === 'inlineShare') {
		const d = data['flight.lookups.done'], j = data['flight.lookups.joined'], i = data['flight.lookups.inline'];
		v = d === undefined || (d + j + i) === 0 ? undefined : (100 * i) / (d + j + i); // no lookups at all = nothing measured, not 0 % (PERFSRV r4)
	} else v = data[b.key];
	const reportOnly = b.budget === null || b.budget === undefined; // COLDARRIVAL: measured and printed, never judged (John sets budgets)
	const ci = reportOnly ? null : (b.ci ?? b.budget * 2);
	let st = 'ok';
	if (typeof v !== 'number') { st = 'absent'; absent++; if (!absentAllowed(b)) absentBad++; }
	else if (reportOnly) st = 'report';
	else if (loaded && timing.has(b.unit) && v > ci) {
		st = 'skip(load)'; skipped++;
		const far = b.loadSkipGuard ? farOf(b) : NaN;
		const line = `PERF SKIPPED (load) ${b.key ?? b.expr} value=${fmt(v)} ceiling=${ci} (${(v / ci).toFixed(1)}x${far > 0 ? `, guard ${far}x` : ''}) load=${load}`;
		skipLines.push(line);
		if (far > 0 && v > far * ci) { st = 'SKIPPED-FAR'; farLines.push(line); }
	}
	else if (v > ci && b.knownOver) {
		const k = b.knownOver, margin = typeof k.margin === 'number' ? k.margin : KNOWN_MARGIN;
		const name = b.key ?? b.expr, acc = k.accepted ?? '?', at = k.lastAt ? ` (${k.lastAt})` : '';
		if (!(typeof k.last === 'number' && k.last > 0)) {
			st = 'FAIL (knownOver without last)'; failed++;
			worseLines.push(`PERF KNOWN OVER WORSE ${name}: knownOver has no positive \`last\` value to compare with (value=${fmt(v)} ceiling=${ci})`);
		} else if (v > k.last * (1 + margin)) {
			st = 'FAIL (known over, worse)'; failed++;
			worseLines.push(`PERF KNOWN OVER WORSE ${name} value=${fmt(v)} last=${fmt(k.last)}${at} (+${((v / k.last - 1) * 100).toFixed(0)} %, margin ${(margin * 100).toFixed(0)} %) ceiling=${ci}: a regression on top of the known bug (${k.issue})`);
		} else {
			st = `KNOWN OVER (bug, accepted for ${acc}: ${k.issue})`; known++;
			knownLines.push(`PERF KNOWN OVER (bug, accepted for ${acc}: ${k.issue}) ${name} value=${fmt(v)} ceiling=${ci} (${(v / ci).toFixed(1)}x) last=${fmt(k.last)}${at} since ${k.since ?? '?'}`);
		}
	}
	else if (v > ci) { st = 'FAIL'; failed++; }
	else if (v > b.budget) { st = 'miss'; missed++; }
	if (b.knownOver && typeof v === 'number' && !reportOnly && v <= ci) {
		underLines.push(`PERF KNOWN OVER NOW UNDER CEILING ${b.key ?? b.expr} value=${fmt(v)} ceiling=${ci} (knownOver: ${b.knownOver.issue}): if a quiet re-run agrees, remove the knownOver field from mod/perf-budget.json`);
		st += ' (knownOver: remove?)';
	}
	const bv = b.baseKey ? data[b.baseKey] : undefined; // vanilla value of the same row, and mod / vanilla
	rows.push({ label: b.label, unit: b.unit, budget: reportOnly ? '-' : b.budget, ci: reportOnly ? '-' : ci, v, st, bv, ratio: typeof v === 'number' && typeof bv === 'number' && bv > 0 ? v / bv : undefined });
}
let md = `| What | Measured | Vanilla (base world) | Mod / vanilla | Budget | CI ceiling | Status |\n|---|---|---|---|---|---|---|\n`;
for (const r of rows) md += `| ${r.label} | ${fmt(r.v)} ${r.unit} | ${r.bv === undefined ? '-' : fmt(r.bv) + ' ' + r.unit} | ${r.ratio === undefined ? '-' : r.ratio.toFixed(1) + 'x'} | ${r.budget} | ${r.ci} | ${r.st} |\n`;
md += `\nloadavg at start of the planner run: ${data['planner.load.before'] ?? '?'}; at its end: ${data['planner.load.after'] ?? '?'}; world run start ${data['mod.load.start'] ?? '?'}, end ${data['mod.load.end'] ?? '?'}\n`;
console.log(md);
const knownAll = [...knownLines, ...worseLines, ...underLines];
if (knownAll.length) md += `\n${knownAll.map((l) => `- ${l}`).join('\n')}\n`;
fs.writeFileSync(path.join(dir, 'summary.md'), md);
if (load >= 2) console.log(`NOTE: highest loadavg seen during the run was ${load}: treat misses as noise until re-measured on a quiet machine.`);
if (skipped) console.log(`${skipped} timing rows were above their ceiling but not failed because the load reached ${load} (>= ${LOAD_SKIP}); counts, sizes and heap are always enforced. NOT a pass for these rows:`);
for (const l of skipLines) console.log(l);
if (known) console.log(`${known} row(s) above their ceiling are KNOWN OVER: bugs accepted for 0.1.0 (John's rule of 2026-11-02), not failed and NOT a pass:`);
for (const l of knownAll) console.log(l);
if (farLines.length) console.log(`PERF SKIPPED ROW FAR OVER CEILING: ${farLines.length} load-skipped row(s) above loadSkipGuard x ci; load does not explain that:\n${farLines.join('\n')}`);
if (absent) { console.log(`${absent} budgets had no measurement${!allowAbsent && absentBad < absent ? ` (${absent - absentBad} allowed by --allow-absent-prefix ${absentPrefixes.join(' ')})` : ''}`); if (absentBad) { console.log('PERF FAIL: rows are missing from a full run (a step failed or was skipped)'); process.exit(1); } }
if (failed) { console.log(`PERF FAIL: ${failed} above the CI ceiling`); process.exit(1); }
if (farLines.length) process.exit(5);
console.log(`perf ok (${known ? `${known} KNOWN OVER their ceiling, bugs accepted for 0.1.0, not a pass for those rows; ` : ''}${missed} above budget but under the ceiling${skipped ? `; ${skipped} rows skipped for load, see the PERF SKIPPED lines` : ''})`);
