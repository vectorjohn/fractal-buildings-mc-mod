import * as THREE from 'three';
import type { Voxel } from './voxels';
import { storeyVisible } from './state';

/** Show or hide whole layer groups by name (`visible` only; meshes are kept). */
export function applyLayers(root: THREE.Object3D, layers: ReadonlySet<string>): void {
  for (const child of root.children) child.visible = layers.has(child.name);
}

/** Highest storey among the scene's voxels (0 if none). */
export function topStorey(root: THREE.Object3D): number {
  let top = 0;
  root.traverse((o) => {
    const vs = o.userData['voxels'] as Voxel[] | undefined;
    if (vs) for (const v of vs) top = Math.max(top, v.storey);
  });
  return top;
}

const HIDDEN = new THREE.Matrix4().makeScale(0, 0, 0);

/** Hide voxel instances above `max` by collapsing their matrix; restores them when the limit rises. */
export function applyStoreyLimit(root: THREE.Object3D, max: number | null, top: number): void {
  const m = new THREE.Matrix4();
  root.traverse((o) => {
    const vs = o.userData['voxels'] as Voxel[] | undefined;
    if (!vs || !(o instanceof THREE.InstancedMesh)) return;
    const k = (o.userData['scale'] as number | undefined) ?? 1;
    vs.forEach((v, i) => {
      o.setMatrixAt(i, storeyVisible(v.storey, max, top) ? m.makeTranslation(v.x + 0.5, v.y + 0.5, v.z + 0.5).scale(new THREE.Vector3(k, k, k)) : HIDDEN);
    });
    o.instanceMatrix.needsUpdate = true;
  });
}
