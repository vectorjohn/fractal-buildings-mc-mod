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
    expect(cells.every((c) => c.waterDist === undefined)).toBe(true); // optional, R2 not in the dump
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
