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

describe('mixed-style districts', () => {
  /** Scene with building 1 re-styled: a second palette in the same district. */
  const mixed = (second: string) => {
    const s = sample();
    expect(s.buildings.length).toBeGreaterThan(1);
    const b = s.buildings.map((x, i) => i === 0 ? { ...x, shell: { ...x.shell, spec: { ...x.shell.spec, style: 'plains' } } }
      : { ...x, shell: { ...x.shell, spec: { ...x.shell.spec, style: second } } });
    return { ...s, buildings: b };
  };
  /** Distinct colours (0xRRGGBB) of the layer's vertex colours or instance colours. */
  function colours(root: THREE.Object3D, name: string): Set<number> {
    const out = new Set<number>();
    const c = new THREE.Color();
    root.getObjectByName(name)!.traverse((o) => {
      if (o instanceof THREE.InstancedMesh) { for (let i = 0; i < o.count; i++) { o.getColorAt(i, c); out.add(c.getHex()); } }
      else if (o instanceof THREE.Mesh) {
        const a = o.geometry.getAttribute('color');
        for (let i = 0; i < a.count; i++) out.add(c.setRGB(a.getX(i), a.getY(i), a.getZ(i)).getHex());
      }
    });
    return out;
  }
  for (const mesh of ['instanced', 'faces'] as const) {
    it(`${mesh}: buildings of two styles take two palettes, terrain follows`, () => {
      const same = buildSceneObject(mixed('plains'), { colour: 'palette', mesh });
      const two = buildSceneObject(mixed('desert'), { colour: 'palette', mesh });
      const a = colours(same, 'shell'), b = colours(two, 'shell');
      expect(b.size).toBeGreaterThan(a.size); // desert walls add colours the all-plains district lacks
      const all = buildSceneObject(mixed('plains'), { colour: 'palette', mesh });
      expect(colours(all, 'shell').size).toBe(a.size);
      expect(colours(two, 'terrain').size).toBeGreaterThan(colours(same, 'terrain').size); // desert sand tint appears
    });
    it(`${mesh}: an explicit style overrides every building`, () => {
      const forced = buildSceneObject(mixed('desert'), { colour: 'palette', mesh, style: 'plains' });
      const same = buildSceneObject(mixed('plains'), { colour: 'palette', mesh, style: 'plains' });
      expect(colours(forced, 'shell')).toEqual(colours(same, 'shell'));
    });
  }
});
