import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { FlyControls } from 'three/examples/jsm/controls/FlyControls.js';
import { applyStop, currentStop, defaultFlyStop, formatStops, parseStops } from './fly';
import type { Scene } from './ir';
import { buildSceneObject, frameCamera } from './render';
import type { SceneStats } from './render';
import { resolveSource } from './source';
import type { Source } from './source';
import { latestGate, parseState, resolveSeed, serializeState, stepSeed, toggleLayer } from './state';
import type { ViewState } from './state';
import { styleIds } from './styles';
import { mountViolationPanel } from './overlay';
import { createUi } from './ui';
import { applyLayers, applyStoreyLimit, topStorey } from './visibility';
import { sceneBounds, sceneVoxels } from './voxels';

declare global {
  interface Window {
    /** Set once the scene has been drawn (or failed); the Playwright screenshot waits for it. voxels = building voxels, not terrain. `seed` is the seed shown; `loads` counts scene (re)builds. */
    __viewerReady?: { ok: boolean; voxels: number; error?: string; seed: number | null; loads: number;
      /** Fly mode: index of the camera stop in effect (-1 = none), and the scene's mesh stats. */
      stop?: number; stats?: SceneStats };
    /** Fly/district helpers for tests and screenshots. */
    __viewer?: {
      goStop(i: number): void; stops: number; pose(): string;
      /** Mean wall time of n renders, ms (readPixels after each), plus renderer.info counters. */
      measure(n: number): { frameMs: number; triangles: number; drawCalls: number };
    };
  }
}

const fixtures = import.meta.glob('../fixtures/*.json', { query: '?raw', import: 'default', eager: true }) as Record<string, string>;

let state: ViewState = parseState(location.search);
let source: Source | null = null;
let sourceKey = '';
let current: Scene | null = null;
let content: THREE.Object3D | null = null;
let top = 0;
let loads = 0;
const rebuilds = latestGate();
let framed = false;
let error: string | null = null;

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(window.devicePixelRatio);
renderer.setSize(window.innerWidth, window.innerHeight);
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x1b1d22);
const camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.1, 1000);
const controls = new OrbitControls(camera, renderer.domElement);
const flyMode = state.camera === 'fly';
const fly = flyMode ? new FlyControls(camera, renderer.domElement) : null;
if (fly) { fly.dragToLook = true; fly.rollSpeed = 0.5; fly.movementSpeed = 20; controls.enabled = false; }
const stops = parseStops(state.stops);
let stop = -1;
const clock = new THREE.Clock();
scene.add(new THREE.HemisphereLight(0xffffff, 0x444455, 1.2));
const sun = new THREE.DirectionalLight(0xffffff, 2);
sun.position.set(5, 10, 7);
scene.add(sun);
const grid = new THREE.GridHelper(64, 64, 0x888888, 0x444444);
scene.add(grid);

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
renderer.setAnimationLoop(() => {
  const dt = clock.getDelta();
  if (fly) fly.update(dt); else controls.update();
  renderer.render(scene, camera);
});

function goStop(i: number): void {
  const s = stops[i];
  if (!s || !fly) return;
  stop = i;
  applyStop(camera, s);
  requestAnimationFrame(() => requestAnimationFrame(() => {
    if (window.__viewerReady) window.__viewerReady = { ...window.__viewerReady, stop: i };
  }));
}
window.addEventListener('keydown', (e) => {
  if (!fly || stops.length === 0 || e.target instanceof HTMLInputElement || e.target instanceof HTMLSelectElement) return;
  if (e.key === ']') goStop((stop + 1) % stops.length);
  else if (e.key === '[') goStop((stop - 1 + stops.length) % stops.length);
});
window.__viewer = {
  goStop, stops: stops.length, pose: () => formatStops([currentStop(camera)]),
  measure(n) {
    const gl = renderer.getContext();
    const px = new Uint8Array(4);
    renderer.info.reset();
    const t0 = performance.now();
    for (let i = 0; i < n; i++) { renderer.render(scene, camera); gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px); } // readPixels forces the GPU to finish (gl.finish alone returned early under ANGLE)
    const ms = (performance.now() - t0) / n;
    return { frameMs: ms, triangles: renderer.info.render.triangles / n, drawCalls: renderer.info.render.calls / n };
  },
};

const sceneStyle = (): string => current?.buildings[0]?.shell.spec.style ?? 'plain';
const effectiveStyle = (): string => state.style ?? sceneStyle();

const ui = createUi({
  onSeed(d) {
    const seeds = source?.seeds ?? [];
    const to = typeof d === 'number' ? stepSeed(seeds, state.seed, d) : resolveSeed(seeds, d.to);
    if (to === state.seed) return;
    void change({ ...state, seed: to }, true);
  },
  onStyle(style) { void change({ ...state, style }, true); },
  onLayer(layer, on) { void change(toggleLayer(state, layer, on), false); },
  onColour(colour) { void change({ ...state, colour }, true); },
  onStoreys(maxStorey) { void change({ ...state, maxStorey }, false); },
});

function syncUi(): void {
  ui.update({
    state, styles: styleIds(), style: effectiveStyle(), seeds: source?.seeds ?? [], topStorey: top,
    status: error ?? (source ? `${source.kind}${source.seeds.length > 1 ? `, ${source.seeds.length} seeds` : ''}` : 'loading'),
  });
}

/** Cheap view changes: layer visibility and storey limit act on the existing groups. */
function applyView(): void {
  if (!content) return;
  applyLayers(content, state.layers);
  applyStoreyLimit(content, state.maxStorey, top);
}

function disposeContent(): void {
  if (!content) return;
  scene.remove(content);
  content.traverse((o) => {
    if (o instanceof THREE.InstancedMesh) { o.geometry.dispose(); (o.material as THREE.Material).dispose(); o.dispose(); }
    else if (o instanceof THREE.Mesh) { o.geometry.dispose(); (o.material as THREE.Material).dispose(); }
  });
  content = null;
}

function buildContent(): void {
  if (!current) return;
  disposeContent();
  content = buildSceneObject(current, { colour: state.colour, style: effectiveStyle(), mesh: state.mesh, exterior: state.exterior });
  scene.add(content);
  mountViolationPanel(document.body, current); // V3: remounts (or clears) the panel on every scene change; the red overlay is the render.ts violations group
  top = topStorey(content);
  const b = sceneBounds(current);
  grid.visible = !(content.userData['stats'] as SceneStats).district;
  grid.position.set((b.min[0] + b.max[0]) / 2, b.min[1], (b.min[2] + b.max[2]) / 2);
  if (!framed) {
    frameCamera(camera, controls, b);
    if (fly) {
      const span = Math.max(b.max[0] - b.min[0], b.max[2] - b.min[2], 16);
      fly.movementSpeed = Math.max(10, span / 6);
      applyStop(camera, defaultFlyStop(b));
      if (stops.length > 0) goStop(0);
    }
    framed = true;
  }
  applyView();
}

/**
 * Apply a new state. `rebuild` = scene geometry or colours change (seed, style, colour); the source is re-resolved
 * only when the batch it comes from changes (style, ir, fixture).
 */
async function change(next: ViewState, rebuild: boolean): Promise<void> {
  const prevState = state;
  state = next;
  history.replaceState(null, '', `${location.pathname}${serializeState(state) ? `?${serializeState(state)}` : ''}`);
  if (!rebuild) { applyView(); syncUi(); return; }
  const ticket = rebuilds.begin();
  try {
    const key = `${state.ir ?? ''}|${state.fixture ?? ''}|${state.style ?? ''}`;
    if (!source || key !== sourceKey) {
      source = await resolveSource({ ir: state.ir, fixture: state.fixture, style: state.style },
        (u) => fetch(u), fixtures);
      sourceKey = key;
      if (!rebuilds.isLatest(ticket)) return;
    }
    state = { ...state, seed: resolveSeed(source.seeds, state.seed) };
    if (state.seed !== prevState.seed || source.seeds.length > 0) {
      history.replaceState(null, '', `${location.pathname}${serializeState(state) ? `?${serializeState(state)}` : ''}`);
    }
    const needScene = !current || state.seed !== lastSceneSeed || key !== lastSceneKey;
    if (needScene) {
      const loaded = await source.load(state.seed);
      if (!rebuilds.isLatest(ticket)) return; // a newer change superseded this load
      current = loaded; lastSceneKey = key; lastSceneSeed = state.seed;
    }
    error = null;
    buildContent();
    loads++;
    syncUi();
    const voxels = sceneVoxels(current!).length;
    const stats = content!.userData['stats'] as SceneStats;
    requestAnimationFrame(() => requestAnimationFrame(() => {
      window.__viewerReady = { ok: true, voxels, seed: state.seed, loads, stats, ...(fly ? { stop } : {}) };
    }));
  } catch (e) {
    console.error(e);
    error = String(e);
    syncUi();
    window.__viewerReady = { ok: false, voxels: 0, error: String(e), seed: state.seed, loads };
  }
}
let lastSceneKey = '';
let lastSceneSeed: number | null = null;

void change(state, true);
