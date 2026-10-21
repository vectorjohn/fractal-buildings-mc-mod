// Plans the cities of city-presets/*.json (settlement, every district, and the chosen district scenes) into out/city/<name>/.
// Usage (inside the dev shell, via `npm run city`): node scripts/gen-city.mjs [preset names...]
// No names = all presets. Node strips the TypeScript types of the shared parser itself (Node >= 22.18).
// The CLI exits 1 when the plan has violations (the city-level roads-connected rule is known to flag hub gaps); the data is still
// written, so a non-zero exit is reported here and does not stop the run unless the CLI crashed (exit 2, or no settlement.json).
import { spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { cityArgs, parseCityPreset } from '../src/cityPresets.ts';

const viewer = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const root = resolve(viewer, '..');
const out = join(viewer, 'out', 'city');
const cli = join(root, 'cli', 'build', 'install', 'fractal-cli', 'bin', 'fractal-cli');
const dir = join(viewer, 'city-presets');

const all = readdirSync(dir).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort();
const wanted = process.argv.slice(2).length > 0 ? process.argv.slice(2) : all;
for (const n of wanted) if (!all.includes(n)) { console.error(`no city preset "${n}" (have ${all.join(', ')})`); process.exit(2); }

mkdirSync(out, { recursive: true });
let failed = 0;
for (const name of wanted) {
  const p = parseCityPreset(name, JSON.parse(readFileSync(join(dir, `${name}.json`), 'utf8')));
  const target = join(out, name);
  rmSync(target, { recursive: true, force: true });
  const r = spawnSync(cli, [...cityArgs(p), '--out-dir', target], { cwd: root, stdio: ['ignore', 'inherit', 'inherit'] });
  if (!existsSync(join(target, 'settlement.json'))) { console.error(`${name}: the CLI wrote no settlement (exit ${r.status})`); failed++; continue; }
  console.error(`${name}: exit ${r.status} -> ${target}`);
}
process.exit(failed > 0 ? 1 : 0);
