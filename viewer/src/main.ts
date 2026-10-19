import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { Scene } from './ir';
import { buildSceneObject, frameCamera } from './render';
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
    __viewerReady?: { ok: boolean; voxels: number; error?: string; seed: number | null; loads: number };
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
  controls.update();
  renderer.render(scene, camera);
});

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
  });
  content = null;
}

function buildContent(): void {
  if (!current) return;
  disposeContent();
  content = buildSceneObject(current, { colour: state.colour, style: effectiveStyle() });
  scene.add(content);
  mountViolationPanel(document.body, current); // V3: remounts (or clears) the panel on every scene change; the red overlay is the render.ts violations group
  top = topStorey(content);
  const b = sceneBounds(current);
  grid.position.set((b.min[0] + b.max[0]) / 2, b.min[1], (b.min[2] + b.max[2]) / 2);
  if (!framed) { frameCamera(camera, controls, b); framed = true; }
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
    requestAnimationFrame(() => requestAnimationFrame(() => {
      window.__viewerReady = { ok: true, voxels, seed: state.seed, loads };
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
