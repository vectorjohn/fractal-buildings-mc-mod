import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { parseScene } from './ir';
import type { Scene } from './ir';
import { buildSceneObject, frameCamera } from './render';
import type { RenderOptions } from './render';
import { sceneBounds, sceneVoxels } from './voxels';

declare global {
  interface Window {
    /** Set once the scene has been drawn (or failed); the Playwright screenshot waits for it. voxels = building voxels, not terrain. */
    __viewerReady?: { ok: boolean; voxels: number; error?: string };
  }
}

const fixtures = import.meta.glob('../fixtures/*.json', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

/** Scene source: `?ir=<url>`, or `?fixture=<name>` (viewer/fixtures/<name>.json); default sample-house. */
async function loadScene(params: URLSearchParams): Promise<Scene> {
  const url = params.get('ir');
  if (url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error(`fetch ${url}: ${res.status}`);
    return parseScene(await res.json());
  }
  const name = params.get('fixture') ?? 'sample-house';
  const text = fixtures[`../fixtures/${name}.json`];
  if (text === undefined) throw new Error(`no fixture "${name}"`);
  return parseScene(JSON.parse(text));
}

const params = new URLSearchParams(location.search);
const opts: RenderOptions = { colour: params.get('colour') === 'palette' ? 'palette' : 'role' };
const style = params.get('style');
if (style) opts.style = style;

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

let current: Scene | null = null;
let content: THREE.Object3D | null = null;

function rebuild(): void {
  if (!current) return;
  if (content) {
    scene.remove(content);
    content.traverse((o) => {
      if (o instanceof THREE.InstancedMesh) { o.geometry.dispose(); (o.material as THREE.Material).dispose(); o.dispose(); }
    });
  }
  content = buildSceneObject(current, opts);
  scene.add(content);
}

function addControls(): void {
  const bar = document.createElement('div');
  bar.style.cssText = 'position:fixed;top:8px;left:8px;font:12px sans-serif;color:#ddd;background:#0008;padding:4px 8px;border-radius:4px';
  const sel = document.createElement('select');
  sel.id = 'colour';
  for (const v of ['role', 'palette']) sel.add(new Option(`colour: ${v}`, v, false, v === opts.colour));
  sel.onchange = () => { opts.colour = sel.value === 'palette' ? 'palette' : 'role'; rebuild(); };
  bar.appendChild(sel);
  document.body.appendChild(bar);
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

loadScene(params).then(
  (s) => {
    current = s;
    rebuild();
    addControls();
    const b = sceneBounds(s);
    scene.add(new THREE.GridHelper(64, 64, 0x888888, 0x444444).translateX((b.min[0] + b.max[0]) / 2).translateY(b.min[1]).translateZ((b.min[2] + b.max[2]) / 2));
    frameCamera(camera, controls, b);
    const voxels = sceneVoxels(s).length;
    // Wait two frames so the first render with the mesh has happened.
    requestAnimationFrame(() => requestAnimationFrame(() => (window.__viewerReady = { ok: true, voxels })));
  },
  (e: unknown) => {
    console.error(e);
    window.__viewerReady = { ok: false, voxels: 0, error: String(e) };
  },
);
