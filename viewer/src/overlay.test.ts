import { readFileSync } from 'node:fs';
import * as THREE from 'three';
import { describe, expect, it } from 'vitest';
import { parseScene } from './ir';
import { buildViolationOverlay, violationRows } from './overlay';
import { buildSceneObject } from './render';

const load = (n: string) => parseScene(JSON.parse(readFileSync(new URL(`../fixtures/${n}.json`, import.meta.url), 'utf8')));

describe('violation overlay', () => {
  const bad = load('sample-house-violations');
  it('draws one instance per cell, through walls, in the violations group', () => {
    const root = buildSceneObject(bad, { colour: 'role' });
    const g = root.getObjectByName('violations')!;
    const fill = g.getObjectByName('violation-fill') as THREE.InstancedMesh;
    expect(fill.count).toBe(bad.violations.reduce((n, v) => n + v.cells.length, 0));
    const mat = fill.material as THREE.MeshBasicMaterial;
    expect(mat.depthTest).toBe(false);
    expect(mat.transparent).toBe(true);
    expect(fill.renderOrder).toBeGreaterThan(100);
    const edges = g.getObjectByName('violation-edges') as THREE.LineSegments;
    expect(edges.geometry.getAttribute('position').count).toBe(fill.count * 24);
  });
  it('places a multi-cell box at its centre with its size', () => {
    const g = buildViolationOverlay([{ invariant: 'x', building: null, message: 'm', cells: [{ min: [10, 20, 30], size: [2, 3, 4] }] }]);
    const fill = g.getObjectByName('violation-fill') as THREE.InstancedMesh;
    const m = new THREE.Matrix4();
    fill.getMatrixAt(0, m);
    const p = new THREE.Vector3(), q = new THREE.Quaternion(), s = new THREE.Vector3();
    m.decompose(p, q, s);
    expect(p.toArray()).toEqual([11, 21.5, 32]);
    expect(s.toArray()).toEqual([2, 3, 4]);
  });
  it('lists the expected invariant ids', () => {
    expect(violationRows(bad.violations).map((r) => r.invariant)).toEqual(['roof-covers', 'roof-closed', 'room-graph', 'rooms-reachable']);
  });
  it('is empty for a clean scene', () => {
    const clean = load('sample-house');
    expect(buildSceneObject(clean, { colour: 'role' }).getObjectByName('violations')!.children[0]!.children).toHaveLength(0);
    expect(violationRows(clean.violations)).toEqual([]);
  });
});
