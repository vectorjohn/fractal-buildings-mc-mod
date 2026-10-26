import { ROLE_COLOURS } from './palettes';
import type { Rgb } from './palettes';

/** One style file as the viewer needs it: `colours[role]` is a list of hex colours (the weathering gradient). */
export interface StyleColours { id: string; colours: Record<string, Rgb[]> }

/** Style files bundled at build time (`planner-core/.../styles/*.json`, raw text; Vite `server.fs.allow` opens `..`). */
const BUNDLED = import.meta.glob('../../planner-core/src/main/resources/styles/*.json', {
  query: '?raw', import: 'default', eager: true,
}) as Record<string, string>;

function hex(s: unknown, where: string): Rgb {
  if (typeof s !== 'string' || !/^#[0-9a-fA-F]{6}$/.test(s)) throw new Error(`style ${where}: bad colour ${JSON.stringify(s)}`);
  return parseInt(s.slice(1), 16);
}

/** Parse style files (file path -> JSON text) and resolve `extends`: a child's role lists replace the parent's. */
export function parseStyles(raw: Record<string, string>): Map<string, StyleColours> {
  type Doc = { id: string; extends?: string; colours?: Record<string, unknown> };
  const docs = new Map<string, Doc>();
  for (const path of Object.keys(raw).sort()) {
    const d = JSON.parse(raw[path]!) as Doc;
    if ((d as { format?: string }).format === 'fb-biome-fallback/1') continue; // the biome map beside the style files, not a style
    if ((d as { format?: string }).format === 'fb-style-variants/1') continue; // the variant rules beside the style files, not a style
    if (typeof d.id !== 'string') throw new Error(`style ${path}: no id`);
    docs.set(d.id, d);
  }
  const out = new Map<string, StyleColours>();
  const resolve = (id: string, seen: string[]): Record<string, Rgb[]> => {
    const d = docs.get(id);
    if (!d) throw new Error(`style ${id}: unknown (extends chain ${seen.join(' > ')})`);
    if (seen.includes(id)) throw new Error(`style extends cycle: ${[...seen, id].join(' > ')}`);
    const base = d.extends ? resolve(d.extends, [...seen, id]) : {};
    const own: Record<string, Rgb[]> = {};
    for (const [role, list] of Object.entries(d.colours ?? {})) {
      if (!Array.isArray(list) || list.length === 0) throw new Error(`style ${id}.colours.${role}: need a non-empty list`);
      own[role] = list.map((c) => hex(c, `${id}.colours.${role}`));
    }
    return { ...base, ...own };
  };
  for (const id of [...docs.keys()].sort()) out.set(id, { id, colours: resolve(id, []) });
  return out;
}

let bundled: Map<string, StyleColours> | null = null;
export function bundledStyles(): Map<string, StyleColours> {
  return (bundled ??= parseStyles(BUNDLED));
}

/** Ids of the bundled styles, sorted. */
export function styleIds(): string[] { return [...bundledStyles().keys()]; }

/** Integer hash of a voxel position (`Math.imul`, deterministic, no dependence on iteration order). */
export function cellHash(x: number, y: number, z: number): number {
  let h = Math.imul(x | 0, 0x1b873593) ^ Math.imul(y | 0, 0x85ebca6b) ^ Math.imul(z | 0, 0xc2b2ae35);
  h = Math.imul(h ^ (h >>> 16), 0x7feb352d);
  h = Math.imul(h ^ (h >>> 15), 0x846ca68b);
  return (h ^ (h >>> 16)) >>> 0;
}

/**
 * Colour of one voxel in a style: `colours[role][hash(x, y, z) % n]`. An unknown style (or a role the style lacks)
 * falls back to `plain`, then to the debug role colour.
 */
export function styleColour(
  style: string, role: string, x: number, y: number, z: number, styles: Map<string, StyleColours> = bundledStyles(),
): Rgb {
  const list = (styles.get(style) ?? styles.get('plain'))?.colours[role] ?? styles.get('plain')?.colours[role];
  if (!list) return ROLE_COLOURS[role] ?? 0xff00ff;
  return list[cellHash(x, y, z) % list.length]!;
}
