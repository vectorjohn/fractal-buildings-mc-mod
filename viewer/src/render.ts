import * as THREE from 'three';
import type { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { Scene } from './ir';
import type { Voxel } from './voxels';
import { LAYERS } from './ir';
import { buildViolationOverlay } from './overlay';
import { hookColour, roleColour, TERRAIN_COLOUR } from './palettes';
import { roofShades } from './shading';
import { styleColour } from './styles';
import { placeColumn, sceneBounds, sceneVoxels, terrainVoxels } from './voxels';

export interface RenderOptions {
  colour: 'role' | 'palette';
  /** Palette style; defaults to the first building's spec style. */
  style?: string;
}

/** Child group names: the contract V2-V4 build on. `violations` holds the red overlay (overlay.ts). */
export const GROUP_NAMES = ['terrain', ...LAYERS, 'violations'] as const;

function instanced(cells: { x: number; y: number; z: number }[], colour: (i: number) => number, scale = 1): THREE.InstancedMesh {
  const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshLambertMaterial(), cells.length);
  const m = new THREE.Matrix4();
  const c = new THREE.Color();
  cells.forEach((v, i) => {
    m.makeTranslation(v.x + 0.5, v.y + 0.5, v.z + 0.5).scale(new THREE.Vector3(scale, scale, scale));
    mesh.setMatrixAt(i, m);
    mesh.setColorAt(i, c.setHex(colour(i)));
  });
  mesh.instanceMatrix.needsUpdate = true;
  return mesh;
}

/** Scale a packed 0xRRGGBB colour by a brightness factor (clamped to 255 per channel). */
export function shadeColour(rgb: number, f: number): number {
  const ch = (shift: number) => Math.min(255, Math.round(((rgb >> shift) & 255) * f));
  return (ch(16) << 16) | (ch(8) << 8) | ch(0);
}

/** Edge length of a hook marker, in voxels. */
export const HOOK_MARKER_SIZE = 0.4;

/** World cell and storey of every furnishing hook (the shell's local pos placed like its boxes), as pseudo-voxels. */
export function hookMarkers(scene: Scene): (Voxel & { kind: string })[] {
  const out: (Voxel & { kind: string })[] = [];
  scene.buildings.forEach((b, building) => {
    const [ox, oy, oz] = b.origin;
    const [sx, , sz] = b.shell.bounds.size;
    for (const h of b.shell.hooks) {
      const [wx, wz] = placeColumn(b.rotation, ox, oz, sx, sz, h.pos[0], h.pos[2]);
      const room = b.shell.rooms.find((r) => r.id === h.room);
      out.push({ x: wx, y: oy + h.pos[1], z: wz, role: 'floor', layer: 'interior', storey: room?.storey ?? 0, building, kind: h.kind });
    }
  });
  return out;
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
  const shades = roofShades(voxels);
  const shadeOf = new Map<unknown, number>();
  voxels.forEach((v, i) => shadeOf.set(v, shades[i]!));
  for (const layer of LAYERS) {
    const vs = voxels.filter((v) => v.layer === layer);
    if (vs.length === 0) continue;
    const mesh = instanced(vs, (i) => {
      const v = vs[i]!;
      const base = opts.colour === 'palette' ? styleColour(style, v.role, v.x, v.y, v.z) : roleColour(v.role);
      return shadeColour(base, shadeOf.get(v)!);
    });
    mesh.userData['voxels'] = vs;
    group(layer).add(mesh);
  }
  const markers = hookMarkers(scene);
  if (markers.length > 0) {
    const mesh = instanced(markers, (i) => hookColour(markers[i]!.kind), HOOK_MARKER_SIZE);
    mesh.userData['voxels'] = markers;
    mesh.userData['scale'] = HOOK_MARKER_SIZE;
    mesh.name = 'hooks';
    group('interior').add(mesh);
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
