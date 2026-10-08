import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import fixtureText from '../fixtures/box5.json?raw';
import { parseIr } from './ir';
import type { Ir } from './ir';
import { irToVoxels } from './voxels';

const ROLE_COLORS: Record<string, number> = { wall: 0xc8b89a, trim: 0x7a5c3a, roof: 0xa04a3a, floor: 0x4a6a8a };

declare global {
  interface Window {
    /** Set once the IR has been drawn (or failed); the Playwright screenshot waits for it. */
    __viewerReady?: { ok: boolean; voxels: number; error?: string };
  }
}

/** IR source: `?ir=<url>` loads JSON from that URL, otherwise the bundled CLI fixture. */
async function loadIr(): Promise<Ir> {
  const url = new URLSearchParams(location.search).get('ir');
  if (!url) return parseIr(JSON.parse(fixtureText));
  const res = await fetch(url);
  if (!res.ok) throw new Error(`fetch ${url}: ${res.status}`);
  return parseIr(await res.json());
}

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(window.devicePixelRatio);
renderer.setSize(window.innerWidth, window.innerHeight);
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x1b1d22);
const camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.1, 1000);
const controls = new OrbitControls(camera, renderer.domElement);

scene.add(new THREE.HemisphereLight(0xffffff, 0x444455, 1.2));
const sun = new THREE.DirectionalLight(0xffffff, 2);
sun.position.set(5, 10, 7);
scene.add(sun);
scene.add(new THREE.GridHelper(32, 32));

function show(ir: Ir): number {
  const voxels = irToVoxels(ir);
  const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshLambertMaterial(), voxels.length);
  const m = new THREE.Matrix4();
  voxels.forEach((v, i) => {
    m.makeTranslation(v.x + 0.5, v.y + 0.5, v.z + 0.5);
    mesh.setMatrixAt(i, m);
    mesh.setColorAt(i, new THREE.Color(ROLE_COLORS[v.role] ?? 0xff00ff));
  });
  scene.add(mesh);
  const [sx, sy, sz] = ir.size;
  controls.target.set(sx / 2, sy / 2, sz / 2);
  const d = Math.max(sx, sy, sz);
  camera.position.set(sx / 2 + d * 1.6, sy / 2 + d * 1.1, sz / 2 + d * 2.0);
  controls.update();
  return voxels.length;
}

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});

renderer.setAnimationLoop(() => {
  controls.update();
  renderer.render(scene, camera);
});

loadIr().then(
  (ir) => {
    const voxels = show(ir);
    // Wait two frames so the first render with the mesh has happened.
    requestAnimationFrame(() => requestAnimationFrame(() => (window.__viewerReady = { ok: true, voxels })));
  },
  (e: unknown) => {
    console.error(e);
    window.__viewerReady = { ok: false, voxels: 0, error: String(e) };
  },
);
