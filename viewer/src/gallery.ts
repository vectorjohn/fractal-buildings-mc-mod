import * as THREE from 'three';
import { buildSceneObject } from './render';
import { FOV_DEG, PRESET_NAMES, frontVector, presetPose } from './presets';
import type { PresetName } from './presets';
import { resolveSource } from './source';
import { sceneBounds, sceneVoxels } from './voxels';

declare global {
  interface Window {
    /** Set once all cells are drawn (or failed). `cells[i]` describes the i-th cell in reading order. */
    __galleryReady?: {
      ok: boolean; error?: string; violations: number; style: string; preset: string; dir: string;
      cells: { seed: number; voxels: number; violations: number }[];
    };
  }
}

const COLS = 4, ROWS = 4;
const q = new URLSearchParams(location.search);
const style = q.get('style') ?? 'medieval';
const terrain = q.get('terrain') ?? 'noise'; // `flat` reads gen/<style>-flat
const from = Number(q.get('from') ?? 0);
const preset = (q.get('preset') ?? 'front-left') as PresetName;
const dir = q.get('dir') ?? (terrain === 'flat' ? `${style}-flat` : style);
const colour = q.get('colour') === 'role' ? 'role' : 'palette';

async function main(): Promise<void> {
  if (!PRESET_NAMES.includes(preset)) throw new Error(`unknown preset ${preset}`);
  const wrap = document.getElementById('wrap')!;
  const size = Math.min(window.innerWidth, window.innerHeight);
  const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.setSize(size, size);
  renderer.setClearColor(0x1b1d22);
  wrap.prepend(renderer.domElement);

  const source = await resolveSource({ ir: null, fixture: null, style: dir }, (u) => fetch(u), {});
  if (source.kind !== 'batch') throw new Error(`no batch at gen/${dir}/index.json (run npm run gen)`);
  const seeds = source.seeds.filter((s) => s >= from).slice(0, COLS * ROWS);
  if (seeds.length === 0) throw new Error(`no seeds >= ${from}`);

  const scene = new THREE.Scene();
  const hemi = new THREE.HemisphereLight(0xffffff, 0x444455, 1.2);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xffffff, 2);
  sun.position.set(5, 10, 7);
  scene.add(sun, sun.target);
  const camera = new THREE.PerspectiveCamera(FOV_DEG, 1, 0.1, 2000);

  const cellPx = size / COLS;
  const cells: { seed: number; voxels: number; violations: number }[] = [];
  renderer.setScissorTest(true);
  for (let i = 0; i < seeds.length; i++) {
    const seed = seeds[i]!;
    const data = await source.load(seed);
    const content = buildSceneObject(data, { colour, style: data.buildings[0]?.shell.spec.style });
    scene.add(content);
    const pose = presetPose(preset, sceneBounds(data), data.lots[0]?.front ?? 'south');
    camera.position.copy(pose.position);
    camera.up.copy(pose.up);
    camera.lookAt(pose.target);
    camera.updateProjectionMatrix();
    // Headlight-ish: the sun follows the camera (a bit above it) so every preset and facing is lit alike.
    const dist = pose.position.distanceTo(pose.target);
    sun.position.copy(pose.position).add(new THREE.Vector3(0, dist * 0.6, 0));
    if (preset === 'top') {
      // From above a headlight makes every upward face identical. Use a fixed low sun from the front-right
      // (relative to the lot's front) so roof slopes, ridges and gable ends take different shades.
      const [fx, fz] = frontVector(data.lots[0]?.front ?? 'south');
      sun.position.copy(pose.target).add(new THREE.Vector3(0.8 * fx + 0.6 * fz, 0.55, 0.8 * fz - 0.6 * fx).multiplyScalar(dist));
      hemi.intensity = 0.5;
    } else hemi.intensity = 1.2;
    sun.target.position.copy(pose.target);
    sun.target.updateMatrixWorld();
    const col = i % COLS, row = Math.floor(i / COLS);
    const x = col * cellPx, yTop = row * cellPx;
    const gl = (v: number) => Math.round(v);
    renderer.setViewport(gl(x), gl(size - yTop - cellPx), gl(cellPx), gl(cellPx));
    renderer.setScissor(gl(x), gl(size - yTop - cellPx), gl(cellPx), gl(cellPx));
    renderer.render(scene, camera);
    scene.remove(content);
    content.traverse((o) => {
      if (o instanceof THREE.InstancedMesh) { o.geometry.dispose(); (o.material as THREE.Material).dispose(); o.dispose(); }
    });

    const voxels = sceneVoxels(data).length, violations = data.violations.length;
    cells.push({ seed, voxels, violations });
    const label = document.createElement('div');
    label.className = `cell${violations > 0 ? ' bad' : ''}`;
    label.style.cssText = `left:${x}px;top:${yTop}px;width:${cellPx}px;height:${cellPx}px`;
    label.innerHTML = `<span>seed ${seed} &middot; ${voxels} vox &middot; ${violations} viol</span>`;
    wrap.appendChild(label);
  }
  window.__galleryReady = {
    ok: true, violations: cells.reduce((n, c) => n + c.violations, 0), style, preset, dir, cells,
  };
}

main().catch((e) => {
  console.error(e);
  document.getElementById('msg')!.textContent = String(e);
  window.__galleryReady = { ok: false, error: String(e), violations: 0, style, preset, dir, cells: [] };
});
