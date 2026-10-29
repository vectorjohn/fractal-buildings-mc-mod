#!/usr/bin/env node
// C2 playtest kit, formatting half: turns the region + roads dump of one seed (`fractal-cli roads --out`) and, optionally, a
// fly-through manifest into docs/review/phase6/gate-kit/<seed>/README.md, map.svg and downscaled frames. Called by
// scripts/playtest-kit.sh (which builds the CLI, dumps the terrain and runs `roads`); see that script for the whole recipe.
//
//   node scripts/playtest-kit.mjs seed --seed S --roads FILE --out DIR [--radius R] [--title T] [--fly DIR] [--images N]
//        [--width W] [--quality Q] [--commit C]
//   node scripts/playtest-kit.mjs index --out DIR --seeds A,B,C [--titles T1|T2|T3] [--commit C] [--roadmap FILE] [--radius R]
//
// Pure functions are exported for scripts/playtest-kit.test.mjs.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

export const TIERS = ['hamlet', 'village', 'town', 'city', 'metropolis'];
const LOCATE_CELLS = 8; // FbCommand.SEARCH_CELLS
const CELL = 1024;
const TIER_COLOUR = { hamlet: '#8fbf6a', village: '#d9b24a', town: '#e0803a', city: '#c94c4c', metropolis: '#8a3fb0' };

/** Compass direction from (0,0) to (x,z), Minecraft axes: north is -z, east is +x. */
export function compass(x, z) {
	if (x === 0 && z === 0) return '-';
	const deg = (Math.atan2(x, -z) * 180) / Math.PI; // 0 = north, 90 = east
	const names = ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'];
	return names[((Math.round(deg / 45) % 8) + 8) % 8];
}

export function polylineLength(pts) {
	let len = 0;
	for (let i = 2; i < pts.length; i += 2) len += Math.hypot(pts[i] - pts[i - 2], pts[i + 1] - pts[i - 1]);
	return len;
}

const key = (cx, cz) => `${cx},${cz}`;
/** The game's settlement id for a cell (core `Ids.site`: `s<cx>_<cz>`, negatives as `m<n>`). */
export const siteId = (cx, cz) => `s${cx < 0 ? 'm' + -cx : cx}_${cz < 0 ? 'm' + -cz : cz}`;

/** Settlements (road-graph nodes) with distance from the origin, biome mix from their deciding cell, and road degree. */
export function settlements(dump) {
	const layer = dump.layers[0];
	const cells = new Map(layer.cells.map((c) => [key(c.cx, c.cz), c]));
	const roads = layer.roads;
	const degree = new Map();
	for (const e of roads.edges) for (const end of [e.a, e.b]) degree.set(key(...end), (degree.get(key(...end)) || 0) + 1);
	for (const m of roads.meet) for (const end of [m.a, m.b]) degree.set(key(...end), (degree.get(key(...end)) || 0) + 1);
	return roads.nodes.map((n) => {
		const c = cells.get(key(n.cx, n.cz));
		const groups = c?.groups ? Object.entries(c.groups).sort((a, b) => b[1] - a[1] || (a[0] < b[0] ? -1 : 1)) : [];
		return {
			id: siteId(n.cx, n.cz), cx: n.cx, cz: n.cz, x: n.x, z: n.z, r: n.r, tier: n.tier, port: !!n.port,
			dist: Math.hypot(n.x, n.z), dir: compass(n.x, n.z), links: degree.get(key(n.cx, n.cz)) || 0,
			mix: groups.filter(([, v]) => v >= 0.05).slice(0, 3).map(([g, v]) => `${g} ${Math.round(v * 100)}`).join(', '),
		};
	}).sort((a, b) => a.dist - b.dist || (a.id < b.id ? -1 : 1));
}

/** Road links: one per routed edge and per meeting point, with the settlements at both ends. */
export function links(dump, byCell) {
	const roads = dump.layers[0].roads;
	const out = [];
	for (const e of roads.edges) {
		out.push({ a: byCell.get(key(...e.a)), b: byCell.get(key(...e.b)), len: polylineLength(e.pts), pa: e.pa, pb: e.pb, pts: e.pts, kind: 'road' });
	}
	for (const m of roads.meet) out.push({ a: byCell.get(key(...m.a)), b: byCell.get(key(...m.b)), len: 0, pa: [m.x, m.z], pb: [m.x, m.z], pts: null, kind: 'touching' });
	return out.filter((l) => l.a && l.b);
}

/**
 * A short route for a first hour: walk to the nearest village or larger, follow road links from there to the largest settlement
 * reachable by road within `maxRoad` blocks of road, then the nearest city or metropolis (teleport or elytra) if not yet visited.
 */
export function route(list, ls, { maxRoad = 4000 } = {}) {
	const adj = new Map();
	for (const l of ls) {
		for (const [p, q] of [[l.a, l.b], [l.b, l.a]]) {
			if (!adj.has(p.id)) adj.set(p.id, []);
			adj.get(p.id).push({ to: q, w: Math.max(l.len, 1), link: l });
		}
	}
	const legs = [];
	const rank = (s) => TIERS.indexOf(s.tier);
	const first = list.find((s) => rank(s) >= 1) || list[0];
	if (!first) return legs;
	legs.push({ from: null, to: first, how: 'walk from spawn', len: first.dist });
	let start = first;
	if (!adj.has(first.id)) {
		const linked = list.filter((s) => adj.has(s.id)).sort((a, b) => Math.hypot(a.x - first.x, a.z - first.z) - Math.hypot(b.x - first.x, b.z - first.z))[0];
		if (linked) {
			const d = Math.hypot(linked.x - first.x, linked.z - first.z);
			legs.push({ from: first, to: linked, how: (d > 1500 ? 'teleport (or walk overland; no road from ' : 'walk overland (no road from ') + first.id + ')', len: d });
			start = linked;
		}
	}
	// Dijkstra over road length (graphs are small: a few hundred nodes)
	const dist = new Map([[start.id, 0]]), prev = new Map(), done = new Set();
	for (;;) {
		let u = null, du = Infinity;
		for (const [id, d] of dist) if (!done.has(id) && d < du) { u = id; du = d; }
		if (u === null) break;
		done.add(u);
		for (const { to, w, link } of adj.get(u) || []) {
			const nd = du + w;
			if (nd <= maxRoad && nd < (dist.get(to.id) ?? Infinity)) { dist.set(to.id, nd); prev.set(to.id, { from: u, link }); }
		}
	}
	const byId = new Map(list.map((s) => [s.id, s]));
	let best = null;
	for (const [id, d] of dist) {
		const s = byId.get(id);
		if (!s || id === start.id) continue;
		if (!best || rank(s) > rank(best.s) || (rank(s) === rank(best.s) && d < best.d)) best = { s, d };
	}
	if (best) {
		const chain = [];
		for (let id = best.s.id; id !== start.id; id = prev.get(id).from) chain.unshift(prev.get(id));
		let at = start;
		for (const step of chain) {
			const to = step.link.a.id === at.id ? step.link.b : step.link.a;
			legs.push({ from: at, to, how: step.link.kind === 'touching' ? 'walk across (the two touch)' : 'follow the road', len: step.link.kind === 'touching' ? Math.hypot(to.x - at.x, to.z - at.z) : step.link.len, link: step.link });
			at = to;
		}
	}
	const visited = new Set(legs.map((l) => l.to.id));
	// end over the biggest place near spawn: the nearest metropolis, else the nearest city (elytra over it, a night in it)
	for (const t of [4, 3]) {
		const big = list.find((s) => rank(s) === t);
		if (!big) continue;
		const last = legs[legs.length - 1].to;
		if (!visited.has(big.id)) legs.push({ from: last, to: big, how: 'teleport or elytra', len: Math.hypot(big.x - last.x, big.z - last.z) });
		break;
	}
	return legs;
}

/** What `/fb locate <tier>` should print when run at (0,0): the nearest settlement of that tier or larger within 8 cells. */
export function locateFromOrigin(list) {
	return TIERS.map((t) => {
		const s = list.find((x) => TIERS.indexOf(x.tier) >= TIERS.indexOf(t) && Math.abs(x.cx) <= LOCATE_CELLS && Math.abs(x.cz) <= LOCATE_CELLS);
		return { tier: t, s };
	});
}

const fmt = (n) => Math.round(n).toLocaleString('en-US');
const tp = (x, z) => `\`/tp @s ${x} ~ ${z}\``;

export function mapSvg(list, ls, radius) {
	const R = radius + 512, scale = 8, size = (2 * R) / scale;
	const X = (x) => ((x + R) / scale).toFixed(1), Z = (z) => ((z + R) / scale).toFixed(1);
	const o = [`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size + 40}" width="${size}" height="${size + 40}" font-family="sans-serif" font-size="11">`,
		`<rect width="100%" height="100%" fill="#f4f1ea"/>`];
	for (let g = -Math.floor(R / CELL) * CELL; g <= R; g += CELL) {
		o.push(`<line x1="${X(g)}" y1="0" x2="${X(g)}" y2="${size}" stroke="#d6d0c4"/><line x1="0" y1="${Z(g)}" x2="${size}" y2="${Z(g)}" stroke="#d6d0c4"/>`);
		o.push(`<text x="${X(g)}" y="${size + 12}" text-anchor="middle" fill="#777">${g}</text><text x="2" y="${Number(Z(g)) - 2}" fill="#777">${g}</text>`);
	}
	o.push(`<circle cx="${X(0)}" cy="${Z(0)}" r="${(radius / scale).toFixed(1)}" fill="none" stroke="#999" stroke-dasharray="4 4"/>`);
	for (const l of ls) {
		if (!l.pts) continue;
		const p = [];
		for (let i = 0; i < l.pts.length; i += 8) p.push(`${X(l.pts[i])},${Z(l.pts[i + 1])}`);
		p.push(`${X(l.pts[l.pts.length - 2])},${Z(l.pts[l.pts.length - 1])}`);
		o.push(`<polyline points="${p.join(' ')}" fill="none" stroke="#5a4632" stroke-width="2"/>`);
	}
	for (const s of list) {
		if (Math.abs(s.x) > R + s.r || Math.abs(s.z) > R + s.r) continue;
		o.push(`<circle cx="${X(s.x)}" cy="${Z(s.z)}" r="${Math.max(3, s.r / scale).toFixed(1)}" fill="${TIER_COLOUR[s.tier]}" fill-opacity="0.55" stroke="${TIER_COLOUR[s.tier]}"/>`);
		o.push(`<text x="${X(s.x)}" y="${Number(Z(s.z)) + 4}" text-anchor="middle" fill="#222">${s.id} ${s.tier[0].toUpperCase()}</text>`);
	}
	o.push(`<path d="M${Number(X(0)) - 8},${Z(0)} h16 M${X(0)},${Number(Z(0)) - 8} v16" stroke="#000" stroke-width="2"/><text x="${Number(X(0)) + 10}" y="${Number(Z(0)) - 6}">0,0</text>`);
	o.push(`<text x="4" y="${size + 32}">N up (-z); grid = 1024-block region cells; H V T C M = tier; brown = intercity road; dashed circle = listing radius</text>`);
	o.push('</svg>');
	return o.join('\n') + '\n';
}

/** Reads a fly-through manifest (tsv with a header) and returns the rows of one seed. */
export function flyRows(dir, seed) {
	const f = path.join(dir, 'manifest.tsv');
	if (!fs.existsSync(f)) return [];
	const [head, ...rows] = fs.readFileSync(f, 'utf8').trim().split('\n').map((l) => l.split('\t'));
	return rows.map((r) => Object.fromEntries(head.map((h, i) => [h, r[i]]))).filter((r) => r.seed === seed);
}

/** Picks up to n frames: one wide view per tier (largest tiers first), then street, square and interior frames. */
export function pickFrames(rows, n) {
	const wide = ['view', 'sweep-a', 'overview', 'sweep-b', 'approach'];
	const order = [...TIERS].reverse();
	const picked = [];
	const take = (r) => { if (r && !picked.includes(r) && picked.length < n) picked.push(r); };
	for (const t of order) take(rows.filter((r) => r.tier === t).sort((a, b) => wide.indexOf(a.label) - wide.indexOf(b.label)).find((r) => wide.includes(r.label)));
	for (const lab of ['street', 'square', 'interior', 'bridge']) for (const t of order) take(rows.find((r) => r.tier === t && r.label === lab));
	return picked.slice(0, n);
}

function toJpeg(src, dst, width, quality) {
	const args = [src, '-filter', 'Lanczos', '-resize', `${width}x>`, '-strip', '-quality', String(quality), dst];
	let r = spawnSync('magick', args, { encoding: 'utf8' });
	if (r.error) r = spawnSync('convert', args, { encoding: 'utf8' });
	if (r.error || r.status !== 0) throw new Error(`ImageMagick failed for ${src}: ${r.error?.message || r.stderr}`);
}

export function seedReadme({ seed, title, radius, commit, list, ls, legs, frames, roadsReport }) {
	const near = list.filter((s) => s.dist <= radius);
	const nearIds = new Set(near.map((s) => s.id));
	const L = [];
	L.push(`# Seed ${seed}`, '');
	if (title) L.push(title, '');
	L.push(`Generated by \`scripts/playtest-kit.sh\` at commit \`${commit}\` from an offline plan of this seed (terrain dump at 64-block`,
		'steps, the same columns the game\'s region layer reads, then `fractal-cli roads`). The game should agree; check with',
		'`/fb locate` at spawn (table below). Distances and directions are from 0,0: your spawn point is usually within a few',
		'hundred blocks of it (F3 shows where you are).', '');
	L.push('# World creation', '', '1. Singleplayer, Create New World, game mode of your choice (Creative makes `/tp` and flying easy; allow commands).',
		`2. More World Options: seed \`${seed}\`, world type Default, Generate Structures ON.`, '3. Create. The first chunks around spawn can take a while.', '');
	L.push('# `/fb locate` at spawn should say', '', '| Command | Settlement | Centre | Distance from 0,0 |', '|---|---|---|---|');
	for (const { tier, s } of locateFromOrigin(list)) L.push(`| \`/fb locate ${tier}\` | ${s ? `${s.id} (${s.tier}, radius ${s.r})` : 'none within 8 cells'} | ${s ? `${s.x}, ${s.z}` : ''} | ${s ? `${fmt(s.dist)} ${s.dir}` : ''} |`);
	L.push('', `\`/fb locate\` searches 8 region cells (about 8 km) around you and gives a settlement's centre; a different answer means the offline plan and the game disagree (worth an FBNOTE).`, '');
	L.push(`# Settlements within ${fmt(radius)} blocks of 0,0`, '', '![map](map.svg)', '');
	L.push('| # | Id | Tier | Centre x, z | Radius | Distance, direction | Biome mix (% of footprint) | Port | Road links | Teleport |', '|---|---|---|---|---|---|---|---|---|---|');
	near.forEach((s, i) => L.push(`| ${i + 1} | ${s.id} | ${s.tier} | ${s.x}, ${s.z} | ${s.r} | ${fmt(s.dist)} ${s.dir} | ${s.mix} | ${s.port ? 'yes' : ''} | ${s.links || ''} | ${tp(s.x, s.z)} |`));
	L.push('', 'The teleport keeps your height (`~`); in Survival use Creative or Spectator first, or replace `~` with a height. A long teleport',
		'into unvisited land is held until the destination is generated (an action-bar message says so; up to 30 s).', '');
	L.push('# Road links to follow', '');
	const nearLinks = ls.filter((l) => nearIds.has(l.a.id) || nearIds.has(l.b.id)).sort((p, q) => Math.min(p.a.dist, p.b.dist) - Math.min(q.a.dist, q.b.dist));
	if (!nearLinks.length) L.push('No intercity road starts within the radius.', '');
	else {
		L.push('Intercity roads leave a settlement at an exit and end at the other one\'s exit. Teleport to the start exit and walk.', '',
			'| From | To | Road length | Start exit (x, z) | End exit (x, z) | Start here |', '|---|---|---|---|---|---|');
		for (const l of nearLinks) {
			const [p, q, pa, pb] = l.a.dist <= l.b.dist ? [l.a, l.b, l.pa, l.pb] : [l.b, l.a, l.pb, l.pa];
			L.push(`| ${p.id} (${p.tier}) | ${q.id} (${q.tier}) | ${l.kind === 'touching' ? 'touching' : fmt(l.len)} | ${pa[0]}, ${pa[1]} | ${pb[0]}, ${pb[1]} | ${tp(pa[0], pa[1])} |`);
		}
		L.push('');
	}
	L.push('# A short route for the first hour', '');
	if (!legs.length) L.push('No settlement found.', '');
	else {
		L.push('Walking is about 4.3 blocks a second (sprint 5.6), so 1,000 blocks is about 4 minutes.', '');
		legs.forEach((g, i) => {
			const to = `${g.to.id} (${g.to.tier}, centre ${g.to.x}, ${g.to.z})`;
			const start = g.link && g.link.kind === 'road' ? `; road starts at ${(g.link.a.id === g.from.id ? g.link.pa : g.link.pb).join(', ')}` : '';
			L.push(`${i + 1}. ${g.how} to ${to}: about ${fmt(g.len)} blocks${start}.`);
		});
		L.push('', 'In each: walk the main street to the centre, go into a few buildings (upstairs too), sleep in a bed, open a chest.', '');
	}
	L.push('# Fly-through frames', '');
	if (!frames.length) L.push('None in this run (`scripts/playtest-kit.sh --fly`, or `--fly-from DIR` with an earlier fly-through output).', '');
	else {
		L.push(`Shot by \`scripts/flythrough.sh\` (commit \`${frames[0].commit || 'unknown'}\`), downscaled. The teleport puts you where the camera stood.`, '');
		for (const f of frames) {
			L.push(`![${f.tier} ${f.label}](${f.out})`, '', `${f.tier} ${f.site} (centre ${f.centre}), ${f.label}${f.tp ? `: \`/tp @s ${f.tp}\`` : ''}`, '');
		}
	}
	L.push('# Offline road report', '', '```', roadsReport.trim(), '```', '');
	return L.join('\n');
}

function args(argv) {
	const o = { _: [] };
	for (let i = 0; i < argv.length; i++) {
		if (argv[i].startsWith('--')) o[argv[i].slice(2)] = argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[++i] : true;
		else o._.push(argv[i]);
	}
	return o;
}

function seedMain(o) {
	const radius = Number(o.radius || 4096), n = Number(o.images || 4);
	const dump = JSON.parse(fs.readFileSync(o.roads, 'utf8'));
	const list = settlements(dump);
	const byCell = new Map(list.map((s) => [key(s.cx, s.cz), s]));
	const ls = links(dump, byCell);
	const legs = route(list.filter((s) => s.dist <= radius + 1024), ls);
	fs.rmSync(o.out, { recursive: true, force: true });
	fs.mkdirSync(o.out, { recursive: true });
	const frames = [];
	if (o.fly) {
		for (const r of pickFrames(flyRows(o.fly, o.seed), n)) {
			const src = path.join(o.fly, r.file);
			if (!fs.existsSync(src)) continue;
			r.out = r.file.replace(/\.(png|jpg)$/i, '') + '.jpg';
			toJpeg(src, path.join(o.out, r.out), Number(o.width || 640), Number(o.quality || 70));
			frames.push(r);
		}
	}
	const report = o.report && fs.existsSync(o.report) ? fs.readFileSync(o.report, 'utf8') : '';
	fs.writeFileSync(path.join(o.out, 'map.svg'), mapSvg(list.filter((s) => s.dist <= radius + 1536), ls.filter((l) => l.a.dist <= radius + 1536 || l.b.dist <= radius + 1536), radius));
	fs.writeFileSync(path.join(o.out, 'README.md'), seedReadme({ seed: o.seed, title: o.title, radius, commit: o.commit || 'unknown', list, ls, legs, frames, roadsReport: report }));
	const near = list.filter((s) => s.dist <= radius);
	console.log(`playtest-kit: seed ${o.seed}: ${near.length} settlements within ${radius} (${TIERS.map((t) => `${near.filter((s) => s.tier === t).length} ${t}`).join(', ')}), ${frames.length} frames, route ${legs.length} legs`);
}

/** Open (unticked) items of the ROADMAP's Phase 6 section, bold titles only. */
export function openPhase6(roadmap) {
	const lines = roadmap.split('\n');
	const a = lines.findIndex((l) => l.startsWith('## Phase 6')), b = lines.findIndex((l, i) => i > a && l.startsWith('## '));
	if (a < 0) return [];
	return lines.slice(a, b < 0 ? undefined : b).filter((l) => /^\s*- \[ \] \*\*/.test(l)).map((l) => {
		const m = l.match(/\*\*(.+?)\*\*/);
		return (l.match(/^\s*/)[0].length >= 2 ? '  ' : '') + '- ' + (m ? m[1] : l.trim());
	});
}

/** Rewrites the block between the KIT markers of the kit's README.md (the rest of the file is hand-written). */
function indexMain(o) {
	const f = path.join(o.out, 'README.md');
	const seeds = String(o.seeds).split(','), titles = o.titles ? String(o.titles).split('|') : [];
	const B = '<!-- KIT:BEGIN (generated by scripts/playtest-kit.sh; edit outside the markers) -->', E = '<!-- KIT:END -->';
	const g = [B, '', `Generated ${o.date || ''} at commit \`${o.commit || 'unknown'}\` (settlement lists within ${fmt(Number(o.radius || 4096))} blocks of 0,0).`, '',
		'| Seed | What it is | Page |', '|---|---|---|'];
	seeds.forEach((s, i) => g.push(`| \`${s}\` | ${titles[i] || ''} | [${s}](${s}/README.md) |`));
	if (o.roadmap && fs.existsSync(o.roadmap)) {
		const open = openPhase6(fs.readFileSync(o.roadmap, 'utf8'));
		g.push('', 'Phase 6 items still open in the ROADMAP at that commit (bugs in flight show up here until they merge):', '', ...(open.length ? open : ['- none']));
	}
	g.push('', E);
	const text = fs.readFileSync(f, 'utf8');
	const i = text.indexOf('<!-- KIT:BEGIN'), j = text.indexOf(E);
	if (i < 0 || j < 0) throw new Error(`${f}: no KIT markers`);
	fs.writeFileSync(f, text.slice(0, i) + g.join('\n') + text.slice(j + E.length));
}

function main() {
	const o = args(process.argv.slice(2));
	if (o._[0] === 'seed') return seedMain(o);
	if (o._[0] === 'index') return indexMain(o);
	console.error('usage: playtest-kit.mjs seed --seed S --roads FILE --out DIR [--radius R] [--title T] [--fly DIR] [--images N] [--report FILE] [--commit C]');
	process.exit(2);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) main();
