import * as THREE from 'three';
import type { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { Scene } from './ir';
import { LAYERS } from './ir';
import { buildViolationOverlay } from './overlay';
import { paletteColour, roleColour, TERRAIN_COLOUR } from './palettes';
import { sceneBounds, sceneVoxels, terrainVoxels } from './voxels';

export interface RenderOptions {
  colour: 'role' | 'palette';
  /** Palette style; defaults to the first building's spec style. */
  style?: string;
}

/** Child group names: the contract V2-V4 build on. `violations` holds the red overlay (overlay.ts). */
export const GROUP_NAMES = ['terrain', ...LAYERS, 'violations'] as const;

function instanced(cells: { x: number; y: number; z: number }[], colour: (i: number) => number): THREE.InstancedMesh {
  const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshLambertMaterial(), cells.length);
  const m = new THREE.Matrix4();
  const c = new THREE.Color();
  cells.forEach((v, i) => {
    m.makeTranslation(v.x + 0.5, v.y + 0.5, v.z + 0.5);
    mesh.setMatrixAt(i, m);
    mesh.setColorAt(i, c.setHex(colour(i)));
  });
  mesh.instanceMatrix.needsUpdate = true;
  return mesh;
}

/** Group with named children terrain, foundation, shell, interior, roof, violations; one InstancedMesh per non-empty layer. */
export function buildSceneObject(scene: Scene, opts: RenderOptions): THREE.Group {
  const style = opts.style ?? scene.buildings[0]?.shell.spec.style ?? 'plain';
  const root = new THREE.Group();
  root.name = 'scene';
  for (const name of GROUP_NAMES) {
    const g = new THREE.Group();
    g.name = name;
    root.add(g);
  }
  const group = (n: string) => root.getObjectByName(n)!;

  const terrain = terrainVoxels(scene);
  if (terrain.length > 0) group('terrain').add(instanced(terrain, () => TERRAIN_COLOUR));

  const voxels = sceneVoxels(scene);
  for (const layer of LAYERS) {
    const vs = voxels.filter((v) => v.layer === layer);
    if (vs.length === 0) continue;
    const mesh = instanced(vs, (i) =>
      opts.colour === 'palette' ? paletteColour(style, vs[i]!.role) : roleColour(vs[i]!.role));
    mesh.userData['voxels'] = vs;
    group(layer).add(mesh);
  }
  group('violations').add(buildViolationOverlay(scene.violations));
  return root;
}

/** Aim the orbit controls at the middle of `bounds` and back the camera off to see all of it. */
export function frameCamera(
  camera: THREE.PerspectiveCamera, controls: OrbitControls, bounds: { min: number[]; max: number[] },
): void {
  const [x0, y0, z0] = bounds.min as [number, number, number];
  const [x1, y1, z1] = bounds.max as [number, number, number];
  const c = new THREE.Vector3((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
  const d = Math.max(x1 - x0, y1 - y0, z1 - z0, 4);
  controls.target.copy(c);
  camera.position.set(c.x + d * 1.0, c.y + d * 0.9, c.z - d * 1.4);
  camera.near = 0.1;
  camera.far = Math.max(1000, d * 20);
  camera.updateProjectionMatrix();
  controls.update();
}

export { sceneBounds };
