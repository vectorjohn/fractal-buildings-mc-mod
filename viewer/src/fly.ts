// Fly-through camera: stop parsing and posing (pure apart from the camera object), FlyControls wiring lives in main.ts.
import * as THREE from 'three';

/** A camera stop: position in world blocks, yaw/pitch in degrees. Yaw 0 looks toward -z, 90 toward -x (three.js: rotation.y); pitch > 0 looks up. */
export interface Stop { x: number; y: number; z: number; yaw: number; pitch: number }

/** `x,y,z,yaw,pitch;x,y,z,yaw,pitch;...`. Malformed entries are skipped (a typo must not blank the view). */
export function parseStops(s: string | null): Stop[] {
  if (!s) return [];
  const out: Stop[] = [];
  for (const part of s.split(';')) {
    const n = part.split(',').map((t) => (t.trim() === '' ? NaN : Number(t)));
    if (n.length === 5 && n.every((v) => Number.isFinite(v))) out.push({ x: n[0]!, y: n[1]!, z: n[2]!, yaw: n[3]!, pitch: n[4]! });
  }
  return out;
}

export function formatStops(stops: readonly Stop[]): string {
  return stops.map((s) => [s.x, s.y, s.z, s.yaw, s.pitch].join(',')).join(';');
}

export function applyStop(camera: THREE.PerspectiveCamera, s: Stop): void {
  camera.position.set(s.x, s.y, s.z);
  camera.rotation.order = 'YXZ';
  camera.rotation.set((s.pitch * Math.PI) / 180, (s.yaw * Math.PI) / 180, 0);
  camera.updateMatrixWorld(true);
}

/** Read the camera back as a stop (for "copy this view" into `?stops=`). */
export function currentStop(camera: THREE.PerspectiveCamera): Stop {
  const e = new THREE.Euler().setFromQuaternion(camera.quaternion, 'YXZ');
  const r = (v: number) => Math.round(v * 10) / 10;
  return { x: r(camera.position.x), y: r(camera.position.y), z: r(camera.position.z), yaw: r((e.y * 180) / Math.PI), pitch: r((e.x * 180) / Math.PI) };
}

/** A default fly pose: above the bounds' south-east corner, looking at the centre. */
export function defaultFlyStop(bounds: { min: number[]; max: number[] }): Stop {
  const [x0, y0, z0] = bounds.min as [number, number, number];
  const [x1, y1, z1] = bounds.max as [number, number, number];
  const cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, d = Math.max(x1 - x0, z1 - z0, 16);
  const px = cx + d * 0.3, py = y1 + d * 0.25, pz = cz + d * 0.6;
  const dx = cx - px, dz = cz - pz, dy = y0 - py;
  return {
    x: px, y: py, z: pz,
    yaw: (Math.atan2(-dx, -dz) * 180) / Math.PI,
    pitch: (Math.atan2(dy, Math.hypot(dx, dz)) * 180) / Math.PI,
  };
}
