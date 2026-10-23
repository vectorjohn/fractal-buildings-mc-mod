import { describe, expect, it } from 'vitest';
import { gzipSync } from 'node:zlib';
import { readFileSync } from 'node:fs';
import { decodeBytes, groupOf, heatColour, isGzip, parseClaim, parseDump, terrainColour } from './regionmap';

const fixture = (n: string): Buffer => readFileSync(new URL(`../fixtures/region/${n}`, import.meta.url));

describe('gzip detection', () => {
  it('goes by magic, and decodes both forms to the same text', async () => {
    const text = '{"a":1}';
    const gz = new Uint8Array(gzipSync(text));
    expect(isGzip(gz)).toBe(true);
    expect(isGzip(new TextEncoder().encode(text))).toBe(false);
    expect(await decodeBytes(gz)).toBe(text);
    expect(await decodeBytes(new TextEncoder().encode(text))).toBe(text);
  });
});

describe('parseDump', () => {
  it('reads the committed world region dump', async () => {
    const d = parseDump(await decodeBytes(new Uint8Array(fixture('world-12345.json.gz'))));
    expect(d.seed).toBe(12345);
    expect(d.grid).toMatchObject({ step: 64, nx: 384, nz: 384 });
    expect(d.biome).toHaveLength(384 * 384);
    const cells = d.layers[0]!.cells;
    expect(cells.length).toBeGreaterThan(100);
    expect(cells.some((c) => c.city)).toBe(true);
    expect(cells.some((c) => c.waterDist !== undefined)).toBe(true); // town and up (R2)
    expect(cells.every((c) => c.waterDist === undefined || (c.waterDist >= 0 && c.waterDist <= 768))).toBe(true);
    expect(cells.some((c) => c.claimedBy !== undefined)).toBe(true); // MC1 joins
  });

  it('picks up waterDist and claimedBy when present, rejects other formats', () => {
    const base = { format: 'fb-region-dump/1', seed: 1, grid: { x0: 0, z0: 0, step: 64, nx: 1, nz: 1 }, palette: ['plains'], biome: [0], surface: [64], water: [0] };
    const cell = { cx: 0, cz: 0, siteX: 1, siteZ: 2, city: false, reason: 'roll', tier: 'village', centreX: 1, centreZ: 2, radius: 50 };
    const d = parseDump(JSON.stringify({ ...base, layers: [{ cellSize: 1024, policy: 'grow', cells: [{ ...cell, waterDist: 300, claimedBy: [1, -2] }] }] }));
    expect(d.layers[0]!.cells[0]).toMatchObject({ waterDist: 300, claimedBy: [1, -2] });
    expect(parseDump(JSON.stringify(base)).layers).toEqual([]);
    expect(() => parseDump(JSON.stringify({ ...base, format: 'x' }))).toThrow(/fb-region-dump/);
  });

  it('parses the claim shapes MC1 might write', () => {
    expect(parseClaim([3, -4])).toEqual([3, -4]);
    expect(parseClaim({ cx: 3, cz: -4 })).toEqual([3, -4]);
    expect(parseClaim('3,-4')).toEqual([3, -4]);
    expect(parseClaim(null)).toBeUndefined();
    expect(parseClaim(undefined)).toBeUndefined();
  });
});

describe('colours', () => {
  it('groups biomes and shades water by depth', () => {
    expect(groupOf('deep_ocean')).toBe('water');
    expect(groupOf('jagged_peaks')).toBe('mountain');
    expect(groupOf('plains')).toBe('temperate');
    const shallow = terrainColour('temperate', 64, 3), deep = terrainColour('temperate', 64, 50);
    expect(deep[2]).toBeLessThan(shallow[2]);
    expect(terrainColour('desert', 64, 0)[0]).toBeGreaterThan(terrainColour('desert', 64, 0)[2]);
  });
  it('heat is hot near water and cold far away', () => {
    expect(heatColour(0)).toContain('hsla(0,');
    expect(heatColour(5000)).toContain('hsla(240,');
  });
});

describe('road layer (6.10)', () => {
  it('parses the roads object and the port tag of the committed roads dump', async () => {
    const d = parseDump(await decodeBytes(new Uint8Array(fixture('roads-12345.json.gz'))));
    const R = d.layers[0]!.roads!;
    expect(R.lgap).toBe(1536);
    expect(R.nodes.length).toBeGreaterThan(100);
    expect(R.nodes.filter((n) => n.port).length).toBeGreaterThan(10);
    expect(R.edges.length).toBeGreaterThan(40);
    for (const e of R.edges) {
      expect(e.pts.length % 2).toBe(0);
      expect(e.pts.length).toBeGreaterThanOrEqual(4);
      expect(e.cross.length % 2).toBe(0);
      for (let k = 0; k < e.cross.length; k += 2) expect(e.cross[k]! < e.cross[k + 1]! && e.cross[k + 1]! < e.pts.length / 2).toBe(true);
    }
    expect(R.meet.length).toBeGreaterThan(3);
    expect(R.dropped.length).toBeGreaterThan(10);
    expect(d.layers[0]!.cells.filter((c) => c.port).length).toBe(R.nodes.filter((n) => n.port).length);
  });

  it('a dump without roads parses without the layer, and a bad edge is an error', async () => {
    const d = parseDump(await decodeBytes(new Uint8Array(fixture('world-12345.json.gz'))));
    expect(d.layers[0]!.roads).toBeUndefined();
    const raw = JSON.parse(await decodeBytes(new Uint8Array(fixture('world-12345.json.gz')))) as { layers: Record<string, unknown>[] };
    raw.layers[0]!['roads'] = { edges: [{ a: [0, 0], b: [1, 1], w: 3, pa: null, pb: null, pts: [1, 2, 3], cross: [] }] };
    expect(() => parseDump(JSON.stringify(raw))).toThrow(/pairs/);
  });
});
