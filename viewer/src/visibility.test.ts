import { describe, expect, it } from 'vitest';
import * as THREE from 'three';
import { applyLayers, applyStoreyLimit, topStorey } from './visibility';
import type { Voxel } from './voxels';

function fixture(): THREE.Group {
  const root = new THREE.Group();
  for (const n of ['terrain', 'shell', 'roof']) { const g = new THREE.Group(); g.name = n; root.add(g); }
  const vs = [-1, 0, 1, 2].map((storey, i) => ({ x: i, y: storey, z: 0, role: 'wall', layer: 'shell', storey, building: 0 }) as Voxel);
  const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial(), vs.length);
  vs.forEach((v, i) => mesh.setMatrixAt(i, new THREE.Matrix4().makeTranslation(v.x + 0.5, v.y + 0.5, v.z + 0.5)));
  mesh.userData['voxels'] = vs;
  root.getObjectByName('shell')!.add(mesh);
  return root;
}
const scaleAt = (root: THREE.Object3D, i: number): number => {
  const mesh = root.getObjectByName('shell')!.children[0] as THREE.InstancedMesh;
  const m = new THREE.Matrix4(); mesh.getMatrixAt(i, m);
  return new THREE.Vector3().setFromMatrixScale(m).x;
};

describe('visibility', () => {
  it('applyLayers sets visible by group name', () => {
    const r = fixture();
    applyLayers(r, new Set(['shell']));
    expect(r.children.map((c) => c.visible)).toEqual([false, true, false]);
    applyLayers(r, new Set(['terrain', 'shell', 'roof']));
    expect(r.children.every((c) => c.visible)).toBe(true);
  });
  it('storey limit hides above N and foundation, and restores', () => {
    const r = fixture();
    expect(topStorey(r)).toBe(2);
    applyStoreyLimit(r, 1, 2);
    expect([0, 1, 2, 3].map((i) => scaleAt(r, i))).toEqual([0, 1, 1, 0]);
    applyStoreyLimit(r, 0, 2);
    expect([0, 1, 2, 3].map((i) => scaleAt(r, i))).toEqual([0, 1, 0, 0]);
    applyStoreyLimit(r, null, 2);
    expect([0, 1, 2, 3].map((i) => scaleAt(r, i))).toEqual([1, 1, 1, 1]);
  });
});
