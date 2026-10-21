import { readFileSync } from 'node:fs';
import * as THREE from 'three';
import { describe, expect, it } from 'vitest';
import { parseScene } from './ir';
import { buildSceneObject } from './render';

const sample = () => parseScene(JSON.parse(readFileSync(new URL('../fixtures/settlement/sample/scenes/d0_0.json', import.meta.url), 'utf8')));

/** Triangles under a named group (instanced: 12 per instance). */
function tris(root: THREE.Object3D, name: string): number {
  let n = 0;
  root.getObjectByName(name)!.traverse((o) => {
    if (o instanceof THREE.InstancedMesh) n += o.count * 12;
    else if (o instanceof THREE.Mesh) n += (o.geometry.index?.count ?? 0) / 3;
  });
  return n;
}

describe('district scene objects', () => {
  for (const mesh of ['instanced', 'faces'] as const) {
    it(`${mesh}: ground boxes and water are drawn, and drop out with the scene's boxes`, () => {
      const s = sample();
      const root = buildSceneObject(s, { colour: 'role', mesh });
      expect(tris(root, 'ground')).toBeGreaterThan(500);
      expect(tris(root, 'water')).toBeGreaterThan(0);
      expect(tris(root, 'terrain')).toBeGreaterThan(0);
      const none = buildSceneObject({ ...s, ground: [] }, { colour: 'role', mesh });
      expect(tris(none, 'ground')).toBe(0);
    });
  }
  it('exterior drops the interior layer', () => {
    const s = sample();
    const full = tris(buildSceneObject(s, { colour: 'role', mesh: 'faces' }), 'interior');
    expect(full).toBeGreaterThan(0);
    expect(tris(buildSceneObject(s, { colour: 'role', mesh: 'faces', exterior: true }), 'interior')).toBe(0);
  });
});
