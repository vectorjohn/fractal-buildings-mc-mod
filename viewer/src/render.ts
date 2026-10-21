import * as THREE from 'three';
import type { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { Scene } from './ir';
import type { Voxel } from './voxels';
import { LAYERS } from './ir';
import { buildViolationOverlay } from './overlay';
import { BED_COLOUR, groundTint, hookColour, roleColour, TERRAIN_COLOUR, WATER_COLOUR } from './palettes';
import { FACE_MESH_THRESHOLD, Occupancy, maxOf, minOf, exposedFaces, groundCells, terrainMesh, terrainTops, waterMesh } from './mesh';
import type { MeshData } from './mesh';
import { cellHash } from './styles';
import { roofShades } from './shading';
import { styleColour } from './styles';
import { placeColumn, sceneBounds, sceneVoxels, styleLocator, terrainVoxels } from './voxels';

export interface RenderOptions {
  colour: 'role' | 'palette';
  /** Palette style; defaults to the first building's spec style. */
  style?: string;
  /** auto = face mesh above FACE_MESH_THRESHOLD building voxels. Default auto. */
  mesh?: 'auto' | 'faces' | 'instanced';
  /** Drop the interior layer and hook markers. */
  exterior?: boolean;
}

/** What buildSceneObject reports (also `root.userData.stats`). */
export interface SceneStats { mode: 'faces' | 'instanced'; voxels: number; triangles: number; district: boolean;
  /** Ground cells drawn (roads, paving, fill) and water quads: lets tests see that those layers are not empty. */
  groundCells: number; waterQuads: number }

/** Child group names: the contract V2-V4 build on. `violations` holds the red overlay (overlay.ts). */
export const GROUP_NAMES = ['terrain', 'water', ...LAYERS, 'violations'] as const;

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

function geometryOf(m: MeshData): THREE.BufferGeometry {
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.BufferAttribute(m.positions, 3));
  g.setAttribute('normal', new THREE.BufferAttribute(m.normals, 3));
  g.setAttribute('color', new THREE.BufferAttribute(m.colours, 3));
  g.setIndex(new THREE.BufferAttribute(m.indices, 1));
  g.computeBoundingSphere();
  return g;
}

const faceMaterial = (): THREE.MeshLambertMaterial => new THREE.MeshLambertMaterial({ vertexColors: true });

/** Group with named children terrain, water, foundation, shell, interior, roof, ground, violations. Instanced cubes for small scenes, merged exposed faces for big ones. */
export function buildSceneObject(scene: Scene, opts: RenderOptions): THREE.Group {
  const locate = styleLocator(scene, opts.style);
  const root = new THREE.Group();
  root.name = 'scene';
  for (const name of GROUP_NAMES) {
    const g = new THREE.Group();
    g.name = name;
    root.add(g);
  }
  const group = (n: string) => root.getObjectByName(n)!;
  const district = scene.ground.length > 0 || (scene.terrain?.water?.some((d) => d > 0) ?? false);

  const all = sceneVoxels(scene);
  const faces = opts.mesh === 'faces' || (opts.mesh !== 'instanced' && all.length > FACE_MESH_THRESHOLD);
  const voxels = opts.exterior ? all.filter((v) => v.layer !== 'interior') : all;
  const ground = groundCells(scene.ground);
  let triangles = 0;
  let waterQuads = 0;
  const colourOf = (v: { role: string; x: number; y: number; z: number; building?: number }, shade: number): number => {
    const style = v.building !== undefined ? (opts.style ?? scene.buildings[v.building]?.shell.spec.style ?? 'plain') : locate(v.x, v.z);
    const base = opts.colour === 'palette' ? styleColour(style, v.role, v.x, v.y, v.z) : roleColour(v.role);
    return shadeColour(base, shade);
  };
  const groundColour = (i: number): number => colourOf(ground[i]!, 1);

  const shades = roofShades(voxels);
  const tops = terrainTops(scene);
  const shadeOf = new Map<unknown, number>();
  voxels.forEach((v, i) => shadeOf.set(v, shades[i]!));

  if (faces) {
    // One occupancy index over everything solid, so a face between two layers (or a wall and the road) is not drawn.
    const lo: [number, number, number] = [Infinity, Infinity, Infinity], hi: [number, number, number] = [-Infinity, -Infinity, -Infinity];
    const grow = (c: { x: number; y: number; z: number }) => {
      lo[0] = Math.min(lo[0], c.x); lo[1] = Math.min(lo[1], c.y); lo[2] = Math.min(lo[2], c.z);
      hi[0] = Math.max(hi[0], c.x); hi[1] = Math.max(hi[1], c.y); hi[2] = Math.max(hi[2], c.z);
    };
    voxels.forEach(grow); ground.forEach(grow);
    const t = scene.terrain;
    if (t) { grow({ x: t.x0, y: minOf(tops) - 4, z: t.z0 }); grow({ x: t.x0 + t.nx - 1, y: maxOf(tops), z: t.z0 + t.nz - 1 }); }
    if (isFinite(lo[0])) {
      const occ = new Occupancy(lo, hi);
      for (const v of voxels) occ.add(v.x, v.y, v.z);
      for (const g of ground) occ.add(g.x, g.y, g.z);
      const solid = (x: number, y: number, z: number): boolean => {
        if (occ.has(x, y, z)) return true;
        if (!t) return false;
        const i = x - t.x0, j = z - t.z0;
        return i >= 0 && j >= 0 && i < t.nx && j < t.nz && y < tops[j * t.nx + i]!;
      };
      for (const layer of LAYERS) {
        const cells = layer === 'ground' ? ground : voxels.filter((v) => v.layer === layer);
        if (cells.length === 0) continue;
        const m = exposedFaces({
          cells,
          colour: layer === 'ground' ? groundColour : (i) => colourOf(cells[i]!, shadeOf.get(cells[i]) ?? 1),
        }, solid);
        triangles += m.quads * 2;
        group(layer).add(new THREE.Mesh(geometryOf(m), faceMaterial()));
      }
    }
  } else {
    for (const layer of LAYERS) {
      const vs = layer === 'ground' ? ground : voxels.filter((v) => v.layer === layer);
      if (vs.length === 0) continue;
      const mesh = instanced(vs, layer === 'ground' ? groundColour : (i) => colourOf(vs[i]!, shadeOf.get(vs[i])!));
      if (layer !== 'ground') mesh.userData['voxels'] = vs;
      group(layer).add(mesh);
      triangles += vs.length * 12;
    }
    if (!opts.exterior) {
      const markers = hookMarkers(scene);
      if (markers.length > 0) {
        const mesh = instanced(markers, (i) => hookColour(markers[i]!.kind), HOOK_MARKER_SIZE);
        mesh.userData['voxels'] = markers;
        mesh.userData['scale'] = HOOK_MARKER_SIZE;
        mesh.name = 'hooks';
        group('interior').add(mesh);
      }
    }
  }

  // Terrain: the heightfield (cut by air ground boxes) for district scenes and the face path, else one cube per column.
  if (scene.terrain && (district || faces)) {
    const t = scene.terrain;
    const groundTop = new Set(ground.map((g) => `${g.x},${g.y},${g.z}`));
    const m = terrainMesh(scene, tops, (x, z) => {
      const wet = (t.water?.[(z - t.z0) * t.nx + (x - t.x0)] ?? 0) > 0;
      const base = wet ? BED_COLOUR : opts.colour === 'palette' ? groundTint(locate(x, z)) : TERRAIN_COLOUR;
      return shadeColour(base, 0.94 + (cellHash(x, 0, z) % 13) / 100);
    }, (x, y, z) => groundTop.has(`${x},${y},${z}`));
    triangles += m.quads * 2;
    group('terrain').add(new THREE.Mesh(geometryOf(m), faceMaterial()));
  } else {
    const terrain = terrainVoxels(scene);
    if (terrain.length > 0) { group('terrain').add(instanced(terrain, () => TERRAIN_COLOUR)); triangles += terrain.length * 12; }
  }
  if (scene.terrain?.water) {
    const m = waterMesh(scene, WATER_COLOUR);
    waterQuads = m.quads;
    if (m.quads > 0) {
      triangles += m.quads * 2;
      const mat = new THREE.MeshLambertMaterial({ vertexColors: true, transparent: true, opacity: 0.65, depthWrite: false, side: THREE.DoubleSide });
      group('water').add(new THREE.Mesh(geometryOf(m), mat));
    }
  }
  group('violations').add(buildViolationOverlay(scene.violations));
  const stats: SceneStats = { mode: faces ? 'faces' : 'instanced', voxels: all.length, triangles, district, groundCells: ground.length, waterQuads };
  root.userData['stats'] = stats;
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
