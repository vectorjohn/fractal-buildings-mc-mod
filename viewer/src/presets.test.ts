import { describe, expect, it } from 'vitest';
import { PRESET_NAMES, TOP_PITCH_DEG, presetPose } from './presets';

const b = { min: [10, 60, 20], max: [30, 70, 40] };

describe('camera presets', () => {
  it('all aim at the bounds centre from the same distance', () => {
    const d = PRESET_NAMES.map((n) => { const p = presetPose(n, b, 'south'); return p.position.distanceTo(p.target); });
    for (const x of d) expect(x).toBeCloseTo(d[0]!, 6);
    expect(presetPose('front', b, 'south').target.toArray()).toEqual([20, 65, 30]);
  });
  it('front-left sits in front (south = +z) and to the viewer-left (-x), 30 degrees up', () => {
    const p = presetPose('front-left', b, 'south');
    expect(p.position.z).toBeGreaterThan(p.target.z);
    expect(p.position.x).toBeLessThan(p.target.x);
    const dx = p.position.clone().sub(p.target);
    expect(Math.asin(dx.y / dx.length()) * 180 / Math.PI).toBeCloseTo(30, 6);
  });
  it('rotates with the front facing; back-right is opposite', () => {
    const e = presetPose('front', b, 'east');
    expect(e.position.x).toBeGreaterThan(e.target.x);
    const bl = presetPose('front-left', b, 'north'), br = presetPose('back-right', b, 'north');
    expect(bl.position.x + br.position.x).toBeCloseTo(2 * bl.target.x, 6);
  });
  it('top looks down at a slight angle off vertical, tipped towards the front and the viewer-left', () => {
    const p = presetPose('top', b, 'south');
    const d = p.position.clone().sub(p.target);
    expect(d.y).toBeGreaterThan(0);
    expect(Math.acos(d.y / d.length()) * 180 / Math.PI).toBeCloseTo(TOP_PITCH_DEG, 6);
    expect(p.position.z).toBeGreaterThan(p.target.z); // front (south) side
    expect(p.position.x).toBeLessThan(p.target.x); // viewer-left
    expect(p.up.x + 0).toBeCloseTo(0); expect(p.up.z).toBeCloseTo(-1); // front edge stays at the bottom of the picture
  });
});
