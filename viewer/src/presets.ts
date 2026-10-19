import * as THREE from 'three';
import type { Facing } from './ir';

export const PRESET_NAMES = ['front-left', 'back-right', 'top', 'front', 'cutaway'] as const;
export type PresetName = (typeof PRESET_NAMES)[number];

/** Fixed for every cell, so the same seed always frames the same. */
export const FOV_DEG = 35;
const DISTANCE_FACTOR = 1.15;
/** The top preset's angle off vertical, so roof slopes and gable ends read instead of a flat rectangle. */
export const TOP_PITCH_DEG = 22;
const TOP_PITCH = (TOP_PITCH_DEG * Math.PI) / 180;

/** Horizontal unit vector pointing out of the front of a lot (north is -z, east is +x, as in the IR). */
export function frontVector(front: Facing): [number, number] {
  switch (front) {
    case 'north': return [0, -1];
    case 'east': return [1, 0];
    case 'south': return [0, 1];
    case 'west': return [-1, 0];
  }
}

export interface CameraPose { position: THREE.Vector3; target: THREE.Vector3; up: THREE.Vector3 }

/**
 * Camera pose for a preset, relative to the world bounds [min, max) of a building and the facing of its lot.
 * front-left: 3/4 view from the front-left corner, 30 degrees down; back-right: the opposite corner; top: 22 degrees off
 * straight down (tipped towards the front, slightly to the left) with the front at the bottom of the picture; front: level elevation from the front; cutaway: the same pose as top (the gallery hides the roof and the upper storeys).
 */
export function presetPose(
  name: PresetName, bounds: { min: number[]; max: number[] }, front: Facing,
): CameraPose {
  const [x0, y0, z0] = bounds.min as [number, number, number];
  const [x1, y1, z1] = bounds.max as [number, number, number];
  const target = new THREE.Vector3((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2);
  const radius = Math.max(0.5 * Math.hypot(x1 - x0, y1 - y0, z1 - z0), 3);
  const dist = (radius / Math.sin((FOV_DEG / 2) * Math.PI / 180)) * DISTANCE_FACTOR;
  const [fx, fz] = frontVector(front);
  // Seen from the front looking at the building, the viewer's left is (fz, -fx) rotated: left = (-fz, fx) in (x, z).
  const [lx, lz] = [-fz, fx];
  const dir = new THREE.Vector3(); // from target towards camera
  const up = new THREE.Vector3(0, 1, 0);
  const tilt = (30 * Math.PI) / 180;
  const flat = (a: number, b: number) => new THREE.Vector3(fx * a + lx * b, 0, fz * a + lz * b).normalize();
  switch (name) {
    case 'front-left': dir.copy(flat(1, 1)).multiplyScalar(Math.cos(tilt)).setY(Math.sin(tilt)); break;
    case 'back-right': dir.copy(flat(-1, -1)).multiplyScalar(Math.cos(tilt)).setY(Math.sin(tilt)); break;
    case 'front': dir.copy(flat(1, 0)); break;
    case 'top':
    case 'cutaway': { // front edge at the bottom of the picture, tipped off vertical towards the front and a little to the left
      dir.copy(flat(1, 0.35)).multiplyScalar(Math.sin(TOP_PITCH)).setY(Math.cos(TOP_PITCH));
      up.set(-fx, 0, -fz);
      break;
    }
  }
  return { position: target.clone().add(dir.multiplyScalar(dist)), target, up };
}
