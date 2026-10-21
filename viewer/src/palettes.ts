export type Rgb = number;

/** Distinct debug hues, one per role. */
export const ROLE_COLOURS: Record<string, Rgb> = {
  foundation: 0x8a8a8a,
  floor: 0x4a78c8,
  wall: 0xe0d2a8,
  trim: 0x7a4a2a,
  window: 0x59d0e8,
  door: 0xe08a1e,
  roof: 0xc03a2e,
  stair: 0x9ad04a,
  beam: 0x5a3a1a,
  fence: 0xb0b0b0,
  road: 0x9a7f55,
  paving: 0x8a8a8a,
  ground: 0x6b4f33,
  deck: 0xa07a48,
};
export const UNKNOWN_COLOUR: Rgb = 0xff00ff;
export const TERRAIN_COLOUR: Rgb = 0x5c7a3c;
/** District view: river bed under water, and the water surface. */
export const BED_COLOUR: Rgb = 0x8a7a55;
export const WATER_COLOUR: Rgb = 0x3a78c8;

export function roleColour(role: string): Rgb {
  return ROLE_COLOURS[role] ?? UNKNOWN_COLOUR;
}

/** Hook marker colours by kind; any other kind gets a hue from a hash of its name. */
const HOOK_COLOURS: Record<string, Rgb> = {
  bed: 0xe0457b, table: 0xf0c040, workstation: 0x40c0f0, chest: 0xb06a2a, hearth: 0xff6a1a,
  altar: 0xb070ff, counter: 0x40e090, light: 0xffffa0,
};
export function hookColour(kind: string): Rgb {
  const known = HOOK_COLOURS[kind];
  if (known !== undefined) return known;
  let h = 0;
  for (let i = 0; i < kind.length; i++) h = (Math.imul(h, 31) + kind.charCodeAt(i)) | 0;
  const hue = ((h >>> 0) % 360) / 360;
  const f = (n: number) => { const k = (n + hue * 12) % 12; return 0.5 - 0.5 * Math.max(-1, Math.min(k - 3, 9 - k, 1)); };
  return (Math.round(f(0) * 255) << 16) | (Math.round(f(8) * 255) << 8) | Math.round(f(4) * 255);
}
