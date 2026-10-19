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
};
export const UNKNOWN_COLOUR: Rgb = 0xff00ff;
export const TERRAIN_COLOUR: Rgb = 0x5c7a3c;

/** Block-like colours per style. */
export const STYLE_PALETTES: Record<string, Record<string, Rgb>> = {
  plain: {
    foundation: 0x7f7f7f, floor: 0xb08a55, wall: 0xd8d2c4, trim: 0x8a6a40,
    window: 0xa8d4f0, door: 0x9c7a46, roof: 0x8a8a8a, stair: 0x9c7a46, beam: 0x6b5030, fence: 0x8a6a40,
  },
  medieval: {
    foundation: 0x7a7a78, floor: 0x6b5030, wall: 0xd9c9a3, trim: 0x3e2c18,
    window: 0xa8d4f0, door: 0x9c7a46, roof: 0x9a3a2a, stair: 0x9c7a46, beam: 0x3e2c18, fence: 0x3e2c18,
  },
};

export function roleColour(role: string): Rgb {
  return ROLE_COLOURS[role] ?? UNKNOWN_COLOUR;
}
export function paletteColour(style: string, role: string): Rgb {
  const p = STYLE_PALETTES[style] ?? STYLE_PALETTES['plain']!;
  return p[role] ?? UNKNOWN_COLOUR;
}
