#!/usr/bin/env node
// Turns a folder of screenshots plus their /fbnote sidecars (FBNOTE; docs/PLAYTEST.md) into a markdown gallery for review:
//
//   scripts/dev scripts/playtest-gallery.mjs <screenshots-dir> [--out docs/review/playtest-<date>] [--date YYYY-MM-DD]
//       [--width 1280] [--quality 82] [--notes-only] [--author NAME]
//
// Writes <out>/REVIEW.md (same layout as docs/review/phase6/playtest-comments/REVIEW.md: frontmatter, one `![[image|800]]` per shot with
// the comment under it) and a downscaled JPEG per image (ImageMagick; the PNGs stay where they are). A screenshot without a sidecar is
// listed too (no caption) unless --notes-only. Sidecars without a PNG are skipped with a warning. Plain markdown for SilverBullet
// (docs/SILVERBULLET.md): `#` headings, `![[file|800]]` images, no anchors.
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { pathToFileURL } from 'node:url';

const FORMAT = 'fb-playtest-note/1';

/** Reads <dir>/*.png with their <same name>.json sidecars (when present and of our format), sorted by name. */
export function readShots(dir, { notesOnly = false } = {}) {
	const out = [];
	for (const f of fs.readdirSync(dir).sort()) {
		if (!/\.png$/i.test(f)) continue;
		const base = f.replace(/\.png$/i, '');
		let note = null;
		const j = path.join(dir, base + '.json');
		if (fs.existsSync(j)) {
			try {
				const parsed = JSON.parse(fs.readFileSync(j, 'utf8'));
				if (parsed && parsed.format === FORMAT) note = parsed;
				else console.error(`warning: ${j} is not ${FORMAT}, ignored`);
			} catch (e) {
				console.error(`warning: ${j}: ${e.message}`);
			}
		}
		if (notesOnly && !note) continue;
		out.push({ base, png: path.join(dir, f), note });
	}
	return out;
}

const num = (v, d = 1) => (typeof v === 'number' ? String(Math.round(v * 10 ** d) / 10 ** d) : '?');

/** One-line facts under a caption: position, biome, targeted block, then the planner's view. */
export function facts(note) {
	if (!note) return '';
	const parts = [];
	const p = note.position;
	if (p) parts.push(`\`${Math.floor(p.x)} ${Math.floor(p.y)} ${Math.floor(p.z)}\` yaw ${num(p.yaw, 0)} pitch ${num(p.pitch, 0)}`);
	if (note.dimension && note.dimension !== 'minecraft:overworld') parts.push(note.dimension);
	if (note.biome) parts.push(note.biome.replace('minecraft:', ''));
	if (note.target) parts.push(`looking at ${note.target.block.replace('minecraft:', '')} \`${note.target.x} ${note.target.y} ${note.target.z}\``);
	const pl = note.planner;
	if (pl && pl.available === false) parts.push(`planner: ${pl.note || 'not available'}`);
	else if (pl) {
		if (pl.settlement) parts.push(`settlement ${pl.settlement.id} (${pl.settlement.tier})`);
		else if ('settlement' in pl) parts.push('outside any settlement');
		if (pl.district) parts.push(`district ${pl.district}`);
		if (pl.lot) parts.push(`lot ${pl.lot.id}`);
		if (pl.building) parts.push(`building ${pl.building.id} (${pl.building.type}, ${pl.building.style}, seed ${pl.building.seed}, ${pl.building.storeys} storeys)`);
		if (pl.room) parts.push(`room ${pl.room.id} (${pl.room.use}, storey ${pl.room.storey})`);
		if (pl.street) parts.push(`street ${pl.street.id} (${pl.street.kind}, width ${pl.street.width})`);
		if (pl.plaza) parts.push(`plaza ${pl.plaza.id} (${pl.plaza.kind})`);
	}
	return parts.join('; ');
}

const most = (xs) => {
	const c = new Map();
	for (const x of xs) if (x != null) c.set(x, (c.get(x) || 0) + 1);
	return [...c.entries()].sort((a, b) => b[1] - a[1] || String(a[0]).localeCompare(String(b[0])))[0]?.[0];
};

/** The REVIEW.md text for entries [{base, note}] whose images are <base>.jpg. */
export function renderMarkdown(entries, { date, author = 'user' }) {
	const notes = entries.map((e) => e.note).filter(Boolean);
	const commit = most(notes.map((n) => n.mod?.commit));
	const seed = most(notes.map((n) => n.seed));
	const lines = ['---', 'status: Ready for review', `author: ${author}`];
	if (commit) lines.push(`commit: ${commit}`);
	if (seed != null) lines.push(`seed: ${seed}`);
	lines.push('---', '', '# Playtest Notes', `Playtest on ${date}`);
	if (commit) lines.push(`Build commit: ${commit}`);
	lines.push('', 'Taken in game with `/fbnote <comment>`; the position, the planner ids and the targeted block are in each caption.', '');
	for (const e of entries) {
		lines.push('', `![[${e.base}.jpg|800]]`);
		lines.push(e.note ? e.note.comment || '(no comment)' : '(no note)');
		const f = facts(e.note);
		if (f) lines.push(`  - ${f}`);
	}
	return lines.join('\n') + '\n';
}

/** Converts one PNG to a downscaled JPEG with ImageMagick (`magick`, else `convert`); throws on failure. */
export function toJpeg(src, dst, { width = 1280, quality = 82 } = {}) {
	const args = [src, '-filter', 'Lanczos', '-resize', `${width}x>`, '-strip', '-quality', String(quality), '-sampling-factor', '4:4:4', dst];
	let r = spawnSync('magick', args, { encoding: 'utf8' });
	if (r.error) r = spawnSync('convert', args, { encoding: 'utf8' });
	if (r.error || r.status !== 0) throw new Error(`ImageMagick failed for ${src}: ${r.error?.message || r.stderr}`);
}

export function buildGallery(srcDir, outDir, { date, width, quality, notesOnly, author, convert = toJpeg } = {}) {
	const entries = readShots(srcDir, { notesOnly });
	if (!entries.length) throw new Error(`no screenshots${notesOnly ? ' with notes' : ''} in ${srcDir}`);
	fs.mkdirSync(outDir, { recursive: true });
	for (const e of entries) convert(e.png, path.join(outDir, e.base + '.jpg'), { width, quality });
	const md = renderMarkdown(entries, { date, author });
	fs.writeFileSync(path.join(outDir, 'REVIEW.md'), md);
	return { entries: entries.length, notes: entries.filter((e) => e.note).length, md: path.join(outDir, 'REVIEW.md') };
}

function main(argv) {
	const opt = { notesOnly: false };
	const pos = [];
	for (let i = 0; i < argv.length; i++) {
		const a = argv[i];
		if (a === '--out') opt.out = argv[++i];
		else if (a === '--date') opt.date = argv[++i];
		else if (a === '--width') opt.width = Number(argv[++i]);
		else if (a === '--quality') opt.quality = Number(argv[++i]);
		else if (a === '--author') opt.author = argv[++i];
		else if (a === '--notes-only') opt.notesOnly = true;
		else if (a.startsWith('--')) { console.error(`unknown option ${a}`); process.exit(2); }
		else pos.push(a);
	}
	if (pos.length !== 1) {
		console.error('usage: playtest-gallery.mjs <screenshots-dir> [--out DIR] [--date YYYY-MM-DD] [--width 1280] [--quality 82] [--notes-only] [--author NAME]');
		process.exit(2);
	}
	opt.date ??= new Date().toISOString().slice(0, 10);
	// Default author: the first word of git's user.name (the person who played), else "user".
	opt.author ??= (spawnSync('git', ['config', 'user.name'], { encoding: 'utf8' }).stdout ?? '').trim().split(/\s+/)[0] || 'user';
	const out = opt.out ?? path.join('docs', 'review', `playtest-${opt.date}`);
	const r = buildGallery(pos[0], out, opt);
	console.log(`${r.entries} screenshots (${r.notes} with notes) -> ${r.md}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
	try { main(process.argv.slice(2)); } catch (e) { console.error(e.message); process.exit(1); }
}
