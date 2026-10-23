import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { cellHash } from './styles';

// The same rows are read by planner-core's VariantTest, so the game picks the block the viewer shows.
describe('cellHash fixture shared with the Java port', () => {
  it('matches every row of planner-core/src/test/resources/raster/cellhash.csv', () => {
    const rows = readFileSync(new URL('../../planner-core/src/test/resources/raster/cellhash.csv', import.meta.url), 'utf8')
      .trim().split('\n').slice(1).map((l) => l.split(',').map(Number));
    expect(rows.length).toBe(50);
    for (const [x, y, z, h] of rows) expect(cellHash(x!, y!, z!)).toBe(h);
  });
});
