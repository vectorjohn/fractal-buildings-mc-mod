// Self-test of scripts/playtest-gallery.mjs: scripts/dev node scripts/playtest-gallery.test.mjs  (FBNOTE)
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { buildGallery, facts, renderMarkdown } from './playtest-gallery.mjs';

let bad = 0;
const check = (name, ok) => { if (!ok) { bad++; console.error('FAIL', name); } else console.log('ok', name); };

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fbgallery-'));
const note = {
	format: 'fb-playtest-note/1', comment: 'stairs don\'t go to top', seed: 1709797029209580947n.toString(),
	position: { x: 2721.5, y: 67, z: 2696.2, yaw: 90.4, pitch: 12 }, biome: 'minecraft:desert', dimension: 'minecraft:overworld',
	target: { block: 'minecraft:stone_bricks', x: 2721, y: 66, z: 2696 }, mod: { version: '0.1.0', commit: 'abcd1234' },
	planner: { available: true, settlement: { id: 's3_1', tier: 'village' }, district: 'd1_2', lot: { id: 'd1_2.l4' },
		building: { id: 'b4', type: 'highrise', style: 'desert', seed: 77, storeys: 9 }, room: { id: 'r2', use: 'bedroom', storey: 3 } },
};
const png = path.join(dir, '2026-10-28_12.00.00.png');
// a real PNG from ImageMagick, wider than the output width
const mk = spawnSync('magick', ['-size', '1920x1080', 'gradient:red-blue', png]);
check('imagemagick available', mk.status === 0);
fs.writeFileSync(path.join(dir, '2026-10-28_12.00.00.json'), JSON.stringify(note));
fs.copyFileSync(png, path.join(dir, '2026-10-28_12.05.00.png')); // no sidecar
fs.copyFileSync(png, path.join(dir, '2026-10-28_12.06.00.png'));
fs.writeFileSync(path.join(dir, '2026-10-28_12.06.00.json'), JSON.stringify({ ...note, comment: 'remote', planner: { available: false, note: 'planner data is only recorded in singleplayer' } }));
fs.writeFileSync(path.join(dir, 'orphan.json'), JSON.stringify(note)); // sidecar without png: ignored

const out = path.join(dir, 'gallery');
const r = buildGallery(dir, out, { date: '2026-10-28', width: 640 });
check('three shots, two notes', r.entries === 3 && r.notes === 2);
const md = fs.readFileSync(path.join(out, 'REVIEW.md'), 'utf8');
check('frontmatter', md.startsWith('---\nstatus: Ready for review\nauthor: user\ncommit: abcd1234\n') && md.includes('seed: 1709797029209580947'));
check('image embed and caption', md.includes('![[2026-10-28_12.00.00.jpg|800]]\nstairs don\'t go to top\n'));
check('coords and planner ids', md.includes('`2721 67 2696`') && md.includes('settlement s3_1 (village)') && md.includes('building b4 (highrise, desert, seed 77, 9 storeys)') && md.includes('room r2 (bedroom, storey 3)') && md.includes('lot d1_2.l4'));
check('target block', md.includes('looking at stone_bricks `2721 66 2696`'));
check('remote note says planner unavailable', md.includes('planner: planner data is only recorded in singleplayer'));
check('no-sidecar shot listed', md.includes('![[2026-10-28_12.05.00.jpg|800]]\n(no note)'));
check('no wiki links, no anchors', !/\[\[[^\]|]*\]\]/.test(md) && !md.includes('](#'));
const jpg = path.join(out, '2026-10-28_12.00.00.jpg');
const size = spawnSync('magick', ['identify', '-format', '%w %m', jpg], { encoding: 'utf8' }).stdout;
check('jpeg, downscaled to 640', size === '640 JPEG');
check('smaller than the png', fs.statSync(jpg).size < fs.statSync(png).size);
const only = buildGallery(dir, path.join(dir, 'g2'), { date: '2026-10-28', notesOnly: true });
check('notes-only skips the shot without sidecar', only.entries === 2);
check('outside a settlement', facts({ planner: { available: true, settlement: null } }).includes('outside any settlement'));
check('empty folder fails', (() => { try { buildGallery(fs.mkdtempSync(path.join(os.tmpdir(), 'fbempty-')), path.join(dir, 'g3'), {}); return false; } catch { return true; } })());
const cli = spawnSync('node', [path.join(import.meta.dirname, 'playtest-gallery.mjs'), dir, '--out', path.join(dir, 'g4'), '--date', '2026-10-28'], { encoding: 'utf8' });
check('cli runs', cli.status === 0 && cli.stdout.includes('3 screenshots (2 with notes)'));
fs.rmSync(dir, { recursive: true });
process.exit(bad ? 1 : 0);
