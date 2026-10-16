import { parseScene } from './ir';
import type { Scene } from './ir';

/** Where scenes come from: a single `?ir=` URL, a CLI batch `gen/<style>/`, or the bundled fixtures. */
export interface Source {
  kind: 'ir' | 'fixture' | 'batch' | 'fixtures';
  /** Seeds the scrubber walks; empty for a single scene. */
  seeds: number[];
  load(seed: number | null): Promise<Scene>;
}

export type FetchFn = (url: string) => Promise<{ ok: boolean; status: number; text(): Promise<string> }>;

export interface SourceRequest { ir: string | null; fixture: string | null; style: string | null }

async function getJson(fetchFn: FetchFn, url: string): Promise<unknown | undefined> {
  try {
    const res = await fetchFn(url);
    if (!res.ok) return undefined;
    return JSON.parse(await res.text()); // a dev server answering 200 with index.html fails here, as intended
  } catch {
    return undefined;
  }
}

export function batchDir(style: string): string {
  return `gen/${style}`;
}

/** Fixture names (`../fixtures/x.json` keys) in scrub order: sample-house first, then alphabetical. */
export function fixtureNames(fixtures: Record<string, string>): string[] {
  // Only fb-scene files: viewer/fixtures also holds box5.json, a bare IR for the CLI test.
  const names = Object.keys(fixtures).filter((k) => fixtures[k]!.includes('"fb-scene/')).map((k) => k.replace(/^.*\//, '').replace(/\.json$/, '')).sort();
  const i = names.indexOf('sample-house');
  if (i > 0) { names.splice(i, 1); names.unshift('sample-house'); }
  return names;
}

function fixtureScene(fixtures: Record<string, string>, name: string): Scene {
  const key = Object.keys(fixtures).find((k) => k.endsWith(`/${name}.json`));
  if (key === undefined) throw new Error(`no fixture "${name}"`);
  return parseScene(JSON.parse(fixtures[key]!));
}

/**
 * `?ir=` wins, then `?fixture=`, then `gen/<style>/index.json` (`{"seeds": [...]}`, as `cli batch` writes it) with
 * `seed-N.json` beside it, else the bundled fixtures with the seed as an index into them.
 */
export async function resolveSource(
  req: SourceRequest, fetchFn: FetchFn, fixtures: Record<string, string>,
): Promise<Source> {
  const { ir, fixture } = req;
  if (ir) {
    return {
      kind: 'ir', seeds: [],
      async load() {
        const res = await fetchFn(ir);
        if (!res.ok) throw new Error(`fetch ${ir}: ${res.status}`);
        return parseScene(JSON.parse(await res.text()));
      },
    };
  }
  if (fixture) return { kind: 'fixture', seeds: [], load: async () => fixtureScene(fixtures, fixture) };

  const dir = batchDir(req.style ?? 'plain');
  const index = await getJson(fetchFn, `${dir}/index.json`);
  const seeds = (index as { seeds?: unknown } | undefined)?.seeds;
  if (Array.isArray(seeds) && seeds.length > 0 && seeds.every((n) => Number.isInteger(n))) {
    return {
      kind: 'batch', seeds: seeds as number[],
      async load(seed) {
        const url = `${dir}/seed-${seed ?? (seeds as number[])[0]}.json`;
        const json = await getJson(fetchFn, url);
        if (json === undefined) throw new Error(`cannot load ${url}`);
        return parseScene(json);
      },
    };
  }
  const names = fixtureNames(fixtures);
  return {
    kind: 'fixtures', seeds: names.map((_, i) => i),
    async load(seed) {
      const name = names[seed ?? 0];
      if (name === undefined) throw new Error(`no fixture #${seed}`);
      return fixtureScene(fixtures, name);
    },
  };
}
