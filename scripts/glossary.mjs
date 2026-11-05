#!/usr/bin/env node
// Generates docs/GLOSSARY.md: one table row per item code (PARREADS, MAINSTUB, TPHOLD, ...) or run term.
// Called by scripts/glossary.sh (see its header for usage). Pure file reads, deterministic, no network.
//
// Sources, in precedence order (the first source that defines a code wins; later ones only add links):
//   1. docs/ROADMAP.md bold items: `- [ ] **6.25 MAINSTUB: title**`, `- [x] **PARREADS: title**`
//      (checkbox -> status open/done; no checkbox -> "-").
//   2. docs/archive/roadmap-phase-*.md, same form (links to the archived file).
//   3. docs/plans/<slug>.md whose upper-cased slug is a code: adds a "plan" link, or defines the code from
//      the plan's first heading when nothing above did.
//   4. docs/log/<date>-<slug>.md whose first heading names a code (`# 6.25 MAINSTUB: ...`, `# P6 CACHE: ...`,
//      `# COLDCHUNK: ...`): defines codes no ROADMAP entry has (status done: a log is written at the end).
//      The newest log wins for the title; a ROADMAP code gets no log link (its entry links its logs).
//   5. docs/glossary-extra.tsv (hand-kept): CODE <tab> meaning <tab> link (relative to docs/, may be empty)
//      <tab> status (optional). For codes and run terms that are not ROADMAP items; a code known only from a
//      log takes its meaning (and extra link) from here. A code defined by a ROADMAP item or plan
//      above is reported on stderr and the extra row is ignored.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, posix } from 'node:path';

const ROOT = process.argv[2];
const DOCS = join(ROOT, 'docs');
const entries = new Map(); // code -> { code, num, title, status, links: [{text, href}] }

const CODE_RE = '[A-Z][A-Z0-9]*[A-Z0-9](?:-[A-Z0-9]+)?';
const read = (p) => readFileSync(p, 'utf8');
const sorted = (dir, re) => (existsSync(dir) ? readdirSync(dir).filter((f) => re.test(f)).sort() : []);

function cleanTitle(t) {
	let s = t
		.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1') // links -> text (paths are relative to their own file)
		.replace(/\*\*/g, '')
		.replace(/\s+/g, ' ')
		.replace(/\s*\((?:\d{4}-\d{2}-\d{2}|coder|lead-coder)[^)]*\)\s*$/, '') // trailing "(2026-10-28)", "(coder)"
		.replace(/\s*-\s*WIP$/, '')
		.trim()
		.replace(/[.:]$/, '');
	if (s.length > 110) s = s.slice(0, 107).replace(/\s+\S*$/, '') + '...';
	return s.replace(/\|/g, '\\|');
}

function add(code, e) {
	if (entries.has(code)) return false;
	entries.set(code, { code, links: [], ...e });
	return true;
}

// 1 and 2: roadmap files.
const ITEM_RE = new RegExp(`^\\s*- (?:\\[([ xX])\\] )?\\*\\*(?:(\\d+\\.\\d+[a-z]?)\\s+)?(${CODE_RE})(?::\\s*|\\s+)(.*)$`);
function scanRoadmap(rel, label) {
	const lines = read(join(DOCS, rel)).split('\n');
	for (let i = 0; i < lines.length; i++) {
		const m = ITEM_RE.exec(lines[i]);
		if (!m) continue;
		const [, box, num, code] = m;
		if (code.length < 3 || !/[A-Z].*[A-Z0-9]/.test(code)) continue;
		let rest = m[4];
		for (let j = 1; j <= 3 && !rest.includes('**') && i + j < lines.length; j++) rest += ' ' + lines[i + j].trim();
		const end = rest.indexOf('**');
		const title = cleanTitle(end >= 0 ? rest.slice(0, end) : rest);
		const status = box === undefined ? '-' : box === ' ' ? 'open' : 'done';
		add(code, { num: num || '', title, status, links: [{ text: num ? `${label} ${num}` : label, href: rel }] });
	}
}
scanRoadmap('ROADMAP.md', 'ROADMAP');
for (const f of sorted(join(DOCS, 'archive'), /^roadmap-phase-.*\.md$/)) {
	scanRoadmap(`archive/${f}`, `archive ${f.replace(/^roadmap-(phase-[^.]*)\.md$/, '$1')}`);
}

// 3: plans named after a code.
for (const f of sorted(join(DOCS, 'plans'), /\.md$/)) {
	const code = f.replace(/\.md$/, '').toUpperCase();
	if (!new RegExp(`^${CODE_RE}$`).test(code)) continue;
	const text = read(join(DOCS, 'plans', f));
	if (entries.has(code)) {
		entries.get(code).links.push({ text: 'plan', href: `plans/${f}` });
		continue;
	}
	const head = (text.match(/^# (.*)$/m) || [, ''])[1];
	if (!head.toUpperCase().includes(code) || !head.includes(code)) continue; // phase3.md etc. are not codes
	const title = cleanTitle(head.replace(new RegExp(`^.*?${code}(?:\\s*plan)?[:\\s-]*`), '')) || 'plan';
	add(code, { num: '', title, status: '-', links: [{ text: 'plan', href: `plans/${f}` }] });
}

// 4: logs, newest first; a code keeps the newest log heading that has a title (round-N logs often have none).
// A lower-case `# p6-spilldirt: title` heading names the code SPILLDIRT (one-word slugs only).
const LOG_RE = new RegExp(
	`^# (?:\\d{4}-\\d{2}-\\d{2}\\s+)?(?:(P\\d)[\\s-]+)?(?:(\\d+\\.\\d+[a-z]?)(?:\\s*/\\s*\\d+\\.\\d+)?\\s+)?(${CODE_RE})(?=[:\\s(]|$)\\s*(.*)$`,
);
const SLUG_RE = /^# (?:\d{4}-\d{2}-\d{2}\s+)?p\d-([a-z][a-z0-9]{3,})(?::\s*|\s+)(.*)$/;
const UNTITLED = '(see log)';
for (const f of sorted(join(DOCS, 'log'), /\.md$/).reverse()) {
	const head = (read(join(DOCS, 'log', f)).match(/^# .*$/m) || [''])[0];
	let num = '', code, rest;
	const m = LOG_RE.exec(head);
	const s = SLUG_RE.exec(head);
	// Short step codes (B1, D0, TA) are kept only with a phase (`# P6 B1: ...`, or a p5- file and a colon).
	const phase = m && m[1] ? m[1].slice(1) : (f.match(/-p(\d)-/) || [])[1];
	const short = m && m[3].replace(/-/g, '').length < 4;
	if (m && !/^P\d$/.test(m[3]) && (!short || (phase && (m[1] || m[4].startsWith(':'))))) {
		[, , num = '', code, rest] = m;
		if (short) rest = `Phase ${phase} step: ${rest.replace(/^:\s*/, '')}`;
	} else if (s) {
		code = s[1].toUpperCase();
		rest = s[2].replace(/^\(([^)]*)\)$/, '$1');
	} else continue;
	rest = rest.replace(/^(?:S\d+[a-z]?|R\d+|DX|M\d+|follow-ups?|round \d+)\b[^:]*:?\s*/i, '').replace(/^:\s*/, '');
	rest = rest.replace(/^\([^)]*\):\s*/, ''); // "(7.12 follow-up): title"
	const title = cleanTitle(rest) || UNTITLED;
	const old = entries.get(code);
	if (old && !(old.fromLog && old.title === UNTITLED && title !== UNTITLED)) continue;
	entries.delete(code);
	add(code, { num: num || (old ? old.num : ''), title, status: 'done', fromLog: true, links: [{ text: 'log', href: `log/${f}` }] });
}

// 4b: Polish backlog lines `- 6.x [playtest 2026-10-26] CODE (source note): text` naming a code nothing above defines
//     (status polish; an extra row may replace the title, as for a log-only code).
{
	const lines = read(join(DOCS, 'ROADMAP.md')).split('\n');
	const start = lines.findIndex((l) => /^## Polish backlog/.test(l));
	const POLISH_RE = new RegExp(`^- (?:\\d+\\.x (?:playtest \\d{4}-\\d{2}-\\d{2} )?)(${CODE_RE})\\b(.*)$`);
	for (let i = start + 1; start >= 0 && i < lines.length && !/^## /.test(lines[i]); i++) {
		const m = POLISH_RE.exec(lines[i]);
		if (!m || m[1].length < 4 || entries.has(m[1])) continue;
		let rest = m[2], qual = '';
		const open = rest.indexOf('(');
		if (open >= 0 && /^[\w\s-]*$/.test(rest.slice(0, open))) { // skip the "(<name>, [shot](...): ...)" source note
			qual = rest.slice(0, open).trim();
			let depth = 0, j = open;
			for (; j < rest.length; j++) {
				if (rest[j] === '(') depth++;
				else if (rest[j] === ')' && --depth === 0) break;
			}
			rest = rest.slice(j + 1);
		}
		rest = rest.replace(/^\s*[.:]\s*/, '');
		add(m[1], { num: '', title: cleanTitle(qual ? `${qual}: ${rest}` : rest), status: 'polish', fromLog: true, links: [{ text: 'ROADMAP polish', href: 'ROADMAP.md' }] });
	}
}

// 5: hand-kept extras.
const extraPath = join(DOCS, 'glossary-extra.tsv');
if (existsSync(extraPath)) {
	for (const [n, line] of read(extraPath).split('\n').entries()) {
		if (!line.trim() || line.startsWith('#')) continue;
		const [code, meaning = '', href = '', status = ''] = line.split('\t').map((s) => s.trim());
		const old = entries.get(code);
		if (old && old.fromLog) { // a log-only code: the extra row gives the meaning, the log link stays
			old.title = cleanTitle(meaning) || old.title;
			if (status) old.status = status;
			if (href) old.links.push({ text: href.replace(/\.md$/, '').replace(/^.*\//, ''), href });
			continue;
		}
		if (old) {
			process.stderr.write(`glossary: docs/glossary-extra.tsv:${n + 1}: ${code} is already defined (${entries.get(code).links[0].href}); extra row ignored\n`);
			continue;
		}
		const links = href ? [{ text: href.replace(/\.md$/, '').replace(/^.*\//, ''), href }] : [];
		add(code, { num: '', title: cleanTitle(meaning), status: status || '-', links });
	}
}

// Links in the table are SilverBullet wiki links, space-absolute (repo root) and without .md:
// [[docs/log/x|log]]. SilverBullet opens a markdown link inside a table in a new tab (it renders the table as
// HTML with target=_blank) but a wiki link in the same tab; docs/SILVERBULLET.md "Links" has the details.
// The alias pipe stays unescaped: SilverBullet needs it raw (`\|` breaks the link); GitHub shows wiki links
// as plain text anyway. Paths SilverBullet doesn't serve (hidden dirs like .claude/) stay markdown links.
function cellLink(l) {
	const path = posix.normalize(posix.join('docs', l.href));
	if (path.startsWith('..') || path.split('/').some((seg) => seg.startsWith('.'))) return `[${l.text}](${l.href})`;
	return `[[${path.replace(/\.md$/, '')}|${l.text}]]`;
}

const key = (c) => c.toLowerCase();
const rows = [...entries.values()].sort((a, b) => (key(a.code) < key(b.code) ? -1 : key(a.code) > key(b.code) ? 1 : a.code < b.code ? -1 : a.code > b.code ? 1 : 0));
const out = [
	'# Glossary',
	'',
	'Item codes (PARREADS, MAINSTUB, TPHOLD, ...) and run terms, one row each. **Generated by `scripts/glossary.sh`; do not edit by hand.** Links in the table are SilverBullet wiki links (same tab there; plain text on GitHub). Codes come from the bold item names in [ROADMAP.md](ROADMAP.md), the archived phases in `archive/`, the plans in `plans/` and the work logs in `log/`; other codes and terms come from the hand-kept `glossary-extra.tsv` (add a row there, then rerun the script). Status: open or done for ROADMAP items, done for codes known only from a work log, - otherwise.',
	'',
	'| Code | Item | Meaning | Status | Defined in |',
	'|---|---|---|---|---|',
	...rows.map((r) => `| ${r.code} | ${r.num} | ${r.title} | ${r.status} | ${r.links.map(cellLink).join(', ')} |`),
	'',
];
process.stdout.write(out.join('\n'));
