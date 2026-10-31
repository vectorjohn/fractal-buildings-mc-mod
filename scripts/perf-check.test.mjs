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

// loadSkipGuard: recorded runs (scripts/perf-fixtures/README.md) against the timing rows (ms, x, %) of the real mod/perf-budget.json,
// planner-only style (--allow-absent). Count/size rows are left out: they are exact per commit, so a later tightening (SKIPROLL
// 6437f6cb: sparseColumns ceiling 85000 -> 64000, the fixtures recorded 68,343) made an old recording FAIL on a row this test is not about.
function fixture(name, env = {}) {
	const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'perfcheck-'));
	fs.cpSync(path.join(import.meta.dirname, 'perf-fixtures', name), dir, { recursive: true });
	const real = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '..', 'mod', 'perf-budget.json'), 'utf8'));
	const budgetFile = path.join(dir, 'timing-budget.json');
	fs.writeFileSync(budgetFile, JSON.stringify({ budgets: real.budgets.filter((b) => ['ms', 'x', '%'].includes(b.unit)) }));
	const e = { ...process.env, ...env };
	for (const k of ['PERF_SKIP_FAR', 'PERF_LOAD_SKIP']) if (!(k in env)) delete e[k];
	const r = spawnSync('node', [path.join(import.meta.dirname, 'perf-check.mjs'), budgetFile, dir, '--allow-absent'], { encoding: 'utf8', env: e });
	fs.rmSync(dir, { recursive: true });
	return r;
}
const ms = fixture('mainstub-87869d24d3');
check('MAINSTUB 60 s first tile under load exits 5', ms.status === 5 && ms.stdout.includes('PERF SKIPPED ROW FAR OVER CEILING'));
check('MAINSTUB prints the loud skip line', ms.stdout.includes('PERF SKIPPED (load) district.cold.plan.ms value=59807 ceiling=4000'));
check('MAINSTUB region row (2.1x, guard 7) is skipped, not far', ms.stdout.includes('PERF SKIPPED (load) region.cold.warm.ms') && !/FAR OVER CEILING[\s\S]*region\.cold/.test(ms.stdout));
const msLoose = fixture('mainstub-87869d24d3', { PERF_SKIP_FAR: '20' });
check('PERF_SKIP_FAR=20 lets it through, still loud', msLoose.status === 0 && msLoose.stdout.includes('PERF SKIPPED (load) district.cold.plan.ms'));
const msQuiet = fixture('mainstub-87869d24d3', { PERF_LOAD_SKIP: '100' });
check('not loaded: plain FAIL, exit 1', msQuiet.status === 1 && msQuiet.stdout.includes('PERF FAIL'));
const lo = fixture('leaforder-ac2f7fc037');
check('loaded run within its guards passes, rows printed as skipped', lo.status === 0 && lo.stdout.includes('PERF SKIPPED (load) district.cold.plan.ms') && lo.stdout.includes('rows skipped for load'));
// COLDARRIVAL: a report-only row (budget null) is printed with its vanilla value and ratio, never judged, even far over anything
budget.budgets = [{ key: 'a.ms', baseKey: 'base.a.ms', label: 'arr', unit: 'ms', budget: null }];
const ro = run({ 'a.ms': 60000, 'base.a.ms': 2000 });
check('report-only row: exit 0, ratio 30.0x, status report', ro.status === 0 && ro.stdout.includes('30.0x') && ro.stdout.includes('| report |'));
const roAbsent = run({});
check('report-only row absent from a full run still fails', roAbsent.status === 1);
// COLDARRIVAL polish: FB_PERF_ARRIVAL=0 makes perf.sh pass --allow-absent-prefix arrival.: only those rows may be missing
budget.budgets = [{ key: 'arrival.cold.metro.ms', label: 'arr', unit: 'ms', budget: null }, { key: 'other.ms', label: 'o', unit: 'ms', budget: 10 }];
const noArr = run({ 'other.ms': 5 }, ['--allow-absent-prefix', 'arrival.']);
check('--allow-absent-prefix arrival.: missing arrival row passes', noArr.status === 0 && noArr.stdout.includes('1 allowed by --allow-absent-prefix arrival.'));
const noOther = run({}, ['--allow-absent-prefix', 'arrival.']);
check('--allow-absent-prefix arrival.: another missing row still fails', noOther.status === 1 && noOther.stdout.includes('rows are missing'));
process.exit(bad ? 1 : 0);
