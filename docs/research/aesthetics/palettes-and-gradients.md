# Palettes and gradients

**Track:** 6 Aesthetics · **Kind:** technique (builder practice) · **Date:** 2026-10-08

## What it is

How builders choose a small set of blocks per role and blend between them
(gradients) so surfaces read as weathered material rather than flat texture
or random speckle. Sources read:

- WesterosCraft, [Gradients](https://westeroscraft.com/docs/building/fundamentals/gradients)
  and [Exteriors](https://westeroscraft.com/docs/building/fundamentals/exteriors).
- Conquest Reforged, [Gradienting your Builds](https://www.conquestreforged.com/guides/gradients).
- Minecraft Wiki, [Tutorial:Color palette](https://minecraft.wiki/w/Tutorial:Color_palette)
  (thin; flagged for rewrite on the wiki itself).
- vibecraft `agent/context/block_palette_guide.md` and the
  `choosing-materials` skill ([repo](https://github.com/amenti-labs/vibecraft)).
- Vanilla: `processor_list/mossify_10_percent.json` and the village house
  palettes (mcmeta data branch, 26.4-snapshot-3).

## Approach in three sentences

A palette is an ordered list of blocks of similar texture going from light to
dark (3 to 20+ entries, WesterosCraft); shade matters most, then hue
undertone, then texture. A gradient walks that list without skipping steps
("red shouldn't touch yellow", Conquest), either as a smooth drip (vertical,
with occasional diagonals) or as clusters that radiate from a focal point.
Placement follows physics: darker/dirtier where water runs and collects (wall
bases, corners, below openings, roof valleys), lighter where sun bleaches
(ridges, top courses).

## Procedural vs templated

Highly procedural: a gradient is a function from (cell position, a
weathering field) to a palette index, plus deterministic noise. Vanilla does
the crude version: a processor swaps cobblestone for mossy cobblestone with
uniform probability 0.1 (plains and taiga houses), which is exactly the
"smallpox" pattern WesterosCraft warns against.

## How it hooks into worldgen

n/a: design rules. Fits the planned "roles to blocks per style or biome"
palette layer: a role maps to a gradient (ordered block list), not one block,
and the mod adapter resolves the index per cell. The weathering field must be
seeded from (world seed, position) to stay deterministic.

## Licence

Wiki text CC BY-NC-SA 3.0; WesterosCraft and Conquest: no licence stated
(ideas only); vibecraft MIT. Vanilla data: Mojang, studied only.

## What to borrow (encodable rules)

- **Role palettes.** Each style defines, per role, an ordered gradient of 3–5
  blocks of similar texture (e.g. stone: stone bricks → cracked → mossy →
  cobble → mossy cobble; wood: stripped → planks of one species). Roles with
  one block are fine for trim/frame.
- **Proportions.** Main material ≈ 60%, secondary (roof, frame, trim) ≈ 30%,
  accent ≈ 10% of visible exterior cells (vibecraft's 60-30-10). Distinct
  materials per building ≤ 4–5 plus glass and details.
- **No skips.** Adjacent cells differ by at most 1 palette index (except at
  role boundaries).
- **Weathering field** w(x, y, z) in [0, 1] = base(height above ground,
  falling off over 2–3 blocks) + edge terms (corners, under sills, wall foot,
  roof valleys/eaves) + low-frequency noise (clusters 2–4 blocks wide). Index
  = quantise(w) with a small dither. Darker at bottom, lighter at top.
- **Building-level variation.** Choose 2 complementary woods per building
  where the style allows (WesterosCraft: birch–oak, oak–jungle, jungle–spruce);
  one timber colour and one infill colour per building.
- **Contrast between roles.** Frame/trim vs infill must differ clearly in
  value (luminance) so depth reads from a distance; roof differs from walls.
- **Per-biome palettes.** Biome picks the style's defaults (vanilla mapping:
  plains oak, taiga spruce + cobble, savanna acacia + terracotta, desert
  sandstone family, snowy spruce + snow/ice; see `vanilla-villages.md`).

## What to avoid

- Uniform random replacement (vanilla's 10% mossy), "splattering" (skipping
  palette steps), "sandwiching" (horizontal stripes of one block each).
- Mixing high-texture blocks into a gradient with smooth ones.
- Unordered block sets in core code: palettes are ordered lists.

## Checks we could test

- Exterior role-share histogram within tolerance of the style's target.
- Max palette-index jump between adjacent same-role cells ≤ 1.
- Mean weathering index of the bottom course > that of the top course.
- Luminance contrast between frame and infill above a threshold (needs a
  per-block mean colour table; see below).

## Verdict

Adopt. Palettes become ordered gradients per role, driven by a deterministic
weathering field. Needs a block colour/luminance table to automate ordering;
otherwise hand-order per style.

## Could not verify

Block luminance orderings (e.g. vibecraft's wood order puts spruce lighter
than dark oak but after acacia) were not measured; computing mean colours
from the vanilla textures in the dev env is a cheap follow-up. No source
gives numeric band widths or noise scales; the numbers above are my choices
to tune in the gallery.
