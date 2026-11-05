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
// PERFKNOWN (the user, 2026-11-02: never raise a ceiling; exceeding one is a bug, accepted for 0.1.0): knownOver rows
const ko = { issue: 'PERFORMANCE.md known perf bugs: test row', since: '2026-11-02', accepted: '0.1.0', last: 5000 };
budget.budgets = [{ key: 'k.ms', label: 'k', unit: 'ms', budget: 1000, ci: 3000, knownOver: ko }];
function summary(world) { // run() but keep summary.md
	const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'perfcheck-'));
	fs.writeFileSync(path.join(dir, 'b.json'), JSON.stringify(budget));
	fs.writeFileSync(path.join(dir, 'world.json'), JSON.stringify(world));
	const r = spawnSync('node', [path.join(import.meta.dirname, 'perf-check.mjs'), path.join(dir, 'b.json'), dir], { encoding: 'utf8' });
	r.md = fs.readFileSync(path.join(dir, 'summary.md'), 'utf8');
	fs.rmSync(dir, { recursive: true });
	return r;
}
const kn = summary({ 'k.ms': 5900 });
check('known over: exit 0 with the visible marker', kn.status === 0 && kn.stdout.includes('PERF KNOWN OVER (bug, accepted for 0.1.0: PERFORMANCE.md known perf bugs: test row) k.ms value=5900 ceiling=3000') && !kn.stdout.includes('PERF FAIL'));
check('known over: not reported as a plain pass', kn.stdout.includes('1 KNOWN OVER their ceiling') && kn.stdout.includes('NOT a pass'));
check('known over: summary.md shows it', kn.md.includes('| KNOWN OVER (bug, accepted for 0.1.0:') && kn.md.includes('- PERF KNOWN OVER (bug'));
budget.budgets.push({ key: 'k2.ms', label: 'k2', unit: 'ms', budget: 1000, ci: 3000 });
const unk2 = run({ 'k2.ms': 5900, 'k.ms': 5000 });
check('unknown over (no knownOver) still fails beside a known one', unk2.status === 1 && unk2.stdout.includes('PERF FAIL: 1 above the CI ceiling') && unk2.stdout.includes('| FAIL |'));
budget.budgets.pop();
const wr = run({ 'k.ms': 6300 }); // 5000 x 1.25 = 6250
check('known over but worse than last + 25 %: fails', wr.status === 1 && wr.stdout.includes('PERF KNOWN OVER WORSE k.ms value=6300 last=5000') && wr.stdout.includes('PERF FAIL'));
const edge = run({ 'k.ms': 6250 });
check('exactly last + 25 %: still known over', edge.status === 0 && edge.stdout.includes('PERF KNOWN OVER (bug'));
budget.budgets[0].knownOver = { ...ko, margin: 0.1 };
check('per-row margin 10 %: 5600 fails', run({ 'k.ms': 5600 }).status === 1);
budget.budgets[0].knownOver = { ...ko, last: undefined };
const nolast = run({ 'k.ms': 3500 });
check('knownOver without a last value fails', nolast.status === 1 && nolast.stdout.includes('no positive'));
budget.budgets[0].knownOver = ko;
const back = run({ 'k.ms': 2500 });
check('known over back under its ceiling: passes with the remove note', back.status === 0 && back.stdout.includes('PERF KNOWN OVER NOW UNDER CEILING k.ms') && back.stdout.includes('remove the knownOver field') && back.stdout.includes('(knownOver: remove?)'));
budget.budgets[0].unit = 'ms';
const loadedKn = run({ 'k.ms': 9000, 'planner.load.before': 20 });
check('load skip still comes first for a known-over row (skip line, no known line)', loadedKn.status === 0 && loadedKn.stdout.includes('PERF SKIPPED (load) k.ms') && !loadedKn.stdout.includes('PERF KNOWN OVER ('));
// the real budget file: knownOver rows are well formed, and no ci/budget was touched to make room (they sit below their last)
const realB = JSON.parse(fs.readFileSync(path.join(import.meta.dirname, '..', 'mod', 'perf-budget.json'), 'utf8'));
const kos = realB.budgets.filter((b) => b.knownOver);
check('real budget: every knownOver has issue, since, accepted, a last above its ci (none is fine)', kos.every((b) => b.knownOver.issue && b.knownOver.since && b.knownOver.accepted && b.knownOver.last > (b.ci ?? b.budget * 2) && !b.knownOver.issue.includes('|')));
process.exit(bad ? 1 : 0);
