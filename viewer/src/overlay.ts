import * as THREE from 'three';
import type { Scene, Violation } from './ir';

export const VIOLATION_COLOUR = 0xff1a1a;
const RENDER_ORDER = 1000;

/** Red translucent boxes plus red edges for every violation cell; drawn through walls (depthTest off). Empty group if none. */
export function buildViolationOverlay(violations: Violation[]): THREE.Group {
  const g = new THREE.Group();
  g.name = 'violation-overlay';
  const cells = violations.flatMap((v) => v.cells);
  if (cells.length === 0) return g;
  const fill = new THREE.InstancedMesh(
    new THREE.BoxGeometry(1, 1, 1),
    new THREE.MeshBasicMaterial({ color: VIOLATION_COLOUR, transparent: true, opacity: 0.35, depthTest: false, depthWrite: false }),
    cells.length,
  );
  const edgePositions: number[] = [];
  const unit = new THREE.EdgesGeometry(new THREE.BoxGeometry(1, 1, 1)).getAttribute('position');
  const m = new THREE.Matrix4();
  cells.forEach((c, i) => {
    const [sx, sy, sz] = c.size;
    const cx = c.min[0] + sx / 2, cy = c.min[1] + sy / 2, cz = c.min[2] + sz / 2;
    m.compose(new THREE.Vector3(cx, cy, cz), new THREE.Quaternion(), new THREE.Vector3(sx, sy, sz));
    fill.setMatrixAt(i, m);
    for (let k = 0; k < unit.count; k++) {
      edgePositions.push(cx + unit.getX(k) * sx, cy + unit.getY(k) * sy, cz + unit.getZ(k) * sz);
    }
  });
  fill.instanceMatrix.needsUpdate = true;
  fill.renderOrder = RENDER_ORDER;
  fill.name = 'violation-fill';
  const eg = new THREE.BufferGeometry();
  eg.setAttribute('position', new THREE.Float32BufferAttribute(edgePositions, 3));
  const edges = new THREE.LineSegments(eg, new THREE.LineBasicMaterial({ color: VIOLATION_COLOUR, depthTest: false }));
  edges.renderOrder = RENDER_ORDER + 1;
  edges.name = 'violation-edges';
  g.add(fill, edges);
  return g;
}

export interface ViolationRow { invariant: string; building: string; message: string; cells: number }

/** One row per violation, in scene order (the panel's content; pure so it can be tested without a DOM). */
export function violationRows(violations: Violation[]): ViolationRow[] {
  return violations.map((v) => ({
    invariant: v.invariant, building: v.building ?? '(scene)', message: v.message,
    cells: v.cells.reduce((n, c) => n + c.size[0] * c.size[1] * c.size[2], 0),
  }));
}

/** Small legend listing failing invariants (id, building, message). Nothing is mounted when there are none. Returns the element or null. */
export function mountViolationPanel(parent: HTMLElement, scene: Scene): HTMLElement | null {
  parent.querySelector('#violation-panel')?.remove();
  const rows = violationRows(scene.violations);
  if (rows.length === 0) return null;
  const el = document.createElement('div');
  el.id = 'violation-panel';
  el.style.cssText = 'position:fixed;bottom:8px;left:8px;max-width:60ch;font:12px sans-serif;color:#eee;background:#000a;padding:6px 10px;border-radius:4px;border-left:4px solid #ff1a1a';
  const h = document.createElement('div');
  h.style.fontWeight = 'bold';
  h.textContent = `Invariant violations (${rows.length})`;
  el.appendChild(h);
  for (const r of rows) {
    const d = document.createElement('div');
    d.className = 'violation-row';
    d.dataset['invariant'] = r.invariant;
    const id = document.createElement('b');
    id.style.color = '#ff6b6b';
    id.textContent = r.invariant;
    d.append(id, ` [${r.building}] ${r.message} (${r.cells} cells)`);
    el.appendChild(d);
  }
  parent.appendChild(el);
  return el;
}
