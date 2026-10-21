// Adds the featured district scene of each Phase 5 gate-pack city (e2e/gate-pack-featured.json) to out/city/<name>/, re-running the
// preset's `fractal-cli city` with that district added to its scene list (the plan bytes do not depend on which scenes are built).
// Usage (inside the dev shell, in viewer/, after `npm run city -- <names>`): node scripts/gate-pack-scenes.mjs [names...]
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cityArgs, parseCityPreset } from '../src/cityPresets.ts';

const viewer = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const root = resolve(viewer, '..');
const cli = join(root, 'cli', 'build', 'install', 'fractal-cli', 'bin', 'fractal-cli');
const featured = JSON.parse(readFileSync(join(viewer, 'e2e', 'gate-pack-featured.json'), 'utf8'));
const wanted = process.argv.slice(2).length > 0 ? process.argv.slice(2) : Object.keys(featured);
let failed = 0;
for (const name of wanted) {
  const id = featured[name];
  const target = join(viewer, 'out', 'city', name);
  if (id === undefined || !existsSync(join(target, 'settlement.json'))) { console.error(`${name}: no featured district or no data (run npm run city -- ${name})`); failed++; continue; }
  const have = existsSync(join(target, 'scenes')) ? readdirSync(join(target, 'scenes')).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)) : [];
  if (have.includes(id)) { console.error(`${name}: ${id} already there`); continue; }
  const p = parseCityPreset(name, JSON.parse(readFileSync(join(viewer, 'city-presets', `${name}.json`), 'utf8')));
  const a = cityArgs(p);
  const i = a.indexOf('--scenes');
  const list = [...have, id].join(',');
  if (i >= 0) a[i + 1] = list; else a.push('--scenes', list);
  const r = spawnSync(cli, [...a, '--out-dir', target], { cwd: root, stdio: ['ignore', 'ignore', 'inherit'] });
  if (!existsSync(join(target, 'scenes', `${id}.json`))) { console.error(`${name}: no scene ${id} (exit ${r.status})`); failed++; } else console.error(`${name}: added ${id}`);
}
process.exit(failed > 0 ? 1 : 0);
