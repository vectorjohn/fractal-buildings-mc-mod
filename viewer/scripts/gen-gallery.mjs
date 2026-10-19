// Generates the gallery data for the presets in gallery-presets/*.json into gallery-data/gen/<name>/.
// Usage (inside the dev shell, via gen-gallery.sh): node scripts/gen-gallery.mjs [preset names...]
// No names = all presets (the gen/ directory is wiped first); with names only those directories are replaced.
// Node strips the TypeScript types of the shared parser itself (Node >= 22.18).
import { execFileSync } from 'node:child_process';
import { existsSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { batchArgs, parsePreset } from '../src/galleryPresets.ts';

const viewer = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const root = resolve(viewer, '..');
const out = join(viewer, 'gallery-data', 'gen');
const cli = join(root, 'cli', 'build', 'install', 'fractal-cli', 'bin', 'fractal-cli');

const dir = join(viewer, 'gallery-presets');
const all = readdirSync(dir).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5)).sort();
const wanted = process.argv.slice(2).length > 0 ? process.argv.slice(2) : all;
for (const n of wanted) if (!all.includes(n)) { console.error(`no gallery preset "${n}" (have ${all.join(', ')})`); process.exit(2); }
if (process.argv.slice(2).length === 0) rmSync(out, { recursive: true, force: true });

for (const name of wanted) {
  const p = parsePreset(name, JSON.parse(readFileSync(join(dir, `${name}.json`), 'utf8')));
  const target = join(out, name);
  rmSync(target, { recursive: true, force: true });
  if (p.kind === 'cli') {
    execFileSync(cli, [...batchArgs(p), '--out-dir', target], { stdio: 'inherit' });
  } else {
    execFileSync(join(root, 'gradlew'), ['-q', ':planner-core:test', '--tests', '*FixtureDumpTest*', '--rerun',
      `-Dfb.dumpFixtures=${target}`, `-Dfb.dumpStyle=${p.style}`], { cwd: root, stdio: 'inherit' });
    if (!existsSync(join(target, 'index.json'))) throw new Error(`fixture dump wrote nothing to ${target}`);
  }
}
