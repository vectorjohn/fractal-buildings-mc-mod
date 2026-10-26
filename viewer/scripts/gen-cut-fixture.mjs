// Writes fixtures/shoplow/cut-fixture.json: a hand-built dug-in shop (SHOPLOW S1v). Run: node scripts/gen-cut-fixture.mjs
// The terrain rises 4 blocks over the lot (street at y 64 to the north, earth to 68 at the back); the slab sits at the street
// level (oy 64), so the footprint is cut. A 1-wide areaway (AIR down to the slab level, PAVING floor) rings the sides and back.
// This is the IR the viewer reads for S1/S2: building siteBoxes with role air (cut) and paving (areaway floor), origin.y = slab.
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const N = 20, OY = 64, OX = 6, OZ = 6, W = 8, D = 8, H = 5;
const floorY = [];
for (let z = 0; z < N; z++) for (let x = 0; x < N; x++) floorY.push(z < 5 ? 64 : Math.min(68, 64 + (z - 5)));
const box = (min, size, role, layer = 'shell', storey = 0) => ({ min, size, role, layer, storey });
const boxes = [
  box([0, 0, 0], [W, 1, D], 'floor'),
  box([0, 1, 0], [W, 3, 1], 'wall'), box([0, 1, D - 1], [W, 3, 1], 'wall'),
  box([0, 1, 1], [1, 3, D - 2], 'wall'), box([W - 1, 1, 1], [1, 3, D - 2], 'wall'),
  box([0, 4, 0], [W, 1, D], 'floor', 'roof', -1),
  box([0, 5, 0], [W, 1, D], 'roof', 'roof', -1),
  box([3, 1, 0], [1, 2, 1], 'door'),
  box([3, 2, D - 1], [1, 1, 1], 'window'), box([5, 2, D - 1], [1, 1, 1], 'window'),
  box([0, 2, 3], [1, 1, 1], 'window'), box([W - 1, 2, 3], [1, 1, 1], 'window'),
];
// the areaway: air over the earth beside and behind the shop, then paving at the slab level over the whole ring
const siteBoxes = [
  box([-1, 0, -1], [1, H, D + 2], 'air', 'ground', -1), box([W, 0, -1], [1, H, D + 2], 'air', 'ground', -1),
  box([0, 0, D], [W, H, 1], 'air', 'ground', -1),
  box([-1, 0, -1], [1, 1, D + 2], 'paving', 'ground', -1), box([W, 0, -1], [1, 1, D + 2], 'paving', 'ground', -1),
  box([0, 0, D], [W, 1, 1], 'paving', 'ground', -1), box([0, 0, -1], [W, 1, 1], 'paving', 'ground', -1),
];
const scene = {
  format: 'fb-scene/1', genVersion: 1, seed: '1',
  terrain: { x0: 0, z0: 0, nx: N, nz: N, floorY },
  lots: [{ id: 'lot0', x: 5, z: 5, w: 10, d: 10, front: 'north' }],
  buildings: [{
    id: 'b0', lots: ['lot0'], origin: [OX, OY, OZ], rotation: 0,
    shell: {
      spec: { genVersion: 1, specSeed: '1', style: 'plain', type: 'shop', width: W, depth: D, storeys: 1, roof: 'flat' },
      specHash: '0000000000000000', bounds: { min: [0, 0, 0], size: [W, 6, D] },
      storeys: [{ index: 0, y: 0, height: 4 }],
      volumes: [{ id: 'v0', min: [0, 0, 0], size: [W, 5, D], parent: null }],
      rooms: [{ id: 'r0', storey: 0, volume: 'v0', use: 'shop', parts: [{ min: [1, 1, 1], size: [W - 2, 3, D - 2] }] }],
      openings: [
        { id: 'o0', kind: 'door', min: [3, 1, 0], size: [1, 2, 1], facing: 'north', from: 'r0', to: 'outside' },
        { id: 'o1', kind: 'window', min: [3, 2, D - 1], size: [1, 1, 1], facing: 'south', from: 'r0', to: 'outside' },
        { id: 'o2', kind: 'window', min: [5, 2, D - 1], size: [1, 1, 1], facing: 'south', from: 'r0', to: 'outside' },
        { id: 'o3', kind: 'window', min: [0, 2, 3], size: [1, 1, 1], facing: 'west', from: 'r0', to: 'outside' },
        { id: 'o4', kind: 'window', min: [W - 1, 2, 3], size: [1, 1, 1], facing: 'east', from: 'r0', to: 'outside' },
      ],
      roofs: [{ form: 'flat', volume: 'v0', ridgeAxis: null, baseY: 5, rise: 0, run: 0 }],
      boxes, hooks: [],
    },
    siteBoxes,
  }],
  violations: [],
};
writeFileSync(fileURLToPath(new URL('../fixtures/shoplow/cut-fixture.json', import.meta.url)), JSON.stringify(scene) + '\n');
