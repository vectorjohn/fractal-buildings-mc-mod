// Self-test of scripts/playtest-kit.mjs: `scripts/dev node scripts/playtest-kit.test.mjs` (exit 1 on a failure).
import assert from 'node:assert/strict';
import { compass, siteId, polylineLength, settlements, links, route, locateFromOrigin, pickFrames, openPhase6, mapSvg } from './playtest-kit.mjs';

assert.equal(compass(0, -100), 'N');
assert.equal(compass(100, 0), 'E');
assert.equal(compass(0, 100), 'S');
assert.equal(compass(-100, -100), 'NW');
assert.equal(siteId(-3, 2), 'sm3_2');
assert.equal(siteId(0, -1), 's0_m1');
assert.equal(polylineLength([0, 0, 3, 4, 3, 10]), 11);

const node = (cx, cz, x, z, r, tier) => ({ cx, cz, x, z, r, tier, port: false });
const dump = { layers: [{ cells: [{ cx: 0, cz: 0, groups: { temperate: 0.7, water: 0.3 } }], roads: {
	nodes: [node(0, 0, 300, 0, 60, 'village'), node(1, 0, 1300, 0, 200, 'town'), node(2, 0, 2300, 0, 500, 'city'), node(0, 3, 0, 3000, 900, 'metropolis'), node(-1, 0, -200, 0, 40, 'hamlet')],
	edges: [{ a: [0, 0], b: [1, 0], pa: [360, 0], pb: [1100, 0], pts: [360, 0, 1100, 0] }, { a: [1, 0], b: [2, 0], pa: [1500, 0], pb: [1800, 0], pts: [1500, 0, 1800, 0] }],
	meet: [], dropped: [] } }] };
const list = settlements(dump);
assert.deepEqual(list.map((s) => s.id), ['sm1_0', 's0_0', 's1_0', 's2_0', 's0_3']);
assert.equal(list[1].mix, 'temperate 70, water 30');
assert.equal(list[1].links, 1);
const ls = links(dump, new Map(list.map((s) => [`${s.cx},${s.cz}`, s])));
assert.equal(ls.length, 2);
const legs = route(list, ls);
// nearest village+ (s0_0), along the roads to the city, then the metropolis
assert.deepEqual(legs.map((l) => l.to.id), ['s0_0', 's1_0', 's2_0', 's0_3']);
assert.equal(legs[1].how, 'follow the road');
assert.equal(legs[3].how, 'teleport or elytra');
const loc = locateFromOrigin(list);
assert.equal(loc[0].s.id, 'sm1_0');
assert.equal(loc[2].s.id, 's1_0');
assert.ok(mapSvg(list, ls, 4096).startsWith('<svg'));

const rows = [
	{ tier: 'town', label: 'street' }, { tier: 'town', label: 'view' }, { tier: 'city', label: 'sweep-b' }, { tier: 'city', label: 'sweep-a' }, { tier: 'hamlet', label: 'view' },
];
assert.deepEqual(pickFrames(rows, 3).map((r) => `${r.tier}-${r.label}`), ['city-sweep-a', 'town-view', 'hamlet-view']);
assert.deepEqual(pickFrames(rows, 5).map((r) => `${r.tier}-${r.label}`), ['city-sweep-a', 'town-view', 'hamlet-view', 'town-street']);

const roadmap = '## Phase 5\n- [ ] **old**\n## Phase 6: x\n- [x] **done**\n- [ ] **Bug list**\n  - [ ] **6.19 A: b**\n## Phase 7\n- [ ] **later**\n';
assert.deepEqual(openPhase6(roadmap), ['- Bug list', '  - 6.19 A: b']);
console.log('playtest-kit.test: ok');
