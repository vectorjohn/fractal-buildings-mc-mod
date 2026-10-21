import * as THREE from 'three';
import { describe, expect, it } from 'vitest';
import { applyStop, currentStop, defaultFlyStop, formatStops, parseStops } from './fly';

describe('stops', () => {
  it('parses and skips junk', () => {
    expect(parseStops('1,2,3,90,-10;bad;4,5,6,0,0')).toEqual([
      { x: 1, y: 2, z: 3, yaw: 90, pitch: -10 }, { x: 4, y: 5, z: 6, yaw: 0, pitch: 0 },
    ]);
    expect(parseStops(null)).toEqual([]);
    expect(parseStops('1,2,3,4')).toEqual([]);
    expect(parseStops('1,2,,4,5')).toEqual([]);
  });
  it('round-trips through format', () => {
    const q = '10,70,20,45,-15;0,64,0,0,0';
    expect(formatStops(parseStops(q))).toBe(q);
  });
  it('yaw 0 looks toward -z, yaw 90 toward -x, pitch down looks down', () => {
    const cam = new THREE.PerspectiveCamera();
    const dir = (): THREE.Vector3 => cam.getWorldDirection(new THREE.Vector3());
    applyStop(cam, { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 });
    expect(dir().z).toBeCloseTo(-1);
    applyStop(cam, { x: 0, y: 0, z: 0, yaw: 90, pitch: 0 });
    expect(dir().x).toBeCloseTo(-1);
    applyStop(cam, { x: 0, y: 0, z: 0, yaw: 0, pitch: -30 });
    expect(dir().y).toBeCloseTo(-0.5);
  });
  it('currentStop inverts applyStop', () => {
    const cam = new THREE.PerspectiveCamera();
    const s = { x: 12.5, y: 70, z: -3, yaw: 120, pitch: -25 };
    applyStop(cam, s);
    expect(currentStop(cam)).toEqual(s);
  });
  it('default pose looks at the centre of the bounds', () => {
    const b = { min: [0, 60, 0], max: [100, 80, 100] };
    const cam = new THREE.PerspectiveCamera();
    applyStop(cam, defaultFlyStop(b));
    const to = new THREE.Vector3(50, 60, 50).sub(cam.position).normalize();
    expect(cam.getWorldDirection(new THREE.Vector3()).dot(to)).toBeGreaterThan(0.999);
  });
});
