import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { Ir } from './ir';
import { irToVoxels } from './voxels';

const ROLE_COLORS: Record<string, number> = { wall: 0xc8b89a, trim: 0x7a5c3a, roof: 0xa04a3a, floor: 0x8a8a8a };

// Placeholder IR until item 0.4 loads the CLI's JSON.
const demo: Ir = { boxes: [{ min: [0, 0, 0], size: [5, 5, 5], role: 'wall' }] };

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setPixelRatio(window.devicePixelRatio);
renderer.setSize(window.innerWidth, window.innerHeight);
document.body.appendChild(renderer.domElement);

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x1b1d22);
const camera = new THREE.PerspectiveCamera(50, window.innerWidth / window.innerHeight, 0.1, 1000);
camera.position.set(10, 8, 12);
const controls = new OrbitControls(camera, renderer.domElement);
controls.target.set(2.5, 2.5, 2.5);
controls.update();

scene.add(new THREE.HemisphereLight(0xffffff, 0x444455, 1.2));
const sun = new THREE.DirectionalLight(0xffffff, 2);
sun.position.set(5, 10, 7);
scene.add(sun);
scene.add(new THREE.GridHelper(32, 32));

const voxels = irToVoxels(demo);
const mesh = new THREE.InstancedMesh(
  new THREE.BoxGeometry(1, 1, 1),
  new THREE.MeshLambertMaterial(),
  voxels.length,
);
const m = new THREE.Matrix4();
voxels.forEach((v, i) => {
  m.makeTranslation(v.x + 0.5, v.y + 0.5, v.z + 0.5);
  mesh.setMatrixAt(i, m);
  mesh.setColorAt(i, new THREE.Color(ROLE_COLORS[v.role] ?? 0xff00ff));
});
scene.add(mesh);

window.addEventListener('resize', () => {
  camera.aspect = window.innerWidth / window.innerHeight;
  camera.updateProjectionMatrix();
  renderer.setSize(window.innerWidth, window.innerHeight);
});
renderer.setAnimationLoop(() => {
  controls.update();
  renderer.render(scene, camera);
});
