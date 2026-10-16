import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fixtureNames, resolveSource } from './source';
import type { FetchFn } from './source';

const house = readFileSync(new URL('../fixtures/sample-house.json', import.meta.url), 'utf8');
const fixtures = {
  '../fixtures/zzz-copy.json': house,
  '../fixtures/sample-house.json': house,
};
const req = { ir: null, fixture: null, style: null };

function fake(files: Record<string, string>, log: string[] = []): FetchFn {
  return async (url) => {
    log.push(url);
    const body = files[url];
    return { ok: body !== undefined, status: body === undefined ? 404 : 200, text: async () => body ?? '' };
  };
}

describe('resolveSource', () => {
  it('uses a CLI batch when gen/<style>/index.json exists', async () => {
    const log: string[] = [];
    const src = await resolveSource({ ...req, style: 'medieval' },
      fake({ 'gen/medieval/index.json': '{"style":"medieval","seeds":[5,6]}', 'gen/medieval/seed-6.json': house }, log), fixtures);
    expect(src.kind).toBe('batch');
    expect(src.seeds).toEqual([5, 6]);
    expect((await src.load(6)).buildings.length).toBeGreaterThan(0);
    expect(log).toContain('gen/medieval/seed-6.json');
    await expect(src.load(5)).rejects.toThrow(/seed-5/);
  });

  it('defaults the batch style to plain', async () => {
    const log: string[] = [];
    await resolveSource(req, fake({}, log), fixtures);
    expect(log).toEqual(['gen/plain/index.json']);
  });

  it('falls back to the fixtures, sample-house first, when there is no batch', async () => {
    const src = await resolveSource(req, fake({}), fixtures);
    expect(src.kind).toBe('fixtures');
    expect(src.seeds).toEqual([0, 1]);
    expect(fixtureNames({ ...fixtures, '../fixtures/box5.json': '{}' })).toEqual(['sample-house', 'zzz-copy']);
    expect(fixtureNames(fixtures)).toEqual(['sample-house', 'zzz-copy']);
    expect((await src.load(0)).buildings.length).toBeGreaterThan(0);
    await expect(src.load(7)).rejects.toThrow();
  });

  it('treats a non-JSON 200 (SPA fallback) and a bad index as no batch', async () => {
    expect((await resolveSource(req, fake({ 'gen/plain/index.json': '<html>' }), fixtures)).kind).toBe('fixtures');
    expect((await resolveSource(req, fake({ 'gen/plain/index.json': '{"seeds":[]}' }), fixtures)).kind).toBe('fixtures');
  });

  it('?ir= and ?fixture= win and have no seeds', async () => {
    const log: string[] = [];
    const ir = await resolveSource({ ...req, ir: 'x/y.json' }, fake({ 'x/y.json': house }, log), fixtures);
    expect(ir.seeds).toEqual([]);
    await ir.load(null);
    expect(log).toEqual(['x/y.json']);
    const fx = await resolveSource({ ...req, fixture: 'zzz-copy' }, fake({}, log), fixtures);
    expect(fx.kind).toBe('fixture');
    await fx.load(null);
    await expect(resolveSource({ ...req, fixture: 'nope' }, fake({}), fixtures).then((s) => s.load(null))).rejects.toThrow(/nope/);
  });
});
